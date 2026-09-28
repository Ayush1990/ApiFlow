import { api } from '../api.js'
import { AuthFields } from './AuthFields.jsx'
import { CertificateManager } from './CertificateManager.jsx'
import { DocsPanel } from './DocsPanel.jsx'
import { KeyValueTable } from './KeyValueTable.jsx'
import { OpenApiPanel } from './OpenApiPanel.jsx'

function Section({ title, desc, children }) {
  return (
    <section className="section-card">
      <h3 className="section-title">{title}</h3>
      {desc && <p className="section-desc muted">{desc}</p>}
      {children}
    </section>
  )
}

function TypedFieldsEditor({ label, rows, onChange }) {
  return (
    <div className="stack">
      <strong>{label}</strong>
      {(rows || []).map((row, index) => (
        <div key={index} className="url-row">
          <input value={row.key || ''} placeholder="key" onChange={(event) => onChange(rows.map((item, i) => i === index ? { ...item, key: event.target.value } : item))} />
          <input value={row.dataType || 'string'} placeholder="type" onChange={(event) => onChange(rows.map((item, i) => i === index ? { ...item, dataType: event.target.value } : item))} />
          <input value={row.enumValues || ''} placeholder="enum a,b,c" onChange={(event) => onChange(rows.map((item, i) => i === index ? { ...item, enumValues: event.target.value } : item))} />
          <label><input type="checkbox" checked={!!row.required} onChange={(event) => onChange(rows.map((item, i) => i === index ? { ...item, required: event.target.checked } : item))} /> required</label>
          <button type="button" className="icon" onClick={() => onChange(rows.filter((_, i) => i !== index))}>×</button>
        </div>
      ))}
      <button type="button" className="text-button" onClick={() => onChange([...(rows || []), { key: '', dataType: 'string', required: false }])}>+ Add field</button>
    </div>
  )
}

function MockScenariosEditor({ scenarios, onChange }) {
  return (
    <div className="stack">
      {(scenarios || []).map((scenario, index) => (
        <div key={scenario.id || index} className="section-card stack">
          <input value={scenario.name || ''} placeholder="Scenario name" onChange={(event) => onChange(scenarios.map((item, i) => i === index ? { ...item, name: event.target.value } : item))} />
          <div className="url-row">
            <input value={scenario.method || 'GET'} onChange={(event) => onChange(scenarios.map((item, i) => i === index ? { ...item, method: event.target.value } : item))} />
            <input value={scenario.path || ''} placeholder="/api/users" onChange={(event) => onChange(scenarios.map((item, i) => i === index ? { ...item, path: event.target.value } : item))} />
          </div>
          <textarea className="body body-compact" value={JSON.stringify(scenario.steps || [], null, 2)} onChange={(event) => {
            try { onChange(scenarios.map((item, i) => i === index ? { ...item, steps: JSON.parse(event.target.value) } : item)) } catch { /* ignore */ }
          }} spellCheck={false} />
        </div>
      ))}
      <button type="button" className="text-button" onClick={() => onChange([...(scenarios || []), { id: crypto.randomUUID(), name: 'Login flow', method: 'POST', path: '/api/login', steps: [{ status: 401, body: '{"error":"invalid"}' }, { status: 200, body: '{"token":"abc"}' }] }])}>+ Add scenario chain</button>
    </div>
  )
}

