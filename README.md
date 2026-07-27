# imageCreater

一个集 AI 图像生成、用户计费与 OpenAI / Anthropic 兼容 API 中转于一体的平台。

项目包含 Vue 3 前端与 Spring Boot 后端：用户可以生成和管理图像、充值、使用 API 密钥；管理员可以管理模型、上游渠道、计费、邮件、用户和运营数据。中转能力以 `/api/v1` 为入口，适合接入 OpenAI SDK、Codex、CCSwitch 等客户端。

> 当前文档以仓库中的现有代码和配置为准。旧版本 README 中已经过时的页面、配置和说明均不再适用。

## 功能概览

### 用户端

- 注册、登录、邮箱验证码、找回密码与个人资料管理
- AI 图像生成：规格选择、上传参考图、生成进度、预览、复用提示词与历史管理
- 余额充值、支付记录、邀请返利与提现申请
- API 中转控制台：创建/停用/删除密钥、额度与 RPM/TPM 限制、IP 白名单、调用日志与费用统计
- 模型状态页：默认展示系统全部模型，并展示全站最近调用状态
- API 密钥可一键导入 CCSwitch；会根据所属分组的上游协议自动选择 Codex（OpenAI）或 Claude

### 管理端

- 数据仪表盘、用户与用户 API 使用情况管理
- 图像规格、模型、价格、固定请求计费和调用日志管理
- 中转渠道、模型、分组、上游模型映射、权重、优先级、并发/RPM/TPM 限制管理
- 公告、支付、邀请返利、提现审核与系统日志管理
- 邮件 SMTP、品牌名称、Logo、站点链接与充值到账通知配置

### 中转能力

- OpenAI 兼容：Chat Completions、Responses、Completions、Embeddings、图像、音频等接口
- Anthropic Messages 与 `count_tokens` 兼容入口
- 多渠道分组、优先级/权重调度、模型映射、渠道状态检测
- 密钥额度、余额、过期时间、IP 白名单、RPM/TPM、渠道并发限制
- 调用日志、输入/输出/缓存 Token 与费用拆分统计

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Tailwind CSS、Axios、Three.js |
| 后端 | Spring Boot 3.3、Spring Security、JWT、MyBatis-Plus、Spring AI、Spring Mail |
| 数据 | MySQL、Redis |

## 目录结构

```text
.
├─ backend/
│  ├─ src/main/java/com/qzcy/backend/    # Spring Boot 源码
│  ├─ src/main/resources/
│  │  ├─ application.yml                 # 通用配置
│  │  ├─ application-dev.yml             # 本地开发数据源配置
│  │  ├─ application-prod.yml            # 生产环境配置
│  │  └─ db/schema.sql                   # 初始建表与历史兼容迁移
│  ├─ mvnw / mvnw.cmd                    # Maven Wrapper
│  └─ pom.xml
├─ src/
│  ├─ api/                               # 前端 API 封装
│  ├─ components/                        # 通用组件
│  ├─ composables/                       # 组合式逻辑
│  ├─ router/                            # 路由与权限守卫
│  ├─ store/                             # Pinia 状态
│  └─ views/                             # 用户端、管理端与中转站页面
├─ public/                               # 静态资源
├─ package.json
└─ vite.config.ts
```

## 环境要求

- Node.js `20.19+` 或 `22.12+`
- JDK `17+`
- MySQL `5.7+`（推荐 MySQL 8）
- Maven 不必单独安装，项目自带 Maven Wrapper
- Redis `6+`（可选）

## 本地启动

### 1. 初始化数据库

创建数据库：

```sql
CREATE DATABASE image_creator
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

执行初始化脚本：

```bash
mysql -u root -p image_creator < backend/src/main/resources/db/schema.sql
```

`schema.sql` 同时包含初始表结构和一部分兼容迁移语句。升级已有环境前，请先备份数据库。

### 2. 配置后端

本地开发默认使用 `dev` Profile。修改 [application-dev.yml](backend/src/main/resources/application-dev.yml) 中的数据源为本机 MySQL 信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/image_creator?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: your-password
```

