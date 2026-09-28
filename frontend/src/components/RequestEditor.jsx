import { useEffect, useMemo, useRef, useState } from 'react'
import { api, downloadResponse, interpolate, maskSecrets, toCurl, toFetch, toJava, toPython } from '../api.js'
import { AuthFields } from './AuthFields.jsx'
import { GraphqlExplorer } from './GraphqlExplorer.jsx'
import { SoapPanel } from './SoapPanel.jsx'
import { JsonBlock } from './JsonBlock.jsx'
import { KeyValueTable } from './KeyValueTable.jsx'
import { ResponseChart } from './ResponseChart.jsx'
import { ResizableSplit } from './ResizableSplit.jsx'
import { TabMoreMenu } from './TabMoreMenu.jsx'
import { Select } from './Select.jsx'
import { PerformanceTestDialog } from './PerformanceTestDialog.jsx'
import { parseVisualizerData, renderVisualizerTemplate } from '../visualizer.js'

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'WS', 'SSE', 'MQTT', 'SOCKETIO', 'GRPC', 'SOAP']
const STREAM_METHODS = new Set(['WS', 'SSE', 'MQTT', 'SOCKETIO'])
const PRIMARY_TABS = [
  ['params', 'Params'],
  ['headers', 'Headers'],
  ['body', 'Body'],
  ['auth', 'Auth'],
  ['script', 'Script'],
  ['tests', 'Tests'],
]
const MORE_TABS = [
  ['gqlvars', 'GQL Variables'],
  ['vars', 'Variables'],
  ['prompt', 'Prompt vars'],
  ['docs', 'Docs'],
  ['example', 'Examples'],
  ['settings', 'Settings'],
]

function RequestTabBar({ tab, onTab, draft }) {
  const isGraphql = draft.bodyType === 'graphql' || draft.method === 'POST' && draft.graphqlQuery
  const moreTabs = MORE_TABS.filter(([id]) => id !== 'gqlvars' || isGraphql)
  const moreActive = moreTabs.some(([id]) => id === tab)
  const moreLabel = moreTabs.find(([id]) => id === tab)?.[1] || 'More'

  return (
    <div className="tabs request-tabs-bar">
      <div className="tabs-scroll">
        {PRIMARY_TABS.map(([id, label]) => (
          <button key={id} type="button" className={tab === id ? 'tab active' : 'tab'} onClick={() => onTab(id)}>{label}</button>
        ))}
      </div>
      {moreTabs.length > 0 && (
        <TabMoreMenu
          items={moreTabs}
          activeId={moreActive ? tab : ''}
          activeLabel={moreLabel}
          onSelect={onTab}
        />
      )}
    </div>
  )
}

