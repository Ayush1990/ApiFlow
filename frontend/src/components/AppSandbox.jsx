import { useEffect, useRef } from 'react'
import { api } from '../api.js'

const BRIDGE = `
<script>
(function() {
  const ctx = {
    submitRequest: (payload) => parent.postMessage({ type: 'apiflow-app-submit', payload }, '*'),
    runRequest: (name) => parent.postMessage({ type: 'apiflow-app-run', name }, '*'),
    setVar: (name, value) => parent.postMessage({ type: 'apiflow-app-setvar', name, value }, '*'),
  };
  window.bru = { ctx: ctx };
  window.addEventListener('message', (event) => {
    if (!event.data || event.data.type !== 'apiflow-app-result') return;
    if (window.__apiflowCallback) window.__apiflowCallback(event.data.result);
  });
})();
</script>
`

export function AppSandbox({ app, environmentId }) {
  const frameRef = useRef(null)

  useEffect(() => {
    function onMessage(event) {
      if (!event.source || event.source !== frameRef.current?.contentWindow) return
      if (!event.data?.type?.startsWith('apiflow-app-')) return
      if (event.data.type === 'apiflow-app-submit') {
        api.submitAppRequest(app.id, event.data.payload || {}, environmentId)
          .then((result) => event.source.postMessage({ type: 'apiflow-app-result', result }, event.origin))
          .catch((err) => event.source.postMessage({ type: 'apiflow-app-result', result: { ok: false, body: err.message } }, event.origin))
      }
      if (event.data.type === 'apiflow-app-run') {
        api.runAppRequest(app.id, event.data.name || '', environmentId)
          .then((result) => event.source.postMessage({ type: 'apiflow-app-result', result }, event.origin))
          .catch((err) => event.source.postMessage({ type: 'apiflow-app-result', result: { ok: false, body: err.message } }, event.origin))
      }
      if (event.data.type === 'apiflow-app-setvar') {
        api.setAppVar(app.id, event.data.name || '', event.data.value ?? '', environmentId, !!event.data.envScope)
          .then(() => event.source.postMessage({ type: 'apiflow-app-result', result: { ok: true, body: 'Variable saved' } }, event.origin))
          .catch((err) => event.source.postMessage({ type: 'apiflow-app-result', result: { ok: false, body: err.message } }, event.origin))
      }
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [app.id, environmentId])

  const html = `${BRIDGE}${app.htmlCode || '<h1>Bruno App</h1><p>Use bru.ctx.submitRequest() or bru.ctx.runRequest(name)</p>'}`

  return (
    <iframe
      ref={frameRef}
      title={app.name}
      className="app-sandbox"
      sandbox="allow-scripts allow-forms"
      srcDoc={html}
    />
  )
}
