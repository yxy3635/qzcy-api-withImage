# 2026-10-14 流式请求结束与异常计费修复

## 现象与诊断范围

用户确认网页正常，OpenAI 供应商直连上游，仅流式模型调用卡住。截图中的 `/v1/responses` 多次耗时约 341 秒，首段数据约 11 秒，输出只有 6–18 token，记录却显示成功。

本地代码确认以下问题：

- 原流式转发一直读取到 HTTP 响应体 EOF，不识别 Responses 的 `response.completed` 等结束事件。即使模型已发出结束事件，上游响应体仍打开时，中转仍等待并占用请求线程、供应商在途计数和渠道并发许可。
- 原 `catch` 抛出读写异常后，`finally` 仍按上游 HTTP 200 记录成功。没有完整 usage 时还会按请求和前 4096 字节响应预览估算 token 并扣款。
- 原用量解析器超过 1 MiB 就静默清空事件缓冲。Responses 的最终事件可能包含全部输出，此时会丢失结束事件与 usage。
- 控制器会吞掉流式写入异常，以避免响应已提交后的二次错误处理。因此上游断流时需要直接通过 SSE 向客户端发送安全错误事件。

这些代码问题已用本地回归测试验证。随后用户提供了线上日志，截图中的四次请求可与日志对照，具体证据见下一节。截图里的“首字”现有实现实际上是首段响应体到达时间，可能只是心跳，并不一定已经产生模型文本。

## 线上日志确认的实际情况

日志文件：`F:/web/imageCreater/backup/backend-last-3h.log`，时间范围为北京时间 2026-10-14 08:36:39 至 11:35:36。

截图对应的四条请求全部为渠道 1 的 `/v1/responses`。完整响应预览只有 SSE 注释心跳 `:\n\n`，随后在向下游 flush 时抛出 `ClientAbortException: Broken pipe`：

| 后端异常时间 | 日志行号 | 捕获的心跳预览字节数 | 原代码估算输出 token | 截图输出 token |
| --- | --- | --- | --- | --- |
| 10:29:07.219 | 10466 | 63 | ceil(63 / 4) = 16 | 16 |
| 10:34:19.209 | 10929 | 60 | ceil(60 / 4) = 15 | 15 |
| 10:39:31.819 | 11681 | 69 | ceil(69 / 4) = 18 | 18 |
| 10:40:54.678 | 11827 | 21 | ceil(21 / 4) = 6 | 6 |

日志中的 `capturedBytes` 分别是 60、57、66、18，比预览少 3 字节：解析器先捕获当前心跳，转发 flush 失败后还没来得及更新该计数。这也与原代码的执行顺序一致。

这四次实际情况是：上游连接提供保活心跳却没有模型事件与 usage；下游客户端或代理随后关闭连接；旧中转按上游 HTTP 200 记录成功，再将心跳原文和请求文本估算为 token 并扣款。日志没有证明它们是“上游已发 completed，但中转继续等 EOF”；收到结束事件后立即结束的修复仍解决另一种代码中已确认的潜在挂起。

另外，渠道 19 的六次请求只有空文本 `SSE-Keep-Alive` 事件，之后上游 HTTP/2 返回 `Received RST_STREAM: Internal error`。渠道 12 还有两次上游读异常，以及其他下游 Broken pipe。全文件共有 28 次流式读写异常，其中 20 次 Broken pipe、8 次 closed。

当前的空闲 watchdog 按任意响应字节刷新，心跳也会刷新，因此这四条请求不会触发“5 分钟无数据”超时。341 秒结束并不是日志中的 watchdog 杀掉请求；要查明客户端还是哪一层代理先断开，以及上游为什么一直没有模型内容，还需要对应 Nginx access/error 日志和上游同一请求的日志。

本文件中的大量 `No static resource api/v1/sub2api/billing`（57 次）、GET 方法不支持等属于兼容接口缺失或请求方法不匹配，现有统一异常处理又将它们记录为 500 和完整栈；这些记录未构成上述四次流式挂起的直接证据。没有发现 OOM 或该时段容器重启的记录。

## Web 容器日志的进一步证据

用户随后提供 `F:/web/imageCreater/backup/web-last-3h.log`。该文件使用 UTC（`+0000`），与后端业务时间对照需要加 8 小时。全文件 100 条 Nginx error 均为 favicon 文件权限错误，没有 `upstream timed out`、`prematurely closed` 或 502/504 的记录。另有 4 条 499，分别是一个仪表盘请求和三个 Chat 请求，不是上述四条 Responses 请求。

按相同客户端来源、User-Agent、小体积心跳响应与时间顺序关联，四条 Responses 对应 Web 访问记录如下（现有日志没有 request ID，属于交叉证据关联）：

| Web 结束时间（北京时间） | Web 日志行号 | HTTP 状态 | Nginx 发送字节数 | 后端发现 Broken pipe 的时间 |
| --- | --- | --- | --- | --- |
| 10:28:37.339 | 4893 | 200 | 152 | 10:29:07.219 |
| 10:33:49.323 | 5140 | 200 | 144 | 10:34:19.209 |
| 10:39:01.923 | 5421 | 200 | 168 | 10:39:31.819 |
| 10:40:18.946 | 5471 | 200 | 40 | 10:40:54.678 |

