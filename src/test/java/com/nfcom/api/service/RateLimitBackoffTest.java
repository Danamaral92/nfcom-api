package com.nfcom.api.service;

import com.nfcom.api.shared.error.NfcomException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitBackoffTest {

    // --- isRateLimit ---

    @Test
    void cStat678IsRateLimit() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(10));
        assertTrue(backoff.isRateLimit(678));
    }

    @Test
    void cStat100IsNotRateLimit() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(10));
        assertFalse(backoff.isRateLimit(100));
    }

    @Test
    void cStatZeroIsNotRateLimit() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(10));
        assertFalse(backoff.isRateLimit(0));
    }

    @Test
    void cStat678UsingDefaultConstructor() {
        RateLimitBackoff backoff = new RateLimitBackoff();
        assertTrue(backoff.isRateLimit(678));
    }

    // --- executeWithRetry: successful on first try ---

    @Test
    void successfulOperationReturnsResult() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(10));
        String result = backoff.executeWithRetry(() -> "success");
        assertEquals("success", result);
    }

    // --- executeWithRetry: non-retryable error ---

    @Test
    void nonRetryableErrorThrowsImmediately() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(10));

        NfcomException ex = assertThrows(NfcomException.class, () ->
                backoff.executeWithRetry(() -> {
                    throw new NfcomException("SEFAZ_ERROR", 502, "Bad gateway");
                })
        );
        assertEquals("SEFAZ_ERROR", ex.getCode());
        assertEquals(502, ex.getHttpStatus());
    }

    // --- executeWithRetry: rate limit exhausts retries ---

    @Test
    void rateLimitExhaustsRetriesThrowsRateLimitExceeded() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(5));
        AtomicInteger attempts = new AtomicInteger(0);

        NfcomException ex = assertThrows(NfcomException.class, () ->
                backoff.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new NfcomException("RATE_LIMIT", 503, "cStat 678 — Consumo Indevido");
                })
        );
        assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
        // 1 original + 3 retries = 4 total attempts
        assertEquals(4, attempts.get());
    }

    // --- executeWithRetry: recovers after rate limit ---

    @Test
    void recoversAfterRateLimitOnSecondAttempt() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(5));
        AtomicInteger attempts = new AtomicInteger(0);

        String result = backoff.executeWithRetry(() -> {
            int count = attempts.incrementAndGet();
            if (count < 2) {
                throw new NfcomException("RATE_LIMIT", 503, "cStat 678 — Consumo Indevido");
            }
            return "recovered";
        });
        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    // --- executeWithRetry: configurable max retries ---

    @Test
    void respectsCustomMaxRetries() {
        RateLimitBackoff backoff = new RateLimitBackoff(1, Duration.ofMillis(5));
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(NfcomException.class, () ->
                backoff.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new NfcomException("RATE_LIMIT", 503, "cStat 678");
                })
        );
        // 1 original + 1 retry = 2 total attempts
        assertEquals(2, attempts.get());
    }

    // --- executeWithRetry: non-NfcomException propagates ---

    @Test
    void nonNfcomExceptionPropagatesWithoutRetry() {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(5));
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(IllegalArgumentException.class, () ->
                backoff.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException("unexpected");
                })
        );
        assertEquals(1, attempts.get());
    }

    // --- executeWithRetry: zero retries ---

    @Test
    void zeroRetriesMeansNoRetry() {
        RateLimitBackoff backoff = new RateLimitBackoff(0, Duration.ofMillis(5));
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(NfcomException.class, () ->
                backoff.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new NfcomException("RATE_LIMIT", 503, "cStat 678");
                })
        );
        assertEquals(1, attempts.get());
    }

    // --- Thread safety: concurrent access ---

    @Test
    void concurrentAccessDoesNotCauseIssues() throws InterruptedException {
        RateLimitBackoff backoff = new RateLimitBackoff(3, Duration.ofMillis(5));

        // Run 10 threads each executing a successful operation
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() ->
                    assertEquals("ok", backoff.executeWithRetry(() -> "ok"))
            );
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join(5000);
        }
        // All threads completed without exception
    }
}
