import { useEffect, useRef, useState } from 'react'
import { api, findCollection, findEnvironment, findRequest, globalEnvironments, localEnvironments, requestSnapshot, secretValues, variableMap } from './api.js'
import { matchKeybinding } from './components/KeybindingsEditor.jsx'
import { CollectionEditor } from './components/CollectionEditor.jsx'
import { EnvironmentEditor } from './components/EnvironmentEditor.jsx'
import { FolderEditor } from './components/FolderEditor.jsx'
import { AppsView } from './components/AppsView.jsx'
import { AiPanel } from './components/AiPanel.jsx'
import { GitView } from './components/GitView.jsx'
import { WorkspaceEditor } from './components/WorkspaceEditor.jsx'
import { RequestEditor } from './components/RequestEditor.jsx'
import { RunDialog } from './components/RunDialog.jsx'
import { RunView } from './components/RunView.jsx'
import { Sidebar } from './components/Sidebar.jsx'
import { ResizableSidebar } from './components/ResizableSidebar.jsx'
import { PromptDialog } from './components/PromptDialog.jsx'
import { Select } from './components/Select.jsx'
import { MockLogPanel } from './components/MockLogPanel.jsx'
import { MockRulesPanel } from './components/MockRulesPanel.jsx'
import { ImportWizard } from './components/ImportWizard.jsx'
import { PlatformHub } from './components/PlatformHub.jsx'
import { CookieEditor } from './components/CookieEditor.jsx'
import { unresolvedVariables } from './utils/promptVars.js'

