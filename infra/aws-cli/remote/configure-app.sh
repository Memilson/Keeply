#!/bin/bash
set -euxo pipefail

mkdir -p /opt/keeply
cat >/opt/keeply/frontend.html <<'HTML'
<!doctype html>
<html lang="pt-BR">
  <head><meta charset="utf-8"><title>Keeply</title></head>
  <body><h1>Keeply frontend OK</h1></body>
</html>
HTML

cat >/etc/nginx/sites-available/keeply-frontend <<'NGINX'
server {
    listen 3000 default_server;
    listen [::]:3000 default_server;
    server_name _;
    root /opt/keeply;
    index frontend.html;

    location / {
        try_files $uri $uri/ /frontend.html;
    }
}
NGINX

ln -sfn /etc/nginx/sites-available/keeply-frontend /etc/nginx/sites-enabled/keeply-frontend
rm -f /etc/nginx/sites-enabled/default /etc/nginx/sites-enabled/lg4us-frontend
nginx -t
systemctl restart nginx
systemctl enable nginx
ss -ltn | grep ':3000'