export function RequestEditor({ draft, onChange, onSave, onSend, sending, saving, response, variables, secrets = [], collection, collectionId = '', environmentId = '', datasets = [], performanceRuns = [], sendKeybinding = 'mod+Enter' }) {
  const [tab, setTab] = useState('params')
  const [responseTab, setResponseTab] = useState('body')
  const [copied, setCopied] = useState('')
  const [snippet, setSnippet] = useState('curl')
  const [socketId, setSocketId] = useState('')
  const [frames, setFrames] = useState([])
  const [socketClosed, setSocketClosed] = useState(false)
  const [grpcStreamId, setGrpcStreamId] = useState('')
  const [grpcFrames, setGrpcFrames] = useState([])
  const [grpcClosed, setGrpcClosed] = useState(false)
  const [grpcText, setGrpcText] = useState('{}')
  const [grpcError, setGrpcError] = useState('')
  const [socketText, setSocketText] = useState('')
  const [socketError, setSocketError] = useState('')
  const [socketBusy, setSocketBusy] = useState(false)
  const [sseId, setSseId] = useState('')
  const [sseFrames, setSseFrames] = useState([])
  const [sseClosed, setSseClosed] = useState(false)
  const [mqttId, setMqttId] = useState('')
  const [mqttFrames, setMqttFrames] = useState([])
  const [mqttClosed, setMqttClosed] = useState(false)
  const [socketIoId, setSocketIoId] = useState('')
  const [socketIoFrames, setSocketIoFrames] = useState([])
  const [socketIoClosed, setSocketIoClosed] = useState(false)
  const [shareUrl, setShareUrl] = useState('')
  const [perfOpen, setPerfOpen] = useState(false)
  const [aiAssistText, setAiAssistText] = useState('')
  const [aiAssistBusy, setAiAssistBusy] = useState(false)
  const sendAction = useRef(() => {})
  const socketRef = useRef('')

  useEffect(() => { socketRef.current = socketId }, [socketId])
  useEffect(() => () => {
    if (socketRef.current) api.wsClose(socketRef.current).catch(() => {})
  }, [draft?.id])

  useEffect(() => {
    if (!socketId) return undefined
    let stop = false
    async function poll() {
      while (!stop) {
        try {
          const next = await api.wsFrames(socketId)
          if (stop) return
          setFrames(next.frames || [])
          setSocketClosed(!!next.closed)
          if (next.closed) return
        } catch (err) {
          if (!stop) setSocketError(err.message)
          return
        }
        await new Promise((resolve) => setTimeout(resolve, 800))
      }
    }
    poll()
    return () => { stop = true }
  }, [socketId])

  async function connectSocket() {
    setSocketError('')
    setSocketBusy(true)
    try {
      const previous = socketId
      setSocketId('')
      if (previous) await api.wsClose(previous).catch(() => {})
      const url = interpolate(draft.url || '', variables)
      const headers = (draft.headers || []).filter((header) => header.enabled !== false && header.key).map((header) => ({
        ...header,
        value: interpolate(header.value || '', variables),
      }))
      const opened = await api.wsOpen({
        url,
        headers,
        extras: draft.extras || {},
        text: interpolate(draft.body || '', variables),
      })
      setSocketId(opened.id)
      setSocketClosed(false)
      setFrames([])
      setSocketText('')
    } catch (err) {
      setSocketError(err.message)
    } finally {
      setSocketBusy(false)
    }
  }

  useEffect(() => {
    if (!sseId) return undefined
    let stop = false
    async function poll() {
      while (!stop) {
        try {
          const next = await api.sseFrames(sseId)
          if (stop) return
          setSseFrames(next.frames || [])
          setSseClosed(!!next.closed)
          if (next.closed) return
        } catch (err) {
          if (!stop) setSocketError(err.message)
          return
        }
        await new Promise((resolve) => setTimeout(resolve, 800))
      }
    }
    poll()
    return () => { stop = true }
  }, [sseId])

  useEffect(() => {
    if (!mqttId) return undefined
    let stop = false
    async function poll() {
      while (!stop) {
        try {
          const next = await api.mqttFrames(mqttId)
          if (stop) return
          setMqttFrames(next.frames || [])
          setMqttClosed(!!next.closed)
          if (next.closed) return
        } catch (err) {
          if (!stop) setSocketError(err.message)
          return
        }
        await new Promise((resolve) => setTimeout(resolve, 800))
      }
    }
    poll()
    return () => { stop = true }
  }, [mqttId])

  useEffect(() => {
    if (!socketIoId) return undefined
    let stop = false
    async function poll() {
      while (!stop) {
        try {
          const next = await api.socketFrames(socketIoId)
          if (stop) return
          setSocketIoFrames(next.frames || [])
          setSocketIoClosed(!!next.closed)
          if (next.closed) return
        } catch (err) {
          if (!stop) setSocketError(err.message)
          return
        }
        await new Promise((resolve) => setTimeout(resolve, 800))
      }
    }
    poll()
    return () => { stop = true }
  }, [socketIoId])

  async function connectMqtt() {
    setSocketError('')
    setSocketBusy(true)
    try {
      if (mqttId) await api.mqttClose(mqttId).catch(() => {})
      const opened = await api.mqttConnect({
        broker: interpolate(draft.url || '', variables),
        topic: draft.extras?.mqttTopic || draft.body || '#',
        username: draft.extras?.mqttUsername || '',
        password: draft.extras?.mqttPassword || '',
        qos: Number(draft.extras?.mqttQos || 1),
      })
      setMqttId(opened.id)
      setMqttClosed(false)
      setMqttFrames([])
    } catch (err) {
      setSocketError(err.message)
    } finally {
      setSocketBusy(false)
    }
  }

  async function connectSocketIo() {
    setSocketError('')
    setSocketBusy(true)
    try {
      if (socketIoId) await api.socketClose(socketIoId).catch(() => {})
      const opened = await api.socketConnect({
        url: interpolate(draft.url || '', variables),
        event: draft.extras?.socketIoEvent || 'message',
      })
      setSocketIoId(opened.id)
      setSocketIoClosed(false)
      setSocketIoFrames([])
    } catch (err) {
      setSocketError(err.message)
    } finally {
      setSocketBusy(false)
    }
  }

  async function connectSse() {
    setSocketError('')
    setSocketBusy(true)
    try {
      const previous = sseId
      setSseId('')
      if (previous) await api.sseClose(previous).catch(() => {})
      const url = interpolate(draft.url || '', variables)
      const headers = (draft.headers || []).filter((header) => header.enabled !== false && header.key).map((header) => ({
        ...header,
        value: interpolate(header.value || '', variables),
      }))
      const opened = await api.sseOpen({ url, headers, timeoutSeconds: draft.timeoutSeconds || 30 })
      setSseId(opened.id)
      setSseClosed(false)
      setSseFrames([])
    } catch (err) {
      setSocketError(err.message)
    } finally {
      setSocketBusy(false)
    }
  }

  useEffect(() => {
    if (!grpcStreamId || grpcClosed) return undefined
    let stop = false
    async function poll() {
      while (!stop) {
        try {
          const next = await api.grpcStreamFrames(grpcStreamId)
          setGrpcFrames(next.frames || [])
          if (next.closed) setGrpcClosed(true)
        } catch {
          stop = true
        }
        await new Promise((resolve) => setTimeout(resolve, 800))
      }
    }
    poll()
    return () => { stop = true }
  }, [grpcStreamId, grpcClosed])

  async function connectGrpcStream(mode) {
    setGrpcError('')
    try {
      const previous = grpcStreamId
      setGrpcStreamId('')
      if (previous) await api.grpcStreamClose(previous).catch(() => {})
      const url = interpolate(draft.url || '', variables)
      const opened = await api.grpcStreamOpen({
        url,
        mode,
        command: {
          method: 'GRPC',
          url,
          body: interpolate(draft.body || '{}', variables),
          headers: draft.headers || [],
          extras: draft.extras || {},
          collectionId: collection?.id || '',
          requestId: draft.id || '',
        },
      })
      setGrpcStreamId(opened.id)
      setGrpcClosed(false)
      setGrpcFrames([])
    } catch (err) {
      setGrpcError(err.message)
    }
  }

  async function sendGrpcMessage() {
    if (!grpcStreamId || !grpcText) return
    setGrpcError('')
    try {
      const next = await api.grpcStreamSend(grpcStreamId, grpcText)
      setGrpcFrames(next.frames || [])
      setGrpcText('{}')
    } catch (err) {
      setGrpcError(err.message)
    }
  }

  async function sendSocket() {
    if (!socketId || !socketText) return
    setSocketError('')
    try {
      const next = await api.wsSend(socketId, socketText)
      setFrames(next.frames || [])
      setSocketText('')
    } catch (err) {
      setSocketError(err.message)
    }
  }

  function send() {
    if ((draft.method || 'GET') === 'WS') {
      connectSocket()
      return
    }
    if ((draft.method || 'GET') === 'SSE') {
      connectSse()
      return
    }
    if ((draft.method || 'GET') === 'MQTT') {
      connectMqtt()
      return
    }
    if ((draft.method || 'GET') === 'SOCKETIO') {
      connectSocketIo()
      return
    }
    if ((draft.method || 'GET') === 'GRPC') {
      const mode = draft.extras?.grpcMode || (draft.extras?.grpcStream ? 'server' : 'unary')
      if (mode === 'client' || mode === 'bidi' || mode === 'server') {
        connectGrpcStream(mode)
        return
      }
    }
    onSend()
  }
  sendAction.current = send

  useEffect(() => {
    function onKey(event) {
      const mod = event.metaKey || event.ctrlKey
      const match = (sendKeybinding || 'mod+Enter') === 'Enter'
        ? event.key === 'Enter' && !mod
        : (sendKeybinding || 'mod+Enter') === 'mod+s'
          ? mod && event.key.toLowerCase() === 's'
          : mod && event.key === 'Enter'
      if (match) {
        event.preventDefault()
        sendAction.current()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [sendKeybinding])

  const names = Object.keys(variables || {})
  const resolved = maskSecrets(interpolate(draft.url || '', variables), secrets)

  function copySnippet() {
    const text = snippet === 'fetch' ? toFetch(draft, variables, collection) : snippet === 'python' ? toPython(draft, variables, collection) : snippet === 'java' ? toJava(draft, variables, collection) : toCurl(draft, variables, collection)
    navigator.clipboard.writeText(text)
    setCopied(snippet)
    setTimeout(() => setCopied(''), 1200)
  }

  function readCertFile(event, field) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const reader = new FileReader()
    const pem = /\.(pem|crt|cer|key)$/i.test(file.name)
    reader.onload = () => {
      let binary = ''
      if (pem) {
        binary = String(reader.result || '')
      } else {
        const bytes = new Uint8Array(reader.result)
        for (let index = 0; index < bytes.length; index += 1) binary += String.fromCharCode(bytes[index])
      }
      onChange({ extras: { ...(draft.extras || {}), [field]: btoa(binary) } })
    }
    if (pem) reader.readAsText(file)
    else reader.readAsArrayBuffer(file)
  }

  const requestPane = (
    <>
      <div className="request-toolbar">
        <input className="request-title" value={draft.name || ''} onChange={(event) => onChange({ name: event.target.value })} aria-label="Request name" />
        <div className="url-composer">
          <div className="url-row url-main">
            <Select
              className={`method method-${draft.method || 'GET'}`}
              value={draft.method || 'GET'}
              aria-label="Method"
              onChange={(method) => {
                const patch = { method }
                if (method === 'SOAP') patch.bodyType = 'soap'
                if (method === 'POST' && draft.bodyType === 'graphql') patch.bodyType = 'graphql'
                onChange(patch)
              }}
              options={METHODS.map((method) => ({ value: method, label: method }))}
            />
            <input
              className="url"
              value={draft.url || ''}
              placeholder={STREAM_METHODS.has(draft.method) ? 'wss://echo.websocket.events or {{baseUrl}}/ws' : 'https://api.example.com/users or {{baseUrl}}/users'}
              onChange={(event) => onChange({ url: event.target.value })}
              aria-label="URL"
            />
            {!STREAM_METHODS.has(draft.method) && (
              <Select
                aria-label="HTTP version"
                value={draft.extras?.httpVersion || 'http/1.1'}
                onChange={(httpVersion) => onChange({ extras: { ...(draft.extras || {}), httpVersion } })}
                options={[
                  { value: 'http/1.1', label: 'HTTP/1.1' },
                  { value: 'http/2', label: 'HTTP/2' },
                  { value: 'http/3', label: 'HTTP/3' },
                ]}
              />
            )}
          </div>
          <div className="url-row url-actions-row">
            <Select
              aria-label="Snippet"
              value={snippet}
              onChange={setSnippet}
              options={[
                { value: 'curl', label: 'curl' },
                { value: 'fetch', label: 'fetch' },
                { value: 'python', label: 'Python' },
                { value: 'java', label: 'Java' },
              ]}
            />
            <div className="url-actions">
              <button type="button" className="secondary" onClick={copySnippet}>{copied ? 'Copied' : 'Copy code'}</button>
              {collectionId && draft?.id && ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].includes(draft.method) && (
                <button type="button" className="secondary" onClick={() => setPerfOpen(true)}>Perf test</button>
              )}
              <button type="button" className="secondary" onClick={() => onSave()} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
              <button type="button" className="send" onClick={send} disabled={sending || socketBusy}>{draft.method === 'WS' ? (socketBusy ? 'Connecting…' : 'Connect') : (sending ? 'Sending…' : 'Send')}</button>
            </div>
          </div>
          {resolved && resolved !== (draft.url || '') && <p className="resolved">{resolved}</p>}
          {(names.length > 0) && (
            <div className="var-hint">
              {names.map((name) => <code key={name}>{`{{${name}}}`}</code>)}
            </div>
          )}
        </div>
      </div>

      <RequestTabBar tab={tab} onTab={setTab} draft={draft} />

      <div className="tab-panel">
        {tab === 'params' && <KeyValueTable rows={draft.params} onChange={(params) => onChange({ params })} />}
        {tab === 'headers' && <KeyValueTable rows={draft.headers} onChange={(headers) => onChange({ headers })} />}
        {tab === 'body' && <BodyEditor draft={draft} onChange={onChange} variables={variables} />}
        {tab === 'gqlvars' && (
          <div className="stack">
            <p className="muted">GraphQL variables JSON — sent separately from the query body.</p>
            <textarea className="body" value={draft.graphqlVariables || ''} spellCheck={false} placeholder='{"id": "1"}' onChange={(event) => onChange({ graphqlVariables: event.target.value, bodyType: draft.bodyType === 'graphql' ? 'graphql' : draft.bodyType, method: draft.method || 'POST' })} />
          </div>
        )}
        {tab === 'vars' && <KeyValueTable rows={draft.variables} onChange={(variables) => onChange({ variables })} secrets />}
        {tab === 'prompt' && (
          <div className="stack">
            <p className="muted">Prompt variables are asked when you send if not set elsewhere. Use for one-off values without saving to an environment.</p>
            <KeyValueTable
              rows={(draft.extras?.promptVars || []).map((row) => ({ ...row, enabled: row.enabled !== false }))}
              onChange={(promptVars) => onChange({ extras: { ...(draft.extras || {}), promptVars } })}
              keyPlaceholder="variable name"
              valuePlaceholder="default (optional)"
            />
          </div>
        )}
        {tab === 'docs' && <textarea className="body" value={draft.docs || ''} onChange={(event) => onChange({ docs: event.target.value })} placeholder="Request documentation" />}
        {tab === 'auth' && (
          <AuthFields
            draft={draft}
            onChange={onChange}
            allowInherit
            onOAuthSignIn={async () => {
              const extras = draft.extras || {}
              const started = await api.oauthStart({
                authUrl: extras.oauthAuthUrl || '',
                tokenUrl: extras.oauthTokenUrl || '',
                clientId: extras.oauthClientId || '',
                clientSecret: extras.oauthClientSecret || '',
                scope: extras.oauthScope || '',
              })
              window.open(started.url, 'apiflow-oauth', 'width=520,height=720')
            }}
            onOAuthSystemBrowser={async () => {
              const extras = draft.extras || {}
              const started = await api.oauthStart({
                authUrl: extras.oauthAuthUrl || '',
                tokenUrl: extras.oauthTokenUrl || '',
                clientId: extras.oauthClientId || '',
                clientSecret: extras.oauthClientSecret || '',
                scope: extras.oauthScope || '',
              })
              window.open(started.url, '_blank')
            }}
            onOAuthDevice={async () => {
              const extras = draft.extras || {}
              const started = await api.oauthDeviceStart({
                deviceUrl: extras.oauthDeviceUrl || '',
                clientId: extras.oauthClientId || '',
                scope: extras.oauthScope || '',
              })
              window.open(started.verificationUri, 'apiflow-device', 'width=720,height=820')
              onChange({ extras: { ...extras, oauthDeviceCode: started.deviceCode, oauthUserCode: started.userCode } })
              window.alert(`Use code ${started.userCode}, finish sign-in in the browser, then click Complete device sign-in.`)
            }}
            onOAuthDeviceComplete={async () => {
              const extras = draft.extras || {}
              const token = await api.oauthDevicePoll({
                tokenUrl: extras.oauthTokenUrl || '',
                clientId: extras.oauthClientId || '',
                clientSecret: extras.oauthClientSecret || '',
                deviceCode: extras.oauthDeviceCode || '',
              })
              onChange({ extras: { ...extras, oauthAccessToken: token.access, oauthRefreshToken: token.refresh, oauthExpiresAt: token.expiresAt } })
            }}
          />
        )}
        {tab === 'script' && (
          <ScriptPanel draft={draft} onChange={onChange} />
        )}
        {tab === 'tests' && <TestsEditor draft={draft} onChange={onChange} />}
        {tab === 'example' && (
          <div className="stack examples-panel">
            <p className="muted">Named response examples for docs and mock servers. Save from the response panel or edit here.</p>
            {(draft.examples || []).map((example, index) => (
              <div key={index} className="section-card stack">
                <div className="url-row">
                  <input value={example.name || ''} placeholder="Example name" onChange={(event) => onChange({ examples: draft.examples.map((row, i) => i === index ? { ...row, name: event.target.value } : row) })} />
                  <input type="number" value={example.status || 200} aria-label="Status code" onChange={(event) => onChange({ examples: draft.examples.map((row, i) => i === index ? { ...row, status: Number(event.target.value) || 200 } : row) })} />
                  <button type="button" className="secondary" onClick={() => onChange({ exampleBody: example.body, exampleContentType: example.contentType })}>Set default</button>
                  <button type="button" className="icon danger" onClick={() => onChange({ examples: draft.examples.filter((_, i) => i !== index) })}>×</button>
                </div>
                <textarea className="body body-compact" value={example.body || ''} spellCheck={false} onChange={(event) => onChange({ examples: draft.examples.map((row, i) => i === index ? { ...row, body: event.target.value } : row) })} />
                <input value={example.requestHeaders || ''} placeholder="Request headers that select this example, one Name: value per line" onChange={(event) => onChange({ examples: draft.examples.map((row, i) => i === index ? { ...row, requestHeaders: event.target.value } : row) })} />
                <input value={example.requestBody || ''} placeholder="Request body text that must be present" onChange={(event) => onChange({ examples: draft.examples.map((row, i) => i === index ? { ...row, requestBody: event.target.value } : row) })} />
              </div>
            ))}
            {!(draft.examples || []).length && draft.exampleBody && (
              <div className="section-card stack">
                <strong>Default example</strong>
                <MarkupPreview text={draft.exampleBody} contentType={draft.exampleContentType} />
              </div>
            )}
            {!(draft.examples || []).length && !draft.exampleBody && <p className="muted">No examples yet.</p>}
          </div>
        )}
        {tab === 'settings' && (
          <div className="auth-panel">
            <label>
              Timeout seconds
              <input type="number" min="1" max="180" value={draft.timeoutSeconds || 30} onChange={(event) => onChange({ timeoutSeconds: Number(event.target.value) })} />
            </label>
            <label className="check-line">
              <input type="checkbox" checked={draft.followRedirects !== false} onChange={(event) => onChange({ followRedirects: event.target.checked })} />
              Follow redirects
            </label>
            <label className="check-line">
              <input type="checkbox" checked={!!draft.extras?.mockEnabled} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), mockEnabled: event.target.checked } })} />
              Serve from the mock server
            </label>
            {draft.extras?.mockEnabled && (
              <>
                <label>Mock status<input type="number" value={draft.extras?.mockStatus || 200} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), mockStatus: Number(event.target.value) } })} /></label>
                <label>Mock delay (ms)<input type="number" value={draft.extras?.mockDelayMs || 0} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), mockDelayMs: Number(event.target.value) } })} /></label>
                <label>Body must contain<input value={draft.extras?.mockBodyMatch || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), mockBodyMatch: event.target.value } })} /></label>
                <label>Mock content type<input value={draft.extras?.mockContentType || 'application/json'} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), mockContentType: event.target.value } })} /></label>
                <label className="stack">Mock body<textarea className="body" value={draft.extras?.mockBody || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), mockBody: event.target.value } })} /></label>
              </>
            )}
            <label>Proxy URL<input value={draft.extras?.proxyUrl || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), proxyUrl: event.target.value } })} placeholder="http://127.0.0.1:8888" /></label>
            <label>SOAPAction<input value={draft.extras?.soapAction || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), soapAction: event.target.value } })} /></label>
            <label>
              Tags (comma-separated)
              <input
                value={(draft.tags || []).join(', ')}
                onChange={(event) => onChange({ tags: event.target.value.split(',').map((item) => item.trim()).filter(Boolean) })}
                placeholder="smoke, regression"
              />
            </label>
            <label>gRPC service<input value={draft.extras?.grpcService || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), grpcService: event.target.value } })} placeholder="package.Service/Method" /></label>
            <p className="muted">Add gRPC metadata and mTLS certificates here or on the Headers tab. Client cert fields below enable mTLS.</p>
            <label>
              Stream mode
              <Select
                value={draft.extras?.grpcMode || (draft.extras?.grpcStream ? 'server' : 'unary')}
                onChange={(grpcMode) => onChange({ extras: { ...(draft.extras || {}), grpcMode, grpcStream: grpcMode === 'server' } })}
                options={[
                  { value: 'unary', label: 'Unary' },
                  { value: 'server', label: 'Server streaming' },
                  { value: 'client', label: 'Client streaming' },
                  { value: 'bidi', label: 'Bidirectional' },
                ]}
              />
            </label>
            <div className="url-row">
              <button type="button" className="secondary" disabled={!draft.url?.trim()} onClick={() => {
                api.grpcReflect(draft.url, draft.extras?.grpcProto || '').then((services) => {
                  if (!services?.length) return
                  onChange({ extras: { ...(draft.extras || {}), grpcService: services[0] } })
                }).catch(() => {})
              }}>Reflect services</button>
              <button type="button" className="secondary" disabled={!draft.extras?.grpcProto?.trim() || !draft.extras?.grpcService?.trim()} onClick={() => {
                api.grpcEncode(draft.extras.grpcProto, draft.extras.grpcService, draft.body || '{}').then((result) => {
                  window.alert(`Encoded ${result.encoded?.length || 0} base64 chars`)
                }).catch((err) => window.alert(err.message))
              }}>Preview encode</button>
            </div>
            <label className="stack">gRPC proto<textarea className="body" value={draft.extras?.grpcProto || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), grpcProto: event.target.value } })} placeholder="syntax = 'proto3'; ..." /></label>
            <label>WebSocket subprotocol<input value={draft.extras?.wsSubprotocol || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), wsSubprotocol: event.target.value } })} /></label>
            <label>
              Custom CA certificate (.pem)
              <input type="file" accept=".pem,.crt,.cer" onChange={(event) => readCertFile(event, 'caCertBase64')} />
            </label>
            <label>
              Client certificate (.p12 or PEM)
              <input type="file" accept=".p12,.pfx,.pem,.crt,.cer,.key" onChange={(event) => readCertFile(event, 'clientCertBase64')} />
            </label>
            <label>
              Private key (.pem, optional if the certificate file already includes it)
              <input type="file" accept=".pem,.key" onChange={(event) => readCertFile(event, 'clientKeyBase64')} />
            </label>
            <label>Certificate password<input type="password" value={draft.extras?.clientCertPassword || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), clientCertPassword: event.target.value } })} /></label>
            {(draft.extras?.clientCertBase64 || draft.extras?.clientKeyBase64) && <p className="muted">A certificate is stored in this request file on this machine.</p>}
          </div>
        )}
      </div>
    </>
  )

  const responsePane = (draft.method === 'GRPC' && ['client', 'bidi', 'server'].includes(draft.extras?.grpcMode || (draft.extras?.grpcStream ? 'server' : ''))) ? (
        <GrpcStreamConsole
          streamId={grpcStreamId}
          frames={grpcFrames}
          closed={grpcClosed}
          text={grpcText}
          error={grpcError}
          mode={draft.extras?.grpcMode || 'client'}
          onText={setGrpcText}
          onSend={sendGrpcMessage}
          onFinish={async () => { if (grpcStreamId) setGrpcFrames((await api.grpcStreamFinish(grpcStreamId)).frames || []) }}
          onClose={async () => {
            if (grpcStreamId) await api.grpcStreamClose(grpcStreamId).catch(() => {})
            setGrpcStreamId('')
            setGrpcClosed(true)
          }}
        />
      ) : draft.method === 'SSE' ? (
        <SseConsole
          streamId={sseId}
          frames={sseFrames}
          closed={sseClosed}
          error={socketError}
          onClose={async () => {
            if (sseId) await api.sseClose(sseId).catch(() => {})
            setSseId('')
            setSseClosed(true)
          }}
        />
      ) : draft.method === 'MQTT' ? (
        <ProtocolConsole
          title="MQTT"
          streamId={mqttId}
          frames={mqttFrames}
          closed={mqttClosed}
          text={socketText}
          error={socketError}
          onText={setSocketText}
          onSend={async () => {
            if (!mqttId) return
            setMqttFrames((await api.mqttPublish(mqttId, { topic: draft.extras?.mqttTopic || draft.body || '#', payload: socketText, qos: 1, retained: false })).frames || [])
            setSocketText('')
          }}
          onClose={async () => { if (mqttId) await api.mqttClose(mqttId).catch(() => {}); setMqttId(''); setMqttClosed(true) }}
        />
      ) : draft.method === 'SOCKETIO' ? (
        <ProtocolConsole
          title="Socket.IO"
          streamId={socketIoId}
          frames={socketIoFrames}
          closed={socketIoClosed}
          text={socketText}
          error={socketError}
          onText={setSocketText}
          onSend={async () => {
            if (!socketIoId) return
            setSocketIoFrames((await api.socketEmit(socketIoId, { event: draft.extras?.socketIoEvent || 'message', payload: socketText })).frames || [])
            setSocketText('')
          }}
          onClose={async () => { if (socketIoId) await api.socketClose(socketIoId).catch(() => {}); setSocketIoId(''); setSocketIoClosed(true) }}
        />
      ) : draft.method === 'WS' ? (
        <SocketConsole
          socketId={socketId}
          frames={frames}
          closed={socketClosed}
          text={socketText}
          error={socketError}
          onText={setSocketText}
          onSend={sendSocket}
          onPing={async () => { if (socketId) setFrames((await api.wsPing(socketId)).frames || []) }}
          onPong={async () => { if (socketId) setFrames((await api.wsSend(socketId, 'pong')).frames || []) }}
          onSendClose={async () => { if (socketId) setFrames((await api.wsCloseFrame(socketId, 1000, 'client close')).frames || []); setSocketClosed(true) }}
          onSendBinary={async (binary) => { if (socketId) setFrames((await api.wsSendBinary(socketId, binary)).frames || []) }}
          onClose={async () => {
            if (socketId) await api.wsClose(socketId).catch(() => {})
            setSocketId('')
            setSocketClosed(true)
          }}
        />
      ) : (
      <ResponseView
        response={response}
        tab={responseTab}
        onTab={setResponseTab}
        aiAssistText={aiAssistText}
        aiAssistBusy={aiAssistBusy}
        onAiAssist={async (mode) => {
          if (!response) return
          const context = JSON.stringify({ method: draft.method, url: draft.url, status: response.status, body: response.body, checks: response.checks }, null, 2)
          const prompt = mode === 'fix'
            ? 'Write a Bruno-compatible post-response test script that fixes the failing checks for this response. Return only JavaScript.'
            : 'Explain this API response briefly and note anything unusual.'
          setAiAssistBusy(true)
          setAiAssistText('')
          try {
            let text = ''
            if (mode === 'fix') {
              const result = await api.aiScript(prompt, context)
              text = result.script || ''
            } else {
              await api.aiChatStream(prompt, context, '', (chunk) => {
                text += chunk
                setAiAssistText(text)
              })
            }
            setAiAssistText(text)
            if (mode === 'fix' && text) onChange({ postResponseScript: `${draft.postResponseScript || ''}\n${text}`.trim() })
          } catch (err) {
            setAiAssistText(err.message)
          } finally {
            setAiAssistBusy(false)
          }
        }}
        onSaveExample={() => {
          if (!response?.ok || response.binary) return
          const name = window.prompt('Example name', 'Success') || 'Success'
          const examples = [...(draft.examples || []), { name, status: response.status, body: response.body || '', contentType: response.contentType || '' }]
          const next = { ...draft, examples, exampleBody: response.body || '', exampleContentType: response.contentType || '' }
          onChange({ examples, exampleBody: next.exampleBody, exampleContentType: next.exampleContentType })
          onSave(next)
        }}
        onShare={async () => {
          if (!response) return
          const link = await api.shareRequest(draft, response)
          const url = link.publicUrl || link.publicPath
          setShareUrl(url)
          await navigator.clipboard.writeText(url)
        }}
        shareUrl={shareUrl}
      />
      )

  return (
    <div className="editor">
      <ResizableSplit storageKey="apiflow-response-height" defaultHeight={300} top={requestPane} bottom={responsePane} />
      {perfOpen && (
        <PerformanceTestDialog
          collectionId={collectionId}
          requestId={draft.id}
          requestName={draft.name}
          environmentId={environmentId}
          datasets={datasets}
          history={performanceRuns}
          onClose={() => setPerfOpen(false)}
        />
      )}
    </div>
  )
}

