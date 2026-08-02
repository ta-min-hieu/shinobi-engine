package dev.kage.server.database.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisAccessControlException;

/** Test bằng Redis THẬT qua Testcontainers — verify sliding TTL + thu hồi tức thì (ADR-017). */
@Testcontainers
class RedisSessionAdapterIntegrationTest {

    private static final String TEST_PASSWORD = "test-password";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", TEST_PASSWORD)
            .withStartupTimeout(Duration.ofMinutes(2));

    private static JedisPool jedisPool() {
        return new JedisPool(REDIS.getHost(), REDIS.getMappedPort(6379), null, TEST_PASSWORD);
    }

    @Test
    void shouldValidateIssuedToken_andReturnAccountId() {
        try (JedisPool jedisPool = jedisPool()) {
            RedisSessionAdapter adapter = new RedisSessionAdapter(jedisPool);

            String token = adapter.issue(42L);
            Optional<Long> result = adapter.validateAndRenew(token);

            assertThat(result).contains(42L);
        }
    }

    @Test
    void shouldReturnEmpty_whenTokenNeverIssued() {
        try (JedisPool jedisPool = jedisPool()) {
            RedisSessionAdapter adapter = new RedisSessionAdapter(jedisPool);

            Optional<Long> result = adapter.validateAndRenew("token-khong-ton-tai");

            assertThat(result).isEmpty();
        }
    }

    @Test
    void shouldInvalidateImmediately_afterRevoke() {
        try (JedisPool jedisPool = jedisPool()) {
            RedisSessionAdapter adapter = new RedisSessionAdapter(jedisPool);
            String token = adapter.issue(7L);

            adapter.revoke(token);

            // Đúng lý do ADR-017 chọn session token thay JWT — thu hồi có hiệu lực NGAY, không
            // chờ hết hạn.
            assertThat(adapter.validateAndRenew(token)).isEmpty();
        }
    }

    @Test
    void shouldIssueDifferentTokens_forDifferentSessions() {
        try (JedisPool jedisPool = jedisPool()) {
            RedisSessionAdapter adapter = new RedisSessionAdapter(jedisPool);

            String tokenA = adapter.issue(1L);
            String tokenB = adapter.issue(2L);

            assertThat(tokenA).isNotEqualTo(tokenB);
            assertThat(adapter.validateAndRenew(tokenA)).contains(1L);
            assertThat(adapter.validateAndRenew(tokenB)).contains(2L);
        }
    }

    @Test
    void shouldRejectConnection_whenNoPasswordProvided() {
        // SECURITY.md §14 — verify runtime thật rằng `--requirepass` từ chối kết nối không auth,
        // không chỉ đọc cấu hình compose bằng mắt.
        try (JedisPool noAuthPool = new JedisPool(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            assertThatThrownBy(() -> {
                        try (Jedis jedis = noAuthPool.getResource()) {
                            jedis.ping();
                        }
                    })
                    .isInstanceOf(JedisAccessControlException.class)
                    .hasMessageContaining("NOAUTH");
        }
    }
}
