package com.github.battle.app.service;

import java.time.Duration;
import com.github.battle.app.exception.BattleException;
import org.springframework.http.HttpStatus;

/** A monotonic budget shared by both profile waits and checked before upstream calls. */
public final class ComparisonDeadline implements AutoCloseable {
    private static final ThreadLocal<Long> END = new ThreadLocal<>();
    private final Long previous = END.get();

    private ComparisonDeadline(Duration timeout) { END.set(System.nanoTime() + timeout.toNanos()); }
    public static ComparisonDeadline start(Duration timeout) { return new ComparisonDeadline(timeout); }
    public static long remainingNanos() {
        Long end = END.get();
        long remaining = end == null ? Long.MAX_VALUE : end - System.nanoTime();
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) throw expired();
        return remaining;
    }
    public static BattleException expired() {
        return new BattleException(HttpStatus.GATEWAY_TIMEOUT, "The comparison deadline was exceeded. Please try again later.");
    }
    @Override public void close() {
        if (previous == null) END.remove(); else END.set(previous);
    }
}