function ScriptPanel({ draft, onChange }) {
  function suggestScript() {
    const prompt = window.prompt('Describe the script you want (pre-request or after-response):', 'Assert status is 200 and save id from JSON body')
    if (!prompt) return
    const lower = prompt.toLowerCase()
    const afterResponse = /visualizer|response|assert|test|expect|status|body|json|header/.test(lower)
    api.aiScript(prompt, JSON.stringify({ method: draft.method, url: draft.url, bodyType: draft.bodyType }, null, 2))
      .then((result) => onChange(afterResponse
        ? { postResponseScript: result.script || '' }
        : { preRequestScript: result.script || '' }))
      .catch((err) => window.alert(err.message))
  }

  return (
    <div className="script-panel">
      <div className="script-panel-header">
        <div>
          <h3 className="script-panel-title">Scripts</h3>
          <p className="muted">JavaScript runs in a sandbox before and after each request.</p>
        </div>
        <button type="button" className="secondary" onClick={suggestScript}>AI suggest script</button>
      </div>
      <div className="script-grid">
        <div className="script-block">
          <div className="script-block-header">
            <span className="script-badge prerequest">Pre-request</span>
            <span className="muted">Before send</span>
          </div>
          <textarea
            className="code-editor"
            value={draft.preRequestScript || ''}
            spellCheck={false}
            onChange={(event) => onChange({ preRequestScript: event.target.value })}
            placeholder={'setVar("token", "abc")\napiflow.setHeader("Authorization", "Bearer " + getVar("token"))'}
          />
        </div>
        <div className="script-block">
          <div className="script-block-header">
            <span className="script-badge post">After-response</span>
            <span className="muted">Tests &amp; visualizer</span>
          </div>
          <textarea
            className="code-editor"
            value={draft.postResponseScript || ''}
            spellCheck={false}
            onChange={(event) => onChange({ postResponseScript: event.target.value })}
            placeholder={'const body = pm.response.json();\npm.visualizer.set(`<h2>{{title}}</h2>`, body);'}
          />
        </div>
      </div>
      <div className="script-help panel-callout">
        <p><strong>Pre-request:</strong> setVar, getVar, apiflow.setUrl, apiflow.setHeader, apiflow.setBody</p>
        <p><strong>After-response:</strong> getStatus, getBody, pm.response, pm.visualizer.set, pm.test, setVar</p>
      </div>
    </div>
  )
}

