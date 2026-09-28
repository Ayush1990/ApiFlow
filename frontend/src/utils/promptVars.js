const VAR_PATTERN = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g

export function promptDefaults(draft) {
  const defaults = {}
  for (const row of draft?.extras?.promptVars || []) {
    if (row.enabled !== false && row.key) defaults[row.key] = row.value || ''
  }
  return defaults
}

export function unresolvedVariables(draft, variables) {
  const names = new Set()
  const merged = { ...promptDefaults(draft), ...(variables || {}) }
  const sources = [
    draft?.url || '',
    draft?.body || '',
    draft?.graphqlQuery || '',
    draft?.graphqlVariables || '',
    draft?.authToken || '',
    draft?.authUsername || '',
    draft?.authPassword || '',
    draft?.apiKeyValue || '',
    ...(draft?.headers || []).map((row) => `${row.key || ''} ${row.value || ''}`),
    ...(draft?.params || []).map((row) => `${row.key || ''} ${row.value || ''}`),
    ...(draft?.form || []).map((row) => `${row.key || ''} ${row.value || ''}`),
  ]
  for (const source of sources) {
    for (const match of source.matchAll(VAR_PATTERN)) {
      const name = match[1]
      if (!Object.prototype.hasOwnProperty.call(merged, name)) {
        names.add(name)
      }
    }
  }
  return [...names]
}
