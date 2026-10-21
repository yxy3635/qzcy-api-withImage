# 账号被盗相关安全审计（2026-10-18）

> 本文保存修复前的审计基线。后续修复见 [账号安全修复与 Docker 升级说明](fix-account-security-20261019.md)。原复现程序已归档为 `security-audit-20261018/AccountSecurityAudit.java.txt`，`run-audit.ps1` 现运行修复后的安全回归测试。源码修复不代表线上已经更新。

## 结论与范围

项目存在与账号接管、会话持续被利用有关的漏洞。用户反馈是 **Docker 部署、已有用户被他人登录、密码被修改、账号下新增中转 API 密钥，未发现邮箱变更**。当前只能确认代码缺陷，不能仅凭源码确定本次入侵入口。

本次审查了本地当前工作区的登录、注册、验证码、找回密码、资料修改、JWT 认证、管理员用户操作、中转密钥创建与认证，以及仓库附带 Docker/Nginx 配置。没有访问线上站点、连接生产数据库、修改真实用户、发送邮件或部署更改；没有读取或输出生产密钥。现有工作区的业务修改保留，新增内容仅为审计文档和隔离验证程序。生产实际 JAR、配置、外层反向代理和访问日志尚未核对，不能假定它们与当前源码完全一致。

## 按本次事件判断可能路径

- **获得原密码后登录，再直接改密、创建密钥**：符合现象；普通改密确实检查原密码，但登录缺少限流，使弱密码猜测、撞库缺少应用层阻挡。还需要排查用户是否复用密码、浏览器或设备泄露。
- **通过找回密码取得账号后创建密钥**：需要有效邮箱验证码；要检查邮箱是否被入侵，以及事发时是否收到找回密码邮件。
- **管理员权限被盗后直接改用户密码**：管理员接口允许设置用户新密码，无需用户原密码，这本身是管理功能；需要检查管理员登录及用户管理请求。
- **网页登录令牌被盗后创建密钥**：可以做到；但单独拿到令牌不能绕过普通改密接口的原密码校验。还可能先改邮箱再找回密码，虽然未发现邮箱变化不能完全排除临时改绑后恢复。
- **JWT 签名密钥已知后伪造身份**：可以做到；是否适用必须核对线上密钥。仓库自带 Docker 配置要求提供密钥，不能因此断定线上使用公开默认值。

## 已确认的问题

### 1. 高：登录没有失败次数限制或应用层防撞库

位置：`backend/src/main/java/com/qzcy/backend/controller/AuthController.java:37`；`backend/src/main/java/com/qzcy/backend/service/impl/AuthServiceImpl.java:69`。

登录直接查询用户并验证 BCrypt，没有按账号、IP 或全局的尝试限制、退避或风险验证。邮箱验证码的 `EmailCodeRateLimiter` 只用于发送验证码，不保护登录。注册和改密允许最低 6 位密码。

本地观察：同一个虚拟账号连续 30 次错误密码全部得到普通 401，紧接着正确密码可以登录，未触发 429 或临时锁定。线上如有 WAF、CDN 或外层 Nginx 限流，需要独立核实，仓库自带 Nginx 未配置登录限流。

修复建议：按可信客户端 IP 和规范化账号限制尝试，增加短期退避及异常告警，避免轻易被滥用的永久账号锁定；多实例部署应共享计数。管理员增加 MFA，敏感操作要求再次验证。

### 2. 高：密码修改/重置后旧 JWT 仍有效，退出登录也只清理浏览器

位置：`backend/src/main/java/com/qzcy/backend/util/JwtUtil.java:23`；`backend/src/main/java/com/qzcy/backend/config/security/JwtAuthenticationFilter.java:47`；`backend/src/main/java/com/qzcy/backend/service/impl/AuthServiceImpl.java:92`；`backend/src/main/java/com/qzcy/backend/service/impl/UserServiceImpl.java:50`；`src/store/authStore.ts:38`。