function StreamStatus({ connected, closed, liveLabel, idleLabel, idleHint }) {
  const status = connected ? (closed ? 'closed' : 'live') : 'idle'
  const title = connected ? (closed ? 'Closed' : liveLabel) : idleLabel
  const hint = connected ? (closed ? 'Connection ended' : 'Connection active') : idleHint
  return (
    <div className="stream-status">
      <span className={`status-dot ${status}`} aria-hidden="true" />
      <div className="stream-status-copy">
        <strong>{title}</strong>
        <span className="muted">{hint}</span>
      </div>
    </div>
  )
}

function StreamEmptyState({ title, hint }) {
  return (
    <div className="stream-empty">
      <div className="stream-empty-icon" aria-hidden="true">⇄</div>
      <strong>{title}</strong>
      <span className="muted">{hint}</span>
    </div>
  )
}

function StreamFrameList({ frames, emptyTitle, emptyHint }) {
  if (!frames?.length) {
    return <StreamEmptyState title={emptyTitle} hint={emptyHint} />
  }
  return frames.map((frame, index) => {
    const direction = frame.direction === 'out' ? 'out' : frame.direction === 'in' ? 'in' : 'system'
    const label = direction === 'out' ? 'Sent' : direction === 'in' ? 'Received' : 'Event'
    return (
      <div key={`${direction}-${index}`} className={`stream-frame ${direction}`}>
        <div className="stream-frame-meta">
          <span className="stream-frame-badge">{label}</span>
          <span className="muted">{frame.kind || frame.topic || frame.event || 'text'}</span>
        </div>
        <pre>{frame.text}</pre>
      </div>
    )
  })
}

