package io.github.soclear.oneuix.hook.systemui

/**
 * Resolves the two-line clock typography without checking the device model.
 * Existing Fold7 preference names are retained for compatibility with saved settings;
 * the UI may expose them on every device.
 */
internal data class DoubleLineClockStyle(
    val timeScale: Float,
    val dateScale: Float,
    val dateBaselineRatio: Float,
    val lineSpacingMultiplier: Float,
)

internal fun resolveDoubleLineClockStyle(
    persistedSize: String,
    legacyPresetScale: Float,
    useCustomScale: Boolean,
    customTimeScale: Float,
    customDateScale: Float,
): DoubleLineClockStyle {
    if (useCustomScale) {
        return DoubleLineClockStyle(
            timeScale = customTimeScale.coerceIn(0.50f, 1.10f),
            dateScale = customDateScale.coerceIn(0.50f, 1.10f),
            dateBaselineRatio = 0.66f,
            lineSpacingMultiplier = -0.85f,
        )
    }
    val size = when (persistedSize) {
        "small", "compact", "standard", "large", "extra_large" -> persistedSize
        else -> when {
            legacyPresetScale < 0.925f -> "small"
            legacyPresetScale < 0.975f -> "compact"
            legacyPresetScale < 1.025f -> "standard"
            legacyPresetScale < 1.075f -> "large"
            else -> "extra_large"
        }
    }
    return when (size) {
        "small" -> DoubleLineClockStyle(0.74f, 0.68f, 0.72f, -0.65f)
        "compact" -> DoubleLineClockStyle(0.78f, 0.72f, 0.69f, -0.65f)
        "large" -> DoubleLineClockStyle(0.86f, 0.80f, 0.63f, -0.65f)
        "extra_large" -> DoubleLineClockStyle(0.90f, 0.84f, 0.60f, -0.65f)
        else -> DoubleLineClockStyle(0.82f, 0.76f, 0.66f, -0.65f)
    }
}