生产环境请使用 `prod` Profile，并自行维护 [application-prod.yml](backend/src/main/resources/application-prod.yml) 中的数据源、上传目录和 CORS 配置。不要把真实密码、支付密钥或 SMTP 授权码提交到仓库。

### 3. 启动后端

Windows：

```bash
cd backend
mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
cd backend
./mvnw spring-boot:run
```

后端默认监听 `http://localhost:8080`。

### 4. 启动前端

在项目根目录执行：

```bash
npm install
npm run dev
```

前端默认地址为 `http://localhost:5173`。开发服务器已经把 `/api` 代理到 `http://localhost:8080`。

### 5. 首次登录

首次启动会自动创建管理员账户：

```text
用户名：admin
密码：admin123
```

登录后访问 `/admin/dashboard`。生产环境请立即修改该密码，并避免长期保留默认账户凭据。

## 常用配置

通用配置在 [application.yml](backend/src/main/resources/application.yml) 中，以下环境变量可直接覆盖相应配置：

| 变量 | 说明 |
| --- | --- |
| `JWT_SECRET` | JWT 签名密钥；生产环境必须设置为随机长字符串 |
| `OPENAI_API_KEY` | Spring AI 默认 OpenAI Key；实际图像/中转渠道通常在管理端单独配置 |
| `OPENAI_BASE_URL` | 默认 OpenAI 兼容服务地址 |
| `MAIL_HOST` | SMTP 主机 |
| `MAIL_PORT` | SMTP 端口，默认 `587` |
| `MAIL_USERNAME` | SMTP 用户名 |
| `MAIL_PASSWORD` | SMTP 密码或授权码 |
| `MAIL_FROM` | 发件人地址，默认使用 `MAIL_USERNAME` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址与端口 |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | Redis 认证信息（如服务端启用） |
| `RELAY_MODEL_STATUS_CACHE_REDIS_ENABLED` | 是否启用模型状态调用条缓存，默认 `false` |
| `RELAY_MODEL_STATUS_CACHE_TTL_SECONDS` | 调用条缓存秒数，默认 `30` |

### 邮件与验证码

管理员可在 `/admin/mail` 完成 SMTP、邮件品牌名、Logo URL、站点 URL 和“充值到账通知”的配置。注册、找回密码及充值到账邮件会使用同一套品牌化模板。

验证码有效期为 10 分钟，并内置以下保护：

- 同一邮箱 60 秒内不能重复发送
- 单邮箱每小时最多 5 次、每天最多 12 次
- 单 IP 每小时最多 15 次，并有全局每小时限制
- 验证码连续错误 5 次后失效
- 找回密码接口不会暴露邮箱是否已注册

开发环境中，只有 SMTP 未启用、`dev-return-code` 为 `true` 且请求来自本机回环地址时，接口才会返回调试验证码。生产环境应在 `/admin/mail` 关闭该选项并启用真实 SMTP。

### Redis：仅缓存模型状态调用条

Redis 是可选项。启用后只缓存中转站“模型状态”页的全站最近调用状态，降低频繁聚合查询压力；用户余额、仪表盘消费、Token、请求统计仍实时查询数据库，不会因该缓存显示旧余额。

示例：

```bash
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
RELAY_MODEL_STATUS_CACHE_REDIS_ENABLED=true
RELAY_MODEL_STATUS_CACHE_TTL_SECONDS=30
```

未安装 Redis 或未启用缓存时，系统会直接从数据库计算，不影响核心中转与计费逻辑。

## OpenAI / Anthropic API 中转

### 基本用法

1. 管理员进入 `/admin/relay`，创建模型、分组和上游渠道，并配置模型映射。
2. 用户进入 `/relay`，创建 API 密钥并按需设置额度、IP 白名单与速率限制。
3. 客户端将站点地址加上 `/api` 作为 Base URL，并使用该密钥作为 Bearer Token。

```text
Base URL: https://your-domain.example/api
API Key:  sk-...（在中转站生成的密钥）
```

例如：