function GrpcStreamConsole({ streamId, frames, closed, text, error, mode, onText, onSend, onFinish, onClose }) {
  const connected = !!streamId
  return (
    <div className="stream-console">
      <div className="stream-console-header">
        <StreamStatus
          connected={connected}
          closed={closed}
          liveLabel={`Live (${mode})`}
          idleLabel="Not connected"
          idleHint="Send to open an interactive gRPC stream"
        />
        {connected && !closed && (
          <div className="stream-actions">
            {mode === 'client' && <button type="button" className="secondary" onClick={onFinish}>Finish stream</button>}
            <button type="button" className="secondary" onClick={onClose}>Close</button>
          </div>
        )}
      </div>
      {error && <p className="stream-error call-error">{error}</p>}
      <div className="stream-log">
        <StreamFrameList
          frames={frames}
          emptyTitle="No stream messages yet"
          emptyHint="Send opens an interactive gRPC stream."
        />
      </div>
      <form className="stream-composer" onSubmit={(event) => { event.preventDefault(); onSend() }}>
        <input value={text} onChange={(event) => onText(event.target.value)} placeholder='{"key":"value"}' aria-label="gRPC message JSON" disabled={!streamId || closed} />
        <button type="submit" className="send" disabled={!streamId || closed || !text}>Send message</button>
      </form>
    </div>
  )
}

