# Token revocation on account delete

Deleting an account always removes its credentials from the device first. That local delete never waits for
the network and is not undone by any failure below.

For providers whose own published documentation lists an [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)
revocation endpoint, oot then sends a best-effort revocation request for the deleted sign-in. It runs off the main
thread with a short timeout (5 s), uses the RFC 7009 form body (`token`, `token_type_hint`, `client_id`), prefers
the refresh token, and never logs token values. If it fails, the provider token simply expires on its own.

Where no revoke endpoint is documented, no request is made: the key is removed from the device only, and the
account's detail dialog says so ("삭제하면 이 기기에 저장된 키만 지워져요. 서버 쪽 철회는 해당 서비스에서 직접 해 주세요.").
Endpoints are never guessed or taken from unofficial clients.

## Covered providers

Covered: the widely used providers oot supports — Claude, OpenAI, xAI (Grok), OpenRouter, DeepSeek.
Not covered: Z.ai, Ollama Cloud, OpenCode Go (smaller user base in oot). Deleting them still removes the key from
the device, without the note.

Checked on 2026-10-23.

| Provider | Auth | On delete | Source |
| --- | --- | --- | --- |
| OpenAI | OAuth (ChatGPT sign-in) | Server revoke: `https://auth.openai.com/api/accounts/oauth/revoke` | `revocation_endpoint` in OpenAI's published OIDC metadata: <https://auth.openai.com/.well-known/openid-configuration> (`revocation_endpoint_auth_methods_supported` includes `none`) |
| xAI (Grok) | OAuth (device sign-in) | Server revoke: `https://auth.x.ai/oauth2/revoke` | `revocation_endpoint` in xAI's published OIDC metadata: <https://auth.x.ai/.well-known/openid-configuration> |
| Claude | OAuth | Device only: no revoke endpoint in Anthropic's docs; revoke at claude.ai settings | <https://platform.claude.com/docs/en/manage-claude/authentication> (no revocation endpoint documented) |
| Claude, OpenAI, xAI, OpenRouter, DeepSeek | API key | Device only: keys are revoked in the vendor console | — |
| Z.ai, Ollama Cloud, OpenCode Go | API key | Not covered: removed from the device only | — |

To add a provider, set `oauthRevocationEndpoint` on its `ProviderType` entry only from the provider's own
documentation, add it to `REVOCATION_COVERED_PROVIDERS` if needed, and update this table and `TokenRevokerTest`.
