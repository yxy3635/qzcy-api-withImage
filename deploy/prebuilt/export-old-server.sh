#!/usr/bin/env bash
set -euo pipefail
# Execute from the OLD project's directory; retain its Compose overrides in arguments.
compose_args=("$@")
dc() { docker compose "${compose_args[@]}" "$@"; }
[[ -f .env ]] || { printf '请先 cd 到旧服务器 compose.yaml 和 .env 所在目录。\n' >&2; exit 1; }
dc config --quiet
umask 077
output="$(pwd)/migration-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -m 700 "$output"
resume_services=()
while IFS= read -r service; do
  case "$service" in backend|web) resume_services+=("$service") ;; esac
done < <(dc ps --status running --services)
resume() {
  local result=$?
  trap - EXIT
  if ((${#resume_services[@]})); then dc start "${resume_services[@]}" || result=1; fi
  if ((result)); then printf '\n备份失败，请不要使用不完整备份：%s\n' "$output" >&2; fi
  exit "$result"
}
trap resume EXIT
if ((${#resume_services[@]})); then dc stop "${resume_services[@]}"; fi
dc exec -T mysql sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --triggers --events --hex-blob image_creator' > "$output/database.sql"
dc run --rm -T --no-deps --user 0 --entrypoint tar backend -czf - -C /app/userImage . > "$output/images.tar.gz"
cp -- .env "$output/server.env"
(cd "$output" && sha256sum database.sql images.tar.gz server.env > BACKUP.sha256)
printf '\n备份成功：%s\n旧服务即将恢复。最终切换期间应继续暂停旧站写入，避免两边数据分叉。\n' "$output"
