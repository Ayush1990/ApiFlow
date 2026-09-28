import fs from 'node:fs/promises'
import path from 'node:path'
import vm from 'node:vm'

const METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']

function blocks(text) {
  const found = []
  let index = 0
  while (index < text.length) {
    const brace = text.indexOf('{', index)
    if (brace < 0) break
    let name = text.slice(index, brace).trim()
    const newline = name.lastIndexOf('\n')
    if (newline >= 0) name = name.slice(newline + 1).trim()
    let depth = 0
    let end = brace
    for (; end < text.length; end += 1) {
      const ch = text.charAt(end)
      if (ch === '{') depth += 1
      else if (ch === '}') {
        depth -= 1
        if (depth === 0) {
          end += 1
          break
        }
      }
    }
    if (name) found.push({ name: name.toLowerCase(), body: text.slice(brace + 1, Math.max(brace + 1, end - 1)) })
    index = end
  }
  return found
}

function field(body, key, fallback = '') {
  for (const line of body.split(/\r?\n/)) {
    const trimmed = line.trim()
    if (trimmed.toLowerCase().startsWith(`${key.toLowerCase()}:`)) {
      return trimmed.slice(trimmed.indexOf(':') + 1).trim()
    }
  }
  return fallback
}

function unquote(value) {
  const trimmed = (value || '').trim()
  if ((trimmed.startsWith('`') && trimmed.endsWith('`')) || (trimmed.startsWith('"') && trimmed.endsWith('"'))) {
    return trimmed.slice(1, -1)
  }
  return trimmed
}

function pairs(body) {
  const rows = []
  for (const line of body.split(/\r?\n/)) {
    let trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('#')) continue
    let enabled = true
    if (trimmed.startsWith('~')) {
      enabled = false
      trimmed = trimmed.slice(1).trim()
    }
    const colon = trimmed.indexOf(':')
    if (colon < 0) continue
    rows.push({ key: trimmed.slice(0, colon).trim(), value: unquote(trimmed.slice(colon + 1).trim()), enabled })
  }
  return rows
}

export function parseBru(content) {
  const request = {
    name: 'Request',
    method: 'GET',
    url: '',
    headers: [],
    params: [],
    tags: [],
    bodyType: 'none',
    body: '',
    form: [],
    authType: 'none',
    auth: {},
    preRequestScript: '',
    postResponseScript: '',
    assertions: [],
  }
  for (const block of blocks(content)) {
    if (block.name === 'meta') {
      request.name = field(block.body, 'name', request.name)
      const tags = field(block.body, 'tags', '')
      if (tags) request.tags = tags.split(/[,\s]+/).filter(Boolean)
    } else if (METHODS.includes(block.name)) {
      request.method = block.name.toUpperCase()
      request.url = unquote(field(block.body, 'url', ''))
      request.bodyType = field(block.body, 'body', 'none')
      const auth = field(block.body, 'auth', 'none')
      if (auth && auth !== 'none') request.authType = auth
    } else if (block.name === 'headers') {
      request.headers = pairs(block.body)
    } else if (block.name === 'params' || block.name === 'query') {
      request.params = pairs(block.body)
    } else if (block.name === 'tags') {
      request.tags = block.body.split(/\r?\n/).map((line) => line.trim().replace(/^-/, '')).filter(Boolean)
    } else if (block.name.startsWith('auth:')) {
      request.authType = block.name.slice(5)
      request.auth = parseAuthBlock(block.body)
    } else if (block.name === 'assert' || block.name.startsWith('assert:')) {
      request.assertions = parseAssertions(block.body)
    } else if (block.name === 'multipart' || block.name === 'body:multipart') {
      request.bodyType = 'multipart'
      request.form = pairs(block.body)
    } else if (block.name.startsWith('body:')) {
      request.bodyType = block.name.slice(5)
      request.body = block.body.trim()
    } else if (block.name === 'script:pre-request') {
      request.preRequestScript = block.body.trim()
    } else if (block.name === 'script:post-response') {
      request.postResponseScript = block.body.trim()
    }
  }
  return request
}

function parseAuthBlock(body) {
  const tokenUrl = field(body, 'access_token_url', field(body, 'token_url', ''))
  const accessKey = field(body, 'access_key', field(body, 'accessKey', ''))
  const secretKey = field(body, 'secret_key', field(body, 'secretKey', ''))
  return {
    token: unquote(field(body, 'token', '')),
    username: unquote(field(body, 'username', '')),
    password: unquote(field(body, 'password', '')),
    key: unquote(field(body, 'key', '')),
    value: unquote(field(body, 'value', '')),
    keyIn: field(body, 'in', 'header'),
    grantType: field(body, 'grant_type', field(body, 'grant', 'client_credentials')),
    accessTokenUrl: unquote(tokenUrl),
    clientId: unquote(field(body, 'client_id', '')),
    clientSecret: unquote(field(body, 'client_secret', '')),
    scope: unquote(field(body, 'scope', '')),
    accessKey: unquote(accessKey),
    secretKey: unquote(secretKey),
    region: unquote(field(body, 'region', 'us-east-1')),
    service: unquote(field(body, 'service', 'execute-api')),
  }
}

