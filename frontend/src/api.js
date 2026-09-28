async function send(path, options = {}) {
  let response
  try {
    response = await fetch(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
      },
    })
  } catch {
    throw new Error('Cannot reach the ApiFlow backend. Start it on port 8080, then refresh.')
  }

  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { error: text }
    }
  }
  if (!response.ok) {
    throw new Error(data?.error || response.statusText || 'Request failed')
  }
  return data
}

export function interpolate(input, variables) {
  if (!input || !variables) return input || ''
  let current = input
  for (let pass = 0; pass < 3; pass += 1) {
    const next = current.replace(/\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g, (match, name) =>
      Object.prototype.hasOwnProperty.call(variables, name) ? variables[name] : match,
    )
    if (next === current) break
    current = next
  }
  return current
}

export function secretValues(workspace, environment, collection) {
  const values = []
  for (const item of [...(workspace?.variables || []), ...(collection?.variables || []), ...(environment?.variables || [])]) {
    if (item.secret && item.value && String(item.value).length >= 4) values.push(String(item.value))
  }
  return values.sort((left, right) => right.length - left.length)
}

export function maskSecrets(text, secrets) {
  let current = text || ''
  for (const secret of secrets || []) current = current.split(secret).join('••••')
  return current
}

export function requestSnapshot(request) {
  if (!request) return ''
  return JSON.stringify({
    name: request.name || '',
    method: request.method || 'GET',
    url: request.url || '',
    params: request.params || [],
    headers: request.headers || [],
    bodyType: request.bodyType || 'none',
    body: request.body || '',
    form: request.form || [],
    authType: request.authType || 'none',
    authToken: request.authToken || '',
    authUsername: request.authUsername || '',
    authPassword: request.authPassword || '',
    apiKeyName: request.apiKeyName || '',
    apiKeyValue: request.apiKeyValue || '',
    apiKeyIn: request.apiKeyIn || 'header',
    preRequestScript: request.preRequestScript || '',
    postResponseScript: request.postResponseScript || '',
    assertions: request.assertions || [],
    extractors: request.extractors || [],
    timeoutSeconds: request.timeoutSeconds || 30,
    followRedirects: request.followRedirects !== false,
    graphqlQuery: request.graphqlQuery || '',
    graphqlVariables: request.graphqlVariables || '',
    files: request.files || [],
    exampleBody: request.exampleBody || '',
    exampleContentType: request.exampleContentType || '',
  })
}

export function variableMap(workspace, environment, collection, request, globalEnvironment) {
  const map = {}
  for (const item of workspace?.variables || []) {
    if (item.enabled !== false && item.key) map[item.key] = item.value || ''
  }
  for (const item of globalEnvironment?.variables || []) {
    if (item.enabled !== false && item.key) map[item.key] = item.value || ''
  }
  for (const item of collection?.variables || []) {
    if (item.enabled !== false && item.key) map[item.key] = item.value || ''
  }
  for (const folder of folderChain(collection, request?.folderId)) {
    for (const item of folder.variables || []) {
      if (item.enabled !== false && item.key) map[item.key] = item.value || ''
    }
  }
  for (const item of request?.variables || []) {
    if (item.enabled !== false && item.key) map[item.key] = item.value || ''
  }
  for (const item of environment?.variables || []) {
    if (item.enabled !== false && item.key) map[item.key] = item.value || ''
  }
  return map
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, `'\\''`)}'`
}

function folderChain(collection, folderId) {
  const chain = []
  let current = folderId || ''
  const seen = new Set()
  while (current && !seen.has(current)) {
    seen.add(current)
    const folder = (collection?.folders || []).find((item) => item.id === current)
    if (!folder) break
    chain.push(folder)
    current = folder.parentId || ''
  }
  return chain.reverse()
}

function pushHeaders(target, rows) {
  for (const row of rows || []) {
    if (!row?.key) continue
    const index = target.findIndex((item) => item.key.toLowerCase() === row.key.toLowerCase())
    if (index >= 0) target[index] = row
    else target.push(row)
  }
}

