# 本次对话全量更新包（含中转报表）

本包整合截至 2026-10-13 的当前完整源码和已完成修改。中转包含 Claude 5 分钟／1 小时缓存计费、请求解压、用户与管理员使用记录的「压缩」标签，以及管理员中转报表（模型／分组／渠道／供应商、今日与历史总计、图表交互、CSV、Redis 3600 秒缓存）。号池包含 Messages 参数转换、默认非流式返回与错误透传修复；完整号池快照也包含此前已存在的 Responses 和思考回传等改动。

未实现已取消的 `/v1/responses/compact` 接口。「压缩」标签只识别明确的 compact 请求路径，不猜测普通请求是否在生成摘要。报表旧多分组日志归入「旧记录待确认」，不重复分摊费用。完整源码不代表修复了此前只诊断、没有实施的其他问题。

## 包内文件

- `imagecreater-full-20261013-reports.zip`：中转完整前后端源码，包含新增和未提交的报表文件，文件直接位于项目根层。
- `workbuddy2api-full-20261013.zip`：`F:/web/workbuddy2api/workbuddy2api` 当前完整源码快照，文件直接位于项目根层。
- `workbuddy2api-messages-fix.zip`：号池精确更新脚本；服务器号池版本不明确时推荐使用，保留其他定制代码。完整号池源码已经包含这个修复，二者不必重复安装。
- `SHA256SUMS.txt`：内层 ZIP 文件校验值；各完整包也含逐文件 SHA-256 清单。

不含真实 `.env`、本地 dev/prod 凭据、账号池 data/auth/management、数据库、用户图片、Git、依赖目录和编译产物。这是用于服务器 Docker 构建的源码包，不是预构建镜像。

## 1. 解开总包

上传 `relay-all-updates-20261013.zip` 到服务器 `/tmp`，执行：

```bash
mkdir -p /tmp/relay-all-updates-20261013
unzip -o /tmp/relay-all-updates-20261013.zip -d /tmp/relay-all-updates-20261013
cd /tmp/relay-all-updates-20261013
sha256sum -c SHA256SUMS.txt
```

以下更新命令适用于已有正常运行的服务器；不要在新目录启动另一套 Compose。所有路径替换成原项目实际路径；如原部署用了 `-p 项目名`、`--env-file` 或多个 `-f`，继续使用原参数。

## 2. 更新中转

进入原中转项目根目录（有 `compose.yaml` 与现有 `.env`）。先按 `docs/docker-deployment.md` 的「备份与恢复」保存数据库、图片和环境配置，再覆盖源码。

```bash
cd /你的/中转项目目录
tar -czf /tmp/imagecreater-source-before-20261013.tar.gz --exclude='./node_modules' --exclude='./backend/target' --exclude='./backend/userImage' --exclude='./backups' --exclude='./.git' .
unzip -o /tmp/relay-all-updates-20261013/imagecreater-full-20261013-reports.zip -d . -x compose.yaml deploy/nginx.conf .env.example
docker compose config --quiet
```

解压默认保留服务器原 Compose、Nginx 及环境示例，避免改变域名、代理、端口和数据卷；真实 `.env` 本就不在包中。若自行修改过其他源码，先对比并保留定制。包里有完整默认 Docker 构建配置，可供比较。

分别构建和更新后端：

```bash
docker compose build backend
docker compose up -d --no-deps --wait --wait-timeout 300 backend
docker compose logs --tail=100 backend
```

确认后端启动成功后，分别构建和更新前端：

```bash
docker compose build web
docker compose up -d --no-deps --wait --wait-timeout 120 web
docker compose ps
docker compose logs --tail=50 web
```

`--no-deps` 要求原 mysql/redis 已运行；若缓存服务未运行，先用原 Compose 执行 `docker compose up -d --wait mysql redis`。不要执行 `docker compose down -v`，不要改项目名或删除数据卷。只执行 restart 不会构建新源码。

