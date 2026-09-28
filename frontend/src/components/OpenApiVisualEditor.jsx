import { useEffect, useMemo, useState } from 'react'

function parseSpec(text) {
  if (!text?.trim()) return { title: 'API', version: '1.0.0', paths: [] }
  try {
    const data = text.trim().startsWith('{') ? JSON.parse(text) : parseYamlLite(text)
    const paths = []
    for (const [path, methods] of Object.entries(data.paths || {})) {
      for (const [method, operation] of Object.entries(methods || {})) {
        if (['get', 'post', 'put', 'patch', 'delete', 'head', 'options'].includes(method)) {
          paths.push({
            path,
            method: method.toUpperCase(),
            summary: operation.summary || operation.operationId || '',
            description: operation.description || '',
          })
        }
      }
    }
    return {
      title: data.info?.title || 'API',
      version: data.info?.version || '1.0.0',
      paths,
      raw: data,
    }
  } catch {
    return { title: 'API', version: '1.0.0', paths: [], error: 'Could not parse spec' }
  }
}

function parseYamlLite(text) {
  const lines = text.split('\n')
  const root = { openapi: '3.0.0', info: { title: 'API', version: '1.0.0' }, paths: {} }
  let currentPath = ''
  let currentMethod = ''
  for (const line of lines) {
    const trimmed = line.trim()
    if (trimmed.startsWith('title:')) root.info.title = trimmed.slice(6).trim()
    if (trimmed.startsWith('version:')) root.info.version = trimmed.slice(8).trim()
    const pathMatch = trimmed.match(/^(\/[^\s:]+\/?):$/)
    if (pathMatch) {
      currentPath = pathMatch[1]
      root.paths[currentPath] = root.paths[currentPath] || {}
      continue
    }
    const methodMatch = trimmed.match(/^(get|post|put|patch|delete|head|options):$/)
    if (methodMatch && currentPath) {
      currentMethod = methodMatch[1]
      root.paths[currentPath][currentMethod] = root.paths[currentPath][currentMethod] || {}
      continue
    }
    if (trimmed.startsWith('summary:') && currentPath && currentMethod) {
      root.paths[currentPath][currentMethod].summary = trimmed.slice(8).trim()
    }
  }
  return root
}

function rebuildSpec(parsed, paths) {
  const next = parsed.raw ? structuredClone(parsed.raw) : { openapi: '3.0.0', info: { title: parsed.title, version: parsed.version }, paths: {} }
  next.info = { ...(next.info || {}), title: parsed.title, version: parsed.version }
  next.paths = {}
  for (const item of paths) {
    next.paths[item.path] = next.paths[item.path] || {}
    next.paths[item.path][item.method.toLowerCase()] = {
      summary: item.summary,
      description: item.description,
      responses: { 200: { description: 'OK' } },
    }
  }
  return JSON.stringify(next, null, 2)
}

export function OpenApiVisualEditor({ spec, onChange, previewUrl }) {
  const parsed = useMemo(() => parseSpec(spec), [spec])
  const [rows, setRows] = useState(parsed.paths)
  const [title, setTitle] = useState(parsed.title)
  const [version, setVersion] = useState(parsed.version)
  const [showPreview, setShowPreview] = useState(false)

  useEffect(() => {
    setRows(parsed.paths)
    setTitle(parsed.title)
    setVersion(parsed.version)
  }, [parsed.title, parsed.version, parsed.paths])

  function commit(nextRows = rows, nextTitle = title, nextVersion = version) {
    onChange(rebuildSpec({ ...parsed, title: nextTitle, version: nextVersion }, nextRows))
  }

  function updateRow(index, patch) {
    const next = rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row)
    setRows(next)
    commit(next)
  }

  function addRow() {
    const next = [...rows, { path: '/new-endpoint', method: 'GET', summary: 'New operation', description: '' }]
    setRows(next)
    commit(next)
  }

  function removeRow(index) {
    const next = rows.filter((_, rowIndex) => rowIndex !== index)
    setRows(next)
    commit(next)
  }

  return (
    <div className="stack">
      <div className="url-row">
        <label>Title<input value={title} onChange={(event) => { setTitle(event.target.value); commit(rows, event.target.value, version) }} /></label>
        <label>Version<input value={version} onChange={(event) => { setVersion(event.target.value); commit(rows, title, event.target.value) }} /></label>
        <button type="button" className="secondary" onClick={() => setShowPreview((current) => !current)}>{showPreview ? 'Hide preview' : 'Inline preview'}</button>
      </div>
      {parsed.error && <p className="call-error">{parsed.error}</p>}
      <div className="card-list openapi-visual-table">
        {rows.map((row, index) => (
          <div key={`${row.method}-${row.path}-${index}`} className="section-card stack">
            <div className="url-row">
              <select value={row.method} onChange={(event) => updateRow(index, { method: event.target.value })}>
                {['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((method) => (
                  <option key={method} value={method}>{method}</option>
                ))}
              </select>
              <input value={row.path} onChange={(event) => updateRow(index, { path: event.target.value })} placeholder="/users/{id}" />
              <button type="button" className="icon danger" onClick={() => removeRow(index)}>×</button>
            </div>
            <input value={row.summary} onChange={(event) => updateRow(index, { summary: event.target.value })} placeholder="Summary" />
            <input value={row.description} onChange={(event) => updateRow(index, { description: event.target.value })} placeholder="Description" />
          </div>
        ))}
      </div>
      <button type="button" className="secondary" onClick={addRow}>Add operation</button>
      {showPreview && (
        previewUrl ? (
          <iframe className="spec-preview-frame" title="OpenAPI preview" src={previewUrl} />
        ) : (
          <pre className="markup">{spec}</pre>
        )
      )}
    </div>
  )
}
