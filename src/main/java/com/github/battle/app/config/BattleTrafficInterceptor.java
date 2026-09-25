package com.github.battle.app.config;

import com.github.battle.app.exception.BattleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.Semaphore;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Global per-instance budget; intentionally does not trust forwarded client IP headers. */
@Component
public class BattleTrafficInterceptor implements HandlerInterceptor {
    private static final String START = BattleTrafficInterceptor.class.getName() + ".start";
    private final Semaphore concurrent = new Semaphore(8);
    private final int capacity;
    private double tokens;
    private long updated = System.nanoTime();

    public BattleTrafficInterceptor(BattleProperties properties) {
        capacity = properties.requestsPerMinute();
        tokens = capacity;
    }

    private synchronized boolean takeToken() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - updated) / 60_000_000_000d * capacity);
        updated = now;
        if (tokens < 1) return false;
        tokens--;
        return true;
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equals(request.getMethod())) return true;
        if (!takeToken()) {
            response.setHeader("Retry-After", Long.toString(Math.max(1, (long) Math.ceil(60d / capacity))));
            throw new BattleException(HttpStatus.TOO_MANY_REQUESTS, "The request limit was reached. Please try again later.");
        }
        if (!concurrent.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            throw new BattleException(HttpStatus.SERVICE_UNAVAILABLE, "The server is busy. Please try again later.");
        }
        request.setAttribute(START, System.nanoTime());
        return true;
    }

    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (request.getAttribute(START) instanceof Long start) {
            concurrent.release();
            request.removeAttribute(START);
            LoggerFactory.getLogger(BattleTrafficInterceptor.class).info("battle_completed status={} duration_ms={}",
                    response.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
    }
}
