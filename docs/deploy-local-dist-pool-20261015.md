# 后端合并更新、本地构建前端、同机号池直连

本指南适用于 Linux 宝塔服务器：中转使用现有 Docker Compose，号池在同一宿主机通过 Python 运行。号池示例端口为 8787，实际端口不同时替换下文的 8787。

## 本次上传文件

- `imagecreater-backend-20261015.zip`：后端源码增量包，合并 10 月 14 日 stream-fix-v2 和普通接口上下文压缩识别。没有前端源码、dist、Compose、环境密钥、号池数据或数据库数据。
- `imagecreater-frontend-dist-20261015.zip`：本地执行 `npm run build` 后的静态文件，解压后有一层 `dist/`。
- `compose.local.yaml`：额外的 Compose 配置，只增加前端静态文件挂载和后端访问宿主机的地址映射。
- 上述 ZIP 各自对应的 `.sha256` 校验文件。

合并包基于 `imagecreater-update-direct-20261013-report-speed.zip`，其 SHA-256 是 `202a2333698635c2a822fe3da9d3c1f77fde5fbfaef0574b7eb17773cce861d5`。若服务器已部署此版，可以直接用合并包，无需先安装旧 stream-fix-v2。更旧或自改版本需先对比源码。

## 一、上传与备份

把上述文件通过宝塔上传到服务器 `/tmp/`。不要把本机 `F:\...` 当作服务器目录。

进入现有中转项目目录，也就是当前 `compose.yaml`、`.env` 所在位置：

```bash
cd /你的/中转项目目录
docker compose ps
```

确认看到现有 web/backend/mysql/redis。以下命令使用默认 `compose.yaml`；如果原部署使用特定 `-p`、`--env-file` 或其他 `-f`，后续命令必须保留那些参数，再追加 `-f compose.local.yaml`。不要修改现有 Compose 项目名。

先校验上传包，然后备份待覆盖的源码和配置。逐条执行，上一条失败时先处理：

```bash
cd /tmp
sha256sum -c imagecreater-backend-20261015.zip.sha256
sha256sum -c imagecreater-frontend-dist-20261015.zip.sha256
cd /你的/中转项目目录

tar -czf /tmp/imagecreater-source-before-20261015.tar.gz \
  backend/src compose.yaml deploy/nginx.conf
```

若备份文件已存在，请换一个名字；另确认已有可恢复的数据库备份。本次启动会增加可空字段，正常情况下无需手工导入 SQL。

如果原目录已有 `dist`，先备份：

```bash
tar -czf /tmp/imagecreater-dist-before-20261015.tar.gz dist
```

原目录没有 `dist` 时跳过这一条。已有 `compose.local.yaml` 时先备份并合并下节配置，不要直接覆盖。

## 二、准备本地构建的前端

本次已经附上当前本地构建结果；使用附带的前端 ZIP 时，服务器无需 Node/npm，也无需构建前端镜像。

在服务器项目根目录解压到暂存目录：

```bash
unzip /tmp/imagecreater-frontend-dist-20261015.zip -d frontend-stage-20261015
test -f frontend-stage-20261015/dist/index.html
mkdir -p dist/assets
cp -a frontend-stage-20261015/dist/assets/. dist/assets/
cp -a frontend-stage-20261015/dist/. dist/
find dist -type d -exec chmod 755 {} +
find dist -type f -exec chmod 644 {} +
test -f dist/index.html
```

先复制 assets，再更新 index，保留原 dist 中的旧 assets，减少正在访问的用户找不到旧资源的情况。首次挂载生效后，后续复制 dist 就会直接对访问者生效；可选择访问较少时更新。

## 三、合并前端挂载和号池直连配置

将 `/tmp/compose.local.yaml` 复制到项目根目录（如已有同名文件，合并，勿覆盖）：

```bash
cp /tmp/compose.local.yaml ./compose.local.yaml
```

文件内容如下，是追加配置，不是完整 Compose，不能单独启动：

