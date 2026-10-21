# 本地编译产物 Docker 部署包

## 2026-10-19 账号安全更新：已有站点先看这里

本次包含安全修复后的完整 `dist/`、`backend/app.jar`、完整初始化 SQL `deploy/schema.sql` 和独立增量 SQL `deploy/updates/security-20261019.sql`。详细修复与验证见 [账号安全升级说明](docs/fix-account-security-20261019.md)。

在原服务器原部署目录升级：先备份数据库和事件日志，保留原 `.env`、Compose 自定义配置和数据卷；将包内完整 `dist/`、`backend/app.jar`、`deploy/schema.sql` 替换到对应位置。检查 `JWT_SECRET` 为独立强随机值（可用 `openssl rand -hex 32` 生成并设置），初始管理员密码至少 10 位，邮件功能已配置真实 SMTP。然后在原部署目录执行：

```bash
docker compose up -d --force-recreate --wait --wait-timeout 300 backend web
docker compose logs --since=10m backend
```

原站若使用 `-f compose.local.yaml`、自定义项目名等参数，应保留原参数。多实例必须全部更新。后端启动自动执行本次增量迁移（DB 用户需 ALTER/CREATE 权限）；若需 DBA 提前执行，选择现有业务数据库后导入 `deploy/updates/security-20261019.sql`。两种方式可重复执行；已有数据库不要重跑完整 `schema.sql`。本次无需清空数据卷，也无需执行下文用于迁移到新服务器的 `migrate` 或全新安装的 `fresh`。

**升级后所有旧网页会话失效，用户需要重新登录。可疑 API 密钥仍需逐一禁用/轮换，修改用户密码不会自动撤销合法单分组 API 密钥。** 历史通配符、多分组或无效分组密钥会被后端拒绝。

此包由当前工作区的前后端重新编译生成，包含本地尚未提交的功能变更。服务器不编译前后端、不执行 npm/Maven、不构建应用镜像，直接运行官方 Nginx、Java 17 JRE、MySQL 8.4 和 Redis 镜像。首次启动需要联网拉取这些镜像；这不是完全离线镜像包。

服务器：Linux，Docker Engine 20.10+，Docker Compose v2（建议较新版本，支持 `up --wait`）。建议至少 2 核 / 4 GB 内存，并给数据库和图片留足磁盘空间。用 root 或有 Docker 权限的用户执行以下命令。

## 一、解压后的文件结构

```text
imagecreater-prebuilt-20261018/
├── README.md
├── BUILD-INFO.json           # 构建版本、测试结果和限制
├── MANIFEST.sha256           # 部署文件校验清单
├── compose.yaml              # 完整运行编排，无 build 配置
├── .env.example              # 配置参考，实际 .env 由脚本生成或从旧站复制
├── deploy.sh                 # 启动、迁移、检查和日志
├── backend/
│   ├── app.jar               # Java 17 可执行 JAR，不含本地 dev/prod 配置
│   └── healthcheck.sh
├── dist/                     # 已编译的前端全部文件
├── deploy/
│   ├── nginx.conf
│   ├── schema.sql
│   └── 02-production-defaults.sql
├── tools/
│   └── export-old-server.sh  # 在旧 Docker 服务器导出一致性备份
└── backups/
    └── README.txt            # 将旧服务器备份放在这里
```

包中没有旧服务器 `.env`、数据库、图片、号池数据和证书，也没有本地开发数据库。所有图片素材已包含在 dist 中，无需额外上传 public。

## 二、迁移旧站（本次推荐流程）

**新服务器请先迁移，再启动。不要先选择全新安装，否则会先创建新数据卷。** 此包固定使用项目名 imagecreater 和数据卷 imagecreater_mysql-data / imagecreater_images，迁移脚本会拒绝覆盖已有同名数据卷。

### 1. 在旧服务器备份

把本包的 `tools/export-old-server.sh` 上传到旧服务器任意目录。进入旧站原有 compose.yaml 和 .env 所在目录执行：

```bash
cd /旧服务器/原项目目录
bash /脚本所在目录/export-old-server.sh
```

如果旧站使用 compose.local.yaml、特定项目名或其他覆盖文件，保留原参数。例如：

```bash
bash /脚本所在目录/export-old-server.sh -f compose.yaml -f compose.local.yaml
```

脚本暂时停止原来正在运行的 web/backend，导出数据库和图片，再恢复服务；导出过程不删除或修改旧站数据卷。生成 migration-backup-日期时间 目录，包含 database.sql、images.tar.gz、server.env 和 BACKUP.sha256。server.env 含旧服务器密钥，请通过可信方式传输。此脚本针对原有 Docker Compose 部署；非 Docker 旧站应自行导出 image_creator 数据库，以及图片目录内容（压缩包解压后应直接得到图片子目录，而非再套一层 userImage）。

**最终备份时应停止旧站业务写入，备份后直到切换完成都保持旧站不再接收写入。** 默认导出脚本会恢复服务以便演练；正式切换时可先在旧站执行 `docker compose stop web backend`（带原有覆盖参数），再运行导出脚本，它就不会恢复原本已停止的服务。不要让新旧站同时处理充值、请求或其他写入。

### 2. 上传新服务器

上传本 ZIP 后解压：

```bash
unzip imagecreater-prebuilt-20261018.zip
cd imagecreater-prebuilt-20261018
```

上传旧站备份目录中的文件到：

