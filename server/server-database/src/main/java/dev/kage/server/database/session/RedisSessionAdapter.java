package dev.kage.server.database.session;

import dev.kage.account.domain.SessionPort;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import redis.clients.jedis.JedisPool;

/**
 * Session token (ADR-017): opaque random 256-bit, key {@code session:{token}} → accountId,
 * sliding TTL 24h — mỗi {@link #validateAndRenew} gia hạn lại (DATABASE.md §6 key pattern).
 *
 * <p>Chỉ dùng lệnh Redis chuẩn (SETEX/GET/EXPIRE/DEL) — không module độc quyền Redis Stack
 * (ADR-005 ràng buộc bắt buộc, giữ chi phí đổi sang Valkey ≈ 0 nếu cần).
 */
public final class RedisSessionAdapter implements SessionPort {

    private static final int TOKEN_ENTROPY_BYTES = 32; // 256-bit (ADR-017)
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "session:";

    private final JedisPool jedisPool;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisSessionAdapter(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public String issue(long accountId) {
        var tokenBytes = new byte[TOKEN_ENTROPY_BYTES];
        secureRandom.nextBytes(tokenBytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        try (var jedis = jedisPool.getResource()) {
            jedis.setex(KEY_PREFIX + token, SESSION_TTL.toSeconds(), Long.toString(accountId));
        }
        return token;
    }

    @Override
    public Optional<Long> validateAndRenew(String token) {
        var key = KEY_PREFIX + token;
        try (var jedis = jedisPool.getResource()) {
            var value = jedis.get(key);
            if (value == null) {
                return Optional.empty();
            }
            jedis.expire(key, SESSION_TTL.toSeconds());
            return Optional.of(Long.parseLong(value));
        }
    }

    @Override
    public void revoke(String token) {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(KEY_PREFIX + token);
        }
    }
}
