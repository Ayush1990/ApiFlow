import { api } from '../api.js'

const ENVELOPE = `<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header></soap:Header>
  <soap:Body>
    <!-- operation payload -->
  </soap:Body>
</soap:Envelope>`

export function SoapPanel({ draft, onChange }) {
  async function importWsdl() {
    const url = window.prompt('WSDL URL or paste WSDL XML')
    if (!url) return
    try {
      const result = await api.importDocument(url.startsWith('http') ? url : url)
      const collection = result.workspace?.collections?.[0]
      const request = collection?.requests?.[0]
      if (!request) {
        window.alert('No SOAP operations found in WSDL')
        return
      }
      onChange({
        method: 'SOAP',
        bodyType: 'soap',
        url: request.url || draft.url,
        body: request.body || ENVELOPE,
        extras: { ...(draft.extras || {}), soapAction: request.extras?.soapAction || '' },
      })
    } catch (err) {
      window.alert(err.message)
    }
  }

  return (
    <div className="stack body-panel">
      <div className="panel-callout">
        <p>SOAP requests are sent as POST with <code>text/xml</code>. Set SOAPAction when the service requires it.</p>
        <div className="panel-actions">
          <button type="button" className="secondary" onClick={() => onChange({ body: ENVELOPE, bodyType: 'soap' })}>Insert envelope</button>
          <button type="button" className="secondary" onClick={importWsdl}>Import WSDL</button>
        </div>
      </div>
      <label className="field-input">
        SOAPAction
        <input value={draft.extras?.soapAction || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), soapAction: event.target.value } })} placeholder="urn:example#Operation" />
      </label>
      <label className="stack field-input">
        SOAP body (XML)
        <textarea className="body" spellCheck={false} value={draft.body || ''} onChange={(event) => onChange({ body: event.target.value, bodyType: 'soap' })} />
      </label>
    </div>
  )
}
