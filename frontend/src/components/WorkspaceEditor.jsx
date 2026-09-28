import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { KeyValueTable } from './KeyValueTable.jsx'
import { KeybindingsEditor } from './KeybindingsEditor.jsx'
import { NpmPackagesPanel } from './NpmPackagesPanel.jsx'
import { SecretMigrationWizard } from './SecretMigrationWizard.jsx'
import { Select } from './Select.jsx'

function Section({ title, desc, children }) {
  return (
    <section className="section-card">
      <h3 className="section-title">{title}</h3>
      {desc && <p className="section-desc muted">{desc}</p>}
      {children}
    </section>
  )
}

function Field({ label, wide, children }) {
  return (
    <label className={wide ? 'field span-2' : 'field'}>
      {label}
      {children}
    </label>
  )
}

export function WorkspaceEditor({ workspace, onChange, onSave, onExportBundle, onImportBundle, onLoadProfile, saving }) {
  const settings = workspace.settings || {}
  const secrets = settings.secretManager || {}
  const identity = settings.identity || {}
  const [profiles, setProfiles] = useState([])
  const [profileName, setProfileName] = useState('')

  useEffect(() => {
    api.listProfiles().then(setProfiles).catch(() => setProfiles([]))
  }, [])

  function patchSettings(next) {
    onChange({ settings: { ...settings, ...next } })
  }

  function patchSecrets(next) {
    onChange({ settings: { ...settings, secretManager: { ...secrets, ...next } } })
  }

  function patchIdentity(next) {
    onChange({ settings: { ...settings, identity: { ...identity, ...next } } })
  }

  return (
    <div className="env-editor workspace-editor">
      <div className="page-header">
        <div className="page-header-row">
          <div>
            <h2>Workspace</h2>
            <p className="muted">Global settings, variables, and integrations for this machine.</p>
          </div>
          <button type="button" className="send" onClick={onSave} disabled={saving}>{saving ? 'Saving…' : 'Save workspace'}</button>
        </div>
      </div>

      <div className="page-body stack">
        <Section
          title="Global variables"
          desc={<>Apply to every collection. Environment variables win on name clashes. Dynamic values: <code>$uuid</code>, <code>$timestamp</code>, <code>$randomInt</code>. Process env: <code>{'{{process.env.NAME}}'}</code>. Secrets: <code>{'{{secret:path/to/secret}}'}</code>.</>}
        >
          <KeyValueTable rows={workspace.variables} onChange={(variables) => onChange({ variables })} secrets />
        </Section>

        <Section title="General" desc="Appearance, storage, scripting, and editor behavior.">
          <div className="settings-grid">
            <Field label="Theme">
              <Select
                value={settings.theme || 'dark'}
                onChange={(theme) => patchSettings({ theme })}
                options={[
                  { value: 'dark', label: 'Dark' },
                  { value: 'light', label: 'Light' },
                ]}
              />
            </Field>
            <Field label="Default on-disk / export format">
              <Select
                value={settings.defaultStorageFormat || (settings.nativeBruStorage ? 'bru' : 'json')}
                onChange={(defaultStorageFormat) => patchSettings({ defaultStorageFormat, nativeBruStorage: defaultStorageFormat === 'bru' })}
                options={[
                  { value: 'json', label: 'JSON (default)' },
                  { value: 'bru', label: 'Native .bru' },
                  { value: 'opencollection', label: 'OpenCollection YAML' },
                ]}
              />
            </Field>
            <Field label="Script sandbox" wide>
              <Select
                value={settings.scriptMode || 'safe'}
                onChange={(scriptMode) => patchSettings({ scriptMode })}
                options={[
                  { value: 'safe', label: 'Safe — no process env, npm, outbound HTTP, or nested runs' },
                  { value: 'developer', label: 'Developer — full script API + npm require' },
                ]}
              />
            </Field>
            <Field label="Git collection scope">
              <Select
                value={settings.gitCollectionId || ''}
                onChange={(gitCollectionId) => patchSettings({ gitCollectionId })}
                options={[
                  { value: '', label: 'Data root (all collections)' },
                  ...(workspace.collections || []).map((collection) => ({ value: collection.id, label: collection.name })),
                ]}
              />
            </Field>
            <Field label="Send shortcut">
              <Select
                value={settings.sendKeybinding || 'mod+Enter'}
                onChange={(sendKeybinding) => patchSettings({ sendKeybinding })}
                options={[
                  { value: 'mod+Enter', label: 'Cmd/Ctrl + Enter' },
                  { value: 'Enter', label: 'Enter' },
                  { value: 'mod+s', label: 'Cmd/Ctrl + S then send' },
                ]}
              />
            </Field>
            <Field label="Proxy URL">
              <input value={settings.proxyUrl || ''} onChange={(event) => patchSettings({ proxyUrl: event.target.value })} placeholder="http://127.0.0.1:8888" />
            </Field>
            <Field label="Proxy bypass" wide>
              <input value={settings.proxyBypass || ''} onChange={(event) => patchSettings({ proxyBypass: event.target.value })} placeholder="localhost,127.0.0.1,.internal" />
            </Field>
            <Field label="Dotenv path">
              <input value={settings.dotenvPath || '.env'} onChange={(event) => patchSettings({ dotenvPath: event.target.value })} placeholder=".env" />
            </Field>
          </div>
          <label className="check-line settings-check">
            <input type="checkbox" checked={settings.storeHistoryBodies !== false} onChange={(event) => patchSettings({ storeHistoryBodies: event.target.checked })} />
            Store request/response bodies in history
          </label>
          <p className="section-foot muted">Built-in script libraries: <code>require('lodash')</code>, <code>require('moment')</code>. Collection modules: <code>collections/&lt;id&gt;/scripts/*.js</code>.</p>
          <p className="section-foot muted">Git scope limits operations to <code>data/collections/&lt;id&gt;/</code> when a collection is selected.</p>
        </Section>

        <Section title="Global environments" desc={<>Global environments apply workspace-wide (Bruno <code>--global-env</code>). Mark environments as non-global in the environment editor to keep them collection-scoped.</>}>
          <p className="muted settings-note">Manage environments from the sidebar under <strong>Environments</strong>.</p>
        </Section>

        <Section title="Default client certificate" desc="Optional workspace-wide mTLS certificate used when a request does not specify its own.">
          <div className="settings-grid">
            <Field label="Client cert (PEM base64)" wide>
              <textarea className="body body-compact" value={settings.clientCertBase64 || ''} onChange={(event) => patchSettings({ clientCertBase64: event.target.value })} placeholder="Optional workspace default mTLS certificate" />
            </Field>
            <Field label="Client key (PEM base64)" wide>
              <textarea className="body body-compact" value={settings.clientKeyBase64 || ''} onChange={(event) => patchSettings({ clientKeyBase64: event.target.value })} />
            </Field>
            <Field label="Cert password">
              <input type="password" value={settings.clientCertPassword || ''} onChange={(event) => patchSettings({ clientCertPassword: event.target.value })} />
            </Field>
          </div>
        </Section>

        <Section title="Keyboard shortcuts" desc="Customize shortcuts beyond the send key.">
          <KeybindingsEditor settings={settings} onChange={patchSettings} />
        </Section>

        <Section title="Workspace profiles" desc="Save and switch between multiple workspace snapshots on this machine.">
          <div className="row">
            <input value={profileName} onChange={(event) => setProfileName(event.target.value)} placeholder="Profile name" aria-label="Profile name" />
            <button type="button" className="secondary" disabled={!profileName.trim()} onClick={async () => {
              await api.saveProfile(profileName.trim())
              setProfiles(await api.listProfiles())
              setProfileName('')
            }}>Save current</button>
          </div>
          <div className="row">
            {(profiles || []).map((name) => (
              <button key={name} type="button" className="secondary" onClick={async () => {
                const result = await api.loadProfile(name)
                onLoadProfile?.(result.workspace)
              }}>{name}</button>
            ))}
            {profiles.length === 0 && <p className="muted">No saved profiles yet.</p>}
          </div>
        </Section>

        <Section title="Secret migration" desc="Move legacy secrets.json references into environment variables.">
          <SecretMigrationWizard workspace={workspace} onMigrated={(next) => onChange(next)} />
        </Section>

        <Section title="Workspace bundle" desc="Export or import a Bruno-compatible bundle (collections + global environments).">
          <div className="row">
            {onExportBundle && <button type="button" className="secondary" onClick={onExportBundle}>Export bundle</button>}
            {onImportBundle && (
              <label className="secondary file-button">
                Import bundle
                <input type="file" accept=".json,application/json" hidden onChange={(event) => {
                  const file = event.target.files?.[0]
                  if (file) onImportBundle(file)
                  event.target.value = ''
                }} />
              </label>
            )}
          </div>
        </Section>

        <Section title="Secret manager" desc="Resolve {{secret:path}} placeholders from an external vault at request time.">
          <div className="settings-grid">
            <Field label="Provider">
              <Select
                value={secrets.provider || 'none'}
                onChange={(provider) => patchSecrets({ provider })}
                options={[
                  { value: 'none', label: 'None' },
                  { value: 'aws', label: 'AWS Secrets Manager' },
                  { value: 'vault', label: 'HashiCorp Vault' },
                  { value: 'azure', label: 'Azure Key Vault' },
                  { value: 'gcp', label: 'GCP Secret Manager' },
                ]}
              />
            </Field>
            {secrets.provider === 'aws' && (
              <Field label="AWS region">
                <input value={secrets.awsRegion || 'us-east-1'} onChange={(event) => patchSecrets({ awsRegion: event.target.value })} />
              </Field>
            )}
            {secrets.provider === 'vault' && (
              <>
                <Field label="Vault URL">
                  <input value={secrets.vaultUrl || ''} onChange={(event) => patchSecrets({ vaultUrl: event.target.value })} placeholder="https://vault.example.com" />
                </Field>
                <Field label="Vault token">
                  <input type="password" value={secrets.vaultToken || ''} onChange={(event) => patchSecrets({ vaultToken: event.target.value })} />
                </Field>
              </>
            )}
            {secrets.provider === 'azure' && (
              <>
                <Field label="Auth mode">
                  <Select
                    value={secrets.azureAuthMode || 'client_secret'}
                    onChange={(azureAuthMode) => patchSecrets({ azureAuthMode })}
                    options={[
                      { value: 'client_secret', label: 'Service principal (client secret)' },
                      { value: 'cli', label: 'Azure CLI (az login)' },
                    ]}
                  />
                </Field>
                <Field label="Key Vault URL">
                  <input value={secrets.azureVaultUrl || ''} onChange={(event) => patchSecrets({ azureVaultUrl: event.target.value })} />
                </Field>
                {(secrets.azureAuthMode || 'client_secret') === 'client_secret' && (
                  <>
                    <Field label="Tenant ID">
                      <input value={secrets.azureTenantId || ''} onChange={(event) => patchSecrets({ azureTenantId: event.target.value })} />
                    </Field>
                    <Field label="Client ID">
                      <input value={secrets.azureClientId || ''} onChange={(event) => patchSecrets({ azureClientId: event.target.value })} />
                    </Field>
                    <Field label="Client secret">
                      <input type="password" value={secrets.azureClientSecret || ''} onChange={(event) => patchSecrets({ azureClientSecret: event.target.value })} />
                    </Field>
                  </>
                )}
              </>
            )}
            {secrets.provider === 'gcp' && (
              <>
                <Field label="Project ID">
                  <input value={secrets.gcpProjectId || ''} onChange={(event) => patchSecrets({ gcpProjectId: event.target.value })} />
                </Field>
                <Field label="Access token">
                  <input type="password" value={secrets.gcpAccessToken || ''} onChange={(event) => patchSecrets({ gcpAccessToken: event.target.value })} />
                </Field>
              </>
            )}
          </div>
        </Section>

        <Section title="Enterprise auth" desc="SAML login gate, SCIM provisioning, and Git provider tokens for PR workflows.">
          <label className="check-line settings-check">
            <input type="checkbox" checked={!!identity.enterpriseAuthEnabled} onChange={(event) => patchIdentity({ enterpriseAuthEnabled: event.target.checked })} />
            Enable SAML login gate
          </label>
          <div className="settings-grid">
            <Field label="SAML entity ID">
              <input value={identity.samlEntityId || 'apiflow'} onChange={(event) => patchIdentity({ samlEntityId: event.target.value })} />
            </Field>
            <Field label="SAML IdP URL">
              <input value={identity.samlIdpUrl || ''} onChange={(event) => patchIdentity({ samlIdpUrl: event.target.value })} placeholder="https://idp.example.com/sso" />
            </Field>
            <Field label="SAML certificate" wide>
              <textarea className="body body-compact" value={identity.samlCertificate || ''} onChange={(event) => patchIdentity({ samlCertificate: event.target.value })} placeholder="-----BEGIN CERTIFICATE-----" />
            </Field>
            <Field label="SCIM bearer token">
              <input type="password" value={identity.scimToken || ''} onChange={(event) => patchIdentity({ scimToken: event.target.value })} />
            </Field>
            <Field label="Git provider token">
              <input type="password" value={identity.gitProviderToken || ''} onChange={(event) => patchIdentity({ gitProviderToken: event.target.value })} placeholder="GitHub PAT or GitLab token" />
            </Field>
          </div>
          <p className="section-foot muted">SAML metadata: <code>/api/auth/saml/metadata</code> · SCIM: <code>/scim/v2/Users</code></p>
        </Section>

        <Section title="NPM script packages" desc={<>Install packages into <code>data/scripts/node_modules</code> and use <code>{'require("package")'}</code> in scripts.</>}>
          <NpmPackagesPanel />
        </Section>

        <Section title="AI assistant" desc="Configure the streaming chat assistant. Keys stay on this machine.">
          <div className="settings-grid">
            <Field label="Provider">
              <Select
                value={settings.aiProvider || 'openai'}
                onChange={(aiProvider) => patchSettings({ aiProvider })}
                options={[
                  { value: 'openai', label: 'OpenAI' },
                  { value: 'anthropic', label: 'Anthropic' },
                  { value: 'custom', label: 'Custom (OpenAI-compatible)' },
                ]}
              />
            </Field>
            <Field label="Model">
              <input value={settings.aiModel || ''} onChange={(event) => patchSettings({ aiModel: event.target.value })} placeholder="gpt-4o-mini / claude-3-5-sonnet-latest" />
            </Field>
            <Field label="OpenAI / custom API key" wide>
              <input type="password" value={settings.aiApiKey || ''} onChange={(event) => patchSettings({ aiApiKey: event.target.value })} placeholder="sk-..." />
            </Field>
            {(settings.aiProvider || 'openai') === 'anthropic' && (
              <Field label="Anthropic API key" wide>
                <input type="password" value={settings.aiAnthropicKey || ''} onChange={(event) => patchSettings({ aiAnthropicKey: event.target.value })} placeholder="sk-ant-..." />
              </Field>
            )}
            {(settings.aiProvider || 'openai') === 'custom' && (
              <Field label="Custom chat URL" wide>
                <input value={settings.aiCustomUrl || ''} onChange={(event) => patchSettings({ aiCustomUrl: event.target.value })} placeholder="https://api.example.com/v1/chat/completions" />
              </Field>
            )}
          </div>
        </Section>
      </div>
    </div>
  )
}
