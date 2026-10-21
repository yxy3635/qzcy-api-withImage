# 2026-10-14 并发流式断连诊断与兼容性缓解（v2）

## 结论与边界

用户确认触发条件是同时开启多个对话；一次一个对话正常。新文件夹本身不是已证实的触发条件。

新增日志证实部分失败发生在后端读取上游响应体时：渠道 19 的 HTTP/2 流被对端重置，渠道 1 的并行请求遭到连接重置。另一些失败发生在向下游写入时。这些情况应分别处理，不能把 `closed` 一概解释为客户端取消，也不能把全部 Broken pipe 解释为 HTTP/2 问题。

v2 将流式 JSON 请求默认指定为 HTTP/1.1，以绕开上游 HTTP/2 多路复用兼容问题。HTTP/1.1 下同时活跃的请求使用不同连接，并没有将请求串行化。它是基于证据的兼容性缓解，尚未证明上游为何重置，也不能保证解决上游自身的账号并发限制、排队、客户端主动取消或只有心跳的生成停滞。

## 证据（北京时间）

来源：`backend-concurrency.log`、`web-concurrency.log`、`relay-user-last-3h.tsv`。日志内容只作诊断数据。

- 后端日志第 146、906、1073、1245 行：12:08:47、12:12:49 的两个请求、12:14:58，均在 `HttpResponseInputStream.read` 失败；对应原因位于第 267、1027、1194、1366 行，均为 `Received RST_STREAM: Internal error`，包含 `Http2Connection.processFrame` 堆栈。预览只有 174 字节的空 `SSE-Keep-Alive` 事件。12:12:49 两条请求在同一毫秒报错；日志没有上游连接标识，不能证明共用同一 TCP 连接。
- 后端第 2535、2672 行：12:25:59.770 两个渠道 1 的流在上游 read 失败；原因分别为第 2655、2792 行的 `SocketException: Connection reset`，此前只捕获 21 和 3 字节心跳。该 Socket 堆栈不足以确认协商协议，也不足以确定重置来自供应商还是中间网络设备。
- 第 2811、2948 行：12:26:13.714 又发生一次请求阶段 Connection reset 和一个流 read 失败。第 2947 行记录供应商范围 `1:20` 熔断 60 秒，随后第 3086–3088 行三次请求被跳过。这是故障后的熔断扩散，不是最初断流原因。
- 第 3235 行：12:30:27 的下游 Broken pipe 已转发 184915 字节；第 3778 行：12:32:41 的下游 Broken pipe 此前只有心跳。堆栈为 Tomcat flush、`SseOutput.emit`，属于后端写下游失败；无法仅凭它确定最终客户端还是外层代理先断开。
- Web 相关流在上述上游错误后结束，已提交响应仍记录 HTTP 200；这不代表正常完成。
- Web 共 100 条 error：60 条 favicon 权限、39 条连接后端被拒、1 条后端提前关闭。后两类集中在 12:05:59–12:06:37 的后端替换／启动窗口；后端 12:06:30 启动、12:06:37 就绪。不能用该窗口内的 502 解释 12:12 或 12:26 的并发失败。日志不说明此次重启的操作来源。未发现后续 OOM、无数据 watchdog 或配置并发闸门超时记录。

用户 109 的 TSV 共 70 条：57 条标记 success（其中 14 条是旧逻辑估算，不能据此认为完成），13 条 failed，失败费用均为零。13 条中的 7 条 closed、3 条 Broken pipe、1 条 Connection reset、2 条上游错误。TSV 的结束落库时间和后端异常时间可能相差不足 1 秒，不能要求毫秒一致。

## 本地修改

生产文件仍只有 `backend/src/main/java/com/qzcy/backend/service/impl/RelayDispatchServiceImpl.java`。

