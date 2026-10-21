# 2026-10-19 前后端分开覆盖部署

这两个 ZIP 都没有外层日期文件夹。前端 ZIP 直接包含 index.html、assets/ 等静态文件；后端 ZIP 直接包含 app.jar、Dockerfile、healthcheck.sh、sql/、升级说明和校验清单。

它们可分别上传和部署。前端仍通过同域 `/api` 请求后端，沿用现有反向代理；若前后端在不同服务器，前端站点的 `/api/` 反向代理必须指向实际后端地址，保留路径及原有转发头覆盖、流式响应和超时配置。包内不覆盖现有 Nginx/Compose/.env。后端 Docker profile 继续使用 mysql、redis 服务名，沿用原后端网络与数据服务；把后端迁移到全新服务器仍需迁移数据库、图片和配置，不能仅靠解压完成。

## 上传前

先备份数据库、原前端静态目录和原 backend 目录。保留事件日志和可疑密钥记录。不要删除数据库或图片卷，不要执行 `docker compose down -v`。

校验文件与压缩包上传到同一目录后执行：

```bash
sha256sum -c imagecreater-frontend-dist-20261019.zip.sha256
sha256sum -c imagecreater-backend-20261019.zip.sha256
```

如果服务器以前使用自定义 `-p`、`--env-file` 或多个 `-f` 参数，下文所有 Compose 命令保留原参数。在有现成前端会话的站点，建议维护窗口内先更新后端，再更新前端，避免短暂的新旧接口不匹配。

## 前端：解压到实际静态目录

把前端 ZIP 上传到 `/tmp`。假设原项目通过宿主机 `dist/` 挂载到 Nginx：在原项目目录执行，解压目标就是 `dist/`，不要解压到项目根目录，也不要再新建一层 dist：

```bash
unzip -o /tmp/imagecreater-frontend-dist-20261019.zip -d ./dist
```

宝塔独立站点则把 `-d ./dist` 换成实际网站根目录（原 index.html 所在目录）。网站根目录不能是后端目录。

若原有前端已通过目录挂载提供静态文件，覆盖后会生效，浏览器强制刷新。若原前端静态文件打在镜像里且没有 dist 挂载，单纯解压宿主机文件不会生效：需在原 web 服务挂载实际静态目录到 `/usr/share/nginx/html:ro` 后，使用原 Compose 参数重建 web 容器。

跨服务器部署时先确认现有 `/api/` 代理可访问后端；本包使用相对路径 `/api`，无需重编译前端或修改 JS 中的地址。

## 后端：解压到原 backend 目录

把后端 ZIP 上传到 `/tmp`，在原项目根目录执行：

```bash
unzip -o /tmp/imagecreater-backend-20261019.zip -d ./backend
(cd backend && sha256sum -c BACKEND-20261019-MANIFEST.sha256)
```

解压后是 `backend/app.jar`，不是 `backend/backend/app.jar`。SQL 在 `backend/sql/security-20261019.sql`。现有 `.env`、Compose、数据库和图片目录不包含在包内。Dockerfile 会替换原 backend/Dockerfile，用已构建 JAR 打运行镜像，服务器不再执行 Maven。若你以前自行修改过 Dockerfile，先保留备份并核对自定义运行依赖。

先检查原 `.env` 的 JWT_SECRET 是独立强随机值，可用 `openssl rand -hex 32` 生成新值；ADMIN_INITIAL_PASSWORD 至少 10 位。已有管理员不会被初始密码变量重置。SMTP 必须实际可用。

### 原来是源码 Docker 构建部署（backend 配置有 build）

在原项目根目录执行：

```bash
docker compose build backend
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend
docker compose logs --since=10m backend
```

本次 build 只把已经编好的 app.jar 放入 Java 17 运行镜像，首次可能需要拉取基础镜像。不会重新编译源码，也不会更新前端容器。原数据库、Redis 应保持运行。

### 原来是预构建部署（挂载 ./backend/app.jar）

无需 build，在原项目根目录执行：

```bash
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend
docker compose logs --since=10m backend
```

仅 restart 可能继续使用被替换前文件的绑定挂载，必须重建容器。所有后端实例都要更新。

## SQL 更新及生效确认

后端启动自动添加 `user.auth_version` 并创建 `auth_attempt_bucket`，数据库账号需要 ALTER/CREATE 权限。随包提供的 `sql/security-20261019.sql` 是同样内容的独立增量脚本，若 DBA 提前执行，在选择现有业务库后导入，可重复执行（避免与启动迁移同时执行 DDL）。本包不包含整库导入文件，不需要重新建库。

升级后所有旧网页会话失效，重新登录。确认用户改密后旧会话失效、无法创建通配符/多个分组的 API 密钥、真实邮件发送正常。攻击者创建的可疑密钥需单独禁用/轮换，改密不会自动停用合法单分组 API 密钥。

本包基于本次已通过的前端构建和 320 项后端测试产物；文件校验与 JAR 结构已验证。本机未安装 Docker，未做真实容器启动和生产部署验证。