export function withInherited(draft, collection) {
  if (!draft || !collection) return draft
  const headers = []
  pushHeaders(headers, collection.defaults?.headers)
  for (const folder of folderChain(collection, draft.folderId)) pushHeaders(headers, folder.defaults?.headers)
  pushHeaders(headers, draft.headers)
  let next = { ...draft, headers }
  if (!draft.authType || draft.authType === 'inherit') {
    const folders = folderChain(collection, draft.folderId)
    let chosen = null
    for (let index = folders.length - 1; index >= 0; index -= 1) {
      const type = folders[index].defaults?.authType
      if (type && type !== 'none' && type !== 'inherit') {
        chosen = folders[index].defaults
        break
      }
    }
    if (!chosen && collection.defaults?.authType && collection.defaults.authType !== 'none' && collection.defaults.authType !== 'inherit') {
      chosen = collection.defaults
    }
    if (chosen) {
      next = {
        ...next,
        authType: chosen.authType,
        authToken: chosen.authToken || '',
        authUsername: chosen.authUsername || '',
        authPassword: chosen.authPassword || '',
        apiKeyName: chosen.apiKeyName || '',
        apiKeyValue: chosen.apiKeyValue || '',
        apiKeyIn: chosen.apiKeyIn || 'header',
        extras: chosen.authType === 'oauth2' ? { ...(draft.extras || {}), ...(chosen.extras || {}) } : (draft.extras || {}),
      }
    } else {
      next = { ...next, authType: 'none' }
    }
  }
  return next
}

export function toCurl(draft, variables, collection) {
  draft = withInherited(draft, collection) || draft
  const method = draft.method || 'GET'
  const params = (draft.params || []).filter((item) => item.enabled !== false && item.key)
  let url = interpolate(draft.url || '', variables)
  if (params.length) {
    const query = params
      .map((item) => `${encodeURIComponent(interpolate(item.key, variables))}=${encodeURIComponent(interpolate(item.value || '', variables))}`)
      .join('&')
    url += (url.includes('?') ? '&' : '?') + query
  }
  const lines = [`curl -X ${method} ${shellQuote(url)}`]
  for (const header of draft.headers || []) {
    if (header.enabled === false || !header.key) continue
    lines.push(`  -H ${shellQuote(`${header.key}: ${interpolate(header.value || '', variables)}`)}`)
  }
  if (draft.authType === 'oauth2' && draft.extras?.oauthAccessToken) {
    lines.push(`  -H ${shellQuote(`Authorization: Bearer ${draft.extras.oauthAccessToken}`)}`)
  }
  if (draft.authType === 'bearer' && draft.authToken) {
    lines.push(`  -H ${shellQuote(`Authorization: Bearer ${interpolate(draft.authToken, variables)}`)}`)
  }
  if (draft.authType === 'basic') {
    lines.push(`  -u ${shellQuote(`${interpolate(draft.authUsername || '', variables)}:${interpolate(draft.authPassword || '', variables)}`)}`)
  }
  if (draft.authType === 'apikey' && draft.apiKeyName && (draft.apiKeyIn || 'header') !== 'query') {
    lines.push(`  -H ${shellQuote(`${interpolate(draft.apiKeyName, variables)}: ${interpolate(draft.apiKeyValue || '', variables)}`)}`)
  }
  if (draft.bodyType === 'json' || draft.bodyType === 'text') {
    lines.push(`  --data ${shellQuote(interpolate(draft.body || '', variables))}`)
  }
  if (draft.bodyType === 'graphql') {
    lines.push(`  --data ${shellQuote(JSON.stringify({ query: draft.graphqlQuery || '', variables: draft.graphqlVariables || {} }))}`)
  }
  return lines.join(' \\\n')
}

function resolvedUrl(draft, variables) {
  const params = (draft.params || []).filter((item) => item.enabled !== false && item.key)
  let url = interpolate(draft.url || '', variables)
  if (params.length) {
    const query = params
      .map((item) => `${encodeURIComponent(interpolate(item.key, variables))}=${encodeURIComponent(interpolate(item.value || '', variables))}`)
      .join('&')
    url += (url.includes('?') ? '&' : '?') + query
  }
  return url
}

function headerLines(draft, variables) {
  const lines = []
  for (const header of draft.headers || []) {
    if (header.enabled === false || !header.key) continue
    lines.push([header.key, interpolate(header.value || '', variables)])
  }
  if (draft.authType === 'bearer' && draft.authToken) lines.push(['Authorization', `Bearer ${interpolate(draft.authToken, variables)}`])
  if (draft.authType === 'oauth2' && draft.extras?.oauthAccessToken) lines.push(['Authorization', `Bearer ${draft.extras.oauthAccessToken}`])
  if (draft.authType === 'basic') {
    const token = btoa(`${interpolate(draft.authUsername || '', variables)}:${interpolate(draft.authPassword || '', variables)}`)
    lines.push(['Authorization', `Basic ${token}`])
  }
  if (draft.authType === 'apikey' && draft.apiKeyName && (draft.apiKeyIn || 'header') !== 'query') {
    lines.push([interpolate(draft.apiKeyName, variables), interpolate(draft.apiKeyValue || '', variables)])
  }
  return lines
}

function bodyText(draft, variables) {
  if (draft.bodyType === 'json' || draft.bodyType === 'text') return interpolate(draft.body || '', variables)
  if (draft.bodyType === 'graphql') return JSON.stringify({ query: draft.graphqlQuery || '', variables: draft.graphqlVariables || {} })
  return ''
}

