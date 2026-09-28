import { useState } from 'react'
import { api } from '../api.js'
import { Select } from './Select.jsx'
import { FlowCanvas } from './FlowCanvas.jsx'

const TABS = ['Spec Hub', 'Monitors', 'Flows', 'Datasets', 'Documents', 'Team', 'Webhooks', 'Inventory', 'SDK', 'Local']

export function PlatformHub({ workspace, onRefresh }) {
  const [tab, setTab] = useState('Spec Hub')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [specDraft, setSpecDraft] = useState(null)
  const [monitorDraft, setMonitorDraft] = useState(null)
  const [flowDraft, setFlowDraft] = useState(null)
  const [datasetDraft, setDatasetDraft] = useState(null)
  const [docDraft, setDocDraft] = useState(null)
  const [lint, setLint] = useState(null)
  const [queryResult, setQueryResult] = useState(null)
  const [perfResult, setPerfResult] = useState(null)
  const [flowPrompt, setFlowPrompt] = useState('')

  async function run(action) {
    setBusy('run')
    setError('')
    try {
      await action()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy('')
    }
  }

  return (
    <div className="platform-hub stack">
      <div className="segmented">
        {TABS.map((name) => (
          <button key={name} type="button" className={tab === name ? 'active' : ''} onClick={() => setTab(name)}>{name}</button>
        ))}
      </div>
      {error && <p className="call-error">{error}</p>}
      {message && <p className="muted">{message}</p>}

      {tab === 'Spec Hub' && (
        <div className="stack">
          <div className="toolbar-row">
            <button type="button" className="secondary" onClick={() => setSpecDraft({ name: 'New Spec', format: 'openapi', content: 'openapi: 3.0.0\ninfo:\n  title: API\n  version: 1.0.0\npaths: {}' })}>New spec</button>
          </div>
          <div className="card-list">
            {(workspace.specs || []).map((spec) => (
              <button key={spec.id} type="button" className="card card-clickable" onClick={() => setSpecDraft(spec)}>
                <strong>{spec.name}</strong>
                <span className="muted">{spec.format}</span>
              </button>
            ))}
          </div>
          {specDraft && (
            <div className="section-card stack">
              <input value={specDraft.name || ''} onChange={(event) => setSpecDraft({ ...specDraft, name: event.target.value })} placeholder="Spec name" />
              <Select value={specDraft.format || 'openapi'} onChange={(value) => setSpecDraft({ ...specDraft, format: value })} options={[
                { value: 'openapi', label: 'OpenAPI' },
                { value: 'asyncapi', label: 'AsyncAPI' },
                { value: 'protobuf', label: 'Protobuf' },
                { value: 'graphql', label: 'GraphQL SDL' },
                { value: 'smithy', label: 'Smithy 2.0' },
              ]} />
              <textarea className="body body-compact" value={specDraft.content || ''} onChange={(event) => setSpecDraft({ ...specDraft, content: event.target.value })} spellCheck={false} />
              <textarea className="body body-compact" value={(specDraft.files || []).map((file) => `# ${file.path}\n${file.content}`).join('\n')} onChange={(event) => {
                const files = event.target.value.split(/^# /m).filter(Boolean).map((block) => {
                  const [path, ...rest] = block.split('\n')
                  return { path: path.trim(), content: rest.join('\n') }
                })
                setSpecDraft({ ...specDraft, files })
              }} spellCheck={false} placeholder={'# components/schemas.yaml\ncomponents:\n  schemas: {}'} />
              <div className="row">
                <button type="button" className="secondary" disabled={!!busy} onClick={() => run(async () => {
                  const result = await api.saveSpec(specDraft)
                  onRefresh(result.workspace)
                  setSpecDraft(result.workspace.specs.find((item) => item.id === result.focusId) || specDraft)
                  setMessage('Spec saved.')
                })}>Save</button>
                <button type="button" className="secondary" disabled={!!busy || !specDraft.id} onClick={() => run(async () => {
                  setLint(await api.lintSpec(specDraft.id))
                })}>Lint / governance</button>
                <button type="button" className="secondary" disabled={!!busy} onClick={() => run(async () => {
                  const schema = await api.inferTypes(specDraft.content || '{}')
                  setSpecDraft({ ...specDraft, content: `${specDraft.content || ''}\n# inferred\n${JSON.stringify(schema, null, 2)}` })
                })}>Infer types from example</button>
                <button type="button" className="secondary" disabled={!!busy || !specDraft.id} onClick={() => run(async () => {
                  window.open(`http://localhost:8080/api/platform/specs/${specDraft.id}/preview`, '_blank')
                })}>Live preview</button>
                <button type="button" className="send" disabled={!!busy || !specDraft.collectionId} onClick={() => run(async () => {
                  const result = await api.syncSpecToCollection(specDraft.id, specDraft.collectionId)
                  onRefresh(result.workspace)
                  setMessage('Collection synced from spec.')
                })}>Sync to collection</button>
              </div>
              <Select value={specDraft.collectionId || ''} onChange={(value) => setSpecDraft({ ...specDraft, collectionId: value })} options={[
                { value: '', label: 'Link collection for sync' },
                ...(workspace.collections || []).map((collection) => ({ value: collection.id, label: collection.name })),
              ]} />
              {lint && (
                <div className="stack">
                  {(lint.issues || []).map((issue, index) => (
                    <p key={index} className={issue.level === 'error' ? 'call-error' : 'muted'}>{issue.level}: {issue.message}</p>
                  ))}
                  {!(lint.issues || []).length && <p className="muted">No governance issues found.</p>}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {tab === 'Monitors' && (
        <div className="stack">
          <button type="button" className="secondary" onClick={() => setMonitorDraft({ name: 'Health check', schedule: 'every 5 minutes', enabled: true, triggerMode: 'schedule', collectionId: workspace.collections?.[0]?.id || '' })}>New monitor</button>
          <div className="card-list">
            {(workspace.monitors || []).map((monitor) => (
              <button key={monitor.id} type="button" className="card card-clickable" onClick={() => setMonitorDraft(monitor)}>
                <strong>{monitor.name}</strong>
                <span className="muted">{monitor.lastStatus || 'never run'} · {monitor.schedule}</span>
              </button>
            ))}
          </div>
          {monitorDraft && (
            <div className="section-card stack">
              <input value={monitorDraft.name || ''} onChange={(event) => setMonitorDraft({ ...monitorDraft, name: event.target.value })} />
              <Select value={monitorDraft.collectionId || ''} onChange={(value) => setMonitorDraft({ ...monitorDraft, collectionId: value })} options={(workspace.collections || []).map((c) => ({ value: c.id, label: c.name }))} />
              <Select value={monitorDraft.environmentId || ''} onChange={(value) => setMonitorDraft({ ...monitorDraft, environmentId: value })} options={[{ value: '', label: 'No env' }, ...(workspace.environments || []).map((e) => ({ value: e.id, label: e.name }))]} />
              <input value={monitorDraft.schedule || ''} onChange={(event) => setMonitorDraft({ ...monitorDraft, schedule: event.target.value })} placeholder="every 5 minutes" />
              <Select value={monitorDraft.triggerMode || 'schedule'} onChange={(value) => setMonitorDraft({ ...monitorDraft, triggerMode: value })} options={[
                { value: 'schedule', label: 'Scheduled runs' },
                { value: 'cli', label: 'CLI trigger only' },
              ]} />
              <label><input type="checkbox" checked={!!monitorDraft.enabled} onChange={(event) => setMonitorDraft({ ...monitorDraft, enabled: event.target.checked })} /> Enabled</label>
              <input value={(monitorDraft.alertEmails || []).join(', ')} onChange={(event) => setMonitorDraft({ ...monitorDraft, alertEmails: event.target.value.split(',').map((v) => v.trim()).filter(Boolean) })} placeholder="Alert emails" />
              <input value={monitorDraft.smtpHost || ''} onChange={(event) => setMonitorDraft({ ...monitorDraft, smtpHost: event.target.value })} placeholder="SMTP host:port for real email" />
              <input value={monitorDraft.region || ''} onChange={(event) => setMonitorDraft({ ...monitorDraft, region: event.target.value })} placeholder="Region label" />
              <input value={monitorDraft.reportTitle || ''} onChange={(event) => setMonitorDraft({ ...monitorDraft, reportTitle: event.target.value })} placeholder="Report title" />
              <Select value={monitorDraft.datasetId || ''} onChange={(value) => setMonitorDraft({ ...monitorDraft, datasetId: value })} options={[{ value: '', label: 'No dataset' }, ...(workspace.datasets || []).map((dataset) => ({ value: dataset.id, label: dataset.name }))]} />
              <Select value={monitorDraft.runnerMode || 'local'} onChange={(value) => setMonitorDraft({ ...monitorDraft, runnerMode: value })} options={[
                { value: 'local', label: 'Local scheduler' },
                { value: 'private', label: 'Private network runner' },
              ]} />
              <label><input type="checkbox" checked={!!monitorDraft.hideUrls} onChange={(event) => setMonitorDraft({ ...monitorDraft, hideUrls: event.target.checked })} /> Hide URLs in the published report</label>
              <input value={monitorDraft.slackWebhook || ''} onChange={(event) => setMonitorDraft({ ...monitorDraft, slackWebhook: event.target.value })} placeholder="Slack webhook URL" />
              <div className="row">
                <button type="button" className="secondary" disabled={!!busy} onClick={() => run(async () => {
                  const result = await api.saveMonitor(monitorDraft)
                  onRefresh(result.workspace)
                  setMonitorDraft(result.workspace.monitors.find((item) => item.id === result.focusId))
                })}>Save</button>
                <button type="button" className="send" disabled={!!busy || !monitorDraft.id} onClick={() => run(async () => {
                  await api.runMonitor(monitorDraft.id)
                  onRefresh(await api.workspace())
                  setMessage('Monitor run completed.')
                })}>Run now</button>
              </div>
            </div>
          )}
          <UptimePanel />
          <div className="stack">
            {(workspace.monitorRuns || []).slice(0, 8).map((item) => (
              <div key={item.id} className="card row">
                <span>{new Date(item.startedAt).toLocaleString()} · {item.passed} passed · {item.failed} failed{item.region ? ` · ${item.region}` : ''}{item.runnerAddress ? ` · ${item.runnerAddress}` : ''}</span>
                <button type="button" className="secondary" disabled={!!busy} onClick={() => run(async () => {
                  const published = await api.publishMonitorRun(item.id)
                  setMessage(`Report published: ${published.reportUrl || api.monitorReportUrl(item.id)}`)
                })}>{item.published ? 'Republish' : 'Publish report'}</button>
                {item.reportUrl && <a href={item.reportUrl} target="_blank" rel="noreferrer">Open report</a>}
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === 'Flows' && (
        <div className="stack">
          <div className="toolbar-row">
            <button type="button" className="secondary" onClick={() => setFlowDraft({ name: 'New Flow', blocks: [{ id: 'start', type: 'start', label: 'Start', x: 40, y: 40, config: '{}' }], connections: [] })}>New flow</button>
            <input value={flowPrompt} onChange={(event) => setFlowPrompt(event.target.value)} placeholder="Describe flow for Agent Mode" />
            <button type="button" className="send" disabled={!!busy} onClick={() => run(async () => {
              const flow = await api.generateFlow(flowPrompt)
              setFlowDraft(flow)
              onRefresh(await api.workspace())
            })}>Generate with AI</button>
          </div>
          <div className="card-list">
            {(workspace.flows || []).map((flow) => (
              <button key={flow.id} type="button" className="card card-clickable" onClick={() => setFlowDraft(flow)}>
                <strong>{flow.name}</strong>
                <span className="muted">{flow.deployed ? `deployed :${flow.deployPort}` : 'draft'}</span>
              </button>
            ))}
          </div>
          {flowDraft && (
            <div className="section-card stack">
              <input value={flowDraft.name || ''} onChange={(event) => setFlowDraft({ ...flowDraft, name: event.target.value })} />
              <FlowCanvas flow={flowDraft} onChange={setFlowDraft} />
              <textarea className="body body-compact" value={JSON.stringify(flowDraft, null, 2)} onChange={(event) => {
                try { setFlowDraft(JSON.parse(event.target.value)) } catch { /* ignore while typing */ }
              }} spellCheck={false} />
              <div className="row">
                <button type="button" className="secondary" disabled={!!busy} onClick={() => run(async () => {
                  const result = await api.saveFlow(flowDraft)
                  onRefresh(result.workspace)
                  setFlowDraft(result.workspace.flows.find((item) => item.id === result.focusId))
                })}>Save</button>
                <button type="button" className="secondary" disabled={!!busy || !flowDraft.id} onClick={() => run(async () => {
                  const result = await api.runFlow(flowDraft.id, {})
                  setMessage(`Flow run: ${result.steps?.filter((s) => s.ok).length}/${result.steps?.length} steps ok`)
                })}>Run</button>
                <button type="button" className="send" disabled={!!busy || !flowDraft.id} onClick={() => run(async () => {
                  const result = await api.deployFlow(flowDraft.id)
                  setMessage(`Deployed at ${result.localUrl}`)
                })}>Deploy endpoint</button>
              </div>
            </div>
          )}
        </div>
      )}

      {tab === 'Datasets' && (
        <div className="stack">
          <button type="button" className="secondary" onClick={() => setDatasetDraft({ name: 'Users CSV', sourceType: 'csv', content: 'id,name\n1,Ada\n2,Grace', sqlView: 'SELECT * FROM data' })}>New dataset</button>
          <div className="card-list">
            {(workspace.datasets || []).map((dataset) => (
              <button key={dataset.id} type="button" className="card card-clickable" onClick={() => setDatasetDraft(dataset)}>
                <strong>{dataset.name}</strong>
                <span className="muted">{dataset.sourceType}</span>
              </button>
            ))}
          </div>
          {datasetDraft && (
            <div className="section-card stack">
              <input value={datasetDraft.name || ''} onChange={(event) => setDatasetDraft({ ...datasetDraft, name: event.target.value })} />
              <Select value={datasetDraft.sourceType || 'csv'} onChange={(value) => setDatasetDraft({ ...datasetDraft, sourceType: value })} options={[
                { value: 'csv', label: 'CSV file' },
                { value: 'json', label: 'JSON rows' },
                { value: 'jdbc', label: 'Live JDBC database' },
              ]} />
              {datasetDraft.sourceType === 'jdbc' ? (
                <>
                  <input value={datasetDraft.jdbcUrl || ''} onChange={(event) => setDatasetDraft({ ...datasetDraft, jdbcUrl: event.target.value })} placeholder="jdbc:postgresql://localhost/db" />
                  <input value={datasetDraft.jdbcUser || ''} onChange={(event) => setDatasetDraft({ ...datasetDraft, jdbcUser: event.target.value })} placeholder="User" />
                  <input type="password" value={datasetDraft.jdbcPassword || ''} onChange={(event) => setDatasetDraft({ ...datasetDraft, jdbcPassword: event.target.value })} placeholder="Password" />
                </>
              ) : (
                <textarea className="body body-compact" value={datasetDraft.content || ''} onChange={(event) => setDatasetDraft({ ...datasetDraft, content: event.target.value })} spellCheck={false} />
              )}
              <input value={datasetDraft.sqlView || ''} onChange={(event) => setDatasetDraft({ ...datasetDraft, sqlView: event.target.value })} placeholder="SQL view e.g. SELECT * FROM data WHERE id = 1" />
              <div className="row">
                <button type="button" className="secondary" disabled={!!busy} onClick={() => run(async () => {
                  const result = await api.saveDataset(datasetDraft)
                  onRefresh(result.workspace)
                  setDatasetDraft(result.workspace.datasets.find((item) => item.id === result.focusId))
                })}>Save</button>
                <button type="button" className="send" disabled={!!busy || !datasetDraft.id} onClick={() => run(async () => {
                  setQueryResult(await api.queryDataset(datasetDraft.id, datasetDraft.sqlView))
                })}>Query</button>
              </div>
              {queryResult && (
                <pre className="markup">{JSON.stringify(queryResult, null, 2)}</pre>
              )}
            </div>
          )}
        </div>
      )}

      {tab === 'Documents' && (
        <div className="stack">
          <p className="muted">Workspace documents. Markdown images and video links (`![alt](url)`, raw video URLs) stay in the page. Custom domain: {workspace.settings?.customDomain || 'not set'}.</p>
          <div className="card-list">
            {(workspace.collections || []).filter((collection) => collection.docsPublic).map((collection) => (
              <div key={collection.id} className="card"><strong>{collection.name}</strong><span className="muted">Private API network</span></div>
            ))}
          </div>
          <div className="stack">
            <strong>Updates</strong>
            {(workspace.activity || []).slice(0, 12).map((event) => (
              <p key={event.id} className="muted">{event.at ? new Date(event.at).toLocaleString() : ''} · {event.message}</p>
            ))}
            {!(workspace.activity || []).length && <p className="muted">Spec, document, and collection pull request updates show up here.</p>}
          </div>
          <button type="button" className="secondary" onClick={() => setDocDraft({ title: 'Design notes', content: '# Overview\n\nDocument API decisions here.' })}>New document</button>
          <div className="card-list">
            {(workspace.documents || []).map((doc) => (
              <button key={doc.id} type="button" className="card card-clickable" onClick={() => setDocDraft(doc)}>
                <strong>{doc.title}</strong>
                {doc.pinned && <span className="badge">Pinned</span>}
              </button>
            ))}
          </div>
          {docDraft && (
            <div className="section-card stack">
              <input value={docDraft.title || ''} onChange={(event) => setDocDraft({ ...docDraft, title: event.target.value })} />
              <label><input type="checkbox" checked={!!docDraft.pinned} onChange={(event) => setDocDraft({ ...docDraft, pinned: event.target.checked })} /> Pin in workspace overview</label>
              <div className="row">
                <button type="button" className="secondary" onClick={() => setDocDraft({ ...docDraft, content: `${docDraft.content || ''}\n![diagram](https://example.com/diagram.png)\n` })}>Insert image</button>
                <button type="button" className="secondary" onClick={() => setDocDraft({ ...docDraft, content: `${docDraft.content || ''}\n<video src="https://example.com/demo.mp4" controls></video>\n` })}>Insert video</button>
              </div>
              <textarea className="body body-compact" value={docDraft.content || ''} onChange={(event) => setDocDraft({ ...docDraft, content: event.target.value })} spellCheck={false} />
              <div className="section-card" dangerouslySetInnerHTML={{ __html: renderDocument(docDraft.content || '') }} />
              <button type="button" className="send" disabled={!!busy} onClick={() => run(async () => {
                const result = await api.saveDocument(docDraft)
                onRefresh(result.workspace)
                setDocDraft(result.workspace.documents.find((item) => item.id === result.focusId))
                setMessage('Document saved.')
              })}>Save</button>
            </div>
          )}
        </div>
      )}

      {tab === 'Team' && (
        <TeamPanel workspace={workspace} busy={busy} run={run} onRefresh={onRefresh} />
      )}
      {tab === 'Webhooks' && (
        <WebhookPanel workspace={workspace} busy={busy} run={run} onRefresh={onRefresh} setMessage={setMessage} />
      )}
      {tab === 'Inventory' && (
        <InventoryPanel workspace={workspace} busy={busy} run={run} onRefresh={onRefresh} />
      )}
      {tab === 'SDK' && (
        <SdkPanel workspace={workspace} busy={busy} run={run} />
      )}
      {tab === 'Local' && (
        <LocalTools workspace={workspace} busy={busy} run={run} onRefresh={onRefresh} setMessage={setMessage} />
      )}
    </div>
  )
}

function renderDocument(markdown) {
  const escaped = markdown
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped
    .replace(/!\[([^\]]*)\]\((https?:\/\/[^)]+)\)/g, '<img alt="$1" src="$2" style="max-width:100%" />')
    .replace(/&lt;video src="(https?:\/\/[^"]+)" controls&gt;&lt;\/video&gt;/g, '<video src="$1" controls style="max-width:100%"></video>')
    .replace(/\n/g, '<br />')
}

function UptimePanel() {
  const [rows, setRows] = useState([])
  return (
    <div className="section-card stack">
      <div className="row">
        <strong>Uptime</strong>
        <button type="button" className="secondary" onClick={async () => setRows(await api.uptime())}>Refresh</button>
      </div>
      {rows.map((row) => (
        <div key={row.id} className="chart-row">
          <span>{row.name}</span>
          <div className="chart-bar"><i style={{ width: `${Math.max(0, Math.min(100, row.uptimePercent))}%` }} /></div>
          <span>{Number(row.uptimePercent).toFixed(0)}% · {row.region || 'local'} · {row.lastStatus || 'no runs'}</span>
        </div>
      ))}
    </div>
  )
}

function TeamPanel({ workspace, busy, run, onRefresh }) {
  const [draft, setDraft] = useState({ name: '', email: '', role: 'editor', active: true })
  return (
    <div className="stack">
      <p className="muted">Local roles. The active member is the actor for comments, webhooks, and inventory capture. Viewer cannot write. Owner manages members.</p>
      <div className="card-list">
        {(workspace.members || []).map((member) => (
          <div key={member.id} className="card">
            <strong>{member.name}</strong>
            <span className="muted">{member.role}{member.active ? ' · active' : ''}</span>
          </div>
        ))}
      </div>
      <div className="section-card stack">
        <input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} placeholder="Name" />
        <input value={draft.email} onChange={(event) => setDraft({ ...draft, email: event.target.value })} placeholder="Email" />
        <Select value={draft.role} onChange={(value) => setDraft({ ...draft, role: value })} options={[
          { value: 'owner', label: 'Owner' },
          { value: 'editor', label: 'Editor' },
          { value: 'viewer', label: 'Viewer' },
        ]} />
        <label><input type="checkbox" checked={!!draft.active} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} /> Active actor</label>
        <button type="button" className="send" disabled={busy || !draft.name.trim()} onClick={() => run(async () => onRefresh((await api.saveMember(draft)).workspace))}>Save member</button>
      </div>
    </div>
  )
}

function WebhookPanel({ workspace, busy, run, onRefresh, setMessage }) {
  const [draft, setDraft] = useState({ name: 'CI hook', targetType: 'collection', targetId: workspace.collections?.[0]?.id || '', enabled: true })
  return (
    <div className="stack">
      <p className="muted">POST /hooks/&lt;id&gt; runs the linked collection or monitor. The HTML reports they publish are standalone files under /public.</p>
      {(workspace.webhooks || []).map((hook) => (
        <div key={hook.id} className="card">
          <strong>{hook.name}</strong>
          <span className="muted">{hook.targetType} · /hooks/{hook.id}</span>
        </div>
      ))}
      <div className="section-card stack">
        <input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
        <Select value={draft.targetType} onChange={(value) => setDraft({ ...draft, targetType: value, targetId: '' })} options={[
          { value: 'collection', label: 'Collection run' },
          { value: 'monitor', label: 'Monitor run' },
        ]} />
        <Select value={draft.targetId} onChange={(value) => setDraft({ ...draft, targetId: value })} options={(draft.targetType === 'monitor' ? workspace.monitors || [] : workspace.collections || []).map((item) => ({ value: item.id, label: item.name }))} />
        <button type="button" className="send" disabled={busy || !draft.targetId} onClick={() => run(async () => {
          const result = await api.saveWebhook(draft)
          onRefresh(result.workspace)
          setMessage(`Webhook URL: /hooks/${result.focusId}`)
        })}>Save webhook</button>
      </div>
    </div>
  )
}

function InventoryPanel({ workspace, busy, run, onRefresh }) {
  const [name, setName] = useState('Web app')
  const [capture, setCapture] = useState('[{"method":"GET","url":"https://example.com/users","status":200}]')
  return (
    <div className="stack">
      <p className="muted">Paste a Playwright-style network capture. Calls are matched to collection requests by method and path.</p>
      <input value={name} onChange={(event) => setName(event.target.value)} />
      <textarea className="body body-compact" value={capture} onChange={(event) => setCapture(event.target.value)} spellCheck={false} />
      <button type="button" className="send" disabled={busy} onClick={() => run(async () => {
        const calls = JSON.parse(capture)
        onRefresh((await api.workspace()))
        await api.captureInventory({ name, environment: 'local', calls })
        onRefresh(await api.workspace())
      })}>Import capture</button>
      {(workspace.inventory || []).map((app) => (
        <div key={app.id} className="card">
          <strong>{app.name}</strong>
          <span className="muted">{app.matched} matched · {app.unmatched} unmatched</span>
        </div>
      ))}
    </div>
  )
}

function SdkPanel({ workspace, busy, run }) {
  const [collectionId, setCollectionId] = useState(workspace.collections?.[0]?.id || '')
  const [language, setLanguage] = useState('typescript')
  const [source, setSource] = useState('')
  return (
    <div className="stack">
      <Select value={collectionId} onChange={setCollectionId} options={(workspace.collections || []).map((item) => ({ value: item.id, label: item.name }))} />
      <Select value={language} onChange={setLanguage} options={[{ value: 'typescript', label: 'TypeScript' }, { value: 'python', label: 'Python' }]} />
      <button type="button" className="send" disabled={busy || !collectionId} onClick={() => run(async () => {
        const result = await api.generateSdk(collectionId, language)
        setSource(`// ${result.filename}\n${result.source}`)
      })}>Generate SDK</button>
      {source && <pre className="markup">{source}</pre>}
    </div>
  )
}

function LocalTools({ workspace, busy, run, onRefresh, setMessage }) {
  const [vaultKey, setVaultKey] = useState('')
  const [vaultValue, setVaultValue] = useState('')
  const [vault, setVault] = useState(null)
  const [passphrase, setPassphrase] = useState('')
  const [captured, setCaptured] = useState([])
  const [packageName, setPackageName] = useState('shared')
  const [packageCode, setPackageCode] = useState('exports.teamHeader = function () { return "apiflow" }')
  const [collectionId, setCollectionId] = useState(workspace.collections?.[0]?.id || '')
  const [targetId, setTargetId] = useState('')
  const [pullTitle, setPullTitle] = useState('Update requests')
  const settings = workspace.settings || {}
  const entries = vault?.entries || {}
  return (
    <div className="stack">
      <div className="section-card stack">
        <strong>Vault</strong>
        <p className="muted">Secrets are encrypted with your passphrase. Use {'{{vault:key}}'} in URLs, headers, and bodies.</p>
        <div className="url-row">
          <input value={passphrase} type="password" placeholder="Passphrase" onChange={(event) => setPassphrase(event.target.value)} />
          <button type="button" className="secondary" disabled={busy || !passphrase} onClick={() => run(async () => setVault(await api.unlockVault(passphrase)))}>Unlock</button>
        </div>
        <div className="url-row">
          <input value={vaultKey} onChange={(event) => setVaultKey(event.target.value)} placeholder="key" />
          <input value={vaultValue} onChange={(event) => setVaultValue(event.target.value)} placeholder="value" type="password" />
          <button type="button" className="secondary" disabled={busy || !vaultKey.trim()} onClick={() => run(async () => {
            setVault(await api.saveVault(vaultKey.trim(), vaultValue))
            setVaultValue('')
          })}>Save</button>
          <button type="button" className="secondary" onClick={() => run(async () => setVault(await api.vault()))}>List</button>
        </div>
        {vault?.locked && <p className="muted">Vault is locked.</p>}
        {Object.keys(entries).map((key) => (
          <div key={key} className="row"><span>{key}</span><span className="muted">{entries[key]}</span><button type="button" className="secondary" onClick={() => run(async () => setVault(await api.deleteVault(key)))}>Delete</button></div>
        ))}
      </div>
      <div className="section-card stack">
        <strong>Traffic capture</strong>
        <p className="muted">Point the browser at this HTTP proxy. HTTPS is decrypted with a local CA. Trust the certificate file shown after start.</p>
        <div className="row">
          <button type="button" className="secondary" disabled={busy} onClick={() => run(async () => {
            const started = await api.startCapture(8888)
            setMessage(`Capture proxy on port ${started.port}. Trust ${started.certificate}`)
          })}>Start proxy</button>
          <button type="button" className="secondary" disabled={busy} onClick={() => run(async () => { await api.stopCapture(); setMessage('Capture stopped') })}>Stop</button>
          <button type="button" className="secondary" disabled={busy} onClick={() => run(async () => setCaptured(await api.captured()))}>Refresh</button>
          <button type="button" className="send" disabled={busy || !collectionId} onClick={() => run(async () => {
            onRefresh((await api.importCaptured(collectionId)).workspace)
            setMessage('Captured calls imported')
          })}>Import into collection</button>
        </div>
        <Select value={collectionId} onChange={setCollectionId} options={(workspace.collections || []).map((item) => ({ value: item.id, label: item.name }))} />
        {captured.map((call, index) => <p key={index} className="muted">{call.method} {call.url}</p>)}
      </div>
      <div className="section-card stack">
        <strong>Team package library</strong>
        <p className="muted">Packages are modules. Call them with require('name') and assign functions to exports.</p>
        <input value={packageName} onChange={(event) => setPackageName(event.target.value)} />
        <textarea className="body body-compact" value={packageCode} onChange={(event) => setPackageCode(event.target.value)} spellCheck={false} />
        <button type="button" className="send" disabled={busy} onClick={() => run(async () => {
          await api.savePackage({ name: packageName, code: packageCode })
          setMessage('Package saved')
        })}>Save package</button>
      </div>
      <div className="section-card stack">
        <strong>Workspace sharing</strong>
        <Select value={settings.visibility || 'private'} onChange={(value) => run(async () => onRefresh((await api.updateWorkspace({ ...workspace, settings: { ...settings, visibility: value } })).workspace || { ...workspace, settings: { ...settings, visibility: value } }))} options={[
          { value: 'private', label: 'Private workspace' },
          { value: 'partner', label: 'Partner workspace' },
          { value: 'public', label: 'Public workspace' },
        ]} />
        <input value={settings.customDomain || ''} placeholder="docs.example.com" onChange={(event) => onRefresh({ ...workspace, settings: { ...settings, customDomain: event.target.value } })} />
        <input value={settings.staticIpRanges || ''} placeholder="203.0.113.0/24" onChange={(event) => onRefresh({ ...workspace, settings: { ...settings, staticIpRanges: event.target.value } })} />
        <input value={settings.regions || ''} placeholder="local, eu, us" onChange={(event) => onRefresh({ ...workspace, settings: { ...settings, regions: event.target.value } })} />
        <button type="button" className="secondary" disabled={busy} onClick={() => run(async () => onRefresh((await api.updateWorkspace(workspace)).workspace))}>Save workspace settings</button>
        <button type="button" className="secondary" disabled={busy || !collectionId} onClick={() => run(async () => onRefresh((await api.forkCollection(collectionId)).workspace))}>Fork selected collection</button>
        <button type="button" className="secondary" disabled={busy || !collectionId} onClick={() => run(async () => {
          const result = await api.standaloneMock(collectionId)
          setMessage(`Standalone mock is running from ${result.file}`)
        })}>Export and start standalone mock</button>
        <button type="button" className="secondary" disabled={busy} onClick={() => run(async () => {
          const started = await api.startGrpcMock(50051)
          setMessage(`gRPC example server on port ${started.port}`)
        })}>Start gRPC mock</button>
        <button type="button" className="secondary" disabled={busy || !collectionId} onClick={() => run(async () => onRefresh((await api.applyModel(collectionId, '{"id":1,"name":"Ada"}')).workspace))}>Create request model from example</button>
      </div>
      <div className="section-card stack">
        <strong>Collection pull request</strong>
        <p className="muted">Open a review from a forked collection into another collection in this workspace.</p>
        <input value={pullTitle} onChange={(event) => setPullTitle(event.target.value)} />
        <Select value={targetId} onChange={setTargetId} options={[{ value: '', label: 'Target collection' }, ...(workspace.collections || []).filter((item) => item.id !== collectionId).map((item) => ({ value: item.id, label: item.name }))]} />
        <button type="button" className="send" disabled={busy || !collectionId || !targetId} onClick={() => run(async () => onRefresh((await api.openPull({ sourceId: collectionId, targetId, title: pullTitle })).workspace))}>Open pull request</button>
        {(workspace.pullRequests || []).map((request) => (
          <div key={request.id} className="row">
            <span>{request.title} · {request.status}</span>
            {request.status === 'open' && <button type="button" className="secondary" disabled={busy} onClick={() => run(async () => onRefresh((await api.mergePull(request.id)).workspace))}>Merge</button>}
          </div>
        ))}
      </div>
    </div>
  )
}
