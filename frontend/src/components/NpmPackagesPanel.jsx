import { useEffect, useState } from 'react'
import { api } from '../api.js'

export function NpmPackagesPanel() {
  const [status, setStatus] = useState({ packages: [], packageJson: '{}' })
  const [packageName, setPackageName] = useState('')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')

  async function refresh() {
    try {
      const data = await api.npmStatus()
      setStatus({ packages: data.packages || [], packageJson: data.output || '{}' })
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { refresh() }, [])

  async function install() {
    setBusy('install')
    setError('')
    try {
      const data = await api.npmInstall()
      setStatus({ packages: data.packages || [], packageJson: data.output || status.packageJson })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function addPackage() {
    if (!packageName.trim()) return
    setBusy('add')
    setError('')
    try {
      const data = await api.npmAdd(packageName.trim())
      setStatus({ packages: data.packages || [], packageJson: data.output || status.packageJson })
      setPackageName('')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function savePackageJson(content) {
    setBusy('save')
    setError('')
    try {
      await api.npmSavePackageJson(content)
      await refresh()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  return (
    <div className="stack npm-panel">
      {error && <p className="call-error">{error}</p>}
      <p className="muted">Manage script dependencies for <code>require('package')</code> in pre/post scripts.</p>
      <div className="row">
        <input value={packageName} onChange={(event) => setPackageName(event.target.value)} placeholder="lodash" aria-label="Package name" />
        <button type="button" className="secondary" disabled={!!busy} onClick={addPackage}>{busy === 'add' ? 'Adding…' : 'Add package'}</button>
        <button type="button" className="send" disabled={!!busy} onClick={install}>{busy === 'install' ? 'Installing…' : 'npm install'}</button>
      </div>
      <div className="stack">
        <strong>Installed packages</strong>
        {(status.packages || []).length === 0 && <p className="muted">No packages installed yet.</p>}
        <ul className="npm-list">
          {(status.packages || []).map((pkg) => <li key={pkg}><code>{pkg}</code></li>)}
        </ul>
      </div>
      <label className="stack">
        package.json
        <textarea
          className="body body-compact"
          value={status.packageJson || '{}'}
          spellCheck={false}
          onChange={(event) => setStatus((current) => ({ ...current, packageJson: event.target.value }))}
        />
      </label>
      <button type="button" className="secondary" disabled={!!busy} onClick={() => savePackageJson(status.packageJson || '{}')}>
        {busy === 'save' ? 'Saving…' : 'Save package.json'}
      </button>
    </div>
  )
}
