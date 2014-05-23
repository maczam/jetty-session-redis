package info.hexin.jetty.session.redis;

import info.hexin.jetty.session.redis.collback.JedisCallback;
import info.hexin.jetty.session.redis.collback.JedisExecutor;
import info.hexin.jetty.session.redis.serializer.ByteSerializer;
import info.hexin.jetty.session.redis.serializer.Converter;
import info.hexin.jetty.session.redis.serializer.Serializer;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.TransactionBlock;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.util.SafeEncoder;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public final class RedisSessionManager extends SessionManagerSkeleton<RedisSessionManager.RedisSession> {

    final static Logger LOG = Log.getLogger(RedisSessionManager.class);
    private static final String[] FIELDS = { "id", "created", "accessed", "lastNode", "expiryTime", "lastSaved",
            "lastAccessed", "maxIdle", "cookieSet", "attributes" };

    private String __app_id;
    private final JedisExecutor jedisExecutor;
    private final Serializer serializer;

    private long saveIntervalSec = 20; // only persist changes to session access
                                       // times every 20 secs

    public RedisSessionManager(RedisSessionIdManager sessionIdManager) {
        this.serializer = new ByteSerializer();
        this.jedisExecutor = sessionIdManager.getJedisExecutor();
    }

    public void setSaveInterval(long sec) {
        saveIntervalSec = sec;
    }

    public void setAppId(String appId) {
        this.__app_id = appId;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
    }

    @Override
    protected RedisSession loadSession(final String clusterId, final RedisSession current) {
        long now = System.currentTimeMillis();
        RedisSession loaded;
        if (current == null) {
           // LOG.debug(" 当前内存中不存在session , loading id={}", clusterId);
            loaded = loadFromStore(clusterId, current);
        } else if (current.requestStarted()) {
          //  LOG.debug(" 当前内存中存在session , loading id={}", clusterId);
            loaded = loadFromStore(clusterId, current);
        } else {
            loaded = current;
        }
        if (loaded == null) {
           // LOG.debug("No session found in Redis for id={}", clusterId);
            if (current != null){
                current.invalidate();
            }
        } else if (loaded == current) {
           // LOG.debug(" No change found in Redis for session id={}", clusterId);
            return loaded;
        } else if (!loaded.lastNode.equals(getSessionIdManager().getWorkerName()) || current == null) {
            // if the session in the database has not already expired
            if (loaded.expiryTime * 1000 > now) {
                // session last used on a different node, or we don't have it in
                // memory
                loaded.changeLastNode(getSessionIdManager().getWorkerName());
            } else {
              //  LOG.debug("Loaded session has expired, id={}", clusterId);
                loaded = null;
            }
        }
        return loaded;
    }

    private RedisSession loadFromStore(final String clusterId, final RedisSession current) {
        List<byte[]> redisData = jedisExecutor.execute(new JedisCallback<List<byte[]>>() {
            @Override
            public List<byte[]> execute(Jedis jedis) {
                final String key = RedisSessionIdManager.REDIS_SESSION_KEY + clusterId;
                final byte[] keyByte = SafeEncoder.encode(key);
                // 当前jetty内存是否存在
                if (current == null) {
                    if (jedis.exists(keyByte)) {
                     //   LOG.info("缓存 redis 中找到session  key >>>>> " + key);
                        return jedis.hmget(keyByte, SafeEncoder.encodeMany(FIELDS));
                    } else {
                        return null;
                    }
                } else {
                    //上次保存时间
                    byte[] val = jedis.hget(keyByte, SafeEncoder.encode("lastSaved"));
                    if (val == null) {
                        //LOG.info("缓存 redis 中没有 上次保存时间 session  key >>>>> " + key);
                        return Collections.emptyList();
                    }
                    if (current.lastSaved != Converter.Long(serializer.deserialize(val))) {
                       // LOG.info("session has changed - reload  key >>>>> " + key);
                        return jedis.hmget(keyByte, SafeEncoder.encodeMany(FIELDS));
                    } else {
                      //  LOG.info("session dit not changed in cache since last save  key >>>>> " + key);
                        return null;
                    }
                }
            }
        });
        if (redisData == null) {
            // case where session has not been modified
            return current;
        }
        if (redisData.isEmpty() || redisData.get(0) == null) {
            // no session found in redis (no data)
            return null;
        }
        Map<String, Object> data = new HashMap<String, Object>();
        for (int i = 0; i < FIELDS.length; i++) {
            data.put(FIELDS[i], serializer.deserialize(redisData.get(i)));
        }
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
        // noinspection unchecked
        return new RedisSession(data, attrs == null ? new HashMap<String, Object>() : attrs);
    }

    /**
     * 保存session
     */
    @Override
    protected void storeSession(final RedisSession session) {
//        if (!session.redisMap.isEmpty()) {
            // toStore 和redis中格式保持一致

            // 为啥要要保存东西呢？
            // final Map<String, Object> tmpToStore =
            // session.redisMap.containsKey("attributes") ? session.redisMap
            // : new TreeMap<String, Object>(session.redisMap);

            final Map<String, Object> tmpToStore = new TreeMap<String, Object>(session.redisMap);
            if (session.redisMap.containsKey("attributes") && session.redisMap.get("attributes") instanceof Map) {
                // 当前保存变量 /// 应该没有实现
            } else {
                tmpToStore.put("attributes", session.getSessionAttributes());
            }

           // LOG.info("[RedisSessionManager] storeSession - Storing session id={}", session.getClusterId());
            jedisExecutor.execute(new JedisCallback<Object>() {
                @Override
                public Object execute(Jedis jedis) {
                    session.lastSaved = System.currentTimeMillis();
                    tmpToStore.put("lastSaved", "" + session.lastSaved);
                    tmpToStore.put("appId", __app_id);
                    final Map<byte[], byte[]> map = new HashMap<byte[], byte[]>();
                    for (Map.Entry<String, Object> entry : tmpToStore.entrySet()) {
                        try {
                            String key = entry.getKey();
                            Object value = entry.getValue();
                            map.put(SafeEncoder.encode(key), serializer.serialize(value));
                        } catch (Exception e) {
                            LOG.warn(e);
                        }
                    }
                    return jedis.multi(new TransactionBlock() {
                        @Override
                        public void execute() throws JedisException {
                            final String key = RedisSessionIdManager.REDIS_SESSION_KEY + session.getClusterId();
                            super.hmset(SafeEncoder.encode(key), map);
                            int ttl = session.getMaxInactiveInterval();
                            if (ttl > 0) {
                                super.expire(key, ttl);
                            }
                        }
                    });
                }
            });
            session.redisMap.clear();
//        }
    }

    @Override
    protected RedisSession newSession(HttpServletRequest request) {
        return new RedisSession(request);
    }

    @Override
    protected void deleteSession(final RedisSession session) {
        LOG.debug("[RedisSessionManager] deleteSession - Deleting from Redis session id={}", session.getClusterId());
        jedisExecutor.execute(new JedisCallback<Object>() {
            @Override
            public Object execute(Jedis jedis) {
                return jedis.del(RedisSessionIdManager.REDIS_SESSION_KEY + session.getClusterId());
            }
        });
    }

    final class RedisSession extends SessionManagerSkeleton.SessionSkeleton {

        private final Map<String, Object> redisMap = new TreeMap<String, Object>();

        private long expiryTime;
        private long lastSaved;
        private String lastNode;
        private final ThreadLocal<Boolean> firstAccess = new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return true;
            }
        };

        private RedisSession(HttpServletRequest request) {
            super(request);
            lastNode = getSessionIdManager().getWorkerName();
            long ttl = getMaxInactiveInterval();
            expiryTime = ttl <= 0 ? 0 : System.currentTimeMillis() / 1000 + ttl;
            // new session so prepare redis map accordingly
            redisMap.put("id", getClusterId());
            redisMap.put("context", getCanonicalizedContext());
            // redisMap.put("virtualHost", getVirtualHost());
            redisMap.put("created", "" + getCreationTime());
            redisMap.put("lastNode", lastNode);
            redisMap.put("lastAccessed", "" + getLastAccessedTime());
            redisMap.put("accessed", "" + getAccessed());
            redisMap.put("expiryTime", "" + expiryTime);
            redisMap.put("maxIdle", "" + ttl);
            redisMap.put("cookieSet", "" + getCookieSetTime());
            redisMap.put("attributes", "");
        }

        RedisSession(Map<String, Object> redisData, Map<String, Object> attributes) {
            // long created, long accessed, String clusterId
            super(Converter.Long(redisData.get("created")), Converter.Long(redisData.get("accessed")), Converter
                    .String(redisData.get("id")));
            lastNode = redisData.get("lastNode") == null ? "" : Converter.String(redisData.get("lastNode"));
            expiryTime = Converter.Long(redisData.get("expiryTime"));
            lastSaved = Converter.Long(redisData.get("lastSaved"));
            super.setMaxInactiveInterval(Converter.Int(redisData.get("maxIdle")));
            setCookieSetTime(Converter.Long(redisData.get("cookieSet")));
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                super.doPutOrRemove(entry.getKey(), entry.getValue());
            }
            super.access(Converter.Long(redisData.get("lastAccessed")));
        }

        public void changeLastNode(String lastNode) {
            this.lastNode = lastNode;
            redisMap.put("lastNode", lastNode);
        }

        @Override
        public void setAttribute(String name, Object value) {
            super.setAttribute(name, value);
            // redisMap.put("attributes", "");
           // LOG.info("向session中存放数据 name >>> "+ name + " value >>>> " + value);
        }

        @Override
        public void removeAttribute(String name) {
            super.removeAttribute(name);
            // redisMap.put("attributes", "");
        }

        public final Map<String, Object> getSessionAttributes() {
            Map<String, Object> attrs = new LinkedHashMap<String, Object>();
            for (String key : super.getNames()) {
                attrs.put(key, super.doGet(key));
            }
            return attrs;
        }

        @Override
        protected boolean access(long time) {
            boolean ret = super.access(time);
            firstAccess.remove();
            int ttl = getMaxInactiveInterval();
            expiryTime = ttl <= 0 ? 0 : time / 1000 + ttl;
            // prepare serialization
            redisMap.put("lastAccessed", "" + getLastAccessedTime());
            redisMap.put("accessed", "" + getAccessed());
            redisMap.put("expiryTime", "" + expiryTime);
            return ret;
        }

        @Override
        public void setMaxInactiveInterval(int secs) {
            super.setMaxInactiveInterval(secs);
            // prepare serialization
            redisMap.put("maxIdle", "" + secs);
        }

        @Override
        protected void cookieSet() {
            super.cookieSet();
            // prepare serialization
            redisMap.put("cookieSet", "" + getCookieSetTime());
        }

        @Override
        protected void complete() {
            super.complete();
            //每次完成都保存---以后要详细判断
//            if (!redisMap.isEmpty()
//                    && (redisMap.size() != 3 || !redisMap.containsKey("lastAccessed")
//                            || !redisMap.containsKey("accessed") || !redisMap.containsKey("expiryTime") || getAccessed()
//                            - lastSaved >= saveIntervalSec * 1000)) {
                try {
                    willPassivate();
                    storeSession(this);
                    didActivate();
                } catch (Exception e) {
                    LOG.warn("[RedisSessionManager] complete - Problem persisting changed session data id=" + getId(),
                            e);
                } finally {
                    redisMap.clear();
                }
//            }
        }

        public boolean requestStarted() {
            boolean first = firstAccess.get();
            if (first)
                firstAccess.set(false);
            return first;
        }
    }
}
