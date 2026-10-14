package dev.kage.server.database.session;

import java.time.Duration;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisClient;

/** Dựng {@link RedisClient} (có pool kết nối bên trong) từ {@link RedisConfig}. */
public final class RedisClientFactory {

    private RedisClientFactory() {}

    /**
     * Dựng client Redis standalone có pool kết nối.
     *
     * <p>Pool dùng {@link GenericObjectPoolConfig} MẶC ĐỊNH của commons-pool2 — đúng cấu hình mà
     * {@code new JedisPool(host, port, user, password)} (deprecated từ Jedis 7) từng dùng. Builder
     * của {@link RedisClient} nếu để mặc định lại dùng {@code ConnectionPoolConfig} (bật
     * test-while-idle + eviction định kỳ), nên phải truyền tường minh để không đổi hành vi — ngoại lệ
     * duy nhất là trần chờ {@link #POOL_MAX_WAIT} (2026-10-14). Timeout
     * kết nối/socket giữ mặc định của {@link DefaultJedisClientConfig} như trước.
     */
    public static RedisClient create(RedisConfig config) {
        return RedisClient.builder()
                .hostAndPort(new HostAndPort(config.host(), config.port()))
                .clientConfig(DefaultJedisClientConfig.builder()
                        .password(config.password())
                        .build())
                .poolConfig(poolConfig())
                .build();
    }

    /**
     * Thời gian TỐI ĐA chờ một kết nối rảnh khi pool đã cạn (chủ dự án chốt 2026-10-14, docs/README.md
     * dòng 45(v); con số 2 giây là tôi chọn). Mặc định của commons-pool2 là chờ VÔ HẠN: Redis treo hoặc
     * pool bị giữ hết là mọi luồng đăng nhập treo theo, không lỗi nào để thấy. Hết hạn thì Jedis ném
     * {@code JedisExhaustedPoolException} (một {@code JedisException}) ⇒ {@code RedisSessionAdapter} đổi
     * thành {@code SessionUnavailableException} ⇒ đăng nhập vẫn qua với token rỗng (dòng 45(i)).
     */
    static final Duration POOL_MAX_WAIT = Duration.ofSeconds(2);

    /** Mặc định commons-pool2 (giữ hành vi cũ — xem {@link #create}) + trần chờ {@link #POOL_MAX_WAIT}. */
    static GenericObjectPoolConfig<Connection> poolConfig() {
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<Connection>();
        poolConfig.setMaxWait(POOL_MAX_WAIT);
        return poolConfig;
    }
}