function SseConsole({ streamId, frames, closed, error, onClose }) {
  const connected = !!streamId
  return (
    <div className="stream-console">
      <div className="stream-console-header">
        <StreamStatus
          connected={connected}
          closed={closed}
          liveLabel="Live SSE"
          idleLabel="Not connected"
          idleHint="Connect to receive server-sent events"
        />
        {connected && !closed && <button type="button" className="secondary" onClick={onClose}>Stop</button>}
      </div>
      {error && <p className="stream-error call-error">{error}</p>}
      <div className="stream-log">
        <StreamFrameList
          frames={frames}
          emptyTitle="Waiting for events"
          emptyHint="Connect to stream server-sent events."
        />
      </div>
    </div>
  )
}

function SocketConsole({ socketId, frames, closed, text, error, onText, onSend, onPing, onPong, onSendClose, onSendBinary, onClose }) {
  const [frameType, setFrameType] = useState('text')
  const connected = !!socketId

  function sendBinary() {
    const input = document.createElement('input')
    input.type = 'file'
    input.onchange = (event) => {
      const file = event.target.files?.[0]
      if (!file) return
      const reader = new FileReader()
      reader.onload = () => {
        const raw = String(reader.result || '')
        const base64 = raw.includes(',') ? raw.split(',')[1] : btoa(raw)
        onSendBinary(base64)
      }
      reader.readAsDataURL(file)
    }
    input.click()
  }

  return (
    <div className="stream-console">
      <div className="stream-console-header">
        <StreamStatus
          connected={connected}
          closed={closed}
          liveLabel="Connected"
          idleLabel="Not connected"
          idleHint="Click Connect to open the socket"
        />
        {connected && !closed && (
          <div className="stream-actions">
            <button type="button" className="secondary" onClick={onPing}>Ping</button>
            <button type="button" className="secondary" onClick={onPong}>Pong</button>
            <button type="button" className="secondary" onClick={onSendClose}>Close frame</button>
            <button type="button" className="secondary" onClick={onClose}>Disconnect</button>
          </div>
        )}
      </div>
      {error && <p className="stream-error call-error">{error}</p>}
      <div className="stream-log">
        <StreamFrameList
          frames={frames}
          emptyTitle="No messages yet"
          emptyHint="Connect to keep the socket open and send messages below."
        />
      </div>
      <div className="stream-composer">
        <div className="segmented stream-frame-types">
          {['text', 'binary', 'ping'].map((type) => (
            <button key={type} type="button" className={frameType === type ? 'active' : ''} onClick={() => setFrameType(type)}>{type}</button>
          ))}
        </div>
        <form className="stream-composer-row" onSubmit={(event) => { event.preventDefault(); onSend() }}>
          <input
            value={text}
            onChange={(event) => onText(event.target.value)}
            placeholder={frameType === 'ping' ? 'Ping payload (optional)' : 'Type a message…'}
            aria-label="WebSocket message"
            disabled={!socketId || closed || frameType === 'binary'}
          />
          {frameType === 'text' && <button type="submit" className="send" disabled={!socketId || closed || !text}>Send</button>}
          {frameType === 'ping' && <button type="button" className="send" disabled={!socketId || closed} onClick={onPing}>Send ping</button>}
          {frameType === 'binary' && <button type="button" className="secondary" disabled={!socketId || closed} onClick={sendBinary}>Choose file</button>}
        </form>
      </div>
    </div>
  )
}

function BodyEditor({ draft, onChange, variables }) {
  return (
    <div className="body-panel">
      <div className="segmented">
        {['none', 'json', 'text', 'xml', 'soap', 'form', 'multipart', 'graphql'].map((type) => (
          <button key={type} type="button" className={draft.bodyType === type ? 'active' : ''} onClick={() => {
            const patch = { bodyType: type }
            if (type === 'graphql') patch.method = 'POST'
            if (type === 'soap') patch.method = 'SOAP'
            onChange(patch)
          }}>{type}</button>
        ))}
      </div>
      {draft.bodyType === 'soap' || draft.method === 'SOAP' ? (
        <SoapPanel draft={draft} onChange={onChange} />
      ) : (draft.bodyType === 'json' || draft.bodyType === 'text' || draft.bodyType === 'xml') && (
        <textarea className="body" value={draft.body || ''} spellCheck={false} onChange={(event) => onChange({ body: event.target.value })} />
      )}
      {(draft.bodyType === 'form' || draft.bodyType === 'multipart') && <KeyValueTable rows={draft.form} onChange={(form) => onChange({ form })} />}
      {draft.bodyType === 'multipart' && <FileList files={draft.files || []} onChange={(files) => onChange({ files })} />}
      {draft.bodyType === 'graphql' && (
        <>
          <GraphqlExplorer draft={draft} variables={variables} onChange={onChange} />
          <textarea className="body" value={draft.graphqlQuery || ''} spellCheck={false} placeholder="query { hello }" onChange={(event) => onChange({ graphqlQuery: event.target.value })} />
          <p className="muted">Variables are edited on the <strong>GQL Variables</strong> tab.</p>
        </>
      )}
      {(!draft.bodyType || draft.bodyType === 'none') && <p className="muted">This request has no body.</p>}
    </div>
  )
}

