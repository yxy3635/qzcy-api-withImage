# ZCode / DSH 实际客户端排查（2026-10-18）

接口：`https://image.qzcy3.top/api/v1`；模型：`deepseek-v4.1-flash`。测试密钥、原始提示词、工具参数没有写入本报告或更新包。

## ZCode Responses

实际客户端日志 `C:/Users/HP/.zcode/cli/log/zcode-2026-10-18.jsonl` 中，截图对应会话在北京时间 00:52–00:58 多次出现 `Connect Timeout Error (image.qzcy3.top:443, timeout: 10000ms)`，随后出现 `read ECONNRESET`。这些尝试没有收到正文和思考增量；早先 Messages 会话则收到过 39 个思考增量，之后被用户停止。

服务器某条 HTTP 200 记录不能证明上述客户端尝试已收到响应。没有共同请求 ID，不能把时间接近的两端记录直接认定为同一次请求。

读取客户端留存的 Responses 原始请求体在内存中回放，保持 8 个 input、35 个工具、`reasoning.effort=max`、`max_output_tokens=384000`，不执行返回的工具调用：

| 回放 | 总时长 | 思考字符 | 正文字符 | 输入 tokens | 缓存 tokens | 结束事件 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 4.609 秒 | 479 | 137 | 23,503 | 23,296 | response.completed |
| 2 | 6.031 秒 | 879 | 28 | 23,503 | 23,296 | response.completed |

缓存命中约 99.1%。这证明当前部署的 Responses 能报告缓存，但不能反推截图中那次请求必然应当命中；缓存由上游实际返回，仍会受输入前缀、时效、路由等条件影响。

进一步使用与 ZCode 相同版本的 `@ai-sdk/openai@3.0.65`，运行于 Node 24.19.0：

- 默认 fetch 复现 10.689 秒连接超时，`UND_ERR_CONNECT_TIMEOUT`。
- 仅启用 Node 的环境代理机制时，仍出现连接中断；不作为修复方案。
- 显式 HTTPS 代理传输（与 ZCode 自带代理传输使用的方式一致）在 6.243 秒内解析完成：72 个 reasoning-delta、48 个 text-delta、1 个 finish；思考 298 字符、正文 91 字符；缓存 23,296 / 23,503。
- 没有出现 SDK schema 校验错误。这是原始请求和同版本解析器的验证，尚不等于重启后 ZCode 界面已确认恢复。

ZCode 的模型传输支持 `network.httpProxy` / `ZCODE_HTTP_PROXY`；其桌面配置原先没有 `httpProxy`。已在本机 `C:/Users/HP/.zcode/v2/setting.json` 写入现有 Windows 代理 `http://127.0.0.1:7897`，只修改 ZCode 设置。原代理字段状态保存在 `C:/Users/HP/.codex/tmp/zcode-proxy-setting-before-20261018.json`，不包含其他设置和凭据。请保持代理程序运行，完全退出 ZCode（包括托盘）后重新打开验证。运行中的进程可能在退出时覆盖设置，如发生此情况，可在 ZCode 的代理设置中重新填写该地址。

## DSH Messages

检查了 `session-98091666-9c44-4d85-92df-80c947cc99e5` 的本地压缩会话事件。该文件在使用中持续增长；本次读取快照包含 35 条 assistant/message，其中 29 条有 reasoning，累计 66,901 个思考字符。01:06:44 那轮有 4,882 个思考字符，缓存读取 46,592 tokens；01:07:24 那轮有 7,418 个思考字符，缓存读取 49,024 tokens。只记录长度和用量，没有导出正文。

因此 DSH 已实际收到并保存思考和缓存。截图顶部也存在“思考”行。若指某一轮 UI 没展开，需要区分思考块显示、模型当轮是否生成思考，以及中转日志中的请求档位标签。

DSH 的 DeepSeek Messages 适配器以 `thinking.type=enabled` 和 `output_config.effort=max` 发送思考控制。中转原来的 `extractThinkingEffort` 漏读 `output_config.effort`，使日志中的思考档位为空，造成“没有思考”的误解。

已修复中转 Java 后端：读取 `output_config.effort`；只有开关时记录 enabled/adaptive；明确 disabled 或 `enable_thinking=false` 优先记录 none。标签描述请求设置，不保证当轮一定生成思考正文。旧日志不会自动回填。

## 验证与部署

`RelayDispatchServiceImplTest` 19 项、`RelayBillingCompatibilityTest` 61 项、`RelayClaudeCacheBillingTest` 12 项全部通过（合计 92 项）。后端重新构建为 `backend/target/backend-0.0.1-SNAPSHOT.jar`。

JAR 基于当前本地源码构建。此次服务器端新增改动仅涉及思考档位日志显示，部署新后端后对新请求生效。此前号池的思考控制、Messages 缓存修复继续保留；本次未新增号池代码补丁。ZCode 网络修复属于本机设置，不需要为此重新部署号池。