export function toFetch(draft, variables, collection) {
  draft = withInherited(draft, collection) || draft
  const headers = Object.fromEntries(headerLines(draft, variables))
  const body = bodyText(draft, variables)
  const init = { method: draft.method || 'GET', headers }
  if (body) init.body = body
  return `const response = await fetch(${JSON.stringify(resolvedUrl(draft, variables))}, ${JSON.stringify(init, null, 2)})\nconst text = await response.text()`
}

export function toPython(draft, variables, collection) {
  draft = withInherited(draft, collection) || draft
  const headers = Object.fromEntries(headerLines(draft, variables))
  const body = bodyText(draft, variables)
  const lines = ['import requests', '', `response = requests.request(${JSON.stringify(draft.method || 'GET')}, ${JSON.stringify(resolvedUrl(draft, variables))}, headers=${JSON.stringify(headers)}${body ? `, data=${JSON.stringify(body)}` : ''})`, 'print(response.text)']
  return lines.join('\n')
}

export function toJava(draft, variables, collection) {
  draft = withInherited(draft, collection) || draft
  const url = resolvedUrl(draft, variables)
  const body = bodyText(draft, variables)
  const headers = headerLines(draft, variables).map(([key, value]) => `request.setHeader(${JSON.stringify(key)}, ${JSON.stringify(value)});`).join('\n')
  return `HttpRequest.Builder request = HttpRequest.newBuilder()\n  .uri(URI.create(${JSON.stringify(url)}))\n  .method(${JSON.stringify(draft.method || 'GET')}, ${body ? `HttpRequest.BodyPublishers.ofString(${JSON.stringify(body)})` : 'HttpRequest.BodyPublishers.noBody()'});\n${headers}`
}

function command(draft, environmentId, collectionId, globalEnvironmentId) {
  return {
    environmentId: environmentId || '',
    globalEnvironmentId: globalEnvironmentId || '',
    collectionId: collectionId || '',
    requestId: draft.id || '',
    requestName: draft.name || '',
    folderId: draft.folderId || '',
    method: draft.method || 'GET',
    url: draft.url || '',
    params: draft.params || [],
    headers: draft.headers || [],
    bodyType: draft.bodyType || 'none',
    body: draft.body || '',
    form: draft.form || [],
    authType: draft.authType || 'none',
    authToken: draft.authToken || '',
    authUsername: draft.authUsername || '',
    authPassword: draft.authPassword || '',
    timeoutSeconds: draft.timeoutSeconds || 30,
    followRedirects: draft.followRedirects !== false,
    graphqlQuery: draft.graphqlQuery || '',
    graphqlVariables: draft.graphqlVariables || '',
    files: draft.files || [],
    apiKeyName: draft.apiKeyName || '',
    apiKeyValue: draft.apiKeyValue || '',
    apiKeyIn: draft.apiKeyIn || 'header',
    preRequestScript: draft.preRequestScript || '',
    postResponseScript: draft.postResponseScript || '',
    extras: draft.extras || {},
    assertions: draft.assertions || [],
    extractors: draft.extractors || [],
  }
}

export function downloadResponse(response) {
  let blob
  if (response.binary && response.bodyBase64) {
    const bytes = Uint8Array.from(atob(response.bodyBase64), (char) => char.charCodeAt(0))
    blob = new Blob([bytes], { type: response.contentType || 'application/octet-stream' })
  } else {
    blob = new Blob([response.body || ''], { type: response.contentType || 'text/plain' })
  }
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename(response.contentType)
  link.click()
  URL.revokeObjectURL(link.href)
}

function filename(contentType = '') {
  if (contentType.includes('png')) return 'response.png'
  if (contentType.includes('jpeg')) return 'response.jpg'
  if (contentType.includes('pdf')) return 'response.pdf'
  if (contentType.includes('json')) return 'response.json'
  return 'response.txt'
}

