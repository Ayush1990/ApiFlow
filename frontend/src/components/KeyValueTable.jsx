export function KeyValueTable({ rows, onChange, keyPlaceholder = 'Key', valuePlaceholder = 'Value', secrets = false }) {
  const list = rows || []

  function update(index, patch) {
    onChange(list.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  return (
    <div className="kv">
      <div className={`kv-head ${secrets ? 'with-secret' : ''}`}>
        <span />
        <span>Key</span>
        <span>Value</span>
        {secrets && <span>Secret</span>}
        <span />
      </div>
      {list.map((row, index) => (
        <div className={`kv-row ${secrets ? 'with-secret' : ''}`} key={index}>
          <input
            type="checkbox"
            checked={row.enabled !== false}
            onChange={(event) => update(index, { enabled: event.target.checked })}
            aria-label="Enabled"
          />
          <input
            value={row.key || ''}
            placeholder={keyPlaceholder}
            onChange={(event) => update(index, { key: event.target.value })}
          />
          <input
            type={row.secret ? 'password' : 'text'}
            value={row.value || ''}
            placeholder={valuePlaceholder}
            onChange={(event) => update(index, { value: event.target.value })}
          />
          {secrets && (
            <input
              type="checkbox"
              checked={!!row.secret}
              onChange={(event) => update(index, { secret: event.target.checked })}
              aria-label="Secret"
              title="Hide this value and keep it out of history"
            />
          )}
          <button type="button" className="icon" onClick={() => onChange(list.filter((_, i) => i !== index))} aria-label="Remove row">
            ×
          </button>
        </div>
      ))}
      <button type="button" className="text-button" onClick={() => onChange([...list, { key: '', value: '', enabled: true, secret: false }])}>
        + Add
      </button>
    </div>
  )
}

