package com.controlcal.app.camera

/**
 * Validates barcode scans by requiring a configurable number of consecutive
 * identical readings (default 2) within a maximum time interval before accepting.
 */
class BarcodeConsecutiveGate(
    private val requiredConsecutiveMatches: Int = 2,
    private val maxIntervalMs: Long = 1500L
) {
    private var lastCandidate: String? = null
    private var matchCount: Int = 0
    private var lastSeenTimestamp: Long = 0L

    /**
     * Feeds a barcode reading into the gate.
     *
     * @param code The barcode string read from the frame.
     * @param timestamp The timestamp of the reading in milliseconds (defaults to system time).
     * @return `true` if this reading completes the required number of consecutive matches, `false` otherwise.
     */
    @Synchronized
    fun onBarcodeRead(code: String, timestamp: Long = System.currentTimeMillis()): Boolean {
        if (code.isBlank()) return false

        if (code == lastCandidate && (timestamp - lastSeenTimestamp) <= maxIntervalMs) {
            matchCount++
        } else {
            lastCandidate = code
            matchCount = 1
        }
        lastSeenTimestamp = timestamp

        return matchCount >= requiredConsecutiveMatches
    }

    /**
     * Resets the gate state.
     */
    @Synchronized
    fun reset() {
        lastCandidate = null
        matchCount = 0
        lastSeenTimestamp = 0L
    }
}
