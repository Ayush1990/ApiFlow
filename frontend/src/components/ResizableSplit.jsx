import { useEffect, useRef, useState } from 'react'

const DEFAULT_HEIGHT = 300
const MIN_HEIGHT = 160

function readHeight(storageKey, fallback) {
  try {
    const stored = Number(localStorage.getItem(storageKey))
    if (Number.isFinite(stored) && stored >= MIN_HEIGHT) return stored
  } catch {
    // ignore
  }
  return fallback
}

export function ResizableSplit({ storageKey, bottom, top, defaultHeight = DEFAULT_HEIGHT }) {
  const [bottomHeight, setBottomHeight] = useState(() => readHeight(storageKey, defaultHeight))
  const heightRef = useRef(bottomHeight)
  const dragging = useRef(false)
  const startY = useRef(0)
  const startHeight = useRef(0)
  const containerRef = useRef(null)

  useEffect(() => {
    heightRef.current = bottomHeight
  }, [bottomHeight])

  useEffect(() => {
    function onMove(event) {
      if (!dragging.current || !containerRef.current) return
      const rect = containerRef.current.getBoundingClientRect()
      const maxHeight = Math.max(MIN_HEIGHT, rect.height * 0.72)
      const next = Math.min(maxHeight, Math.max(MIN_HEIGHT, startHeight.current + startY.current - event.clientY))
      setBottomHeight(next)
      heightRef.current = next
    }

    function onUp() {
      if (!dragging.current) return
      dragging.current = false
      document.body.classList.remove('split-resizing')
      try {
        localStorage.setItem(storageKey, String(heightRef.current))
      } catch {
        // ignore
      }
    }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
      document.body.classList.remove('split-resizing')
    }
  }, [storageKey])

  function startDrag(event) {
    dragging.current = true
    startY.current = event.clientY
    startHeight.current = heightRef.current
    document.body.classList.add('split-resizing')
    event.preventDefault()
  }

  function resetHeight() {
    setBottomHeight(defaultHeight)
    heightRef.current = defaultHeight
    try {
      localStorage.setItem(storageKey, String(defaultHeight))
    } catch {
      // ignore
    }
  }

  return (
    <div className="split-vertical" ref={containerRef}>
      <div className="split-pane split-pane-top">{top}</div>
      <div
        className="split-handle"
        role="separator"
        aria-orientation="horizontal"
        aria-label="Resize response panel"
        tabIndex={0}
        onMouseDown={startDrag}
        onDoubleClick={resetHeight}
      />
      <div className="split-pane split-pane-bottom" style={{ height: bottomHeight }}>
        {bottom}
      </div>
    </div>
  )
}
