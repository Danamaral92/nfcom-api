package com.nfcom.api.service;

import com.nfcom.api.shared.error.ErrorDetail;
import com.nfcom.api.shared.error.NfcomException;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Implements exponential backoff for SEFAZ rate limiting (cStat 678 — Consumo Indevido).
 * <p>
 * Thread-safe: uses ReentrantLock to serialize retry state across concurrent callers.
 */
@ApplicationScoped
public class RateLimitBackoff {

    static final String RATE_LIMIT_CODE = "RATE_LIMIT";
    static final String RATE_LIMIT_EXCEEDED_CODE = "RATE_LIMIT_EXCEEDED";

    private final int maxRetries;
    private final Duration baseDelay;
    private final Random random = new Random();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Default constructor used by CDI. Reads config from application.properties.
     * Default: maxRetries=3, baseDelay=30s
     */
    public RateLimitBackoff() {
        this(3, Duration.ofSeconds(30));
    }

    /**
     * Constructor with explicit configuration.
     *
     * @param maxRetries maximum number of retry attempts (0 = no retry)
     * @param baseDelay  initial delay before first retry
     */
    public RateLimitBackoff(int maxRetries, Duration baseDelay) {
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
    }

    /**
     * Checks if a SEFAZ cStat code indicates a retryable rate limit (cStat 678).
     */
    public boolean isRateLimit(int cStat) {
        return cStat == 678;
    }

    /**
     * Executes the given operation with retry on rate-limit errors.
     * <p>
     * If the operation throws an {@link NfcomException} with code "RATE_LIMIT",
     * it will be retried up to {@code maxRetries} times with exponential backoff.
     * Any other exception type is rethrown immediately without retry.
     *
     * @param operation the operation to execute
     * @param <T>       the return type
     * @return the result of the operation
     * @throws NfcomException with code "RATE_LIMIT_EXCEEDED" if all retries are exhausted
     */
    public <T> T executeWithRetry(Supplier<T> operation) {
        lock.lock();
        try {
            int attempts = 0;
            while (true) {
                try {
                    return operation.get();
                } catch (NfcomException e) {
                    if (!isRateLimitException(e)) {
                        // Non-rate-limit NfcomException: propagate immediately
                        throw e;
                    }
                    if (attempts >= maxRetries) {
                        throw new NfcomException(RATE_LIMIT_EXCEEDED_CODE, 429,
                                "Rate limit exceeded after " + maxRetries + " retries",
                                List.of(new ErrorDetail(null, "cStat 678 — Consumo Indevido. "
                                        + "Retries exhausted after " + (attempts + 1) + " attempts.")));
                    }
                    attempts++;
                    long delayMs = calculateDelayMs(attempts);
                    sleep(delayMs);
                } catch (RuntimeException e) {
                    // Non-NfcomException: propagate immediately without retry
                    throw e;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Calculates the exponential backoff delay for the given attempt number.
     * Formula: baseDelay * 2^(attempt-1) + random(0, 500ms)
     */
    long calculateDelayMs(int attempt) {
        long exponential = baseDelay.toMillis() * (1L << (attempt - 1));
        long jitter = random.nextLong(0, 501);
        return exponential + jitter;
    }

    private boolean isRateLimitException(NfcomException e) {
        return RATE_LIMIT_CODE.equals(e.getCode());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new NfcomException("INTERRUPTED", 503, "Operation interrupted during backoff");
        }
    }
}
