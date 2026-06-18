# imageCreater

imageCreater 是一个 AI 图像生成与 OpenAI 兼容接口中转平台。项目包含 Vue 3 前端和 Spring Boot 后端，支持用户注册登录、余额扣费、图像生成、生成历史、充值配置、邀请返佣、公告管理、邮件配置、后台管理，以及兼容 `/v1/*` 的模型中转接口。

## 功能概览

- 门户首页：白色极简风格，包含 Three.js 交互背景和 AI 作品展示。
- 用户认证：注册、登录、邮箱验证码、忘记密码。
- 图像生成：提示词生图、规格选择、余额扣费、生成状态、图片预览、下载和删除。
- 用户中心：仪表盘、余额、充值记录、生成历史、个人资料、修改密码。
- 邀请返佣：邀请码、邀请用户列表、返佣记录、提现二维码、提现申请。
- 管理后台：数据看板、用户管理、公告管理、生图配置、邮件配置、支付配置、返佣审核、系统日志。
- OpenAI 兼容中转：支持模型、渠道、分组、令牌、额度、RPM/TPM、IP 白名单、用量日志和成本统计。

## 技术栈

前端：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Tailwind CSS
- Three.js
- Axios

后端：

- Spring Boot 3.3.5
- Spring Security + JWT
- MyBatis-Plus
- MySQL
- Spring AI OpenAI
- Spring Mail

## 目录结构

```text
.
├── backend/                         # Spring Boot 后端
│   ├── src/main/java/               # 后端源码
│   │   └── com/qzcy/backend/
│   │       ├── config/              # 配置、初始化器、安全配置
│   │       ├── controller/          # HTTP 接口
│   │       ├── dto/                 # 请求/响应 DTO
│   │       ├── entity/              # 数据库实体
│   │       ├── mapper/              # MyBatis-Plus Mapper
│   │       ├── service/             # 业务服务
│   │       └── util/                # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml          # 通用配置
│   │   ├── application-dev.yml      # 本地开发配置
│   │   └── db/schema.sql            # 数据库初始化/迁移脚本
│   └── userImage/                   # 本地生成图片与上传文件目录
├── public/                          # 静态资源
├── src/                             # Vue 前端源码
│   ├── api/                         # API 封装
│   ├── assets/                      # 样式和图片资源
│   ├── components/                  # 公共组件
│   ├── composables/                 # 组合式函数
│   ├── router/                      # 前端路由
│   ├── store/                       # Pinia 状态
│   ├── types/                       # TypeScript 类型
│   └── views/                       # 页面
├── package.json
├── vite.config.ts
└── README.md
```

## 环境要求

- Node.js `20.19+` 或 `22.12+`
- JDK `17+`
- MySQL `8+`
- Maven Wrapper：项目已在 `backend/` 内包含 `mvnw` 和 `mvnw.cmd`

## 快速开始

### 1. 初始化数据库

先在 MySQL 中创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS image_creator
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

再执行初始化脚本：

```sh
mysql -u root -p image_creator < backend/src/main/resources/db/schema.sql
```

也可以在 MySQL 客户端中执行：

```sql
USE image_creator;
SOURCE backend/src/main/resources/db/schema.sql;
```

如果是从旧版本升级，`schema.sql` 中包含部分兼容迁移语句；重复执行前仍建议先备份数据库。

### 2. 配置后端

本地开发默认启用 `dev` profile，数据库配置在：

```text
backend/src/main/resources/application-dev.yml
```

默认配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/image_creator?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: root
```

根据本机 MySQL 用户名和密码修改即可。

通用配置在：

```text
backend/src/main/resources/application.yml
```

常用环境变量：

```text
JWT_SECRET       JWT 签名密钥，生产环境必须修改
OPENAI_API_KEY   默认 OpenAI API Key
OPENAI_BASE_URL  默认 OpenAI 兼容服务商基础地址，默认 https://api.openai.com
MAIL_HOST        SMTP 服务器
MAIL_PORT        SMTP 端口，默认 587
MAIL_USERNAME    SMTP 用户名
MAIL_PASSWORD    SMTP 密码
MAIL_FROM        发件人地址
```

本地开发默认 `app.mail.dev-return-code: true`。没有配置 SMTP 时，验证码接口会返回开发验证码，方便调试；生产环境必须关闭。

### 3. 启动后端

Windows：

```sh
cd backend
cmd /c mvnw.cmd spring-boot:run
```

macOS / Linux：

```sh
cd backend
./mvnw spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

### 4. 启动前端

```sh
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`。

## 默认管理员

后端首次启动时会自动创建默认管理员：