数据库启动时自动补齐缺失字段，包括本次 `billing_group_code` 和此前缓存细分字段；账号需有本库 ALTER 权限。已有数据库不用重新导入完整 `schema.sql`。大日志表的 ALTER 可能需要更长时间，可查看日志判断启动进度。

独立镜像构建（仅当你使用自定义部署并自行引用镜像标签时）：

```bash
docker build -t imagecreater-backend:20261013-reports -f backend/Dockerfile backend
docker build -t imagecreater-web:20261013-reports -f Dockerfile .
```

这些独立标签不会自动被默认 Compose 使用；常规更新用前面的 Compose 命令即可。

## 3. 更新号池（独立项目）

### 推荐：只应用本次 Messages 修复

```bash
mkdir -p /tmp/workbuddy-messages-fix
unzip -o /tmp/relay-all-updates-20261013/workbuddy2api-messages-fix.zip -d /tmp/workbuddy-messages-fix
python3 /tmp/workbuddy-messages-fix/apply_messages_fix.py /你的/号池项目目录
```

脚本检查目标版本，备份两个原文件；无法识别时会在写入前停止，重复执行不会重复修改。随后按原部署重新构建并启动。补丁测试和说明见补丁包内 `MESSAGES_FIX.md`。

### 完整源码更新（需要整合同一版本的其他改动时）

```bash
cd /你的/号池项目目录
tar -czf /tmp/workbuddy-source-before-20261013.tar.gz --exclude='./data' --exclude='./auth' --exclude='./management' --exclude='./deploy/one-click/data' --exclude='./.venv' --exclude='./.git' .
unzip -o /tmp/relay-all-updates-20261013/workbuddy2api-full-20261013.zip -d . -x 'deploy/*' .dockerignore
```

保留服务器原部署目录与 `.dockerignore`，沿用原账号目录、环境配置和挂载。完整覆盖涉及现有其他功能，服务器有定制源码时须对比；版本不明确可使用上面的 Messages 精确脚本。

### Docker 重新构建：选择原来正在使用的一种部署

一键部署（服务名 `app`）：

```bash
cd /你的/号池项目目录/deploy/one-click
docker compose config --quiet
docker compose build app
docker compose up -d --no-deps --wait --wait-timeout 180 app
docker compose logs --tail=100 app
```

管理员控制台部署（服务名也是 `app`）：

```bash
cd /你的/号池项目目录/deploy/admin
docker compose config --quiet
docker compose build app
docker compose up -d --no-deps --wait --wait-timeout 180 app
docker compose logs --tail=100 app
```

独立转换器部署（服务名 `codebuddy2openai`）：

```bash
cd /你的/号池项目目录/deploy/standalone
docker compose build codebuddy2openai
docker compose up -d --no-deps codebuddy2openai
docker compose logs --tail=100 codebuddy2openai
```

只执行原部署对应的一组，不能同时启动三套。若服务器是宝塔 Python 或其他进程管理部署，应用源码后用原管理器重启，不需要新增 Docker 部署。号池管理界面为静态文件和 Python 同服务，没有分别构建前后端的两个服务。

## 4. 更新后检查

- 管理员菜单出现「中转报表」，检查四个维度和今日／累计切换；缓存快照 TTL 一小时，重新加载不会强制绕过。
- 使用记录和管理员日志的 compact 路径显示「压缩」标签。
- Messages 用 `stream:false` 和 `tool_choice:{"type":"auto"}` 复测，再测 `stream:true`。不把 API Key 写进命令历史或更新包。
- Claude 用量检查缓存读取、5 分钟／1 小时缓存创建的统计与费率。

本地验证：中转后端 239 项测试全部通过（含真实 MySQL 聚合与管理员权限）；前端生产构建、用量测试和浏览器交互检查通过。Messages 修复此前 141 项测试通过。打包时核对当前文件清单、内容哈希和 ZIP CRC。当前环境没有 Docker，未构建 Docker 镜像或更新线上服务器。
