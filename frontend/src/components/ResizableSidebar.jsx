import { useEffect, useRef, useState } from 'react'

const STORAGE_KEY = 'apiflow-sidebar-width'
const DEFAULT = 280
const MIN = 200
const MAX = 520

function readWidth() {
  try {
    const stored = Number(localStorage.getItem(STORAGE_KEY))
    if (Number.isFinite(stored) && stored >= MIN && stored <= MAX) return stored
  } catch {
    // ignore
  }
  return DEFAULT
}

export function ResizableSidebar({ children, collapsed = false }) {
  const [width, setWidth] = useState(readWidth)
  const widthRef = useRef(width)
  const dragging = useRef(false)
  const startX = useRef(0)
  const startWidth = useRef(0)

  useEffect(() => {
    widthRef.current = width
  }, [width])

  useEffect(() => {
    function onMove(event) {
      if (!dragging.current) return
      const next = Math.min(MAX, Math.max(MIN, startWidth.current + event.clientX - startX.current))
      setWidth(next)
      widthRef.current = next
    }

    function onUp() {
      if (!dragging.current) return
      dragging.current = false
      document.body.classList.remove('sidebar-resizing')
      try {
        localStorage.setItem(STORAGE_KEY, String(widthRef.current))
      } catch {
        // ignore
      }
    }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
      document.body.classList.remove('sidebar-resizing')
    }
  }, [])

  function startDrag(event) {
    dragging.current = true
    startX.current = event.clientX
    startWidth.current = widthRef.current
    document.body.classList.add('sidebar-resizing')
    event.preventDefault()
  }

  function onKeyDown(event) {
    let next = widthRef.current
    if (event.key === 'ArrowLeft') next -= event.shiftKey ? 40 : 16
    else if (event.key === 'ArrowRight') next += event.shiftKey ? 40 : 16
    else if (event.key === 'Home') next = MIN
    else if (event.key === 'End') next = MAX
    else return
    event.preventDefault()
    next = Math.min(MAX, Math.max(MIN, next))
    setWidth(next)
    widthRef.current = next
    try {
      localStorage.setItem(STORAGE_KEY, String(next))
    } catch {
      // ignore
    }
  }

  if (collapsed) {
    return <div className="sidebar-shell collapsed" style={{ width: 0 }} aria-hidden />
  }

  return (
    <div className="sidebar-shell" style={{ width }}>
      {children}
      <div
        className="sidebar-resizer"
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize sidebar"
        aria-valuemin={MIN}
        aria-valuemax={MAX}
        aria-valuenow={width}
        tabIndex={0}
        onMouseDown={startDrag}
        onDoubleClick={() => {
          setWidth(DEFAULT)
          widthRef.current = DEFAULT
          try {
            localStorage.setItem(STORAGE_KEY, String(DEFAULT))
          } catch {
            // ignore
          }
        }}
        onKeyDown={onKeyDown}
      />
    </div>
  )
}
