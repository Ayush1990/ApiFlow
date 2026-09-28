import { useMemo, useState } from 'react'
import { api, interpolate } from '../api.js'

function typeLabel(type) {
  if (!type) return 'Unknown'
  if (type.kind === 'NON_NULL') return `${typeLabel(type.ofType)}!`
  if (type.kind === 'LIST') return `[${typeLabel(type.ofType)}]`
  return type.name || type.kind || 'Unknown'
}

function unwrapType(type) {
  let current = type
  while (current && (current.kind === 'NON_NULL' || current.kind === 'LIST')) {
    current = current.ofType
  }
  return current
}

function buildSelectionFromSelected(field, selectedSet, depth = 0) {
  const returnType = unwrapType(field.type)
  const scalar = returnType && !['OBJECT', 'INTERFACE', 'UNION'].includes(returnType.kind)
  const key = field.path || field.name
  if (!selectedSet.has(key)) return ''
  if (scalar || depth > 2) return field.name
  const nested = (returnType?.fields || [])
    .map((child) => {
      const childPath = `${key}.${child.name}`
      return buildSelectionFromSelected({ ...child, path: childPath }, selectedSet, depth + 1)
    })
    .filter(Boolean)
    .map((line) => `    ${line}`)
    .join('\n')
  return nested ? `${field.name} {\n${nested}\n  }` : field.name
}

function buildQuery(rootName, field, operation, selectedSet) {
  const args = (field.args || []).filter((arg) => arg.name)
  const argList = args.map((arg) => `${arg.name}: ${arg.defaultValue || `$${arg.name}`}`).join(', ')
  const variables = args.length
    ? `(${args.map((arg) => `$${arg.name}: ${typeLabel(arg.type)}`).join(', ')})`
    : ''
  const varsJson = args.length
    ? JSON.stringify(Object.fromEntries(args.map((arg) => [arg.name, arg.defaultValue || ''])), null, 2)
    : '{}'
  const selection = buildSelectionFromSelected({ ...field, path: field.name }, selectedSet)
  return {
    graphqlQuery: `${operation} ${field.name[0].toUpperCase()}${field.name.slice(1)}${variables} {\n  ${field.name}${argList ? `(${argList})` : ''} {\n    ${selection}\n  }\n}`,
    graphqlVariables: varsJson,
    method: 'POST',
    bodyType: 'graphql',
  }
}

function FieldTree({ field, path, selected, onToggle, depth = 0 }) {
  const returnType = unwrapType(field.type)
  const scalar = returnType && !['OBJECT', 'INTERFACE', 'UNION'].includes(returnType.kind)
  const key = path || field.name
  const checked = selected.has(key)
  return (
    <li className="gql-field-node">
      <label className="gql-field-label">
        <input type="checkbox" checked={checked} onChange={() => onToggle(key, field, !checked)} />
        <span>{field.name}</span>
        <span className="muted"> → {typeLabel(field.type)}</span>
      </label>
      {!scalar && checked && (returnType?.fields || []).length > 0 && (
        <ul className="gql-field-children">
          {(returnType.fields || []).slice(0, 24).map((child) => (
            <FieldTree
              key={`${key}.${child.name}`}
              field={child}
              path={`${key}.${child.name}`}
              selected={selected}
              onToggle={onToggle}
              depth={depth + 1}
            />
          ))}
        </ul>
      )}
    </li>
  )
}

export function GraphqlExplorer({ draft, variables, onChange }) {
  const [schema, setSchema] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [search, setSearch] = useState('')
  const [operation, setOperation] = useState('query')
  const [selectedField, setSelectedField] = useState(null)
  const [selectedPaths, setSelectedPaths] = useState(new Set())

  async function introspect() {
    setLoading(true)
    setError('')
    try {
      const url = interpolate(draft.url || '', variables || {})
      const headers = (draft.headers || []).filter((row) => row.enabled !== false && row.key).map((row) => ({
        ...row,
        value: interpolate(row.value || '', variables || {}),
      }))
      const raw = await api.graphqlIntrospect(url, headers)
      setSchema(typeof raw === 'string' ? JSON.parse(raw) : raw)
    } catch (err) {
      setError(err.message)
      setSchema(null)
    } finally {
      setLoading(false)
    }
  }

  const schemaRoot = schema?.data?.__schema
  const queryType = useMemo(() => schemaRoot?.types?.find((type) => type.name === schemaRoot?.queryType?.name), [schemaRoot])
  const mutationType = useMemo(() => schemaRoot?.types?.find((type) => type.name === schemaRoot?.mutationType?.name), [schemaRoot])
  const rootType = operation === 'mutation' ? mutationType : queryType

  const fields = useMemo(() => {
    if (!rootType) return []
    return (rootType.fields || []).filter((field) => !search || field.name.toLowerCase().includes(search.toLowerCase()))
  }, [rootType, search])

  function togglePath(path, field, next) {
    setSelectedPaths((current) => {
      const copy = new Set(current)
      if (next) copy.add(path)
      else copy.delete(path)
      if (field && next && path === field.name) {
        setSelectedField(field)
      }
      return copy
    })
  }

  function applyQuery() {
    if (!selectedField || selectedPaths.size === 0) return
    onChange(buildQuery(rootType.name, selectedField, operation, selectedPaths))
  }

  function quickInsert(field) {
    setSelectedField(field)
    setSelectedPaths(new Set([field.name]))
    onChange(buildQuery(rootType.name, field, operation, new Set([field.name])))
  }

  return (
    <div className="stack graphql-builder">
      <div className="modal-actions">
        <button type="button" className="secondary" onClick={introspect} disabled={loading}>{loading ? 'Loading…' : 'Load schema'}</button>
        {schema && (
          <>
            <div className="segmented">
              {['query', 'mutation'].filter((item) => item !== 'mutation' || mutationType).map((item) => (
                <button key={item} type="button" className={operation === item ? 'active' : ''} onClick={() => setOperation(item)}>{item}</button>
              ))}
            </div>
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Filter root fields" />
          </>
        )}
      </div>
      {error && <p className="call-error">{error}</p>}
      {schema && rootType && (
        <div className="graphql-explorer-grid">
          <div className="graphql-explorer stack">
            <strong>Root fields</strong>
            <ul>
              {fields.map((field) => (
                <li key={field.name}>
                  <button type="button" className={`text-button${selectedField?.name === field.name ? ' active' : ''}`} onClick={() => quickInsert(field)}>
                    {field.name}
                  </button>
                  <span className="muted"> → {typeLabel(field.type)}</span>
                </li>
              ))}
            </ul>
          </div>
          {selectedField && (
            <div className="graphql-explorer stack">
              <div className="section-head">
                <strong>Query builder · {selectedField.name}</strong>
                <button type="button" className="send" onClick={applyQuery} disabled={selectedPaths.size === 0}>Apply query</button>
              </div>
              <p className="muted">Select fields to include in the generated GraphQL query.</p>
              <ul className="gql-field-tree">
                <FieldTree field={selectedField} path={selectedField.name} selected={selectedPaths} onToggle={togglePath} />
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
