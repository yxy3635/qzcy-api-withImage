# Thinking / reasoning 透传排查（2026-10-18）

接口：`https://image.qzcy3.top/api/v1`；模型：`deepseek-v4.1-flash`。
使用用户授权的临时测试密钥，密钥未写入报告或项目文件。

## 结论

线上 Chat 和 Responses 在明确开启 reasoning 后，流式、非流式都能返回独立的思考内容。本次复现的缺失发生在思考开启参数没有被传递或转换时。

- Chat 的默认请求没有独立思考；仅添加 `thinking: {"type":"enabled"}` 也没有独立思考。
- Chat 添加 `reasoning_effort: "high"` 后返回 `reasoning_content`，且 usage 中的 reasoning_tokens 大于零。
- Responses 添加 `reasoning: {"effort":"high","summary":"auto"}` 后返回 reasoning summary 流事件及最终 reasoning item。
- Messages 的 `thinking.type=enabled` 和 `thinking.type=adaptive` / `output_config.effort=high` 均未开启独立思考。

## 线上验证

以下八组为同一道同余方程题，全部 HTTP 200，并正常结束。字符数是思考字符串长度，不是 token 数；未存储思考正文。

| 接口 | 模式 | 开启参数 | 思考字符数 |
| --- | --- | --- | ---: |
| Chat | SSE | 无 | 0 |
| Chat | SSE | `thinking.type=enabled` | 0 |
| Chat | SSE | `reasoning_effort=high` | 726 |
| Responses | SSE | `reasoning.effort=high, summary=auto` | 899 |
| Messages | SSE | `thinking.type=enabled, budget_tokens=1024` | 0 |
| Messages | SSE | `thinking.type=adaptive, output_config.effort=high` | 0 |
| Chat | JSON | `reasoning_effort=high` | 651 |
| Responses | JSON | `reasoning.effort=high, summary=auto` | 644 |

原始统计见同目录 `relay-reasoning-live-20261018.json`。此前第一轮四组对照也得到同样方向的结果。

## 源码定位

### 中转项目 imageCreater

- `backend/src/main/java/com/qzcy/backend/service/impl/RelayDispatchServiceImpl.java:820` 的 `prepareOutboundBody` 对请求做 deepCopy，仅处理模型映射和 Chat 的 include_usage，没有移除 thinking/reasoning 参数。
- `backend/src/main/java/com/qzcy/backend/service/UpstreamErrors.java:171` 将正常 SSE 事件按原始字节写回；错误事件才重建。
- 线上成功返回独立思考的对照请求与上述行为一致，未发现中转层删除正常 reasoning 字段的证据。

### 上游号池 F:/web/workbuddy2api/workbuddy2api

- `core/converter.py:393` 的 `PASSTHROUGH_BODY_KEYS` 包含 reasoning_effort 和 reasoning_summary，但没有 thinking、enable_thinking 或 reasoning 对象。
- `core/converter.py:559` 按白名单重建 Chat 请求。因此客户端即使发送 thinking，也不会被当前本地代码送到腾讯后端，且没有将其转换为已验证有效的 reasoning_effort。
- `core/anthropic_adapter.py:31` 的 `anthropic_request_to_chat` 没有将 thinking、budget_tokens、output_config.effort 转为上游思考控制参数；额外传入 reasoning_effort 同样未保留。直接执行本地转换函数已复现这些参数从上游请求体消失。
- `core/responses_adapter.py:78` 已将 reasoning.effort 映射到 reasoning_effort，这解释了 Responses 对照请求成功。
- `core/reasoning.py:86` 暂存 reasoning_content，在遇到正文、工具、结束或 EOF 时发出。这会影响纯思考流的实时性，但不能解释本次“默认为零、high 非零”的差异。线上返回的 delta 还带有值为 null 的 tool_calls 等字段，会触发当前实现提前 flush，因此本次 Chat high 测试实际收到了多段思考，第一段早于正文。

## 当前可用的请求方式

Chat：向 `/api/v1/chat/completions` 发送：

```json
{
  "model": "deepseek-v4.1-flash",
  "messages": [{"role": "user", "content": "请解答这个问题"}],
  "stream": true,
  "reasoning_effort": "high"
}
```

读取 `choices[].delta.reasoning_content`；非流式读取 `choices[].message.reasoning_content`。

Responses：向 `/api/v1/responses` 发送：

```json
{
  "model": "deepseek-v4.1-flash",
  "input": "请解答这个问题",
  "stream": true,
  "reasoning": {"effort": "high", "summary": "auto"}
}
```

读取 `response.reasoning_summary_text.delta`；非流式读取 `output` 中 type 为 reasoning 的 summary。

Messages 要支持当前上游，需在号池增加思考控制参数到上游 reasoning_effort 的映射。应明确 enabled/adaptive/disabled 及显式 effort 的优先级；budget_tokens 没有证据可按固定比例换算为 effort，不能宣称实现精确预算。

## 范围

本次仅诊断、测试并新增报告，没有修改业务代码或部署线上服务。未取得用户原客户端的实际请求体，也未读取服务器部署文件或上游原始腾讯响应，因此无法确认历史上从哪个版本开始变化，或保证本地源码与线上版本逐字一致。
