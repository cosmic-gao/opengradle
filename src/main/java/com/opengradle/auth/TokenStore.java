package com.opengradle.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的进程内 token 存储,支持每个条目独立 TTL。
 *
 * <p>{@link #put(String, UserContext, long)} 调用时各自指定过期时间,语义对齐
 * 之前 Redis 的 {@code SET ... EX}。过期条目由 Caffeine 自动清理,读取不会续期。
 *
 * <p>数据仅保留在当前 JVM 内 —— 单实例网关或本地开发足够;多实例部署需要切换
 * 到共享存储(Redis / JWT 等)。
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
     * 单条目过期策略 —— TTL 直接挂在 {@link Entry} 上。
     * 拆成命名内部类是为了规避部分 IDE(如 Eclipse JDT)对匿名内部类
     * + Caffeine 注解的 override 误报。
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
