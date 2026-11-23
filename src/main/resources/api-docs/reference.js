const version = new URLSearchParams(window.location.search).get('version') === 'v1' ? 'v1' : 'v3'
const scalarUrl = 'https://file.fishpi.cn/ada/js/scalar-v2.js'
const scalarIntegrity = 'sha384-oalkqIG8teEqGhwdk1ccsl7WVVQ8kr9YXtfIBN7DBLU9wRBR2Nt0T4N2XWO1kMmK'
const loading = document.querySelector('#api-docs-loading')
const loadingTitle = document.querySelector('#api-docs-loading-title')
const loadingDetail = document.querySelector('#api-docs-loading-detail')
const loadingProgress = document.querySelector('#api-docs-loading-progress')
const loadingIndicator = document.querySelector('#api-docs-loading-indicator')
const reference = document.querySelector('#api-reference-mount')
const fallback = document.querySelector('#reference-fallback')

for (const link of document.querySelectorAll('[data-version]')) {
  if (link.dataset.version === version) link.setAttribute('aria-current', 'page')
  else link.removeAttribute('aria-current')
}

const formatBytes = (bytes) => `${(bytes / 1024 / 1024).toFixed(1)} MB`

function toBase64(bytes) {
  let result = ''
  const chunkSize = 0x8000
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    result += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize))
  }
  return btoa(result)
}

function setLoading(title, detail, error = false) {
  loading.hidden = false
  loadingTitle.textContent = title
  loadingTitle.classList.toggle('api-docs-loading-error', error)
  loadingDetail.textContent = detail
  loadingProgress.hidden = true
  loadingIndicator.hidden = !error
  fallback.hidden = !error
}

async function loadScalar() {
  const response = await fetch(scalarUrl, { credentials: 'omit', cache: 'default' })
  if (!response.ok || !response.body) throw new Error(`文档组件加载失败（HTTP ${response.status}）`)

  const total = Number(response.headers.get('content-length'))
  const reader = response.body.getReader()
  const chunks = []
  let received = 0
  loadingIndicator.hidden = true
  loadingProgress.hidden = !Number.isFinite(total) || total <= 0

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
    received += value.length
    loadingDetail.textContent = Number.isFinite(total) && total > 0
      ? `正在下载文档组件… ${Math.round(received / total * 100)}%`
      : `正在下载文档组件… 已下载 ${formatBytes(received)}`
    if (!loadingProgress.hidden) {
      loadingProgress.value = Math.min(100, received / total * 100)
    }
  }

  const bytes = new Uint8Array(received)
  let offset = 0
  for (const chunk of chunks) {
    bytes.set(chunk, offset)
    offset += chunk.length
  }
  const digest = await crypto.subtle.digest('SHA-384', bytes)
  const actualIntegrity = `sha384-${toBase64(new Uint8Array(digest))}`
  if (actualIntegrity !== scalarIntegrity) throw new Error('文档组件完整性校验失败')

  await new Promise((resolve, reject) => {
    const script = document.createElement('script')
    const objectUrl = URL.createObjectURL(new Blob([bytes], { type: 'text/javascript' }))
    script.src = objectUrl
    script.integrity = scalarIntegrity
    script.onload = () => {
      URL.revokeObjectURL(objectUrl)
      if (!window.Scalar) {
        reject(new Error('文档组件未完成初始化'))
        return
      }
      try {
        const base = new URL('./api-docs/', window.location.href)
        reference.id = 'api-reference'
        window.Scalar.createApiReference('#api-reference', {
          url: new URL(version, base).href,
          persistAuth: false,
          hideTestRequestButton: true,
          hideClientButton: true,
          showDeveloperTools: 'never',
          withDefaultFonts: false,
        })
        loading.hidden = true
        reference.hidden = false
        resolve()
      } catch (error) {
        reject(error)
      }
    }
    script.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('文档组件执行失败'))
    }
    document.head.append(script)
  })
}

function start() {
  setLoading('正在加载 API 文档', '正在连接文档组件…')
  loadScalar().catch((error) => {
    setLoading('API 文档加载失败', error.message || '请稍后重试', true)
    const retry = document.createElement('button')
    retry.type = 'button'
    retry.textContent = '重新加载'
    retry.addEventListener('click', () => {
      retry.remove()
      start()
    })
    loading.querySelector('.api-docs-loading-card').append(retry)
  })
}

start()
