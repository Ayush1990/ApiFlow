# Publishing ApiFlow packages

## npm — `@apiflow/cli`

Interactive publish (dry-run first):

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

Requires an npm account with publish rights to the `@apiflow` scope.

Local runner features (v0.3.1+): scripts, bearer/basic/apikey/oauth2 auth, CSV data rows, tag expressions, multipart, assertions.

## GitHub Action — Marketplace

1. Tag a release: `git tag apiflow-run-v0.3.1 && git push origin apiflow-run-v0.3.1`
2. Create a GitHub Release from the tag
3. In GitHub: **Settings → Actions → General → Workflow permissions → Read and write**
4. Open **Actions** → select **ApiFlow Run** → **Publish to Marketplace**
5. Fill in category (Testing), description, and branding from `.github/actions/apiflow-run/README.md`

The composite action supports:

- `collection` — workspace collection name (backend mode)
- `collection-path` — local `.bru` folder (standalone mode)
- `csv-file` — CSV data file (local and backend mode)
- `tags` — tag expression (`smoke`, `@tag(smoke)`, `smoke && health`)
- Structured outputs: `passed`, `failed`, `total`, `exit-code`

## VS Code extension

```bash
cd vscode-extension
npm run compile
npx vsce package
npx vsce publish
```

Requires a Visual Studio Marketplace publisher account.