```bash
curl https://your-domain.example/api/v1/models \
  -H "Authorization: Bearer sk-your-token"
```

常用接口包括：

| 方法 | 路径 |
| --- | --- |
| `GET` | `/api/v1/models` |
| `POST` | `/api/v1/chat/completions` |
| `POST` | `/api/v1/responses` |
| `POST` | `/api/v1/completions` |
| `POST` | `/api/v1/embeddings` |
| `POST` | `/api/v1/images/generations` |
| `POST` | `/api/v1/images/edits` |
| `POST` | `/api/v1/audio/transcriptions` |
| `POST` | `/api/v1/audio/translations` |
| `POST` | `/api/v1/audio/speech` |
| `POST` | `/api/v1/messages` |
| `POST` | `/api/v1/messages/count_tokens` |

完整的在线请求参数与示例请访问前端 `/docs` 页面。

### CCSwitch 导入

在 `/relay` 的 API 密钥卡片中点击“导入 CCSwitch”，确认后会通过 `ccswitch://` 协议唤起客户端，并按密钥分组和可用上游协议自动导入为：

- `Codex（OpenAI）`：存在 OpenAI 兼容渠道或无法唯一识别为 Anthropic 时
- `Claude`：该密钥匹配的渠道均为 Anthropic 时

浏览器对 `ccswitch://` 的“打开外部应用”安全提示由浏览器控制，网页无法修改；CCSwitch 内部仍会进行一次导入确认。

## 生产部署

### 构建产物

前端：

```bash
npm ci
npm run build
```

产物在 `dist/`。

后端：

```bash
cd backend
./mvnw -DskipTests package
```

Windows 可将 `./mvnw` 替换为 `mvnw.cmd`。产物在 `backend/target/backend-0.0.1-SNAPSHOT.jar`。

以生产 Profile 启动：

```bash
java -jar backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Nginx 示例

将前端 `dist/` 部署为静态站点，并把 API 请求反向代理给后端：

```nginx
server {
    listen 80;
    server_name your-domain.example;

    root /var/www/imagecreater/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

如通过 HTTPS 对外提供服务，请配置证书，并将站点的 HTTPS 域名填入生产环境的 CORS 白名单与邮件站点链接。

### 上线检查清单

- [ ] 已备份数据库，并执行与当前版本匹配的 `schema.sql`
- [ ] `JWT_SECRET` 已替换为随机强密钥
- [ ] 数据库、SMTP、支付和上游渠道密钥均不在源码或前端中暴露
- [ ] 默认管理员密码已修改
- [ ] `/admin/mail` 已启用 SMTP，且关闭开发验证码返回
- [ ] 上传目录位于持久化磁盘，并具备写入权限
- [ ] 生产 CORS 仅允许可信域名
- [ ] 如启用 Redis，已配置连接信息和合理的缓存 TTL
- [ ] 已检查充值异步通知地址可从支付服务访问

## 开发与验证

前端类型检查：

```bash
npm run type-check
```

前端生产构建：

```bash
npm run build
```

后端测试：

```bash
cd backend
./mvnw test
```

## 常见问题

### 页面请求 `/api` 返回 404 或无法连接

本地开发时确认后端运行在 `http://localhost:8080`，并使用 `npm run dev` 启动前端。生产环境中确认 Nginx 的 `/api/` 反向代理已生效，且没有把 `/api` 路径意外剥离。

### 收不到邮箱验证码

检查 `/admin/mail` 中的 SMTP 主机、端口、用户名、授权码、STARTTLS 与发件人设置；同时检查限流是否已触发。生产环境不要依赖开发验证码返回功能。

### 模型状态页响应较慢

可部署 Redis 并开启 `RELAY_MODEL_STATUS_CACHE_REDIS_ENABLED=true`。该优化只缓存最近调用状态条，不会缓存或延迟余额数据。

### 上游请求失败或返回 401 / 429 / 503

在 `/admin/relay` 检查渠道 API Key、上游地址、协议类型、模型映射、余额和 RPM/TPM 限制；再通过渠道状态同步与调用日志定位具体上游响应。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
