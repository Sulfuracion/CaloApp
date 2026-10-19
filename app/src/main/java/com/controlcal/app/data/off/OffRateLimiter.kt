package com.controlcal.app.data.off

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

sealed interface RateLimitResult {
    data object Allowed : RateLimitResult
    data class Limited(val secondsRemaining: Int) : RateLimitResult
}

@Singleton
class OffRateLimiter @Inject constructor(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    companion object {
        const val DEFAULT_MAX_REQUESTS = 10
        const val DEFAULT_WINDOW_MILLIS = 60_000L
    }

    private val requestTimestamps = mutableListOf<Long>()
    private val lock = Any()

    fun tryAcquire(): RateLimitResult = synchronized(lock) {
        val now = clock.millis()
        val threshold = now - windowMillis

        requestTimestamps.removeAll { it <= threshold }

        if (requestTimestamps.size < maxRequests) {
            requestTimestamps.add(now)
            RateLimitResult.Allowed
        } else {
            val oldest = requestTimestamps.first()
            val millisRemaining = (oldest + windowMillis) - now
            val secondsRemaining = maxOf(1, ceil(millisRemaining / 1000.0).toInt())
            RateLimitResult.Limited(secondsRemaining)
        }
    }

    fun reset() = synchronized(lock) {
        requestTimestamps.clear()
    }
}
