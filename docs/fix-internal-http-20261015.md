# Python 号池直连 HTTP 兼容修复

现象：Docker 后端内 curl `/health` 成功；HTTP/1.1 POST `/v1/chat/completions`（不携带 Key）得到 Uvicorn 的正常 JSON 401；管理后台渠道测试却显示 `Unrecognized token 'Invalid'`。

本地复现：Java 默认 HttpClient 通过明文 HTTP POST 到 Uvicorn 0.30.6 + httptools 0.6.4，会尝试 h2c Upgrade。在复现中返回 HTTP 400 和纯文本 `Invalid HTTP request received.`；同样的请求显式使用 HTTP/1.1 后得到正常 JSON 401。新版 Uvicorn 中响应存在时序差异，服务器实际库版本尚未核对；线上原始错误内容与复现吻合，修复后需实际复测。

原流式补丁只对 stream 请求指定 HTTP/1.1，未覆盖后台渠道测试与普通调用。此次 `RelayHttpProtocol` 为明文 `http://` 上游指定 HTTP/1.1，覆盖：渠道测试、非流式 JSON 调用、multipart、模型列表读取和定时供应商健康检查。HTTPS 的普通请求保留原协议协商；流式请求继续沿用已有协议配置。

渠道测试遇到非 JSON 错误时显示 HTTP 状态和明确的非 JSON 提示，避免返回 Jackson 的解析异常；不展示原始错误正文。

`imagecreater-internal-http-fix-20261015.zip` 是后端累计源码增量包，基于 10 月 13 日 report-speed 版本，包含 10 月 14 日 stream-fix-v2、10 月 15 日上下文压缩标识后端及本次 HTTP 修复。可以直接覆盖匹配版本的项目，不需顺序安装先前两个包。没有 Compose、.env、前端、号池配置或账户数据。

把 ZIP 上传并解压到 `/www/wwwroot/imageCreater`，确保文件直接覆盖 `backend/src/...`，不要嵌套一层目录。然后在宝塔终端逐条执行，失败先处理：

```bash
cd /www/wwwroot/imageCreater
sha256sum -c INTERNAL-HTTP-20261015-MANIFEST.sha256
docker compose build backend
docker compose up -d --no-deps --wait --wait-timeout 300 backend
docker compose logs --tail=60 backend
```

以上按用户已将 dist 挂载和 extra_hosts 写入主 compose.yaml 的现状执行，不需要额外 compose.local.yaml。保持原部署项目名、环境变量和数据卷。本次重建后端会中断正在进行的流式调用，不需重建前端。

保留供应商地址 `http://host.docker.internal:8787/v1` 和现有号池客户端 API Key。后台复测渠道、模型列表、普通和流式调用。此前不带 Key 的 curl 返回 401 是正常鉴权行为，不能据此判断后台保存的 Key 是否正确。若修复后出现鉴权错误，单独检查后台 Key。

没有连接服务器代为部署。仅凭 health 成功只能确认基础连通，不能证明真实模型调用已正常完成。
