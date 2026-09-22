package io.github.soclear.oneuix.common

/** Shared, device-independent limits and presets for the two-line status-bar clock. */
object DoubleLineClockSettings {
    val presetSizes = listOf("small", "compact", "standard", "large", "extra_large")

    fun normalizedPreset(value: String): String =
        value.takeIf { it in presetSizes } ?: "standard"

    fun presetScale(value: String): Float = when (normalizedPreset(value)) {
        "small" -> 0.90f
        "compact" -> 0.95f
        "large" -> 1.05f
        "extra_large" -> 1.10f
        else -> 1.00f
    }

    fun fromLegacyScale(scale: Float): String = when {
        scale < 0.925f -> "small"
        scale < 0.975f -> "compact"
        scale < 1.025f -> "standard"
        scale < 1.075f -> "large"
        else -> "extra_large"
    }

    fun timeScale(value: Float): Float = value.coerceIn(0.50f, 1.10f)

    fun dateScale(value: Float): Float = value.coerceIn(0.50f, 1.10f)

    fun lineGapDp(value: Float): Float = value.coerceIn(0f, 2f)
}