export function CollectionEditor({ draft, onChange, onSave, saving, onRefresh }) {
  const defaults = draft.defaults || {}
  function patch(next) {
    onChange({ defaults: { ...defaults, ...next } })
  }

  return (
    <div className="env-editor">
      <div className="page-header">
        <div className="page-header-row">
          <input className="request-title" value={draft.name || ''} onChange={(event) => onChange({ name: event.target.value })} aria-label="Collection name" />
          <button type="button" className="send" onClick={onSave} disabled={saving}>{saving ? 'Saving…' : 'Save collection'}</button>
        </div>
      </div>
      <div className="page-body stack">
        <Section title="Collection variables" desc={<>Available as <code>{'{{name}}'}</code>. Environment variables win on name clashes.</>}>
          <KeyValueTable rows={draft.variables} onChange={(variables) => onChange({ variables })} keyPlaceholder="name" valuePlaceholder="value" secrets />
        </Section>
        <Section title="Default headers" desc="Sent with every request unless overridden at request level.">
          <KeyValueTable rows={defaults.headers} onChange={(headers) => patch({ headers })} />
        </Section>
        <Section title="Default auth" desc="Used when a request auth type is Inherit and no folder sets auth. Includes OAuth 2.0 sign-in.">
          <AuthFields
            draft={defaults}
            onChange={patch}
            wide
            onOAuthSignIn={async () => {
              const extras = defaults.extras || {}
              const started = await api.oauthStart({
                authUrl: extras.oauthAuthUrl || '',
                tokenUrl: extras.oauthTokenUrl || '',
                clientId: extras.oauthClientId || '',
                clientSecret: extras.oauthClientSecret || '',
                scope: extras.oauthScope || '',
              })
              window.open(started.url, 'apiflow-oauth', 'width=520,height=720')
            }}
            onOAuthSystemBrowser={async () => {
              const extras = defaults.extras || {}
              const started = await api.oauthStart({
                authUrl: extras.oauthAuthUrl || '',
                tokenUrl: extras.oauthTokenUrl || '',
                clientId: extras.oauthClientId || '',
                clientSecret: extras.oauthClientSecret || '',
                scope: extras.oauthScope || '',
              })
              window.open(started.url, '_blank')
            }}
            onOAuthDevice={async () => {
              const extras = defaults.extras || {}
              const started = await api.oauthDeviceStart({
                deviceUrl: extras.oauthDeviceUrl || '',
                clientId: extras.oauthClientId || '',
                scope: extras.oauthScope || '',
              })
              window.open(started.verificationUri, 'apiflow-device', 'width=720,height=820')
              patch({ extras: { ...extras, oauthDeviceCode: started.deviceCode, oauthUserCode: started.userCode } })
              window.alert(`Use code ${started.userCode}, finish sign-in in the browser, then click Complete device sign-in.`)
            }}
            onOAuthDeviceComplete={async () => {
              const extras = defaults.extras || {}
              const token = await api.oauthDevicePoll({
                tokenUrl: extras.oauthTokenUrl || '',
                clientId: extras.oauthClientId || '',
                clientSecret: extras.oauthClientSecret || '',
                deviceCode: extras.oauthDeviceCode || '',
              })
              patch({ extras: { ...extras, oauthAccessToken: token.access, oauthRefreshToken: token.refresh, oauthExpiresAt: token.expiresAt } })
            }}
          />
        </Section>
        <Section title="Client certificates" desc="Collection-level mTLS defaults for all requests.">
          <CertificateManager draft={draft} onChange={onChange} level="collection" />
        </Section>
        <Section title="HTML documentation" desc="Auto-generate, export static zip for cloud hosting, or deploy interactive docs locally.">
          <DocsPanel collection={draft} onChange={onChange} onRefresh={onRefresh} />
        </Section>
        <Section title="Collection apps" desc="Attach Bruno-style apps scoped to this collection.">
          <p className="muted">Open the Apps page with this collection pre-selected to create runners or HTML dashboards.</p>
          <button type="button" className="secondary" onClick={() => window.dispatchEvent(new CustomEvent('apiflow-open-apps', { detail: { collection: draft.name } }))}>Manage collection apps</button>
        </Section>
        <Section title="Collection scripts" desc="Run before/after every request in this collection.">
          <div className="script-grid">
            <label className="stack">Pre-request<textarea className="body body-compact" value={draft.preRequestScript || ''} onChange={(event) => onChange({ preRequestScript: event.target.value })} placeholder="// JavaScript" /></label>
            <label className="stack">Post-response<textarea className="body body-compact" value={draft.postResponseScript || ''} onChange={(event) => onChange({ postResponseScript: event.target.value })} placeholder="// JavaScript" /></label>
          </div>
        </Section>
        <Section title="HTTP collection types" desc="Validate params, headers, and body against typed schemas (Postman-style).">
          <TypedFieldsEditor label="Typed query params" rows={draft.typedParams || []} onChange={(typedParams) => onChange({ typedParams })} />
          <TypedFieldsEditor label="Typed headers" rows={draft.typedHeaders || []} onChange={(typedHeaders) => onChange({ typedHeaders })} />
          <label className="stack">Body schema (JSON)<textarea className="body body-compact" value={draft.bodySchema || ''} onChange={(event) => onChange({ bodySchema: event.target.value })} placeholder='{"type":"object"}' /></label>
        </Section>
        <Section title="Mock scenario chains" desc="Return different responses on repeated calls to the same path.">
          <MockScenariosEditor scenarios={draft.mockScenarios || []} onChange={(mockScenarios) => onChange({ mockScenarios })} />
          <label className="stack">
            JavaScript mock handler
            <textarea className="body body-compact" value={draft.mockScript || ''} spellCheck={false} onChange={(event) => onChange({ mockScript: event.target.value })} placeholder={'mock.status = 201\nmock.body = JSON.stringify({ ok: true, path: mock.path })'} />
            <span className="muted">Runs after route matching. Mutate mock.status, mock.body, and mock.contentType. requestBody, method, and path are available.</span>
          </label>
        </Section>
        <section className="section-card">
          <OpenApiPanel collection={draft} />
        </section>
      </div>
    </div>
  )
}
