import { useEffect, useState } from 'react'
import { api } from '../api.js'

export function MockLogPanel({ open, onClose }) {
  const [entries, setEntries] = useState([])
  useEffect(() => {
    if (!open) return undefined
    let stop = false
    async function poll() {
      while (!stop) {
        try {
          setEntries(await api.mockLog())
        } catch {
          setEntries([])
        }
        await new Promise((resolve) => setTimeout(resolve, 1500))
      }
    }
    poll()
    return () => { stop = true }
  }, [open])
  if (!open) return null
  return (
    <div className="modal-back" onClick={onClose}>
      <div className="modal wide" onClick={(event) => event.stopPropagation()}>
        <h2>Mock server log</h2>
        <div className="mock-log">
          {entries.length === 0 && <p className="muted">No requests yet.</p>}
          {entries.slice().reverse().map((entry, index) => (
            <div key={index} className={`history-row ${entry.matched ? 'ok' : 'err'}`}>
              <span className={`verb verb-${entry.method}`}>{entry.method}</span>
              <span className="request-name">{entry.path}</span>
              <span>{entry.matched ? 'Matched' : 'Miss'}</span>
              {entry.bodyPreview && <span className="muted">{entry.bodyPreview}</span>}
            </div>
          ))}
        </div>
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
