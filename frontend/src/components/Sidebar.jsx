import { useRef, useState } from 'react'

export function Sidebar({
  workspace,
  selection,
  onSelectRequest,
  onSelectCollection,
  onSelectFolder,
  onSelectEnvironment,
  onCreateCollection,
  onRenameCollection,
  onDeleteCollection,
  onCreateRequest,
  onDeleteRequest,
  onDuplicateRequest,
  onRenameRequest,
  onCreateFolder,
  onDeleteFolder,
  onRenameFolder,
  onReorder,
  onRunCollection,
  onRunFolder,
  onExportCollection,
  onExportWorkspace,
  onExportEnvironment,
  onOpenMockLog,
  onAddCookie,
  onImport,
  onOpenImportWizard,
  onPasteCurl,
  onCreateEnvironment,
  onDeleteEnvironment,
  onOpenHistory,
  onClearHistory,
  onClearCookies,
  onDeleteCookie,
  onImportBruno,
  onOpenGit,
  onOpenApps,
  onOpenPlatform,
  onSelectDocument,
  onToggleMock,
  mockRunning,
}) {
  const [open, setOpen] = useState({})
  const [search, setSearch] = useState('')
  const [editingId, setEditingId] = useState(null)
  const [editingName, setEditingName] = useState('')
  const [pasting, setPasting] = useState(false)
  const [curlText, setCurlText] = useState('')
  const [exportMenu, setExportMenu] = useState('')
  const [historyExpanded, setHistoryExpanded] = useState(false)
  const fileRef = useRef(null)
  const folderRef = useRef(null)
  const query = search.trim().toLowerCase()

  function finishRename(id, currentName, commit) {
    setEditingId(null)
    if (editingName.trim() && editingName.trim() !== currentName) commit(id, editingName.trim())
  }

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="mark">A</div>
        <div>
          <div className="brand-name">ApiFlow</div>
          <div className="brand-sub">Local API client</div>
        </div>
      </div>
      <div className="search-row">
        <div className="sidebar-search">
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search requests" aria-label="Search" />
        </div>
        <button type="button" className="icon" title="Paste curl" onClick={() => setPasting(true)}>⌘</button>
        <button type="button" className="icon" title="Import" onClick={() => fileRef.current?.click()}>↓</button>
        {onOpenImportWizard && <button type="button" className="icon" title="Postman migration wizard" onClick={onOpenImportWizard}>⇄</button>}
        <button type="button" className="icon" title="Import Bruno folder" onClick={() => folderRef.current?.click()}>▣</button>
        <input
          ref={folderRef}
          type="file"
          hidden
          multiple
          webkitdirectory=""
          onChange={async (event) => {
            const chosen = [...(event.target.files || [])]
            event.target.value = ''
            const files = []
            for (const file of chosen) {
              const path = file.webkitRelativePath || file.name
              if (!path.endsWith('.bru') && !path.endsWith('bruno.json')) continue
              files.push({ path, content: await file.text() })
            }
            if (files.length) onImportBruno(files)
          }}
        />
        <input
          ref={fileRef}
          type="file"
          hidden
          accept=".json,.yaml,.yml,.bru,.txt,.sh"
          onChange={async (event) => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (!file) return
            onImport(await file.text())
          }}
        />
      </div>
      {pasting && (
        <form className="paste-box card" onSubmit={(event) => { event.preventDefault(); onPasteCurl(curlText); setCurlText(''); setPasting(false) }}>
          <p className="card-title">Import from curl</p>
          <p className="card-desc">Paste a curl command to create a new request.</p>
          <textarea value={curlText} onChange={(event) => setCurlText(event.target.value)} placeholder="curl https://example.com/users" spellCheck={false} />
          <div className="modal-actions">
            <button type="button" className="secondary" onClick={() => setPasting(false)}>Cancel</button>
            <button type="submit" className="send">Add request</button>
          </div>
        </form>
      )}
      <div className="side-scroll">
        <div className="side-label">
          <span>Collections</span>
          <button type="button" className="icon" title="New collection" onClick={onCreateCollection}>+</button>
        </div>
        {(workspace.collections || []).map((collection) => {
          const expanded = open[collection.id] !== false
          const visible = !query || collection.name.toLowerCase().includes(query) || (collection.requests || []).some((request) => request.name.toLowerCase().includes(query))
          if (!visible) return null
          return (
            <div key={collection.id} className="collection">
              <div className="collection-row">
                <button type="button" className="chevron" onClick={() => setOpen((current) => ({ ...current, [collection.id]: !expanded }))}>{expanded ? '▾' : '▸'}</button>
                {editingId === collection.id ? (
                  <input
                    className="rename"
                    autoFocus
                    value={editingName}
                    onChange={(event) => setEditingName(event.target.value)}
                    onBlur={() => finishRename(collection.id, collection.name, onRenameCollection)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') event.currentTarget.blur()
                      if (event.key === 'Escape') setEditingId(null)
                    }}
                  />
                ) : (
                  <button type="button" className="collection-name" onClick={() => onSelectCollection(collection)} onDoubleClick={() => { setEditingId(collection.id); setEditingName(collection.name) }}>
                    {collection.name}
                  </button>
                )}
                <button type="button" className="icon" title="Run collection" onClick={() => onRunCollection(collection)}>▶</button>
                <button type="button" className="icon" title="Mock server" onClick={() => onToggleMock(collection)}>{mockRunning === collection.id ? '■' : '◇'}</button>
                <button type="button" className="icon" title="New folder" onClick={() => onCreateFolder(collection.id, '')}>▣</button>
                <button type="button" className="icon" title="New request" onClick={() => onCreateRequest(collection.id, '')}>+</button>
                <button type="button" className="icon" title="Export" onClick={() => setExportMenu(exportMenu === collection.id ? '' : collection.id)}>⇧</button>
                {exportMenu === collection.id && (
                  <div className="export-menu">
                    <button type="button" onClick={() => { onExportCollection(collection, 'json'); setExportMenu('') }}>ApiFlow JSON</button>
                    <button type="button" onClick={() => { onExportCollection(collection, 'bruno'); setExportMenu('') }}>Bruno (.bru)</button>
                    <button type="button" onClick={() => { onExportCollection(collection, 'bruno-folder'); setExportMenu('') }}>Bruno folder tree</button>
                    <button type="button" onClick={() => { onExportCollection(collection, 'postman'); setExportMenu('') }}>Postman</button>
                    <button type="button" onClick={() => { onExportCollection(collection, 'openapi'); setExportMenu('') }}>OpenAPI</button>
                    <button type="button" onClick={() => { onExportCollection(collection, 'opencollection'); setExportMenu('') }}>OpenCollection YAML</button>
                    <button type="button" onClick={() => { onExportCollection(collection, 'docs'); setExportMenu('') }}>HTML docs</button>
                  </div>
                )}
                <button type="button" className="icon danger" title="Delete collection" onClick={() => onDeleteCollection(collection)}>×</button>
              </div>
              {expanded && (
                <Tree
                  collection={collection}
                  parentId=""
                  query={query}
                  selection={selection}
                  editingId={editingId}
                  editingName={editingName}
                  setEditingName={setEditingName}
                  setEditingId={setEditingId}
                  onSelectRequest={onSelectRequest}
                  onSelectFolder={onSelectFolder}
                  onDeleteRequest={onDeleteRequest}
                  onDuplicateRequest={onDuplicateRequest}
                  onRenameRequest={onRenameRequest}
                  onCreateRequest={onCreateRequest}
                  onCreateFolder={onCreateFolder}
                  onDeleteFolder={onDeleteFolder}
                  onRenameFolder={onRenameFolder}
                  onReorder={onReorder}
                  onRunFolder={onRunFolder}
                />
              )}
            </div>
          )
        })}

        {(workspace.documents || []).some((doc) => doc.pinned) && (
          <>
            <div className="side-label"><span>Docs</span></div>
            {(workspace.documents || []).filter((doc) => doc.pinned).map((doc) => (
              <button
                key={doc.id}
                type="button"
                className={`doc-row ${selection?.kind === 'document' && selection.documentId === doc.id ? 'active' : ''}`}
                onClick={() => onSelectDocument?.(doc)}
              >
                {doc.title}
              </button>
            ))}
          </>
        )}

        <div className="side-label"><span>Workspace</span></div>
        <div className="side-actions">
          <button type="button" className="text-button" onClick={onExportWorkspace}>Export</button>
          <button type="button" className="text-button" onClick={onOpenMockLog}>Mock log</button>
          <button type="button" className="text-button" onClick={onOpenGit}>Git</button>
          <button type="button" className="text-button" onClick={onOpenApps}>Apps</button>
          {onOpenPlatform && <button type="button" className="text-button" onClick={onOpenPlatform}>Platform</button>}
        </div>
        <div className="side-label">
          <span>Environments</span>
          <button type="button" className="icon" title="New environment" onClick={onCreateEnvironment}>+</button>
        </div>
        {(workspace.environments || []).map((environment) => (
          <div key={environment.id} className={`env-row ${selection?.kind === 'environment' && selection.id === environment.id ? 'active' : ''}`}>
            <button type="button" className="env-name" onClick={() => onSelectEnvironment(environment)}>{environment.name}</button>
            <button type="button" className="icon" title="Export environment" onClick={() => onExportEnvironment(environment)}>⇧</button>
            <button type="button" className="icon danger" title="Delete environment" onClick={() => onDeleteEnvironment(environment)}>×</button>
          </div>
        ))}

        <div className="side-label">
          <span>Cookies</span>
          <button type="button" className="text-button" onClick={onAddCookie}>Add</button>
          {(workspace.cookies || []).length > 0 && <button type="button" className="text-button" onClick={onClearCookies}>Clear</button>}
        </div>
        {(workspace.cookies || []).map((cookie) => (
          <div key={`${cookie.domain}-${cookie.name}`} className="history-row">
            <span className="request-name">{cookie.name}</span>
            <span>{cookie.domain}</span>
            <button type="button" className="icon danger" title="Delete cookie" onClick={() => onDeleteCookie(cookie)}>×</button>
          </div>
        ))}

        <div className="side-label">
          <span>History</span>
          <span className="history-actions">
            {(workspace.history || []).length > 12 && (
              <button type="button" className="text-button" onClick={() => setHistoryExpanded((current) => !current)}>
                {historyExpanded ? 'Show less' : 'Show all'}
              </button>
            )}
            {(workspace.history || []).length > 0 && <button type="button" className="text-button" onClick={onClearHistory}>Clear</button>}
          </span>
        </div>
        {(workspace.history || []).slice(0, historyExpanded ? 100 : 12).map((entry) => (
          <button key={entry.id} type="button" className="history-row" onClick={() => onOpenHistory(entry)} title={entry.url || entry.requestName}>
            <span className={`verb verb-${entry.method}`}>{entry.method}</span>
            <span className="request-name">{entry.requestName || entry.url}</span>
            <span className="history-meta">{entry.at ? String(entry.at).slice(11, 16) : ''}</span>
            <span className={entry.ok ? 'status ok' : 'status err'}>{entry.status || '—'}</span>
          </button>
        ))}
      </div>
    </aside>
  )
}

