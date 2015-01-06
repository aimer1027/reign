/*
 * Copyright 2013, 2014 Yen Pai ypai@kompany.org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.reign;

import io.reign.conf.ConfService;
import io.reign.conf.ConfServiceRequestBuilder;
import io.reign.lease.LeaseService;
import io.reign.lease.LeaseServiceRequestBuilder;
import io.reign.mesg.MessagingService;
import io.reign.mesg.MessagingServiceRequestBuilder;
import io.reign.metrics.MetricsService;
import io.reign.metrics.MetricsServiceRequestBuilder;
import io.reign.presence.PresenceService;
import io.reign.presence.PresenceServiceRequestBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point into framework functionality.
 * 
 * @author ypai
 * 
 */
public class Reign implements Watcher {

    private static final Logger logger = LoggerFactory.getLogger(Reign.class);

    public static final String DEFAULT_FRAMEWORK_CLUSTER_ID = "reign";
    public static final String CLIENT_SERVICE_ID = "client";

    public static final String DEFAULT_FRAMEWORK_BASE_PATH = "/reign";

    public static final List<ACL> DEFAULT_ACL_LIST = new ArrayList<ACL>();
    static {
        DEFAULT_ACL_LIST.add(new ACL(ZooDefs.Perms.ALL, new Id("world", "anyone")));
    }

    public static int DEFAULT_MESSAGING_PORT = 33033;

    private ZkClient zkClient;

    private final Map<String, ServiceWrapper> serviceMap = new ConcurrentHashMap<String, ServiceWrapper>(8, 0.9f, 2);

    private volatile ReignContext context;

    private PathScheme pathScheme;

    private volatile boolean started = false;
    private volatile boolean shutdown = false;

    /** List to ensure Watcher(s) are called in a specific order */
    private final List<Watcher> watcherList = new ArrayList<Watcher>();

    private List<ACL> defaultZkAclList = DEFAULT_ACL_LIST;

    private NodeIdProvider nodeIdProvider;

    private final ObserverManager observerManager;

    private final TestingServer zkTestServer;

    public static final LifecycleEventHandler NULL_LIFECYCLE_EVENT_HANDLER = new NullLifecycleEventHandler();

    private LifecycleEventHandler lifecycleEventHandler = NULL_LIFECYCLE_EVENT_HANDLER;

    /** executed on completion of start() */
    private Runnable startHook;

    /** executed on completion of stop() */
    private Runnable stopHook;
    
    public static ReignMaker maker() {
        return new ReignMaker();
    }

    public Reign(ZkClient zkClient, PathScheme pathScheme, NodeIdProvider nodeIdProvider, TestingServer zkTestServer,
            LifecycleEventHandler lifecycleEventHandler) {

        this.zkClient = zkClient;

        this.pathScheme = pathScheme;

        this.nodeIdProvider = nodeIdProvider;

        observerManager = new ObserverManager(zkClient);

        this.zkTestServer = zkTestServer;

        this.lifecycleEventHandler = lifecycleEventHandler;

    }
    
    public synchronized void setStartHook(Runnable startHook) {
        if (started) {
            throw new IllegalStateException("Cannot set after framework is started!");
        }
        this.startHook = startHook;
    }

    public synchronized void setStopHook(Runnable stopHook) {
        if (started) {
            throw new IllegalStateException("Cannot set after framework is started!");
        }
        this.stopHook = stopHook;
    }

    public NodeIdProvider getNodeIdProvider() {
        if (!started) {
            throw new IllegalStateException("Cannot get provider before framework is started!");
        }
        return this.nodeIdProvider;
    }

    public ReignContext getContext() {
        if (!started) {
            throw new IllegalStateException("Cannot get context before framework is started!");
        }
        return this.context;
    }

    public List<ACL> getDefaultZkAclList() {
        return defaultZkAclList;
    }

    public void setDefaultZkAclList(List<ACL> defaultZkAclList) {
        if (started) {
            throw new IllegalStateException("Cannot set defaultAclList once started!");
        }
        this.defaultZkAclList = defaultZkAclList;
    }

    public void setCanonicalIdProvider(NodeIdProvider canonicalIdMaker) {
        if (started) {
            throw new IllegalStateException("Cannot set canonicalIdMaker once started!");
        }
        this.nodeIdProvider = canonicalIdMaker;
    }

