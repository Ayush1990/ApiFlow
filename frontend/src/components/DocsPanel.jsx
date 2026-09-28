import { useState } from 'react'
import { api } from '../api.js'

export function DocsPanel({ collection, onChange, onRefresh }) {
  const [busy, setBusy] = useState('')
  const [deployUrl, setDeployUrl] = useState('')
  const [embedHtml, setEmbedHtml] = useState('')
  const [error, setError] = useState('')

  async function autoGenerate() {
    setBusy('generate')
    setError('')
    try {
      const result = await api.generateCollectionDocs(collection.id)
      onRefresh?.(result.workspace)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function deploy() {
    setBusy('deploy')
    setError('')
    try {
      const result = await api.deployCollectionDocs(collection.id)
      const full = `http://localhost:8080${result.url}`
      setDeployUrl(full)
      window.open(full, '_blank')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function downloadHtml() {
    setBusy('download')
    setError('')
    try {
      const html = await api.collectionDocsHtml(collection.id)
      const blob = new Blob([html], { type: 'text/html' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `${collection.name || 'collection'}-docs.html`
      link.click()
      URL.revokeObjectURL(link.href)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function publishPublic() {
    setBusy('publish')
    setError('')
    try {
      const result = await api.publishDocs(collection.id)
      const full = `http://localhost:8080${result.publicUrl}`
      setDeployUrl(full)
      setEmbedHtml(result.embedHtml || '')
      window.open(full, '_blank')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function copyEmbed() {
    setBusy('embed')
    setError('')
    try {
      const result = await api.embedDocs(collection.id)
      setEmbedHtml(result.html || '')
      await navigator.clipboard.writeText(result.html || '')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  async function exportBundle() {
    setBusy('bundle')
    setError('')
    try {
      await api.exportDocsBundle(collection.id, `${collection.name || 'collection'}-docs.zip`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  return (
    <div className="stack">
      <p className="muted">Auto-generate docs, publish with try-it proxy, embed a Run in ApiFlow button, export a static zip, or deploy locally.</p>
      {error && <p className="call-error">{error}</p>}
      <textarea
        className="body body-compact"
        value={collection.docs || ''}
        onChange={(event) => onChange({ docs: event.target.value })}
        placeholder="Collection documentation (auto-generated if empty on deploy)"
      />
      <div className="row">
        <button type="button" className="secondary" disabled={!!busy} onClick={autoGenerate}>{busy === 'generate' ? 'Generating…' : 'Auto-generate'}</button>
        <button type="button" className="secondary" disabled={!!busy} onClick={downloadHtml}>{busy === 'download' ? 'Exporting…' : 'Download HTML'}</button>
        <button type="button" className="secondary" disabled={!!busy} onClick={exportBundle}>{busy === 'bundle' ? 'Exporting…' : 'Export static zip'}</button>
        <button type="button" className="send" disabled={!!busy} onClick={deploy}>{busy === 'deploy' ? 'Deploying…' : 'Deploy locally'}</button>
        <button type="button" className="secondary" disabled={!!busy} onClick={publishPublic}>{busy === 'publish' ? 'Publishing…' : 'Publish public docs'}</button>
        <button type="button" className="secondary" disabled={!!busy} onClick={copyEmbed}>{busy === 'embed' ? 'Copying…' : 'Copy embed button'}</button>
      </div>
      {embedHtml && <textarea className="body body-compact" readOnly value={embedHtml} />}
      {deployUrl && (
        <p className="muted">
          Deployed at <a href={deployUrl} target="_blank" rel="noreferrer">{deployUrl}</a>
        </p>
      )}
    </div>
  )
}
