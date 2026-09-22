package io.github.soclear.oneuix.common

/** Shared, device-independent limits for the two-line status-bar clock. */
object DoubleLineClockSettings {
    val presetSizes = listOf("small", "compact", "standard", "large", "extraLarge")

    fun normalizedPreset(value: String): String =
        value.takeIf { it in presetSizes } ?: "standard"

    fun timeScale(value: Float): Float = value.coerceIn(0.50f, 1.10f)

    fun dateScale(value: Float): Float = value.coerceIn(0.50f, 1.10f)

    fun lineGapDp(value: Float): Float = value.coerceIn(0f, 2f)
}
