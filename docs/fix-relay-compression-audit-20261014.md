# 2026-10-14 gzip/zstd 更新与并发卡住的复核

## 当前结论

用户补充：故障开始于两种请求压缩格式上线之后。用户同时确认，上一轮 HTTP/1.1 兼容修复 v2 只存在本地，尚未在服务器部署。

本次对照压缩更新提交、实际备份 ZIP、两份后端日志，并补充多个压缩请求保持 SSE 打开的验证。没有发现解压槽位被长期占用或请求内容交叉污染。日志中的代表性并发故障请求走的是未压缩/identity 分支，根本没有进入解压或申请解压预算。

部分故障可以确定发生在后端读取上游时：HTTP/2 流重置和连接重置。另有下游写入失败，不能把全部故障解释为同一种原因。尚未证明为什么故障在那次部署后才出现，也未证明切换 HTTP/1.1 能消除所有故障。

## 更新内容核对

- 提交 `649db00`，提交时间 2026-10-12 00:27:18 +08:00，新增 gzip/zstd 请求解压、解压预算、配置接口及兼容路径鉴权。服务器实际部署时间不能从 Git 提交时间推定。
- 备份包 `F:/web/imageCreater/backup/imagecreater-compression-update-20261011.zip` 中，五个压缩生产类与检查时的本地文件逐字节一致，包括 `RelayCompressionBodyAdvice`。不能据此保证服务器当前镜像一定包含相同源码，但异常堆栈行号与该版本吻合。
- 该提交和该 ZIP 没有更改 `RelayDispatchServiceImpl`、上游 HTTP 协议选择、渠道流式并发闸门或上游压缩方式。
- 当时的上游 Java HttpClient 与压缩更新前一样，未明确限制 HTTP 版本，默认优先 HTTP/2。v2 才对 `stream:true` 请求显式选择 HTTP/1.1。
- 解压后的 JSON 会重新序列化再发往上游，没有把客户端的 `Content-Encoding` 转发到上游。

## 故障请求没有进入解压的证据

`RelayCompressionFilter.java` 第 51–55 行：缺少 `Content-Encoding` 或为 `identity` 时，第 53 行直接 `chain.doFilter(request, response)` 并返回。只有第 75 行才会传入解压请求包装器。

`backend-concurrency.log` 中的相关异常堆栈在第 182、942、1109、1281、2570、2707、2845、2983、3274、3817 等行均显示 `RelayCompressionFilter.java:53`。该文件所有 13 次出现此堆栈帧的行都指向 53，没有指向 75。旧 `backend-last-3h.log` 的对应堆栈也指向 53。

代表性的上游失败：

| 北京时间 | 异常 | 对应解压路径 |
| --- | --- | --- |
| 12:12:49.203，两次请求同时报错 | 上游 read，`Received RST_STREAM: Internal error`，HTTP/2 堆栈 | 未压缩/identity，直接放行 |
| 12:25:59.770，两次请求同时报错 | 上游 read，`SocketException: Connection reset`；此堆栈不能确定协议或重置来源 | 未压缩/identity，直接放行 |
| 12:26:13.714 | 请求建立及另一条流 read 的连接重置，随后供应商范围熔断 60 秒 | 未压缩/identity，直接放行 |

这些失败不是 gzip/zstd 解码器卡住或解压预算被流式响应占用的直接结果。时间先后关系仍值得关注，但不等于证明压缩实现导致故障。

## 复核测试

在 `RelayCompressionTest` 新增三种输入组合：全 gzip、全 zstd、交替混合。

每种组合把预算降到 32 MB，即只有一个解压槽位；依次让 6 个请求完成解析并进入流式写入，保留全部响应打开。这样仍然同时存在 6 个长流，避免把合法的同时解码预算拒绝与流生命周期泄漏混淆。每个请求包含独立编号和约 360 KB 中文输入，覆盖多个解压分块。

验证进入上游及 SSE 写入时预算均为零，6 条响应同时保持打开、没有串行等待其他流完成，请求 JSON 与响应 UTF-8 字节分别保持完整且互不污染，最终资源归零。真实上游被模拟；本测试覆盖过滤器、JSON 解析、BodyAdvice、真实 RelayController 的流式写入路径，不验证生产 Nginx、TLS 或供应商行为。

```powershell
cd F:\web\imageCreater\imageCreater\backend
.\mvnw.cmd '-Dtest=RelayCompressionTest,RelayCompressionSecurityTest' test
```

结果：17 项测试通过，失败、错误、跳过均为零。输出在 `backend/compression-concurrency-check.log`。本次只新增回归测试和本报告，未追加生产代码修改。

## 下一步

部署此前已有的 `imagecreater-stream-fix-20261014-v2.zip`，按其 README 备份源码、校验 manifest、重新构建并替换 backend，等待就绪后再测试。源码仅在本地或服务器解压但未重建镜像，均不会改变正在运行的容器。

使用同一客户端、供应商、模型、思考强度和 API Key，分别测试一个、两个、三个同时对话，并取消其中一个观察其他对话。重点核对 v2 新日志中的 `userId`、`httpVersion`、`stage`。如果仍失败，保留完整异常堆栈，区分实际 HTTP/1.1 下的上游连接重置、只有心跳的等待和下游主动断开，再决定下一项修复。无需据当前证据修改解压预算或外层 Nginx HTTP/2 设置。

现有 v2 ZIP 内容和校验值保持原样。本次新增测试不在该包内，不影响其中的生产修复。