    @Override
    public void process(WatchedEvent event) {
        // log if TRACE
        if (logger.isTraceEnabled()) {
            logger.trace("***** Received ZooKeeper Event:  {}",
                    ReflectionToStringBuilder.toString(event, ToStringStyle.DEFAULT_STYLE));

        }

        if (shutdown) {
            logger.warn("Already shutdown:  ignoring event:  type={}; path={}", event.getType(), event.getPath());
            return;
        }

        for (Watcher watcher : watcherList) {
            watcher.process(event);
        }
    }

    public ZkClient getZkClient() {
        return zkClient;
    }

    public void setZkClient(ZkClient zkClient) {
        if (started) {
            throw new IllegalStateException("Cannot set zkClient once started!");
        }
        this.zkClient = zkClient;
    }

    public PathScheme getPathScheme() {
        return pathScheme;
    }

    public void setPathScheme(PathScheme pathScheme) {
        if (started) {
            throw new IllegalStateException("Cannot set pathScheme once started!");
        }
        this.pathScheme = pathScheme;
    }

    public <T extends Service> T getService(String serviceName) {
        if (!started) {
            throw new IllegalStateException("Cannot get service before framework is started!");
        }
        return context.getService(serviceName);
    }

    public synchronized void registerServices(Map<String, Service> serviceMap) {
        throwExceptionIfNotOkayToRegister();

        for (String serviceName : serviceMap.keySet()) {
            register(serviceName, serviceMap.get(serviceName));
        }

    }

    public PresenceServiceRequestBuilder presence() {
        return context().presence();
    }

    public MessagingServiceRequestBuilder mesg() {
        return context().mesg();
    }

    public MetricsServiceRequestBuilder metrics() {
        return context().metrics();
    }

    public LeaseServiceRequestBuilder lease() {
        return context().lease();
    }

    public ConfServiceRequestBuilder conf() {
        return context().conf();
    }

    public ReignContext context() {
        if (!started) {
            throw new IllegalStateException("Cannot get context before framework is started!");
        }
        return this.context;
    }

