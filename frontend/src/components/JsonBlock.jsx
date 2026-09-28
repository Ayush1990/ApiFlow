function escapeHtml(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function JsonBlock({ text }) {
  let pretty = text || ''
  let json = false
  try {
    pretty = JSON.stringify(JSON.parse(text), null, 2)
    json = true
  } catch {
    json = false
  }
  if (!json) return <pre>{pretty}</pre>
  const html = escapeHtml(pretty).replace(
    /("(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
    (match) => {
      let kind = 'json-num'
      if (match.startsWith('"')) kind = match.endsWith(':') ? 'json-key' : 'json-str'
      else if (match === 'true' || match === 'false') kind = 'json-bool'
      else if (match === 'null') kind = 'json-null'
      return `<span class="${kind}">${match}</span>`
    },
  )
  return <pre className="json" dangerouslySetInnerHTML={{ __html: html }} />
}