function FileList({ files, onChange }) {
  return (
    <div>
      {files.map((file, index) => (
        <div key={index} className="file-row">
          <input value={file.key || ''} placeholder="field" onChange={(event) => onChange(files.map((item, i) => i === index ? { ...item, key: event.target.value } : item))} />
          <span>{file.fileName}</span>
          <button type="button" className="icon" onClick={() => onChange(files.filter((_, i) => i !== index))}>×</button>
        </div>
      ))}
      <label className="text-button">
        + Add file
        <input type="file" hidden onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          if (!file) return
          const reader = new FileReader()
          reader.onload = () => {
            const raw = String(reader.result || '')
            const dataBase64 = raw.includes(',') ? raw.split(',')[1] : raw
            onChange([...files, { key: 'file', fileName: file.name, contentType: file.type || 'application/octet-stream', dataBase64, enabled: true }])
          }
          reader.readAsDataURL(file)
        }} />
      </label>
    </div>
  )
}

function TestsEditor({ draft, onChange }) {
  const assertions = draft.assertions || []
  const extractors = draft.extractors || []
  return (
    <div className="stack">
      <p className="muted">Assertions run after the response. Extractors save a JSON field into a variable for the next request.</p>
      {assertions.map((item, index) => (
        <div key={index} className="test-row">
          <Select
            value={item.type || 'status'}
            onChange={(type) => onChange({ assertions: assertions.map((row, i) => i === index ? { ...row, type } : row) })}
            options={[
              { value: 'status', label: 'Status equals' },
              { value: 'contains', label: 'Body contains' },
              { value: 'jsonEquals', label: 'JSON field equals' },
              { value: 'header', label: 'Header equals' },
              { value: 'time', label: 'Time under ms' },
              { value: 'regex', label: 'Body matches regex' },
              { value: 'exists', label: 'JSON field exists' },
              { value: 'type', label: 'JSON field type' },
              { value: 'isJson', label: 'Body is JSON' },
              { value: 'isArray', label: 'JSON path is array' },
            ]}
          />
          {(item.type === 'jsonEquals' || item.type === 'header') && <input value={item.path || ''} placeholder={item.type === 'header' ? 'Content-Type' : 'data.id'} onChange={(event) => onChange({ assertions: assertions.map((row, i) => i === index ? { ...row, path: event.target.value } : row) })} />}
          <input value={item.expected || ''} placeholder={item.type === 'time' ? '500' : item.type === 'regex' ? 'id":\\s*"\\w+' : 'expected'} onChange={(event) => onChange({ assertions: assertions.map((row, i) => i === index ? { ...row, expected: event.target.value } : row) })} />
          <button type="button" className="icon" onClick={() => onChange({ assertions: assertions.filter((_, i) => i !== index) })}>×</button>
        </div>
      ))}
      <button type="button" className="text-button" onClick={() => onChange({ assertions: [...assertions, { type: 'status', expected: '200', path: '', enabled: true }] })}>+ Assertion</button>
      {extractors.map((item, index) => (
        <div key={index} className="test-row">
          <input value={item.path || ''} placeholder="token" onChange={(event) => onChange({ extractors: extractors.map((row, i) => i === index ? { ...row, path: event.target.value } : row) })} />
          <input value={item.variable || ''} placeholder="variable" onChange={(event) => onChange({ extractors: extractors.map((row, i) => i === index ? { ...row, variable: event.target.value } : row) })} />
          <Select
            value={item.scope || 'collection'}
            onChange={(scope) => onChange({ extractors: extractors.map((row, i) => i === index ? { ...row, scope } : row) })}
            options={[
              { value: 'collection', label: 'Collection' },
              { value: 'environment', label: 'Environment' },
            ]}
          />
          <button type="button" className="icon" onClick={() => onChange({ extractors: extractors.filter((_, i) => i !== index) })}>×</button>
        </div>
      ))}
      <button type="button" className="text-button" onClick={() => onChange({ extractors: [...extractors, { path: '', variable: '', scope: 'collection', enabled: true }] })}>+ Save from response</button>
    </div>
  )
}

const RESPONSE_PRIMARY = [
  ['body', 'Body'],
  ['visualizer', 'Visualizer'],
  ['headers', 'Headers'],
  ['tests', 'Tests'],
  ['timeline', 'Timeline'],
]
const RESPONSE_MORE = [
  ['cookies', 'Cookies'],
  ['chart', 'Chart'],
  ['dev', 'Dev'],
]

const TIMELINE_ICONS = {
  start: '▶',
  dns: '◎',
  connect: '⇄',
  ttfb: '⏱',
  script: '{ }',
  http: '↗',
  console: '›',
  end: '■',
}

function buildUnifiedTimeline(response) {
  if (!response) return []
  const entries = [...(response.timeline || [])]
  if (entries.length === 0 && response.scriptLogs?.length) {
    response.scriptLogs.forEach((line, index) => {
      entries.push({ kind: 'console', label: 'Script console', atMs: index, detail: line })
    })
  }
  return entries.sort((left, right) => (left.atMs || 0) - (right.atMs || 0))
}

function ProtocolConsole({ title, streamId, frames, closed, text, error, onText, onSend, onClose }) {
  const connected = !!streamId
  return (
    <div className="stream-console">
      <div className="stream-console-header">
        <StreamStatus
          connected={connected}
          closed={closed}
          liveLabel={`Live ${title}`}
          idleLabel="Not connected"
          idleHint={`Connect to start ${title}`}
        />
        {connected && !closed && <button type="button" className="secondary" onClick={onClose}>Close</button>}
      </div>
      {error && <p className="stream-error call-error">{error}</p>}
      <div className="stream-log">
        <StreamFrameList
          frames={frames}
          emptyTitle="No messages yet"
          emptyHint={`Connect to start sending ${title} messages.`}
        />
      </div>
      <form className="stream-composer" onSubmit={(event) => { event.preventDefault(); onSend() }}>
        <input value={text} onChange={(event) => onText(event.target.value)} placeholder={`${title} message`} disabled={!streamId || closed} />
        <button type="submit" className="send" disabled={!streamId || closed}>Send</button>
      </form>
    </div>
  )
}

