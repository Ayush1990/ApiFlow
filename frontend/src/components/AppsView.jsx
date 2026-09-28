import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { AppSandbox } from './AppSandbox.jsx'
import { Select } from './Select.jsx'

function Section({ title, desc, children, actions }) {
  return (
    <section className="section-card">
      <div className="section-head">
        <div>
          <h3 className="section-title">{title}</h3>
          {desc && <p className="section-desc muted">{desc}</p>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  )
}

function Field({ label, wide, children }) {
  return (
    <label className={wide ? 'field span-2' : 'field'}>
      {label}
      {children}
    </label>
  )
}

export function AppsView({ workspace, onRefresh, environmentId, scopeCollection, scopeRequest }) {
  const [apps, setApps] = useState([])
  const [name, setName] = useState('')
  const [collection, setCollection] = useState(scopeCollection || '')
  const [requestName, setRequestName] = useState(scopeRequest || '')
  const [scope, setScope] = useState(scopeCollection ? 'collection' : 'workspace')
  const [uiType, setUiType] = useState('collection')
  const [htmlCode, setHtmlCode] = useState('<button onclick="bru.ctx.runRequest(\'Health\')">Run health</button>')
  const [aiPrompt, setAiPrompt] = useState('Dashboard with buttons to run smoke and health requests')
  const [selectedId, setSelectedId] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function load() {
    setError('')
    try {
      const list = await api.listApps()
      setApps(list)
      if (!selectedId && list.length) setSelectedId(list[0].id)
      else if (selectedId && !list.some((app) => app.id === selectedId)) setSelectedId(list[0]?.id || '')
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])
  useEffect(() => {
    if (scopeCollection) {
      setCollection(scopeCollection)
      setScope('collection')
    }
    if (scopeRequest) {
      setRequestName(scopeRequest)
      setScope('request')
    }
  }, [scopeCollection, scopeRequest])

  async function saveApp() {
    setBusy(true)
    setError('')
    try {
      await api.saveApp({
        name: name.trim(),
        collection: collection.trim(),
        request: requestName.trim(),
        scope,
        triggerType: uiType === 'html' ? 'html' : (scope === 'request' ? 'request' : 'collection'),
        uiType: scope === 'request' ? 'request' : uiType,
        htmlCode,
        icon: uiType === 'html' ? '🎨' : '⚡',
      })
      setName('')
      setCollection('')
      await load()
      onRefresh?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function runApp(app) {
    setBusy(true)
    setError('')
    try {
      const report = await api.runApp(app.id, environmentId)
      window.alert(`Passed ${report.passed}/${report.total || report.passed + report.failed}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function deleteApp(app) {
    if (!window.confirm(`Delete app "${app.name}"?`)) return
    setBusy(true)
    setError('')
    try {
      await api.deleteApp(app.id)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function generateWithAi() {
    setBusy(true)
    setError('')
    try {
      const context = JSON.stringify({
        collection,
        request: requestName,
        requests: (workspace?.collections || []).find((item) => item.name === collection || item.id === collection)?.requests?.map((item) => item.name) || [],
      }, null, 2)
      const result = await api.generateApp(aiPrompt, context)
      setHtmlCode(result.script || '')
      setUiType('html')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const scopedApps = apps.filter((app) => {
    if (scopeCollection && app.collection && app.collection !== scopeCollection) return false
    if (scopeRequest && app.request && app.request !== scopeRequest) return false
    return true
  })
  const selected = scopedApps.find((app) => app.id === selectedId) || scopedApps[0]
  const canSave = name.trim() && collection && (scope !== 'request' || requestName.trim())

  return (
    <div className="env-editor apps-view">
      <div className="page-header">
        <div className="page-header-row">
          <div>
            <h2>Bruno Apps</h2>
            <p className="muted">One-click collection runners and custom HTML dashboards with <code>bru.ctx</code>.</p>
          </div>
        </div>
      </div>

      <div className="page-body stack">
        {error && (
          <section className="section-card ai-error-card">
            <p className="call-error">{error}</p>
          </section>
        )}

        <Section
          title="Create app"
          desc={<>Collection apps run every request in a collection. HTML apps use <code>bru.ctx.submitRequest()</code> and <code>bru.ctx.runRequest(name)</code> in a sandbox.</>}
        >
          <div className="settings-grid">
            <Field label="App name">
              <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Smoke test" />
            </Field>
            <Field label="Scope">
              <Select
                value={scope}
                onChange={setScope}
                options={[
                  { value: 'workspace', label: 'Workspace' },
                  { value: 'collection', label: 'Collection' },
                  { value: 'request', label: 'Single request' },
                ]}
              />
            </Field>
            <Field label="Collection">
              <Select
                value={collection}
                onChange={setCollection}
                options={[
                  { value: '', label: 'Select collection' },
                  ...(workspace?.collections || []).map((item) => ({ value: item.name, label: item.name })),
                ]}
              />
            </Field>
            {scope === 'request' && (
              <Field label="Request">
                <Select
                  value={requestName}
                  onChange={setRequestName}
                  options={[
                    { value: '', label: 'Select request' },
                    ...((workspace?.collections || []).find((item) => item.name === collection)?.requests || []).map((item) => ({ value: item.name, label: item.name })),
                  ]}
                />
              </Field>
            )}
            {scope !== 'request' && (
              <Field label="UI type">
                <Select
                  value={uiType}
                  onChange={setUiType}
                  options={[
                    { value: 'collection', label: 'Collection runner' },
                    { value: 'html', label: 'Custom HTML (bru.ctx)' },
                  ]}
                />
              </Field>
            )}
          </div>
          {uiType === 'html' && (
            <>
              <Field label="Describe app (AI)" wide>
                <textarea className="body body-compact" value={aiPrompt} onChange={(event) => setAiPrompt(event.target.value)} placeholder="Dashboard with run buttons for each request" />
              </Field>
              <div className="row">
                <button type="button" className="secondary" disabled={busy} onClick={generateWithAi}>{busy ? 'Generating…' : 'Generate with AI'}</button>
              </div>
              <Field label="HTML" wide>
                <textarea className="body body-compact" value={htmlCode} onChange={(event) => setHtmlCode(event.target.value)} spellCheck={false} placeholder="<button onclick=&quot;bru.ctx.runRequest('Health')&quot;>Run</button>" />
              </Field>
            </>
          )}
          <div className="row apps-create-actions">
            <button type="button" className="send" disabled={busy || !canSave} onClick={saveApp}>
              {busy ? 'Saving…' : 'Save app'}
            </button>
          </div>
        </Section>

        <Section
          title="Saved apps"
          desc={scopedApps.length ? `${scopedApps.length} app${scopedApps.length === 1 ? '' : 's'} configured on this machine.` : 'No apps yet — create one above.'}
          actions={scopedApps.length > 0 && (
            <button type="button" className="ghost-button" onClick={load}>Refresh</button>
          )}
        >
          {scopedApps.length === 0 ? (
            <p className="settings-note">Saved apps appear here with quick Run and Delete actions.</p>
          ) : (
            <div className="apps-list">
              {scopedApps.map((app) => {
                const active = selectedId === app.id
                const typeLabel = app.scope === 'request' ? `Request · ${app.request}` : app.uiType === 'html' ? 'HTML dashboard' : 'Collection runner'
                return (
                  <div key={app.id} className={`app-card${active ? ' active' : ''}`}>
                    <button type="button" className="app-card-main" onClick={() => setSelectedId(app.id)}>
                      <span className="app-card-icon" aria-hidden>{app.icon || '⚡'}</span>
                      <span className="app-card-text">
                        <strong>{app.name}</strong>
                        <span className="muted">{typeLabel} · {app.collection}</span>
                      </span>
                    </button>
                    <div className="app-card-actions">
                      <button type="button" className="secondary" disabled={busy} onClick={() => runApp(app)}>Run</button>
                      <button type="button" className="ghost-button danger-text" disabled={busy} onClick={() => deleteApp(app)}>Delete</button>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </Section>

        {selected?.uiType === 'html' && selected.htmlCode && (
          <Section title="Preview" desc={`Live sandbox for "${selected.name}".`}>
            <AppSandbox app={selected} environmentId={environmentId} />
          </Section>
        )}
      </div>
    </div>
  )
}