1. 对 `stream:true` 的 JSON 上游请求显式指定 HTTP/1.1；非流式请求保留客户端原来的协商行为。通过 Spring 配置 `relay.stream.http-version=HTTP_2` 可恢复原协议偏好。
2. 流异常增加 `userId`、`tokenId`、`providerId`、供应商名称、实际 `httpVersion`、`stage`、`terminalSeen`。建立请求失败时记录请求的协议偏好。没有增加完整请求体或认证密钥日志。
3. 区分 `upstream_read`、`upstream_parse`、`upstream_completion`、`upstream_close`、`downstream_write`、`downstream_finish`。finish 写失败也标记为下游失败，避免误计上游熔断。
4. 保留 v1 的协议结束、异常零费用、心跳不估算输出与安全 SSE 错误修复。

## 验证

137 项相关测试通过，失败／错误／跳过均为零，其中流式测试 19 项。新增真实本地 HTTP 并发测试同时保持三个流打开，取消其中一个，其余两个继续生成并完成；核对三条连接、无 h2c Upgrade、取消请求失败且零费用、两次成功扣费、在途计数归零，以及上游保持响应体打开时仍及时结束。另验证协议配置覆盖与非流式行为。

本地 HTTP 测试不复现真实供应商的 TLS/HTTP2 重置；没有服务器执行环境或上游测试凭据，线上效果必须复测。没有修改并发上限、熔断阈值或心跳等待策略。测试输出：`backend/stream-concurrency-tests.log`。

## 上传与更新

上传 `imagecreater-stream-fix-20261014-v2.zip` 到服务器 `/tmp`。本包包含 v1 修复，已安装昨晚报表更新的项目，无论是否装过 v1 均可使用。请使用原项目目录、原 Compose 项目名和原部署参数。

```bash
cd /你的/中转项目目录

# 备份原生产源码；如该备份名已存在，换一个文件名。
tar -czf /tmp/imagecreater-before-stream-v2-20261014.tar.gz \
  backend/src/main/java/com/qzcy/backend/service/impl/RelayDispatchServiceImpl.java \
  backend/src/test

unzip -o /tmp/imagecreater-stream-fix-20261014-v2.zip -d .
sha256sum -c STREAM-FIX-V2-MANIFEST.sha256
docker compose config --quiet
docker compose build backend
docker compose up -d --no-deps --wait --wait-timeout 300 backend
docker compose logs --tail=100 backend
```

逐条执行，失败先处理再继续；后端替换会中断已有流，应让测试用户待后端就绪后再重测。原数据库与 Redis 应处于正常运行状态。本包不含环境文件、应用配置、Compose、Nginx、数据库、前端或号池，部署无需修改这些文件。只有后端需要构建。

复测同一客户端、同一模型、同一思考强度、同一密钥下的 1、2、3 个并行对话；比较是否仍仅有心跳、是否成组断流。主动取消一个对话，确认其他对话继续。如果仍失败，导出新 backend 日志；新日志可按 `userId=109` 定位，并观察 `httpVersion=HTTP_1_1` 和 `stage`。只看 HTTP 200 或首段响应时间不足以确认生成成功。

如仅需恢复协议偏好，在原有 `JAVA_TOOL_OPTIONS` 后追加 `-Drelay.stream.http-version=HTTP_2`，用原 Compose 重新创建后端容器；保留已有 JVM 参数。该选项只是恢复协商偏好，不强制所有上游必须支持 HTTP/2。

## 源码回滚

```bash
cd /你的/中转项目目录
# 先移除本包可能新增的测试；备份中若原本有该文件，下一条会恢复。
rm -f backend/src/test/java/com/qzcy/backend/service/impl/RelayStreamCompletionTest.java
tar -xzf /tmp/imagecreater-before-stream-v2-20261014.tar.gz -C .
docker compose build backend
docker compose up -d --no-deps --wait --wait-timeout 300 backend
```

备份若来自未安装 v1 的版本，回滚后也会恢复旧流式处理与计费行为。回滚不修改历史调用或扣款。
