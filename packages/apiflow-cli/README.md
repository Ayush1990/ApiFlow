# @apiflow/cli

ApiFlow command-line tool for running Bruno-compatible `.bru` collections.

## Install

```bash
npm install -g @apiflow/cli
```

## Local mode (no backend)

Run a folder of `.bru` files directly from disk:

```bash
apiflow run ./my-collection
apiflow run-local ./my-collection --env dev --tag smoke
apiflow run-local ./my-collection --csv ./data/users.csv --tag "@tag(smoke) && health"
```

Local mode supports pre/post scripts, bearer/basic/apikey/oauth2 auth, multipart bodies, CSV data rows, tag expressions, and assert blocks. Digest, NTLM, and AWS auth require backend mode.

## Backend mode

When the target is a collection name (not a path), the CLI talks to ApiFlow on port 8080:

```bash
export APIFLOW_URL=http://localhost:8080
apiflow run "My Collection" --env dev --stop
```

Auto-start the bundled backend JAR:

```bash
export APIFLOW_AUTO_START=1
apiflow run "My Collection"
```

## Commands

- `run <collection|path>` — run a collection
- `run-local <path>` — always run from disk
- `export`, `import`, `grpc reflect`, `report`

## Publish

Maintainers:

```bash
cd packages/apiflow-cli
npm login
npm publish --access public
```

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](../../LICENSE) in the repository root.
