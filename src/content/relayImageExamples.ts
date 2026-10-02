type Examples = Record<'curl' | 'javascript' | 'python', string>

// 教学使用本站实际路由；可选的画质、格式等字段由所选供应商决定。
export function relayImageExamples(base: string): { generate: Examples; edit: Examples } {
  return {
    generate: {
      curl: `# 先在终端设置 RELAY_API_KEY 环境变量
curl "${base}/images/generations" \\
  -H "Authorization: Bearer $RELAY_API_KEY" \\
  -H "Content-Type: application/json" \\
  --max-time 300 \\
  -d '{
    "model": "gpt-image-2",
    "prompt": "云雾中的未来城市，清晨柔光，电影感构图",
    "size": "1024x1024",
    "n": 1
  }' -o result.json

# result.json 是 JSON 响应，请读取 data 中的 b64_json 或 url。`,
      javascript: `// Node.js 18+，保存为 generate.mjs 后运行 node generate.mjs
import { writeFile } from 'node:fs/promises'

const apiKey = process.env.RELAY_API_KEY
if (!apiKey) throw new Error('请先设置 RELAY_API_KEY')

const response = await fetch('${base}/images/generations', {
  method: 'POST',
  headers: {
    Authorization: \`Bearer \${apiKey}\`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    model: 'gpt-image-2',
    prompt: '云雾中的未来城市，清晨柔光，电影感构图',
    size: '1024x1024',
    n: 1
  }),
  signal: AbortSignal.timeout(300_000)
})
const text = await response.text()
if (!response.ok) throw new Error(\`HTTP \${response.status}: \${text}\`)
const result = JSON.parse(text)
const picture = result.data?.[0]
if (!picture) throw new Error('响应没有图片数据')

let bytes
if (picture.b64_json) {
  bytes = Buffer.from(picture.b64_json, 'base64')
} else if (picture.url) {
  const download = await fetch(picture.url, { signal: AbortSignal.timeout(60_000) })
  if (!download.ok) throw new Error('图片下载失败')
  bytes = Buffer.from(await download.arrayBuffer())
} else {
  throw new Error('响应缺少 b64_json / url')
}
await writeFile('generated.png', bytes)
console.log('已保存 generated.png')`,
      python: `# pip install requests
import os
import base64
from pathlib import Path
import requests

response = requests.post(
    "${base}/images/generations",
    headers={"Authorization": f"Bearer {os.environ['RELAY_API_KEY']}"},
    json={
        "model": "gpt-image-2",
        "prompt": "云雾中的未来城市，清晨柔光，电影感构图",
        "size": "1024x1024",
        "n": 1,
    },
    timeout=(10, 300),
)
if not response.ok:
    raise RuntimeError(f"HTTP {response.status_code}: {response.text}")
pictures = response.json().get("data", [])
if not pictures:
    raise RuntimeError("响应没有图片数据")
picture = pictures[0]
if picture.get("b64_json"):
    image = base64.b64decode(picture["b64_json"])
elif picture.get("url"):
    download = requests.get(picture["url"], timeout=60)
    download.raise_for_status()
    image = download.content
else:
    raise RuntimeError("响应缺少 b64_json / url")
Path("generated.png").write_bytes(image)
print("已保存 generated.png")`
    },
    edit: {
      curl: `curl "${base}/images/edits" \\
  -H "Authorization: Bearer $RELAY_API_KEY" \\
  --max-time 300 \\
  -F "model=gpt-image-2" \\
  -F "prompt=保留建筑结构，将天空改为日落暖色" \\
  -F "size=1024x1024" \\
  -F "image=@reference.png" \\
  -o edited-result.json

# 多张参考图可重复 -F "image=@另一张图片.png"（数量以供应商限制为准）。
# 不要手动设置 Content-Type，curl 会自动生成 multipart boundary。`,
      javascript: `// Node.js 20+，参考图放在当前目录 reference.png
import { readFile, writeFile } from 'node:fs/promises'

const apiKey = process.env.RELAY_API_KEY
if (!apiKey) throw new Error('请先设置 RELAY_API_KEY')
const form = new FormData()
form.append('model', 'gpt-image-2')
form.append('prompt', '保留建筑结构，将天空改为日落暖色')
form.append('size', '1024x1024')
form.append('image', new Blob([await readFile('reference.png')], {
  type: 'image/png'
}), 'reference.png')

const response = await fetch('${base}/images/edits', {
  method: 'POST',
  headers: { Authorization: \`Bearer \${apiKey}\` },
  body: form,
  signal: AbortSignal.timeout(300_000)
})
const text = await response.text()
if (!response.ok) throw new Error(\`HTTP \${response.status}: \${text}\`)
await writeFile('edited-result.json', text)
// 使用文生图示例中的 b64_json / url 处理方式保存图片。
console.log('已保存 edited-result.json')`,
      python: `import os
import requests
from pathlib import Path

with open("reference.png", "rb") as reference:
    response = requests.post(
        "${base}/images/edits",
        headers={"Authorization": f"Bearer {os.environ['RELAY_API_KEY']}"},
        data={
            "model": "gpt-image-2",
            "prompt": "保留建筑结构，将天空改为日落暖色",
            "size": "1024x1024",
        },
        files={"image": ("reference.png", reference, "image/png")},
        timeout=(10, 300),
    )
if not response.ok:
    raise RuntimeError(f"HTTP {response.status_code}: {response.text}")
Path("edited-result.json").write_text(response.text, encoding="utf-8")
# 使用文生图示例中的 b64_json / url 处理方式保存图片。
print("已保存 edited-result.json")`
    }
  }
}