JWT 只记录用户 ID、用户名、角色和签发/到期时间。过滤器核验签名、用户存在和封禁状态，没有会话撤销或密码变更版本校验。默认有效期 24 小时。正常改密、找回密码、管理员设置密码均未撤销已有网页登录令牌。退出登录仅删除本机 localStorage。

本地观察：同一旧令牌在找回密码、正常改密之后，仍能通过真实 `JwtAuthenticationFilter` 建立认证。攻击者因此可能在用户找回账号后继续操作、创建 API 密钥，直到令牌到期或签名密钥轮换。

修复建议：新增独立会话版本或服务端会话记录；密码修改/重置、管理员安全处置时原子撤销旧会话。不要复用 `User.version`，它同时在余额变动时递增。按接口契约处理改密后重新登录。

### 3. 高：修改绑定邮箱没有二次验证，可将会话泄露升级为永久接管

位置：`backend/src/main/java/com/qzcy/backend/service/impl/UserServiceImpl.java:35`；`backend/src/main/java/com/qzcy/backend/dto/UserProfileDto.java:7`。

`PUT /api/user/profile` 只校验新邮箱格式和唯一性，不要求原密码、原邮箱验证码或新邮箱验证码。拿到有效 JWT 即可将邮箱改为攻击者控制的邮箱，然后走正常找回密码流程。

本地观察：仅使用虚拟用户会话身份改邮箱，真实验证码服务将找回密码验证码发送到新邮箱，真实重置流程完成改密，并能用新密码登录；未提供原密码或原邮箱验证码。

本次未发现邮箱变化，因此它是确认存在的接管漏洞，尚无证据表明就是本次入口。

修复建议：改绑要求重新验证身份及新邮箱所有权，优先验证原邮箱；提供单独的旧邮箱不可用人工恢复流程。改绑成功后通知旧邮箱并撤销旧会话。

### 4. 条件性严重：公开默认 JWT 签名密钥可导致身份伪造

位置：`backend/src/main/resources/application.yml:59`；`backend/src/main/java/com/qzcy/backend/util/JwtUtil.java:59`。

通用配置有公开默认密钥，且短密钥会被补零到最低长度，未在启动时拒绝弱值。若部署使用该默认值或泄露的自定义密钥，攻击者可以离线签名任意已有用户 ID 的 JWT，绕过密码登录。过滤器从数据库读取实际角色；不是简单地把 JWT 的 role 写成 ADMIN 就能提升普通用户权限，但可以指定已有管理员的 ID 冒充管理员。

本地观察：使用配置文件的默认密钥在外部签名虚拟用户身份，被使用同一默认密钥的真实过滤器接受；使用无关测试密钥的验证器拒绝此令牌。

**Docker 限定**：`application-docker.yml:24` 使用 `${JWT_SECRET}`；`compose.yaml:24` 启用 docker profile，并在第 27 行要求非空密钥。预构建 Compose 也如此。因此当前标准 Docker 部署不会自动回退到通用默认值，但仍允许人为填入弱值、公开示例值或已泄露值。

修复建议：生产启动时拒绝缺省、已知示例及过短密钥，取消补零；使用密码学随机生成的独立密钥。核对线上时只比较安全性和是否等于已知默认值，不要分享实际内容。`ADMIN_INITIAL_PASSWORD` 同样应核对，修改环境变量不会覆盖已有管理员密码。

### 5. 中：找回密码接口可枚举已注册邮箱

位置：`backend/src/main/java/com/qzcy/backend/service/impl/AuthServiceImpl.java:97`。

该接口在验证码验证前查询邮箱；不存在返回 404，存在但验证码错误返回 400。虽然发送验证码接口统一了成功响应，重置密码接口仍暴露账号存在性，便于收集撞库目标。

本地观察：向不存在邮箱和虚拟用户邮箱提交同一无效验证码，分别得到 404 和 400。

修复建议：统一对外错误，结合请求限流和验证码校验，避免通过状态、消息及明显耗时差异枚举。

### 6. 中：管理员接口向浏览器返回用户密码哈希

