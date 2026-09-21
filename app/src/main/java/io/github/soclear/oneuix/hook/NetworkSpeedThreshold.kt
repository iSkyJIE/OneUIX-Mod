package io.github.soclear.oneuix.hook

/** Shared threshold rule for the separate upload/download status-bar speed display. */
internal object NetworkSpeedThreshold {
    /** A zero threshold disables filtering; equality to a positive threshold is hidden. */
    fun shouldShow(txBytesPerSecond: Float, rxBytesPerSecond: Float, thresholdKb: Int): Boolean {
        if (thresholdKb <= 0) return true
        val thresholdBytesPerSecond = thresholdKb.toDouble() * 1024.0
        return txBytesPerSecond > thresholdBytesPerSecond || rxBytesPerSecond > thresholdBytesPerSecond
    }
}
