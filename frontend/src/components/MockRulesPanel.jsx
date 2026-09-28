import { useState } from 'react'
import { api } from '../api.js'
import { Select } from './Select.jsx'

const emptyRoute = () => ({
  method: 'GET',
  path: '/api/example',
  query: '',
  status: 200,
  contentType: 'application/json',
  body: '{"ok":true}',
  bodyMatch: '',
  delayMs: 0,
})

export function MockRulesPanel({ collection, onClose, onStarted, mockRunning, onResync }) {
  const [port, setPort] = useState('4010')
  const [mode, setMode] = useState(collection ? 'collection' : 'manual')
  const [routes, setRoutes] = useState([emptyRoute()])
  const [publicBind, setPublicBind] = useState(false)
  const [chaos, setChaos] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [publicUrl, setPublicUrl] = useState('')

  function updateRoute(index, patch) {
    setRoutes((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  async function start() {
    setBusy(true)
    setError('')
    try {
      const parsedPort = Number(port) || 4010
      let state
      if (mode === 'examples' && collection) {
        state = await api.startExampleMock(collection.id, parsedPort)
      }
      else if (mode === 'manual') {
        state = await api.startManualMock(parsedPort, routes)
      }
      else if (collection && publicBind) {
        state = await api.startPublicMock(collection.id, parsedPort)
      }
      else if (collection) {
        state = await api.startMock(collection.id, parsedPort)
      }
      else {
        throw new Error('Choose a collection or manual mode')
      }
      if (chaos) await api.setMockChaos(true)
      setPublicUrl(state.publicUrl || '')
      onStarted?.(state)
      onClose?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-back" onClick={onClose}>
      <div className="modal-card stack" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
        <h3>Mock server</h3>
        <p className="muted">Start a collection mock, sync from saved examples, or define manual routes.</p>
        <label>
          Mode
          <Select
            value={mode}
            onChange={setMode}
            options={[
              ...(collection ? [{ value: 'collection', label: 'Collection routes' }] : []),
              ...(collection ? [{ value: 'examples', label: 'From response examples' }] : []),
              { value: 'manual', label: 'Manual rules' },
            ]}
          />
        </label>
        <label>Port<input value={port} onChange={(event) => setPort(event.target.value)} /></label>
        <label><input type="checkbox" checked={publicBind} onChange={(event) => setPublicBind(event.target.checked)} /> Cloud/LAN URL (bind 0.0.0.0)</label>
        <label><input type="checkbox" checked={chaos} onChange={(event) => setChaos(event.target.checked)} /> Chaos mode (random delays & failures)</label>
        {publicUrl && <p className="muted">Mock URL: {publicUrl}</p>}
        {mockRunning && collection && (
          <button type="button" className="secondary" onClick={onResync}>Re-sync routes from collection</button>
        )}
        {mode === 'manual' && (
          <div className="stack">
            {routes.map((route, index) => (
              <div key={index} className="stack mock-rule">
                <div className="url-row">
                  <Select
                    className={`method method-${route.method}`}
                    value={route.method}
                    onChange={(method) => updateRoute(index, { method })}
                    options={['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((method) => ({ value: method, label: method }))}
                  />
                  <input value={route.path} placeholder="/path" onChange={(event) => updateRoute(index, { path: event.target.value })} />
                  <input type="number" value={route.status} onChange={(event) => updateRoute(index, { status: Number(event.target.value) })} />
                  <button type="button" className="icon" onClick={() => setRoutes((current) => current.filter((_, i) => i !== index))}>×</button>
                </div>
                <input value={route.query} placeholder="query=match" onChange={(event) => updateRoute(index, { query: event.target.value })} />
                <input value={route.bodyMatch} placeholder="Body must contain" onChange={(event) => updateRoute(index, { bodyMatch: event.target.value })} />
                <input value={route.contentType} onChange={(event) => updateRoute(index, { contentType: event.target.value })} />
                <textarea className="body" value={route.body} onChange={(event) => updateRoute(index, { body: event.target.value })} />
              </div>
            ))}
            <button type="button" className="secondary" onClick={() => setRoutes((current) => [...current, emptyRoute()])}>Add route</button>
          </div>
        )}
        {error && <p className="call-error">{error}</p>}
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onClose}>Cancel</button>
          <button type="button" className="send" disabled={busy} onClick={start}>{busy ? 'Starting…' : 'Start mock'}</button>
        </div>
      </div>
    </div>
  )
}
