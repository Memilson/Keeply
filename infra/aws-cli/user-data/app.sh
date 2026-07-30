#!/bin/bash
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io nginx python3
systemctl enable --now docker

mkdir -p /opt/lg4us

cat >/opt/lg4us/backend.py <<'PY'
from http.server import BaseHTTPRequestHandler, HTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = b'{"service":"backend","status":"ok"}\n'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(fmt % args)

HTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
PY

cat >/opt/lg4us/frontend.html <<'HTML'
<!doctype html>
<html lang="pt-BR">
  <head><meta charset="utf-8"><title>LG-4US</title></head>
  <body><h1>LG-4US frontend OK</h1><p>VM de aplicação funcionando.</p></body>
</html>
HTML

cat >/etc/systemd/system/lg4us-backend.service <<'UNIT'
[Unit]
Description=LG-4US backend de teste
After=network-online.target

[Service]
WorkingDirectory=/opt/lg4us
ExecStart=/usr/bin/python3 /opt/lg4us/backend.py
Restart=always
User=www-data

[Install]
WantedBy=multi-user.target
UNIT

cat >/etc/nginx/sites-available/lg4us-frontend <<'NGINX'
server {
    listen 3000 default_server;
    listen [::]:3000 default_server;
    root /opt/lg4us;
    index frontend.html;
    location / { try_files $uri $uri/ /frontend.html; }
}
NGINX
ln -sf /etc/nginx/sites-available/lg4us-frontend /etc/nginx/sites-enabled/lg4us-frontend
rm -f /etc/nginx/sites-enabled/default

systemctl daemon-reload
systemctl enable --now lg4us-backend
nginx -t
systemctl enable --now nginx

systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service || systemctl enable --now amazon-ssm-agent || true
