<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ApiCodeBlock from '@/components/docs/ApiCodeBlock.vue'

type Product = 'image' | 'relay'
type Language = 'curl' | 'javascript' | 'python'

interface DocSection {
  id: string
  label: string
  eyebrow: string
}

const route = useRoute()
const router = useRouter()
const product = ref<Product>(route.query.type === 'relay' ? 'relay' : 'image')
const language = ref<Language>('curl')
const mobileMenuOpen = ref(false)
const copiedValue = ref('')
let copiedTimer: ReturnType<typeof setTimeout> | undefined
let sectionObserver: IntersectionObserver | undefined
let previousTitle = ''

const origin = typeof window === 'undefined' ? 'https://your-domain.com' : window.location.origin
const imageApiBase = `${origin}/api`
const relayApiBase = `${origin}/api/v1`

const imageSections: DocSection[] = [
  { id: 'image-overview', label: '概览', eyebrow: '01' },
  { id: 'image-quickstart', label: '快速接入', eyebrow: '02' },
  { id: 'image-auth', label: '登录与鉴权', eyebrow: '03' },
  { id: 'image-generate', label: '提交生成任务', eyebrow: '04' },
  { id: 'image-task', label: '任务查询', eyebrow: '05' },
  { id: 'image-other', label: '配置与历史', eyebrow: '06' },
  { id: 'image-formats', label: '格式与后缀', eyebrow: '07' },
  { id: 'image-errors', label: '错误处理', eyebrow: '08' }
]

const relaySections: DocSection[] = [
  { id: 'relay-overview', label: '概览', eyebrow: '01' },
  { id: 'relay-quickstart', label: '快速接入', eyebrow: '02' },
  { id: 'relay-auth', label: 'API Key 鉴权', eyebrow: '03' },
  { id: 'relay-endpoints', label: '接口清单', eyebrow: '04' },
  { id: 'relay-openai', label: 'OpenAI 格式', eyebrow: '05' },
  { id: 'relay-anthropic', label: 'Anthropic 格式', eyebrow: '06' },
  { id: 'relay-formats', label: '数据格式', eyebrow: '07' },
  { id: 'relay-suffixes', label: '路径与兼容后缀', eyebrow: '08' }
]

const currentSections = computed(() => product.value === 'image' ? imageSections : relaySections)
const activeSection = ref(currentSections.value[0]!.id)

const imageExamples = computed<Record<Language, string>>(() => ({
  curl: `curl -X POST "${imageApiBase}/image/generate" \\
  -H "Authorization: Bearer YOUR_JWT" \\
  -H "Content-Type: application/json" \\
  -d '{
    "prompt": "雨夜霓虹街道，电影感光影",
    "qualityCode": "1k",
    "size": "1024x1024",
    "referenceImages": []
  }'`,
  javascript: `const API_BASE = '${imageApiBase}'
const token = 'YOUR_JWT'

const request = await fetch(\`\${API_BASE}/image/generate\`, {
  method: 'POST',
  headers: {
    Authorization: \`Bearer \${token}\`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    prompt: '雨夜霓虹街道，电影感光影',
    qualityCode: '1k',
    size: '1024x1024',
    referenceImages: []
  })
})

const submitted = await request.json()
if (!request.ok || submitted.code !== 200) {
  throw new Error(submitted.message || '提交失败')
}

let record = submitted.data
while (record.status === 'pending') {
  await new Promise(resolve => setTimeout(resolve, 1500))
  const response = await fetch(\`\${API_BASE}/image/\${record.id}\`, {
    headers: { Authorization: \`Bearer \${token}\` }
  })
  record = (await response.json()).data
}

console.log(record.status, record.generatedImageUrl)`,
  python: `import time
import requests

API_BASE = "${imageApiBase}"
TOKEN = "YOUR_JWT"
headers = {"Authorization": f"Bearer {TOKEN}"}

submitted = requests.post(
    f"{API_BASE}/image/generate",
    headers=headers,
    json={
        "prompt": "雨夜霓虹街道，电影感光影",
        "qualityCode": "1k",
        "size": "1024x1024",
        "referenceImages": [],
    },
).json()

record = submitted["data"]
while record["status"] == "pending":
    time.sleep(1.5)
    record = requests.get(
        f"{API_BASE}/image/{record['id']}",
        headers=headers,
    ).json()["data"]

print(record["status"], record.get("generatedImageUrl"))`
}))

const relayExamples = computed<Record<Language, string>>(() => ({
  curl: `curl -X POST "${relayApiBase}/chat/completions" \\
  -H "Authorization: Bearer YOUR_RELAY_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "YOUR_MODEL_NAME",
    "messages": [
      {"role": "user", "content": "你好，请介绍一下你自己"}
    ],
    "stream": false
  }'`,
  javascript: `const response = await fetch(
  '${relayApiBase}/chat/completions',
  {
    method: 'POST',
    headers: {
      Authorization: 'Bearer YOUR_RELAY_API_KEY',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model: 'YOUR_MODEL_NAME',
      messages: [
        { role: 'user', content: '你好，请介绍一下你自己' }
      ],
      stream: false
    })
  }
)

if (!response.ok) throw new Error(await response.text())
const result = await response.json()
console.log(result.choices[0].message.content)`,
  python: `import requests

response = requests.post(
    "${relayApiBase}/chat/completions",
    headers={
        "Authorization": "Bearer YOUR_RELAY_API_KEY",
        "Content-Type": "application/json",
    },
    json={
        "model": "YOUR_MODEL_NAME",
        "messages": [
            {"role": "user", "content": "你好，请介绍一下你自己"}
        ],
        "stream": False,
    },
)
response.raise_for_status()
print(response.json()["choices"][0]["message"]["content"])`
}))

const activeExample = computed(() => product.value === 'image'
  ? imageExamples.value[language.value]
  : relayExamples.value[language.value])

const authRequest = `curl -X POST "${imageApiBase}/auth/login" \\
  -H "Content-Type: application/json" \\
  -d '{
    "username": "YOUR_USERNAME",
    "password": "YOUR_PASSWORD"
  }'`

const authResponse = `{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 12,
      "username": "demo_user",
      "role": "USER"
    }
  }
}`

const configsResponse = `{
  "code": 200,
  "message": "success",
  "data": [
    {
      "code": "1k",
      "name": "标准画质",
      "model": "gpt-image-1",
      "size": "1024x1024",
      "quality": "medium",
      "price": 0.12,
      "enabled": true
    }
  ]
}`

const taskResponse = `{
  "code": 200,
  "message": "success",
  "data": {
    "id": 381,
    "prompt": "雨夜霓虹街道，电影感光影",
    "status": "success",
    "generatedImageUrl": "/api/images/demo_user/381.png",
    "generationModel": "gpt-image-1",
    "cost": 0.12,
    "errorStatusCode": null,
    "errorType": null,
    "errorMessage": null,
    "createdAt": "2026-07-26T14:30:12"
  }
}`