function ResponseView({ response, tab, onTab, onSaveExample, onAiAssist, aiAssistText, aiAssistBusy, onShare, shareUrl }) {
  const [copied, setCopied] = useState(false)
  const moreActive = RESPONSE_MORE.some(([id]) => id === tab)
  const moreLabel = RESPONSE_MORE.find(([id]) => id === tab)?.[1] || 'More'

  return (
    <section className="response">
      <div className="response-bar">
        <div className="tabs response-tabs-bar">
          <div className="tabs-scroll">
            {RESPONSE_PRIMARY.map(([id, label]) => (
              <button key={id} type="button" className={tab === id ? 'tab active' : 'tab'} onClick={() => onTab(id)}>{label}</button>
            ))}
          </div>
          <TabMoreMenu
            items={RESPONSE_MORE}
            activeId={moreActive ? tab : ''}
            activeLabel={moreLabel}
            onSelect={onTab}
          />
        </div>
        <div className="meta">
          {response?.ok && (
            <>
              <span className={`status-pill ${statusClass(response.status)}`}>{response.status} {response.statusText}</span>
              <span>{response.timeMs} ms</span>
              <span>{formatSize(response.size)}</span>
            </>
          )}
          {response && !response.binary && (
            <button type="button" className="secondary" onClick={() => { navigator.clipboard.writeText(response.body || ''); setCopied(true); setTimeout(() => setCopied(false), 1200) }}>{copied ? 'Copied' : 'Copy'}</button>
          )}
          {response?.ok && !response.binary && <button type="button" className="secondary" onClick={onSaveExample}>Save example</button>}
          {response && onAiAssist && <button type="button" className="secondary" disabled={aiAssistBusy} onClick={() => onAiAssist('explain')}>{aiAssistBusy ? 'Thinking…' : 'Explain response'}</button>}
          {response && onAiAssist && (response.checks || []).some((check) => !check.passed) && <button type="button" className="secondary" disabled={aiAssistBusy} onClick={() => onAiAssist('fix')}>Fix test</button>}
          {response && <button type="button" className="secondary" onClick={() => downloadResponse(response)}>Download</button>}
          {response?.ok && onShare && <button type="button" className="secondary" onClick={onShare}>Share link</button>}
        </div>
      </div>
      {shareUrl && <p className="muted share-url">Shared: <a href={shareUrl} target="_blank" rel="noreferrer">{shareUrl}</a></p>}
      <div className="response-body">
        {!response && (
          <div className="response-empty">
            <div className="response-empty-icon" aria-hidden>↗</div>
            <strong>No response yet</strong>
            <span>Send a request to see the body, headers, and tests here.</span>
            <kbd className="kbd">⌘ Enter</kbd>
          </div>
        )}
        {response && !response.ok && <p className="call-error">{response.error}</p>}
        {response?.ok && tab === 'body' && <BodyPreview response={response} />}
        {response?.ok && tab === 'visualizer' && <Visualizer response={response} />}
        {response?.ok && tab === 'headers' && <HeaderList rows={response.headers} />}
        {response?.ok && tab === 'cookies' && <HeaderList rows={response.cookies} />}
        {response?.ok && tab === 'tests' && (
          <div className="stack">
            {(response.checks || []).length === 0 && <p className="muted">No assertions on this request.</p>}
            {(response.checks || []).map((check, index) => (
              <p key={index} className={check.passed ? 'status ok' : 'status err'}>{check.passed ? 'Passed' : 'Failed'} · {check.message}</p>
            ))}
          </div>
        )}
        {response?.ok && tab === 'timeline' && (
          <div className="timeline-unified">
            {buildUnifiedTimeline(response).length === 0 && <p className="muted">No timeline entries.</p>}
            {buildUnifiedTimeline(response).map((entry, index) => (
              <div key={`${entry.kind}-${entry.atMs}-${index}`} className={`timeline-row kind-${entry.kind}`}>
                <span className="timeline-icon" aria-hidden>{TIMELINE_ICONS[entry.kind] || '·'}</span>
                <div className="timeline-copy">
                  <strong>{entry.label || entry.kind}</strong>
                  {entry.detail && <pre className="timeline-detail">{entry.detail}</pre>}
                </div>
              </div>
            ))}
          </div>
        )}
        {response?.ok && tab === 'chart' && <ResponseChart body={response.body} />}
        {aiAssistText && (
          <div className="stack">
            <strong>AI assist</strong>
            <pre className="markup">{aiAssistText}</pre>
          </div>
        )}
        {response?.ok && tab === 'dev' && (
          <div className="stack">
            <p className="muted">Network summary from the last request.</p>
            <div className="header-list">
              <div className="header-line"><span>Status</span><span>{response.status}</span></div>
              <div className="header-line"><span>Time</span><span>{response.timeMs} ms</span></div>
              <div className="header-line"><span>Size</span><span>{formatSize(response.size)}</span></div>
              {(response.timeline || []).map((entry, index) => (
                <div key={index} className="header-line"><span>{entry.label}</span><span>{entry.detail || entry.kind}</span></div>
              ))}
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

function Visualizer({ response }) {
  const template = response.visualizerTemplate || ''
  if (!template) {
    return <p className="muted">Call pm.visualizer.set(template, data) in the After-response script, then open this tab.</p>
  }
  let data
  try {
    data = parseVisualizerData(response.visualizerData)
  } catch {
    return <p className="call-error">Visualizer data is not valid JSON.</p>
  }
  const html = renderVisualizerTemplate(template, data)
  return (
    <div
      className="visualizer-panel"
      title="Response visualizer"
      style={{ width: '100%', minHeight: 280, background: '#fff', color: '#111' }}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  )
}

function BodyPreview({ response }) {
  if (response.binary && response.bodyBase64) {
    return <BinaryPreview response={response} />
  }
  return <MarkupPreview text={response.body} contentType={response.contentType} />
}

function MarkupPreview({ text, contentType = '' }) {
  const type = (contentType || '').toLowerCase()
  const source = text || ''
  const trimmed = source.trim()
  if (type.includes('html') || trimmed.startsWith('<!doctype html') || trimmed.startsWith('<html')) {
    return (
      <div className="stack">
        <iframe className="preview-frame" title="HTML preview" sandbox="" srcDoc={source} />
        <pre className="markup">{prettyMarkup(source)}</pre>
      </div>
    )
  }
  if (type.includes('xml') || (trimmed.startsWith('<') && trimmed.endsWith('>'))) {
    return <pre className="markup">{prettyMarkup(source)}</pre>
  }
  return <JsonBlock text={source} />
}

function prettyMarkup(source) {
  const compact = source.replace(/>\s+</g, '><').trim()
  let pad = 0
  return compact.replace(/></g, '>\n<').split('\n').map((line) => {
    if (line.match(/^<\//)) pad = Math.max(pad - 1, 0)
    const indented = `${'  '.repeat(pad)}${line}`
    if (line.match(/^<[^!/?][^>]*[^/]>$/)) pad += 1
    return indented
  }).join('\n')
}

function BinaryPreview({ response }) {
  const type = (response.contentType || 'application/octet-stream').split(';')[0].trim().toLowerCase()
  const url = useMemo(() => {
    const bytes = Uint8Array.from(atob(response.bodyBase64), (char) => char.charCodeAt(0))
    return URL.createObjectURL(new Blob([bytes], { type: type || 'application/octet-stream' }))
  }, [response.bodyBase64, type])
  useEffect(() => () => URL.revokeObjectURL(url), [url])
  if (type.startsWith('image/')) return <img className="preview" alt="Response" src={url} />
  if (type.startsWith('audio/')) return <audio controls src={url} />
  if (type.startsWith('video/')) return <video className="preview" controls src={url} />
  if (type.includes('pdf')) return <iframe className="preview-frame" title="PDF preview" src={url} />
  return <p className="muted">This response is a file ({response.contentType || 'binary'}). Use Download to save it.</p>
}

function HeaderList({ rows }) {
  return (
    <div className="header-list">
      {(rows || []).map((header, index) => (
        <div key={index} className="header-line"><span>{header.key}</span><span>{header.value}</span></div>
      ))}
    </div>
  )
}

function statusClass(code) {
  if (code >= 200 && code < 300) return 'status ok'
  if (code >= 300 && code < 400) return 'status warn'
  return 'status err'
}

function formatSize(bytes) {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`
  return `${(bytes / 1024).toFixed(1)} KB`
}
