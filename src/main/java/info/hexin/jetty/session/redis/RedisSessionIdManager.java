package info.hexin.jetty.session.redis;

import info.hexin.jetty.session.redis.collback.JedisCallback;
import info.hexin.jetty.session.redis.collback.JedisExecutor;
import info.hexin.jetty.session.redis.collback.PooledJedisExecutor;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.TransactionBlock;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.util.SafeEncoder;

import java.util.LinkedList;
import java.util.List;

/**
 * @author hexin
 */
public final class RedisSessionIdManager extends SessionIdManagerSkeleton {


    final static Logger LOG = Log.getLogger(RedisSessionIdManager.class);

    private static final Long ZERO = 0L;
    private static final String REDIS_SESSIONS_KEY = "jetty-sessions";
    static final String REDIS_SESSION_KEY = "jetty-session-";

    private final JedisExecutor jedisExecutor;

    public RedisSessionIdManager(Server server, JedisPool jedisPool) {
        super(server);
        // LOG.info("RedisSessionIdManager   ", new Object[] { null });
        this.jedisExecutor = new PooledJedisExecutor(jedisPool);
    }

    public JedisExecutor getJedisExecutor() {
        return jedisExecutor;
    }

    @Override
    protected void deleteClusterId(final String clusterId) {
        //   LOG.info(" deleteClusterId session is id >>>> {}", clusterId);
        jedisExecutor.execute(new JedisCallback<Object>() {
            @Override
            public Object execute(Jedis jedis) {
                return jedis.srem(REDIS_SESSIONS_KEY, clusterId);
            }
        });
    }

    @Override
    protected void storeClusterId(final String clusterId) {
        //  LOG.info(" storeClusterId session is id >>>> {}", clusterId);
        jedisExecutor.execute(new JedisCallback<Object>() {
            @Override
            public Object execute(Jedis jedis) {
                return jedis.sadd(REDIS_SESSIONS_KEY, clusterId);
            }
        });
    }

    @Override
    protected boolean hasClusterId(final String clusterId) {
        ////LOG.info("hasClusterId   ", new Object[] { null });
        return jedisExecutor.execute(new JedisCallback<Boolean>() {
            @Override
            public Boolean execute(Jedis jedis) {
                return jedis.sismember(SafeEncoder.encode(REDIS_SESSIONS_KEY), SafeEncoder.encode(clusterId));
            }
        });
    }

    @Override
    protected List<String> scavenge(final List<String> clusterIds) {
        List<String> expired = new LinkedList<String>();
        List<Object> status = jedisExecutor.execute(new JedisCallback<List<Object>>() {
            @Override
            public List<Object> execute(Jedis jedis) {
                return jedis.multi(new TransactionBlock() {
                    @Override
                    public void execute() throws JedisException {
                        for (String clusterId : clusterIds) {
                            exists(SafeEncoder.encode(REDIS_SESSION_KEY + clusterId));
                        }
                    }
                });
            }
        });
        for (int i = 0; i < status.size(); i++)
            if (ZERO.equals(status.get(i)))
                expired.add(clusterIds.get(i));
        if (LOG.isDebugEnabled() && !expired.isEmpty()) {
            // LOG.debug("[RedisSessionIdManager] Scavenger found {} sessions to expire: {}", expired.size(), expired);
        }
        return expired;
    }
}