function Tree({
  collection, parentId, query, selection, editingId, editingName, setEditingName, setEditingId,
  onSelectRequest, onSelectFolder, onDeleteRequest, onDuplicateRequest, onRenameRequest,
  onCreateRequest, onCreateFolder, onDeleteFolder, onRenameFolder, onReorder, onRunFolder,
}) {
  const folders = (collection.folders || [])
    .filter((folder) => (folder.parentId || '') === parentId)
    .sort((a, b) => a.position - b.position)
  const requests = (collection.requests || [])
    .filter((request) => (request.folderId || '') === parentId)
    .filter((request) => !query || request.name.toLowerCase().includes(query) || collection.name.toLowerCase().includes(query))
    .sort((a, b) => a.position - b.position)

  function dropRequest(event, targetId) {
    event.preventDefault()
    const draggedId = event.dataTransfer.getData('text/plain')
    if (!draggedId) return
    const ids = requests.map((request) => request.id).filter((id) => id !== draggedId)
    const index = targetId ? ids.indexOf(targetId) : ids.length
    ids.splice(index < 0 ? ids.length : index, 0, draggedId)
    onReorder(collection.id, parentId, ids)
  }

  return (
    <div>
      {folders.map((folder) => (
        <div key={folder.id} className="folder" onDragOver={(event) => event.preventDefault()} onDrop={(event) => {
          event.preventDefault()
          event.stopPropagation()
          const draggedId = event.dataTransfer.getData('text/plain')
          if (!draggedId) return
          const inside = (collection.requests || []).filter((request) => request.folderId === folder.id).map((request) => request.id)
          onReorder(collection.id, folder.id, [...inside, draggedId])
        }}>
          <div className={`folder-row ${selection?.kind === 'folder' && selection.folderId === folder.id ? 'active' : ''}`}>
            {editingId === folder.id ? (
              <input
                className="rename"
                autoFocus
                value={editingName}
                onChange={(event) => setEditingName(event.target.value)}
                onBlur={() => {
                  setEditingId(null)
                  if (editingName.trim() && editingName.trim() !== folder.name) onRenameFolder(folder.id, editingName.trim())
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') event.currentTarget.blur()
                  if (event.key === 'Escape') setEditingId(null)
                }}
              />
            ) : (
              <button type="button" className="collection-name" onClick={() => onSelectFolder(collection, folder)} onDoubleClick={() => { setEditingId(folder.id); setEditingName(folder.name) }}>
                ▣ {folder.name}
              </button>
            )}
            <button type="button" className="icon" title="Run folder" onClick={() => onRunFolder(collection, folder)}>▶</button>
            <button type="button" className="icon" title="New request" onClick={() => onCreateRequest(collection.id, folder.id)}>+</button>
            <button type="button" className="icon" title="New folder" onClick={() => onCreateFolder(collection.id, folder.id)}>▣</button>
            <button type="button" className="icon danger" title="Delete folder" onClick={() => onDeleteFolder(folder)}>×</button>
          </div>
          <Tree
            collection={collection}
            parentId={folder.id}
            query={query}
            selection={selection}
            editingId={editingId}
            editingName={editingName}
            setEditingName={setEditingName}
            setEditingId={setEditingId}
            onSelectRequest={onSelectRequest}
            onSelectFolder={onSelectFolder}
            onDeleteRequest={onDeleteRequest}
            onDuplicateRequest={onDuplicateRequest}
            onRenameRequest={onRenameRequest}
            onCreateRequest={onCreateRequest}
            onCreateFolder={onCreateFolder}
            onDeleteFolder={onDeleteFolder}
            onRenameFolder={onRenameFolder}
            onReorder={onReorder}
            onRunFolder={onRunFolder}
          />
        </div>
      ))}
      {requests.map((request) => {
        const active = selection?.kind === 'request' && selection.requestId === request.id
        return (
          <div
            key={request.id}
            className={`request-row ${active ? 'active' : ''}`}
            draggable
            onDragStart={(event) => event.dataTransfer.setData('text/plain', request.id)}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => dropRequest(event, request.id)}
          >
            {editingId === request.id ? (
              <input
                className="rename"
                autoFocus
                value={editingName}
                onChange={(event) => setEditingName(event.target.value)}
                onBlur={() => {
                  setEditingId(null)
                  if (editingName.trim() && editingName.trim() !== request.name) onRenameRequest(request, editingName.trim())
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') event.currentTarget.blur()
                  if (event.key === 'Escape') setEditingId(null)
                }}
              />
            ) : (
              <button type="button" className="request-main" onClick={() => onSelectRequest(collection.id, request)} onDoubleClick={() => { setEditingId(request.id); setEditingName(request.name) }}>
                <span className={`verb verb-${request.method}`}>{request.method}</span>
                <span className="request-name">{request.name}</span>
              </button>
            )}
            <button type="button" className="icon" title="Duplicate" onClick={() => onDuplicateRequest(request)}>⧉</button>
            <button type="button" className="icon danger" title="Delete request" onClick={() => onDeleteRequest(request)}>×</button>
          </div>
        )
      })}
    </div>
  )
}
