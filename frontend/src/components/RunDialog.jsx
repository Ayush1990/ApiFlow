import { useEffect, useState } from 'react'
import { Select } from './Select.jsx'
import { api } from '../api.js'

const PRESET_KEY = 'apiflow-run-presets'

function loadPresets(collectionId) {
  try {
    const all = JSON.parse(localStorage.getItem(PRESET_KEY) || '{}')
    return all[collectionId] || []
  } catch {
    return []
  }
}

function savePresets(collectionId, presets) {
  try {
    const all = JSON.parse(localStorage.getItem(PRESET_KEY) || '{}')
    all[collectionId] = presets
    localStorage.setItem(PRESET_KEY, JSON.stringify(all))
  } catch {
    // ignore
  }
}

export function RunDialog({ name, collectionId, workspace, onCancel, onRun }) {
  const [stopOnFailure, setStopOnFailure] = useState(true)
  const [dataCsv, setDataCsv] = useState('')
  const [parallel, setParallel] = useState(false)
  const [delayMs, setDelayMs] = useState(0)
  const [tags, setTags] = useState('')
  const [globalEnvironmentId, setGlobalEnvironmentId] = useState('')
  const [datasetId, setDatasetId] = useState('')
  const [keepVariables, setKeepVariables] = useState(true)
  const [ignoreCookies, setIgnoreCookies] = useState(false)
  const [saveCookies, setSaveCookies] = useState(true)
  const [mockBaseUrl, setMockBaseUrl] = useState('')
  const [shareResults, setShareResults] = useState(false)
  const [quietLogs, setQuietLogs] = useState(false)
  const [orderedIds, setOrderedIds] = useState([])
  const [included, setIncluded] = useState({})
  const [scheduleMinutes, setScheduleMinutes] = useState(5)
  const [presets, setPresets] = useState([])
  const [presetName, setPresetName] = useState('')
  const [presetPick, setPresetPick] = useState('')

  useEffect(() => {
    if (collectionId) setPresets(loadPresets(collectionId))
    const collection = (workspace?.collections || []).find((item) => item.id === collectionId)
    const ordered = [...(collection?.requests || [])].sort((left, right) => (left.position || 0) - (right.position || 0))
    setOrderedIds(ordered.map((item) => item.id))
    setIncluded(Object.fromEntries(ordered.map((item) => [item.id, true])))
  }, [collectionId, workspace])

  function applyPreset(preset) {
    setStopOnFailure(!!preset.stopOnFailure)
    setDataCsv(preset.dataCsv || '')
    setParallel(!!preset.parallel)
    setDelayMs(Number(preset.delayMs) || 0)
    setTags(preset.tags || '')
    setDatasetId(preset.datasetId || '')
  }

  function savePreset() {
    if (!presetName.trim() || !collectionId) return
    const preset = { name: presetName.trim(), stopOnFailure, dataCsv, parallel, delayMs, tags, datasetId }
    const next = [...presets.filter((item) => item.name !== preset.name), preset]
    setPresets(next)
    savePresets(collectionId, next)
    setPresetName('')
  }

  return (
    <div className="modal-back" onClick={onCancel}>
      <form className="modal" onClick={(event) => event.stopPropagation()} onSubmit={(event) => {
        event.preventDefault()
        const tagList = tags.split(',').map((item) => item.trim()).filter(Boolean)
        onRun({ stopOnFailure, dataCsv, parallel, delayMs, tags: tagList, globalEnvironmentId, datasetId, keepVariables, ignoreCookies, saveCookies, mockBaseUrl, shareResults, quietLogs, requestIds: orderedIds.filter((id) => included[id]) })
      }}>
        <h2>Run {name}</h2>
        {(workspace?.environments || []).some((item) => item.global) && (
          <label>
            Global environment
            <Select
              aria-label="Global environment"
              value={globalEnvironmentId}
              onChange={setGlobalEnvironmentId}
              options={[
                { value: '', label: 'No global environment' },
                ...(workspace.environments || []).filter((item) => item.global).map((item) => ({ value: item.id, label: item.name })),
              ]}
            />
          </label>
        )}
        {presets.length > 0 && (
          <label>
            Run preset
            <Select
              aria-label="Run preset"
              value={presetPick}
              placeholder="Choose preset…"
              onChange={(name) => {
                const preset = presets.find((item) => item.name === name)
                if (preset) applyPreset(preset)
                setPresetPick('')
              }}
              options={presets.map((preset) => ({ value: preset.name, label: preset.name }))}
            />
          </label>
        )}
        <label className="check-line">
          <input type="checkbox" checked={stopOnFailure} onChange={(event) => setStopOnFailure(event.target.checked)} />
          Stop on the first failure
        </label>
        <label className="check-line">
          <input type="checkbox" checked={keepVariables} onChange={(event) => setKeepVariables(event.target.checked)} />
          Keep variable values after the run
        </label>
        <label className="check-line">
          <input type="checkbox" checked={ignoreCookies} onChange={(event) => setIgnoreCookies(event.target.checked)} />
          Run without stored cookies
        </label>
        <label className="check-line">
          <input type="checkbox" checked={saveCookies} onChange={(event) => setSaveCookies(event.target.checked)} />
          Save cookies after the run
        </label>
        <label className="check-line">
          <input type="checkbox" checked={shareResults} onChange={(event) => setShareResults(event.target.checked)} />
          Write a standalone HTML report
        </label>
        <label className="check-line">
          <input type="checkbox" checked={quietLogs} onChange={(event) => setQuietLogs(event.target.checked)} />
          Turn off logs for this run
        </label>
        <div className="stack">
          <strong>Requests in this run</strong>
          <span className="muted">Uncheck a request to skip it. GraphQL and gRPC requests in the list run with the same runner.</span>
          {orderedIds.map((id, index) => {
            const collection = (workspace?.collections || []).find((item) => item.id === collectionId)
            const request = (collection?.requests || []).find((item) => item.id === id)
            if (!request) return null
            return (
              <div key={id} className="url-row">
                <label className="check-line">
                  <input type="checkbox" checked={!!included[id]} onChange={(event) => setIncluded((current) => ({ ...current, [id]: event.target.checked }))} />
                  {request.method} {request.name}
                </label>
                <button type="button" className="secondary" disabled={index === 0} onClick={() => setOrderedIds((current) => {
                  const next = [...current]
                  const [item] = next.splice(index, 1)
                  next.splice(index - 1, 0, item)
                  return next
                })}>Up</button>
              </div>
            )
          })}
        </div>
        <label>
          Redirect to mock
          <input value={mockBaseUrl} onChange={(event) => setMockBaseUrl(event.target.value)} placeholder="http://127.0.0.1:4010" />
        </label>
        <label className="check-line">
          <input type="checkbox" checked={parallel} onChange={(event) => setParallel(event.target.checked)} />
          Run requests in parallel
        </label>
        <label>
          Delay between requests (ms)
          <input type="number" min="0" value={delayMs} onChange={(event) => setDelayMs(Number(event.target.value) || 0)} />
        </label>
        <label>
          Tag expression
          <input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="@tag(smoke) || regression" />
          <span className="muted">Supports @tag(name), comma lists, && and ||. Example: smoke && @tag(regression)</span>
        </label>
        {(workspace?.datasets || []).length > 0 && (
          <label>
            Dataset
            <Select
              aria-label="Dataset"
              value={datasetId}
              onChange={setDatasetId}
              options={[
                { value: '', label: 'No dataset (use CSV below)' },
                ...(workspace.datasets || []).map((dataset) => ({ value: dataset.id, label: dataset.name })),
              ]}
            />
            <span className="muted">Workspace datasets drive data rows and are available in scripts as pm.datasets.</span>
          </label>
        )}
        <label className="stack">
          Data file
          <textarea className="body" value={dataCsv} spellCheck={false} onChange={(event) => setDataCsv(event.target.value)} placeholder={'id,name\n1,Ada\n2,Grace'} />
          <span className="muted">Optional CSV when no dataset is selected. The header row becomes variables, and the requests run once per data row.</span>
        </label>
        {collectionId && (
          <div className="url-row">
            <input value={presetName} onChange={(event) => setPresetName(event.target.value)} placeholder="Preset name" aria-label="Preset name" />
            <button type="button" className="secondary" disabled={!presetName.trim()} onClick={savePreset}>Save preset</button>
          </div>
        )}
        <div className="url-row">
          <input type="number" min="1" value={scheduleMinutes} onChange={(event) => setScheduleMinutes(Number(event.target.value) || 1)} aria-label="Schedule interval minutes" />
          <button type="button" className="secondary" onClick={async () => {
            await api.saveSchedule({ collectionId, environmentId: globalEnvironmentId, intervalMs: Math.max(scheduleMinutes, 1) * 60_000, enabled: true })
          }}>Schedule this collection</button>
        </div>
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
          <button type="submit" className="send">Run</button>
        </div>
      </form>
    </div>
  )
}
