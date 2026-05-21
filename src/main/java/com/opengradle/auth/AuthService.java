package com.opengradle.auth;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.opengradle.auth.dto.LoginRequest;
import com.opengradle.auth.dto.LoginResponse;
import com.opengradle.constant.RedisConstant;
import com.opengradle.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Token-based authentication service.
 *
 * <p>This is a deliberately simple implementation suitable as a starting point:
 * <ul>
 *   <li>users are stored in an in-memory map (replace with DB / LDAP for real systems)</li>
 *   <li>passwords are stored as SHA-256 digests (replace with BCrypt for real systems)</li>
 *   <li>tokens are opaque UUIDs persisted to Redis keyed by {@link RedisConstant#TOKENS}</li>
 * </ul>
 *
 * <p>The Redis layout matches what {@code AuthFilter} expects, so swapping the
 * user store / password scheme later does not require filter changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RedisUtils redisUtils;

    /** Token TTL — refreshed on each successful auth. */
    @Value("${auth.token-ttl-seconds:7200}")
    private long tokenTtlSeconds;

    /**
     * Demo user store. Replace with a real {@code UserRepository} lookup.
     *
     * <p>Key: username; value: {@link DemoUser} with hashed password and identity.
     */
    private final Map<String, DemoUser> userStore = new HashMap<>();

    @PostConstruct
    void seedDemoUsers() {
        // username / password / userId / tenantCode / superAdmin
        register("admin",   "admin123", 1L,    1000L, true);
        register("alice",   "alice123", 1001L, 1000L, false);
        register("bob",     "bob123",   1002L, 1000L, false);
        log.info("auth: seeded {} demo users", userStore.size());
    }

    private void register(String username, String rawPwd, long userId, long tenantCode, boolean superAdmin) {
        userStore.put(username, new DemoUser(
                userId, username, sha256(rawPwd), tenantCode, superAdmin));
    }

    /**
     * Authenticate the credentials, mint a fresh token, and persist the
     * {@link UserContext} under that token in Redis.
     */
    public Optional<LoginResponse> login(LoginRequest req) {
        if (req == null || CharSequenceUtil.isBlank(req.getUsername()) || CharSequenceUtil.isBlank(req.getPassword())) {
            log.debug("auth: login rejected, empty credentials");
            return Optional.empty();
        }
        DemoUser user = userStore.get(req.getUsername().trim().toLowerCase());
        if (user == null) {
            // also try as-is (case sensitive) — keep behavior predictable for demos
            user = userStore.get(req.getUsername());
        }
        if (user == null) {
            log.debug("auth: login rejected, user not found: {}", req.getUsername());
            return Optional.empty();
        }
        if (!user.passwordHash.equals(sha256(req.getPassword()))) {
            log.debug("auth: login rejected, password mismatch: {}", req.getUsername());
            return Optional.empty();
        }

        UserContext ctx = UserContext.builder()
                .userId(user.userId)
                .username(user.username)
                .tenantCode(user.tenantCode)
                .superAdmin(user.superAdmin)
                .build();

        String token = IdUtil.fastSimpleUUID();
        redisUtils.set(RedisConstant.TOKENS + token, ctx, tokenTtlSeconds);
        log.info("auth: login ok, user={}, token={}", user.username, token);

        return Optional.of(LoginResponse.builder()
                .token(token)
                .expiresIn(tokenTtlSeconds)
                .user(ctx)
                .build());
    }

    /** Invalidate the token by deleting the cache entry. */
    public boolean logout(String token) {
        if (CharSequenceUtil.isBlank(token)) {
            return false;
        }
        Boolean deleted = redisUtils.delete(RedisConstant.TOKENS + token);
        log.info("auth: logout token={}, deleted={}", token, deleted);
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * Retrieve the cached user context for a token, or {@link Optional#empty()}
     * if the token is unknown / expired.
     */
    public Optional<UserContext> getUserContext(String token) {
        if (CharSequenceUtil.isBlank(token)) {
            return Optional.empty();
        }
        Object raw = redisUtils.get(RedisConstant.TOKENS + token);
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof UserContext) {
            return Optional.of((UserContext) raw);
        }
        // Fallback: legacy entries that just stored the userId as a string.
        try {
            Long userId = Long.parseLong(String.valueOf(raw));
            return Optional.of(UserContext.builder().userId(userId).build());
        } catch (NumberFormatException e) {
            log.warn("auth: token cache value is not a UserContext, raw type={}", raw.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String sha256(String input) {
        return SecureUtil.sha256(input);
    }

    /** Internal demo user record. */
    private static final class DemoUser {
        final long userId;
        final String username;
        final String passwordHash;
        final long tenantCode;
        final boolean superAdmin;

        DemoUser(long userId, String username, String passwordHash, long tenantCode, boolean superAdmin) {
            this.userId = userId;
            this.username = username;
            this.passwordHash = passwordHash;
            this.tenantCode = tenantCode;
            this.superAdmin = superAdmin;
        }
    }
}