```text
backups/database.sql
backups/images.tar.gz
backups/server.env
backups/BACKUP.sha256
```

校验备份，并使用旧站配置：

```bash
(cd backups && sha256sum -c BACKUP.sha256)
cp backups/server.env .env
chmod 600 .env
nano .env
```

修改 `SITE_URL` 为新服务器访问地址（不带末尾 /）；按实际情况调整 HTTP_PORT / HTTP_BIND_ADDRESS。保留数据库密码等配置；JWT_SECRET 必须满足本次强度要求，涉及账号安全事件时应轮换为新的随机值。新库会按这些密码初始化，旧账号密码会从业务数据库恢复。这里的 ADMIN_INITIAL_PASSWORD 不会重置已存在的管理员。

执行迁移（可能需数分钟）：

```bash
bash deploy.sh migrate
```

脚本先检查文件并拉取镜像，再只启动 MySQL/Redis，恢复数据库与图片，最后启动后端和前端。数据库导入失败时不会继续启动应用；保留 .migration-in-progress 标记和失败现场，请先查看日志排错。不要删除有业务数据的数据卷，也不要直接移除标记后当作恢复成功。

如果没有旧 .env，也可直接运行 migrate，脚本会询问地址、端口和旧 JWT_SECRET，其余密码随机生成。JWT_SECRET 影响登录状态；原有 API 密钥、余额和账号记录来自数据库备份。旧环境如有额外自定义变量或代理配置，需要自行迁移到新部署。

## 三、全新安装（不迁移旧用户和余额时）

```bash
unzip imagecreater-prebuilt-20261018.zip
cd imagecreater-prebuilt-20261018
bash deploy.sh fresh
```

按提示填写访问地址和 HTTP 端口。脚本自动生成 .env、随机数据库密码、JWT_SECRET 和初始管理员密码，完成启动。用户名为 admin，初始密码查看 .env 的 ADMIN_INITIAL_PASSWORD；不要把 .env 发到公共渠道。

也可执行 `bash deploy.sh`，通过菜单选择迁移、全新安装或仅生成配置。若手工配置则先 `cp .env.example .env`，填齐所有必填值。

## 四、端口、域名和号池

- 直接通过 IP 访问：例如 HTTP_BIND_ADDRESS=0.0.0.0、HTTP_PORT=80、SITE_URL=http://新服务器IP。
- 宝塔/Nginx/Caddy 在宿主机提供 HTTPS：HTTP_BIND_ADDRESS=127.0.0.1、HTTP_PORT=8088、SITE_URL=https://你的域名。配置证书并反向代理到 127.0.0.1:8088。外层代理保留 Host，上传上限至少 50 MB，读写超时至少 600 秒，关闭响应缓冲。迁移证书或重新签发后，再切换 DNS。
- HTTPS 外层代理需要配套修改 deploy/nginx.conf：server 内设置可信代理的 set_real_ip_from、real_ip_header X-Forwarded-For、real_ip_recursive on，并将 X-Forwarded-Proto 设置为 https（仅用于外层强制 HTTPS 的场景）。使用容器实际看到的可信代理 IP/CIDR，不要信任 0.0.0.0/0。修改配置后执行 `docker compose --env-file .env -f compose.yaml restart web`。默认配置适用于容器直接接收 HTTP 请求。
- 本包已设置 host.docker.internal → 宿主机网关。若号池也搬到新服务器，确认号池监听 Docker 可访问的地址，再在后台配置 http://host.docker.internal:8787/v1（按实际端口修改）。号池程序、账号、密钥和运行环境需另外迁移；仅搬中转项目不会自动搬走号池。
- 仅 Web 发布宿主机端口；不需要公开 MySQL、Redis 或 Java 端口。
- 后台还需检查真实 SMTP、邮件站点 URL、支付通知地址，以及渠道上游地址。本次已禁止验证码调试返回，必须实际发送邮件。SITE_URL 用于 CORS，不会自动修改数据库中的这些业务设置。

## 五、常用命令与数据

```bash
bash deploy.sh status
bash deploy.sh logs
bash deploy.sh start
docker compose --env-file .env -f compose.yaml logs --tail=100 backend mysql
docker compose --env-file .env -f compose.yaml restart backend
```

修改 .env 后执行 start 以重建受影响容器；单纯 restart 不会加载新的环境变量。修改 nginx.conf 后重启 web。后续更新编译产物时，先备份，替换完整 dist 和 app.jar 后执行 `docker compose --env-file .env -f compose.yaml up -d --force-recreate --wait backend web`，确保 JAR 文件挂载重新建立。无需重建任何应用镜像。

数据库与图片保存在 Docker 数据卷，Redis 仅缓存可重建。backend-data-init 为一次性目录权限初始化容器，Exited (0) 是正常状态；Java 以 UID/GID 10001 运行。`docker compose down` 保留数据；**不要执行 docker compose down -v**。

schema.sql 只在空 MySQL 卷初始化时执行；恢复备份后不要再手动重跑整份 schema。后端有部分启动兼容迁移，跨很老的版本仍需按对应版本迁移说明处理。首次启动失败时查看 backend/mysql 日志，不要反复删除数据卷。

本包不含 Docker 镜像。如果服务器访问 Docker Hub 失败，需要配置可用镜像来源，或另外准备与服务器 CPU 架构一致的离线镜像。本机未安装 Docker，构建和包结构已在本地验证，容器联动与旧数据恢复需在目标服务器验证。
