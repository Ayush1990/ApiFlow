import { useRef, useState } from 'react'

const BLOCK_TYPES = [
  { type: 'start', label: 'Start' },
  { type: 'request', label: 'HTTP Request', config: '{"collectionId":"","requestId":"","environmentId":""}' },
  { type: 'transform', label: 'Transform', config: '{"mode":"fql","script":"set greeting = Hello {{name}}"}' },
  { type: 'delay', label: 'Delay', config: '{"ms":500}' },
  { type: 'condition', label: 'Condition', config: '{"expression":"{{lastStatus}} < 400"}' },
]

function newBlock(type, label, x, y, config) {
  return {
    id: `block-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    type,
    label,
    x,
    y,
    config: config || '{}',
  }
}

export function FlowCanvas({ flow, onChange }) {
  const canvasRef = useRef(null)
  const [dragging, setDragging] = useState(null)
  const [linkFrom, setLinkFrom] = useState('')
  const [selectedId, setSelectedId] = useState('')

  function updateBlocks(blocks) {
    onChange({ ...flow, blocks })
  }

  function updateConnections(connections) {
    onChange({ ...flow, connections })
  }

  function onMouseDown(block, event) {
    event.stopPropagation()
    setSelectedId(block.id)
    setDragging({ id: block.id, offsetX: event.clientX - block.x, offsetY: event.clientY - block.y })
  }

  function onMouseMove(event) {
    if (!dragging || !canvasRef.current) return
    const rect = canvasRef.current.getBoundingClientRect()
    const x = Math.max(0, event.clientX - rect.left - dragging.offsetX)
    const y = Math.max(0, event.clientY - rect.top - dragging.offsetY)
    updateBlocks((flow.blocks || []).map((block) => block.id === dragging.id ? { ...block, x, y } : block))
  }

  function finishDrag() {
    setDragging(null)
  }

  function addBlock(item) {
    const blocks = [...(flow.blocks || []), newBlock(item.type, item.label, 60 + (flow.blocks?.length || 0) * 24, 60 + (flow.blocks?.length || 0) * 16, item.config)]
    updateBlocks(blocks)
  }

  function removeBlock(id) {
    updateBlocks((flow.blocks || []).filter((block) => block.id !== id))
    updateConnections((flow.connections || []).filter((connection) => connection.from !== id && connection.to !== id))
    if (selectedId === id) setSelectedId('')
  }

  function connectBlocks(from, to) {
    if (!from || !to || from === to) return
    const connections = [...(flow.connections || [])]
    if (!connections.some((connection) => connection.from === from && connection.to === to)) {
      const source = (flow.blocks || []).find((block) => block.id === from)
      const when = source?.type === 'condition'
        ? (connections.some((connection) => connection.from === from && connection.when === 'true') ? 'false' : 'true')
        : ''
      connections.push({ from, to, when })
    }
    updateConnections(connections)
    setLinkFrom('')
  }

  const selected = (flow.blocks || []).find((block) => block.id === selectedId)

  return (
    <div className="flow-editor stack">
      <div className="toolbar-row">
        {BLOCK_TYPES.map((item) => (
          <button key={item.type} type="button" className="secondary" onClick={() => addBlock(item)}>{item.label}</button>
        ))}
        {linkFrom && <span className="muted">Click target block to connect from {linkFrom}</span>}
      </div>
      <div
        ref={canvasRef}
        className="flow-canvas flow-canvas-interactive"
        onMouseMove={onMouseMove}
        onMouseUp={finishDrag}
        onMouseLeave={finishDrag}
      >
        {(flow.connections || []).map((connection) => {
          const from = (flow.blocks || []).find((block) => block.id === connection.from)
          const to = (flow.blocks || []).find((block) => block.id === connection.to)
          if (!from || !to) return null
          const x1 = from.x + 60
          const y1 = from.y + 30
          const x2 = to.x + 60
          const y2 = to.y + 30
          return (
            <svg key={`${connection.from}-${connection.to}`} className="flow-line" aria-hidden="true">
              <line x1={x1} y1={y1} x2={x2} y2={y2} />
            </svg>
          )
        })}
        {(flow.blocks || []).map((block) => (
          <div
            key={block.id}
            className={`flow-block flow-block-${block.type}${selectedId === block.id ? ' selected' : ''}`}
            style={{ left: block.x, top: block.y }}
            onMouseDown={(event) => onMouseDown(block, event)}
            onClick={(event) => {
              event.stopPropagation()
              if (linkFrom) connectBlocks(linkFrom, block.id)
              else setSelectedId(block.id)
            }}
          >
            <strong>{block.label || block.type}</strong>
            <span className="muted">{block.type}{selectedId === block.id && (flow.connections || []).some((connection) => connection.from === block.id && connection.when) ? '' : ''}</span>
            <div className="flow-block-actions">
              <button type="button" className="icon" title="Connect from" onClick={(event) => { event.stopPropagation(); setLinkFrom(block.id) }}>→</button>
              {block.type !== 'start' && (
                <button type="button" className="icon danger" title="Remove" onClick={(event) => { event.stopPropagation(); removeBlock(block.id) }}>×</button>
              )}
            </div>
          </div>
        ))}
      </div>
      {selected && (
        <div className="section-card stack">
          <input value={selected.label || ''} onChange={(event) => {
            updateBlocks((flow.blocks || []).map((block) => block.id === selected.id ? { ...block, label: event.target.value } : block))
          }} placeholder="Block label" />
          <textarea
            className="body body-compact"
            value={selected.config || '{}'}
            spellCheck={false}
            onChange={(event) => {
              updateBlocks((flow.blocks || []).map((block) => block.id === selected.id ? { ...block, config: event.target.value } : block))
            }}
          />
          {selected.type === 'transform' && (
            <p className="muted">FQL: set key = value, delete key, pick a,b,c, json source dest. JS: set mode to js and write context mutations.</p>
          )}
        </div>
      )}
    </div>
  )
}
