# Token revocation on account delete

When an OAuth account is deleted, oot removes its credentials from the device. For providers that have a
revocation endpoint configured, it then makes a best-effort [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)
revocation request. The request runs off the main thread with a short timeout (5 s), never blocks or fails the
local delete, and never logs token values. If the call fails, the provider token simply expires on its own.

No provider has an endpoint configured today, so no revocation request is made yet (see the table below).

A provider is listed as supported only when its revocation endpoint is clearly documented publicly. Where that
could not be confirmed, no request is made.

| Provider | Auth | Revocation on delete |
| --- | --- | --- |
| Claude | OAuth | Not supported (no public endpoint confirmed) |
| OpenAI | OAuth | Not supported (no public endpoint confirmed) |
| xAI (Grok) | OAuth | Not supported (no public endpoint confirmed) |
| OpenRouter, Z.ai, DeepSeek, Ollama Cloud, OpenCode Go | API key | Not applicable: manage keys in the vendor console |

To enable a provider, set `oauthRevocationEndpoint` on its `ProviderType` entry and update this table.
