# Token revocation on account delete

When an OAuth account is deleted, oot removes its credentials from the device. For providers that have a
revocation endpoint configured, it then makes a best-effort [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)
revocation request. The request runs off the main thread with a short timeout (5 s), never blocks or fails the
local delete, and never logs token values. If the call fails, the provider token simply expires on its own.

A provider is listed as supported only when its revocation endpoint is published by the provider or used by its
own official client. Where that could not be confirmed, no request is made.

Checked on 2026-10-22.

| Provider | Auth | Revocation on delete | Source |
| --- | --- | --- | --- |
| Claude | OAuth | Not supported: no official endpoint found; only user reports and feature requests ask for one | [claude-code#34198](https://github.com/anthropics/claude-code/issues/34198) |
| OpenAI | OAuth | `https://auth.openai.com/oauth/revoke`, JSON body (`token`, `token_type_hint`, `client_id`), as sent by Codex CLI on logout | [openai/codex#17825](https://github.com/openai/codex/pull/17825), [OIDC metadata](https://auth.openai.com/.well-known/openid-configuration) |
| xAI (Grok) | OAuth | `https://auth.x.ai/oauth2/revoke`, RFC 7009 form body | [OIDC metadata](https://auth.x.ai/.well-known/openid-configuration) (`revocation_endpoint`) |
| OpenRouter, Z.ai, DeepSeek, Ollama Cloud, OpenCode Go | API key | Not applicable: manage keys in the vendor console |

To enable a provider, set `oauthRevocationEndpoint` on its `ProviderType` entry and update this table.
