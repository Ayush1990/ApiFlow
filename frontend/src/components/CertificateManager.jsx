export function CertificateManager({ draft, onChange, level = 'collection' }) {
  const defaults = draft.defaults || {}
  const extras = draft.extras || {}

  function patchDefaults(next) {
    onChange({ defaults: { ...defaults, extras: { ...(defaults.extras || {}), ...next } } })
  }

  function patchExtras(next) {
    onChange({ extras: { ...extras, ...next } })
  }

  const isCollection = level === 'collection'

  return (
    <div className="stack cert-manager">
      <p className="muted">
        {isCollection
          ? 'Default mTLS certificate for requests in this collection unless overridden per request.'
          : 'Request-level client certificate (PEM base64).'}
      </p>
      {isCollection ? (
        <div className="settings-grid">
          <label className="field span-2 stack">
            Client cert (PEM base64)
            <textarea className="body body-compact" value={defaults.extras?.clientCertBase64 || ''} onChange={(event) => patchDefaults({ clientCertBase64: event.target.value })} />
          </label>
          <label className="field span-2 stack">
            Client key (PEM base64)
            <textarea className="body body-compact" value={defaults.extras?.clientKeyBase64 || ''} onChange={(event) => patchDefaults({ clientKeyBase64: event.target.value })} />
          </label>
          <label className="field">
            Cert password
            <input type="password" value={defaults.extras?.clientCertPassword || ''} onChange={(event) => patchDefaults({ clientCertPassword: event.target.value })} />
          </label>
        </div>
      ) : (
        <div className="settings-grid">
          <label className="field span-2 stack">
            Client cert (PEM base64)
            <textarea className="body body-compact" value={extras.clientCertBase64 || ''} onChange={(event) => patchExtras({ clientCertBase64: event.target.value })} />
          </label>
          <label className="field span-2 stack">
            Client key (PEM base64)
            <textarea className="body body-compact" value={extras.clientKeyBase64 || ''} onChange={(event) => patchExtras({ clientKeyBase64: event.target.value })} />
          </label>
          <label className="field">
            Cert password
            <input type="password" value={extras.clientCertPassword || ''} onChange={(event) => patchExtras({ clientCertPassword: event.target.value })} />
          </label>
        </div>
      )}
    </div>
  )
}
