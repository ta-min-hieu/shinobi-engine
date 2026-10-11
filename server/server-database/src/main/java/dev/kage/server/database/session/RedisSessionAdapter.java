package dev.kage.server.database.session;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.SetParams;

import dev.kage.account.domain.SessionPort;
import dev.kage.account.domain.SessionUnavailableException;

/**
 * Session token (ADR-017): opaque random 256-bit, key {@code session:{token}} → accountId,
 * sliding TTL 24h — mỗi {@link #validateAndRenew} gia hạn lại (DATABASE.md §6 key pattern).
 *
 * <p>Chỉ dùng lệnh Redis chuẩn (SET … EX/GET/EXPIRE/DEL) — không module độc quyền Redis Stack
 * (ADR-005 ràng buộc bắt buộc, giữ chi phí đổi sang Valkey ≈ 0 nếu cần).
 */
public final class RedisSessionAdapter implements SessionPort {

    private static final int TOKEN_ENTROPY_BYTES = 32; // 256-bit (ADR-017)
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "session:";

    private final RedisClient redisClient;
    private final SecureRandom secureRandom = new SecureRandom();

    /** {@code redisClient} tự quản pool kết nối; mỗi lệnh mượn rồi trả kết nối bên trong nó. */
    public RedisSessionAdapter(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public String issue(long accountId) {
        byte[] tokenBytes = new byte[TOKEN_ENTROPY_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        // SET ... EX là dạng thay thế chính thức của SETEX (Jedis 7.3 deprecate setex) — cùng ngữ
        // nghĩa nguyên tử: ghi giá trị và TTL trong MỘT lệnh.
        try {
            redisClient.set(
                    KEY_PREFIX + token,
                    Long.toString(accountId),
                    SetParams.setParams().ex(SESSION_TTL.toSeconds()));
        } catch (JedisException e) {
            // Dịch sang exception của domain: tầng application quyết định Redis hỏng thì làm gì
            // (2026-10-11: vẫn cho đăng nhập), và nó không được biết Jedis là gì.
            throw new SessionUnavailableException("Không ghi được session token vào Redis", e);
        }
        return token;
    }

    @Override
    public Optional<Long> validateAndRenew(String token) {
        String key = KEY_PREFIX + token;
        String value = redisClient.get(key);
        if (value == null) {
            return Optional.empty();
        }
        redisClient.expire(key, SESSION_TTL.toSeconds());
        return Optional.of(Long.parseLong(value));
    }

    @Override
    public void revoke(String token) {
        try {
            redisClient.del(KEY_PREFIX + token);
        } catch (JedisException e) {
            throw new SessionUnavailableException("Không thu hồi được session token ở Redis", e);
        }
    }
}
