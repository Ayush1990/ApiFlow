import { KeyValueTable } from './KeyValueTable.jsx'

export function EnvironmentEditor({ draft, onChange, onSave, onDelete, saving }) {
  return (
    <div className="env-editor">
      <div className="page-header">
        <div className="env-toolbar">
          <input
            className="request-title"
            value={draft.name || ''}
            onChange={(event) => onChange({ name: event.target.value })}
            aria-label="Environment name"
          />
          <button type="button" className="secondary" onClick={onSave} disabled={saving}>
            {saving ? 'Saving' : 'Save'}
          </button>
          <button type="button" className="danger-button" onClick={onDelete}>
            Delete
          </button>
        </div>
      </div>
      <div className="page-body">
      <p className="muted">
        Use these names in a request as <code>{'{{name}}'}</code>. The environment selected in the top bar is applied when you send. Mark a value secret to hide it in this editor and keep it out of history.
      </p>
      <label className="check-line">
        <input type="checkbox" checked={draft.global !== false} onChange={(event) => onChange({ global: event.target.checked })} />
        Global environment (workspace-wide, Bruno <code>--global-env</code>)
      </label>
      <h3>External secrets</h3>
      <p className="muted">Bruno v3 <code>externalSecrets</code> references (key → secret manager path).</p>
      <KeyValueTable
        rows={Object.entries(draft.externalSecrets || {}).map(([key, value]) => ({ key, value, enabled: true }))}
        onChange={(rows) => onChange({ externalSecrets: Object.fromEntries(rows.filter((row) => row.key).map((row) => [row.key, row.value || ''])) })}
        keyPlaceholder="variable"
        valuePlaceholder="secret path"
      />
      <h3>Variables</h3>
      <KeyValueTable
        rows={draft.variables}
        onChange={(variables) => onChange({ variables })}
        keyPlaceholder="name"
        valuePlaceholder="value"
        secrets
      />
      </div>
    </div>
  )
}
