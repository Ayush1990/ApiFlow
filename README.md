# ApiFlow Run — GitHub Action

Run **ApiFlow** or Bruno-compatible `.bru` API collections in CI/CD — without Postman or Bruno Desktop.

This composite action invokes the bundled [`@apiflow/cli`](packages/apiflow-cli) runner. It supports **standalone mode** (no Java backend, `.bru` folders on disk) and **backend mode** (full Spring Boot workspace, collection-by-name, folder filters, and workspace data).

---

## Table of contents

- [About ApiFlow](#about-apiflow)
- [Architecture](#architecture)
- [Quick start](#quick-start)
- [Action inputs](#action-inputs)
- [Action outputs](#action-outputs)
- [Execution modes](#execution-modes)
- [What the action does internally](#what-the-action-does-internally)
- [Workflow examples](#workflow-examples)
- [Local development](#local-development)
- [CLI reference](#cli-reference)
- [Project layout](#project-layout)
- [Requirements & compatibility](#requirements--compatibility)
- [Publishing to GitHub Marketplace](#publishing-to-github-marketplace)
- [License](#license)

---

## About ApiFlow

**ApiFlow** is a local-first API client and test runner (Postman/Bruno alternative) with:

| Area | Capabilities |
|------|----------------|
| **HTTP** | REST, HTTP/1.1–HTTP/3, redirects, cookies, mTLS, proxy |
| **Protocols** | WebSocket, SSE, gRPC (incl. streaming), MQTT, Socket.IO, SOAP |
| **Scripts** | Pre-request & after-response JavaScript (GraalVM), `pm.*` / Bruno-style APIs, visualizer |
| **Testing** | Assertions, extractors, collection runner, tags, CSV/dataset rows |
| **Import/export** | Bruno, Postman, OpenAPI, OpenCollection |
| **Platform** | Mock server, monitors, performance tests, flows, spec hub, vault, capture proxy |
| **Integrations** | CLI, VS Code extension, Electron desktop shell, Docker image |

**License:** [Apache License 2.0](LICENSE)

---

## Architecture

### High level

```
┌─────────────────┐     HTTP :8080      ┌──────────────────────────────┐
│  React UI       │ ◄──────────────────►│  Spring Boot backend         │
│  (Vite :5173)   │      /api/*         │  Java 17, GraalVM scripts    │
└─────────────────┘                     │  data/apiflow.json workspace   │
                                        └──────────────┬───────────────┘
                                                       │
┌─────────────────┐     HTTP :8080                     │
│  @apiflow/cli   │ ◄──────────────────────────────────┘
│  bru-runner.js  │   /api/cli/run, /api/execute, …
└────────┬────────┘
         │
         │  Standalone: parses .bru on disk (Node vm)
         ▼
┌─────────────────┐
│  .bru collection│  bruno.json / folder of *.bru
└─────────────────┘

┌─────────────────┐
│  This GitHub    │  composite action → node packages/apiflow-cli/index.js
│  Action         │  optional: mvn package + java -jar backend
└─────────────────┘
```

### Low level — backend services

| Package / path | Role |
|----------------|------|
| `backend/.../RequestExecutor` | Sends HTTP/gRPC/WS requests, runs scripts |
| `backend/.../ScriptRunner` | GraalVM JS sandbox (`pm`, `apiflow`, tests, visualizer) |
| `backend/.../WorkspaceService` | Collections, environments, collection runner, CLI `/api/cli/run` |
| `backend/.../ImportService` | Postman, Bruno, OpenAPI import |
| `backend/.../MockServer` | Local mock routes from examples |
| `backend/.../PerformanceTestService` | Load / VU tests |
| `backend/data/apiflow.json` | Default workspace persistence (`apiflow.data-file`) |

### Low level — standalone runner

When `collection-path` is set, the action runs **`apiflow run <path> --local`**, which uses `packages/apiflow-cli/bru-runner.js` to:

1. Discover `.bru` files and `bruno.json` metadata
2. Merge environments from `environments/*.bru`
3. Interpolate `{{variables}}`, apply auth (bearer, basic, apikey, oauth2)
4. Execute requests via Node `fetch`
5. Run inline pre/post scripts in a VM (subset of full backend scripting)
6. Evaluate `assert` blocks and return JSON `{ passed, failed, total, items }`

**Standalone limitations:** Digest, NTLM, AWS SigV4, gRPC streaming, and full `pm.visualizer` require backend mode.

---

## Quick start

### Standalone — `.bru` folder in your repo (recommended for CI)

No Java build. Fastest path for Bruno-compatible collections checked into git.

```yaml
name: API smoke tests

on:
  pull_request:

jobs:
  apiflow:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Run collection
        uses: ./.github/actions/apiflow-run
        with:
          collection-path: ./collections/my-api
          environment: staging
          tags: smoke
          stop-on-failure: 'true'
```

### Backend — workspace collection by name

Builds and starts the embedded Spring Boot JAR, then runs a collection stored in the ApiFlow workspace (`backend/data/apiflow.json` or your mounted data file).

```yaml
- uses: ./.github/actions/apiflow-run
  with:
    start-backend: 'true'
    collection: Sample API
    environment: dev
    folder: Users
    tags: '@tag(smoke) && health'
    csv-file: ./data/users.csv
    stop-on-failure: 'true'
```

### External backend (already running)

```yaml
- uses: ./.github/actions/apiflow-run
  with:
    url: http://apiflow.internal:8080
    collection: Regression Suite
    environment: staging
```

---

## Action inputs

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `collection-path` | One of `collection` or `collection-path` | `''` | Path to a local `.bru` collection directory. Enables **standalone mode** (`--local`). |
| `collection` | One of `collection` or `collection-path` | `''` | Collection **name** (or id) in the ApiFlow workspace. Requires a running backend. |
| `start-backend` | No | `false` | If `true`, runs `mvn package`, starts `backend/target/apiflow-*.jar`, waits for `/api/workspace`, stops the JVM after the run. |
| `url` | No | `http://localhost:8080` | Backend base URL (`APIFLOW_URL`). Used for health check and CLI calls. |
| `environment` | No | `''` | Environment name (matches `environments/*.bru` locally or workspace env name). |
| `folder` | No | `''` | Restrict run to a folder (**backend mode only**). |
| `tags` | No | `''` | Tag filter: `smoke`, comma-separated tags, or expressions like `@tag(smoke) && health`. |
| `csv-file` | No | `''` | Path to CSV file for data-driven iterations (header row = variable names). |
| `stop-on-failure` | No | `true` | Maps to CLI `--stop` — exit on first failed request/assertion. |
| `java-version` | No | `17` | Temurin JDK when `start-backend: true`. |
| `working-directory` | No | `.` | Repo root relative to checkout. Backend build uses `{working-directory}/backend`. |

### Tag expression examples

| Value | Meaning |
|-------|---------|
| `smoke` | Requests tagged `smoke` |
| `smoke,regression` | Either tag |
| `@tag(smoke)` | Bruno-style tag syntax |
| `smoke && health` | Must have both tags |

---

## Action outputs

| Output | Type | Description |
|--------|------|-------------|
| `passed` | string | Count of requests that passed |
| `failed` | string | Count of requests that failed |
| `total` | string | Total requests executed |
| `exit-code` | string | `0` if all passed, `1` if any failed |

The run step prints the full JSON report to the workflow log. Parse outputs in later steps:

```yaml
- name: Run collection
  id: apiflow
  uses: ./.github/actions/apiflow-run
  with:
    collection-path: ./collections/api

- name: Fail job on errors
  if: steps.apiflow.outputs.exit-code != '0'
  run: |
    echo "Failed: ${{ steps.apiflow.outputs.failed }} / ${{ steps.apiflow.outputs.total }}"
    exit 1
```

---

## Execution modes

| Mode | Trigger | Backend | Best for |
|------|---------|---------|----------|
| **Standalone** | `collection-path` set | Not required | CI with `.bru` in repo; forks; minimal setup |
| **Embedded backend** | `collection` + `start-backend: true` | Built & started by action | Full workspace in `backend/data/`, folder filter, advanced auth |
| **Remote backend** | `collection` + `url` | You provide | Shared ApiFlow instance, Docker, k8s |

### Report JSON shape (stdout)

```json
{
  "passed": 12,
  "failed": 0,
  "total": 12,
  "items": [
    {
      "name": "Get todo",
      "ok": true,
      "status": 200,
      "timeMs": 142
    }
  ]
}
```

---

## What the action does internally

Composite steps (see [`.github/actions/apiflow-run/action.yml`](.github/actions/apiflow-run/action.yml)):

1. **Set up Java** — only if `start-backend: true` (`actions/setup-java@v4`, Temurin).
2. **Build backend** — `mvn -q -DskipTests package` in `{working-directory}/backend`.
3. **Start ApiFlow backend** — `java -jar backend/target/apiflow-*.jar` in background; poll `GET {url}/api/workspace` up to ~120s; write `apiflow-backend.log` on failure.
4. **Run collection** — `node packages/apiflow-cli/index.js run …` with flags derived from inputs; capture JSON report; set outputs; propagate exit code.
5. **Stop backend** — `always()` kill PID from `apiflow-backend.pid`.

CLI path resolution: `$GITHUB_ACTION_PATH/../../../packages/apiflow-cli/index.js` (monorepo layout).

---

## Workflow examples

### Matrix — environments

```yaml
strategy:
  matrix:
    env: [dev, staging, prod]
steps:
  - uses: actions/checkout@v4
  - uses: ./.github/actions/apiflow-run
    with:
      collection-path: ./collections/api
      environment: ${{ matrix.env }}
      tags: smoke
```

### Data-driven CSV

```yaml
- uses: ./.github/actions/apiflow-run
  with:
    collection-path: ./collections/users
    csv-file: ./testdata/users.csv
    environment: test
```

### JUnit report (separate CLI step)

The action does not emit JUnit XML. After a backend run, call the CLI or shell wrapper:

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '17'
- run: mvn -q -DskipTests package
  working-directory: backend
- run: |
    java -jar backend/target/apiflow-*.jar &
    sleep 15
    node packages/apiflow-cli/index.js report "My API" --format junit --out report.xml
- uses: actions/upload-artifact@v4
  with:
    name: apiflow-junit
    path: report.xml
```

### ApiFlow repo CI (reference)

See [`.github/workflows/apiflow.yml`](.github/workflows/apiflow.yml) — backend tests, frontend build, extension compile, CLI smoke against a live backend, Docker build.

---

## Local development

### Run the full stack

```bash
# Terminal 1 — backend (:8080)
cd backend && mvn spring-boot:run

# Terminal 2 — UI (:5173, proxies /api → :8080)
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173**.

### Run the CLI manually (same as the action)

```bash
# Standalone
node packages/apiflow-cli/index.js run ./path/to/collection --local --env dev --tag smoke

# Backend (backend must be up)
export APIFLOW_URL=http://localhost:8080
node packages/apiflow-cli/index.js run "Sample API" --env dev --stop
```

### Docker

```bash
docker build -t apiflow .
docker run -p 8080:8080 -v apiflow-data:/data apiflow
```

### Sample workspace data

Bundled sample collection: `backend/data/collections/col-sample/` (JSON on disk) and `backend/data/apiflow.json` (workspace index).

---

## CLI reference

Installed as `@apiflow/cli` or via repo `packages/apiflow-cli/index.js`.

| Command | Backend required | Description |
|---------|------------------|-------------|
| `run <name\|path>` | Path: no; name: yes | Run collection |
| `run-local <path>` | No | Force standalone `.bru` run |
| `request <collection> <request>` | Yes | Single request |
| `export/import` | Yes | Collection interchange |
| `report <collection>` | Yes | HTML or JUnit report |
| `performance run` | Yes | Load test |
| `mock start/stop` | Yes | Mock server |
| `monitor run` | Yes | Uptime monitor |
| `flows run/deploy` | Yes | Flow automation |

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `APIFLOW_URL` | `http://localhost:8080` | Backend base URL |
| `APIFLOW_AUTO_START` | — | `1` / `true` to launch JAR if backend is down |

See [`packages/apiflow-cli/README.md`](packages/apiflow-cli/README.md).

---

## Project layout

```
ApiFlow/
├── LICENSE                          Apache 2.0
├── Dockerfile                       Backend JRE image + CLI shim
├── apiflow                          Shell CLI wrapper (curl + python)
├── backend/                         Spring Boot 4.x, Java 17
│   ├── pom.xml
│   ├── src/main/java/com/apiflow/   Services, web controllers, models
│   └── data/                        Workspace JSON + collection files
├── frontend/                        React 19 + Vite UI (:5173)
├── packages/apiflow-cli/            Node CLI + standalone bru-runner
├── vscode-extension/                VS Code / Cursor extension
├── desktop/                         Electron wrapper
├── docs/PUBLISHING.md               npm, Marketplace, VS Code publish
└── .github/
    ├── actions/apiflow-run/         ← this action
    └── workflows/apiflow.yml        Project CI
```

---

## Requirements & compatibility

| Component | Version |
|-----------|---------|
| Node.js | ≥ 18 (action runner includes Node 20 on `ubuntu-latest`) |
| Java | 17+ (only when `start-backend: true`) |
| Maven | Bundled on GitHub-hosted runners |
| Collection format | Bruno `.bru` folders, ApiFlow JSON workspace |

**Bruno compatibility:** `.bru` syntax for meta, HTTP blocks, auth, asserts, and scripts. Postman collections should be imported into ApiFlow/Bruno format first.

**Secrets in CI:** Use GitHub Actions secrets and substitute via environment `.bru` files or workspace environments — do not commit credentials.

---

## Publishing to GitHub Marketplace

1. Tag a release: `git tag apiflow-run-v0.3.1 && git push origin apiflow-run-v0.3.1`
2. Create a GitHub Release from the tag.
3. Repository **Settings → Actions → General → Workflow permissions → Read and write**.
4. **Actions** tab → **ApiFlow Run** → **Publish to Marketplace**.
5. Category: **Testing**. Use this README for the listing description.

Additional publish steps (npm CLI, VS Code): [`docs/PUBLISHING.md`](docs/PUBLISHING.md).

---

## License

This action and the ApiFlow project are licensed under the **[Apache License, Version 2.0](LICENSE)**.

```
Copyright 2026 ApiFlow Contributors

Licensed under the Apache License, Version 2.0
```
