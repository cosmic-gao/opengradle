package com.opengradle.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * In-memory token store backed by Caffeine, with per-entry TTL.
 *
 * <p>Each {@link #put(String, UserContext, long)} call sets its own expiration,
 * matching the previous Redis {@code SET ... EX} semantics. Tokens are evicted
 * automatically once expired; reads do not extend the TTL.
 *
 * <p>State is local to the JVM — adequate for single-instance gateways and
 * development. Multi-instance deployments need a shared backend (Redis / JWT).
 */
@Component
public class TokenStore {

    private static final int MAX_TOKENS = 100_000;

    private final Cache<String, Entry> cache = Caffeine.newBuilder()
            .maximumSize(MAX_TOKENS)
            .expireAfter(new PerEntryExpiry())
            .build();

    public Optional<UserContext> get(String token) {
        Entry entry = cache.getIfPresent(token);
        return entry == null ? Optional.empty() : Optional.of(entry.ctx);
    }

    public void put(String token, UserContext ctx, long ttlSeconds) {
        cache.put(token, new Entry(ctx, TimeUnit.SECONDS.toNanos(ttlSeconds)));
    }

    public boolean remove(String token) {
        if (cache.getIfPresent(token) == null) {
            return false;
        }
        cache.invalidate(token);
        return true;
    }

    private static final class Entry {
        final UserContext ctx;
        final long ttlNanos;

        Entry(UserContext ctx, long ttlNanos) {
            this.ctx = ctx;
            this.ttlNanos = ttlNanos;
        }
    }

    /**
     * Per-entry expiration policy — TTL is carried on the {@link Entry} itself.
     * Pulled out of the cache builder so older IDEs (e.g. Eclipse JDT) don't
     * misreport overrides on the anonymous form.
     */
    private static final class PerEntryExpiry implements Expiry<String, Entry> {
        @Override
        public long expireAfterCreate(String key, Entry value, long currentTime) {
            return value.ttlNanos;
        }

        @Override
        public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
            return value.ttlNanos;
        }

        @Override
        public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
