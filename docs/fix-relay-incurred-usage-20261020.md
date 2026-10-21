# 2026-10-20 中断请求的用量结算与安全转发

## v2 管理员启动初始化修复

启动时先按 ADMIN 角色检查用户表，只要存在管理员（无论用户名是否为 admin、是否被禁用），直接跳过初始密码校验与默认 admin 创建。不会修改已有管理员的密码或资料，也不会自动删除此前创建的多余 admin。

没有管理员时，保留原有默认用户名占用检查：若 admin 已被普通用户占用，跳过创建，不覆盖或提升该账号权限。没有管理员且默认用户名未占用时，使用配置的初始密码创建 admin，密码仍须满足原有规则。

回归测试使用 H2 MySQL 模式验证改名、禁用的管理员在初始密码为空或不合规则时仍可跳过初始化，并验证普通用户占用 admin 时不会被覆盖或提升权限。v2 包含前一版全部计费及安全转发修复，原版更新包保留。

## 本次行为

- 调用成功与计费分别判断。上游已报告真实正数用量时，即使上游错误、提前 EOF、读取失败或下游断开，也按本站配置的模型、缓存、分组及渠道价格结算，失败状态保留。
- 对已报告用量的 JSON 错误停止自动切换备用渠道，避免同一次调用额外产生一笔上游费用。无用量的错误保留原重试规则。
- 客户端断开后不再写入坏连接，但继续读取原来的上游请求，获取末尾用量；默认最多继续 10 分钟，原 5 分钟无数据超时同样生效。没有重新请求上游；会保留线程及渠道并发名额直到完成或超时。
- 正常 SSE 事件逐事件检查后转发；中断前已经转发的内容不会撤回。最后一个事件只有分隔符缺失但 JSON 完整时，也尽量发送；无法解析的半截 JSON 不原样发送。
- 上游错误重新构造为本站已有的安全错误格式。Responses 的 failed/incomplete 保留生成内容及用量，错误文字、网址和供应商诊断不透传；正常模型内容中的引用链接、工具参数保留。
- 过滤 codex.* 上游账号/会话元数据事件，以及已知的供应商地址、凭据、响应头等诊断字段。模型生成的正常文本不按网址一刀切删除。
- 中转已发生费用使用原子扣减；并发用尽余额时允许余额为负。后续调用受现有余额检查拦截。结算后发现超出密钥配额时停用该密钥，但仍结算已发生费用。图像等原有预付费扣款仍保持余额不足拒绝规则。
- 下游连接异常在管理日志中标记 downstream_error，避免统一误标为 upstream_error。

## 必须说明的边界

“上游已扣费”目前以中转收到的实际 usage 为依据，并使用本站价格结算，金额不等于供应商账单原价。没有增加供应商账单查询接口。

如果上游收取费用却未返回 usage，或上游用量返回前网络已断开，本地无法据此知道应扣多少。此类流式异常保留 billing_status=unconfirmed 并记录待对账告警，不能把费用暂为 0 解释成供应商未计费；需要供应商请求记录或账单接口进一步对账。本次不会猜测中断请求的 token 用量扣款，也不会自动追扣历史记录。

客户端已经关闭连接后无法再把内容发送给它。继续读取只用于获取用量，不提供断线重连后的内容补发或持久化会话恢复。

成功但没有 usage 的响应仍沿用项目已有估算逻辑。失败且缺少真实用量不会使用这类估算。

进程被强制终止、数据库无法写入等故障仍可能影响结算；本次没有引入独立持久化账单队列或跨系统账单对账。

## 验证

新增回归覆盖：下游断开后收到完成用量、真实 HTTP 并发流相互独立、限时排空、错误响应含用量时只扣一次且不切换渠道、固定价格错误请求带用量计费、超配额后仍结算、零用量错误不计费、部分内容保留、拆包错误脱敏、账号元数据过滤、正常引用链接与工具参数保留。

并发扣费通过 H2 MySQL 模式实际运行 UserMapper SQL 验证：余额 1 同时扣 0.8 两次，最终 -0.6，两笔均落账；普通预付费扣减仍拒绝余额不足。

构建及测试使用 Java 17。完整应用启动测试 BackendApplicationTests、RelayTokenDeletionIntegrationTest 因本地未配置有效 JWT_SECRET 无法启动，打包验证明确排除；依赖专用 MySQL 测试环境的用例自动跳过。准确测试统计见更新包 BUILD-INFO.json。本机未运行 Docker 或部署到生产。

## 后端更新包

包名 imagecreater-backend-billing-20261020-v2.zip，直接解压到项目 backend 目录。内含 app.jar、运行用 Dockerfile、healthcheck.sh、本文及校验清单。不是源码补丁，不含前端、Nginx、Compose、.env 或数据库数据。JAR 基于当前工作区（包含此前已有更新），打包时移除 dev/prod 本地环境文件，保留 docker profile。

本次更新不需要手动执行 SQL；数据库结构初始化仍沿用项目原有逻辑，管理员初始化按上述 v2 规则执行。

以下在原服务器项目目录执行，保留原有 -p、--env-file、-f 等 Compose 参数。逐条执行，失败先处理。

```bash
cd /www/wwwroot/imageCreate

# 将 ZIP 与同名 .sha256 文件上传到 /tmp 后验证。
(cd /tmp && sha256sum -c imagecreater-backend-billing-20261020-v2.zip.sha256)

# 在覆盖前保留原后端，用于回滚。
tar -czf "/tmp/imagecreater-backend-before-billing-$(date +%Y%m%d-%H%M%S).tar.gz" backend

unzip -o /tmp/imagecreater-backend-billing-20261020-v2.zip -d ./backend
(cd backend && sha256sum -c BILLING-20261020-MANIFEST.sha256)
docker compose config --quiet
```

如果原 backend 服务带 build 配置（源码/运行镜像构建方式）：

```bash
docker compose build backend
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend
docker compose logs --since=10m backend
```

包内 Dockerfile 只将预编译 JAR 放入 Java 17 运行镜像，不在服务器编译 Java。若原 Dockerfile 有自定义设置，先核对备份。

如果原服务绑定挂载 ./backend/app.jar，使用原预构建镜像方式：

```bash
docker compose up -d --no-deps --force-recreate --wait --wait-timeout 300 backend
docker compose logs --since=10m backend
```

不要只 restart：覆盖 JAR 后须重建容器，确保绑定挂载或镜像使用新文件。无需更新 web。替换后端会中断在途请求，先让在途请求完成再更新。

继续读取用量的最长等待可通过现有 JAVA_TOOL_OPTIONS 追加 -Drelay.stream.billing-drain-timeout-ms=600000 调整，保留已有 JVM 参数。这个设置不是自动重试次数，也不是对所有正常请求强制 10 分钟结束。

## 上线验收

先用一个测试用户验证：正常完成扣一次；生成一段内容后取消，等待上游结束后确认失败记录仍保留实际 tokens/cost；无用量的纯错误返回安全错误且不估算扣款；余额不足以覆盖并发最终费用时结算欠费、后续请求拒绝。请核对供应商同一请求的实际 usage，不能仅按上游 HTTP 200 判断已计费。

只观察服务器日志可搜索：

```bash
docker compose logs --since=20m backend 2>&1 | grep -E 'draining upstream for billing|billing requires reconciliation|stream write/read failed'
```

回滚时恢复之前备份的 backend，再按原部署方式重新构建/创建后端。回滚程序不会撤销已结算费用；历史账务需单独核对，不能通过修改调用状态代替退款或补扣。
