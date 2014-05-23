package info.hexin.jetty.session.redis;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.AbstractSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 
 * @author hexin
 * 
 */
public abstract class SessionIdManagerSkeleton extends AbstractSessionIdManager {

    final static Logger LOG = Log.getLogger(SessionIdManagerSkeleton.class);
    // for a session id in the whole jetty, each webapp can have different
    // sessions for the same id
    private final ConcurrentMap<String, Object> sessions = new ConcurrentHashMap<String, Object>();

    private final Server server;

    private long scavengerInterval = 10 * 60 * 1000; // 1min
    private long scavengerDelay = 30 * 60 * 1000; // 1min
    private ScheduledFuture<?> scavenger;
    private ScheduledExecutorService executorService;

    protected SessionIdManagerSkeleton(Server server) {
        this.server = server;
    }

    public final void setScavengerInterval(long scavengerInterval) {
        this.scavengerInterval = scavengerInterval;
    }

    @Override
    protected final void doStart() throws Exception {
        sessions.clear();
        if (scavenger != null) {
            scavenger.cancel(true);
            scavenger = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        if (scavengerInterval > 0) {
            executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setName("RedisSessionIdManager-ScavengerThread");
                    return t;
                }
            });
            scavenger = executorService.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    if (!sessions.isEmpty()) {
                        try {
                            final List<String> expired = scavenge(new ArrayList<String>(sessions.keySet()));
                            for (String clusterId : expired) {
                                sessions.remove(clusterId);
                            }
                            sessionManager(new SessionManagerCallback() {
                                @Override
                                public void execute(SessionManagerSkeleton sessionManager) {
                                    sessionManager.expire(expired);
                                }
                            });
                        } catch (Exception e) {
                            LOG.warn("Scavenger thread failure: " + e.getMessage(), e);
                        }
                    }
                }
            }, scavengerDelay, scavengerInterval, TimeUnit.MILLISECONDS);
        }
        super.doStart();
    }

    @Override
    protected final void doStop() throws Exception {
        sessions.clear();
        if (scavenger != null) {
            scavenger.cancel(true);
            scavenger = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        super.doStop();
    }

    @Override
    public final String getClusterId(String nodeId) {
        int dot = nodeId.lastIndexOf('.');
        return dot > 0 ? nodeId.substring(0, dot) : nodeId;
    }

    @Override
    public final String getNodeId(String clusterId, HttpServletRequest request) {
        if (_workerName != null) {
            return clusterId + '.' + _workerName;
        }
        return clusterId;
    }

    /**
     * 删除实现。。 需要调用redis查看
     */
    @Override
    public final boolean idInUse(String id) {
        String cid = getClusterId(id);
        boolean is = id != null && (sessions.containsKey(cid) || hasClusterId(cid));
        //LOG.debug(" session is id idInUse >>>> {}", is);
        return is;
    }

    @Override
    public final void addSession(HttpSession session) {
        //LOG.debug(" addSession session is id >>>> {}", session.getId());
        String clusterId = getClusterId(session.getId());
        storeClusterId(clusterId);
        sessions.putIfAbsent(clusterId, Void.class);
    }

    @Override
    public final void removeSession(HttpSession session) {
        //LOG.debug(" removeSession session is id >>>> {}", session.getId());
        String clusterId = getClusterId(session.getId());
        if (sessions.containsKey(clusterId)) {
            sessions.remove(clusterId);
            deleteClusterId(clusterId);
        }
    }

    @Override
    public final void invalidateAll(final String clusterId) {
        //LOG.debug(" invalidateAll session is id >>>> {}", clusterId);
        if (sessions.containsKey(clusterId)) {
            sessions.remove(clusterId);
            deleteClusterId(clusterId);
            sessionManager(new SessionManagerCallback() {
                @Override
                public void execute(SessionManagerSkeleton sessionManager) {
                    sessionManager.invalidateSession(clusterId);
                }
            });
        }
    }

    protected abstract void deleteClusterId(String clusterId);

    protected abstract void storeClusterId(String clusterId);

    protected abstract boolean hasClusterId(String clusterId);

    protected abstract List<String> scavenge(List<String> clusterIds);

    private void sessionManager(SessionManagerCallback callback) {
        Handler[] contexts = server.getChildHandlersByClass(ContextHandler.class);
        for (int i = 0; contexts != null && i < contexts.length; i++) {
            SessionHandler sessionHandler = ((ContextHandler) contexts[i]).getChildHandlerByClass(SessionHandler.class);
            if (sessionHandler != null) {
                SessionManager manager = sessionHandler.getSessionManager();
                if (manager != null && manager instanceof SessionManagerSkeleton)
                    callback.execute((SessionManagerSkeleton) manager);
            }
        }
    }

    /**
     * @author Mathieu Carbou (mathieu.carbou@gmail.com)
     */
    private static interface SessionManagerCallback {
        void execute(SessionManagerSkeleton sessionManager);
    }
}
