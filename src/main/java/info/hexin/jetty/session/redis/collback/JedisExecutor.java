package info.hexin.jetty.session.redis.collback;

public interface JedisExecutor {
    <V> V execute(JedisCallback<V> cb);
}
