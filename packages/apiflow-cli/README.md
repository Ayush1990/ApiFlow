# @apiflow/cli

Command-line interface for **[ApiFlow](https://github.com/apiflow/apiflow)** — run API collections in your terminal, in CI, or against a local ApiFlow backend. The CLI speaks Bruno-compatible **`.bru`** folders (standalone) and the ApiFlow workspace API (backend mode).

**License:** [Apache License 2.0](../../LICENSE)

---

## Table of contents

- [Overview](#overview)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Architecture](#architecture)
- [Environment variables](#environment-variables)
- [Commands reference](#commands-reference)
  - [run](#run)
  - [run-local](#run-local)
  - [request](#request)
  - [report](#report)
  - [export / import](#export--import)
  - [grpc reflect](#grpc-reflect)
  - [mock](#mock)
  - [monitor](#monitor)
  - [performance](#performance)
  - [dataset](#dataset)
  - [flows](#flows)
  - [runner](#runner)
  - [webhook](#webhook)
  - [search](#search)
  - [spec lint](#spec-lint)
  - [sdk generate](#sdk-generate)
  - [inventory capture](#inventory-capture)
- [Flags reference](#flags-reference)
- [Local mode (standalone)](#local-mode-standalone)
  - [Collection layout](#collection-layout)
  - [Environments & variables](#environments--variables)
  - [Tags & filtering](#tags--filtering)
  - [CSV data-driven runs](#csv-data-driven-runs)
  - [Auth support](#auth-support)
  - [Scripts & assertions](#scripts--assertions)
- [Backend mode](#backend-mode)
- [Run report JSON](#run-report-json)
- [Exit codes](#exit-codes)
- [CI / GitHub Actions](#ci--github-actions)
- [Troubleshooting](#troubleshooting)
- [Development & publishing](#development--publishing)
- [Related packages](#related-packages)

---

## Overview

| Capability | Local mode | Backend mode |
|------------|:----------:|:------------:|
| Run `.bru` collection from disk | ✅ | — |
| Run workspace collection by name | — | ✅ |
| Pre/post-request scripts | ✅ (subset) | ✅ (full GraalVM) |
| Bearer / Basic / API key / OAuth2 | ✅ | ✅ |
| Digest / NTLM / AWS SigV4 | ❌ | ✅ |
| Folder filter | ❌ | ✅ |
| Parallel runner | ❌ | ✅ |
| Mock server / perf tests / flows | ❌ | ✅ |
| HTML / JUnit reports | ❌ | ✅ |
| gRPC / WebSocket / HTTP/3 | ❌ | ✅ |

**Local mode** uses Node.js `fetch` and a lightweight `.bru` parser (`bru-runner.js`). No Java required.

**Backend mode** calls the ApiFlow Spring Boot server (default `http://localhost:8080`) for the full feature set: GraalVM scripts, `pm.visualizer`, advanced auth, mocks, monitors, datasets, and more.

---

## Installation

### npm (global)

```bash
npm install -g @apiflow/cli
apiflow --help
```

Requires **Node.js ≥ 18**.

### npx (no install)

```bash
npx @apiflow/cli run ./collections/my-api --local --env dev
```

### From the ApiFlow monorepo

```bash
# Clone the repo, then either:
node packages/apiflow-cli/index.js run ./path/to/collection --local

# Or use the root shell wrapper (backend commands only):
chmod +x ./apiflow
./apiflow run "Sample API" --env dev
```

The root `./apiflow` script is a thin `curl` wrapper for backend mode. Prefer `packages/apiflow-cli/index.js` for local `.bru` runs and all subcommands.

---

## Quick start

### 1. Run a Bruno folder (no backend)

```bash
apiflow run ./collections/petstore --env dev --tag smoke --stop
```

If the path exists and contains `.bru` files, the CLI auto-detects **local mode**.

### 2. Run against ApiFlow backend

```bash
# Start backend first (separate terminal):
cd backend && mvn spring-boot:run

# Then:
export APIFLOW_URL=http://localhost:8080
apiflow run "Sample API" --env dev --stop
```

### 3. Auto-start backend from monorepo

```bash
cd backend && mvn -DskipTests package
export APIFLOW_AUTO_START=1
apiflow run "Sample API"
```

The CLI launches `backend/target/apiflow-0.0.1-SNAPSHOT.jar` and waits for `/api/workspace`.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  index.js (CLI entry)                                       │
│  • parseArgs, requireBackend, APIFLOW_AUTO_START            │
│  • Routes commands → fetch(BASE + /api/...)                 │
└───────────────┬─────────────────────────┬───────────────────┘
                │                         │
     path is .bru folder                  │  collection name
                │                         │
                ▼                         ▼
┌───────────────────────────┐   ┌─────────────────────────────┐
│  bru-runner.js            │   │  ApiFlow backend :8080      │
│  • parseBru()             │   │  POST /api/cli/run          │
│  • interpolate {{vars}}   │   │  POST /api/execute          │
│  • fetch() each request   │   │  GraalVM ScriptRunner       │
│  • vm.runInNewContext()   │   │  Full protocol support      │
└───────────────────────────┘   └─────────────────────────────┘
```

**Published npm package** includes only:

- `index.js`
- `bru-runner.js`
- `README.md`

No backend JAR is bundled on npm; use `APIFLOW_AUTO_START` only when running from the monorepo with a built JAR.

---

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `APIFLOW_URL` | `http://localhost:8080` | Base URL for backend API calls |
| `APIFLOW_AUTO_START` | — | Set to `1` or `true` to launch `backend/target/apiflow-*.jar` if backend is unreachable |

Example:

```bash
export APIFLOW_URL=https://apiflow.internal:8080
export APIFLOW_AUTO_START=1
apiflow run "Regression Suite" --env staging
```

---

## Commands reference

Run `apiflow` with no arguments (or `-h` / `--help`) to print built-in usage.

### run

Run a collection by **filesystem path** (local) or **workspace name** (backend).

```bash
apiflow run <collection|path> [flags]
```

**Examples:**

```bash
# Auto local mode (directory with .bru files)
apiflow run ./collections/api --env staging --tag smoke --stop

# Force local mode even if name collides
apiflow run ./collections/api --local

# Backend mode (collection name in workspace)
apiflow run "Sample API" --env dev --folder Users --parallel

# Data-driven backend run
apiflow run "Users API" --csv ./data/users.csv --env test

# Run against mock server
apiflow run "Sample API" --mock http://127.0.0.1:4010
```

**Backend API:** `POST /api/cli/run`

---

### run-local

Always run from disk; never contacts the backend.

```bash
apiflow run-local <path> [flags]
```

**Examples:**

```bash
apiflow run-local ./bruno/my-api --env dev
apiflow run-local ./bruno/my-api --csv ./data/rows.csv --tag "@tag(smoke) && health"
apiflow run-local ./bruno/my-api --global-env shared --delay 200 --stop
```

Supports: `--env`, `--global-env`, `--stop`, `--tag`, `--delay`, `--csv`.

---

### request

Execute a **single request** from a workspace collection (backend only).

```bash
apiflow request <collection> <request-name> [--env name]
```

**Example:**

```bash
apiflow request "Sample API" "Get todo" --env dev
```

Prints `{ status, body }`. Exits `1` if status ≥ 400 or 0.

**Backend API:** `POST /api/execute`

---

### report

Generate an HTML or JUnit report after a collection run (backend only).

```bash
apiflow report <collection> [--format html|junit] [--out file] [--env name] [--folder name] [--stop]
```

**Examples:**

```bash
apiflow report "Sample API" --format html --out ./reports/run.html
apiflow report "Sample API" --format junit --out ./reports/junit.xml --env ci
```

**Backend API:** `POST /api/run/report/html` or `/api/run/report/junit`

---

### export / import

**Export** a workspace collection (backend only):

```bash
apiflow export <collection> [--format bruno|bruno-folder|postman|openapi|opencollection]
```

| Format | Output |
|--------|--------|
| `bruno` (default) | Single `.bru` collection file |
| `bruno-folder` | Folder of `.bru` files |
| `postman` | Postman Collection v2 JSON |
| `openapi` | OpenAPI 3 document |
| `opencollection` | OpenCollection JSON |

**Import** a file into the workspace:

```bash
apiflow import ./collection.json
```

Detects Postman, OpenAPI, Bruno, and related formats.

**Backend API:** `GET /api/collections/{id}/export/*`, `POST /api/import`

---

### grpc reflect

List gRPC services from a reflection-enabled server:

```bash
apiflow grpc reflect grpc://localhost:50051
```

**Backend API:** `POST /api/grpc/reflect`

---

### mock

Start, stop, or inspect the ApiFlow mock server (backend only).

```bash
apiflow mock start <collection> [--port 4010]
apiflow mock stop
apiflow mock log
```

**Example:**

```bash
apiflow mock start "Sample API" --port 4010
# Use with: apiflow run "Sample API" --mock http://127.0.0.1:4010
apiflow mock stop
```

---

### monitor

Run a configured uptime monitor once:

```bash
apiflow monitor run <monitor-id>
```

Exits `1` if the monitor run reports failures.

**Backend API:** `POST /api/platform/monitors/{id}/run`

---

### performance

Load-test a collection or compare two perf runs (backend only).

```bash
apiflow performance run <collection> \
  [--request id] [--env name] [--vu 5] [--iterations 10] [--ramp 1000] [--dataset id]

apiflow performance compare <leftRunId> <rightRunId>
```

**Example:**

```bash
apiflow performance run "Sample API" --request req-get-todo --vu 20 --iterations 5 --env dev
```

**Backend API:** `POST /api/platform/performance`, `GET /api/platform/performance/compare`

---

### dataset

List or query workspace datasets (backend only).

```bash
apiflow dataset list
apiflow dataset query <dataset-id> [--sql "SELECT * FROM rows LIMIT 10"]
```

---

### flows

List, run, or deploy automation flows (backend only).

```bash
apiflow flows list
apiflow flows run <flow-id>
apiflow flows deploy <flow-id> [--port 4020]
```

---

### runner

Start a private runner process that polls for monitor jobs (backend only).

```bash
apiflow runner start
```

Long-running process; polls `POST /api/platform/runners/claim` every 15 seconds.

---

### webhook

Trigger a workspace webhook by ID:

```bash
apiflow webhook trigger <webhook-id>
```

**Backend API:** `POST /hooks/{id}`

---

### search

Search requests across all workspace collections:

```bash
apiflow search requests <query>
```

Case-insensitive match against request name, URL, and method.

---

### spec lint

Lint an OpenAPI spec stored in the workspace:

```bash
apiflow spec lint <spec-id>
```

**Backend API:** `POST /api/platform/specs/{id}/lint`

---

### sdk generate

Generate a typed API client from a collection:

```bash
apiflow sdk generate <collection> [--language typescript|python]
```

Prints generated source to stdout.

**Backend API:** `POST /api/platform/sdk`

---

### inventory capture

Import captured HTTP calls as an inventory app:

```bash
apiflow inventory capture ./calls.json [--name "My App"] [--env local]
```

The JSON file should contain an array of call objects.

**Backend API:** `POST /api/platform/inventory/capture`

---

## Flags reference

Flags apply to `run`, `run-local`, and (where noted) `report`.

| Flag | Commands | Description |
|------|----------|-------------|
| `--env <name>` | run, run-local, report, request, performance | Environment name (`.bru` or workspace env) |
| `--global-env <name>` | run, run-local | Global environment layered before collection env |
| `--folder <name>` | run, report | Run only requests in folder (backend only) |
| `--stop` | run, run-local, report | Stop on first failure |
| `--csv <file>` | run, run-local | CSV data file — one iteration per row |
| `--tag <expr>` | run, run-local | Tag filter (repeatable via multiple `--tag`) |
| `--delay <ms>` | run, run-local | Delay between requests |
| `--local` | run | Force standalone mode for a path |
| `--parallel` | run | Parallel collection run (backend only) |
| `--dataset <id>` | run, performance | Workspace dataset ID (backend only) |
| `--mock <url>` | run | Route requests through mock base URL (backend) |
| `--share` | run | Share run results (backend) |
| `--format <type>` | export, report | Output format |
| `--out <file>` | report | Write report to file instead of stdout |
| `--language <lang>` | sdk | `typescript` or `python` |
| `--port <n>` | mock, flows | Server port |
| `--vu`, `--iterations`, `--ramp`, `--request` | performance | Load test tuning |

---

## Local mode (standalone)

Local mode is triggered when:

1. You use `apiflow run-local <path>`, or
2. `apiflow run <path>` resolves to an existing directory containing `.bru` files, or
3. You pass `--local` explicitly.

Implementation: `bru-runner.js` → `runLocalCollection()`.

### Collection layout

Bruno-compatible folder structure:

```
my-collection/
├── bruno.json              # optional collection metadata
├── collection.bru          # optional (skipped at runtime)
├── environments/
│   ├── dev.bru
│   └── staging.bru
├── users/
│   ├── folder.bru          # optional (skipped)
│   └── get-user.bru
└── get-todo.bru
```

Each request file uses Bruno block syntax:

```bru
meta {
  name: Get todo
  tags: smoke, regression
}

get {
  url: {{baseUrl}}/todos/1
}

headers {
  Accept: application/json
}

assert {
  status: eq 200
  body:contains "userId"
}

script:pre-request {
  bru.setVar("timestamp", Date.now());
}

script:post-response {
  bru.setVar("todoId", JSON.parse(res.body).id);
}
```

Supported blocks: `meta`, HTTP methods (`get`, `post`, …), `headers`, `params` / `query`, `tags`, `auth:*`, `body:*`, `multipart`, `assert`, `script:pre-request`, `script:post-response`.

Folder entries (`type: folder` in meta) are skipped.

### Environments & variables

Environment files live in `environments/<name>.bru` or `environments/<name>.json` (relative to collection or parent workspace).

**`.bru` environment:**

```bru
vars {
  baseUrl: https://api.example.com
  token: secret-value
}
```

**`.json` environment:**

```json
{
  "variables": [
    { "key": "baseUrl", "value": "https://api.example.com" }
  ]
}
```

Variable merge order:

1. Defaults (`baseUrl: http://localhost:8080`)
2. `--global-env` variables
3. `--env` variables
4. CSV row variables (per iteration)

Use `{{variableName}}` in URLs, headers, and bodies.

### Tags & filtering

Filter which requests run with `--tag`:

```bash
apiflow run-local ./api --tag smoke
apiflow run-local ./api --tag "smoke && health"
apiflow run-local ./api --tag "@tag(regression) || smoke"
```

Tag logic (`matchesTags` in `bru-runner.js`):

- `&&` — all parts must match
- `||` — any part matches
- `@tag(name)` — Bruno-style tag call
- Plain `name` — case-insensitive tag match

Requests without tags are **excluded** when any `--tag` filter is set.

### CSV data-driven runs

Provide a CSV with a header row; each row becomes one full pass over the collection:

```csv
userId,role
1,admin
2,guest
```

```bash
apiflow run-local ./api --csv ./data/users.csv --env test
```

Column headers become variables (`{{userId}}`, `{{role}}`).

### Auth support

| Auth type | Local mode | Notes |
|-----------|:----------:|-------|
| `none` / `inherit` | ✅ | Default |
| `bearer` | ✅ | `auth:bearer { token: ... }` |
| `basic` | ✅ | Username / password |
| `apikey` | ✅ | Header or query (`in: query`) |
| `oauth2` | ✅ | Client credentials or password grant |
| `digest` | ❌ | Use backend mode |
| `ntlm` | ❌ | Use backend mode |
| `aws` / `awsv4` | ❌ | Use backend mode |

OAuth2 example in `.bru`:

```bru
auth:oauth2 {
  grant_type: client_credentials
  access_token_url: {{tokenUrl}}
  client_id: {{clientId}}
  client_secret: {{clientSecret}}
  scope: read
}
```

### Scripts & assertions

**Scripts** run in a Node.js VM sandbox (5s timeout) with:

| API | Description |
|-----|-------------|
| `setVar` / `getVar` | Collection variables |
| `setEnvVar` / `getEnvVar` | Same variable store in local mode |
| `setCollectionVar` / `getCollectionVar` | Same variable store |
| `bru.setUrl`, `bru.setHeader` | Mutate outgoing request |
| `bru.skip()` | Skip current request |
| `bru.getProcessEnv(name)` | Read `process.env` |
| `res.status`, `res.body` | Post-response only |

Post-response scripts also receive `getStatus()` / `getBody()` on `res`.

**Assertions** (`assert` block):

```bru
assert {
  status: eq 200
  body:contains "completed"
  body: eq {"id":1}
}
```

Supported types: `status`, `body`, `body:contains`, `{field}:contains`.

---

## Backend mode

Backend mode is used when the target is **not** a local `.bru` directory (or when `--local` is not set and the path does not exist as a directory).

### Start the server

```bash
cd backend
mvn spring-boot:run
# Listening on http://localhost:8080
```

Workspace data defaults to `backend/data/apiflow.json` (configurable via `apiflow.data-file`).

### Backend-only features

- Full GraalVM JavaScript (`pm.*`, `pm.visualizer`, `pm.test`, `pm.expect`)
- HTTP/2, HTTP/3, gRPC, WebSocket, SSE, SOAP
- Digest, NTLM, AWS SigV4, OAuth1, EdgeGrid auth
- Folder-scoped runs, parallel execution, datasets
- Mock server, monitors, performance tests, flows
- HTML/JUnit reports, import/export pipelines

### Collection run request body

`POST /api/cli/run` accepts:

```json
{
  "collection": "Sample API",
  "environment": "dev",
  "globalEnvironment": "",
  "folder": "",
  "stopOnFailure": true,
  "dataCsv": "",
  "parallel": false,
  "delayMs": 0,
  "tags": ["smoke"],
  "datasetId": "",
  "mockBaseUrl": "",
  "shareResults": false
}
```

---

## Run report JSON

Both local and backend `run` commands print JSON to stdout:

```json
{
  "total": 3,
  "passed": 2,
  "failed": 1,
  "items": [
    {
      "name": "Get todo",
      "ok": true,
      "status": 200,
      "timeMs": 142,
      "body": "{\"id\":1,...}"
    },
    {
      "name": "Create todo",
      "ok": false,
      "status": 400,
      "timeMs": 88,
      "body": "{\"error\":\"bad request\"}",
      "assertionFailures": ["status expected 201, got 400"]
    },
    {
      "name": "Skipped example",
      "ok": true,
      "status": 0,
      "skipped": true,
      "timeMs": 0,
      "body": ""
    }
  ]
}
```

Parse in CI:

```bash
REPORT=$(apiflow run ./collections/api --local --env ci)
echo "$REPORT" | node -e "const r=JSON.parse(require('fs').readFileSync(0,'utf8')); process.exit(r.failed>0?1:0)"
```

---

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Success (all requests passed, or command completed) |
| `1` | One or more requests failed, backend unreachable, or command error |

Commands that exit non-zero on failure: `run`, `run-local`, `request`, `monitor`, `performance run`.

---

## CI / GitHub Actions

Use the composite action from this repo (no npm publish required):

```yaml
- uses: ./.github/actions/apiflow-run
  with:
    collection-path: ./collections/my-api
    environment: ci
    tags: smoke
    stop-on-failure: 'true'
```

Or with embedded backend:

```yaml
- uses: ./.github/actions/apiflow-run
  with:
    start-backend: 'true'
    collection: Sample API
    environment: ci
```

See [`.github/actions/apiflow-run/README.md`](../../.github/actions/apiflow-run/README.md) for full action documentation.

**Standalone CI job (npm):**

```yaml
jobs:
  api-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: npm install -g @apiflow/cli
      - run: apiflow run ./collections/api --local --env ci --tag smoke --stop
```

---

## Troubleshooting

### `ApiFlow backend is not running on http://localhost:8080`

Start the backend:

```bash
cd backend && mvn spring-boot:run
```

Or enable auto-start (monorepo only, JAR must be built):

```bash
export APIFLOW_AUTO_START=1
```

### `Backend jar not found`

Build the JAR:

```bash
cd backend && mvn -DskipTests package
```

### `Auth type "digest" requires the ApiFlow backend`

Advanced auth types are not implemented in local mode. Use backend mode or export simpler auth for CI.

### `Collection not found: My API`

The name must match a collection in the workspace (`backend/data/apiflow.json`). List collections via:

```bash
curl -s http://localhost:8080/api/workspace | node -pe 'JSON.parse(require("fs").readFileSync(0,"utf8")).collections.map(c=>c.name)'
```

### Local run skips all requests

If you pass `--tag` but requests have no matching tags, nothing runs. Verify tags in `meta { tags: ... }` or `tags { ... }` blocks.

### Scripts silently fail in local mode

Local scripts use `vm.runInNewContext` with a 5s timeout. `console.log` is a no-op in the sandbox. Use `bru.setVar` to pass values between steps.

---

## Development & publishing

### Run from source

```bash
node --check packages/apiflow-cli/index.js
node --check packages/apiflow-cli/bru-runner.js

node packages/apiflow-cli/index.js run ./path/to/collection --local --env dev
```

### Publish to npm

Dry-run and interactive publish:

```bash
./scripts/publish-cli.sh
```

Manual:

```bash
cd packages/apiflow-cli
npm login
npm version patch
npm publish --access public
```

`prepublishOnly` runs syntax checks on `index.js` and `bru-runner.js`.

---

## Related packages

| Package | Path | Description |
|---------|------|-------------|
| ApiFlow backend | `backend/` | Spring Boot API server |
| ApiFlow UI | `frontend/` | React + Vite desktop UI |
| GitHub Action | `.github/actions/apiflow-run/` | CI composite action |
| VS Code extension | `vscode-extension/` | Collection runner in IDE |
| Shell CLI | `apiflow` (repo root) | curl-based backend wrapper |
| Docker | `Dockerfile` | Backend JRE image |

---

## License

Licensed under the **Apache License, Version 2.0**. See [LICENSE](../../LICENSE) in the repository root.
