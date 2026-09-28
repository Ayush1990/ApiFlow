import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

export function TabMoreMenu({ items, activeId, activeLabel, onSelect }) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef(null)
  const menuRef = useRef(null)
  const [pos, setPos] = useState({ top: 0, left: 0, width: 160 })

  useEffect(() => {
    if (!open) return undefined
    function update() {
      const rect = triggerRef.current?.getBoundingClientRect()
      if (!rect) return
      setPos({
        top: rect.bottom + 6,
        left: rect.left,
        width: Math.max(rect.width, 168),
      })
    }
    update()
    window.addEventListener('scroll', update, true)
    window.addEventListener('resize', update)
    return () => {
      window.removeEventListener('scroll', update, true)
      window.removeEventListener('resize', update)
    }
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    function onDoc(event) {
      const target = event.target
      if (triggerRef.current?.contains(target) || menuRef.current?.contains(target)) return
      setOpen(false)
    }
    function onKey(event) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const label = activeId ? activeLabel : 'More…'

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={`tab tab-more-trigger${activeId ? ' active' : ''}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        {label}
        <span className="tab-more-chevron" aria-hidden>▾</span>
      </button>
      {open && createPortal(
        <ul
          ref={menuRef}
          className="tab-more-menu"
          role="listbox"
          style={{ top: pos.top, left: pos.left, minWidth: pos.width }}
        >
          {items.map(([id, name]) => (
            <li key={id}>
              <button
                type="button"
                role="option"
                aria-selected={id === activeId}
                className={id === activeId ? 'active' : ''}
                onClick={() => {
                  onSelect(id)
                  setOpen(false)
                }}
              >
                {id === activeId && <span className="tab-more-check">✓</span>}
                {name}
              </button>
            </li>
          ))}
        </ul>,
        document.body,
      )}
    </>
  )
}
