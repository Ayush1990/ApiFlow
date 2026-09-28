import * as vscode from 'vscode'
import * as fs from 'fs'
import { ApiFlowClient } from './apiflowClient'
import { registerBruLanguage } from './bruLanguage'

const BASE = process.env.APIFLOW_URL || 'http://localhost:8080'

type TreeKind = 'collection' | 'folder' | 'request'

class CollectionTreeProvider implements vscode.TreeDataProvider<TreeItem> {
  private readonly client: ApiFlowClient
  private readonly emitter = new vscode.EventEmitter<void>()
  readonly onDidChangeTreeData = this.emitter.event

  constructor(client: ApiFlowClient) {
    this.client = client
  }

  refresh(): void {
    this.emitter.fire()
  }

  getTreeItem(element: TreeItem): vscode.TreeItem {
    return element
  }

  async getChildren(element?: TreeItem): Promise<TreeItem[]> {
    const workspace = await this.client.workspace()
    if (!element) {
      return (workspace.collections || []).map((collection: { id: string; name: string }) =>
        new TreeItem(collection.name, vscode.TreeItemCollapsibleState.Expanded, 'collection', collection.id, collection.name, collection.id, ''),
      )
    }
    if (element.kind === 'collection') {
      const collection = (workspace.collections || []).find((item: { id: string }) => item.id === element.id)
      const items: TreeItem[] = []
      for (const folder of collection?.folders || []) {
        if (!folder.parentId) {
          items.push(new TreeItem(folder.name, vscode.TreeItemCollapsibleState.Collapsed, 'folder', folder.id, element.collectionName, element.id, folder.id))
        }
      }
      for (const request of collection?.requests || []) {
        if (!request.folderId) {
          items.push(new TreeItem(`${request.method || 'GET'} ${request.name}`, vscode.TreeItemCollapsibleState.None, 'request', request.id, element.collectionName, element.id, '', request))
        }
      }
      return items
    }
    if (element.kind === 'folder') {
      const collection = (workspace.collections || []).find((item: { id: string }) => item.id === element.collectionId)
      const items: TreeItem[] = []
      for (const folder of collection?.folders || []) {
        if (element.folderId === folder.parentId) {
          items.push(new TreeItem(folder.name, vscode.TreeItemCollapsibleState.Collapsed, 'folder', folder.id, element.collectionName, element.collectionId, folder.id))
        }
      }
      for (const request of collection?.requests || []) {
        if (element.folderId === request.folderId) {
          items.push(new TreeItem(`${request.method || 'GET'} ${request.name}`, vscode.TreeItemCollapsibleState.None, 'request', request.id, element.collectionName, element.collectionId, request.folderId || '', request))
        }
      }
      return items
    }
    return []
  }
}

class TreeItem extends vscode.TreeItem {
  constructor(
    label: string,
    collapsibleState: vscode.TreeItemCollapsibleState,
    readonly kind: TreeKind,
    readonly id: string,
    readonly collectionName: string,
    readonly collectionId: string,
    readonly folderId: string,
    readonly request?: any,
  ) {
    super(label, collapsibleState)
    this.contextValue = kind
    if (kind === 'collection') {
      this.command = { command: 'apiflow.runCollection', title: 'Run collection', arguments: [collectionName] }
    } else if (kind === 'request') {
      this.command = { command: 'apiflow.sendRequest', title: 'Send request', arguments: [collectionId, id, request] }
    }
  }
}

export function activate(context: vscode.ExtensionContext) {
  const client = new ApiFlowClient(BASE)
  const tree = new CollectionTreeProvider(client)
  const output = vscode.window.createOutputChannel('ApiFlow')

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('apiflowCollections', tree),
    vscode.commands.registerCommand('apiflow.refreshCollections', () => tree.refresh()),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('apiflow.openWorkspace', async () => {
      const workspace = await client.workspace()
      vscode.window.showInformationMessage(`ApiFlow workspace loaded with ${workspace.collections?.length || 0} collections`)
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('apiflow.pickEnvironment', async () => {
      const workspace = await client.workspace()
      const names = ['(none)', ...(workspace.environments || []).map((env: { name: string }) => env.name)]
      const picked = await vscode.window.showQuickPick(names, { placeHolder: 'Active environment' })
      if (!picked) return
      context.workspaceState.update('apiflow.environment', picked === '(none)' ? '' : picked)
      vscode.window.showInformationMessage(`ApiFlow environment: ${picked}`)
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('apiflow.runCollection', async (collectionName?: string) => {
      const workspace = await client.workspace()
      const names = (workspace.collections || []).map((c: { name: string }) => c.name)
      const picked = collectionName || await vscode.window.showQuickPick(names, { placeHolder: 'Select collection to run' })
      if (!picked) return
      const environment = context.workspaceState.get<string>('apiflow.environment') || ''
      const report = await client.runCollection(picked, environment)
      output.appendLine(JSON.stringify(report, null, 2))
      output.show(true)
      vscode.window.showInformationMessage(`Run finished: ${report.passed} passed, ${report.failed} failed`)
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('apiflow.sendRequest', async (collectionId?: string, requestId?: string, request?: any) => {
      const environment = context.workspaceState.get<string>('apiflow.environment') || ''
      const editor = vscode.window.activeTextEditor
      if (editor?.document.fileName.endsWith('.bru')) {
        const parsed = await client.parseBru(editor.document.getText())
        const result = await client.execute({
          ...parsed,
          collectionId: collectionId || '',
          requestId: requestId || parsed.id || '',
          environmentId: environment,
        })
        output.appendLine(JSON.stringify(result, null, 2))
        output.show(true)
        vscode.window.showInformationMessage(`Response ${result.status}`)
        return
      }
      if (!request) {
        vscode.window.showWarningMessage('Pick a request from the ApiFlow tree or open a .bru file')
        return
      }
      const result = await client.execute({
        ...request,
        collectionId,
        requestId,
        environmentId: environment,
      })
      output.appendLine(JSON.stringify(result, null, 2))
      output.show(true)
      vscode.window.showInformationMessage(`Response ${result.status}`)
    }),
  )

  context.subscriptions.push(output)
  registerBruLanguage(context, client)

  const watcher = vscode.workspace.createFileSystemWatcher('**/*.bru')
  watcher.onDidChange((uri) => {
    if (fs.existsSync(uri.fsPath)) tree.refresh()
  })
  context.subscriptions.push(watcher)
}

export function deactivate() {}
