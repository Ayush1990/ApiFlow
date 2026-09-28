import { useEffect, useState } from 'react'
import { api } from '../api.js'

export function PerformanceTestDialog({ collectionId, requestId, requestName, environmentId, datasets = [], history = [], onClose }) {
  const [scope, setScope] = useState('request')
  const [virtualUsers, setVirtualUsers] = useState(5)
  const [iterations, setIterations] = useState(3)
  const [rampUpMs, setRampUpMs] = useState(500)
  const [datasetId, setDatasetId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  const [liveId, setLiveId] = useState('')
  const [compare, setCompare] = useState(null)
  const [leftId, setLeftId] = useState('')
  const [rightId, setRightId] = useState('')

  async function runTest(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const started = await api.startPerformance({
        collectionId,
        requestId: scope === 'collection' ? '' : requestId,
        environmentId: environmentId || '',
        virtualUsers: Number(virtualUsers) || 1,
        iterations: Number(iterations) || 1,
        rampUpMs: Number(rampUpMs) || 0,
        datasetId,
      })
      setResult(started)
      setLiveId(started.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    if (!liveId || result?.status === 'completed') return undefined
    const timer = setInterval(async () => {
      try {
        const live = await api.livePerformance(liveId)
        setResult(live)
        if (live.status === 'completed') clearInterval(timer)
      } catch {
        clearInterval(timer)
      }
    }, 800)
    return () => clearInterval(timer)
  }, [liveId, result?.status])

  return (
    <div className="modal-back" onClick={onClose}>
      <form className="modal perf-modal" onClick={(event) => event.stopPropagation()} onSubmit={runTest}>
        <h2>Performance test — {scope === 'collection' ? 'Collection' : (requestName || 'Request')}</h2>
        <p className="muted">Each virtual user repeats the selected requests. A dataset assigns one row per virtual user.</p>
        <label>
          Scope
          <select value={scope} onChange={(event) => setScope(event.target.value)}>
            <option value="request">This request</option>
            <option value="collection">Whole collection</option>
          </select>
        </label>
        <label>
          Virtual users
          <input type="number" min="1" max="50" value={virtualUsers} onChange={(event) => setVirtualUsers(event.target.value)} />
        </label>
        <label>
          Iterations per user
          <input type="number" min="1" max="1000" value={iterations} onChange={(event) => setIterations(event.target.value)} />
        </label>
        <label>
          Ramp-up (ms)
          <input type="number" min="0" value={rampUpMs} onChange={(event) => setRampUpMs(event.target.value)} />
        </label>
        {datasets.length > 0 && (
          <label>
            Per-user dataset
            <select value={datasetId} onChange={(event) => setDatasetId(event.target.value)}>
              <option value="">Same data for every user</option>
              {datasets.map((dataset) => <option key={dataset.id} value={dataset.id}>{dataset.name}</option>)}
            </select>
          </label>
        )}
        {error && <p className="call-error">{error}</p>}
        {result && (
          <div className="section-card stack">
            <div className="row">
              <span><strong>{result.success}</strong> ok</span>
              <span><strong>{result.failure}</strong> failed</span>
              <span><strong>{Math.round(result.requestsPerSecond || 0)}</strong> req/s</span>
              <span><strong>{result.durationMs}</strong> ms total</span>
            </div>
            <div className="row">
              <span>p50: {result.p50Ms}ms</span>
              <span>p95: {result.p95Ms}ms</span>
              <span>p99: {result.p99Ms}ms</span>
              <span>{result.status || 'completed'}</span>
            </div>
            {(result.failures || []).length > 0 && (
              <div className="stack">
                <strong>Failures</strong>
                {result.failures.map((item, index) => <p key={index} className="call-error">{item}</p>)}
              </div>
            )}
            {(result.timeline || []).length > 0 && (
              <div className="stack">
                <strong>Virtual user timeline</strong>
                {result.timeline.slice(-12).map((item, index) => <p key={index} className="muted">{item}</p>)}
              </div>
            )}
            <p className="muted">Assertions this run: {result.assertionPass || 0} passed, {result.assertionFail || 0} failed.</p>
            <div className="chart">
              {[
                { label: 'p50', value: result.p50Ms || 0 },
                { label: 'p95', value: result.p95Ms || 0 },
                { label: 'p99', value: result.p99Ms || 0 },
              ].map((item) => (
                <div key={item.label} className="chart-row">
                  <span>{item.label}</span>
                  <div className="chart-bar"><i style={{ width: `${Math.min(100, item.value)}%` }} /></div>
                  <span>{item.value}ms</span>
                </div>
              ))}
            </div>
          </div>
        )}
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onClose}>Close</button>
          <button type="submit" className="send" disabled={busy || !collectionId}>{busy ? 'Running…' : 'Run test'}</button>
        </div>
        {history.length > 1 && (
          <div className="stack">
            <strong>Assertion trend</strong>
            {history.slice(0, 8).map((item) => {
              const total = (item.assertionPass || 0) + (item.assertionFail || 0)
              const percent = total === 0 ? 0 : Math.round((item.assertionPass || 0) * 100 / total)
              return (
                <div key={item.id} className="chart-row">
                  <span>{new Date(item.startedAt).toLocaleString()}</span>
                  <div className="chart-bar"><i style={{ width: `${percent}%` }} /></div>
                  <span>{percent}%</span>
                </div>
              )
            })}
          </div>
        )}
        {history.length > 1 && (
          <div className="stack">
            <strong>Compare saved runs</strong>
            <div className="url-row">
              <select value={leftId} onChange={(event) => setLeftId(event.target.value)}>
                <option value="">Baseline</option>
                {history.map((item) => <option key={item.id} value={item.id}>{new Date(item.startedAt).toLocaleString()} · {item.virtualUsers} VU</option>)}
              </select>
              <select value={rightId} onChange={(event) => setRightId(event.target.value)}>
                <option value="">Current</option>
                {history.map((item) => <option key={item.id} value={item.id}>{new Date(item.startedAt).toLocaleString()} · {item.requestsPerSecond?.toFixed?.(1) || item.requestsPerSecond} rps</option>)}
              </select>
              <button type="button" className="secondary" disabled={!leftId || !rightId} onClick={async () => setCompare(await api.comparePerformance(leftId, rightId))}>Compare</button>
            </div>
            {compare && <p className="muted">p95 {compare.p95DeltaMs} ms · throughput {Number(compare.rpsDelta).toFixed(2)} req/s · failures {compare.failureDelta}</p>}
          </div>
        )}
      </form>
    </div>
  )
}
