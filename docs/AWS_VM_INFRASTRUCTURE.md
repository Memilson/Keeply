# Keeply infrastructure

## Topology

```text
Internet / Cloudflare DNS (keeply.app.br)
  -> keeply-edge (public EC2, Nginx + CrowdSec)
     -> keeply-app-1 (private EC2)
     -> keeply-app-2 (private EC2)
        -> keeply-db (private EC2, PostgreSQL)
```

The public DNS is managed in Cloudflare. Its A record must point to the public IP of `keeply-edge`.

## AWS resources

- VPC: `vpc-0754d19b2fe6a5637` — `10.50.0.0/16`
- Public subnet: `10.50.1.0/24` — edge only
- Private subnet: `10.50.2.0/24` — applications and database
- Private DNS zone: `keeply.internal`
- Internal names: `app-1.keeply.internal`, `app-2.keeply.internal`, `db.keeply.internal`, and `edge.keeply.internal`

## Request routing

Nginx on the edge routes:

- `/` to the frontend on port `3000`
- `/api/` to the backend on port `8000`

Each application VM exposes both ports. Nginx uses the two application VMs as upstreams with `least_conn`.

## Security model

- Only the edge Security Group allows inbound HTTP/HTTPS from the internet.
- Application ports accept inbound traffic only from the edge Security Group.
- PostgreSQL port `5432` accepts inbound traffic only from the application Security Group.
- EC2 management is performed through AWS Systems Manager (SSM), not SSH.

## PostgreSQL

PostgreSQL is installed on `keeply-db`, enabled at boot, and listens on port `5432` for the private VPC network. Create application credentials separately and store them in a secret manager before deploying production code.

## Migration status

The initial instances were started in the public subnet for bootstrap.

- `keeply-app-1` and `keeply-app-2` were replaced by instances in the private subnet, without public IPs.
- `keeply-edge` remains the only public entry point.
- The PostgreSQL AMI is ready, but the private database replacement is waiting for AWS quota request `54557d97934840219cb127dabe2f4b35Kqw4vWB7` to raise the on-demand standard vCPU limit from 8 to 16.

## Operations

The source CLI and bootstrap scripts are in `infra/aws-cli/`. They are deliberately kept as learning material for rebuilding or extending the environment.