export default function App() {
  const [workspace, setWorkspace] = useState({ collections: [], environments: [], history: [] })
  const [selection, setSelection] = useState(null)
  const [draft, setDraft] = useState(null)
  const [tabs, setTabs] = useState([])
  const [activeEnvId, setActiveEnvId] = useState('')
  const [activeGlobalEnvId, setActiveGlobalEnvId] = useState('')
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [response, setResponse] = useState(null)
  const [runReport, setRunReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [banner, setBanner] = useState('')
  const [sending, setSending] = useState(false)
  const [saving, setSaving] = useState(false)
  const [runDialog, setRunDialog] = useState(null)
  const [mockCollectionId, setMockCollectionId] = useState('')
  const [promptNames, setPromptNames] = useState(null)
  const [showMockLog, setShowMockLog] = useState(false)
  const [showMockRules, setShowMockRules] = useState(null)
  const [showCookieEditor, setShowCookieEditor] = useState(false)
  const [showImportWizard, setShowImportWizard] = useState(false)

  const draftRef = useRef(null)
  const selectionRef = useRef(null)
  const sendRef = useRef(() => {})
  const saveRef = useRef(() => {})
  const newRequestRef = useRef(() => {})
  useEffect(() => { draftRef.current = draft }, [draft])
  useEffect(() => { selectionRef.current = selection }, [selection])

  useEffect(() => {
    function onKeyDown(event) {
      const target = event.target
      if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target?.isContentEditable) {
        if (!event.metaKey && !event.ctrlKey) return
      }
      const bindings = workspace.settings?.keybindings || {}
      const sendBinding = bindings.send || workspace.settings?.sendKeybinding || 'mod+Enter'
      if (matchKeybinding(event, sendBinding) && selectionRef.current?.kind === 'request') {
        event.preventDefault()
        sendRef.current()
        return
      }
      if (matchKeybinding(event, bindings.save) && selectionRef.current?.kind === 'request') {
        event.preventDefault()
        saveRef.current()
        return
      }
      if (matchKeybinding(event, bindings.newRequest)) {
        event.preventDefault()
        newRequestRef.current()
        return
      }
      if (matchKeybinding(event, bindings.toggleSidebar)) {
        event.preventDefault()
        setSidebarCollapsed((current) => !current)
        return
      }
      if (matchKeybinding(event, bindings.search)) {
        event.preventDefault()
        document.querySelector('.sidebar-search input')?.focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [workspace.settings])

  useEffect(() => {
    function onMessage(event) {
      if (event.origin !== 'http://localhost:8080') return
      if (!event.data || event.data.type !== 'apiflow-oauth' || !event.data.token) return
      setDraft((current) => current ? {
        ...current,
        extras: {
          ...(current.extras || {}),
          oauthAccessToken: event.data.token,
          oauthRefreshToken: event.data.refresh || '',
          oauthExpiresAt: event.data.expiresAt || 0,
        },
      } : current)
      setBanner('OAuth token received. Save the request to keep it.')
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = workspace.settings?.theme || 'dark'
  }, [workspace.settings?.theme])

  useEffect(() => {
    function onOpenApps(event) {
      setSelection({ kind: 'apps', scopeCollection: event.detail?.collection || '' })
      setDraft(null)
      setResponse(null)
    }
    window.addEventListener('apiflow-open-apps', onOpenApps)
    return () => window.removeEventListener('apiflow-open-apps', onOpenApps)
  }, [])

  useEffect(() => {
    api.workspace()
      .then((data) => {
        setWorkspace(data)
        const firstLocal = localEnvironments(data)[0]
        const firstGlobal = globalEnvironments(data)[0]
        if (firstLocal) setActiveEnvId(firstLocal.id)
        if (firstGlobal) setActiveGlobalEnvId(firstGlobal.id)
        const first = data.collections?.[0]?.requests?.[0]
        if (first) {
          setTabs([{ collectionId: data.collections[0].id, requestId: first.id }])
          setSelection({ kind: 'request', collectionId: data.collections[0].id, requestId: first.id })
          setDraft(structuredClone(first))
        }
      })
      .catch((error) => setBanner(error.message))
      .finally(() => setLoading(false))
  }, [])

  const activeEnvironment = findEnvironment(workspace, activeEnvId)
  const activeGlobalEnvironment = findEnvironment(workspace, activeGlobalEnvId)
  const activeCollection = findCollection(workspace, selection?.collectionId)

  async function persistCurrent() {
    const current = draftRef.current
    const selected = selectionRef.current
    if (!current || !selected) return null
    if (selected.kind === 'request') {
      if (selected.adhoc) return (await api.updateAdhocRequest(current.id, current)).workspace
      return (await api.updateRequest(current.id, current)).workspace
    }
    if (selected.kind === 'environment') return (await api.updateEnvironment(current.id, current)).workspace
    if (selected.kind === 'collection') return (await api.updateCollection(current.id, current)).workspace
    if (selected.kind === 'folder') return (await api.updateFolder(current.id, current)).workspace
    return null
  }

  async function run(action) {
    setBanner('')
    try {
      await action()
    } catch (error) {
      setBanner(error.message)
    }
  }

  function openRequest(collectionId, request, nextWorkspace = workspace, adhoc = false) {
    setWorkspace(nextWorkspace)
    setTabs((current) => current.some((tab) => tab.requestId === request.id) ? current : [...current, { collectionId: collectionId || '', requestId: request.id, adhoc }])
    setSelection({ kind: 'request', collectionId: collectionId || '', requestId: request.id, adhoc })
    setDraft(structuredClone(request))
    setResponse(null)
    setRunReport(null)
  }

  async function openAdhocTab() {
    const result = await api.createAdhocRequest()
    const request = findRequest(result.workspace, result.focusId)?.request
    if (request) openRequest('', request, result.workspace, true)
    else setWorkspace(result.workspace)
  }

  newRequestRef.current = () => {
    run(openAdhocTab)
  }

  function closeTab(requestId, adhoc = false) {
    setTabs((current) => current.filter((tab) => tab.requestId !== requestId))
    if (selection?.requestId === requestId) {
      setSelection(null)
      setDraft(null)
      setResponse(null)
    }
    if (adhoc) run(async () => setWorkspace((await api.deleteAdhocRequest(requestId)).workspace))
  }

  function sendRequest() {
    const currentDraft = draftRef.current
    const currentSelection = selectionRef.current
    if (!currentDraft || currentSelection?.kind !== 'request') return
    const vars = variableMap(workspace, activeEnvironment, activeCollection, currentDraft, activeGlobalEnvironment)
    const missing = unresolvedVariables(currentDraft, vars)
    if (missing.length) {
      setPromptNames(missing)
      return
    }
    run(async () => {
      setSending(true)
      try {
        if (currentSelection.adhoc) setWorkspace((await api.updateAdhocRequest(currentDraft.id, currentDraft)).workspace)
        else setWorkspace((await api.updateRequest(currentDraft.id, currentDraft)).workspace)
        if (currentSelection.collectionId) {
          const validation = await api.validateRequest(currentSelection.collectionId, currentDraft)
          if (!validation.valid) {
            const message = (validation.issues || []).map((issue) => `${issue.field}: ${issue.message}`).join('\n')
            if (!window.confirm(`Typed validation failed:\n${message}\n\nSend anyway?`)) return
          }
        }
        setResponse(await api.execute(currentDraft, activeEnvId, currentSelection.collectionId || '', {}, activeGlobalEnvId))
        setWorkspace(await api.workspace())
      } finally { setSending(false) }
    })
  }

  function saveRequest(next) {
    const currentDraft = next || draftRef.current
    const currentSelection = selectionRef.current
    if (!currentDraft || currentSelection?.kind !== 'request') return
    run(async () => {
      setSaving(true)
      try {
        if (currentSelection.adhoc) setWorkspace((await api.updateAdhocRequest(currentDraft.id, currentDraft)).workspace)
        else setWorkspace((await api.updateRequest(currentDraft.id, currentDraft)).workspace)
        if (next) setDraft(currentDraft)
      } finally { setSaving(false) }
    })
  }

  sendRef.current = sendRequest
  saveRef.current = () => saveRequest()

  return (
    <div className="app">
      <ResizableSidebar collapsed={sidebarCollapsed}>
      {!sidebarCollapsed && <Sidebar
        workspace={workspace}
        selection={selection}
        onSelectRequest={(collectionId, request) => run(async () => {
          const saved = await persistCurrent()
          const nextWorkspace = saved || workspace
          const fresh = saved ? findRequest(saved, request.id) : null
          openRequest(fresh?.collection.id || collectionId, fresh?.request || request, nextWorkspace)
        })}
        onSelectCollection={(collection) => run(async () => {
          const saved = await persistCurrent()
          if (saved) setWorkspace(saved)
          setSelection({ kind: 'collection', collectionId: collection.id })
          setDraft(structuredClone(collection))
          setResponse(null)
        })}
        onSelectFolder={(collection, folder) => run(async () => {
          const saved = await persistCurrent()
          if (saved) setWorkspace(saved)
          setSelection({ kind: 'folder', collectionId: collection.id, folderId: folder.id })
          setDraft(structuredClone(folder))
          setResponse(null)
        })}
        onSelectEnvironment={(environment) => run(async () => {
          const saved = await persistCurrent()
          if (saved) setWorkspace(saved)
          setSelection({ kind: 'environment', id: environment.id })
          setDraft(structuredClone(environment))
          setResponse(null)
        })}
        onCreateCollection={() => run(async () => setWorkspace((await api.createCollection('New Collection')).workspace))}
        onRenameCollection={(id, name) => run(async () => setWorkspace((await api.renameCollection(id, name)).workspace))}
        onDeleteCollection={(collection) => run(async () => {
          if (!window.confirm(`Delete collection "${collection.name}"?`)) return
          setWorkspace((await api.deleteCollection(collection.id)).workspace)
          setTabs((current) => current.filter((tab) => tab.collectionId !== collection.id))
          if (selection?.collectionId === collection.id) { setSelection(null); setDraft(null) }
        })}
        onCreateRequest={(collectionId, folderId) => run(async () => {
          await persistCurrent()
          const result = await api.createRequest(collectionId, 'New Request', folderId)
          const found = findRequest(result.workspace, result.focusId)
          if (found) openRequest(found.collection.id, found.request, result.workspace)
        })}
        onDeleteRequest={(request) => run(async () => {
          if (!window.confirm(`Delete request "${request.name}"?`)) return
          setWorkspace((await api.deleteRequest(request.id)).workspace)
          closeTab(request.id)
        })}
        onDuplicateRequest={(request) => run(async () => {
          const result = await api.duplicateRequest(request.id)
          const found = findRequest(result.workspace, result.focusId)
          if (found) openRequest(found.collection.id, found.request, result.workspace)
        })}
        onRenameRequest={(request, name) => run(async () => setWorkspace((await api.updateRequest(request.id, { ...request, name })).workspace))}
        onCreateFolder={(collectionId, parentId) => run(async () => setWorkspace((await api.createFolder(collectionId, 'New Folder', parentId)).workspace))}
        onDeleteFolder={(folder) => run(async () => {
          if (!window.confirm(`Delete folder "${folder.name}"?`)) return
          setWorkspace((await api.deleteFolder(folder.id)).workspace)
        })}
        onRenameFolder={(id, name) => run(async () => setWorkspace((await api.renameFolder(id, name)).workspace))}
        onReorder={(collectionId, folderId, requestIds) => run(async () => setWorkspace((await api.reorder(collectionId, folderId, requestIds)).workspace))}
        onRunCollection={(collection) => setRunDialog({ collectionId: collection.id, folderId: '', name: collection.name })}
        onRunFolder={(collection, folder) => setRunDialog({ collectionId: collection.id, folderId: folder.id, name: folder.name })}
        onExportCollection={(collection, format) => run(async () => {
          let text = ''
          let mime = 'application/json'
          let filename = `${collection.name || 'collection'}.json`
          if (format === 'bruno') {
            text = await api.exportBruno(collection.id)
            mime = 'text/plain'
            filename = `${collection.name || 'collection'}.bru`
          } else if (format === 'postman') {
            text = await api.exportPostman(collection.id)
            filename = `${collection.name || 'collection'}.postman.json`
          } else if (format === 'openapi') {
            text = await api.exportOpenApi(collection.id)
            filename = `${collection.name || 'collection'}.openapi.json`
          } else if (format === 'opencollection') {
            text = await api.exportOpenCollection(collection.id)
            mime = 'text/yaml'
            filename = `${collection.name || 'collection'}.opencollection.yaml`
          } else if (format === 'bruno-folder') {
            const files = await api.exportBrunoFolder(collection.id)
            text = JSON.stringify({ files: files.map((file) => ({ path: file.path, content: file.content })) }, null, 2)
            filename = `${collection.name || 'collection'}.bruno-folder.json`
          } else if (format === 'docs') {
            text = await api.collectionDocsHtml(collection.id)
            mime = 'text/html'
            filename = `${collection.name || 'collection'}-docs.html`
          } else {
            const data = await api.exportCollection(collection.id)
            text = JSON.stringify(data, null, 2)
          }
          const blob = new Blob([text], { type: mime })
          const link = document.createElement('a')
          link.href = URL.createObjectURL(blob)
          link.download = filename
          link.click()
          URL.revokeObjectURL(link.href)
        })}
        onExportWorkspace={() => run(async () => {
          const data = await api.exportWorkspace()
          const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
          const link = document.createElement('a')
          link.href = URL.createObjectURL(blob)
          link.download = 'workspace.json'
          link.click()
          URL.revokeObjectURL(link.href)
        })}
        onExportEnvironment={(environment) => run(async () => {
          const text = await api.exportEnvironment(environment.id)
          const blob = new Blob([text], { type: 'text/plain' })
          const link = document.createElement('a')
          link.href = URL.createObjectURL(blob)
          link.download = `${environment.name || 'environment'}.bru`
          link.click()
          URL.revokeObjectURL(link.href)
        })}
        onOpenMockLog={() => setShowMockLog(true)}
        onAddCookie={() => setShowCookieEditor(true)}
        onImport={(content) => run(async () => setWorkspace((await api.importDocument(content)).workspace))}
        onOpenImportWizard={() => setShowImportWizard(true)}
        onPasteCurl={(content) => run(async () => {
          const collectionId = selection?.collectionId || workspace.collections?.[0]?.id
          if (!collectionId) {
            const created = await api.createCollection('Imported curl')
            const id = created.focusId
            const result = await api.importCurl(id, content, '')
            const found = findRequest(result.workspace, result.focusId)
            if (found) openRequest(found.collection.id, found.request, result.workspace)
            else setWorkspace(result.workspace)
            return
          }
          const result = await api.importCurl(collectionId, content, selection?.kind === 'folder' ? selection.folderId : '')
          const found = findRequest(result.workspace, result.focusId)
          if (found) openRequest(found.collection.id, found.request, result.workspace)
          else setWorkspace(result.workspace)
        })}
        onCreateEnvironment={() => run(async () => {
          await persistCurrent()
          const result = await api.createEnvironment('New Environment')
          const environment = findEnvironment(result.workspace, result.focusId)
          setWorkspace(result.workspace)
          setActiveEnvId(result.focusId)
          if (environment) {
            setSelection({ kind: 'environment', id: environment.id })
            setDraft(structuredClone(environment))
          }
        })}
        onDeleteEnvironment={(environment) => run(async () => {
          if (!window.confirm(`Delete environment "${environment.name}"?`)) return
          const result = await api.deleteEnvironment(environment.id)
          setWorkspace(result.workspace)
          if (activeEnvId === environment.id) setActiveEnvId(result.workspace.environments[0]?.id || '')
          if (selection?.id === environment.id) { setSelection(null); setDraft(null) }
        })}
        onOpenHistory={(entry) => run(async () => {
          const saved = await persistCurrent()
          const nextWorkspace = saved || workspace
          const found = findRequest(nextWorkspace, entry.requestId)
          if (!found) {
            setBanner('That request is no longer in a collection.')
            return
          }
          openRequest(found.collection.id, found.request, nextWorkspace)
        })}
        onClearHistory={() => run(async () => setWorkspace((await api.clearHistory()).workspace))}
        onClearCookies={() => run(async () => setWorkspace((await api.clearCookies()).workspace))}
        onDeleteCookie={(cookie) => run(async () => setWorkspace((await api.deleteCookie(cookie.domain, cookie.name)).workspace))}
        onImportBruno={(files) => run(async () => setWorkspace((await api.importBruno(files)).workspace))}
        onOpenGit={() => { setSelection({ kind: 'git' }); setDraft(null); setResponse(null) }}
        onOpenApps={() => { setSelection({ kind: 'apps' }); setDraft(null); setResponse(null) }}
        onOpenPlatform={() => { setSelection({ kind: 'platform' }); setDraft(null); setResponse(null) }}
        onSelectDocument={(doc) => {
          setSelection({ kind: 'document', documentId: doc.id })
          setDraft(structuredClone(doc))
          setResponse(null)
        }}
        onToggleMock={(collection) => run(async () => {
          if (mockCollectionId === collection.id) {
            await api.stopMock()
            setMockCollectionId('')
            setBanner('Mock server stopped.')
            return
          }
          setShowMockRules(collection)
        })}
        mockRunning={mockCollectionId}
      />}
      </ResizableSidebar>

      <main className="main">
        <header className="topbar">
          <div className="topbar-start">
            <span className="top-breadcrumb">{activeCollection?.name || (selection?.kind === 'git' ? 'Git' : selection?.kind === 'workspace' ? 'Workspace' : 'ApiFlow')}</span>
            <span className="top-sub">Local-first · collections stay on this machine</span>
          </div>
          <div className="topbar-end">
            {globalEnvironments(workspace).length > 0 && (
              <label className="env-select pill">
                Global
                <Select
                  value={activeGlobalEnvId}
                  onChange={setActiveGlobalEnvId}
                  options={[
                    { value: '', label: 'No global env' },
                    ...globalEnvironments(workspace).map((environment) => ({ value: environment.id, label: environment.name })),
                  ]}
                />
              </label>
            )}
            <label className="env-select pill">
              Environment
              <Select
                value={activeEnvId}
                onChange={setActiveEnvId}
                options={[
                  { value: '', label: 'No environment' },
                  ...localEnvironments(workspace).map((environment) => ({ value: environment.id, label: environment.name })),
                ]}
              />
            </label>
            <div className="topbar-actions">
              <button type="button" className="ghost-button" onClick={() => { setSelection({ kind: 'workspace' }); setDraft(null); setResponse(null) }}>Workspace</button>
              <button type="button" className="ghost-button accent" onClick={() => { setSelection({ kind: 'ai' }); setDraft(null); setResponse(null) }}>AI</button>
            </div>
          </div>
        </header>
        {(tabs.length > 0 || selection?.kind === 'request') && (
          <div className="request-tabs">
            {tabs.map((tab) => {
              const found = findRequest(workspace, tab.requestId)
              const dirty = selection?.requestId === tab.requestId && requestSnapshot(draft) !== requestSnapshot(found?.request)
              return (
                <button key={tab.requestId} type="button" className={selection?.requestId === tab.requestId ? 'active' : ''} onClick={() => run(async () => {
                  const saved = await persistCurrent()
                  const nextWorkspace = saved || workspace
                  const fresh = findRequest(nextWorkspace, tab.requestId)
                  if (fresh) openRequest(fresh.collection?.id || '', fresh.request, nextWorkspace, !!fresh.adhoc)
                })}>
                  {dirty && <i className="dirty-dot" />}
                  {found?.request.name || 'Request'}
                  {tab.adhoc && <span className="muted"> · unsaved</span>}
                  <span className="tab-close" role="button" tabIndex={0} aria-label="Close tab" onClick={(event) => { event.stopPropagation(); closeTab(tab.requestId, tab.adhoc) }} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); event.stopPropagation(); closeTab(tab.requestId, tab.adhoc) } }}>×</span>
                </button>
              )
            })}
            <button type="button" className="tab-add" title="New untitled request" onClick={() => run(openAdhocTab)}>+</button>
          </div>
        )}
        {banner && <div className="banner">{banner}</div>}
        {loading && <p className="empty">Loading workspace…</p>}
        {!loading && selection?.kind === 'request' && draft && (
          <>
            {selection.adhoc && (
              <div className="banner adhoc-banner">
                <span>Ad-hoc request — not saved to a collection.</span>
                <button type="button" className="secondary" onClick={() => run(async () => {
                  const collectionId = workspace.collections?.[0]?.id
                  if (!collectionId) {
                    setBanner('Create a collection first.')
                    return
                  }
                  await persistCurrent()
                  const result = await api.saveAdhocToCollection(draft.id, collectionId, '')
                  const found = findRequest(result.workspace, result.focusId)
                  setTabs((current) => current.filter((tab) => tab.requestId !== draft.id))
                  if (found) openRequest(found.collection.id, found.request, result.workspace, false)
                  else setWorkspace(result.workspace)
                  setBanner('Request saved to collection.')
                })}>Save to collection</button>
              </div>
            )}
            <RequestEditor
              draft={draft}
              variables={variableMap(workspace, activeEnvironment, activeCollection, draft, activeGlobalEnvironment)}
              secrets={secretValues(workspace, activeEnvironment, activeCollection)}
              collection={activeCollection}
              collectionId={selection?.collectionId || ''}
              environmentId={activeEnvId}
              datasets={workspace.datasets || []}
              performanceRuns={workspace.performanceRuns || []}
              sending={sending}
              saving={saving}
              response={response}
              onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
              onSave={(next) => saveRequest(next)}
              onSend={sendRequest}
              sendKeybinding={workspace.settings?.keybindings?.send || workspace.settings?.sendKeybinding || 'mod+Enter'}
            />
          </>
        )}
        {!loading && selection?.kind === 'environment' && draft && (
          <EnvironmentEditor
            draft={draft}
            saving={saving}
            onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
            onSave={() => run(async () => {
              setSaving(true)
              try { setWorkspace((await api.updateEnvironment(draft.id, draft)).workspace) } finally { setSaving(false) }
            })}
            onDelete={() => run(async () => {
              if (!window.confirm(`Delete environment "${draft.name}"?`)) return
              const result = await api.deleteEnvironment(draft.id)
              setWorkspace(result.workspace)
              setSelection(null)
              setDraft(null)
              if (activeEnvId === draft.id) setActiveEnvId(result.workspace.environments[0]?.id || '')
            })}
          />
        )}
        {!loading && selection?.kind === 'collection' && draft && (
          <CollectionEditor
            draft={draft}
            saving={saving}
            onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
            onRefresh={(next) => {
              setWorkspace(next)
              const collection = next.collections?.find((item) => item.id === draft.id)
              if (collection) setDraft(structuredClone(collection))
            }}
            onSave={() => run(async () => {
              setSaving(true)
              try { setWorkspace((await api.updateCollection(draft.id, draft)).workspace) } finally { setSaving(false) }
            })}
          />
        )}
        {!loading && selection?.kind === 'folder' && draft && (
          <FolderEditor
            draft={draft}
            saving={saving}
            onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
            onSave={() => run(async () => {
              setSaving(true)
              try { setWorkspace((await api.updateFolder(draft.id, draft)).workspace) } finally { setSaving(false) }
            })}
          />
        )}
        {!loading && selection?.kind === 'document' && draft && (
          <div className="stack document-view">
            <h2>{draft.title}</h2>
            <pre className="markup">{draft.content}</pre>
          </div>
        )}
        {!loading && selection?.kind === 'run' && (
          <RunView
            report={runReport}
            name={activeCollection?.name || 'Collection'}
            history={(workspace.collectionRuns || []).filter((item) => !activeCollection || item.collectionId === activeCollection.id)}
            onComment={async (body) => {
              if (!runReport?.id) return
              const result = await api.addComment({ targetType: 'run', targetId: runReport.id, body })
              setWorkspace(result.workspace)
            }}
            comments={(workspace.comments || []).filter((item) => item.targetId === runReport?.id)}
          />
        )}
        {!loading && selection?.kind === 'workspace' && (
          <WorkspaceEditor
            workspace={workspace}
            onChange={(patch) => setWorkspace((current) => ({ ...current, ...patch, settings: patch.settings ? { ...(current.settings || {}), ...patch.settings } : current.settings }))}
            onSave={() => run(async () => {
              setSaving(true)
              try {
                setWorkspace((await api.updateWorkspace(workspace)).workspace)
                setBanner('Workspace saved.')
              } finally { setSaving(false) }
            })}
            onExportBundle={() => run(async () => {
              const data = await api.exportWorkspaceBundle()
              const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
              const link = document.createElement('a')
              link.href = URL.createObjectURL(blob)
              link.download = 'workspace-bundle.json'
              link.click()
              URL.revokeObjectURL(link.href)
            })}
            onImportBundle={(file) => run(async () => {
              const content = await file.text()
              setWorkspace((await api.importWorkspaceBundle(content)).workspace)
              setBanner('Workspace bundle imported.')
            })}
            onLoadProfile={(next) => {
              setWorkspace(next)
              setSelection(null)
              setDraft(null)
              setBanner('Workspace profile loaded.')
            }}
            saving={saving}
          />
        )}
        {!loading && selection?.kind === 'git' && <GitView />}
        {!loading && selection?.kind === 'platform' && (
          <PlatformHub workspace={workspace} onRefresh={(next) => setWorkspace(next)} />
        )}
        {!loading && selection?.kind === 'apps' && (
          <AppsView
            workspace={workspace}
            environmentId={activeEnvId}
            scopeCollection={selection?.scopeCollection || activeCollection?.name}
            scopeRequest={selection?.kind === 'request' ? draft?.name : ''}
            onRefresh={() => run(async () => setWorkspace(await api.workspace()))}
          />
        )}
        {!loading && selection?.kind === 'ai' && (
          <AiPanel
            context={draft ? JSON.stringify({ name: draft.name, method: draft.method, url: draft.url }, null, 2) : ''}
            onInsert={(script) => {
              if (selection?.kind !== 'request' || !draft) return
              setDraft((current) => ({ ...current, postResponseScript: `${current.postResponseScript || ''}\n${script}`.trim() }))
            }}
          />
        )}
        {runDialog && (
          <RunDialog
            name={runDialog.name}
            collectionId={runDialog.collectionId}
            workspace={workspace}
            onCancel={() => setRunDialog(null)}
            onRun={({ stopOnFailure, dataCsv, parallel, delayMs, tags, globalEnvironmentId, datasetId, keepVariables, ignoreCookies, saveCookies, mockBaseUrl, shareResults, quietLogs, requestIds }) => run(async () => {
              const dialog = runDialog
              setRunDialog(null)
              await persistCurrent()
              const report = await api.runCollection(dialog.collectionId, activeEnvId, dialog.folderId, stopOnFailure, dataCsv, parallel, delayMs, tags, globalEnvironmentId || activeGlobalEnvId, datasetId, { keepVariables, ignoreCookies, saveCookies, mockBaseUrl, shareResults, quietLogs, requestIds })
              setWorkspace(await api.workspace())
              setRunReport(report)
              setSelection({ kind: 'run', collectionId: dialog.collectionId })
              setDraft(null)
            })}
          />
        )}
        {!loading && !selection && (
          <div className="empty">
            <h1>Call an API</h1>
            <p>Create a collection, add a request, then send it. Nothing is synced to a cloud account.</p>
          </div>
        )}
        {promptNames && (
          <PromptDialog
            names={promptNames}
            onCancel={() => setPromptNames(null)}
            onSubmit={(promptVars) => {
              setPromptNames(null)
              run(async () => {
                setSending(true)
                try {
                  const mergedPromptVars = { ...(draft.extras?.promptVars || []).reduce((acc, row) => {
                    if (row.enabled !== false && row.key) acc[row.key] = row.value || ''
                    return acc
                  }, {}), ...promptVars }
                  if (selection.adhoc) setWorkspace((await api.updateAdhocRequest(draft.id, draft)).workspace)
                  else setWorkspace((await api.updateRequest(draft.id, draft)).workspace)
                  setResponse(await api.execute(draft, activeEnvId, selection.collectionId || '', mergedPromptVars, activeGlobalEnvId))
                  setWorkspace(await api.workspace())
                } finally { setSending(false) }
              })
            }}
          />
        )}
        <MockLogPanel open={showMockLog} onClose={() => setShowMockLog(false)} />
        {showImportWizard && (
          <ImportWizard
            onClose={() => setShowImportWizard(false)}
            onImported={(next) => {
              setWorkspace(next)
              setBanner('Postman collection imported.')
              setShowImportWizard(false)
            }}
          />
        )}
        {showMockRules && (
          <MockRulesPanel
            collection={showMockRules}
            mockRunning={mockCollectionId === showMockRules.id}
            onClose={() => setShowMockRules(null)}
            onStarted={(state) => {
              setMockCollectionId(showMockRules.id)
              setBanner(`Mock server listening on http://127.0.0.1:${state.port}`)
              setShowMockRules(null)
            }}
            onResync={() => run(async () => {
              const state = await api.resyncMock(showMockRules.id)
              setBanner(`Mock re-synced on http://127.0.0.1:${state.port}`)
            })}
          />
        )}
        <CookieEditor
          open={showCookieEditor}
          onCancel={() => setShowCookieEditor(false)}
          onSave={(cookie) => run(async () => {
            setWorkspace((await api.addCookie(cookie)).workspace)
            setShowCookieEditor(false)
          })}
        />
      </main>
    </div>
  )
}
