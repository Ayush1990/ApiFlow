#!/usr/bin/env node

import { spawn } from 'node:child_process'
import path from 'node:path'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { isBruCollection, runLocalCollection } from './bru-runner.js'

const BASE = process.env.APIFLOW_URL || 'http://localhost:8080'
const __dirname = path.dirname(fileURLToPath(import.meta.url))

function usage() {
  console.log(`Usage:
  apiflow run <collection|path> [--env name] [--global-env name] [--folder name] [--stop] [--csv file] [--dataset id] [--tag tag] [--parallel] [--delay ms] [--local] [--mock url] [--share]
  apiflow run-local <path> [--env name] [--global-env name] [--stop] [--tag tag] [--delay ms]
  apiflow request <collection> <request> [--env name]
  apiflow export <collection> [--format bruno|bruno-folder|postman|openapi|opencollection]
  apiflow import <file>
  apiflow grpc reflect <url>
  apiflow report <collection> [--format html|junit] [--out file]
  apiflow monitor run <id>
  apiflow performance run <collection> [--request id] [--env name] [--vu n] [--iterations n] [--ramp ms] [--dataset id]
  apiflow performance compare <leftId> <rightId>
  apiflow mock start <collection> [--port n]
  apiflow mock stop <collection>
  apiflow mock log
  apiflow dataset list
  apiflow dataset query <id> [--sql statement]
  apiflow flows list
  apiflow flows run <id>
  apiflow flows deploy <id>
  apiflow runner start
  apiflow webhook trigger <id>
  apiflow search requests <query>
  apiflow spec lint <id>
  apiflow sdk generate <collection> [--language typescript|python]
  apiflow inventory capture <file> [--name app]

Environment:
  APIFLOW_URL          Backend URL (default http://localhost:8080)
  APIFLOW_AUTO_START   When 1/true, auto-start backend jar if missing

Local mode:
  When <collection> is a directory containing .bru files, runs without backend.
  Use --local to force local mode, or apiflow run-local <path>.`)
  process.exit(0)
}

async function request(path, options = {}) {
  const response = await fetch(`${BASE}${path}`, options)
  const text = await response.text()
  if (!response.ok) throw new Error(text || response.statusText)
  try { return JSON.parse(text) } catch { return text }
}

function findBackendJar() {
  const candidates = [
    path.resolve(__dirname, '../../backend/target/apiflow-0.0.1-SNAPSHOT.jar'),
    path.resolve(process.cwd(), 'backend/target/apiflow-0.0.1-SNAPSHOT.jar'),
  ]
  return candidates.find((file) => fs.existsSync(file))
}

async function startBackend() {
  const jar = findBackendJar()
  if (!jar) {
    throw new Error('Backend jar not found. Build with: cd backend && mvn -DskipTests package')
  }
  const child = spawn('java', ['-jar', jar], { stdio: 'ignore', detached: true })
  child.unref()
  for (let attempt = 0; attempt < 45; attempt += 1) {
    try {
      await fetch(`${BASE}/api/workspace`)
      return
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 2000))
    }
  }
  throw new Error('Timed out waiting for auto-started backend on ' + BASE)
}

async function requireBackend() {
  try {
    await fetch(`${BASE}/api/workspace`)
    return
  } catch {
    if (process.env.APIFLOW_AUTO_START === '1' || process.env.APIFLOW_AUTO_START === 'true') {
      console.error('Starting ApiFlow backend…')
      await startBackend()
      return
    }
    console.error('ApiFlow backend is not running on', BASE)
    console.error('Set APIFLOW_AUTO_START=1 to launch the bundled jar automatically.')
    process.exit(1)
  }
}

function parseArgs(argv) {
  const positional = []
  const flags = {}
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]
    if (token.startsWith('--')) {
      const key = token.slice(2)
      const next = argv[index + 1]
      if (next && !next.startsWith('--')) {
        flags[key] = next
        index += 1
      } else {
        flags[key] = true
      }
    } else {
      positional.push(token)
    }
  }
  return { positional, flags }
}

