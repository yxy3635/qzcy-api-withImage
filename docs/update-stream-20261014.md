# 2026-10-14 流式中转修复增量包

文件：`imagecreater-stream-fix-20261014.zip`。

基于昨晚的 `imagecreater-update-direct-20261013-report-speed.zip` 核对：本次只更新一个生产文件 `RelayDispatchServiceImpl.java`，以及两个相关测试文件。旧包中的实体、数据库初始化和其余后端源码与当前版本匹配，不需要再覆盖。包内另附诊断报告、发布信息和 SHA-256 清单。文件直接位于项目根层。

本包修复：收到协议结束事件后立即结束转发；提前断流和客户端断开记为失败、费用为零；心跳不再估算成输出 token；上游断流时向仍连接的客户端返回脱敏 SSE 错误；较大的最终事件不再静默丢失。

上游一直发送心跳而没有模型事件的问题仍需排查。本包不会改变上游生成行为，也不会修改历史记录和扣款。相关 135 项测试通过，包含 17 项流式回归；未在服务器部署或构建 Docker 镜像。

## 上传和更新

将 ZIP 上传到服务器 `/tmp`。在原来的项目根目录执行（该目录应有现用的 `compose.yaml` 和 `.env`）：

```bash
cd /你的/中转项目目录

# 备份本次要覆盖的原文件；若已存在该备份，改用另一个文件名。
tar -czf /tmp/imagecreater-before-stream-20261014.tar.gz \
  backend/src/main/java/com/qzcy/backend/service/impl/RelayDispatchServiceImpl.java \
  backend/src/test/java/com/qzcy/backend/service/impl/RelayBillingCompatibilityTest.java

unzip -o /tmp/imagecreater-stream-fix-20261014.zip -d .
sha256sum -c STREAM-FIX-MANIFEST.sha256
docker compose config --quiet
docker compose build backend
docker compose up -d --no-deps --wait --wait-timeout 300 backend
docker compose logs --tail=100 backend
docker compose ps
```

上一条命令失败时先停止，处理错误后再执行下一条。如果原部署使用 `-p`、`--env-file` 或多个 `-f`，以上所有 Compose 命令沿用原参数。原 mysql/redis 应保持正常运行。

包内不含 Compose、Nginx、Dockerfile、application 配置、真实环境文件、前端、号池、数据库、用户图片或编译产物。本次只需构建后端。已有数据库无需重新导入 schema。仅 restart 无法应用源码修改。

本包适用于已安装昨晚中转更新的项目。更旧版本至少需核对 `RelayUsageLog.billingGroupCode` 和对应数据库初始化，不要把此文件直接覆盖到不匹配的版本。有自行修改的同名源码时先对比。

## 更新后验证

用原客户端和相同上游复测 `/v1/responses` 的文本、工具调用流；核对模型结束后客户端及时结束。对提前断流或主动取消的请求，检查新调用记录为失败、费用为零，心跳没有被计为输出。如果依然只有心跳、等待后断开，继续收集对应请求的上游和代理日志。

## 回滚本次改动

在同一原项目目录执行（若备份时修改了文件名，使用实际备份路径）：

```bash
tar -xzf /tmp/imagecreater-before-stream-20261014.tar.gz -C .
# 此测试文件是本包新加的，旧服务代码回滚后一并移除。
rm -f backend/src/test/java/com/qzcy/backend/service/impl/RelayStreamCompletionTest.java
docker compose build backend
docker compose up -d --no-deps --wait --wait-timeout 300 backend
docker compose logs --tail=100 backend
```

回滚恢复旧代码，也会恢复旧流式处理和计费行为。回滚不修改数据库或历史记录。
