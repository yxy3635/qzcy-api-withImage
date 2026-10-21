# 2026-10-15 线上全新对话测试

接口：`https://image.qzcy3.top/api/v1/responses`，模型严格使用用户指定的 `gpt-6.1-sol`。使用用户提供的测试密钥，报告不保存密钥。

每条请求只有独立的新用户输入，不携带历史或 `previous_response_id`；`stream:true`、`store:false`，思考强度 high。测试程序直接调用 API，并非启动真实 Codex 客户端；客户端到网站使用 HTTP/1.1，这不说明后端到上游使用何种协议。

| 组合 | 时间（北京时间） | 结果 |
| --- | --- | --- |
| 单条全新对话 | 10-14 23:59:57 | 0.766 秒返回 HTTP 403，没有流式事件 |
| 三条同时发起的全新对话 | 10-15 00:44:02 | 分别 0.688、0.657、0.860 秒返回 HTTP 403，没有流式事件 |
| 同密钥 GET /models | 10-15 | HTTP 200，列表包含 gpt-6.1-sol |

用户要求再次尝试后，继续同时发起三条独立新请求。三条仍全部返回相同的 HTTP 403，耗时分别为 1.828、1.641、0.625 秒；结果保存于 `F:/web/imageCreater/backup/relay-live-parallel-retry-20261015.json`。未替换模型或密钥。

四条生成请求的错误一致：

```text
Current group cannot access the selected model: gpt-6.1-sol
```

## 解释与限制

根据当前本地代码，错误来自 `RelayPolicyServiceImpl.resolveGroup`，在发往上游之前。可能原因包括密钥所属分组不存在/未启用、有效分组不允许该模型、模型类型与本次 chat/Responses 请求不匹配。尚未读取服务器数据库，无法确认是哪一种。

GET /models 与调用鉴权并非相同算法：列表在有效分组可见模型集合为空时将模型视为不受限，而且不按 chat/code 类型过滤。因此列表出现模型不等于这把密钥一定能调用它。这里只解释现象，不证明服务器配置中存在何种具体问题。

本次测试尚未进入上游流式生成，无法验证用户此前的“单对话成功，多对话等待数分钟后断开”故障，也不能据此评价尚未部署的 v2 修复效果。需要先让测试密钥能正常调用指定模型，再复测同一模型的单条及多条流。

## 数据库只读排查

在原 Compose 项目目录连接 MySQL：

```bash
docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
```

在 MySQL 内执行以下只读查询（密钥仅按末 8 位匹配，不显示完整密钥；如有多行请核对 token_id）：

```sql
SELECT id AS token_id, user_id, name, group_names, allowed_models,
       enabled, deleted, expires_at
FROM relay_token
WHERE RIGHT(token, 8) = 'b0d49a1b';
```

继续检查分组启用状态和模型类型：

```sql
SELECT t.id AS token_id, t.user_id, t.name, t.group_names,
       g.id AS group_id, g.code, g.enabled AS group_enabled
FROM relay_token t
LEFT JOIN relay_group g ON FIND_IN_SET(g.code, REPLACE(t.group_names, ' ', '')) > 0
WHERE RIGHT(t.token, 8) = 'b0d49a1b';

SELECT id, model, display_name, model_type, enabled, sort_order
FROM relay_model
WHERE model = 'gpt-6.1-sol' OR display_name = 'gpt-6.1-sol'
ORDER BY sort_order, id;

SELECT g.id AS group_id, g.code, g.enabled AS group_enabled,
       m.id AS model_id, m.model, m.display_name, m.model_type,
       m.enabled AS model_enabled
FROM relay_group g
JOIN relay_group_model gm ON gm.group_id = g.id
JOIN relay_model m ON m.id = gm.model_id
WHERE m.model = 'gpt-6.1-sol' OR m.display_name = 'gpt-6.1-sol';
```

原始结果保存在 `F:/web/imageCreater/backup/relay-live-single-20261014.json`、`relay-live-parallel-20261015.json`、`relay-live-models-20261015.json`，不含认证密钥。测试脚本为 `scripts/relay_live_concurrency_check.py`，密钥仅通过临时环境变量传入，不写入脚本或报告。没有更改线上配置。
