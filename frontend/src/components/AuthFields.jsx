import { Select } from './Select.jsx'

const AUTH_TYPES = [
  { value: 'inherit', label: 'Inherit' },
  { value: 'none', label: 'No auth' },
  { value: 'bearer', label: 'Bearer token' },
  { value: 'basic', label: 'Basic auth' },
  { value: 'apikey', label: 'API key' },
  { value: 'oauth2', label: 'OAuth 2.0' },
  { value: 'digest', label: 'Digest' },
  { value: 'aws', label: 'AWS Signature' },
  { value: 'ntlm', label: 'NTLM' },
  { value: 'oauth1', label: 'OAuth 1.0' },
  { value: 'edgegrid', label: 'Akamai EdgeGrid' },
]

export function AuthFields({ draft, onChange, allowInherit = false, wide = false, onOAuthSignIn, onOAuthSystemBrowser, onOAuthDevice, onOAuthDeviceComplete }) {
  const type = draft.authType || (allowInherit ? 'inherit' : 'none')
  const authOptions = allowInherit ? AUTH_TYPES : AUTH_TYPES.filter((item) => item.value !== 'inherit')
  return (
    <div className={`auth-panel${wide ? ' wide' : ''}`}>
      <p className="muted">Guided auth: pick a type, then fill the fields it asks for. Bearer uses a token. API key asks where to send it. OAuth asks for the authorize and token URLs before you sign in.</p>
      <label>
        Type
        <Select
          value={type}
          onChange={(authType) => onChange({ authType })}
          options={authOptions}
        />
      </label>
      {type === 'inherit' && <p className="muted">Uses the nearest folder auth, then the collection auth.</p>}
      {type === 'bearer' && (
        <label>Token<input value={draft.authToken || ''} onChange={(event) => onChange({ authToken: event.target.value })} placeholder="{{token}}" /></label>
      )}
      {type === 'basic' && (
        <>
          <label>Username<input value={draft.authUsername || ''} onChange={(event) => onChange({ authUsername: event.target.value })} /></label>
          <label>Password<input type="password" value={draft.authPassword || ''} onChange={(event) => onChange({ authPassword: event.target.value })} /></label>
        </>
      )}
      {type === 'ntlm' && (
        <>
          <label>Username<input value={draft.authUsername || ''} onChange={(event) => onChange({ authUsername: event.target.value })} /></label>
          <label>Password<input type="password" value={draft.authPassword || ''} onChange={(event) => onChange({ authPassword: event.target.value })} /></label>
        </>
      )}
      {type === 'apikey' && (
        <>
          <label>Name<input value={draft.apiKeyName || ''} onChange={(event) => onChange({ apiKeyName: event.target.value })} placeholder="X-API-Key" /></label>
          <label>Value<input value={draft.apiKeyValue || ''} onChange={(event) => onChange({ apiKeyValue: event.target.value })} /></label>
          <label>
            Add to
            <Select
              value={draft.apiKeyIn || 'header'}
              onChange={(apiKeyIn) => onChange({ apiKeyIn })}
              options={[
                { value: 'header', label: 'Header' },
                { value: 'query', label: 'Query param' },
              ]}
            />
          </label>
        </>
      )}
      {type === 'digest' && (
        <>
          <label>Username<input value={draft.authUsername || ''} onChange={(event) => onChange({ authUsername: event.target.value })} /></label>
          <label>Password<input type="password" value={draft.authPassword || ''} onChange={(event) => onChange({ authPassword: event.target.value })} /></label>
        </>
      )}
      {type === 'aws' && (
        <>
          <label>Access key<input value={draft.extras?.awsAccessKey || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), awsAccessKey: event.target.value } })} /></label>
          <label>Secret key<input type="password" value={draft.extras?.awsSecretKey || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), awsSecretKey: event.target.value } })} /></label>
          <label>Region<input value={draft.extras?.awsRegion || 'us-east-1'} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), awsRegion: event.target.value } })} /></label>
          <label>Service<input value={draft.extras?.awsService || 'execute-api'} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), awsService: event.target.value } })} /></label>
        </>
      )}
      {type === 'oauth1' && (
        <>
          <label>Consumer key<input value={draft.extras?.oauth1ConsumerKey || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), oauth1ConsumerKey: event.target.value } })} /></label>
          <label>Consumer secret<input type="password" value={draft.extras?.oauth1ConsumerSecret || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), oauth1ConsumerSecret: event.target.value } })} /></label>
          <label>Token<input value={draft.extras?.oauth1Token || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), oauth1Token: event.target.value } })} /></label>
          <label>Token secret<input type="password" value={draft.extras?.oauth1TokenSecret || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), oauth1TokenSecret: event.target.value } })} /></label>
        </>
      )}
      {type === 'edgegrid' && (
        <>
          <label>Client token<input value={draft.extras?.edgeGridClientToken || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), edgeGridClientToken: event.target.value } })} /></label>
          <label>Client secret<input type="password" value={draft.extras?.edgeGridClientSecret || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), edgeGridClientSecret: event.target.value } })} /></label>
          <label>Access token<input value={draft.extras?.edgeGridAccessToken || ''} onChange={(event) => onChange({ extras: { ...(draft.extras || {}), edgeGridAccessToken: event.target.value } })} /></label>
        </>
      )}
      {type === 'oauth2' && <OAuthFields draft={draft} onChange={onChange} onOAuthSignIn={onOAuthSignIn} onOAuthSystemBrowser={onOAuthSystemBrowser} onOAuthDevice={onOAuthDevice} onOAuthDeviceComplete={onOAuthDeviceComplete} />}
    </div>
  )
}