function parseAssertions(body) {
  const assertions = []
  for (const line of body.split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('//')) continue
    const colon = trimmed.indexOf(':')
    if (colon < 0) continue
    let type = trimmed.slice(0, colon).trim()
    const rest = trimmed.slice(colon + 1).trim()
    let expected = rest
    if (rest.startsWith('eq ')) expected = rest.slice(3).trim()
    else if (rest.startsWith('contains ')) {
      type = `${type}:contains`
      expected = rest.slice(9).trim()
    }
    assertions.push({ type, expected })
  }
  return assertions
}

export function interpolate(input, vars) {
  if (!input) return ''
  return input.replace(/\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g, (match, name) =>
    Object.prototype.hasOwnProperty.call(vars, name) ? vars[name] : match,
  )
}

export function matchesTags(tags, expression) {
  if (!expression) return true
  if (!tags?.length) return false
  const expr = expression.trim()
  if (expr.includes('||')) return expr.split('||').some((part) => matchesTags(tags, part.trim()))
  if (expr.includes('&&')) return expr.split('&&').every((part) => matchesTags(tags, part.trim()))
  const tagCall = expr.match(/^@tag\s*\(\s*([^)]+?)\s*\)$/i)
  const wanted = tagCall ? tagCall[1].trim() : expr
  return tags.some((tag) => tag.toLowerCase() === wanted.toLowerCase())
}

export function parseCsv(content) {
  const lines = content.split(/\r?\n/).filter((line) => line.trim())
  if (!lines.length) return []
  const headers = splitCsvLine(lines[0])
  const rows = []
  for (let index = 1; index < lines.length; index += 1) {
    const values = splitCsvLine(lines[index])
    const row = {}
    headers.forEach((header, i) => {
      if (header) row[header.trim()] = values[i] ?? ''
    })
    rows.push(row)
  }
  return rows
}

function splitCsvLine(line) {
  const values = []
  let current = ''
  let quoted = false
  for (let index = 0; index < line.length; index += 1) {
    const ch = line.charAt(index)
    if (ch === '"') {
      if (quoted && line.charAt(index + 1) === '"') {
        current += '"'
        index += 1
      } else quoted = !quoted
    } else if (ch === ',' && !quoted) {
      values.push(current)
      current = ''
    } else current += ch
  }
  values.push(current)
  return values
}

function createScriptApi(vars, request, response = null) {
  const api = {
    setVar(name, value) { vars[name] = String(value ?? '') },
    getVar(name) { return vars[name] ?? '' },
    setEnvVar(name, value) { vars[name] = String(value ?? '') },
    getEnvVar(name) { return vars[name] ?? '' },
    setCollectionVar(name, value) { vars[name] = String(value ?? '') },
    getCollectionVar(name) { return vars[name] ?? '' },
    getProcessEnv(name) { return process.env[name] ?? '' },
    interpolate(value) { return interpolate(String(value ?? ''), vars) },
    setUrl(url) { request.url = String(url ?? '') },
    setHeader(name, value) {
      const row = request.headers.find((item) => item.key.toLowerCase() === String(name).toLowerCase())
      if (row) row.value = String(value ?? '')
      else request.headers.push({ key: String(name), value: String(value ?? ''), enabled: true })
    },
    skip() { request._skip = true },
  }
  if (response) {
    api.getStatus = () => response.status
    api.getBody = () => response.body
    api.getHeaders = () => response.headers
  }
  return api
}

function runScript(script, vars, request, response = null) {
  if (!script) return
  const api = createScriptApi(vars, request, response)
  const sandbox = {
    setVar: api.setVar,
    getVar: api.getVar,
    setEnvVar: api.setEnvVar,
    getEnvVar: api.getEnvVar,
    setCollectionVar: api.setCollectionVar,
    getCollectionVar: api.getCollectionVar,
    getProcessEnv: api.getProcessEnv,
    bru: api,
    apiflow: api,
    req: request,
    res: response ? { status: response.status, body: response.body, getStatus: api.getStatus, getBody: api.getBody } : undefined,
    console: { log: () => {} },
  }
  vm.runInNewContext(script, sandbox, { timeout: 5000, filename: 'script.js' })
}

