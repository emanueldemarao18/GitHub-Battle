package com.github.battle.app.client;

import com.github.battle.app.config.BattleProperties;
import com.github.battle.app.exception.BattleException;
import com.github.battle.app.model.Profile;
import com.github.battle.app.service.ComparisonDeadline;
import com.github.battle.app.service.ProfileProvider;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CachedProfileProvider implements ProfileProvider {
    private record Entry(Profile profile, long expiresAt) {}
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, CompletableFuture<Profile>> loading = new HashMap<>();
    private final ProfileProvider delegate;
    private final BattleProperties properties;
    private final ThreadPoolExecutor workers;

    @Autowired
    public CachedProfileProvider(GithubClient delegate, BattleProperties properties) {
        this((ProfileProvider) delegate, properties);
    }

    CachedProfileProvider(ProfileProvider delegate, BattleProperties properties) {
        this.delegate = delegate;
        this.properties = properties;
        workers = new ThreadPoolExecutor(properties.upstreamWorkers(), properties.upstreamWorkers(),
                0, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                    var thread = new Thread(runnable, "github-profile");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override public Profile fetch(String username) {
        ComparisonDeadline.remainingNanos();
        String key = username.trim().toLowerCase(Locale.ROOT);
        CompletableFuture<Profile> future;
        synchronized (this) {
            Entry entry = cache.get(key);
            if (entry != null && entry.expiresAt() - System.nanoTime() > 0) return entry.profile();
            cache.remove(key);
            future = loading.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                loading.put(key, future);
                var pending = future;
                try { workers.execute(() -> load(key, pending)); }
                catch (RejectedExecutionException exception) {
                    loading.remove(key);
                    throw new BattleException(HttpStatus.SERVICE_UNAVAILABLE, "The server is busy. Please try again later.");
                }
            }
        }
        try {
            return future.get(Math.min(ComparisonDeadline.remainingNanos(), properties.timeout().toNanos()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            // A caller's deadline must not cancel a load shared by another caller.
            throw ComparisonDeadline.expired();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw ComparisonDeadline.expired();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new BattleException(HttpStatus.BAD_GATEWAY, "Could not retrieve data from GitHub.");
        }
    }

    private void load(String key, CompletableFuture<Profile> future) {
        try (var deadline = ComparisonDeadline.start(properties.timeout())) {
            Profile profile = delegate.fetch(key);
            ComparisonDeadline.remainingNanos();
            synchronized (this) {
                cache.put(key, new Entry(profile, System.nanoTime() + properties.cacheTtl().toNanos()));
                while (cache.size() > properties.cacheSize()) cache.remove(cache.keySet().iterator().next());
                loading.remove(key);
                future.complete(profile);
            }
        } catch (Throwable failure) {
            synchronized (this) {
                loading.remove(key);
                future.completeExceptionally(failure);
            }
            if (failure instanceof Error error) throw error;
        }
    }

    @PreDestroy public void close() { workers.shutdownNow(); }
}