这四条 User-Agent 都是 `Codex Desktop/0.162.0-alpha.17.2`。Nginx 的发送字节计数与 Java 捕获的响应体字节计数口径不同，不当作模型文本长度；两者均为极少量心跳数据。

前三条 Web 请求先结束，后端分别晚 29.880、29.886、29.895 秒发现连接已断开。将截图的后端耗时减去该延迟，再减去首段延迟，得到从首段数据到 Web 请求结束约 300.130、300.114、300.105 秒。该规律强烈指向客户端或外层代理的约 300 秒等待限制，而不是后端统计中的完整 341 秒都是模型生成时间。第四条更早结束，不能套用相同 300 秒规律。

已经发送 HTTP 200 与心跳后，连接再中断也可能仍以 200 写访问日志，因此 Web 的 200 不证明 Responses 正常完成。该 Web 日志未记录请求耗时或上游耗时，也没有 INFO 级别的客户端断连说明，不能仅凭它确定究竟是客户端、宝塔反代还是其他前置网关先关闭连接。当前仓库默认 Nginx 的 `proxy_read_timeout` 和 `proxy_send_timeout` 为 600 秒，但服务器更新说明要求保留原 Nginx 文件，因此仍需读取线上生效配置核实。

已确认的修复可以纠正中转记录和计费，不能使只有心跳的上游凭空产生模型输出。后续定位应优先核对实际反代超时/缓冲配置，并对同一上游、相同模型和请求体进行直接流式对照测试；目前没有相关授权凭据或服务器执行环境，尚未执行该对照。

## 修复后的行为

- Responses 在完整 `response.completed`、`response.failed`、`response.incomplete` 或错误事件后结束转发；Messages 在 `message_stop` 后结束；Chat 在 `[DONE]` 后结束。Chat 的 `finish_reason` 不提前截断，保留其后的 usage 帧。
- 结束事件支持网络拆包、UTF-8 拆包和 CRLF 分隔，终止后不再转发同一网络块中的多余数据。
- 需要结束事件的接口遇到提前 EOF、只返回 Chat `[DONE]` 的 Responses、上游读异常或客户端写失败时，记录失败并停止扣款；上游已提供的 usage 仍保留供排查。
- 上游读异常、提前 EOF 或空闲超时，且客户端仍可写时，发送经过脱敏的 SSE 错误事件，不暴露上游地址或原始异常细节。
- SSE 心跳与协议元数据不再作为输出文本参与 token 估算，即使单独调用估算逻辑也不会把心跳算成模型输出。
- `response.incomplete` 保留原始客户端事件，在本地记为失败、费用为零。渠道成功状态在流正常完成时更新；客户端写失败不计入上游渠道熔断。
- 事件大小上限与现有 SSE 转发器对齐为 32 MiB，超过上限明确失败，不再静默丢弃。
- 保留原来 5 分钟的无数据超时，避免用随意缩短超时的方式打断正常长思考请求。上游一直发送心跳却没有协议结束事件的情况仍需结合上游日志处理。

## 验证

135 项相关测试全部通过，覆盖流式终止、异常隐私、OpenAI/Claude 缓存计费、渠道选择与接口路径。新增 17 项流式回归用例中，真实 HTTP 测试让上游发出 `response.completed` 后保持响应体打开，中转在 3 秒测试期限内完成，无需等上游关闭；其余用例覆盖大于 1 MiB 的结束事件、拆包、提前 EOF、客户端断开、错误事件和计费。根据线上日志又补充了四种心跳字节数与 Broken pipe 的回归用例，核对失败状态、输出 token 为零、费用为零。

原缓存计费测试中的模拟流调整为协议有效的序列：Responses 的中间 usage 使用 `response.in_progress`，最后只发一次 `response.completed`；Messages 追加 `message_stop`。原有费用、缓存明细和一次扣款断言继续通过。

Windows 本地验证命令（在 `backend` 目录）：

```powershell
.\mvnw.cmd '-Dtest=RelayStreamCompletionTest,RelayDispatchServiceImplTest,UpstreamErrorPrivacyTest,RelayClaudeCacheBillingTest,RelayBillingCompatibilityTest,RelayControllerProtocolTest' test
```

## 更新服务器

本次生产逻辑修改位于 `backend/src/main/java/com/qzcy/backend/service/impl/RelayDispatchServiceImpl.java`，基于当前含 10 月 13 日报表字段的完整源码。服务器应使用与其匹配的完整后端源码；单独复制到更旧版本需先核对 `billing_group_code` 及缓存 TTL 字段依赖。

将修复源码同步到服务器原项目目录，沿用原 Compose 项目名、环境文件和部署参数，然后只重新构建与更新后端：

```bash
docker compose build backend
docker compose up -d --no-deps --wait --wait-timeout 300 backend
docker compose logs --tail=100 backend
```

本次不需要重建前端。上述命令尚未在服务器执行。

更新后用相同上游复测 `/v1/responses` 的普通文本与工具调用流，检查模型结束时客户端是否立即结束、调用记录是否及时落库。若仍卡住，取对应 backend 日志，重点查 `Upstream SSE ended before a terminal event`、`Relay downstream stream write/read failed`、`Relay upstream stream closed by idle watchdog`；日志若显示没有结束事件，需继续检查上游与服务器实际反向代理配置。

新逻辑不会改写已产生的历史调用记录与费用。
