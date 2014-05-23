jetty-session-redis
=====
jetty-session-redis是将jetty容器中的session保存到redis中，方便做jetty集群。


Quick Examples
===

jetty使用
---
启动jetty是使用，[详细的实例](https://github.com/maczam/jetty-session-redis/blob/master/src/test/java/info/hexin/Demo.java)
```java
   RedisSessionIdManager sessionIdManager = new RedisSessionIdManager(server, jedisPool);
   RedisSessionManager redisSessionManager = new RedisSessionManager(sessionIdManager);
   sessionIdManager.setWorkerName("jvm-1");
   redisSessionManager.setAppId("aa.war");
```

TODO
====
1. 清理无用的对象