function collectTags(flags) {
  if (!flags.tag) return []
  return Array.isArray(flags.tag) ? flags.tag : [flags.tag]
}

async function runLocalTarget(target, flags) {
  let dataCsv = ''
  if (flags.csv) {
    dataCsv = await (await import('node:fs/promises')).readFile(flags.csv, 'utf8')
  }
  const report = await runLocalCollection(target, {
    environment: flags.env || '',
    globalEnvironment: flags['global-env'] || '',
    stopOnFailure: !!flags.stop,
    delayMs: Number(flags.delay || 0),
    tags: collectTags(flags),
    dataCsv,
  })
  console.log(JSON.stringify(report, null, 2))
  process.exit(report.failed > 0 ? 1 : 0)
}

async function main() {
  const [cmd, ...rest] = process.argv.slice(2)
  if (!cmd || cmd === '-h' || cmd === '--help') usage()
  const { positional, flags } = parseArgs(rest)

  if (cmd === 'run-local') {
    const target = positional[0]
    if (!target) throw new Error('Collection path is required')
    await runLocalTarget(path.resolve(target), flags)
  }

  if (cmd === 'run') {
    const collection = positional[0]
    if (!collection) throw new Error('Collection name or path is required')
    const resolved = path.resolve(collection)
    const forceLocal = !!flags.local
    if (forceLocal || (fs.existsSync(resolved) && await isBruCollection(resolved))) {
      await runLocalTarget(resolved, flags)
    }
    await requireBackend()
    const tags = collectTags(flags)
    const body = {
      collection,
      environment: flags.env || '',
      globalEnvironment: flags['global-env'] || '',
      folder: flags.folder || '',
      stopOnFailure: !!flags.stop,
      dataCsv: flags.csv ? await (await import('node:fs/promises')).readFile(flags.csv, 'utf8') : '',
      parallel: !!flags.parallel,
      delayMs: Number(flags.delay || 0),
      tags,
      datasetId: flags.dataset || '',
      mockBaseUrl: flags.mock || '',
      shareResults: !!flags.share,
    }
    const report = await request('/api/cli/run', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    console.log(JSON.stringify(report, null, 2))
    process.exit(report.failed > 0 ? 1 : 0)
  }

  if (cmd === 'export') {
    const collection = positional[0]
    if (!collection) throw new Error('Collection name is required')
    await requireBackend()
    const workspace = await request('/api/workspace')
    const match = (workspace.collections || []).find((item) => item.name === collection || item.id === collection)
    if (!match) throw new Error(`Collection not found: ${collection}`)
    const format = flags.format || 'bruno'
    const exportPath = format === 'bruno-folder'
      ? `/api/collections/${match.id}/export/bruno-folder`
      : `/api/collections/${match.id}/export/${format === 'bruno' ? 'bruno' : format}`
    const result = await fetch(`${BASE}${exportPath}`)
    console.log(await result.text())
    return
  }

  if (cmd === 'import') {
    const file = positional[0]
    if (!file) throw new Error('File is required')
    await requireBackend()
    const content = await (await import('node:fs/promises')).readFile(file, 'utf8')
    const result = await request('/api/import', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content }) })
    console.log(JSON.stringify(result, null, 2))
    return
  }

  if (cmd === 'grpc') {
    const sub = positional[0]
    const url = positional[1]
    if (sub !== 'reflect' || !url) throw new Error('Usage: apiflow grpc reflect <url>')
    await requireBackend()
    const result = await request('/api/grpc/reflect', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url }) })
    console.log(JSON.stringify(result, null, 2))
    return
  }

  if (cmd === 'request') {
    const [collection, requestName] = positional
    if (!collection || !requestName) throw new Error('Usage: apiflow request <collection> <request>')
    await requireBackend()
    const workspace = await request('/api/workspace')
    const match = (workspace.collections || []).find((item) => item.name === collection || item.id === collection)
    if (!match) throw new Error(`Collection not found: ${collection}`)
    const found = (match.requests || []).find((item) => item.name === requestName || item.id === requestName)
    if (!found) throw new Error(`Request not found: ${requestName}`)
    const env = (workspace.environments || []).find((item) => item.name === flags.env || item.id === flags.env)
    const result = await request('/api/execute', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...found, collectionId: match.id, environmentId: env?.id || '' }) })
    console.log(JSON.stringify({ status: result.status, body: result.body }, null, 2))
    process.exit(result.status >= 400 || result.status === 0 ? 1 : 0)
  }

  if (cmd === 'performance') {
    const sub = positional[0]
    await requireBackend()
    if (sub === 'compare') {
      const [leftId, rightId] = positional.slice(1)
      if (!leftId || !rightId) throw new Error('Usage: apiflow performance compare <leftId> <rightId>')
      console.log(JSON.stringify(await request(`/api/platform/performance/compare?leftId=${leftId}&rightId=${rightId}`), null, 2))
      return
    }
    if (sub !== 'run') throw new Error('Usage: apiflow performance run <collection>')
    const collection = positional[1]
    const workspace = await request('/api/workspace')
    const match = (workspace.collections || []).find((item) => item.name === collection || item.id === collection)
    if (!match) throw new Error(`Collection not found: ${collection}`)
    const env = (workspace.environments || []).find((item) => item.name === flags.env || item.id === flags.env)
    const report = await request('/api/platform/performance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        collectionId: match.id,
        requestId: flags.request || '',
        environmentId: env?.id || '',
        virtualUsers: Number(flags.vu || 5),
        iterations: Number(flags.iterations || 1),
        rampUpMs: Number(flags.ramp || 0),
        datasetId: flags.dataset || '',
      }),
    })
    console.log(JSON.stringify(report, null, 2))
    process.exit(report.failure > 0 ? 1 : 0)
  }

  if (cmd === 'mock') {
    const sub = positional[0]
    await requireBackend()
    if (sub === 'log') {
      console.log(JSON.stringify(await request('/api/mock/log'), null, 2))
      return
    }
    if (sub === 'stop') {
      await request('/api/mock', { method: 'DELETE' })
      console.log('Mock stopped')
      return
    }
    const collection = positional[1]
    const workspace = await request('/api/workspace')
    const match = (workspace.collections || []).find((item) => item.name === collection || item.id === collection)
    if (!match) throw new Error(`Collection not found: ${collection}`)
    if (sub !== 'start') throw new Error('Usage: apiflow mock start|stop|log')
    const state = await request(`/api/collections/${match.id}/mock`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ port: Number(flags.port || 4010) }) })
    console.log(JSON.stringify(state, null, 2))
    return
  }

  if (cmd === 'dataset') {
    const sub = positional[0]
    await requireBackend()
    if (sub === 'list') {
      const workspace = await request('/api/workspace')
      console.log(JSON.stringify((workspace.datasets || []).map((item) => ({ id: item.id, name: item.name, sourceType: item.sourceType })), null, 2))
      return
    }
    if (sub !== 'query') throw new Error('Usage: apiflow dataset list|query <id>')
    const id = positional[1]
    if (!id) throw new Error('Dataset id is required')
    console.log(JSON.stringify(await request(`/api/platform/datasets/${id}/query`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sql: flags.sql || '' }) }), null, 2))
    return
  }

  if (cmd === 'runner') {
    if (positional[0] !== 'start') throw new Error('Usage: apiflow runner start')
    await requireBackend()
    console.log('Private runner is polling for monitors. Leave this process running.')
    while (true) {
      try {
        const claimed = await request('/api/platform/runners/claim', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })
        console.log(JSON.stringify({ claimed: claimed.name || claimed.id, at: new Date().toISOString() }))
      } catch (error) {
        console.log(error.message)
      }
      await new Promise((resolve) => setTimeout(resolve, 15000))
    }
  }

  if (cmd === 'flows') {
    const sub = positional[0]
    await requireBackend()
    const workspace = await request('/api/workspace')
    if (sub === 'list') {
      console.log(JSON.stringify((workspace.flows || []).map((item) => ({ id: item.id, name: item.name, deployed: item.deployed })), null, 2))
      return
    }
    if (sub === 'deploy') {
      const id = positional[1]
      if (!id) throw new Error('Usage: apiflow flows deploy <id>')
      console.log(JSON.stringify(await request(`/api/platform/flows/${id}/deploy`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ port: Number(flags.port) || 4020 }) }), null, 2))
      return
    }
    if (sub !== 'run') throw new Error('Usage: apiflow flows list|run <id>|deploy <id>')
    const id = positional[1]
    console.log(JSON.stringify(await request(`/api/platform/flows/${id}/run`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }), null, 2))
    return
  }

  if (cmd === 'webhook') {
    if (positional[0] !== 'trigger' || !positional[1]) throw new Error('Usage: apiflow webhook trigger <id>')
    await requireBackend()
    console.log(JSON.stringify(await request(`/hooks/${positional[1]}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }), null, 2))
    return
  }

  if (cmd === 'search') {
    if (positional[0] !== 'requests' || !positional[1]) throw new Error('Usage: apiflow search requests <query>')
    await requireBackend()
    const query = positional.slice(1).join(' ').toLowerCase()
    const workspace = await request('/api/workspace')
    const hits = []
    for (const collection of workspace.collections || []) {
      for (const item of collection.requests || []) {
        if (`${item.name} ${item.url} ${item.method}`.toLowerCase().includes(query)) {
          hits.push({ collection: collection.name, name: item.name, method: item.method, url: item.url })
        }
      }
    }
    console.log(JSON.stringify(hits, null, 2))
    return
  }

  if (cmd === 'spec') {
    if (positional[0] !== 'lint' || !positional[1]) throw new Error('Usage: apiflow spec lint <id>')
    await requireBackend()
    console.log(JSON.stringify(await request(`/api/platform/specs/${positional[1]}/lint`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }), null, 2))
    return
  }

  if (cmd === 'sdk') {
    if (positional[0] !== 'generate' || !positional[1]) throw new Error('Usage: apiflow sdk generate <collection>')
    await requireBackend()
    const workspace = await request('/api/workspace')
    const match = (workspace.collections || []).find((item) => item.name === positional[1] || item.id === positional[1])
    if (!match) throw new Error(`Collection not found: ${positional[1]}`)
    const result = await request('/api/platform/sdk', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ collectionId: match.id, language: flags.language || 'typescript' }) })
    console.log(result.source || JSON.stringify(result, null, 2))
    return
  }

  if (cmd === 'inventory') {
    if (positional[0] !== 'capture' || !positional[1]) throw new Error('Usage: apiflow inventory capture <file>')
    await requireBackend()
    const calls = JSON.parse(await (await import('node:fs/promises')).readFile(positional[1], 'utf8'))
    console.log(JSON.stringify(await request('/api/platform/inventory/capture', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: flags.name || 'Application', environment: flags.env || 'local', calls }) }), null, 2))
    return
  }

  if (cmd === 'monitor') {
    const sub = positional[0]
    const monitorId = positional[1]
    if (sub !== 'run' || !monitorId) throw new Error('Usage: apiflow monitor run <id>')
    await requireBackend()
    const run = await request(`/api/platform/monitors/${monitorId}/run`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })
    console.log(JSON.stringify(run, null, 2))
    process.exit(run.failed > 0 ? 1 : 0)
  }

  if (cmd === 'report') {
    const collection = positional[0]
    if (!collection) throw new Error('Collection name is required')
    await requireBackend()
    const body = { collection, environment: flags.env || '', folder: flags.folder || '', stopOnFailure: !!flags.stop, dataCsv: '' }
    const endpoint = flags.format === 'junit' ? '/api/run/report/junit' : '/api/run/report/html'
    const response = await fetch(`${BASE}${endpoint}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    const text = await response.text()
    if (flags.out) {
      await (await import('node:fs/promises')).writeFile(flags.out, text)
      console.log('Wrote', flags.out)
    } else {
      console.log(text)
    }
    return
  }

  usage()
}

main().catch((error) => {
  console.error(error.message || error)
  process.exit(1)
})
