#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

fail() { printf '\n错误：%s\n' "$*" >&2; exit 1; }
dc() { docker compose --project-name imagecreater --env-file .env -f compose.yaml "$@"; }
random_hex() { od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'; }
check_docker() {
  command -v docker >/dev/null || fail '请先安装 Docker Engine 和 Docker Compose v2。'
  docker compose version >/dev/null || fail '需要 Docker Compose v2（docker compose）。'
  docker info >/dev/null 2>&1 || fail 'Docker 未运行，或当前用户没有访问权限。可使用 sudo bash deploy.sh。'
}
verify_files() {
  sha256sum -c MANIFEST.sha256 --quiet || fail '部署文件不完整或发生变化，请检查校验结果。'
}
configure() {
  local purpose="${1:-fresh}" origin port secret generated
  if [[ -f .env ]]; then
    chmod 600 .env
    printf '保留现有 .env；请确认 SITE_URL 和 HTTP_PORT 对应新服务器。\n'
    return
  fi
  [[ -t 0 ]] || fail '首次配置需要交互终端；也可先复制 .env.example 为 .env 并填写。'
  read -r -p '新服务器访问地址（例如 https://images.example.com 或 http://服务器IP:8088）：' origin
  [[ "$origin" =~ ^https?://[a-zA-Z0-9.-]+(:[0-9]+)?$ ]] || fail '请输入完整的 HTTP/HTTPS 源地址，不带末尾 / 或路径。'
  read -r -p '容器对外端口（直接访问通常为 80，外层代理通常为 8088）[80]：' port
  port="${port:-80}"
  [[ "$port" =~ ^[0-9]{1,5}$ ]] && ((10#$port >= 1 && 10#$port <= 65535)) || fail '端口必须为 1–65535。'
  if [[ "$purpose" == migrate ]]; then
    read -r -s -p '粘贴旧服务器 JWT_SECRET（输入不显示，保留登录凭证）：' secret
    printf '\n'
    [[ ${#secret} -ge 32 && "$secret" != *"'"* && "$secret" != *$'\r'* && "$secret" != *$'\n'* ]] || fail 'JWT_SECRET 至少 32 字符；复杂值请在 .env 中手动配置。'
  else
    secret="$(random_hex 32)"
  fi
  umask 077
  generated="$(mktemp .env.generated.XXXXXX)"
  {
    printf 'HTTP_BIND_ADDRESS=0.0.0.0\nHTTP_PORT=%s\nSITE_URL=%s\n' "$port" "$origin"
    printf 'MYSQL_USER=imagecreater\nMYSQL_PASSWORD=%s\nMYSQL_ROOT_PASSWORD=%s\n' "$(random_hex 24)" "$(random_hex 24)"
    printf "JWT_SECRET='%s'\n" "$secret"
    printf 'ADMIN_INITIAL_PASSWORD=%s\n' "$(random_hex 16)"
    printf 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70.0\n'
  } > "$generated"
  mv -- "$generated" .env
  printf '已生成 .env 和独立随机密码。全新安装管理员为 admin，初始密码见 .env。\n'
}
validate() {
  [[ -f .env ]] || fail '缺少 .env，先执行 bash deploy.sh init。'
  dc config --quiet
}
start_app() {
  [[ ! -f .migration-in-progress ]] || fail '上次迁移尚未完成。请查看 README，排查恢复失败原因后再启动。'
  dc up -d --wait --wait-timeout 600
  dc ps
  printf '\n启动完成。访问 .env 中的 SITE_URL。迁移环境使用原管理员账号和密码。\n'
}
migrate() {
  [[ -s backups/database.sql && -s backups/images.tar.gz ]] || fail '请先上传旧服务器 backups/database.sql 和 backups/images.tar.gz；详见 README。'
  tar -tzf backups/images.tar.gz >/dev/null || fail '图片备份不是有效的 tar.gz。'
  if docker volume inspect imagecreater_mysql-data >/dev/null 2>&1 || docker volume inspect imagecreater_images >/dev/null 2>&1; then
    fail '目标存在同名数据卷。迁移命令仅用于全新目标，避免覆盖现有业务。不要先执行 fresh/start。'
  fi
  configure migrate
  validate
  dc pull
  : > .migration-in-progress
  dc up -d --wait --wait-timeout 600 mysql redis
  printf '\n正在恢复数据库，期间不会启动前后端……\n'
  dc exec -T mysql sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --binary-mode=1 -uroot image_creator' < backups/database.sql
  printf '\n正在恢复图片……\n'
  dc run --rm -T --no-deps --user 0:0 --entrypoint /bin/sh backend-data-init \
    -ec 'tar --no-same-owner --no-same-permissions -xzf - -C /data && chown -R 10001:10001 /data' < backups/images.tar.gz
  mv .migration-in-progress .migration-complete
  start_app
  printf '\n数据已恢复。请检查邮件站点链接、支付回调、渠道地址和旧号池连接后再切换域名。\n'
}

action="${1:-}"
if [[ -z "$action" ]]; then
  printf '1) 从旧服务器迁移（需先放入数据库和图片备份）\n2) 全新安装（创建空业务库）\n3) 仅生成 .env，暂不启动\n'
  read -r -p '选择 [1/2/3]：' answer
  case "$answer" in 1) action=migrate ;; 2) action=fresh ;; 3) action=init ;; *) fail '请选择 1、2 或 3。' ;; esac
fi
case "$action" in
  help|--help|-h)
    printf '用法：bash deploy.sh [init|fresh|migrate|start|status|logs|verify]\n迁移请先看 README，不要先执行 fresh/start。\n'
    exit 0 ;;
esac
check_docker
case "$action" in
  init) verify_files; configure; validate ;;
  fresh) verify_files; configure; validate; start_app ;;
  migrate) verify_files; migrate ;;
  start) validate; start_app ;;
  status) validate; dc ps ;;
  logs) validate; dc logs -f --tail=100 ;;
  verify) verify_files ;;
  *) fail '未知命令，运行 bash deploy.sh help 查看用法。' ;;
esac
