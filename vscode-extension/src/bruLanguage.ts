import * as vscode from 'vscode'
import { ApiFlowClient } from './apiflowClient'

const BLOCK_SUGGESTIONS = [
  'meta', 'get', 'post', 'put', 'patch', 'delete', 'head', 'options', 'grpc',
  'headers', 'params', 'query', 'tags', 'docs', 'settings', 'vars', 'assert',
  'script:pre-request', 'script:post-response',
  'auth:bearer', 'auth:basic', 'auth:apikey', 'auth:oauth2', 'auth:awsv4', 'auth:digest', 'auth:ntlm',
  'body:json', 'body:text', 'body:xml', 'body:multipart', 'body:graphql',
]

const META_FIELDS = ['name:', 'type:', 'tags:']
const HTTP_FIELDS = ['url:', 'body:', 'auth:']

function localLint(document: vscode.TextDocument): vscode.Diagnostic[] {
  const diagnostics: vscode.Diagnostic[] = []
  const text = document.getText()
  if (!text.includes('meta {')) {
    diagnostics.push(new vscode.Diagnostic(
      new vscode.Range(0, 0, 0, 0),
      'Bruno requests should start with a meta { } block',
      vscode.DiagnosticSeverity.Warning,
    ))
  }
  let depth = 0
  const lines = text.split(/\r?\n/)
  for (let index = 0; index < lines.length; index += 1) {
    for (const ch of lines[index]) {
      if (ch === '{') depth += 1
      if (ch === '}') depth -= 1
    }
    if (depth < 0) {
      diagnostics.push(new vscode.Diagnostic(
        new vscode.Range(index, 0, index, lines[index].length),
        'Unexpected closing brace',
        vscode.DiagnosticSeverity.Error,
      ))
      depth = 0
    }
  }
  if (depth !== 0) {
    diagnostics.push(new vscode.Diagnostic(
      new vscode.Range(lines.length - 1, 0, lines.length - 1, lines[lines.length - 1].length),
      'Unbalanced braces in .bru file',
      vscode.DiagnosticSeverity.Error,
    ))
  }
  return diagnostics
}

export function registerBruLanguage(context: vscode.ExtensionContext, client: ApiFlowClient) {
  const collection = vscode.languages.createDiagnosticCollection('apiflow-bru')

  async function validate(document: vscode.TextDocument) {
    if (document.languageId !== 'bru') return
    const diagnostics = localLint(document)
    try {
      await client.parseBru(document.getText())
    } catch (err: any) {
      diagnostics.push(new vscode.Diagnostic(
        new vscode.Range(0, 0, 0, 1),
        err?.message || 'Could not parse .bru file',
        vscode.DiagnosticSeverity.Error,
      ))
    }
    collection.set(document.uri, diagnostics)
  }

  context.subscriptions.push(
    collection,
    vscode.languages.registerCompletionItemProvider('bru', {
      provideCompletionItems(document, position) {
        const linePrefix = document.lineAt(position).text.slice(0, position.character).trim()
        const items: vscode.CompletionItem[] = []
        if (!linePrefix || linePrefix.endsWith('{')) {
          for (const block of BLOCK_SUGGESTIONS) {
            const item = new vscode.CompletionItem(`${block} {`, vscode.CompletionItemKind.Snippet)
            item.insertText = new vscode.SnippetString(`${block} {\n  $0\n}`)
            items.push(item)
          }
        }
        if (linePrefix.startsWith('meta')) {
          for (const field of META_FIELDS) {
            items.push(new vscode.CompletionItem(field, vscode.CompletionItemKind.Field))
          }
        }
        if (/^(get|post|put|patch|delete|head|options|grpc)\s*\{/.test(document.lineAt(Math.max(0, position.line - 1)).text.trim()) || HTTP_FIELDS.some((field) => linePrefix.startsWith(field))) {
          for (const field of HTTP_FIELDS) {
            items.push(new vscode.CompletionItem(field, vscode.CompletionItemKind.Field))
          }
        }
        return items
      },
    }, '{', ':'),
    vscode.workspace.onDidOpenTextDocument((doc) => { validate(doc).catch(() => {}) }),
    vscode.workspace.onDidChangeTextDocument((event) => { validate(event.document).catch(() => {}) }),
    vscode.workspace.onDidSaveTextDocument((doc) => { validate(doc).catch(() => {}) }),
  )

  for (const document of vscode.workspace.textDocuments) {
    if (document.languageId === 'bru') validate(document).catch(() => {})
  }
}
