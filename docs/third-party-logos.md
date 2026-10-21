# Third-party vendor logos

Official marks used to identify AI providers in oot. Downloaded **2026-10-22**. Symbols only (no wordmarks). Each mark remains the trademark of its owner; use does not imply endorsement.

| Vendor | Asset used | Source URL | License / guideline note | In-app |
| --- | --- | --- | --- | --- |
| Claude (Anthropic) | Claude Spark – Clay SVG → `ic_vendor_claude` / `_mono` | [Anthropic press kit](https://www.anthropic.com/press-kit) (`Anthropic media resources.zip`) | Press-kit brand assets; identify Claude/Anthropic only, do not imply endorsement | Logo |
| OpenRouter | Glyph grape / volt (night) / ink (mono) SVG | [openrouter.ai/brand](https://openrouter.ai/brand) | “Don’t stretch, recolor, or remix the marks.” Color marks kept as provided; mono is the official ink glyph tinted with notification/`onSurface` text color | Logo |
| xAI | `xAI_Logomark_{Dark,Light}.svg` from official zip | [x.ai brand guidelines](https://x.ai/legal/brand-guidelines) + [xAI_Grok_Assets.zip](https://data.x.ai/logos/xAI_Grok_Assets.zip) | Use logos exactly as provided; Light = white (dark UI), Dark = black (light UI) | Logo |
| Google Antigravity | Full-color PNG (SVG uses masks) + one-color / white SVG | [antigravity.google/press](https://antigravity.google/press/) (`/assets/image/brand/antigravity-icon__*`) | Official press assets; product icon only | Logo |
| DeepSeek | Whale symbol path from official `figures/logo.svg` (wordmark omitted) | [deepseek-ai/DeepSeek-V2](https://raw.githubusercontent.com/deepseek-ai/DeepSeek-V2/main/figures/logo.svg) | Logo ships with DeepSeek’s public GitHub repos (see repo `LICENSE-CODE`); trademark still DeepSeek’s | Logo |
| Ollama | `docs/logo.svg` | [ollama/ollama `docs/logo.svg`](https://raw.githubusercontent.com/ollama/ollama/main/docs/logo.svg) | Official repo asset; black mark tinted with `onSurface` / notification text color | Logo |
| OpenCode | Square logo light/dark SVG from brand assets | [opencode.ai/brand](https://opencode.ai/brand) / [anomalyco/opencode brand assets](https://github.com/anomalyco/opencode/tree/dev/packages/console/app/src/asset/brand) | Official brand pack; light/dark square symbols | Logo |
| OpenAI | — | [openai.com/brand](https://openai.com/brand/) | Guidelines require permission before logo use; no obtainable official SVG from this environment (403) | **Letter tile (O)** |
| Z.ai (GLM) | — | [z.ai terms](https://chat.z.ai/legal-agreement/terms-of-service) (logo URL exists at `z-cdn.chatglm.cn` but unused) | Terms forbid using Z.ai logos/marks without prior written consent | **Letter tile (G)** |

Conversion: `scripts/svg_to_vectordrawable.py` (paths and fills preserved). Antigravity full-color is the official PNG resized for mdpi–xxhdpi because the official SVG relies on masks that VectorDrawable cannot express.
