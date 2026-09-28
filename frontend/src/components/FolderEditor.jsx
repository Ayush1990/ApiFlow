import { AuthFields } from './AuthFields.jsx'
import { KeyValueTable } from './KeyValueTable.jsx'

function Section({ title, desc, children }) {
  return (
    <section className="section-card">
      <h3 className="section-title">{title}</h3>
      {desc && <p className="section-desc muted">{desc}</p>}
      {children}
    </section>
  )
}

export function FolderEditor({ draft, onChange, onSave, saving }) {
  const defaults = draft.defaults || {}
  function patch(next) {
    onChange({ defaults: { ...defaults, ...next } })
  }
  return (
    <div className="env-editor">
      <div className="page-header">
        <div className="page-header-row">
          <input className="request-title" value={draft.name || ''} onChange={(event) => onChange({ name: event.target.value })} aria-label="Folder name" />
          <button type="button" className="send" onClick={onSave} disabled={saving}>{saving ? 'Saving…' : 'Save folder'}</button>
        </div>
      </div>
      <div className="page-body stack">
        <Section title="Folder headers" desc="Added to every request in this folder unless the request overrides them.">
          <KeyValueTable rows={defaults.headers} onChange={(headers) => patch({ headers })} />
        </Section>
        <Section title="Folder auth" desc="Overrides collection auth for requests in this folder.">
          <AuthFields draft={defaults} onChange={patch} wide />
        </Section>
        <Section title="Folder variables" desc={<>Available as <code>{'{{name}}'}</code> for requests in this folder.</>}>
          <KeyValueTable rows={draft.variables} onChange={(variables) => onChange({ variables })} secrets />
        </Section>
        <Section title="Folder docs" desc="Documentation notes for this folder.">
          <textarea className="body body-compact" value={draft.docs || ''} onChange={(event) => onChange({ docs: event.target.value })} />
        </Section>
        <Section title="Folder scripts" desc="Run before/after requests in this folder.">
          <div className="script-grid">
            <label className="stack">Pre-request<textarea className="body body-compact" value={draft.preRequestScript || ''} onChange={(event) => onChange({ preRequestScript: event.target.value })} /></label>
            <label className="stack">Post-response<textarea className="body body-compact" value={draft.postResponseScript || ''} onChange={(event) => onChange({ postResponseScript: event.target.value })} /></label>
          </div>
        </Section>
      </div>
    </div>
  )
}
