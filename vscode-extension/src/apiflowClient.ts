export class ApiFlowClient {
  constructor(private readonly base: string) {}

  async workspace(): Promise<any> {
    const response = await fetch(`${this.base}/api/workspace`)
    if (!response.ok) throw new Error(`ApiFlow backend unavailable (${response.status})`)
    return response.json()
  }

  async runCollection(name: string, environment = '', tags: string[] = []): Promise<any> {
    const response = await fetch(`${this.base}/api/cli/run`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        collection: name,
        environment,
        folder: '',
        stopOnFailure: false,
        dataCsv: '',
        tags,
        parallel: false,
        delayMs: 0,
      }),
    })
    if (!response.ok) throw new Error(`Run failed (${response.status})`)
    return response.json()
  }

  async execute(request: Record<string, unknown>): Promise<any> {
    const response = await fetch(`${this.base}/api/execute`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
    if (!response.ok) throw new Error(`Execute failed (${response.status})`)
    return response.json()
  }

  async parseBru(content: string): Promise<any> {
    const response = await fetch(`${this.base}/api/bru/parse`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    })
    if (!response.ok) throw new Error(`Parse failed (${response.status})`)
    return response.json()
  }

  async exportBrunoFolder(collectionId: string): Promise<Array<{ path: string; content: string }>> {
    const response = await fetch(`${this.base}/api/collections/${collectionId}/export/bruno-folder`)
    if (!response.ok) throw new Error(`Export failed (${response.status})`)
    return response.json() as Promise<Array<{ path: string; content: string }>>
  }
}
