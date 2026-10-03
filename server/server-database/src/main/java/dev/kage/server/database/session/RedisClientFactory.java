package dev.kage.server.database.session;

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
     * test-while-idle + eviction định kỳ), nên phải truyền tường minh để không đổi hành vi. Timeout
     * kết nối/socket giữ mặc định của {@link DefaultJedisClientConfig} như trước.
     */
    public static RedisClient create(RedisConfig config) {
        return RedisClient.builder()
                .hostAndPort(new HostAndPort(config.host(), config.port()))
                .clientConfig(DefaultJedisClientConfig.builder()
                        .password(config.password())
                        .build())
                .poolConfig(new GenericObjectPoolConfig<Connection>())
                .build();
    }
}