const modelsRequest = `curl "${relayApiBase}/models" \\
  -H "Authorization: Bearer YOUR_RELAY_API_KEY"`

const responsesRequest = `curl -X POST "${relayApiBase}/responses" \\
  -H "Authorization: Bearer YOUR_RELAY_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "YOUR_MODEL_NAME",
    "input": "用三句话解释什么是向量数据库",
    "stream": false
  }'`

const anthropicRequest = `curl -X POST "${imageApiBase}/v1/messages" \\
  -H "x-api-key: YOUR_RELAY_API_KEY" \\
  -H "anthropic-version: 2023-06-01" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "YOUR_CLAUDE_MODEL",
    "max_tokens": 1024,
    "messages": [
      {"role": "user", "content": "你好"}
    ]
  }'`

const imageEditRequest = `curl -X POST "${relayApiBase}/images/edits" \\
  -H "Authorization: Bearer YOUR_RELAY_API_KEY" \\
  -F "model=YOUR_IMAGE_MODEL" \\
  -F "prompt=将天空改为日落" \\
  -F "image=@reference.png"`

async function copyText(value: string, key: string) {
  try {
    await navigator.clipboard.writeText(value)
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
  }

  copiedValue.value = key
  if (copiedTimer) window.clearTimeout(copiedTimer)
  copiedTimer = window.setTimeout(() => {
    copiedValue.value = ''
  }, 1600)
}

