import { useState } from 'react'
import { api } from '../api.js'

const STEPS = ['Organize', 'Scripts', 'Import']

export function ImportWizard({ onClose, onImported }) {
  const [step, setStep] = useState(0)
  const [content, setContent] = useState('')
  const [organized, setOrganized] = useState('')
  const [summary, setSummary] = useState(null)
  const [scripts, setScripts] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function loadFile(file) {
    setError('')
    const text = await file.text()
    setContent(text)
    setStep(0)
  }

  async function organize() {
    if (!content.trim()) {
      setError('Paste or upload a Postman collection export first.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const result = await api.organizePostman(content)
      setOrganized(result.content || content)
      setSummary(result)
      setStep(1)
      const preview = await api.previewPostmanScripts(result.content || content)
      setScripts(preview || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function finishImport() {
    setBusy(true)
    setError('')
    try {
      const payload = organized || content
      const result = await api.importDocument(payload)
      onImported?.(result.workspace)
      onClose?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-back" onClick={onClose}>
      <div className="modal-card stack import-wizard" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
        <div className="page-header-row">
          <div>
            <h3>Postman migration wizard</h3>
            <p className="muted">Organize export → preview script translation → import into ApiFlow.</p>
          </div>
          <button type="button" className="icon" onClick={onClose} aria-label="Close">×</button>
        </div>

        <div className="wizard-steps">
          {STEPS.map((label, index) => (
            <span key={label} className={index === step ? 'wizard-step active' : index < step ? 'wizard-step done' : 'wizard-step'}>
              {index + 1}. {label}
            </span>
          ))}
        </div>

        {error && <p className="call-error">{error}</p>}

        {step === 0 && (
          <div className="stack">
            <label className="stack">
              Postman collection JSON
              <textarea className="body" value={content} onChange={(event) => setContent(event.target.value)} placeholder="Paste Postman v2.1 export…" spellCheck={false} />
            </label>
            <label className="secondary file-button">
              Upload export
              <input type="file" accept=".json,application/json" hidden onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                if (file) loadFile(file)
              }} />
            </label>
            <div className="row">
              <button type="button" className="send" disabled={busy || !content.trim()} onClick={organize}>{busy ? 'Organizing…' : 'Organize export'}</button>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="stack">
            {summary && (
              <p className="muted">
                {summary.name}: {summary.requests} requests, {summary.folders} folders, {summary.scripts} scripts found.
              </p>
            )}
            <details open>
              <summary>Cleaned export preview</summary>
              <pre className="markup">{organized.slice(0, 4000)}{organized.length > 4000 ? '\n…' : ''}</pre>
            </details>
            <div className="stack">
              <strong>Script translator preview</strong>
              {scripts.length === 0 && <p className="muted">No Postman scripts in this export.</p>}
              {scripts.map((row) => (
                <div key={row.name} className="section-card stack">
                  <strong>{row.name}</strong>
                  {row.preRequest && (
                    <>
                      <span className="muted">Pre-request (translated)</span>
                      <pre className="markup">{row.translatedPre || row.preRequest}</pre>
                    </>
                  )}
                  {row.postResponse && (
                    <>
                      <span className="muted">Tests (translated)</span>
                      <pre className="markup">{row.translatedPost || row.postResponse}</pre>
                    </>
                  )}
                </div>
              ))}
            </div>
            <div className="row">
              <button type="button" className="secondary" onClick={() => setStep(0)}>Back</button>
              <button type="button" className="send" onClick={() => setStep(2)}>Continue</button>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="stack">
            <p className="muted">Import the organized collection with translated scripts into your workspace.</p>
            <div className="row">
              <button type="button" className="secondary" onClick={() => setStep(1)}>Back</button>
              <button type="button" className="send" disabled={busy} onClick={finishImport}>{busy ? 'Importing…' : 'Import collection'}</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
