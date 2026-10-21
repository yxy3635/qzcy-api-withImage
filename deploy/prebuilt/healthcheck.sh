#!/usr/bin/env bash
set -euo pipefail
# The Ubuntu JRE contains Bash; no curl installation or image build is needed.
exec 3<>/dev/tcp/127.0.0.1/8080
printf 'GET /api/auth/registration-config HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r -t 4 status <&3
[[ "$status" == HTTP/*" 200 "* ]]
