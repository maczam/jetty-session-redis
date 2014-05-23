package info.hexin;

import info.hexin.jetty.session.redis.RedisSessionIdManager;
import info.hexin.jetty.session.redis.RedisSessionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Created by hexin on 2013-05-22
 */
public class Demo {
    public static void main(String[] args) throws Exception {
        Server server = new Server();
        QueuedThreadPool pool = new QueuedThreadPool();
        pool.setMinThreads(10);
        pool.setMaxThreads(200);
        server.setThreadPool(pool);

        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setAcceptQueueSize(128);
        connector.setResolveNames(false);
        connector.setUseDirectBuffers(false);
        connector.setSoLingerTime(0);
        connector.setHost("0.0.0.0");
        connector.setPort(80);
        server.addConnector(connector);
        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        context.setWar("/work/aa.war");


        //redis 连接池
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxActive(20);
        config.setMaxIdle(5);
        config.setMaxWait(1000l);
        config.setTestOnReturn(false);
        config.setTestOnBorrow(false);

        String redisAddress = "192.168.0.100";

        //可能不是默认端口
        JedisPool jedisPool = null;
        if (null != redisAddress && !redisAddress.equals("")) {
            if (redisAddress.contains(":")) {
                String[] ss = redisAddress.split(":");
                jedisPool = new JedisPool(config, ss[0], Integer.parseInt(ss[1]));
            } else {
                jedisPool = new JedisPool(config, redisAddress);
            }
        } else {
            throw new RuntimeException("redisAddress is blank ,,");
        }

        //创建  redisSessionManager
        RedisSessionIdManager sessionIdManager = new RedisSessionIdManager(server, jedisPool);
        RedisSessionManager redisSessionManager = new RedisSessionManager(sessionIdManager);
        sessionIdManager.setWorkerName("jvm-1");
        redisSessionManager.setAppId("aa.war");


        SessionHandler sessionHandler = new SessionHandler(redisSessionManager);
        context.setSessionHandler(sessionHandler);
        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.addHandler(context);
        server.setHandler(handlerCollection);

        server.start();
    }
}
