import { Children, useEffect, useId, useMemo, useRef, useState } from 'react'

function optionsFromChildren(children) {
  const options = []
  Children.forEach(children, (child) => {
    if (!child || child.type !== 'option') return
    options.push({
      value: child.props.value ?? '',
      label: child.props.children ?? child.props.value ?? '',
      disabled: !!child.props.disabled,
    })
  })
  return options
}

export function Select({
  value,
  defaultValue,
  onChange,
  options,
  children,
  className = '',
  disabled = false,
  'aria-label': ariaLabel,
  placeholder = '',
}) {
  const [open, setOpen] = useState(false)
  const [internal, setInternal] = useState(defaultValue ?? '')
  const rootRef = useRef(null)
  const listId = useId()
  const controlled = value !== undefined
  const current = controlled ? value : internal
  const items = useMemo(() => options || optionsFromChildren(children), [options, children])
  const selected = items.find((item) => String(item.value) === String(current))
  const label = selected?.label ?? (current ? String(current) : placeholder || 'Select…')

  useEffect(() => {
    function onDocClick(event) {
      if (!rootRef.current?.contains(event.target)) setOpen(false)
    }
    function onKey(event) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [])

  function pick(next) {
    if (!controlled) setInternal(next)
    onChange?.(next)
    setOpen(false)
  }

  return (
    <div className={`ui-select${open ? ' open' : ''}${disabled ? ' disabled' : ''}${className ? ` ${className}` : ''}`} ref={rootRef}>
      <button
        type="button"
        className="ui-select-trigger"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        disabled={disabled}
        onClick={() => !disabled && setOpen((state) => !state)}
      >
        <span className={selected ? '' : 'ui-select-placeholder'}>{label}</span>
        <span className="ui-select-chevron" aria-hidden>▾</span>
      </button>
      {open && (
        <ul className="ui-select-menu" id={listId} role="listbox">
          {items.map((item) => (
            <li key={String(item.value)}>
              <button
                type="button"
                role="option"
                aria-selected={String(item.value) === String(current)}
                className={`ui-select-option${String(item.value) === String(current) ? ' active' : ''}`}
                disabled={item.disabled}
                onClick={() => !item.disabled && pick(item.value)}
              >
                {String(item.value) === String(current) && <span className="ui-select-check">✓</span>}
                {item.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
