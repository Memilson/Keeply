#!/bin/bash
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y nginx curl gnupg ca-certificates lua5.1 libnginx-mod-http-lua luarocks gettext-base

curl -s https://install.crowdsec.net | bash
apt-get update
apt-get install -y crowdsec crowdsec-nginx-bouncer
cscli collections install crowdsecurity/nginx || true
systemctl enable --now crowdsec

cat >/etc/nginx/sites-available/lg4us-edge <<'NGINX'
upstream frontend {
    server APP1_PRIVATE_IP:3000;
    server APP2_PRIVATE_IP:3000;
}

upstream backend {
    server APP1_PRIVATE_IP:8000;
    server APP2_PRIVATE_IP:8000;
}

server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name keeply.app.br;

    location /api/ {
        proxy_pass http://backend/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location / {
        proxy_pass http://frontend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINX
ln -sf /etc/nginx/sites-available/lg4us-edge /etc/nginx/sites-enabled/lg4us-edge
rm -f /etc/nginx/sites-enabled/default

nginx -t
systemctl enable --now nginx
systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service || systemctl enable --now amazon-ssm-agent || true
