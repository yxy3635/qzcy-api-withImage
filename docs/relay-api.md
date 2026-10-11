# 中转请求压缩与余额查询

Base URL 为 `https://你的域名/api/v1`，认证使用中转 API Key。在线文档：`/docs?type=relay`。

## gzip / zstd 请求解压

支持以下 POST 接口的流式和非流式 JSON 请求：

- `/api/v1/chat/completions`
- `/api/v1/responses`、`/api/v1/responses/compact`
- `/api/v1/messages`、`/api/v1/messages/count_tokens`
- Responses 和 Messages 已有的 `/api/...`、`/api/v1/v1/...` 兼容别名。

先压缩 UTF-8 JSON，再发送压缩字节。保留 `Content-Type: application/json`，加上 `Content-Encoding: gzip` 或 `Content-Encoding: zstd`。`Accept-Encoding` / `curl --compressed` 是响应压缩协商，不能代替请求体压缩。

```bash
# Bash；request.json 内容应符合所调用 API 的请求格式
export RELAY_BASE='https://你的域名/api/v1'
export RELAY_API_KEY='你的中转密钥'
gzip -c request.json > request.json.gz
curl "$RELAY_BASE/chat/completions" \
  -H "Authorization: Bearer $RELAY_API_KEY" \
  -H 'Content-Type: application/json' \
  -H 'Content-Encoding: gzip' \
  --data-binary @request.json.gz

# 安装 zstd CLI 后使用；默认窗口即可，不要开启超大 long 窗口
zstd -q -f request.json -o request.json.zst
curl "$RELAY_BASE/responses" \
  -H "Authorization: Bearer $RELAY_API_KEY" \
  -H 'Content-Type: application/json' \
  -H 'Content-Encoding: zstd' \
  --data-binary @request.json.zst
```

中转解压并解析后，向上游发送普通 JSON，不透传原请求的 Content-Encoding 和压缩 Content-Length。号池无需增加解压功能，但必须支持被调用的接口；例如 `/responses/compact` 的 HTTP 压缩适配不会解决上游该路由的 404。不支持多层编码、外部 zstd 字典或其他压缩格式。其他 API / Multipart 本次未增加请求解压。

### 管理设置和内存语义

管理员进入 `/admin/relay` → **请求压缩**，分别开关 gzip / zstd 并修改解压预算。首次默认两个格式均开启、预算 **128 MB**；设置持久化于 MySQL `relay_compression_config` 表，启动自动建表，保存后对当前实例的新请求立即生效，无需重启。

- 预算允许 **32–1024 MB 整数**，MB 按 1024² 字节计算。
- 每个正在解压/解析的压缩请求预占 **32 MB 名额**；默认最多 4 个。所有上述路由共享当前 JVM 的预算。
- 压缩输入最多 **18 MiB**，解压输出最多 **16 MiB**，zstd 解码窗口最多 **8 MiB**。
- 分块存储解压内容，避免增长缓冲区和完整字节数组复制；zstd 窗口受解码器限制。32 MB 名额保守覆盖解压内容、窗口和解码缓冲区，不是对 JVM RSS 的硬限制。
- 请求体解析后立即清理缓冲并释放名额；上游等待和长时间 SSE 不占用名额。错误、取消和提前结束也释放资源。
- 下调预算不终止已接纳请求；当前已占名额可能暂时高于新预算，等名额释放后才接纳新请求。关闭格式也只影响新请求。
- 预算按需使用，不常驻预分配。JSON 对象、Jackson、业务处理、网络转发和 JVM 本身仍需额外内存，不能把 128 MB 当作整个中转的总内存上限。
- 多副本各有独立预算和内存中的设置快照；通过本实例保存立即生效，其他已运行副本需重启加载数据库设置。本项目默认单后端实例。
- 未压缩或 `Content-Encoding: identity` 的 JSON 不受格式开关和解压预算影响。

管理员 API 使用管理员账户 JWT：

```http
GET /api/admin/relay/compression-config
PUT /api/admin/relay/compression-config
Content-Type: application/json
Authorization: Bearer YOUR_ADMIN_JWT

{"gzipEnabled":true,"zstdEnabled":true,"memoryBudgetMb":128}
```

返回标准 `ApiResponse` 包装，`data` 包含三个设置以及 `reservedMb`、`activeRequests`（查询瞬时名额占用）、`requestBudgetMb: 32`、`maxBodyMb: 16`、`zstdWindowMb: 8`。预留名额不表示操作系统实际 RSS。

### 错误响应

| HTTP | `error.code` | 处理 |
| --- | --- | --- |
| 400 | `invalid_compressed_body` | 压缩流损坏、截断或 zstd 窗口超限；检查完整性和窗口 |
| 400 | `invalid_json` | 解压后不是有效 JSON 对象 |
| 413 | `compressed_body_too_large` / `decompressed_body_too_large` | 减少输入或拆分 |
| 415 | `unsupported_content_encoding` | 格式关闭、不支持或多层编码；改用普通 JSON |
| 415 | `unsupported_media_type` | 压缩请求仍须标为 application/json |
| 503 | `decompression_budget_exhausted` | 带 `Retry-After: 1`，退避后重试 |

OpenAI 格式示例：

```json
{"error":{"type":"overloaded_error","code":"decompression_budget_exhausted","message":"解压内存预算暂时不足，请稍后重试"}}
```

Messages 格式另包含顶层 `"type":"error"`。反向代理的请求体限制仍生效，可能更早返回 413。解压不会解决上游超时或代理 504。

## 余额查询

```bash
curl "$RELAY_BASE/user/balance" \
  -H "Authorization: Bearer $RELAY_API_KEY"
```

`GET /api/v1/user/balance`，兼容 `GET /api/v1/dashboard/billing/balance`。使用中转 API Key（不是网站 JWT），也支持 `x-api-key`；推荐放在请求头，不把密钥放进 URL。无请求体。

成功直接返回 HTTP 200 JSON，**没有 data 包装**：

```json
{"balance":12.345678,"todayCost":0.123456,"todayRequests":42,"unit":"USD"}
```

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| balance | number | 密钥所属用户的当前账户余额，不是单个 Key 的剩余额度 |
| todayCost | number | 全账户今日中转消费金额，按数据库当前日期汇总 |
| todayRequests | number | 全账户今日中转请求数 |
| unit | string | 金额单位，固定 USD |

密钥可用性、账户状态、IP 白名单和 User-Agent 限制仍生效。不消耗推理 Token，不调用模型；响应带 `Cache-Control: no-store, private, max-age=0` 和 `Pragma: no-cache`。

失败返回相应 HTTP 状态（如密钥无效 401、权限/限制 403、账户不存在 404）和 `{"error":"错误信息"}`，消费脚本应先检查 HTTP 状态再读取字段。CC Switch 的用量查询可将 `todayCost` 映射为 used、`balance` 映射为 remaining，二者为账户维度。