async function fetchOAuthToken(auth, vars) {
  const tokenUrl = interpolate(auth.accessTokenUrl, vars)
  if (!tokenUrl) throw new Error('OAuth access_token_url is required')
  const grant = (auth.grantType || 'client_credentials').toLowerCase()
  const params = new URLSearchParams()
  params.set('grant_type', grant)
  if (auth.clientId) params.set('client_id', interpolate(auth.clientId, vars))
  if (auth.clientSecret) params.set('client_secret', interpolate(auth.clientSecret, vars))
  if (auth.scope) params.set('scope', interpolate(auth.scope, vars))
  if (grant === 'password') {
    params.set('username', interpolate(auth.username, vars))
    params.set('password', interpolate(auth.password, vars))
  }
  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params.toString(),
  })
  const text = await response.text()
  if (!response.ok) throw new Error(`OAuth token request failed (${response.status}): ${text.slice(0, 200)}`)
  const payload = JSON.parse(text)
  return payload.access_token
}

async function applyAuth(request, vars, headers) {
  const authType = (request.authType || 'none').toLowerCase()
  const auth = request.auth || {}
  if (authType === 'none' || authType === 'inherit') return
  if (authType === 'bearer') {
    headers.Authorization = `Bearer ${interpolate(auth.token, vars)}`
    return
  }
  if (authType === 'basic') {
    const user = interpolate(auth.username, vars)
    const pass = interpolate(auth.password, vars)
    headers.Authorization = `Basic ${Buffer.from(`${user}:${pass}`).toString('base64')}`
    return
  }
  if (authType === 'apikey') {
    const value = interpolate(auth.value, vars)
    if ((auth.keyIn || 'header').toLowerCase() === 'query') {
      request._apiKeyQuery = { key: interpolate(auth.key, vars), value }
    } else {
      headers[interpolate(auth.key, vars)] = value
    }
    return
  }
  if (authType === 'oauth2' || authType === 'oauth-2') {
    const token = await fetchOAuthToken(auth, vars)
    headers.Authorization = `Bearer ${token}`
    return
  }
  if (authType === 'digest' || authType === 'ntlm' || authType === 'aws' || authType === 'awsv4') {
    throw new Error(`Auth type "${authType}" requires the ApiFlow backend — use backend mode or set APIFLOW_AUTO_START=1`)
  }
}

function evaluateAssertions(assertions, status, body) {
  if (!assertions?.length) return { passed: true, failures: [] }
  const failures = []
  for (const assertion of assertions) {
    const type = assertion.type || 'status'
    const expected = assertion.expected ?? ''
    if (type === 'status') {
      if (String(status) !== String(expected)) failures.push(`status expected ${expected}, got ${status}`)
    } else if (type.endsWith(':contains')) {
      const fieldName = type.slice(0, -9)
      const haystack = fieldName === 'body' ? body : ''
      if (!haystack.includes(unquote(expected))) failures.push(`${fieldName} does not contain ${expected}`)
    } else if (type === 'body') {
      if (body.trim() !== unquote(expected)) failures.push(`body mismatch`)
    }
  }
  return { passed: failures.length === 0, failures }
}

async function walkBruFiles(root) {
  const files = []
  async function walk(dir) {
    const entries = await fs.readdir(dir, { withFileTypes: true })
    for (const entry of entries) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) await walk(full)
      else if (entry.name.endsWith('.bru') && entry.name !== 'collection.bru' && entry.name !== 'folder.bru') files.push(full)
    }
  }
  await walk(root)
  return files.sort()
}

async function loadEnvFile(collectionDir, envName, defaults = { baseUrl: 'http://localhost:8080' }) {
  const vars = { ...defaults }
  if (!envName) return vars
  const candidates = [
    path.join(collectionDir, 'environments', `${envName}.bru`),
    path.join(collectionDir, 'environments', `${envName}.json`),
    path.join(collectionDir, '..', 'environments', `${envName}.bru`),
  ]
  for (const file of candidates) {
    try {
      const content = await fs.readFile(file, 'utf8')
      if (file.endsWith('.json')) {
        const parsed = JSON.parse(content)
        for (const row of parsed.variables || []) {
          if (row.key) vars[row.key] = row.value ?? ''
        }
      } else {
        for (const block of blocks(content)) {
          if (block.name === 'vars') {
            for (const row of pairs(block.body)) {
              if (row.enabled && row.key) vars[row.key] = row.value
            }
          }
        }
      }
      return vars
    } catch {
      // try next
    }
  }
  return vars
}

async function loadGlobalEnv(collectionDir, globalEnvName) {
  if (!globalEnvName) return {}
  const candidates = [
    path.join(collectionDir, '..', 'environments', `${globalEnvName}.bru`),
    path.join(collectionDir, '..', 'environments', `${globalEnvName}.json`),
    path.join(collectionDir, '..', '..', 'environments', `${globalEnvName}.bru`),
    path.join(collectionDir, '..', '..', 'environments', `${globalEnvName}.json`),
  ]
  for (const file of candidates) {
    try {
      const content = await fs.readFile(file, 'utf8')
      if (file.endsWith('.json')) {
        const parsed = JSON.parse(content)
        const vars = {}
        for (const row of parsed.variables || []) {
          if (row.key) vars[row.key] = row.value ?? ''
        }
        return vars
      }
      const vars = {}
      for (const block of blocks(content)) {
        if (block.name === 'vars') {
          for (const row of pairs(block.body)) {
            if (row.enabled && row.key) vars[row.key] = row.value
          }
        }
      }
      return vars
    } catch {
      // try next
    }
  }
  return {}
}

