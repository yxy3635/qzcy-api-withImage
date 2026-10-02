# Docker 服务器部署

适用于全新 Linux 服务器，需安装 Docker Engine 和 Docker Compose v2（`docker compose version` 可用）。建议至少 2 核、4 GB 内存，并留出图片与数据库空间。服务器只需 Docker，无需安装 Node、Java 或 Maven；首次构建需要联网下载镜像和依赖。

## 首次启动

在服务器上传或克隆完整仓库，进入项目根目录：

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

填写以下配置，空值会阻止 Compose 启动：

| 变量 | 填写内容 |
| --- | --- |
| `SITE_URL` | 实际访问源，例如 `http://203.0.113.10`、`http://203.0.113.10:8088` 或 `https://images.example.com`，不要带末尾 `/` |
| `MYSQL_PASSWORD` | 应用数据库账号的随机密码 |
| `MYSQL_ROOT_PASSWORD` | 与应用账号不同的数据库管理员密码 |
| `JWT_SECRET` | 至少 32 字节随机字符串，可用 `openssl rand -hex 32` 生成 |
| `ADMIN_INITIAL_PASSWORD` | 首次创建 `admin` 的强密码 |

`.env` 中含 `$`、`#` 等特殊字符的值建议用单引号包裹，避免插值；不要提交 `.env`。`HTTP_PORT` 默认 80，冲突时可改为 8088，并同步修改 `SITE_URL`。

```bash
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
```

访问 `SITE_URL`，使用用户名 `admin` 和 `ADMIN_INITIAL_PASSWORD` 登录。首次启动会自动建库、建表、创建管理员，已有管理员不会被环境变量重置密码。MySQL 初始化脚本仅在空数据卷上执行。

进入管理端配置 SMTP、站点链接、图像模型/上游 API Key、中转渠道及支付参数。默认不配置 SMTP，普通用户注册/找回密码需要先启用邮件。Docker 空库初始化已关闭调试验证码返回；迁移旧库时请检查 `/admin/mail` 中该设置。`SITE_URL` 用于 CORS，不会自动填写数据库内的邮件链接或支付配置。

## 组件与数据

图像模型和中转渠道的上游地址、API Key 统一在管理后台配置；部署用的 `.env` 无需填写 `OPENAI_API_KEY` 或 `OPENAI_BASE_URL`。

- `web`：多阶段构建 Vue，由 Nginx 提供页面和 `/api/` 代理；支持页面刷新、最长 600 秒的上游空闲等待和 SSE 流式输出，上传请求上限 50 MB。
- `backend`：Java 17，使用独立 `docker` Profile，不包含本地 `dev/prod` 配置，以非 root 用户运行。
- `mysql`：MySQL 8.4，数据库名称固定为 `image_creator`，与仓库 SQL 一致。
- `redis`：仅用于缓存，限制 128 MB，可随时重建。

仅 Web 发布宿主机端口，MySQL、Redis、后端通过 Compose 网络通信。`imagecreater_mysql-data` 保存数据库，`imagecreater_images` 保存生成图片和收款二维码。不要随意修改 Compose 项目名，否则会切换到另一组数据卷。

`docker compose down` 保留数据；**不要执行 `docker compose down -v`，它会删除数据卷。** 数据库密码在首次初始化时生效，已有数据库改密码需要在 MySQL 中修改账号密码，再同步 `.env`。

## 域名与 HTTPS

仓库内的 Nginx 默认提供 HTTP。正式使用域名时可通过宿主机 Nginx、Caddy 或宝塔配置 TLS，代理到本机容器端口。此时设置 `HTTP_BIND_ADDRESS=127.0.0.1`、`HTTP_PORT=8088`、`SITE_URL=https://你的域名`。

外层代理也需设置 50 MB 上传上限、600 秒读写超时，并关闭响应缓冲以支持流式中转。外层代理必须保留原始 Host。

当前配置按 Nginx 直接接收请求设计，会覆盖客户端提交的转发头。增加外层代理时，在 `deploy/nginx.conf` 的 `server` 内设置 `set_real_ip_from <容器实际看到的可信代理IP或CIDR>`、`real_ip_header X-Forwarded-For`、`real_ip_recursive on`，将 `X-Forwarded-Proto` 行改为 `proxy_set_header X-Forwarded-Proto https;`（仅用于外层强制 HTTPS 的部署）。由外层代理覆盖 `X-Forwarded-For` 为真实客户端 IP。不要信任 `0.0.0.0/0`；修改后重建 Web，否则 IP 限流/白名单和支付回调可能使用错误的地址或协议。

```bash
docker compose up -d --build web
```

## 更新与日志

### 本次更新：登录、导航、图像调用文档与渠道供应商统计

