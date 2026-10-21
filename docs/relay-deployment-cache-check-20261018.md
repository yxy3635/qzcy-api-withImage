# 部署复测与缓存排查（2026-10-18）

接口 `https://image.qzcy3.top/api/v1`，模型 `deepseek-v4.1-flash`。

思考修复上线验证：六组直接线上请求全部成功并完整结束，enabled/adaptive/high 返回独立思考内容，disabled 不返回思考。详见 `relay-reasoning-deployed-20261018.json`。

缓存复测：相同 3,634 token 输入连续请求，Chat 第二次起命中 3,456 token（约 95.1%）；Responses 也报告 3,456，Messages 响应则没有缓存字段。八次请求原始统计见 `relay-cache-live-20261018.json`。

用户确认使用 deepseek-harness/0.2.0-rc.2 的 `/v1/messages`。号池 Anthropic 适配器会丢弃上游缓存统计，这与用户的接口一致。已在 `F:/web/workbuddy2api/workbuddy2api/core/anthropic_adapter.py` 修复，并生成 `F:/web/workbuddy2api/workbuddy2api-messages-cache-fix-20261018.zip`。缓存补丁尚未部署。

修复后按 Messages 口径报告普通输入 178、缓存读 3,456、创建 0，避免总输入重复计算。号池相关测试 171 项、中转现有缓存计费测试 73 项通过；本地修复后的 Messages 路由调用线上 Chat 上游也验证了真实命中及统计守恒。详细说明为号池项目的 `MESSAGES_CACHE_FIX.md`。

中转项目本次只新增报告，业务代码无需改动。密钥没有写入任何报告或更新包。本次仅验证指定测试密钥、模型与构造的请求，没有直接运行用户客户端或查看用户服务器的私人调用日志。