function buildUrl(request, vars) {
  let url = interpolate(request.url, vars)
  const enabled = request.params.filter((row) => row.enabled && row.key)
  if (enabled.length) {
    const query = enabled.map((row) => `${encodeURIComponent(row.key)}=${encodeURIComponent(interpolate(row.value, vars))}`).join('&')
    url += (url.includes('?') ? '&' : '?') + query
  }
  if (request._apiKeyQuery?.key) {
    const pair = `${encodeURIComponent(request._apiKeyQuery.key)}=${encodeURIComponent(request._apiKeyQuery.value)}`
    url += (url.includes('?') ? '&' : '?') + pair
  }
  return url
}

async function buildBody(request, vars) {
  if (request.bodyType === 'none') return undefined
  if (request.bodyType === 'multipart') {
    const form = new FormData()
    for (const row of request.form) {
      if (row.enabled && row.key) form.append(row.key, interpolate(row.value, vars))
    }
    return form
  }
  if (request.body) return interpolate(request.body, vars)
  return undefined
}

async function runRequest(request, vars) {
  runScript(request.preRequestScript, vars, request)
  if (request._skip) {
    return { name: request.name, ok: true, status: 0, skipped: true, timeMs: 0, body: '' }
  }
  const url = buildUrl(request, vars)
  const headers = {}
  for (const row of request.headers) {
    if (row.enabled && row.key) headers[row.key] = interpolate(row.value, vars)
  }
  await applyAuth(request, vars, headers)
  const body = await buildBody(request, vars)
  if (body && !(body instanceof FormData) && !headers['Content-Type'] && !headers['content-type']) {
    headers['Content-Type'] = request.bodyType === 'json' ? 'application/json' : 'text/plain'
  }
  const started = Date.now()
  const response = await fetch(url, { method: request.method, headers, body })
  const text = await response.text()
  const responseView = { status: response.status, body: text, headers: Object.fromEntries(response.headers.entries()) }
  runScript(request.postResponseScript, vars, request, responseView)
  const checks = evaluateAssertions(request.assertions, response.status, text)
  return {
    name: request.name,
    ok: response.ok && checks.passed,
    status: response.status,
    timeMs: Date.now() - started,
    body: text.slice(0, 500),
    assertionFailures: checks.failures,
  }
}

async function runFile(file, baseVars, options, report) {
  const content = await fs.readFile(file, 'utf8')
  if (content.includes('type: folder')) return
  const request = parseBru(content)
  const tagExpressions = options.tags || []
  if (tagExpressions.length && !tagExpressions.some((expr) => matchesTags(request.tags, expr))) return
  report.total += 1
  const vars = { ...baseVars, ...(options.rowVars || {}) }
  try {
    const result = await runRequest(request, vars)
    report.items.push(result)
    if (result.skipped) return
    if (result.ok) report.passed += 1
    else {
      report.failed += 1
      if (options.stopOnFailure) report._stop = true
    }
  } catch (err) {
    report.failed += 1
    report.items.push({ name: request.name, ok: false, status: 0, error: err.message })
    if (options.stopOnFailure) report._stop = true
  }
}

export async function runLocalCollection(collectionPath, options = {}) {
  const root = path.resolve(collectionPath)
  const stat = await fs.stat(root)
  if (!stat.isDirectory()) throw new Error(`Not a directory: ${root}`)
  const globalVars = await loadGlobalEnv(root, options.globalEnvironment || '')
  const collectionVars = await loadEnvFile(root, options.environment || '', {})
  const baseVars = { baseUrl: 'http://localhost:8080', ...globalVars, ...collectionVars }
  const files = await walkBruFiles(root)
  const report = { total: 0, passed: 0, failed: 0, items: [] }
  const dataRows = options.dataCsv ? parseCsv(options.dataCsv) : [{}]
  for (const rowVars of dataRows) {
    for (const file of files) {
      await runFile(file, baseVars, { ...options, rowVars }, report)
      if (report._stop) break
      if (options.delayMs > 0) await new Promise((resolve) => setTimeout(resolve, options.delayMs))
    }
    if (report._stop) break
  }
  delete report._stop
  return report
}

export async function isBruCollection(target) {
  try {
    const stat = await fs.stat(target)
    return stat.isDirectory()
  } catch {
    return false
  }
}
