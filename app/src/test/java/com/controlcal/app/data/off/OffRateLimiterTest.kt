package com.controlcal.app.data.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class OffRateLimiterTest {

    private class MutableClock(
        private var currentInstant: Instant,
        private val zone: ZoneId = ZoneId.of("UTC")
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId?): Clock = MutableClock(currentInstant, zone ?: this.zone)
        override fun instant(): Instant = currentInstant

        fun advanceSeconds(seconds: Long) {
            currentInstant = currentInstant.plusSeconds(seconds)
        }

        fun advanceMillis(millis: Long) {
            currentInstant = currentInstant.plusMillis(millis)
        }
    }

    @Test
    fun allowsUpToTenRequests_andLimitsEleventh() {
        val startInstant = Instant.parse("2026-10-17T12:00:00Z")
        val clock = MutableClock(startInstant)
        val rateLimiter = OffRateLimiter(clock = clock, maxRequests = 10, windowMillis = 60_000L)

        // 10 requests allowed
        for (i in 1..10) {
            val result = rateLimiter.tryAcquire()
            assertTrue("Request $i should be allowed", result is RateLimitResult.Allowed)
            clock.advanceSeconds(1) // 1 second apart
        }

        // At this point, 10 requests made at t = 0, 1, 2, ..., 9.
        // Current time is t = 10s.
        // The 11th request at t = 10s should be limited.
        // The oldest request was at t = 0s, expires at t = 60s.
        // Remaining time: 60 - 10 = 50 seconds.
        val limitedResult = rateLimiter.tryAcquire()
        assertTrue(limitedResult is RateLimitResult.Limited)
        val limited = limitedResult as RateLimitResult.Limited
        assertEquals(50, limited.secondsRemaining)

        // Advance clock by 49 seconds (t = 59s) -> still limited (1s remaining)
        clock.advanceSeconds(49)
        val stillLimited = rateLimiter.tryAcquire()
        assertTrue(stillLimited is RateLimitResult.Limited)
        assertEquals(1, (stillLimited as RateLimitResult.Limited).secondsRemaining)

        // Advance clock by 2 seconds (t = 61s) -> oldest request at t=0 has expired!
        clock.advanceSeconds(2)
        val allowedAgain = rateLimiter.tryAcquire()
        assertTrue("Request at t=61s should be allowed after oldest expired", allowedAgain is RateLimitResult.Allowed)
    }

    @Test
    fun reset_clearsAllHistory() {
        val clock = MutableClock(Instant.parse("2026-10-17T12:00:00Z"))
        val rateLimiter = OffRateLimiter(clock = clock, maxRequests = 2, windowMillis = 60_000L)

        assertTrue(rateLimiter.tryAcquire() is RateLimitResult.Allowed)
        assertTrue(rateLimiter.tryAcquire() is RateLimitResult.Allowed)
        assertTrue(rateLimiter.tryAcquire() is RateLimitResult.Limited)

        rateLimiter.reset()

        assertTrue(rateLimiter.tryAcquire() is RateLimitResult.Allowed)
    }
}
