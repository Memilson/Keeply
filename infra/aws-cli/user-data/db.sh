#!/bin/bash
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y postgresql

systemctl enable --now postgresql

systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service || systemctl enable --now amazon-ssm-agent || true
