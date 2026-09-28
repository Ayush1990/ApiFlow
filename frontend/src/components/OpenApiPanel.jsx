import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Select } from './Select.jsx'
import { OpenApiVisualEditor } from './OpenApiVisualEditor.jsx'

export function OpenApiPanel({ collection }) {
  const [spec, setSpec] = useState(collection?.openApiSpec || '')
  const [mockPort, setMockPort] = useState('4010')
  const [syncMode, setSyncMode] = useState('update')
  const [deleteStale, setDeleteStale] = useState(false)
  const [diff, setDiff] = useState(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [visualMode, setVisualMode] = useState(true)

  useEffect(() => {
    if (collection?.openApiSpec) {
      setSpec(collection.openApiSpec)
    }
  }, [collection?.id, collection?.openApiSpec])

  async function sync() {
    if (!collection?.id) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const result = await api.syncOpenApi(collection.id, spec, syncMode, deleteStale)
      setMessage(`Synced — ${result.workspace?.collections?.find((item) => item.id === collection.id)?.requests?.length || 0} requests in collection`)
      setDiff(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function exportSpec() {
    if (!collection?.id) return
    setBusy(true)
    setError('')
    try {
      const text = await api.exportOpenApi(collection.id)
      setSpec(text)
      setMessage('Loaded merged OpenAPI spec from collection')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function previewDiff() {
    if (!collection?.id || !spec.trim()) return
    setBusy(true)
    setError('')
    try {
      setDiff(await api.openApiDiff(collection.id, spec))
      setMessage('Diff preview ready — review changes before syncing')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function startMock() {
    if (!spec.trim()) {
      setError('Paste or load an OpenAPI spec first')
      return
    }
    setBusy(true)
    setError('')
    try {
      const state = await api.startOpenApiMock(spec, Number(mockPort) || 4010)
      setMessage(`OpenAPI mock listening on http://127.0.0.1:${state.port}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack">
      <h3 className="section-title">OpenAPI sync & mock</h3>
      <p className="section-desc muted">Sync requests from an OpenAPI spec, preview changes, export the merged spec, or start a mock server.</p>
      <div className="url-row">
        <button type="button" className="secondary" disabled={busy} onClick={exportSpec}>Load from collection</button>
        <label className="secondary file-button">
          Import file
          <input type="file" accept=".json,.yaml,.yml" hidden onChange={(event) => {
            const file = event.target.files?.[0]
            if (!file) return
            file.text().then((text) => {
              setSpec(text)
              setMessage(`Loaded ${file.name}`)
            })
            event.target.value = ''
          }} />
        </label>
        <button type="button" className="secondary" disabled={busy || !spec.trim()} onClick={previewDiff}>Preview diff</button>
        <button type="button" className="send" disabled={busy || !spec.trim() || !diff} onClick={sync}>Sync into collection</button>
      </div>
      <div className="url-row">
        <label>
          Sync mode
          <Select
            value={syncMode}
            onChange={setSyncMode}
            options={[
              { value: 'additive', label: 'Additive (new only)' },
              { value: 'update', label: 'Update existing' },
              { value: 'replace', label: 'Replace (full resync)' },
            ]}
          />
        </label>
        <label className="check-line">
          <input type="checkbox" checked={deleteStale} onChange={(event) => setDeleteStale(event.target.checked)} />
          Remove stale synced requests
        </label>
      </div>
      {diff && (
        <div className="stack">
          <h4>Spec diff</h4>
          {diff.added?.length > 0 && <p className="muted">Added: {diff.added.join(', ')}</p>}
          {diff.updated?.length > 0 && <p className="muted">Updated: {diff.updated.join(', ')}</p>}
          {diff.removed?.length > 0 && <p className="muted">Removed from collection: {diff.removed.join(', ')}</p>}
          {!diff.added?.length && !diff.updated?.length && !diff.removed?.length && <p className="muted">No changes detected.</p>}
        </div>
      )}
      <div className="url-row">
        <button type="button" className={visualMode ? 'send' : 'secondary'} onClick={() => setVisualMode(true)}>Visual editor</button>
        <button type="button" className={!visualMode ? 'send' : 'secondary'} onClick={() => setVisualMode(false)}>Raw spec</button>
      </div>
      {visualMode ? (
        <OpenApiVisualEditor spec={spec} onChange={setSpec} previewUrl={collection?.specId ? `http://localhost:8080/api/platform/specs/${collection.specId}/preview` : ''} />
      ) : (
        <label className="stack">
          OpenAPI JSON/YAML
          <textarea className="body" value={spec} onChange={(event) => setSpec(event.target.value)} placeholder='{"openapi":"3.0.0",...}' spellCheck={false} />
        </label>
      )}
      <div className="url-row">
        <label>Mock port<input value={mockPort} onChange={(event) => setMockPort(event.target.value)} /></label>
        <button type="button" className="secondary" disabled={busy || !spec.trim()} onClick={startMock}>Start OpenAPI mock</button>
      </div>
      {message && <p className="muted">{message}</p>}
      {error && <p className="call-error">{error}</p>}
    </div>
  )
}