```yaml
services:
  web:
    volumes:
      - ./dist:/usr/share/nginx/html:ro
  backend:
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

原 Compose 的端口、数据库、Redis、图片卷、环境变量、健康检查仍由 `compose.yaml` 提供。额外文件不会将后端切换到 host 网络。

校验两份配置可以合并：

```bash
docker compose -f compose.yaml -f compose.local.yaml config --quiet
```

以后管理这套服务时继续带上这两份 `-f`。若只使用原 Compose 重建容器，可能移除本次增加的挂载或宿主机映射。普通 `docker compose restart` 也不能加载新的镜像或配置。

## 四、只构建与更新后端

在服务器原项目根目录解压合并后端包：

```bash
unzip -o /tmp/imagecreater-backend-20261015.zip -d .
sha256sum -c BACKEND-20261015-MANIFEST.sha256
docker compose -f compose.yaml -f compose.local.yaml build backend
docker compose -f compose.yaml -f compose.local.yaml up -d --no-deps --wait --wait-timeout 300 backend
docker compose -f compose.yaml -f compose.local.yaml logs --tail=100 backend
```

这个步骤只构建后端。数据库和 Redis 保持原服务运行；后端容器替换会中断当前正在进行的流式请求。后端启动检查并补齐 `context_compaction` 字段，不要重新导入整份 `schema.sql`，也不要删除数据卷。

`--wait` 成功后再继续。若超时，先检查后端日志，不要直接切换供应商地址。

## 五、启动使用 dist 的前端

服务器已经部署过前端，使用现有 web 镜像即可：

```bash
docker compose -f compose.yaml -f compose.local.yaml up -d --no-deps --no-build --force-recreate --wait --wait-timeout 120 web
docker compose -f compose.yaml -f compose.local.yaml exec -T web sh -c 'test -f /usr/share/nginx/html/index.html'
docker compose -f compose.yaml -f compose.local.yaml exec -T web wget -qO- http://127.0.0.1/healthz
docker compose -f compose.yaml -f compose.local.yaml ps
```

`--no-build` 跳过服务器前端构建。挂载替换网页静态目录；已有 web 镜像中的 Nginx 配置继续提供 `/api/` 代理、页面刷新和 SSE 支持。本方案要求原 web 镜像已经存在，且使用项目原 Nginx 配置。

浏览器强制刷新，检查登录、使用记录和管理员日志。需要压缩识别的请求必须实际匹配当前总结指令或上游压缩块；旧普通接口记录不会自动补标。

## 六、确认号池可被 Docker 访问

在服务器宿主机检查 Python 号池实际监听端口：

```bash
ss -lntp '( sport = :8787 )'
curl -fsS --connect-timeout 5 --max-time 10 http://127.0.0.1:8787/health
```

如果仅监听 `127.0.0.1:8787`，容器通过宿主机网关不能连接。号池需要监听 Docker 可访问的宿主机地址；常用配置是 `0.0.0.0:8787`，并通过宝塔/系统防火墙只允许本机和中转 Docker 网络访问，公网不开放 8787。如果此前有对公网允许此端口的规则，需要收紧，云安全组继续关闭此端口。

检查的本地号池 `python3 -m admin.server` 默认监听 `0.0.0.0:8787`。如果线上也是此入口，通常无需修改 Python。保持当前工作目录、环境变量、API Key 和数据路径；不要为了改监听切换成 `core.converter`，那样会绕过现有账号池管理层。请以宝塔实际启动命令和 `ss` 输出为准。

查看后端所属 Docker 网络，以便在防火墙里允许实际网段（不要直接假定是 172.17.0.0/16）：

```bash
docker inspect "$(docker compose -f compose.yaml -f compose.local.yaml ps -q backend)" --format '{{json .NetworkSettings.Networks}}'
docker network inspect 这里替换为上一步的网络名 --format '{{json .IPAM.Config}}'
```

然后从中转后端容器内测试：

```bash
docker compose -f compose.yaml -f compose.local.yaml exec -T backend \
  curl -fsS --connect-timeout 5 --max-time 10 http://host.docker.internal:8787/health
```

成功返回健康结果后再修改中转后台。如果无法解析名字，检查是否已用两份 Compose 重建 backend；连接拒绝通常检查监听地址/端口；超时通常检查防火墙/网段。需要 Docker Engine 支持 `host-gateway`（20.10+）。

## 七、切换中转后台上游地址

编辑指向这个号池的渠道供应商，将 OpenAI/Anthropic 上游地址改为：

```text
http://host.docker.internal:8787/v1
```

OpenAI、Messages 都由此号池提供时，两处填写相同地址。填基础地址，不要追加 `/chat/completions` 或 `/messages`；API Key 使用号池现有客户端 Key。其他外部供应商的域名地址不变。

这里使用本机 HTTP，不经过宝塔 HTTPS 域名、CDN 和公网转发。号池管理后台仍可沿用原 HTTPS 域名，保持管理登录正常。

测试模型列表、一个普通对话、一个流式对话，再测试 2–3 个并发对话和取消其中一个。内网直连减少中转与号池之间的外部转发；号池访问真正上游及用户访问中转仍有公网流量。

## 八、以后只更新前端

本地 Windows 项目根目录执行：

```powershell
npm run build
```

将生成的 dist 内容上传到服务器项目的 dist 中，先上传新的 assets，最后更新 index.html，保留暂时仍可能被浏览器引用的旧 assets。只改静态文件时，Nginx 无需重启，也无需重建 web 镜像。如果修改了 Compose 挂载配置，则重新执行第五节的 web 重建容器命令。

## 回退

号池直连不通时，先将该供应商上游地址改回原 HTTPS 域名即可恢复原路径，无需改数据库。

前端若需回退，将之前备份的 dist 恢复到当前挂载目录；首次引入挂载前的前端则在 `compose.local.yaml` 移除 web 的 volumes，并使用两份配置按第五节命令重建 web 容器，恢复原镜像自带页面。

后端若需回退，先检查备份内容，再恢复原源码；恢复时需移除本包新增的 `backend/src/main/java/com/qzcy/backend/util/RelayRequestKind.java`、`backend/src/test/java/com/qzcy/backend/util/RelayRequestKindTest.java`，以及备份原本不存在时的 `RelayStreamCompletionTest.java`。然后解压源码备份，重新 build/up backend。新增可空数据库字段可保留，回退不需要删除它。后端回退也会撤回相应修复，新增压缩标识可能不再出现。

本地已验证源码覆盖与构建；尚未连接你的服务器执行部署，线上端口、防火墙及运行状态需按本指南验证。
