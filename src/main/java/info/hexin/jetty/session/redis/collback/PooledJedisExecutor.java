package info.hexin.jetty.session.redis.collback;

import info.hexin.jetty.session.redis.collback.JedisExecutor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class PooledJedisExecutor implements JedisExecutor {
    private final JedisPool jedisPool;

    public PooledJedisExecutor(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public <V> V execute(JedisCallback<V> cb) {
        Jedis jedis = jedisPool.getResource();
        try {
            return cb.execute(jedis);
        } catch (JedisException e) {
            jedisPool.returnBrokenResource(jedis);
            throw e;
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

}
