package dev.kage.server.database.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Pool Redis không được chờ vô hạn (chủ dự án chốt 2026-10-14, docs/README.md dòng 45(v)) — mặc định
 * của commons-pool2 là {@code -1}, tức Redis treo kéo mọi luồng đăng nhập treo theo.
 */
class RedisClientFactoryTest {

    @Test
    void shouldBoundTheWaitForAPooledConnection() {
        Duration maxWait = RedisClientFactory.poolConfig().getMaxWaitDuration();

        assertThat(maxWait).isPositive().isEqualTo(RedisClientFactory.POOL_MAX_WAIT);
    }

    /** Ngoài trần chờ, pool vẫn là mặc định commons-pool2 như thời {@code JedisPool} (xem javadoc factory). */
    @Test
    void shouldKeepTheDefaultPoolBehaviourOtherwise() {
        assertThat(RedisClientFactory.poolConfig().getTestWhileIdle()).isFalse();
        assertThat(RedisClientFactory.poolConfig().getBlockWhenExhausted()).isTrue();
    }
}
