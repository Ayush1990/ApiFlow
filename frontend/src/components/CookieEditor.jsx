import { useState } from 'react'

export function CookieEditor({ open, onCancel, onSave }) {
  const [cookie, setCookie] = useState({ domain: 'localhost', name: '', value: '', path: '/' })
  if (!open) return null
  return (
    <div className="modal-back" onClick={onCancel}>
      <form className="modal" onClick={(event) => event.stopPropagation()} onSubmit={(event) => { event.preventDefault(); onSave(cookie) }}>
        <h2>Add cookie</h2>
        <label>Domain<input value={cookie.domain} onChange={(event) => setCookie((current) => ({ ...current, domain: event.target.value }))} /></label>
        <label>Name<input value={cookie.name} onChange={(event) => setCookie((current) => ({ ...current, name: event.target.value }))} /></label>
        <label>Value<input value={cookie.value} onChange={(event) => setCookie((current) => ({ ...current, value: event.target.value }))} /></label>
        <label>Path<input value={cookie.path} onChange={(event) => setCookie((current) => ({ ...current, path: event.target.value }))} /></label>
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
          <button type="submit" className="send">Save</button>
        </div>
      </form>
    </div>
  )
}
