#!/bin/bash
set -euxo pipefail

PGCONF=$(su - postgres -c "psql -tAc 'SHOW config_file'")
PGHBA=$(su - postgres -c "psql -tAc 'SHOW hba_file'")

sed -i "/^[#]*listen_addresses/c\listen_addresses = '*'" "$PGCONF"
grep -qF 'host all all 10.50.1.0/24 scram-sha-256' "$PGHBA" || echo 'host all all 10.50.1.0/24 scram-sha-256' >> "$PGHBA"

systemctl restart postgresql
systemctl enable postgresql
systemctl is-active postgresql
ss -ltn | grep ':5432'
