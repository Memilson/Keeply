#!/bin/bash
set -euxo pipefail

cat >/etc/nginx/sites-available/keeply-edge <<'NGINX'
upstream keeply_frontend {
    least_conn;
    server app-1.keeply.internal:3000 max_fails=3 fail_timeout=10s;
    server app-2.keeply.internal:3000 max_fails=3 fail_timeout=10s;
}

upstream keeply_backend {
    least_conn;
    server app-1.keeply.internal:8000 max_fails=3 fail_timeout=10s;
    server app-2.keeply.internal:8000 max_fails=3 fail_timeout=10s;
}

server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name keeply.app.br;

    location /api/ {
        proxy_pass http://keeply_backend/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://keeply_frontend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX

ln -sfn /etc/nginx/sites-available/keeply-edge /etc/nginx/sites-enabled/keeply-edge
rm -f /etc/nginx/sites-enabled/default /etc/nginx/sites-enabled/lg4us-edge
nginx -t
systemctl restart nginx
systemctl enable nginx

cscli collections install crowdsecurity/nginx || true
systemctl enable --now crowdsec
systemctl is-active nginx
systemctl is-active crowdsec
