import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Select } from './Select.jsx'

export function SecretMigrationWizard({ workspace, onMigrated }) {
  const [preview, setPreview] = useState(null)
  const [environmentId, setEnvironmentId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  useEffect(() => {
    api.secretMigrationPreview()
      .then((data) => {
        setPreview(data)
        setEnvironmentId(workspace.environments?.[0]?.id || '')
      })
      .catch((err) => setError(err.message))
  }, [workspace.environments])

  async function migrate() {
    setBusy(true)
    setError('')
    try {
      const result = await api.migrateSecrets(environmentId)
      onMigrated?.(result.workspace)
      setDone(true)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!preview) return <p className="muted">Checking for secrets.json…</p>

  return (
    <div className="stack secret-migration">
      {error && <p className="call-error">{error}</p>}
      {!preview.found && <p className="muted">No <code>secrets.json</code> found in the data root.</p>}
      {preview.found && (
        <>
          <p className="muted">
            Found <strong>{preview.count}</strong> secret reference(s) in <code>{preview.path}</code>.
          </p>
          <label className="field">
            Target environment
            <Select
              value={environmentId}
              onChange={setEnvironmentId}
              options={(workspace.environments || []).map((env) => ({ value: env.id, label: env.name }))}
            />
          </label>
          <button type="button" className="send" disabled={busy || done || !environmentId} onClick={migrate}>
            {done ? 'Migrated' : busy ? 'Migrating…' : 'Migrate to environment'}
          </button>
        </>
      )}
    </div>
  )
}
