import { useState } from 'react'

export function PromptDialog({ names, onCancel, onSubmit }) {
  const [values, setValues] = useState(Object.fromEntries((names || []).map((name) => [name, ''])))
  return (
    <div className="modal-back" onClick={onCancel}>
      <form className="modal" onClick={(event) => event.stopPropagation()} onSubmit={(event) => { event.preventDefault(); onSubmit(values) }}>
        <h2>Enter variable values</h2>
        <p className="muted">These variables are used in the request but are not defined yet.</p>
        {(names || []).map((name) => (
          <label key={name}>
            {name}
            <input value={values[name] || ''} onChange={(event) => setValues((current) => ({ ...current, [name]: event.target.value }))} />
          </label>
        ))}
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
          <button type="submit" className="send">Send</button>
        </div>
      </form>
    </div>
  )
}
