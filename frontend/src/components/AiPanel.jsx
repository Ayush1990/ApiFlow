import { useEffect, useRef, useState } from 'react'
import { api } from '../api.js'
import { Select } from './Select.jsx'

const SESSION_KEY = 'apiflow-ai-session'

function Section({ title, desc, children, actions }) {
  return (
    <section className="section-card">
      <div className="section-head">
        <div>
          <h3 className="section-title">{title}</h3>
          {desc && <p className="section-desc muted">{desc}</p>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  )
}

function ChatMessage({ role, content }) {
  const isUser = role === 'user'
  return (
    <div className={`ai-message${isUser ? ' user' : ' assistant'}`}>
      <span className="ai-message-role">{isUser ? 'You' : 'Assistant'}</span>
      <pre className="ai-message-body">{content}</pre>
    </div>
  )
}

export function AiPanel({ context, onInsert }) {
  const [prompt, setPrompt] = useState('')
  const [reply, setReply] = useState('')
  const [history, setHistory] = useState([])
  const [mode, setMode] = useState('assistant')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)
  const [sessionId, setSessionId] = useState(() => localStorage.getItem(SESSION_KEY) || '')
  const replyRef = useRef('')
  const replyEndRef = useRef(null)

  useEffect(() => {
    if (!sessionId) return
    localStorage.setItem(SESSION_KEY, sessionId)
    api.aiHistory(sessionId).then(setHistory).catch(() => setHistory([]))
  }, [sessionId])

  useEffect(() => {
    replyEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [reply, history])

  async function ask() {
    if (!prompt.trim() || busy) return
    setBusy(true)
    setError('')
    setReply('')
    replyRef.current = ''
    try {
      if (mode === 'script') {
        const result = await api.aiScript(prompt.trim(), context || '')
        const text = result.script || ''
        setReply(text)
        return
      }
      const nextSession = sessionId || crypto.randomUUID()
      if (!sessionId) setSessionId(nextSession)
      await api.aiChatStream(prompt.trim(), context || '', nextSession, (chunk) => {
        replyRef.current += chunk
        setReply(replyRef.current)
      })
      const items = await api.aiHistory(nextSession)
      setHistory(items)
      setPrompt('')
      setReply('')
      replyRef.current = ''
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function clearHistory() {
    setHistory([])
    setReply('')
    setSessionId('')
    localStorage.removeItem(SESSION_KEY)
  }

  function copyReply() {
    if (!reply) return
    navigator.clipboard.writeText(reply)
    setCopied(true)
    setTimeout(() => setCopied(false), 1200)
  }

  const hasContext = !!context?.trim()
  const showLiveReply = !!reply && mode === 'assistant'

  return (
    <div className="env-editor ai-panel">
      <div className="page-header">
        <div className="page-header-row">
          <div>
            <h2>AI Assistant</h2>
            <p className="muted">Streaming chat with conversation history. Configure API keys in Workspace settings.</p>
          </div>
          {hasContext && <span className="ai-context-pill">Request context attached</span>}
        </div>
      </div>

      <div className="page-body stack">
        <Section
          title="Mode"
          desc={mode === 'script'
            ? 'Generate Bruno-compatible test scripts from a description.'
            : 'Multi-turn chat that remembers the current session.'}
        >
          <div className="ai-mode-row">
            <Select
              value={mode}
              onChange={setMode}
              options={[
                { value: 'assistant', label: 'Chat (streaming)' },
                { value: 'script', label: 'Script helper' },
              ]}
            />
          </div>
        </Section>

        {mode === 'assistant' && (history.length > 0 || showLiveReply) && (
          <Section
            title="Conversation"
            desc="Recent messages from this session."
            actions={<button type="button" className="ghost-button" onClick={clearHistory}>Clear history</button>}
          >
            <div className="ai-thread">
              {history.slice(-8).map((item, index) => (
                <ChatMessage key={`${item.at}-${index}`} role={item.role} content={item.content} />
              ))}
              {showLiveReply && (
                <div className="ai-message assistant streaming">
                  <span className="ai-message-role">Assistant</span>
                  <pre className="ai-message-body">{reply}</pre>
                </div>
              )}
              <div ref={replyEndRef} />
            </div>
          </Section>
        )}

        <Section
          title={mode === 'script' ? 'Describe the script' : 'Message'}
          desc={mode === 'script'
            ? 'Describe what the post-response test should assert or extract.'
            : 'Ask about errors, write tests, or explain a response.'}
        >
          <textarea
            className="body ai-prompt"
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            onKeyDown={(event) => {
              if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
                event.preventDefault()
                ask()
              }
            }}
            placeholder={mode === 'script'
              ? 'Assert status 200 and body contains "id"'
              : 'Explain this API error… or write a test for status 200'}
            rows={4}
          />
          <div className="ai-compose-actions">
            <span className="muted ai-hint"><kbd className="kbd">⌘ Enter</kbd> to send</span>
            <div className="row">
              <button type="button" className="send" disabled={busy || !prompt.trim()} onClick={ask}>
                {busy ? (mode === 'script' ? 'Generating…' : 'Streaming…') : (mode === 'script' ? 'Generate script' : 'Send')}
              </button>
            </div>
          </div>
        </Section>

        {error && (
          <section className="section-card ai-error-card">
            <p className="call-error">{error}</p>
          </section>
        )}

        {(mode === 'script' ? reply : false) && (
          <Section
            title="Generated script"
            desc="Review before inserting into your request."
            actions={(
              <div className="row">
                <button type="button" className="secondary" onClick={copyReply}>{copied ? 'Copied' : 'Copy'}</button>
                {onInsert && <button type="button" className="secondary" onClick={() => onInsert(reply)}>Insert into script</button>}
              </div>
            )}
          >
            <pre className="ai-output">{reply}</pre>
          </Section>
        )}

        {hasContext && (
          <Section title="Attached context" desc="The active request is included with each prompt.">
            <pre className="ai-context-block">{context}</pre>
          </Section>
        )}
      </div>
    </div>
  )
}
