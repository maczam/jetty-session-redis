package info.hexin.jetty.session.redis.collback;

import redis.clients.jedis.Jedis;

/**
 * 
 * 执行器
 * 
 * @author hexin
 * 
 * @param <V>
 */
public interface JedisCallback<V> {
    V execute(Jedis jedis);
}
