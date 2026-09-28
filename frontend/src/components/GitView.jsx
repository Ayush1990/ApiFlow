import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Select } from './Select.jsx'

export function GitView() {
  const [status, setStatus] = useState(null)
  const [conflicts, setConflicts] = useState([])
  const [message, setMessage] = useState('')
  const [remoteName, setRemoteName] = useState('origin')
  const [remoteUrl, setRemoteUrl] = useState('')
  const [branchName, setBranchName] = useState('')
  const [cloneUrl, setCloneUrl] = useState('')
  const [cloneDir, setCloneDir] = useState('clone')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [prs, setPrs] = useState([])
  const [prTitle, setPrTitle] = useState('')
  const [prBody, setPrBody] = useState('')
  const [prHead, setPrHead] = useState('')
  const [prBase, setPrBase] = useState('main')

  async function load() {
    setError('')
    try {
      setStatus(await api.gitStatus())
      setConflicts(await api.gitConflicts())
      if (remoteUrl.trim()) {
        try { setPrs(await api.gitPullRequests(remoteUrl.trim())) } catch { setPrs([]) }
      }
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => { load() }, [])

  async function act(action) {
    setBusy(true)
    setError('')
    try {
      setStatus(await action())
      setConflicts(await api.gitConflicts())
      if (remoteUrl.trim()) {
        try { setPrs(await api.gitPullRequests(remoteUrl.trim())) } catch { setPrs([]) }
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const repoReady = !!status?.repo

  return (
    <div className="env-editor">
      <div className="page-header">
        <div className="toolbar-row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h2>Git</h2>
            <p className="muted">Sync the folder that holds your collections. Pull and push use the remote you choose — no force-push.</p>
          </div>
          {status && (
            <span className={`status-pill ${repoReady ? 'ok' : 'neutral'}`}>
              {repoReady ? `On ${status.branch || 'branch'}` : 'Not initialized'}
            </span>
          )}
        </div>
        {status?.path && <p className="muted"><code>{status.path}</code></p>}
      </div>

      <div className="page-body stack">
        {error && <p className="call-error">{error}</p>}

        {!repoReady && (
          <div className="card-grid">
            <div className="card">
              <p className="card-title">Initialize repository</p>
              <p className="card-desc">Create a new git repo in your data folder to start tracking collections locally.</p>
              <button type="button" className="secondary" disabled={busy} onClick={() => act(() => api.gitInit())}>Initialize git</button>
            </div>
            <div className="card">
              <p className="card-title">Clone existing repo</p>
              <p className="card-desc">Pull a remote repository into a subfolder under your data directory.</p>
              <div className="stack">
                <label>Clone URL<input value={cloneUrl} onChange={(event) => setCloneUrl(event.target.value)} placeholder="https://github.com/you/collections.git" /></label>
                <label>Folder name<input value={cloneDir} onChange={(event) => setCloneDir(event.target.value)} placeholder="clone" /></label>
                <button type="button" className="send" disabled={busy || !cloneUrl.trim()} onClick={() => act(() => api.gitClone(cloneUrl.trim(), cloneDir.trim()))}>Clone repository</button>
              </div>
            </div>
          </div>
        )}

        {repoReady && (
          <>
            <div className="card">
              <p className="card-title">Branch & remote</p>
              <div className="stack">
                <label>
                  Current branch
                  <Select
                    aria-label="Branch"
                    value={status.branch || ''}
                    onChange={(branch) => act(() => api.gitBranch(branch, false))}
                    disabled={busy}
                    options={[
                      ...(status.branches || []).map((branch) => ({ value: branch, label: branch })),
                      ...(status.branch && !(status.branches || []).includes(status.branch) ? [{ value: status.branch, label: status.branch }] : []),
                    ]}
                  />
                </label>
                <div className="url-row">
                  <input value={branchName} onChange={(event) => setBranchName(event.target.value)} placeholder="New branch name" aria-label="New branch" />
                  <button type="button" className="secondary" disabled={busy || !branchName.trim()} onClick={() => act(() => api.gitBranch(branchName.trim(), true)).then(() => setBranchName(''))}>Create branch</button>
                </div>
                <div className="toolbar-row">
                  <button type="button" className="ghost-button" disabled={busy} onClick={() => { setRemoteUrl('https://github.com/you/collections.git'); setRemoteName('origin') }}>GitHub</button>
                  <button type="button" className="ghost-button" disabled={busy} onClick={() => { setRemoteUrl('https://gitlab.com/you/collections.git'); setRemoteName('origin') }}>GitLab</button>
                  <button type="button" className="ghost-button" disabled={busy} onClick={() => { setRemoteUrl('https://bitbucket.org/you/collections.git'); setRemoteName('origin') }}>Bitbucket</button>
                </div>
                <div className="url-row">
                  <input value={remoteName} onChange={(event) => setRemoteName(event.target.value)} placeholder="origin" aria-label="Remote name" />
                  <input value={remoteUrl} onChange={(event) => setRemoteUrl(event.target.value)} placeholder="https://github.com/you/collections.git" aria-label="Remote URL" style={{ flex: 2 }} />
                  <button type="button" className="secondary" disabled={busy || !remoteName.trim() || !remoteUrl.trim()} onClick={() => act(() => api.gitRemote(remoteName.trim(), remoteUrl.trim()))}>Save remote</button>
                </div>
                {(status.remotes || []).length > 0 && <p className="muted">Remotes: {(status.remotes || []).join(', ')}</p>}
                <p className="muted">Add a Git provider token in Workspace → Enterprise auth for PR workflows.</p>
              </div>
            </div>

            <div className="card">
              <p className="card-title">Sync</p>
              <div className="toolbar-row">
                <button type="button" className="secondary" disabled={busy} onClick={() => act(() => api.gitFetch(remoteName))}>Fetch</button>
                <button type="button" className="secondary" disabled={busy} onClick={() => act(() => api.gitPull(remoteName))}>Pull</button>
                <button type="button" className="send" disabled={busy} onClick={() => act(() => api.gitPush(remoteName))}>Push</button>
              </div>
            </div>

            <div className="card">
              <p className="card-title">Commit changes</p>
              <form className="stack" onSubmit={(event) => { event.preventDefault(); act(() => api.gitCommit(message)).then(() => setMessage('')) }}>
                <input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Describe your changes…" aria-label="Commit message" />
                <button type="submit" className="send" disabled={busy || !message.trim()} style={{ alignSelf: 'flex-start' }}>Commit</button>
              </form>
            </div>
          </>
        )}

        {conflicts.length > 0 && (
          <div className="card">
            <p className="card-title">Merge conflicts ({conflicts.length})</p>
            {conflicts.map((file) => (
              <div key={file.path} className="stack">
                <strong>{file.path}</strong>
                {(file.blocks || []).map((block, index) => (
                  <div key={`${file.path}-${index}`} className="stack">
                    <pre className="markup">{'<<<<<<< ours\n' + (block.ours || '') + '\n=======\n' + (block.theirs || '') + '\n>>>>>>> theirs'}</pre>
                    <div className="toolbar-row">
                      <button type="button" className="secondary" disabled={busy} onClick={() => act(() => api.gitResolveConflict(file.path, index, 'ours'))}>Keep ours</button>
                      <button type="button" className="secondary" disabled={busy} onClick={() => act(() => api.gitResolveConflict(file.path, index, 'theirs'))}>Keep theirs</button>
                      <button type="button" className="secondary" disabled={busy} onClick={() => act(() => api.gitResolveConflict(file.path, index, 'both'))}>Keep both</button>
                    </div>
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}

        {repoReady && remoteUrl.trim() && (
          <div className="card">
            <p className="card-title">Pull requests</p>
            <div className="toolbar-row">
              <button type="button" className="secondary" disabled={busy} onClick={() => act(async () => { setPrs(await api.gitPullRequests(remoteUrl.trim())); return status })}>Refresh PRs</button>
              {remoteUrl.includes('github.com') && <a href={remoteUrl.replace(/\.git$/, '')} target="_blank" rel="noreferrer">Open on GitHub</a>}
            </div>
            {(prs || []).map((pr) => (
              <div key={pr.number} className="test-row">
                <a href={pr.url} target="_blank" rel="noreferrer">#{pr.number} {pr.title}</a>
                <span className="muted">{pr.head} → {pr.base}</span>
                <button type="button" className="secondary" disabled={busy} onClick={() => act(() => api.gitMergePr(remoteUrl.trim(), pr.number))}>Merge</button>
              </div>
            ))}
            <label>PR title<input value={prTitle} onChange={(event) => setPrTitle(event.target.value)} /></label>
            <label className="stack">PR body<textarea className="body" value={prBody} onChange={(event) => setPrBody(event.target.value)} /></label>
            <div className="url-row">
              <input value={prHead} onChange={(event) => setPrHead(event.target.value)} placeholder="Head branch" aria-label="Head branch" />
              <input value={prBase} onChange={(event) => setPrBase(event.target.value)} placeholder="Base branch" aria-label="Base branch" />
              <button type="button" className="secondary" disabled={busy || !prTitle.trim()} onClick={() => act(async () => {
                await api.gitCreatePr(remoteUrl.trim(), prTitle.trim(), prBody, prHead.trim() || status?.branch || '', prBase.trim())
                setPrs(await api.gitPullRequests(remoteUrl.trim()))
                return status
              })}>Create PR</button>
            </div>
          </div>
        )}

        {status && (
          <div className="card-grid">
            <div className="card">
              <p className="card-title">Working tree</p>
              <pre className="code-block">{status.output || 'No changes'}</pre>
            </div>
            {repoReady && (
              <div className="card">
                <p className="card-title">Diff</p>
                <pre className="code-block">{status.diff || 'No diff'}</pre>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
