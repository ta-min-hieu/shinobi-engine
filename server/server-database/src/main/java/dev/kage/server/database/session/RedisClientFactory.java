package dev.kage.server.database.session;

import redis.clients.jedis.JedisPool;

/** Dựng {@link JedisPool} từ {@link RedisConfig}. */
public final class RedisClientFactory {

    private RedisClientFactory() {
    }

    public static JedisPool create(RedisConfig config) {
        return new JedisPool(config.host(), config.port(), null, config.password());
    }
}