位置：`backend/src/main/java/com/qzcy/backend/entity/User.java:17`；`backend/src/main/java/com/qzcy/backend/controller/AdminController.java:110`；`backend/src/main/java/com/qzcy/backend/service/impl/AdminServiceImpl.java:87`、`:160`。

用户列表及修改结果直接返回含 `password` 的 `User` 实体，字段未配置禁止 JSON 输出，管理员浏览器会收到 BCrypt 哈希。它不是明文，也不代表匿名接口泄露；但后台会话、浏览器或响应日志一旦被获取，可能扩大为离线弱密码破解。

本地检查：用真实 Jackson 序列化用户实体，确认 JSON 包含 password 字段，验证输出没有打印哈希。

修复建议：采用不含敏感字段的管理 DTO，并对实体 password 加禁止序列化保护。

## 条件性问题：调试验证码与代理信任

位置：`backend/src/main/java/com/qzcy/backend/service/impl/EmailCodeServiceImpl.java:51`；`backend/src/main/java/com/qzcy/backend/controller/AuthController.java:47`。

SMTP 未启用、数据库 `dev_return_code=true` 且判断客户端 IP 为回环时，接口直接返回验证码。判断远端为回环时会信任转发请求头。如果实际代理透传攻击者提供的客户端 IP，或生产转发头配置错误，可绕过邮箱证明，取得找回密码验证码。

隔离验证复现了这些条件组合下验证码返回。**这不等于仓库标准 Docker 配置存在同一路径**：`deploy/nginx.conf:21` 起覆盖 X-Real-IP、X-Forwarded-For，并清空 Forwarded；后端未映射公网端口。`deploy/02-production-defaults.sql` 首次初始化时关闭调试验证码。已有数据库迁移和后续后台更改仍需核查。

修复建议：生产环境从代码层禁止调试验证码返回；开发模式开关与邮箱数据库配置分离；只信任实际受控代理。SMTP 配置缺失应拒绝发送，不向客户端暴露验证码。

## 已检查的保护与边界

- 正常改密验证原密码；错误原密码在本地被拒绝，未发现直接跳过校验的路径。
- 密码使用 BCrypt 哈希。未发现普通用户可以直接写入 role 或读取其他用户密码的接口。
- 中转 `sk-...` API 密钥与网页登录 JWT 分别认证；本地将伪造中转密钥用于网页登录被拒绝。没有发现仅凭中转密钥就可以调用用户改密/创建密钥接口的路径。
- 中转密钥修改、显示、删除检查用户归属；创建用户 ID 来自登录身份。新增密钥证明账号操作权限被获取，不单独证明原密码被获取。
- 邮箱验证码使用 SecureRandom，按邮箱/场景隔离，单次成功即删除，错误 5 次后失效。没有发现通用固定验证码或忽略验证码的重置路径。
- 封禁用户会阻止 JWT 认证，中转密钥认证也检查封禁；本地验证网站认证封禁生效。
- localStorage 保存网页登录令牌，若同源脚本或浏览器扩展被攻陷会暴露令牌。本次前端检索未发现直接使用 v-html、innerHTML、eval 的入口，不能据此排除全部 XSS 或设备侧窃取。
- 自带 Nginx 仅监听 HTTP；可能由外层代理提供 HTTPS，尚未核实。若浏览器直接通过 HTTP 登录，密码和令牌可能被网络窃听。

## 立即处置建议（尚未在线执行）

