import { useRef, useState } from 'react'
import { api } from '../api.js'

export function RunView({ report, name, history = [], comments = [], onComment }) {
  const [comment, setComment] = useState('')
  const [imported, setImported] = useState(null)
  const [debug, setDebug] = useState('')
  const [debugBusy, setDebugBusy] = useState(false)
  const fileRef = useRef(null)
  const shown = imported || report
  if (!shown) return null
  return (
    <div className="run-view">
      <h2>{name}</h2>
      <p className="muted">{shown.passed} passed, {shown.failed} failed{shown.stopped ? '. Stopped after the first failure.' : ''}. Secret values are masked in this report.</p>
      <div className="row">
        <button type="button" className="secondary" onClick={() => {
          const blob = new Blob([JSON.stringify(shown, null, 2)], { type: 'application/json' })
          const link = document.createElement('a')
          link.href = URL.createObjectURL(blob)
          link.download = 'apiflow-run.json'
          link.click()
        }}>Export results</button>
        <button type="button" className="secondary" onClick={() => fileRef.current?.click()}>Import results</button>
        <input ref={fileRef} type="file" accept="application/json" hidden onChange={async (event) => {
          const file = event.target.files?.[0]
          if (!file) return
          setImported(JSON.parse(await file.text()))
        }} />
        <button type="button" className="secondary" disabled={debugBusy || shown.failed === 0} onClick={async () => {
          setDebugBusy(true)
          try {
            const result = await api.debugRun(shown)
            setDebug(result.explanation || '')
          } finally {
            setDebugBusy(false)
          }
        }}>{debugBusy ? 'Debugging…' : 'AI debug'}</button>
      </div>
      {debug && <pre className="markup">{debug}</pre>}
      {shown.shareUrl && <p className="muted">Standalone report: <a href={shown.shareUrl} target="_blank" rel="noreferrer">{shown.shareFile || shown.shareUrl}</a></p>}
      <div className="run-list">
        {(shown.items || []).map((item, index) => (
          <div key={`${item.requestId}-${index}`} className={`run-item ${item.ok ? 'ok' : 'err'}`}>
            <span className={`verb verb-${item.method}`}>{item.method}</span>
            <strong>{item.name}</strong>
            <span>{item.ok ? 'Passed' : 'Failed'}</span>
            <span>{item.status || item.error}</span>
            {(item.checks || []).map((check, checkIndex) => (
              <small key={checkIndex}>{check.passed ? 'ok' : 'fail'} · {check.message}</small>
            ))}
            {item.responseBody && <pre className="markup">{item.responseBody}</pre>}
          </div>
        ))}
      </div>
      {history.length > 0 && (
        <div className="stack">
          <h3>Run history</h3>
          {history.slice(0, 8).map((item) => (
            <div key={item.id} className="card">
              <span>{item.startedAt ? new Date(item.startedAt).toLocaleString() : item.id} · {item.passed} passed · {item.failed} failed</span>
              {item.shareUrl && <a href={item.shareUrl} target="_blank" rel="noreferrer">Open report</a>}
            </div>
          ))}
        </div>
      )}
      <div className="stack">
        <h3>Comments</h3>
        {comments.map((item) => (
          <p key={item.id} className="muted"><strong>{item.author}</strong> · {item.body}</p>
        ))}
        {onComment && (
          <form className="url-row" onSubmit={(event) => { event.preventDefault(); if (!comment.trim()) return; onComment(comment.trim()); setComment('') }}>
            <input value={comment} onChange={(event) => setComment(event.target.value)} placeholder="Comment on this run" />
            <button type="submit" className="secondary">Comment</button>
          </form>
        )}
      </div>
    </div>
  )
}