    public synchronized void start() {
        if (started) {
            logger.debug("start():  already started...");
            return;
        }

        logger.info("START:  begin");

        /** create graceful shutdown hook **/
        logger.info("START:  add shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Reign.this.stop();
            }
        });

        /** init observer manager **/
        observerManager.init();

        /** create context object **/
        logger.info("START:  creating ReignContext...");
        final List<ACL> finalDefaultZkAclList = defaultZkAclList;
        this.context = new ReignContext() {

            @Override
            public Service getService(String serviceName) {
                if (shutdown) {
                    throw new IllegalStateException("Already shutdown:  cannot get service.");
                }
                waitForInitializationIfNecessary();

                if (serviceName == null) {
                    return serviceMap.get("null").getService();
                }

                ServiceWrapper serviceWrapper = serviceMap.get(serviceName);
                if (serviceWrapper != null) {
                    return serviceWrapper.getService();
                } else {
                    return null;
                }
            }

            @Override
            public String getNodeId() {
                return nodeIdProvider.get();
            }

            @Override
            public ZkClient getZkClient() {
                return zkClient;
            }

            @Override
            public PathScheme getPathScheme() {
                return pathScheme;
            }

            @Override
            public List<ACL> getDefaultZkAclList() {
                return finalDefaultZkAclList;
            }

            @Override
            public ObserverManager getObserverManager() {
                return observerManager;
            }

            @Override
            public PresenceServiceRequestBuilder presence() {
                return new PresenceServiceRequestBuilder((PresenceService) getService("presence"));
            }

            @Override
            public MessagingServiceRequestBuilder mesg() {
                return new MessagingServiceRequestBuilder((MessagingService) getService("mesg"));
            }

            @Override
            public MetricsServiceRequestBuilder metrics() {
                return new MetricsServiceRequestBuilder((MetricsService) getService("metrics"));
            }

            @Override
            public LeaseServiceRequestBuilder lease() {
                return new LeaseServiceRequestBuilder((LeaseService) getService("lease"));
            }

            @Override
            public ConfServiceRequestBuilder conf() {
                return new ConfServiceRequestBuilder((ConfService) getService("conf"));
            }

        };

        /** init services **/
        for (String serviceName : serviceMap.keySet()) {
            logger.info("START:  initializing:  serviceName={}", serviceName);

            Service service = serviceMap.get(serviceName).getService();
            // service.setPathScheme(pathScheme);
            // service.setZkClient(zkClient);
            service.setObserverManager(observerManager);
            service.setContext(context);
            // service.setDefaultZkAclList(defaultZkAclList);
            service.init();

            // add to zkClient's list of watchers if Watcher interface is
            // implemented
            if (service instanceof Watcher) {
                logger.info("START:  adding as ZooKeeper watcher:  serviceName={}", serviceName);
                watcherList.add((Watcher) service);
            }
        }

        /** watcher set-up **/
        logger.info("START:  registering watchers");
        // register self as watcher
        this.zkClient.register(this);

        started = true;

        /** notify any waiters **/
        logger.info("START:  notifying all waiters");
        this.notifyAll();

        logger.info("START:  DONE");

        /** run start hook **/
        logger.info("START:  invoking lifecycleEventHandler:  {}", lifecycleEventHandler.getClass().getName());
        lifecycleEventHandler.onStart(getContext());

        /** announce as a Reign Server: must be done after all other start-up tasks are complete **/
        PresenceService presenceService = context.getService("presence");
        if (presenceService != null) {
            logger.info("START:  announcing server availability...");
            presenceService.announce(pathScheme.getFrameworkClusterId(), "server", true);
        } else {
            logger.warn("START:  did not announce node availability:  (presenceService==null)={}",
                    presenceService == null);
        }
        
        // run start hook
        if (this.startHook != null) {
            startHook.run();
        }
    }

    public synchronized void stop() {
        if (shutdown) {
            logger.debug("stop():  already stopped...");
            return;
        }
        shutdown = true;

        logger.info("STOP:  begin");

        /** run stop hook **/
        logger.info("STOP:  invoking lifecycleEventHandler:  {}", lifecycleEventHandler.getClass().getName());
        lifecycleEventHandler.onStop(getContext());

        /** clean up services **/
        logger.info("STOP:  cleaning up services");
        for (ServiceWrapper serviceWrapper : serviceMap.values()) {
            serviceWrapper.getService().destroy();
        }

        /** observer manager **/
        logger.info("STOP:  stopping observer manager");
        observerManager.destroy();

        /** clean up zk client **/
        logger.info("STOP:  closing Zookeeper client");
        this.zkClient.close();

        /** shutdown test zk server, if there **/
        if (this.zkTestServer != null) {
            logger.info("STOP:  stopping test Zookeeper server");
            try {
                this.zkTestServer.stop();
            } catch (IOException e) {
                logger.error("STOP:  error shutting down test ZooKeeper:  " + e, e);
            }
        }

        logger.info("STOP:  DONE");
        
        // run stop hook
        if (this.stopHook != null) {
            stopHook.run();
        }
    }

    void register(String serviceName, Service service) {
        throwExceptionIfNotOkayToRegister();

        logger.info("Registering service:  serviceName={}", serviceName);

        // check that we don't have duplicate services
        if (serviceMap.put(serviceName, new ServiceWrapper(service)) != null) {
            throw new IllegalStateException("An existing service already exists under the same name:  serviceName="
                    + serviceName);
        }

    }

    private void throwExceptionIfNotOkayToRegister() {
        if (started) {
            throw new IllegalStateException("Cannot register services once started!");
        }
        if (zkClient == null) {
            throw new IllegalStateException("Cannot register services before zkClient is initialized!");
        }
    }

    private void waitForInitializationIfNecessary() {
        if (!started) {
            try {
                while (!started) {
                    logger.info("Waiting for notification of start() completion...");
                    synchronized (this) {
                        this.wait(1000);
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for start:  " + e, e);
            }
            if (!started) {
                throw new IllegalStateException("Unable to start:  check environment and ZK settings...");
            }
            logger.info("Received notification of start() completion");
        }// if
    }

    /**
     * Convenience wrapper providing methods for interpreting service metadata.
     * 
     * @author ypai
     * 
     */
    private static class ServiceWrapper {
        private final Service service;

        public ServiceWrapper(Service service) {
            this.service = service;
        }

        public Service getService() {
            return service;
        }

    }

}