function OAuthFields({ draft, onChange, onOAuthSignIn, onOAuthSystemBrowser, onOAuthDevice, onOAuthDeviceComplete }) {
  const extras = draft.extras || {}
  function patch(next) {
    onChange({ extras: { ...extras, ...next } })
  }
  return (
    <>
      <label>
        Grant
        <Select
          value={extras.oauthGrant || 'client_credentials'}
          onChange={(oauthGrant) => patch({ oauthGrant })}
          options={[
            { value: 'client_credentials', label: 'Client credentials' },
            { value: 'authorization_code', label: 'Authorization code' },
            { value: 'password', label: 'Password' },
            { value: 'device_code', label: 'Device code' },
          ]}
        />
      </label>
      {(extras.oauthGrant || '') === 'password' && (
        <>
          <label>Username<input value={extras.oauthUsername || ''} onChange={(event) => patch({ oauthUsername: event.target.value })} /></label>
          <label>Password<input type="password" value={extras.oauthPassword || ''} onChange={(event) => patch({ oauthPassword: event.target.value })} /></label>
        </>
      )}
      {(extras.oauthGrant || '') === 'device_code' && (
        <>
          <label>Device URL<input value={extras.oauthDeviceUrl || ''} onChange={(event) => patch({ oauthDeviceUrl: event.target.value })} placeholder="https://auth.example.com/oauth/device" /></label>
          {onOAuthDevice && <button type="button" className="secondary" onClick={onOAuthDevice}>Start device sign-in</button>}
          {extras.oauthDeviceCode && onOAuthDeviceComplete && <button type="button" className="secondary" onClick={onOAuthDeviceComplete}>Complete device sign-in</button>}
        </>
      )}
      <label>Token URL<input value={extras.oauthTokenUrl || ''} onChange={(event) => patch({ oauthTokenUrl: event.target.value })} placeholder="https://auth.example.com/oauth/token" /></label>
      {(extras.oauthGrant || 'client_credentials') === 'authorization_code' && (
        <label>Auth URL<input value={extras.oauthAuthUrl || ''} onChange={(event) => patch({ oauthAuthUrl: event.target.value })} placeholder="https://auth.example.com/authorize" /></label>
      )}
      <label>Client ID<input value={extras.oauthClientId || ''} onChange={(event) => patch({ oauthClientId: event.target.value })} /></label>
      <label>Client secret<input type="password" value={extras.oauthClientSecret || ''} onChange={(event) => patch({ oauthClientSecret: event.target.value })} /></label>
      <label>Scope<input value={extras.oauthScope || ''} onChange={(event) => patch({ oauthScope: event.target.value })} placeholder="openid profile" /></label>
      {(extras.oauthGrant || '') === 'authorization_code' && onOAuthSignIn && (
        <>
          <button type="button" className="secondary" onClick={onOAuthSignIn}>Sign in (popup)</button>
          {onOAuthSystemBrowser && <button type="button" className="secondary" onClick={onOAuthSystemBrowser}>Open system browser</button>}
        </>
      )}
      {extras.oauthAccessToken && <p className="muted">Access token is saved on this request.</p>}
    </>
  )
}
