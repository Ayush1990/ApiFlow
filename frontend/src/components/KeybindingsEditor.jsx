import { Select } from './Select.jsx'

const ACTIONS = [
  { id: 'send', label: 'Send request' },
  { id: 'save', label: 'Save request' },
  { id: 'newRequest', label: 'New request' },
  { id: 'search', label: 'Focus search' },
  { id: 'toggleSidebar', label: 'Toggle sidebar' },
]

const CHOICES = [
  { value: '', label: 'Default / disabled' },
  { value: 'mod+Enter', label: 'Cmd/Ctrl + Enter' },
  { value: 'Enter', label: 'Enter' },
  { value: 'mod+s', label: 'Cmd/Ctrl + S' },
  { value: 'mod+n', label: 'Cmd/Ctrl + N' },
  { value: 'mod+k', label: 'Cmd/Ctrl + K' },
  { value: 'mod+b', label: 'Cmd/Ctrl + B' },
  { value: 'mod+Shift+s', label: 'Cmd/Ctrl + Shift + S' },
]

export function KeybindingsEditor({ settings, onChange }) {
  const bindings = settings.keybindings || {}

  function patch(action, value) {
    onChange({
      keybindings: {
        ...bindings,
        [action]: value,
      },
      ...(action === 'send' && value ? { sendKeybinding: value } : {}),
    })
  }

  return (
    <div className="stack keybindings-editor">
      <p className="muted">Customize keyboard shortcuts. Send also respects the legacy send shortcut field.</p>
      {ACTIONS.map((action) => (
        <label key={action.id} className="field">
          {action.label}
          <Select
            value={bindings[action.id] || (action.id === 'send' ? settings.sendKeybinding || 'mod+Enter' : '')}
            onChange={(value) => patch(action.id, value)}
            options={CHOICES}
          />
        </label>
      ))}
    </div>
  )
}

export function matchKeybinding(event, binding) {
  if (!binding) return false
  const mod = event.metaKey || event.ctrlKey
  const parts = binding.split('+')
  const key = parts[parts.length - 1].toLowerCase()
  const needsMod = parts.includes('mod')
  const needsShift = parts.includes('Shift')
  if (needsMod !== mod) return false
  if (needsShift !== event.shiftKey) return false
  if (key === 'enter') return event.key === 'Enter'
  return event.key.toLowerCase() === key
}
