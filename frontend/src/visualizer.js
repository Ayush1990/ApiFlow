export function parseVisualizerData(raw) {
  if (raw == null || raw === '') {
    return {}
  }
  if (typeof raw === 'object') {
    return raw
  }
  return JSON.parse(raw)
}

export function renderVisualizerTemplate(template, data) {
  let out = template.replace(/\{\{#if ([\w.]+)\}\}([\s\S]*?)\{\{else\}\}([\s\S]*?)\{\{\/if\}\}/g, (_, key, yes, no) => (
    data[key] ? yes : no
  ))
  out = out.replace(/\{\{#if ([\w.]+)\}\}([\s\S]*?)\{\{\/if\}\}/g, (_, key, block) => (
    data[key] ? block : ''
  ))
  return out.replace(/\{\{([\w.]+)\}\}/g, (_, key) => (
    data[key] == null ? '' : String(data[key])
  ))
}
