# 2026-10-22 提示词规则：前后端合并更新包

压缩包：`imagecreater-update-20261022-prompt-rules.zip`。没有外层文件夹，解压到**原项目根目录**，解压后为 `dist/index.html` 和 `backend/app.jar`。本包用于更新已有部署。

包内包含完整前端 `dist/`、Java 17 可运行的 `backend/app.jar`、前后端运行时 Dockerfile、对应 `.dockerignore`、后端健康检查、构建信息、文件校验清单和功能说明。服务器无需执行 npm 或 Maven。

本包会覆盖根目录和 backend 目录的 Dockerfile、`.dockerignore`。原 Dockerfile 若有自定义运行依赖，请先备份并合并。现有 `.env`、Compose、Nginx 配置、数据库和图片数据不在包内，继续沿用。根目录的 Dockerfile 继续读取服务器原有的 `deploy/nginx.conf`；原项目须已有这个文件。`.dockerignore` 也随包更新，避免旧配置排除 `dist` 导致前端镜像构建失败。

所有命令在原部署目录执行。服务名按本项目为 `backend`、`web`；如果原命令带有 `-p`、`-f` 或 `--env-file`，下文每条 Compose 命令均保留相同参数。建议先后端、后前端。

## 1. 解压覆盖

先备份原前后端文件和现有数据库。假设 ZIP 已上传至 `/tmp`，在**原项目根目录**执行：

```bash
unzip -o /tmp/imagecreater-update-20261022-prompt-rules.zip -d .
sha256sum -c MANIFEST-20261022-PROMPT-RULES.sha256
```

全部显示 `OK` 后继续。不要把包解压到 backend 或 dist 目录，否则会多套一层目录。不要删除现有 `dist/assets` 中的旧哈希文件，保留旧文件可支持仍打开旧页面的浏览器。

## 2. 后端单独更新

**原 Compose 的 backend 有 `build:`（包括之前的源码构建部署）：**

```bash
docker compose build backend
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend
docker compose logs --since=10m backend
```

包内 backend/Dockerfile 直接复制已经编译的 `app.jar`，此处仅生成运行镜像，不编译源码。Java 17 基础镜像未缓存时需要联网拉取。

**原 Compose 的 backend 通过 `./backend/app.jar:/app/app.jar:ro` 挂载（预构建部署）：**

```bash
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend
docker compose logs --since=10m backend
```

此方式不执行 build。重建容器确保使用替换后的 JAR，不能只 restart。数据库和 Redis 沿用原服务。多个后端实例均需更新。

## 3. 前端单独更新

**原 Compose 的 web 有 `build:`，静态文件包含在镜像内：**

```bash
docker compose build web
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 120 web
docker compose exec -T web nginx -t
```

包内根目录 Dockerfile 直接复制 `dist/` 到 Nginx，不再下载 Node 依赖或编译前端。

**原 Compose 的 web 通过 `./dist:/usr/share/nginx/html:ro` 挂载（预构建部署）：**

覆盖 dist 后静态文件已经生效；若同时需要明确重建前端容器，执行：

```bash
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 120 web
docker compose exec -T web nginx -t
```

此方式不执行 build。最后浏览器强制刷新。如果前端由宝塔等独立站点提供，只把包内 dist 的**内容**覆盖到原网站目录，并沿用该站点原有的 `/api/` 代理。

## 4. 启动迁移及生效确认

无需手动导入 SQL。后端启动会创建提示词配置表、命中记录表，并给已有配置表补充 `auto_ban_threshold` 字段，默认值 3；数据库账号需要 CREATE/ALTER 权限。已有提示词设置会保留。不要重新导入完整 schema，也不要执行 `docker compose down -v`。

```bash
docker compose ps backend web
```

登录管理员后台 → 中转管理 → 提示词规则，确认能编辑规则、开关和自动封禁阈值（默认累计 3 次），可查看命中用户的「观察中／已封禁」状态。规则命中返回真正的 HTTP 400 和指定警告。检测以模型完整拒答句为依据，不因单独出现 jailbreak 等关键词标记。为在发送响应头前确定状态码，开启规则后 Responses 和多候选流式回复会等待完整检测后返回。

封禁会限制账号的后续网站及中转访问；管理员可在用户管理解除封禁，历史次数保留。降低阈值在用户下一次新命中时判断，不追溯批量封禁历史用户。

本包包含当前工作区的全部前后端改动；测试结果及 SHA-256 见 `BUILD-INFO-20261022-PROMPT-RULES.json` 和 `MANIFEST-20261022-PROMPT-RULES.sha256`。本地未安装 Docker，未验证服务器真实容器启动；这里只生成并校验部署文件，没有连接或更新线上服务。
