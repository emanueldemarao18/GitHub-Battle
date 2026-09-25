package com.github.battle.app.client;

import com.github.battle.app.config.BattleProperties;
import com.github.battle.app.exception.BattleException;
import com.github.battle.app.model.Profile;
import com.github.battle.app.service.ComparisonDeadline;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;

class CachedProfileProviderTests {
    private static BattleProperties settings(int size, int workers) {
        return new BattleProperties(Duration.ofSeconds(3), Duration.ofMinutes(5), size, workers, 60, "");
    }
    private static Profile profile(String name) {
        return new Profile(name, null, "", "", 1, 1, 1, null, null, Instant.EPOCH);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("Latch timeout"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    @Test void reusesNormalizedProfilesAndEvictsLeastRecentlyUsedEntry() {
        var calls = new AtomicInteger();
        var cache = new CachedProfileProvider(name -> { calls.incrementAndGet(); return profile(name); }, settings(1, 4));
        try {
            assertThat(cache.fetch(" Left ").fetchedAt()).isEqualTo(Instant.EPOCH);
            cache.fetch("left");
            assertThat(calls).hasValue(1);
            cache.fetch("right");
            cache.fetch("left");
            assertThat(calls).hasValue(3);
        } finally { cache.close(); }
    }

    @Test void failuresAreNotCached() {
        var calls = new AtomicInteger();
        var cache = new CachedProfileProvider(name -> {
            if (calls.incrementAndGet() == 1) throw new BattleException(HttpStatus.BAD_GATEWAY, "upstream failed");
            return profile(name);
        }, settings(1, 4));
        try {
            assertThatThrownBy(() -> cache.fetch("left")).isInstanceOf(BattleException.class);
            assertThat(cache.fetch("left").username()).isEqualTo("left");
            assertThat(calls).hasValue(2);
        } finally { cache.close(); }
    }

    @Test void timedOutWaiterDoesNotCancelSharedLoadAndCapacityIsBounded() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var cache = new CachedProfileProvider(name -> {
            calls.incrementAndGet(); started.countDown(); await(release); return profile(name);
        }, settings(1, 1));
        var callers = Executors.newSingleThreadExecutor();
        try {
            var first = callers.submit(() -> cache.fetch("left"));
            await(started);
            try (var deadline = ComparisonDeadline.start(Duration.ofMillis(50))) {
                assertThatThrownBy(() -> cache.fetch("LEFT")).isInstanceOfSatisfying(BattleException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
            }
            assertThatThrownBy(() -> cache.fetch("other")).isInstanceOfSatisfying(BattleException.class,
                    exception -> assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).username()).isEqualTo("left");
            assertThat(cache.fetch("left").username()).isEqualTo("left");
            assertThat(calls).hasValue(1);
        } finally { release.countDown(); callers.shutdownNow(); cache.close(); }
    }

    @Test void expiredProfilesAreFetchedAgain() throws Exception {
        var calls = new AtomicInteger();
        var properties = new BattleProperties(Duration.ofSeconds(3), Duration.ofMillis(1), 1, 4, 60, "");
        var cache = new CachedProfileProvider(name -> { calls.incrementAndGet(); return profile(name); }, properties);
        try {
            cache.fetch("left");
            Thread.sleep(10);
            cache.fetch("left");
            assertThat(calls).hasValue(2);
        } finally { cache.close(); }
    }
    @Test void comparisonUsesItsOwnDeadlineAcrossProfileWaits() {
        var release = new CountDownLatch(1);
        var cache = new CachedProfileProvider(name -> {
            if (name.equals("right")) await(release);
            return profile(name);
        }, settings(2, 4));
        var shortBudget = new BattleProperties(Duration.ofMillis(80), Duration.ofMinutes(5), 2, 4, 60, "");
        var service = new com.github.battle.app.service.BattleService(cache, shortBudget);
        try {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                    assertThatThrownBy(() -> service.compare("left", "right"))
                            .isInstanceOfSatisfying(BattleException.class,
                                    exception -> assertThat(exception.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT)));
        } finally { release.countDown(); cache.close(); }
    }}