function setProduct(value: Product) {
  if (product.value === value) return
  product.value = value
  activeSection.value = value === 'image' ? imageSections[0]!.id : relaySections[0]!.id
  mobileMenuOpen.value = false
  void router.replace({ path: '/docs', query: value === 'relay' ? { type: 'relay' } : {} })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function navigateTo(id: string) {
  mobileMenuOpen.value = false
  activeSection.value = id
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function connectSectionObserver() {
  sectionObserver?.disconnect()
  sectionObserver = new IntersectionObserver((entries) => {
    const visible = entries
      .filter(entry => entry.isIntersecting)
      .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
    if (visible[0]?.target.id) activeSection.value = visible[0].target.id
  }, {
    rootMargin: '-92px 0px -68% 0px',
    threshold: [0, 0.15]
  })

  document.querySelectorAll<HTMLElement>('[data-doc-section]').forEach(section => {
    sectionObserver?.observe(section)
  })
}

watch(product, async () => {
  await nextTick()
  connectSectionObserver()
})

onMounted(async () => {
  previousTitle = document.title
  document.title = 'API 对接文档 | imageCreater'
  await nextTick()
  connectSectionObserver()
})

onBeforeUnmount(() => {
  sectionObserver?.disconnect()
  if (copiedTimer) window.clearTimeout(copiedTimer)
  document.title = previousTitle
})
</script>

<template>
  <div class="docs-page">
    <header class="docs-header">
      <div class="docs-header-inner">
        <button class="brand-button" type="button" @click="router.push('/')">
          <img src="/favicon.ico" alt="" />
          <span class="brand-name">imageCreater</span>
          <span class="brand-divider"></span>
          <span class="brand-context">API 文档</span>
        </button>

        <div class="product-switch product-switch-desktop" role="tablist" aria-label="文档类型">
          <button
            type="button"
            :class="{ active: product === 'image' }"
            :aria-selected="product === 'image'"
            @click="setProduct('image')"
          >
            图片生成 API
          </button>
          <button
            type="button"
            :class="{ active: product === 'relay' }"
            :aria-selected="product === 'relay'"
            @click="setProduct('relay')"
          >
            AI 中转 API
          </button>
        </div>

        <div class="header-actions">
          <button class="header-link desktop-only" type="button" @click="router.push('/')">返回首页</button>
          <button class="header-link mobile-toc-button" type="button" @click="mobileMenuOpen = true">目录</button>
          <button class="console-button" type="button" @click="router.push('/relay')">进入控制台</button>
        </div>
      </div>
    </header>

    <div class="mobile-product-bar">
      <div class="product-switch" role="tablist" aria-label="文档类型">
        <button type="button" :class="{ active: product === 'image' }" @click="setProduct('image')">图片生成 API</button>
        <button type="button" :class="{ active: product === 'relay' }" @click="setProduct('relay')">AI 中转 API</button>
      </div>
    </div>

    <div class="docs-layout">
      <aside class="section-sidebar" aria-label="文档章节">
        <p class="sidebar-label">CONTENTS</p>
        <nav>
          <button
            v-for="section in currentSections"
            :key="section.id"
            type="button"
            :class="{ active: activeSection === section.id }"
            @click="navigateTo(section.id)"
          >
            <span>{{ section.eyebrow }}</span>
            {{ section.label }}
          </button>
        </nav>
        <div class="sidebar-support">
          <span>接口状态</span>
          <strong><i></i> 服务可用</strong>
          <p>文档内容与当前服务端路由同步。</p>
        </div>
      </aside>

      <main class="docs-content">
        <template v-if="product === 'image'">
          <section id="image-overview" class="doc-section intro-section" data-doc-section>
            <div class="section-kicker">PLATFORM API · V1</div>
            <h1>图片生成 API</h1>
            <p class="intro-copy">
              使用账户 JWT 提交图片生成任务、查询异步进度并获取最终图片地址。适合将 imageCreater 的生图能力接入网站、工作流或内部工具。
            </p>

            <div class="base-url-box">
              <div>
                <span>Base URL</span>
                <code>{{ imageApiBase }}</code>
              </div>
              <button type="button" @click="copyText(imageApiBase, 'image-base')">
                {{ copiedValue === 'image-base' ? '已复制' : '复制地址' }}
              </button>
            </div>

            <div class="contract-row">
              <div><span>认证</span><strong>Bearer JWT</strong></div>
              <div><span>请求格式</span><strong>application/json</strong></div>
              <div><span>任务模式</span><strong>异步轮询</strong></div>
              <div><span>图片后缀</span><strong>.png</strong></div>
            </div>
          </section>

          <section id="image-quickstart" class="doc-section" data-doc-section>
            <div class="section-kicker">QUICK START</div>
            <h2>五步完成首次调用</h2>
            <p class="section-lead">先登录获得 JWT，再读取管理员开放的画质配置。生成接口立即返回任务记录，客户端需要轮询任务详情。</p>

            <div class="step-list">
              <article>
                <span>01</span>
                <div><h3>登录账户</h3><p>调用 <code>POST /auth/login</code>，保存响应中的 <code>data.token</code>。</p></div>
              </article>
              <article>
                <span>02</span>
                <div><h3>读取可用配置</h3><p>调用 <code>GET /image/configs</code>，从启用项中选择 <code>code</code> 作为 <code>qualityCode</code>。</p></div>
              </article>
              <article>
                <span>03</span>
                <div><h3>提交生成任务</h3><p>发送提示词、画质代码、尺寸和可选参考图，记录返回的任务 <code>id</code>。</p></div>
              </article>
              <article>
                <span>04</span>
                <div><h3>轮询任务状态</h3><p>每 1 至 2 秒查询 <code>GET /image/{id}</code>，直到状态不再是 <code>pending</code>。</p></div>
              </article>
              <article>
                <span>05</span>
                <div><h3>读取图片地址</h3><p><code>success</code> 时使用 <code>generatedImageUrl</code>；<code>failed</code> 时读取错误字段。</p></div>
              </article>
            </div>

            <div class="language-tabs" role="tablist" aria-label="示例语言">
              <button v-for="item in (['curl', 'javascript', 'python'] as Language[])" :key="item" type="button" :class="{ active: language === item }" @click="language = item">
                {{ item === 'javascript' ? 'JavaScript' : item === 'python' ? 'Python' : 'cURL' }}
              </button>
            </div>
            <ApiCodeBlock :code="activeExample" :language="language" label="完整生成与轮询示例" />
          </section>

          <section id="image-auth" class="doc-section" data-doc-section>
            <div class="section-kicker">AUTHENTICATION</div>
            <h2>登录与鉴权</h2>
            <p class="section-lead">图片任务 API 使用用户账户 JWT。除登录接口和最终图片静态地址外，请求都应携带 Authorization 请求头。</p>

            <div class="endpoint-title">
              <span class="method method-post">POST</span>
              <code>/api/auth/login</code>
              <em>获取 JWT</em>
            </div>
            <ApiCodeBlock :code="authRequest" language="bash" label="登录请求" />
            <ApiCodeBlock :code="authResponse" language="json" label="成功响应" />

            <div class="notice notice-warning">
              <strong>请勿将登录密码或 JWT 写入前端公开代码。</strong>
              <p>服务端集成应通过环境变量保存凭据；浏览器应用应使用当前登录会话，并在收到 401 时重新登录。</p>
            </div>
          </section>

          <section id="image-generate" class="doc-section" data-doc-section>
            <div class="section-kicker">GENERATE</div>
            <h2>提交生成任务</h2>
            <div class="endpoint-title">
              <span class="method method-post">POST</span>
              <code>/api/image/generate</code>
              <em>异步</em>
            </div>

            <div class="table-wrap">
              <table>
                <thead><tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
                <tbody>
                  <tr><td><code>prompt</code></td><td>string</td><td><span class="required">是</span></td><td>图片描述或编辑指令。</td></tr>
                  <tr><td><code>qualityCode</code></td><td>string</td><td><span class="required">是</span></td><td>来自 <code>/image/configs</code> 的启用配置 code，不要硬编码猜测。</td></tr>
                  <tr><td><code>size</code></td><td>string</td><td>否</td><td><code>auto</code> 或 <code>WIDTHxHEIGHT</code>；留空使用该画质配置默认尺寸。</td></tr>
                  <tr><td><code>referenceImages</code></td><td>string[]</td><td>否</td><td>Base64 图片 Data URL 数组，最多 4 张。</td></tr>
                </tbody>
              </table>
            </div>

            <div class="async-flow" aria-label="异步任务状态流程">
              <div><small>提交</small><strong>POST generate</strong></div>
              <span>→</span>
              <div><small>返回</small><strong>pending + id</strong></div>
              <span>→</span>
              <div><small>轮询</small><strong>GET /image/{id}</strong></div>
              <span>→</span>
              <div><small>结束</small><strong>success / failed</strong></div>
            </div>

            <div class="notice notice-info">
              <strong>接口返回 200 仅表示任务成功入队。</strong>
              <p>真正的生成结果由任务状态决定。推荐轮询间隔 1 至 2 秒，并为整体流程设置合理超时。</p>
            </div>
          </section>

          <section id="image-task" class="doc-section" data-doc-section>
            <div class="section-kicker">TASK STATUS</div>
            <h2>查询任务与读取结果</h2>
            <div class="endpoint-title">
              <span class="method method-get">GET</span>
              <code>/api/image/{id}</code>
              <em>仅可查询自己的任务</em>
            </div>

            <div class="status-grid">
              <article><span class="status-dot pending"></span><strong>pending</strong><p>任务排队或生成中，继续轮询。</p></article>
              <article><span class="status-dot success"></span><strong>success</strong><p>生成完成，读取图片地址和实际扣费。</p></article>
              <article><span class="status-dot failed"></span><strong>failed</strong><p>任务失败，读取状态码、类型与错误信息。</p></article>
            </div>

            <ApiCodeBlock :code="taskResponse" language="json" label="任务成功响应" />

            <div class="field-notes">
              <div><code>generatedImageUrl</code><p>站内相对地址。跨域或离线程序应拼接当前站点 Origin。</p></div>
              <div><code>cost</code><p>本次任务实际扣除金额，以服务端返回值为准。</p></div>
              <div><code>errorStatusCode</code></div>
              <div><code>errorType</code></div>
              <div><code>errorMessage</code></div>
            </div>
          </section>

          <section id="image-other" class="doc-section" data-doc-section>
            <div class="section-kicker">REFERENCE</div>
            <h2>配置、预估与历史记录</h2>
            <div class="endpoint-list">
              <article><span class="method method-get">GET</span><code>/api/image/configs</code><p>读取管理员当前开放的画质、模型、默认尺寸与价格。</p></article>
              <article><span class="method method-get">GET</span><code>/api/image/estimate</code><p>返回近期平均生成耗时 <code>averageDurationMs</code> 和样本数 <code>sampleCount</code>。</p></article>
              <article><span class="method method-get">GET</span><code>/api/image/history?page=1&amp;size=10</code><p>分页读取当前用户的历史任务。</p></article>
              <article><span class="method method-delete">DELETE</span><code>/api/image/{id}</code><p>删除当前用户指定的图片任务记录。</p></article>
            </div>
            <ApiCodeBlock :code="configsResponse" language="json" label="配置列表响应示例" />
          </section>

          <section id="image-formats" class="doc-section" data-doc-section>
            <div class="section-kicker">FORMATS &amp; SUFFIXES</div>
            <h2>支持的格式、尺寸与后缀</h2>
            <div class="format-grid">
              <article><span>请求正文</span><strong>JSON</strong><p><code>Content-Type: application/json</code>，字符编码使用 UTF-8。</p></article>
              <article><span>参考图片</span><strong>Data URL</strong><p><code>data:image/&lt;type&gt;;base64,...</code>，单张编码长度不超过 12 MiB 字符。</p></article>
              <article><span>生成结果</span><strong>PNG</strong><p>公开地址格式为 <code>/api/images/{username}/{filename}.png</code>。</p></article>
            </div>

            <div class="table-wrap">
              <table>
                <thead><tr><th>类别</th><th>支持值</th><th>限制</th></tr></thead>
                <tbody>
                  <tr><td>尺寸</td><td><code>auto</code>、<code>1024x1024</code> 等</td><td>宽高分别为 256 至 8192 像素，格式必须为整数 <code>宽x高</code>。</td></tr>
                  <tr><td>参考图 MIME</td><td><code>image/png</code>、<code>image/jpeg</code>、<code>image/jpg</code>、<code>image/webp</code>、<code>image/gif</code></td><td>最多 4 张，必须是合法 Base64 Data URL。</td></tr>
                  <tr><td>结果后缀</td><td><code>.png</code></td><td>即使参考图为 JPG、WebP 或 GIF，平台保存结果仍使用 PNG 地址。</td></tr>
                  <tr><td>分页参数</td><td><code>page</code>、<code>size</code></td><td>均为正整数；默认值分别为 1 和 10。</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <section id="image-errors" class="doc-section" data-doc-section>
            <div class="section-kicker">ERRORS</div>
            <h2>统一响应与错误处理</h2>
            <p class="section-lead">平台业务接口使用统一响应包。客户端应同时检查 HTTP 状态和响应体中的 <code>code</code>，不要只判断网络请求是否完成。</p>

            <div class="response-contract">
              <code>{ code: number, message: string, data: T | null }</code>
            </div>
            <div class="table-wrap">
              <table>
                <thead><tr><th>状态</th><th>常见原因</th><th>处理建议</th></tr></thead>
                <tbody>
                  <tr><td><code>400</code></td><td>参数、尺寸、Base64 或画质代码无效</td><td>直接展示 <code>message</code>，修正请求后重试。</td></tr>
                  <tr><td><code>401</code></td><td>JWT 缺失、过期或无效</td><td>重新登录并替换 Authorization。</td></tr>
                  <tr><td><code>403</code></td><td>账户权限不足或被限制</td><td>停止自动重试，检查账户状态。</td></tr>
                  <tr><td><code>404</code></td><td>任务不存在或不属于当前用户</td><td>核对任务 id 与登录账户。</td></tr>
                  <tr><td><code>429</code></td><td>调用频率或上游额度受限</td><td>指数退避后重试。</td></tr>
                  <tr><td><code>500 / 502</code></td><td>服务端或上游生成服务异常</td><td>记录 message 和任务 id，稍后重试。</td></tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>

        <template v-else>
          <section id="relay-overview" class="doc-section intro-section" data-doc-section>
            <div class="section-kicker">COMPATIBLE API · V1</div>
            <h1>AI 中转 API</h1>
            <p class="intro-copy">使用中转 API Key 接入兼容 OpenAI 与 Anthropic 协议的模型。原有 SDK 通常只需替换 Base URL、API Key 和模型名。</p>

            <div class="base-url-box">
              <div><span>推荐 Base URL</span><code>{{ relayApiBase }}</code></div>
              <button type="button" @click="copyText(relayApiBase, 'relay-base')">{{ copiedValue === 'relay-base' ? '已复制' : '复制地址' }}</button>
            </div>

            <div class="contract-row">
              <div><span>认证</span><strong>Relay API Key</strong></div>
              <div><span>同步响应</span><strong>JSON</strong></div>
              <div><span>流式响应</span><strong>SSE</strong></div>
              <div><span>文件请求</span><strong>Multipart</strong></div>
            </div>
          </section>

          <section id="relay-quickstart" class="doc-section" data-doc-section>
            <div class="section-kicker">QUICK START</div>
            <h2>三步接入现有客户端</h2>
            <div class="step-list compact-steps">
              <article><span>01</span><div><h3>创建中转密钥</h3><p>登录后进入中转站，在令牌管理中创建并妥善保存 API Key。</p></div></article>
              <article><span>02</span><div><h3>获取可用模型</h3><p>调用 <code>GET /v1/models</code>，选择当前令牌有权访问的模型 id。</p></div></article>
              <article><span>03</span><div><h3>替换客户端配置</h3><p>将 SDK Base URL 设为本站 <code>/api/v1</code>，并替换 API Key 与模型名。</p></div></article>
            </div>

            <div class="language-tabs" role="tablist" aria-label="示例语言">
              <button v-for="item in (['curl', 'javascript', 'python'] as Language[])" :key="item" type="button" :class="{ active: language === item }" @click="language = item">
                {{ item === 'javascript' ? 'JavaScript' : item === 'python' ? 'Python' : 'cURL' }}
              </button>
            </div>
            <ApiCodeBlock :code="activeExample" :language="language" label="Chat Completions 示例" />
          </section>

          <section id="relay-auth" class="doc-section" data-doc-section>
            <div class="section-kicker">AUTHENTICATION</div>
            <h2>API Key 鉴权</h2>
            <p class="section-lead">中转 API Key 与账户登录 JWT 不同。请在中转站创建令牌，并按以下优先级传递。</p>
            <div class="auth-options">
              <article class="recommended"><span>推荐</span><code>Authorization: Bearer YOUR_RELAY_API_KEY</code><p>兼容 OpenAI SDK 及绝大多数客户端。</p></article>
              <article><span>兼容</span><code>x-api-key: YOUR_RELAY_API_KEY</code><p>适用于 Anthropic SDK 或自定义 HTTP 客户端。</p></article>
              <article><span>备用</span><code>?key=YOUR_RELAY_API_KEY</code><p>服务端支持，但 URL 可能进入日志与历史记录，不建议生产环境使用。</p></article>
            </div>
            <ApiCodeBlock :code="modelsRequest" language="bash" label="验证密钥并列出模型" />
          </section>

          <section id="relay-endpoints" class="doc-section" data-doc-section>
            <div class="section-kicker">ENDPOINTS</div>
            <h2>当前支持的接口</h2>
            <div class="table-wrap endpoint-table">
              <table>
                <thead><tr><th>方法</th><th>接口</th><th>请求格式</th><th>用途</th></tr></thead>
                <tbody>
                  <tr><td><span class="method method-get">GET</span></td><td><code>/v1/models</code></td><td>HTTP</td><td>列出当前令牌可用模型</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/chat/completions</code></td><td>JSON / SSE</td><td>OpenAI Chat Completions</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/responses</code></td><td>JSON / SSE</td><td>OpenAI Responses API</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/responses/compact</code></td><td>JSON</td><td>压缩 Responses 上下文</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/completions</code></td><td>JSON / SSE</td><td>传统文本补全</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/embeddings</code></td><td>JSON</td><td>文本向量</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/messages</code></td><td>JSON / SSE</td><td>Anthropic Messages</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/messages/count_tokens</code></td><td>JSON</td><td>Anthropic Token 计数</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/images/generations</code></td><td>JSON / Multipart</td><td>图片生成</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/images/edits</code></td><td>Multipart</td><td>参考图编辑</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/moderations</code></td><td>JSON</td><td>内容审核</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/audio/transcriptions</code></td><td>JSON</td><td>音频转写协议转发</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/audio/translations</code></td><td>JSON</td><td>音频翻译协议转发</td></tr>
                  <tr><td><span class="method method-post">POST</span></td><td><code>/v1/audio/speech</code></td><td>JSON</td><td>文本转语音</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <section id="relay-openai" class="doc-section" data-doc-section>
            <div class="section-kicker">OPENAI COMPATIBLE</div>
            <h2>OpenAI 格式调用</h2>
            <p class="section-lead">Chat Completions 与 Responses API 均按上游协议透传请求字段和响应。模型名必须来自 <code>/v1/models</code>。</p>
            <div class="endpoint-title"><span class="method method-post">POST</span><code>/api/v1/responses</code><em>JSON / SSE</em></div>
            <ApiCodeBlock :code="responsesRequest" language="bash" label="Responses API" />
            <div class="notice notice-info">
              <strong>SDK 配置要点</strong>
              <p>OpenAI 兼容 SDK 的 <code>baseURL</code> / <code>base_url</code> 使用 <code>{{ relayApiBase }}</code>。不要再手动追加 <code>/chat/completions</code> 到 Base URL。</p>
            </div>
          </section>

          <section id="relay-anthropic" class="doc-section" data-doc-section>
            <div class="section-kicker">ANTHROPIC COMPATIBLE</div>
            <h2>Anthropic Messages 格式</h2>
            <p class="section-lead">支持 <code>x-api-key</code>、<code>anthropic-version</code> 与 <code>anthropic-beta</code> 请求头。Anthropic SDK 通常会自行追加 <code>/v1/messages</code>。</p>
            <div class="endpoint-title"><span class="method method-post">POST</span><code>/api/v1/messages</code><em>JSON / SSE</em></div>
            <ApiCodeBlock :code="anthropicRequest" language="bash" label="Anthropic Messages" />
          </section>

          <section id="relay-formats" class="doc-section" data-doc-section>
            <div class="section-kicker">DATA FORMATS</div>
            <h2>JSON、SSE 与文件上传</h2>
            <div class="format-grid">
              <article><span>常规请求</span><strong>JSON</strong><p>聊天、Responses、向量、审核和音频路由使用 <code>application/json</code>。</p></article>
              <article><span>流式请求</span><strong>SSE</strong><p>请求体设置 <code>stream: true</code>，响应类型为 <code>text/event-stream</code>。</p></article>
              <article><span>图片文件</span><strong>Multipart</strong><p>图片生成接受 <code>image</code>；图片编辑同时接受 <code>image</code> 和 <code>image[]</code>。</p></article>
            </div>
            <ApiCodeBlock :code="imageEditRequest" language="bash" label="图片编辑 Multipart 示例" />
            <div class="notice notice-warning">
              <strong>当前音频转写与翻译路由只接收 JSON。</strong>
              <p>标准 OpenAI Multipart 音频文件上传尚未由当前控制器接收；调用前请确认所选上游渠道支持 JSON 形式的音频数据。</p>
            </div>
          </section>

          <section id="relay-suffixes" class="doc-section" data-doc-section>
            <div class="section-kicker">PATH COMPATIBILITY</div>
            <h2>路径、接口后缀与兼容别名</h2>
            <p class="section-lead">推荐始终使用标准 <code>/api/v1</code> Base URL。以下别名用于兼容部分会重复追加 <code>/v1</code> 或省略版本号的客户端。</p>
            <div class="table-wrap">
              <table>
                <thead><tr><th>标准路径</th><th>兼容路径</th><th>说明</th></tr></thead>
                <tbody>
                  <tr><td><code>/api/v1/models</code></td><td><code>/api/models</code><br><code>/api/v1/v1/models</code></td><td>模型列表</td></tr>
                  <tr><td><code>/api/v1/messages</code></td><td><code>/api/messages</code><br><code>/api/v1/v1/messages</code></td><td>Anthropic Messages</td></tr>
                  <tr><td><code>/api/v1/messages/count_tokens</code></td><td><code>/api/messages/count_tokens</code><br><code>/api/v1/v1/messages/count_tokens</code></td><td>Anthropic Token 计数</td></tr>
                  <tr><td><code>/api/v1/responses</code></td><td><code>/api/responses</code><br><code>/api/v1/v1/responses</code></td><td>Responses API</td></tr>
                  <tr><td><code>/api/v1/responses/compact</code></td><td><code>/api/responses/compact</code><br><code>/api/v1/v1/responses/compact</code></td><td>Responses Compact</td></tr>
                </tbody>
              </table>
            </div>
            <div class="notice notice-info">
              <strong>接口路径没有文件扩展名。</strong>
              <p><code>.json</code>、<code>.html</code> 等后缀均不需要。响应格式由 <code>Content-Type</code> 和 <code>stream</code> 参数决定。</p>
            </div>
          </section>
        </template>

        <footer class="docs-footer">
          <img src="/favicon.ico" alt="" />
          <div><strong>imageCreater API</strong><span>接口行为以当前服务端配置与上游模型能力为准。</span></div>
          <button type="button" @click="router.push('/relay')">管理中转密钥</button>
        </footer>
      </main>

      <aside class="facts-sidebar" aria-label="接入摘要">
        <div class="facts-panel">
          <p>接入摘要</p>
          <template v-if="product === 'image'">
            <dl><dt>凭据</dt><dd>账户 JWT</dd><dt>Base URL</dt><dd><code>/api</code></dd><dt>结果</dt><dd>异步任务</dd><dt>输出</dt><dd>PNG URL</dd></dl>
            <button type="button" @click="navigateTo('image-quickstart')">查看快速接入</button>
          </template>
          <template v-else>
            <dl><dt>凭据</dt><dd>Relay API Key</dd><dt>Base URL</dt><dd><code>/api/v1</code></dd><dt>协议</dt><dd>OpenAI / Anthropic</dd><dt>流式</dt><dd>SSE</dd></dl>
            <button type="button" @click="navigateTo('relay-endpoints')">查看接口清单</button>
          </template>
        </div>
        <div class="security-note">
          <span>SECURITY</span>
          <p>密钥只展示一次时请立即保存。不要提交到 Git、前端源码或公开日志。</p>
        </div>
      </aside>
    </div>

    <Transition name="drawer-fade">
      <div v-if="mobileMenuOpen" class="mobile-drawer-backdrop" @click.self="mobileMenuOpen = false">
        <aside class="mobile-drawer" aria-label="移动端文档目录">
          <div class="mobile-drawer-header"><strong>文档目录</strong><button type="button" @click="mobileMenuOpen = false">关闭</button></div>
          <nav>
            <button v-for="section in currentSections" :key="section.id" type="button" :class="{ active: activeSection === section.id }" @click="navigateTo(section.id)">
              <span>{{ section.eyebrow }}</span>{{ section.label }}
            </button>
          </nav>
        </aside>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.docs-page {
  --docs-accent: #0891b2;
  --docs-accent-dark: #0e7490;
  --docs-ink: #0f172a;
  --docs-muted: #64748b;
  --docs-line: #dce3ec;
  min-height: 100vh;
  background:
    linear-gradient(90deg, rgba(226, 232, 240, 0.34) 1px, transparent 1px),
    #f8fafc;
  background-size: 64px 64px;
  color: var(--docs-ink);
  font-family: Inter, "Noto Sans SC", "Microsoft YaHei", sans-serif;
  animation: docsPageEnter 0.42s ease-out both;
}

button { letter-spacing: 0; }

.docs-header {
  position: sticky;
  top: 0;
  z-index: 50;
  border-bottom: 1px solid rgba(203, 213, 225, 0.86);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(18px) saturate(1.15);
  animation: docsHeaderEnter 0.58s cubic-bezier(0.22, 0.8, 0.24, 1) both;
}

.docs-header-inner {
  display: grid;
  width: min(100% - 40px, 1420px);
  min-height: 68px;
  margin: 0 auto;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 24px;
}

.brand-button {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  padding: 6px 0;
  color: var(--docs-ink);
  cursor: pointer;
}

.brand-button img,
.docs-footer img {
  width: 28px;
  height: 28px;
  border-radius: 6px;
}

.brand-name { font-size: 16px; font-weight: 900; }
.brand-divider { width: 1px; height: 18px; background: #cbd5e1; }
.brand-context { color: #64748b; font-size: 13px; font-weight: 700; }

.product-switch {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid #dbe3ec;
  border-radius: 8px;
  background: #f1f5f9;
  padding: 3px;
}

.product-switch button {
  border: 0;
  border-radius: 5px;
  background: transparent;
  padding: 8px 14px;
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
  transition: background-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}

.product-switch button.active {
  background: #fff;
  color: #0e7490;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.08);
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.header-link,
.console-button {
  border-radius: 7px;
  padding: 9px 13px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
  transition: transform 0.18s ease, background-color 0.18s ease, border-color 0.18s ease;
}

.header-link { border: 1px solid transparent; background: transparent; color: #475569; }
.header-link:hover { border-color: #cbd5e1; background: #f8fafc; }
.console-button { border: 1px solid #0f172a; background: #0f172a; color: #fff; }
.console-button:hover { transform: translateY(-1px); background: #0e7490; border-color: #0e7490; }
.mobile-toc-button { display: none; }
.mobile-product-bar { display: none; }

.docs-layout {
  display: grid;
  width: min(100% - 48px, 1420px);
  margin: 0 auto;
  grid-template-columns: 216px minmax(0, 820px) 244px;
  justify-content: space-between;
  gap: 40px;
}

.section-sidebar,
.facts-sidebar {
  position: fixed;
  top: 92px;
  z-index: 10;
  max-height: calc(100vh - 112px);
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.36) transparent;
}

.section-sidebar {
  left: max(24px, calc((100vw - 1420px) / 2));
  width: 216px;
  padding: 42px 0 28px;
  animation: docsLeftRailEnter 0.66s 0.08s cubic-bezier(0.22, 0.8, 0.24, 1) both;
}
.sidebar-label { margin: 0 0 12px 12px; color: #94a3b8; font-size: 10px; font-weight: 900; }
.section-sidebar nav { display: grid; gap: 3px; }

.section-sidebar nav button,
.mobile-drawer nav button {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  padding: 9px 12px;
  color: #64748b;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.section-sidebar nav button span,
.mobile-drawer nav button span { color: #a8b4c4; font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; }
.section-sidebar nav button:hover { background: #eef3f7; color: #0f172a; transform: translateX(2px); }
.section-sidebar nav button.active { background: #e6f6f9; color: #0e7490; }
.section-sidebar nav button.active span { color: #0891b2; }

.sidebar-support {
  margin-top: 26px;
  border-top: 1px solid #dce3ec;
  padding: 20px 12px 0;
}
.sidebar-support > span { color: #94a3b8; font-size: 10px; font-weight: 900; }
.sidebar-support strong { display: flex; align-items: center; gap: 7px; margin-top: 8px; font-size: 12px; }
.sidebar-support i { width: 7px; height: 7px; border-radius: 50%; background: #10b981; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.12); }
.sidebar-support p { margin: 7px 0 0; color: #94a3b8; font-size: 11px; line-height: 1.6; }

.docs-content {
  min-width: 0;
  grid-column: 2;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 0 0 1px rgba(226, 232, 240, 0.72);
  animation: docsContentEnter 0.7s 0.04s cubic-bezier(0.22, 0.8, 0.24, 1) both;
}
.doc-section { scroll-margin-top: 88px; border-bottom: 1px solid var(--docs-line); padding: 68px 64px; }
.intro-section { padding-top: 76px; }
.section-kicker { margin-bottom: 16px; color: var(--docs-accent-dark); font-family: "Cascadia Code", Consolas, monospace; font-size: 11px; font-weight: 900; }
h1, h2, h3, p { letter-spacing: 0; }
h1 { max-width: 720px; margin: 0; font-size: 45px; font-weight: 900; line-height: 1.1; }
h2 { margin: 0; font-size: 29px; font-weight: 900; line-height: 1.25; }
h3 { margin: 0; font-size: 15px; font-weight: 850; }
.intro-copy { max-width: 700px; margin: 20px 0 0; color: #475569; font-size: 16px; line-height: 1.85; }
.section-lead { max-width: 720px; margin: 14px 0 0; color: #64748b; font-size: 14px; line-height: 1.85; }
code { border-radius: 4px; color: #0e7490; font-family: "Cascadia Code", "SFMono-Regular", Consolas, monospace; font-size: 0.9em; }
p code, td code, li code { background: #edf5f7; padding: 2px 5px; }

.base-url-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 32px;
  border: 1px solid #cbd5e1;
  border-left: 4px solid var(--docs-accent);
  border-radius: 7px;
  background: #f8fafc;
  padding: 16px 18px;
}
.base-url-box div { display: grid; min-width: 0; gap: 6px; }
.base-url-box span { color: #94a3b8; font-size: 10px; font-weight: 900; text-transform: uppercase; }
.base-url-box code { overflow: hidden; color: #0f172a; font-size: 13px; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.base-url-box button { flex: 0 0 auto; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; padding: 8px 11px; color: #334155; cursor: pointer; font-size: 11px; font-weight: 800; }
.base-url-box button:hover { border-color: #67e8f9; color: #0e7490; }

.contract-row {
  display: grid;
  margin-top: 22px;
  border-top: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  grid-template-columns: repeat(4, 1fr);
}
.contract-row div { display: grid; gap: 5px; padding: 15px 16px; border-right: 1px solid #e2e8f0; }
.contract-row div:first-child { padding-left: 0; }
.contract-row div:last-child { border-right: 0; }
.contract-row span { color: #94a3b8; font-size: 10px; font-weight: 800; }
.contract-row strong { font-size: 12px; }

.step-list { display: grid; margin-top: 30px; border-top: 1px solid #dce3ec; }
.step-list article { display: grid; grid-template-columns: 48px 1fr; gap: 14px; border-bottom: 1px solid #e2e8f0; padding: 18px 0; }
.step-list article > span { color: #0891b2; font-family: "Cascadia Code", Consolas, monospace; font-size: 11px; font-weight: 900; }
.step-list p { margin: 6px 0 0; color: #64748b; font-size: 13px; line-height: 1.7; }
.compact-steps { grid-template-columns: repeat(3, 1fr); gap: 18px; border-top: 0; }
.compact-steps article { display: block; border: 1px solid #dce3ec; border-radius: 8px; padding: 18px; }
.compact-steps article > span { display: block; margin-bottom: 18px; }

.language-tabs { display: inline-grid; margin-top: 28px; grid-template-columns: repeat(3, auto); border: 1px solid #d6dee8; border-radius: 7px; background: #eef2f7; padding: 3px; }
.language-tabs button { min-width: 88px; border: 0; border-radius: 4px; background: transparent; padding: 7px 12px; color: #64748b; cursor: pointer; font-size: 11px; font-weight: 800; }
.language-tabs button.active { background: #fff; color: #0e7490; box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08); }

.endpoint-title { display: flex; align-items: center; gap: 12px; margin-top: 28px; border: 1px solid #dce3ec; border-radius: 7px; background: #f8fafc; padding: 13px 15px; }
.endpoint-title code { overflow: hidden; color: #0f172a; font-size: 13px; font-weight: 750; text-overflow: ellipsis; }
.endpoint-title em { margin-left: auto; color: #94a3b8; font-size: 11px; font-style: normal; font-weight: 700; }
.method { display: inline-flex; min-width: 48px; align-items: center; justify-content: center; border-radius: 4px; padding: 4px 6px; font-family: "Cascadia Code", Consolas, monospace; font-size: 9px; font-weight: 900; }
.method-get { background: #dff7ef; color: #047857; }
.method-post { background: #def4fb; color: #0369a1; }
.method-delete { background: #fee2e2; color: #b91c1c; }

.notice { margin-top: 22px; border-left: 3px solid; border-radius: 5px; padding: 15px 17px; }
.notice strong { font-size: 13px; }
.notice p { margin: 5px 0 0; color: #64748b; font-size: 12px; line-height: 1.75; }
.notice-warning { border-color: #f59e0b; background: #fffbeb; }
.notice-info { border-color: #06b6d4; background: #ecfeff; }

.table-wrap { margin-top: 24px; overflow-x: auto; border: 1px solid #dce3ec; border-radius: 7px; }
table { width: 100%; border-collapse: collapse; background: #fff; font-size: 12px; }
th { background: #f5f7fa; color: #64748b; font-size: 10px; font-weight: 900; text-align: left; text-transform: uppercase; }
th, td { padding: 12px 14px; border-bottom: 1px solid #e2e8f0; vertical-align: top; line-height: 1.6; }
tbody tr:last-child td { border-bottom: 0; }
tbody tr { transition: background-color 0.15s ease; }
tbody tr:hover { background: #f8fbfc; }
.required { color: #b91c1c; font-size: 11px; font-weight: 800; }
.endpoint-table table { min-width: 680px; }

.async-flow { display: grid; align-items: center; gap: 10px; margin-top: 24px; grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr; }
.async-flow div { min-width: 0; border: 1px solid #dce3ec; border-radius: 7px; background: #fff; padding: 12px; }
.async-flow small { display: block; margin-bottom: 4px; color: #94a3b8; font-size: 9px; font-weight: 800; }
.async-flow strong { display: block; overflow: hidden; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.async-flow > span { color: #94a3b8; font-size: 14px; }

.status-grid { display: grid; gap: 12px; margin-top: 24px; grid-template-columns: repeat(3, 1fr); }
.status-grid article { position: relative; border: 1px solid #dce3ec; border-radius: 7px; background: #fff; padding: 16px; }
.status-grid strong { margin-left: 9px; font-family: "Cascadia Code", Consolas, monospace; font-size: 11px; }
.status-grid p { margin: 9px 0 0; color: #64748b; font-size: 11px; line-height: 1.6; }
.status-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; }
.status-dot.pending { background: #f59e0b; }
.status-dot.success { background: #10b981; }
.status-dot.failed { background: #ef4444; }

.field-notes { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.field-notes > div { border: 1px solid #dce3ec; border-radius: 6px; background: #f8fafc; padding: 9px 11px; }
.field-notes > div:has(p) { flex: 1 1 280px; }
.field-notes p { margin: 5px 0 0; color: #64748b; font-size: 11px; line-height: 1.6; }

.endpoint-list { display: grid; gap: 0; margin-top: 24px; border-top: 1px solid #dce3ec; }
.endpoint-list article { display: grid; align-items: center; gap: 12px; border-bottom: 1px solid #e2e8f0; padding: 15px 0; grid-template-columns: 58px minmax(180px, 1fr) 1.4fr; }
.endpoint-list p { margin: 0; color: #64748b; font-size: 11px; line-height: 1.6; }

.format-grid { display: grid; gap: 12px; margin-top: 24px; grid-template-columns: repeat(3, 1fr); }
.format-grid article { border: 1px solid #dce3ec; border-radius: 7px; background: #fff; padding: 17px; }
.format-grid span { color: #94a3b8; font-size: 9px; font-weight: 900; text-transform: uppercase; }
.format-grid strong { display: block; margin-top: 9px; font-size: 18px; }
.format-grid p { margin: 8px 0 0; color: #64748b; font-size: 11px; line-height: 1.7; }

.response-contract { margin-top: 22px; border: 1px solid #cbd5e1; border-radius: 7px; background: #0f172a; padding: 16px; }
.response-contract code { color: #bae6fd; font-size: 12px; }

.auth-options { display: grid; gap: 10px; margin-top: 24px; }
.auth-options article { position: relative; border: 1px solid #dce3ec; border-radius: 7px; background: #fff; padding: 16px 18px; }
.auth-options article.recommended { border-color: #67e8f9; background: #f0fdff; }
.auth-options span { display: inline-block; min-width: 38px; margin-right: 10px; color: #0891b2; font-size: 9px; font-weight: 900; }
.auth-options p { margin: 8px 0 0 52px; color: #64748b; font-size: 11px; }

.facts-sidebar {
  right: max(24px, calc((100vw - 1420px) / 2));
  width: 244px;
  padding-top: 42px;
  animation: docsRightRailEnter 0.66s 0.12s cubic-bezier(0.22, 0.8, 0.24, 1) both;
}
.facts-panel { border: 1px solid #dce3ec; border-radius: 8px; background: rgba(255, 255, 255, 0.88); padding: 18px; box-shadow: 0 16px 38px rgba(15, 23, 42, 0.06); }
.facts-panel > p { margin: 0 0 15px; color: #64748b; font-size: 11px; font-weight: 900; }
.facts-panel dl { display: grid; margin: 0; grid-template-columns: 74px 1fr; }
.facts-panel dt, .facts-panel dd { margin: 0; border-top: 1px solid #e2e8f0; padding: 10px 0; font-size: 11px; }
.facts-panel dt { color: #94a3b8; }
.facts-panel dd { color: #334155; font-weight: 750; text-align: right; }
.facts-panel button { width: 100%; margin-top: 14px; border: 1px solid #0e7490; border-radius: 6px; background: #0e7490; padding: 9px; color: #fff; cursor: pointer; font-size: 11px; font-weight: 800; }
.facts-panel button:hover { background: #155e75; }
.security-note { margin-top: 20px; border-top: 1px solid #dce3ec; padding: 18px 4px; }
.security-note span { color: #b45309; font-size: 9px; font-weight: 900; }
.security-note p { margin: 7px 0 0; color: #64748b; font-size: 11px; line-height: 1.7; }

.docs-footer { display: flex; align-items: center; gap: 12px; padding: 32px 64px; background: #f8fafc; }
.docs-footer div { display: grid; gap: 3px; }
.docs-footer strong { font-size: 12px; }
.docs-footer span { color: #94a3b8; font-size: 10px; }
.docs-footer button { margin-left: auto; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; padding: 8px 11px; color: #475569; cursor: pointer; font-size: 10px; font-weight: 800; }

.mobile-drawer-backdrop { position: fixed; inset: 0; z-index: 80; display: flex; justify-content: flex-end; background: rgba(15, 23, 42, 0.4); backdrop-filter: blur(3px); }
.mobile-drawer { width: min(86vw, 340px); height: 100%; overflow-y: auto; background: #fff; padding: 18px; box-shadow: -20px 0 50px rgba(15, 23, 42, 0.18); }
.mobile-drawer-header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e2e8f0; padding: 6px 4px 16px; }
.mobile-drawer-header strong { font-size: 14px; }
.mobile-drawer-header button { border: 0; background: transparent; color: #64748b; cursor: pointer; font-size: 12px; font-weight: 700; }
.mobile-drawer nav { display: grid; gap: 3px; margin-top: 14px; }
.mobile-drawer nav button.active { background: #e6f6f9; color: #0e7490; }
.drawer-fade-enter-active, .drawer-fade-leave-active { transition: opacity 0.2s ease; }
.drawer-fade-enter-active .mobile-drawer, .drawer-fade-leave-active .mobile-drawer { transition: transform 0.24s ease; }
.drawer-fade-enter-from, .drawer-fade-leave-to { opacity: 0; }
.drawer-fade-enter-from .mobile-drawer, .drawer-fade-leave-to .mobile-drawer { transform: translateX(100%); }

@keyframes docsPageEnter {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes docsHeaderEnter {
  from { opacity: 0; transform: translateY(-12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes docsContentEnter {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes docsLeftRailEnter {
  from { opacity: 0; transform: translateX(-14px); }
  to { opacity: 1; transform: translateX(0); }
}

@keyframes docsRightRailEnter {
  from { opacity: 0; transform: translateX(14px); }
  to { opacity: 1; transform: translateX(0); }
}

@media (max-width: 1180px) {
  .docs-layout { grid-template-columns: 190px minmax(0, 820px); justify-content: center; gap: 30px; }
  .section-sidebar {
    left: max(24px, calc((100vw - 1040px) / 2));
    width: 190px;
  }
  .facts-sidebar { display: none; }
}

@media (max-width: 860px) {
  .docs-header-inner { width: min(100% - 24px, 820px); grid-template-columns: 1fr auto; gap: 12px; }
  .product-switch-desktop { display: none; }
  .mobile-product-bar { position: sticky; top: 69px; z-index: 40; display: flex; justify-content: center; border-bottom: 1px solid #dce3ec; background: rgba(248, 250, 252, 0.94); padding: 8px 12px; backdrop-filter: blur(14px); }
  .mobile-product-bar .product-switch { width: min(100%, 420px); }
  .section-sidebar { display: none; }
  .docs-layout { display: block; width: min(100% - 24px, 820px); }
  .docs-content { width: 100%; grid-column: auto; }
  .mobile-toc-button { display: inline-flex; }
  .desktop-only { display: none; }
  .doc-section { scroll-margin-top: 128px; }
}

@media (max-width: 640px) {
  .docs-header-inner { min-height: 62px; }
  .brand-button img { width: 25px; height: 25px; }
  .brand-name { font-size: 14px; }
  .brand-divider, .brand-context { display: none; }
  .header-actions { gap: 4px; }
  .header-link, .console-button { padding: 8px 9px; font-size: 10px; }
  .mobile-product-bar { top: 63px; }
  .product-switch button { padding: 7px 8px; font-size: 10px; }
  .docs-layout { width: 100%; }
  .docs-content { box-shadow: none; }
  .doc-section { padding: 48px 20px; scroll-margin-top: 120px; }
  .intro-section { padding-top: 54px; }
  h1 { font-size: 35px; }
  h2 { font-size: 24px; }
  .intro-copy { font-size: 14px; }
  .base-url-box { align-items: flex-end; padding: 14px; }
  .base-url-box code { font-size: 10px; }
  .base-url-box button { padding: 7px 8px; font-size: 9px; }
  .contract-row { grid-template-columns: repeat(2, 1fr); }
  .contract-row div:nth-child(2) { border-right: 0; }
  .contract-row div:first-child, .contract-row div:nth-child(2) { border-bottom: 1px solid #e2e8f0; }
  .contract-row div:first-child, .contract-row div:nth-child(3) { padding-left: 0; }
  .compact-steps, .status-grid, .format-grid { grid-template-columns: 1fr; }
  .language-tabs { display: grid; grid-template-columns: repeat(3, 1fr); width: 100%; }
  .language-tabs button { min-width: 0; padding: 7px 4px; }
  .endpoint-title { flex-wrap: wrap; gap: 8px; }
  .endpoint-title code { max-width: calc(100% - 64px); font-size: 10px; }
  .endpoint-title em { width: 100%; margin-left: 60px; }
  .async-flow { grid-template-columns: 1fr; }
  .async-flow > span { transform: rotate(90deg); text-align: center; }
  .endpoint-list article { align-items: start; grid-template-columns: 54px 1fr; }
  .endpoint-list p { grid-column: 2; }
  .auth-options code { display: block; margin-top: 10px; overflow-wrap: anywhere; }
  .auth-options p { margin-left: 0; }
  th, td { padding: 10px 11px; }
  .docs-footer { align-items: flex-start; padding: 28px 20px; }
  .docs-footer button { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-delay: 0s !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