```text
账号：admin
密码：admin123
```

登录后进入：

```text
/admin/dashboard
```

生产环境部署后请立即修改默认管理员密码。

## 主要页面

用户端：

- `/`：首页
- `/login`：登录
- `/register`：注册
- `/create` 或 `/user/generate`：AI 生图
- `/user/dashboard`：用户仪表盘
- `/user/history`：生成历史
- `/user/payment`：充值与充值记录
- `/user/referral`：邀请返佣
- `/user/profile`：个人资料
- `/relay`：用户中转令牌与用量

管理端：

- `/admin/dashboard`：后台仪表盘
- `/admin/announcements`：公告管理
- `/admin/users`：用户管理
- `/admin/pricing`：生图规格与扣费配置
- `/admin/relay`：中转渠道、模型、分组管理
- `/admin/payment`：支付配置
- `/admin/referral`：返佣审核与提现管理
- `/admin/mail`：邮件配置
- `/admin/logs`：生图记录与日志

## AI 生图配置

管理员登录后进入：

```text
/admin/pricing
```

每个生图规格可配置：

- 显示名称
- API Key
- 服务商基础地址，例如 `https://api.openai.com` 或兼容中转服务地址
- 图像接口路径，例如 `/v1/images/generations`
- 模型名称
- 尺寸，例如 `1024x1024`
- 质量参数，例如 `standard`、`hd`
- 用户扣费价格
- 启用状态
- 排序

注意：服务商基础地址只填写根地址，不要把 `/v1/images/generations` 拼到基础地址里；接口路径在后台单独选择或填写。

## OpenAI 兼容中转

平台提供兼容 OpenAI 风格的接口中转能力，入口包括：

```text
GET  /api/v1/models
POST /api/v1/chat/completions
POST /api/v1/responses
POST /api/v1/completions
POST /api/v1/embeddings
POST /api/v1/images/generations
POST /api/v1/audio/transcriptions
POST /api/v1/audio/translations
POST /api/v1/audio/speech
```

使用方式：

1. 管理员在 `/admin/relay` 配置上游渠道、模型、分组和价格。
2. 用户在 `/relay` 创建访问令牌。
3. 客户端使用平台地址作为 OpenAI Base URL，并使用创建的令牌作为 Bearer Token。

示例：

```text
Base URL: http://localhost:8080/api
API Key: 用户在 /relay 创建的 token
```

## 构建

前端构建：

```sh
npm run build
```

前端预览：

```sh
npm run preview
```

后端测试：

```sh
cd backend
cmd /c mvnw.cmd test
```

后端打包：

```sh
cd backend
cmd /c mvnw.cmd -DskipTests package
```

构建产物：

- 前端：`dist/`
- 后端：`backend/target/backend-0.0.1-SNAPSHOT.jar`

## 生产部署提示

- 修改 `JWT_SECRET`，不要使用默认开发密钥。
- 修改默认管理员密码。
- 使用独立的生产数据库账号和强密码。
- 将 `app.mail.dev-return-code` 设置为 `false`。
- 配置真实 SMTP 邮件服务。
- 在后台配置可用的 OpenAI 或兼容服务商 API Key。
- 确认 `app.upload.image-path` 指向持久化目录。
- 对外部署时建议使用 Nginx 反向代理前端静态资源和后端接口。
- 数据库升级前先备份，再执行迁移脚本。

## 常见问题

### 前端请求 `/api` 返回 404 或连接失败

确认后端已经启动在 `http://localhost:8080`，并且前端通过 `npm run dev` 启动。开发环境下 Vite 会把 `/api` 代理到后端。

### 邮箱验证码收不到

本地开发如果没有配置 SMTP，验证码接口会返回开发验证码。生产环境需要正确配置 `MAIL_HOST`、`MAIL_USERNAME`、`MAIL_PASSWORD`、`MAIL_FROM`，并关闭 `app.mail.dev-return-code`。

### AI 生图失败或返回 401

检查后台生图规格中的 API Key、服务商基础地址、接口路径和模型名称是否正确。服务商基础地址只填写根地址，例如 `https://api.openai.com`。

### OpenAI 429 TOO_MANY_REQUESTS

通常是服务商限流、额度不足或请求频率过高。检查 API Key 额度、RPM/TPM 限制和上游服务商状态。

### OpenAI 503 SERVICE_UNAVAILABLE

通常是上游服务临时不可用、节点拥塞或模型不支持。稍后重试，或切换后台配置的服务商渠道。

### `Lock wait timeout exceeded`

旧版本可能因为长时间生图事务锁住用户余额。当前版本已将外部生图请求移出长事务；升级后请重启后端并重试。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
