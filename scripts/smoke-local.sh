#!/usr/bin/env bash
set -euo pipefail

BIN=${BIN:-./build/keeply-agent}
rm -rf /tmp/keeply-smoke
mkdir -p /tmp/keeply-smoke/testdata
printf 'hello keeply\n' > /tmp/keeply-smoke/testdata/a.txt
printf 'chunk test\n' > /tmp/keeply-smoke/testdata/b.txt

$BIN init-local --config /tmp/keeply-smoke/keeply.json --repo /tmp/keeply-smoke/repo --db /tmp/keeply-smoke/keeply.db
$BIN backup --config /tmp/keeply-smoke/keeply.json --source /tmp/keeply-smoke/testdata
$BIN verify --config /tmp/keeply-smoke/keeply.json --snapshot latest
$BIN list --config /tmp/keeply-smoke/keeply.json
$BIN restore --config /tmp/keeply-smoke/keeply.json --snapshot latest --target /tmp/keeply-smoke/restore

diff -r /tmp/keeply-smoke/testdata /tmp/keeply-smoke/restore

echo 'smoke ok'