export const api = {
  workspace: () => send('/api/workspace'),
  createCollection: (name) => send('/api/collections', { method: 'POST', body: JSON.stringify({ name }) }),
  renameCollection: (id, name) => send(`/api/collections/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) }),
  updateCollection: (id, collection) => send(`/api/collections/${id}`, { method: 'PUT', body: JSON.stringify(collection) }),
  deleteCollection: (id) => send(`/api/collections/${id}`, { method: 'DELETE' }),
  exportCollection: (id) => send(`/api/collections/${id}/export`),
  importDocument: (content) => send('/api/import', { method: 'POST', body: JSON.stringify({ content }) }),
  createFolder: (collectionId, name, parentId) =>
    send(`/api/collections/${collectionId}/folders`, { method: 'POST', body: JSON.stringify({ name, parentId: parentId || '' }) }),
  deleteFolder: (id) => send(`/api/folders/${id}`, { method: 'DELETE' }),
  updateFolder: (id, folder) => send(`/api/folders/${id}`, { method: 'PUT', body: JSON.stringify(folder) }),
  renameFolder: (id, name) => send(`/api/folders/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) }),
  importCurl: (collectionId, content, folderId) =>
    send(`/api/collections/${collectionId}/curl`, { method: 'POST', body: JSON.stringify({ content, folderId: folderId || '' }) }),
  createRequest: (collectionId, name, folderId) =>
    send(`/api/collections/${collectionId}/requests`, { method: 'POST', body: JSON.stringify({ name, folderId: folderId || '' }) }),
  updateRequest: (id, request) => send(`/api/requests/${id}`, { method: 'PUT', body: JSON.stringify(request) }),
  deleteRequest: (id) => send(`/api/requests/${id}`, { method: 'DELETE' }),
  duplicateRequest: (id) => send(`/api/requests/${id}/duplicate`, { method: 'POST', body: '{}' }),
  reorder: (collectionId, folderId, requestIds) =>
    send(`/api/collections/${collectionId}/order`, { method: 'PUT', body: JSON.stringify({ folderId: folderId || '', requestIds }) }),
  createAdhocRequest: () => send('/api/adhoc', { method: 'POST', body: '{}' }),
  updateAdhocRequest: (id, request) => send(`/api/adhoc/${id}`, { method: 'PUT', body: JSON.stringify(request) }),
  deleteAdhocRequest: (id) => send(`/api/adhoc/${id}`, { method: 'DELETE' }),
  saveAdhocToCollection: (id, collectionId, folderId) =>
    send(`/api/adhoc/${id}/save`, { method: 'POST', body: JSON.stringify({ collectionId, folderId: folderId || '' }) }),
  runCollection: (id, environmentId, folderId, stopOnFailure, dataCsv, parallel, delayMs, tags, globalEnvironmentId, datasetId, options = {}) =>
    send(`/api/collections/${id}/run`, {
      method: 'POST',
      body: JSON.stringify({
        environmentId: environmentId || '',
        globalEnvironmentId: globalEnvironmentId || '',
        folderId: folderId || '',
        stopOnFailure: !!stopOnFailure,
        dataCsv: dataCsv || '',
        parallel: !!parallel,
        delayMs: Number(delayMs) || 0,
        tags: tags || [],
        datasetId: datasetId || '',
        keepVariables: options.keepVariables !== false,
        ignoreCookies: !!options.ignoreCookies,
        saveCookies: options.saveCookies !== false,
        mockBaseUrl: options.mockBaseUrl || '',
        shareResults: !!options.shareResults,
        quietLogs: !!options.quietLogs,
        requestIds: options.requestIds || [],
      }),
    }),
  createEnvironment: (name) => send('/api/environments', { method: 'POST', body: JSON.stringify({ name }) }),
  updateEnvironment: (id, environment) =>
    send(`/api/environments/${id}`, { method: 'PUT', body: JSON.stringify(environment) }),
  deleteEnvironment: (id) => send(`/api/environments/${id}`, { method: 'DELETE' }),
  clearHistory: () => send('/api/history', { method: 'DELETE' }),
  clearCookies: () => send('/api/cookies', { method: 'DELETE' }),
  deleteCookie: (domain, name) => send('/api/cookies/remove', { method: 'POST', body: JSON.stringify({ domain, name }) }),
  importBruno: (files) => send('/api/import/bruno', { method: 'POST', body: JSON.stringify({ files }) }),
  oauthStart: (body) => send('/api/oauth/start', { method: 'POST', body: JSON.stringify(body) }),
  oauthDeviceStart: (body) => send('/api/oauth/device/start', { method: 'POST', body: JSON.stringify(body) }),
  oauthDevicePoll: (body) => send('/api/oauth/device/poll', { method: 'POST', body: JSON.stringify(body) }),
  startMock: (id, port) => send(`/api/collections/${id}/mock`, { method: 'POST', body: JSON.stringify({ port: Number(port) || 4010 }) }),
  stopMock: () => send('/api/mock', { method: 'DELETE' }),
  gitStatus: () => send('/api/git'),
  gitInit: () => send('/api/git/init', { method: 'POST', body: '{}' }),
  gitCommit: (message) => send('/api/git/commit', { method: 'POST', body: JSON.stringify({ name: message }) }),
  gitPull: (remote) => send('/api/git/pull', { method: 'POST', body: JSON.stringify({ name: remote || '' }) }),
  gitPush: (remote) => send('/api/git/push', { method: 'POST', body: JSON.stringify({ name: remote || '' }) }),
  gitRemote: (name, url) => send('/api/git/remote', { method: 'POST', body: JSON.stringify({ name, url }) }),
  gitBranch: (name, create) => send('/api/git/branch', { method: 'POST', body: JSON.stringify({ name, create: !!create }) }),
  wsOpen: (body) => send('/api/ws/open', { method: 'POST', body: JSON.stringify(body) }),
  wsFrames: (id) => send(`/api/ws/${id}`),
  wsSend: (id, text) => send(`/api/ws/${id}/send`, { method: 'POST', body: JSON.stringify({ text }) }),
  wsClose: (id) => send(`/api/ws/${id}`, { method: 'DELETE' }),
  updateWorkspace: (workspace) => send('/api/workspace', { method: 'PUT', body: JSON.stringify(workspace) }),
  exportWorkspace: () => send('/api/export/workspace'),
  exportWorkspaceBundle: () => send('/api/export/workspace/bundle'),
  importWorkspaceBundle: (content) => send('/api/import/workspace/bundle', { method: 'POST', body: JSON.stringify({ content }) }),
  startExampleMock: (id, port) => send(`/api/collections/${id}/mock/examples`, { method: 'POST', body: JSON.stringify({ port: Number(port) || 4010 }) }),
  startManualMock: (port, routes = []) => send('/api/mock/manual', { method: 'POST', body: JSON.stringify({ port: Number(port) || 4010, routes }) }),
  sseOpen: (body) => send('/api/sse/open', { method: 'POST', body: JSON.stringify(body) }),
  sseFrames: (id) => send(`/api/sse/${id}`),
  sseClose: (id) => send(`/api/sse/${id}`, { method: 'DELETE' }),
  exportBruno: (id) => fetch(`/api/collections/${id}/export/bruno`).then((r) => r.text()),
  exportBrunoFolder: (id) => send(`/api/collections/${id}/export/bruno-folder`),
  exportPostman: (id) => fetch(`/api/collections/${id}/export/postman`).then((r) => r.text()),
  exportOpenApi: (id) => fetch(`/api/collections/${id}/export/openapi`).then((r) => r.text()),
  exportOpenCollection: (id) => fetch(`/api/collections/${id}/export/opencollection`).then((r) => r.text()),
  syncOpenApi: (id, content, mode = 'additive', deleteStale = false) => send(`/api/collections/${id}/openapi/sync`, { method: 'POST', body: JSON.stringify({ content, mode, deleteStale }) }),
  openApiDiff: (id, content) => send(`/api/collections/${id}/openapi/diff`, { method: 'POST', body: JSON.stringify({ content }) }),
  grpcReflect: (url, proto) => send('/api/grpc/reflect', { method: 'POST', body: JSON.stringify({ url, proto: proto || '' }) }),
  grpcEncode: (proto, method, json) => send('/api/grpc/encode', { method: 'POST', body: JSON.stringify({ proto, method, json }) }),
  grpcDecode: (proto, method, encoded) => send('/api/grpc/decode', { method: 'POST', body: JSON.stringify({ proto, method, encoded }) }),
  npmStatus: () => send('/api/scripts/npm'),
  npmInstall: () => send('/api/scripts/npm/install', { method: 'POST', body: '{}' }),
  npmAdd: (name) => send('/api/scripts/npm/add', { method: 'POST', body: JSON.stringify({ name }) }),
  npmSavePackageJson: (content) => send('/api/scripts/npm/package', { method: 'PUT', body: JSON.stringify({ content }) }),
  organizePostman: (content) => send('/api/import/postman/organize', { method: 'POST', body: JSON.stringify({ content }) }),
  previewPostmanScripts: (content) => send('/api/import/postman/scripts', { method: 'POST', body: JSON.stringify({ content }) }),
  generateCollectionDocs: (id) => send(`/api/collections/${id}/docs/generate`, { method: 'POST', body: '{}' }),
  deployCollectionDocs: (id) => send(`/api/collections/${id}/docs/deploy`, { method: 'POST', body: '{}' }),
  exportDocsBundle: async (id, filename) => {
    const response = await fetch(`/api/collections/${id}/docs/bundle`)
    if (!response.ok) throw new Error(await response.text() || response.statusText)
    const blob = await response.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename || 'docs.zip'
    link.click()
    URL.revokeObjectURL(link.href)
  },
  generateApp: (prompt, context) => send('/api/apps/generate', { method: 'POST', body: JSON.stringify({ prompt, context: context || '' }) }),
  resyncMock: (id) => send(`/api/collections/${id}/mock/resync`, { method: 'POST', body: '{}' }),
  secretMigrationPreview: () => send('/api/secrets/migrate/preview'),
  migrateSecrets: (environmentId) => send('/api/secrets/migrate', { method: 'POST', body: JSON.stringify({ environmentId }) }),
  listProfiles: () => send('/api/profiles'),
  saveProfile: (name) => send(`/api/profiles/${encodeURIComponent(name)}`, { method: 'POST', body: '{}' }),
  loadProfile: (name) => send(`/api/profiles/${encodeURIComponent(name)}/load`, { method: 'POST', body: '{}' }),
  wsCloseFrame: (id, closeCode = 1000, closeReason = '') =>
    send(`/api/ws/${id}/send`, { method: 'POST', body: JSON.stringify({ close: true, closeCode, closeReason }) }),
  gitPullRequests: (remote) => send(`/api/git/prs?remote=${encodeURIComponent(remote || '')}`),
  gitCreatePr: (remote, title, body, head, base) => send('/api/git/pr/create', { method: 'POST', body: JSON.stringify({ remote, title, body, head, base }) }),
  gitMergePr: (remote, number) => send('/api/git/pr/merge', { method: 'POST', body: JSON.stringify({ remote, number }) }),
  listApps: () => send('/api/apps'),
  saveApp: (manifest) => send('/api/apps', { method: 'POST', body: JSON.stringify(manifest) }),
  runApp: (id, environmentId) => send(`/api/apps/${id}/run`, { method: 'POST', body: JSON.stringify({ environmentId: environmentId || '' }) }),
  deleteApp: (id) => send(`/api/apps/${id}`, { method: 'DELETE' }),
  gitConflicts: () => send('/api/git/conflicts'),
  gitResolveConflict: (path, blockIndex, choice) => send('/api/git/conflicts/resolve', { method: 'POST', body: JSON.stringify({ path, blockIndex, choice }) }),
  aiScript: (prompt, context) => send('/api/ai/script', { method: 'POST', body: JSON.stringify({ prompt, context: context || '' }) }),
  aiChat: (prompt, context, sessionId) => send('/api/ai/chat', { method: 'POST', body: JSON.stringify({ prompt, context: context || '', sessionId: sessionId || '' }) }),
  aiHistory: (sessionId) => send(`/api/ai/history?session=${encodeURIComponent(sessionId || '')}`),
  aiChatStream: async (prompt, context, sessionId, onChunk) => {
    let response
    try {
      response = await fetch('/api/ai/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt, context: context || '', sessionId: sessionId || '' }),
      })
    } catch {
      throw new Error('Cannot reach the ApiFlow backend for AI streaming.')
    }
    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || response.statusText)
    }
    const reader = response.body?.getReader()
    if (!reader) {
      const text = await response.text()
      onChunk(text)
      return text
    }
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const part of parts) {
        for (const line of part.split('\n')) {
          if (line.startsWith('data:')) {
            const chunk = line.slice(5).trim()
            if (chunk && chunk !== '[DONE]') onChunk(chunk)
          }
        }
      }
    }
    return buffer
  },
  grpcStreamOpen: (body) => send('/api/grpc/stream/open', { method: 'POST', body: JSON.stringify(body) }),
  grpcStreamFrames: (id) => send(`/api/grpc/stream/${id}`),
  grpcStreamSend: (id, content) => send(`/api/grpc/stream/${id}/send`, { method: 'POST', body: JSON.stringify({ content }) }),
  grpcStreamFinish: (id) => send(`/api/grpc/stream/${id}/finish`, { method: 'POST', body: '{}' }),
  grpcStreamClose: (id) => send(`/api/grpc/stream/${id}`, { method: 'DELETE' }),
  gitFetch: (remote) => send('/api/git/fetch', { method: 'POST', body: JSON.stringify({ name: remote || '' }) }),
  submitAppRequest: (id, payload, environmentId) => send(`/api/apps/${id}/submit`, { method: 'POST', body: JSON.stringify({ ...(payload || {}), environmentId: environmentId || '' }) }),
  runAppRequest: (id, requestName, environmentId) => send(`/api/apps/${id}/run-request`, { method: 'POST', body: JSON.stringify({ requestName, environmentId: environmentId || '' }) }),
  setAppVar: (id, name, value, environmentId, envScope) => send(`/api/apps/${id}/set-var`, { method: 'POST', body: JSON.stringify({ name, value, environmentId: environmentId || '', envScope: !!envScope }) }),
  parseBru: (content) => send('/api/bru/parse', { method: 'POST', body: JSON.stringify({ content }) }),
  exportEnvironment: (id) => fetch(`/api/environments/${id}/export`).then((r) => r.text()),
  collectionDocsHtml: (id) => fetch(`/api/collections/${id}/docs/html`).then((r) => r.text()),
  mockLog: () => send('/api/mock/log'),
  startOpenApiMock: (content, port) => send('/api/mock/openapi', { method: 'POST', body: JSON.stringify({ content, port: Number(port) || 4010, key: 'openapi' }) }),
  addCookie: (cookie) => send('/api/cookies', { method: 'POST', body: JSON.stringify(cookie) }),
  importEnvironment: (content) => send('/api/import', { method: 'POST', body: JSON.stringify({ content }) }),
  gitClone: (url, name) => send('/api/git/clone', { method: 'POST', body: JSON.stringify({ url, name }) }),
  graphqlIntrospect: (url, headers) => send('/api/graphql/introspect', { method: 'POST', body: JSON.stringify({ url, headers }) }),
  wsSendBinary: (id, binary) => send(`/api/ws/${id}/send`, { method: 'POST', body: JSON.stringify({ binary }) }),
  wsPing: (id) => send(`/api/ws/${id}/send`, { method: 'POST', body: JSON.stringify({ ping: true }) }),
  execute: (draft, environmentId, collectionId, promptVars, globalEnvironmentId) =>
    send('/api/execute', { method: 'POST', body: JSON.stringify({ ...command(draft, environmentId, collectionId, globalEnvironmentId), promptVars: promptVars || {} }) }),
  saveSpec: (spec) => send('/api/platform/specs', { method: 'POST', body: JSON.stringify(spec) }),
  lintSpec: (id) => send(`/api/platform/specs/${id}/lint`, { method: 'POST', body: '{}' }),
  syncSpecToCollection: (id, collectionId) => send(`/api/platform/specs/${id}/sync-collection`, { method: 'POST', body: JSON.stringify({ collectionId, mode: 'update' }) }),
  syncCollectionToSpec: (id) => send(`/api/platform/collections/${id}/sync-spec`, { method: 'POST', body: '{}' }),
  saveMonitor: (monitor) => send('/api/platform/monitors', { method: 'POST', body: JSON.stringify(monitor) }),
  runMonitor: (id) => send(`/api/platform/monitors/${id}/run`, { method: 'POST', body: '{}' }),
  publishMonitorRun: (runId) => send(`/api/platform/monitors/runs/${runId}/publish`, { method: 'POST', body: '{}' }),
  monitorReportUrl: (runId) => `/api/platform/monitors/runs/${runId}/report`,
  saveFlow: (flow) => send('/api/platform/flows', { method: 'POST', body: JSON.stringify(flow) }),
  runFlow: (id, input) => send(`/api/platform/flows/${id}/run`, { method: 'POST', body: JSON.stringify(input || {}) }),
  deployFlow: (id, port) => send(`/api/platform/flows/${id}/deploy`, { method: 'POST', body: JSON.stringify({ port: Number(port) || 4020 }) }),
  generateFlow: (prompt) => send('/api/platform/flows/generate', { method: 'POST', body: JSON.stringify({ prompt }) }),
  saveDataset: (dataset) => send('/api/platform/datasets', { method: 'POST', body: JSON.stringify(dataset) }),
  queryDataset: (id, sql) => send(`/api/platform/datasets/${id}/query`, { method: 'POST', body: JSON.stringify({ sql }) }),
  saveDocument: (doc) => send('/api/platform/documents', { method: 'POST', body: JSON.stringify(doc) }),
  publishDocs: (id) => send(`/api/platform/collections/${id}/docs/publish`, { method: 'POST', body: '{}' }),
  embedDocs: (id) => send(`/api/platform/collections/${id}/docs/embed`),
  shareRequest: (request, response) => send('/api/platform/share', { method: 'POST', body: JSON.stringify({ request, response }) }),
  startPublicMock: (id, port) => send(`/api/platform/collections/${id}/mock/public`, { method: 'POST', body: JSON.stringify({ port: Number(port) || 4010 }) }),
  setMockChaos: (enabled) => send('/api/platform/mock/chaos', { method: 'POST', body: JSON.stringify({ enabled: !!enabled }) }),
  validateRequest: (collectionId, request) => send(`/api/platform/collections/${collectionId}/validate`, { method: 'POST', body: JSON.stringify(request) }),
  performanceTest: (body) => send('/api/platform/performance', { method: 'POST', body: JSON.stringify(body) }),
  startPerformance: (body) => send('/api/platform/performance/start', { method: 'POST', body: JSON.stringify(body) }),
  livePerformance: (id) => send(`/api/platform/performance/live/${id}`),
  performanceHistory: (collectionId) => send(`/api/platform/performance${collectionId ? `?collectionId=${collectionId}` : ''}`),
  comparePerformance: (leftId, rightId) => send(`/api/platform/performance/compare?leftId=${leftId}&rightId=${rightId}`),
  addComment: (comment) => send('/api/platform/comments', { method: 'POST', body: JSON.stringify(comment) }),
  saveMember: (member) => send('/api/platform/members', { method: 'POST', body: JSON.stringify(member) }),
  saveWebhook: (webhook) => send('/api/platform/webhooks', { method: 'POST', body: JSON.stringify(webhook) }),
  captureInventory: (body) => send('/api/platform/inventory/capture', { method: 'POST', body: JSON.stringify(body) }),
  generateSdk: (collectionId, language) => send('/api/platform/sdk', { method: 'POST', body: JSON.stringify({ collectionId, language }) }),
  unlockVault: (passphrase) => send('/api/platform/vault/unlock', { method: 'POST', body: JSON.stringify({ passphrase }) }),
  startGrpcMock: (port) => send('/api/platform/grpc/mock', { method: 'POST', body: JSON.stringify({ port: Number(port) || 50051 }) }),
  applyModel: (collectionId, example) => send(`/api/platform/collections/${collectionId}/model`, { method: 'POST', body: JSON.stringify({ example }) }),
  openPull: (body) => send('/api/platform/pulls', { method: 'POST', body: JSON.stringify(body) }),
  mergePull: (id) => send(`/api/platform/pulls/${id}/merge`, { method: 'POST', body: '{}' }),
  uptime: () => send('/api/platform/monitors/uptime'),
  saveVault: (key, value) => send('/api/platform/vault', { method: 'POST', body: JSON.stringify({ key, value }) }),
  deleteVault: (key) => send(`/api/platform/vault/${encodeURIComponent(key)}`, { method: 'DELETE' }),
  startCapture: (port) => send('/api/platform/capture/start', { method: 'POST', body: JSON.stringify({ port: Number(port) || 8888 }) }),
  stopCapture: () => send('/api/platform/capture/stop', { method: 'POST', body: '{}' }),
  captured: () => send('/api/platform/capture'),
  importCaptured: (collectionId) => send('/api/platform/capture/import', { method: 'POST', body: JSON.stringify({ collectionId }) }),
  saveSchedule: (body) => send('/api/platform/schedules', { method: 'POST', body: JSON.stringify(body) }),
  debugRun: (report) => send('/api/platform/runs/debug', { method: 'POST', body: JSON.stringify(report) }),
  inferTypes: (example) => send('/api/platform/specs/infer', { method: 'POST', body: JSON.stringify({ example }) }),
  standaloneMock: (collectionId) => send(`/api/platform/mocks/${collectionId}/standalone`, { method: 'POST', body: '{}' }),
  forkCollection: (id) => send(`/api/platform/collections/${id}/fork`, { method: 'POST', body: '{}' }),
  simulate: (body) => send('/api/platform/simulate', { method: 'POST', body: JSON.stringify(body) }),
  packages: () => send('/api/platform/packages'),
  savePackage: (script) => send('/api/platform/packages', { method: 'POST', body: JSON.stringify(script) }),
  flowHistory: (id) => send(`/api/platform/flows/${id}/history`),
  mqttConnect: (body) => send('/api/platform/mqtt/connect', { method: 'POST', body: JSON.stringify(body) }),
  mqttPublish: (id, body) => send(`/api/platform/mqtt/${id}/publish`, { method: 'POST', body: JSON.stringify(body) }),
  mqttFrames: (id) => send(`/api/platform/mqtt/${id}/frames`),
  mqttClose: (id) => send(`/api/platform/mqtt/${id}`, { method: 'DELETE' }),
  socketConnect: (body) => send('/api/platform/socketio/connect', { method: 'POST', body: JSON.stringify(body) }),
  socketEmit: (id, body) => send(`/api/platform/socketio/${id}/emit`, { method: 'POST', body: JSON.stringify(body) }),
  socketFrames: (id) => send(`/api/platform/socketio/${id}/frames`),
  socketClose: (id) => send(`/api/platform/socketio/${id}`, { method: 'DELETE' }),
}

export function findRequest(workspace, id) {
  for (const collection of workspace.collections || []) {
    const request = (collection.requests || []).find((item) => item.id === id)
    if (request) return { collection, request }
  }
  const adhoc = (workspace.adhocRequests || []).find((item) => item.id === id)
  if (adhoc) return { collection: null, request: adhoc, adhoc: true }
  return null
}

export function findAdhocRequest(workspace, id) {
  return (workspace.adhocRequests || []).find((item) => item.id === id) || null
}

export function globalEnvironments(workspace) {
  return (workspace.environments || []).filter((item) => item.global)
}

export function localEnvironments(workspace) {
  return (workspace.environments || []).filter((item) => !item.global)
}

export function findEnvironment(workspace, id) {
  return (workspace.environments || []).find((item) => item.id === id) || null
}

export function findCollection(workspace, id) {
  return (workspace.collections || []).find((item) => item.id === id) || null
}
