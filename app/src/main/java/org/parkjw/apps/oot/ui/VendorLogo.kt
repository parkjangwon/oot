package org.parkjw.apps.oot.ui

import androidx.annotation.DrawableRes
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderType

/** Official vendor mark drawables (spec §7). Null = keep letter tile. */
object VendorLogo {

    data class Spec(
        @DrawableRes val color: Int,
        @DrawableRes val mono: Int,
        /** Black-only marks: tint mono with onSurface instead of drawing color as-is. */
        val tintWithOnSurface: Boolean,
        /** Wide marks use 14dp height inside the 20dp box. */
        val wide: Boolean = false,
        /** Official light/inverse variant used on dark surfaces; null = [color] works on both. */
        @DrawableRes val night: Int? = null,
    ) {
        @DrawableRes fun colorFor(dark: Boolean): Int = if (dark) night ?: color else color
    }

    fun of(provider: ProviderType): Spec? = when (provider) {
        ProviderType.CLAUDE -> Spec(R.drawable.ic_vendor_claude, R.drawable.ic_vendor_claude_mono, tintWithOnSurface = false)
        ProviderType.OPENROUTER -> Spec(R.drawable.ic_vendor_openrouter, R.drawable.ic_vendor_openrouter_mono, tintWithOnSurface = false, wide = true, night = R.drawable.ic_vendor_openrouter_night)
        ProviderType.XAI -> Spec(R.drawable.ic_vendor_xai, R.drawable.ic_vendor_xai_mono, tintWithOnSurface = false, night = R.drawable.ic_vendor_xai_night)
        ProviderType.DEEPSEEK -> Spec(R.drawable.ic_vendor_deepseek, R.drawable.ic_vendor_deepseek_mono, tintWithOnSurface = false, wide = true)
        ProviderType.OLLAMA_CLOUD -> Spec(R.drawable.ic_vendor_ollama, R.drawable.ic_vendor_ollama_mono, tintWithOnSurface = true)
        ProviderType.OPENCODE_GO -> Spec(R.drawable.ic_vendor_opencode, R.drawable.ic_vendor_opencode_mono, tintWithOnSurface = false, night = R.drawable.ic_vendor_opencode_night)
        // OpenAI: brand guidelines require permission; Z.ai ToS forbids in-app logo use.
        ProviderType.OPENAI, ProviderType.ZAI -> null
    }
}