现有 Dockerfile、Compose 端口、Nginx 路由及数据卷可直接使用，不需要新增环境变量。前端的新组件、样式和调用示例均在 `src/` 内，会自动打进 Web 镜像；邮箱密码登录与供应商统计涉及后端，必须同时更新 `web` 和 `backend`，仅重启旧容器不会加载新代码。

先按下文备份，再上传完整新代码（包含新增文件），或拉取已经提交了全部改动的版本。在服务器项目根目录执行：

```bash
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300 web backend
docker compose ps
docker compose logs --tail=100 backend
```

保留服务器现有 `.env`、Compose 项目名和数据卷。此次更新不需要重新生成 JWT 密钥、数据库密码或管理员密码。图像上游地址和 API Key 仍在管理后台配置。

供应商统计新增 `relay_usage_log.provider_id` 和 `provider_name` 两列。全新数据库由 `schema.sql` 创建；已有正常运行的数据库由后端 `RelaySchemaInitializer` 启动时检查并补齐，已存在的列不会重复添加。因此，本次更新通常无需手动执行整份 SQL。默认 Compose 创建的数据库账号具有本库的修改表结构权限；若使用自行收紧权限的账号，需要由数据库管理员先补齐这两列。日志表很大时应预留表结构变更时间，并检查后端启动日志。

更新后可检查字段是否存在（预期输出两行）：

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" image_creator -e "SHOW COLUMNS FROM relay_usage_log WHERE Field IN (\"provider_id\", \"provider_name\");"'
```

历史调用日志无法准确还原供应商，将显示在“未归属 / 渠道直连”；更新后的新调用才会保存供应商归属。上线后验证邮箱密码登录、`/admin/dashboard` 和 `/relay` 导航滚动，以及中转管理页的渠道/供应商统计。

### 其他版本的数据库迁移

更新前先备份。已有数据库不会自动重跑 Docker 初始化 SQL；跨版本升级时应检查对应迁移要求。`schema.sql` 含兼容迁移，仅在确认确实需要且审阅整份脚本后手动执行，不能把重跑初始化脚本作为每次更新的默认步骤：

```bash
docker compose stop web backend
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" image_creator' < backend/src/main/resources/db/schema.sql
docker compose up -d --build --wait --wait-timeout 300
docker compose logs --tail=100 backend
```

日常查看：`docker compose logs -f --tail=100`；重启后端：`docker compose restart backend`。修改 `.env` 后用 `docker compose up -d` 重建容器，单纯 `restart` 不会更新环境变量。升级后端镜像前请在开发环境执行 `backend/mvnw test`；镜像构建默认跳过测试。

## 备份与恢复

以下命令在 Linux 项目根目录运行。为保持图片与数据库一致，备份期间暂停应用：

```bash
mkdir -p backups
chmod 700 backups
docker compose stop web backend
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces --routines --triggers image_creator' > backups/database.sql
docker compose run --rm --no-deps --user 0 --entrypoint tar backend -czf - -C /app/userImage . > backups/images.tar.gz
docker compose start backend web
```

确认两条备份命令成功后，将备份和 `.env` 安全复制到服务器以外的位置；备份文件包含敏感数据。恢复会覆盖当前数据，仅对明确要恢复的环境操作，先保存现状：

```bash
docker compose stop web backend
docker compose up -d --wait mysql redis
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot image_creator' < backups/database.sql
docker compose run --rm -T --no-deps --user 0 --entrypoint sh backend -c 'tar -xzf - -C /app/userImage && chown -R 10001:10001 /app/userImage' < backups/images.tar.gz
docker compose up -d --wait
```

图片恢复为覆盖合并，目标卷中不在备份内的文件会保留；精确恢复请使用全新目标环境。迁移已有非 Docker 安装时，也要同时导出数据库和原 `userImage/`，按上述方式导入，保留旧 `JWT_SECRET` 可避免所有用户重新登录。

## 排错

- `mysql` 不健康：查看 `docker compose logs mysql`；检查磁盘、密码和初始化 SQL。初始化中途失败可能留下不完整数据卷，修复 SQL 后需要手动补跑脚本；不要删除有业务数据的卷。
- `backend` 不健康：查看 `docker compose logs backend`，检查数据库表、连接、JWT 和内存；首次构建慢属于下载依赖，健康等待超时可查看日志后重新等待。
- 页面能打开但 API 502：检查后端健康状态、Nginx 代理和外层代理配置。
- SSE 内容集中返回：确认每一层反向代理/CDN 都关闭了响应缓冲。
- 镜像或依赖拉取失败：检查服务器访问 Docker Hub、npm、Maven Central 的网络。

健康检查分别覆盖 Nginx、数据库、Redis 和后端注册配置查询，不代表 SMTP、支付或上游模型已配置成功；上线时需在管理端完成相应测试。
