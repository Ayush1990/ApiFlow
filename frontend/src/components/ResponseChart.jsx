function flattenNumbers(value, prefix = '') {
  const rows = []
  if (typeof value === 'number') {
    rows.push({ label: prefix || 'value', value })
    return rows
  }
  if (Array.isArray(value)) {
    value.slice(0, 20).forEach((item, index) => rows.push(...flattenNumbers(item, `${prefix}[${index}]`)))
    return rows
  }
  if (value && typeof value === 'object') {
    Object.entries(value).slice(0, 20).forEach(([key, item]) => rows.push(...flattenNumbers(item, prefix ? `${prefix}.${key}` : key)))
  }
  return rows
}

export function ResponseChart({ body }) {
  let values = []
  let table = []
  try {
    const parsed = JSON.parse(body || '[]')
    values = flattenNumbers(parsed).filter((item) => Number.isFinite(item.value)).slice(0, 16)
    if (Array.isArray(parsed)) table = parsed.slice(0, 8)
    else if (parsed && typeof parsed === 'object') table = [parsed]
  } catch {
    return <p className="muted">Response is not chartable JSON.</p>
  }
  if (!values.length) return <p className="muted">No numeric values to chart.</p>
  const max = Math.max(...values.map((item) => item.value), 1)
  const total = values.reduce((sum, item) => sum + item.value, 0) || 1
  let pieOffset = 0
  const pieColors = ['#4c8bf5', '#34a853', '#fbbc04', '#ea4335', '#9aa0a6', '#a142f4', '#00bcd4', '#ff7043']
  const pieStops = values.map((item, index) => {
    const pct = (item.value / total) * 100
    const stop = `${pieColors[index % pieColors.length]} ${pieOffset}% ${pieOffset + pct}%`
    pieOffset += pct
    return stop
  }).join(', ')
  return (
    <div className="stack">
      <div className="chart chart-pie" style={{ background: pieStops ? `conic-gradient(${pieStops})` : undefined }} title="Distribution" />
      <div className="chart">
        {values.map((item) => (
          <div key={item.label} className="chart-row">
            <span>{item.label}</span>
            <div className="chart-bar"><i style={{ width: `${(item.value / max) * 100}%` }} /></div>
            <span>{item.value}</span>
          </div>
        ))}
      </div>
      {table.length > 0 && (
        <pre className="markup">{JSON.stringify(table, null, 2)}</pre>
      )}
    </div>
  )
}