1. **先保留证据**：备份受害用户信息、密钥记录与相关调用记录，导出事发前后代理/CDN、Nginx、后端日志。不要先清空日志或删除密钥。记录各日志时区，数据库配置使用 Asia/Shanghai。
2. **临时封禁受害账号并禁用异常密钥**，阻断继续创建密钥和消费。检查该用户全部密钥；攻击者也可能读取了原有密钥，恢复时建议全部轮换。
3. **彻底撤销攻击者网站会话**：目前单纯改密码无效，短暂封禁后立即解封也不会使旧 JWT 永久失效。可以轮换 JWT_SECRET 并重建所有 backend 实例，使所有用户重新登录；或部署会话撤销修复后，撤销受害用户旧会话再解封。多实例须统一更新。JWT 轮换不会撤销独立的中转 API 密钥，异常 API 密钥需要单独禁用/轮换。
4. 确认用户原邮箱和身份后重置为独立强密码，检查管理员账号和邮箱安全；保留已泄露密钥的 ID/预览与消费证据，不在工单、聊天中分享完整密钥。
5. 补上登录限流、改密撤销会话、改绑验证及安全审计日志，再恢复常态使用。确认对外登录入口启用 HTTPS。

上述建议包含影响真实用户的操作，本次仅检查与本地验证，未执行线上处置。

## 日志取证：用接口序列区分入口

| 接口/记录 | 判断价值 |
| --- | --- |
| `POST /api/auth/login` | 大量失败后成功更支持猜测/撞库；也可能直接使用泄露密码，必须结合状态与其他证据 |
| `POST /api/user/password` | 普通改密路径，当前必须通过原密码校验 |
| `POST /api/auth/email-code`、`POST /api/auth/forgot-password` | 找回密码路径，核对用户是否收到邮件、SMTP记录及事发时邮箱配置 |
| `PUT /api/user/profile` | 核对是否先改绑邮箱；仅看最终邮箱相同不能排除中途变更 |
| `PUT /api/admin/users/{id}` | 管理员可设置密码，需要核对是否存在对应管理员操作 |
| `POST /api/relay/tokens` | 新建密钥时间、来源 IP、UA，与密码操作的先后关系 |
| 用户 `updated_at` | 只能辅助定时，余额等其他操作也会更新，不能作为密码修改的专用时间 |
| `relay_token.created_at`、`last_used_at`、`token_preview`、关联调用记录 | 定位新增密钥与异常消费，不导出完整 token 字段 |

优先查看 HTTP 状态、来源 IP、时间、UA 和关联请求。仓库 Nginx 默认没有请求体、用户 ID 或登录成功审计，某些错误状态或路径不能单独证明对应的是哪个用户。JWT 被盗或伪造时也可能完全没有新的 login 请求。CDN/外层代理后日志 IP 可能只是代理地址。

当前 AuthService、UserService 没有独立的登录/改密/改绑安全日志，因此即便日志完整也可能无法最终归因。后续应记录事件类型、结果、用户 ID、可信 IP、UA、请求关联 ID及管理员操作者，严禁记录密码、验证码、完整 Authorization 或 API 密钥。

## 本地验证记录

日期：2026-10-18。采用真实认证/密码服务和真实 JWT/BCrypt；数据库与邮件使用 Mockito，HTTP 过滤器使用 Spring Mock 请求。仅虚拟 `.invalid` 邮箱和虚拟用户，不启动完整应用或联网测试站点。

- 原有 `AuthServiceImplTest` 12 项、`RegistrationPolicyTest` 15 项、`AdminAccountInitializerTest` 2 项，共 29 项通过。这些原有用例通过不代表不存在安全问题。
- 隔离程序复现 6 种行为：改绑后找回密码接管、旧会话在改密后有效、30 次错误登录无限流、默认密钥身份伪造、条件性调试验证码返回、邮箱枚举；另确认管理员响应实体序列化包含密码哈希。
- 反向检查：错误原密码被拒绝、中转密钥不能当网站 JWT、封禁阻止网站 JWT。

复现入口：`docs/security-audit-20261018/run-audit.ps1`。它编译当前后端，将产物存入 `backend/target`，通过隔离验证程序记录漏洞基线。**OBSERVED 表示漏洞被复现，并非安全检查通过**；修复后该程序应不再满足原观察，需要转为对应的安全回归用例。

源码：`docs/security-audit-20261018/AccountSecurityAudit.java`。这不是生产攻击工具，没有目标 URL 参数，不发送网络请求，不输出令牌、密码哈希或验证码。
