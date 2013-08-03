/*
 Copyright 2013 Yen Pai ypai@reign.io

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package io.reign.conf;

import io.reign.AbstractService;
import io.reign.DataSerializer;
import io.reign.JsonDataSerializer;
import io.reign.ObservableService;
import io.reign.PathType;
import io.reign.ServiceObserverManager;
import io.reign.ServiceObserverWrapper;
import io.reign.mesg.RequestMessage;
import io.reign.mesg.ResponseMessage;
import io.reign.mesg.SimpleResponseMessage;
import io.reign.util.PathCacheEntry;
import io.reign.util.ZkClientUtil;

import java.util.Arrays;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote/centralized configuration service.
 * 
 * @author ypai
 * 
 */
public class ConfService extends AbstractService implements ObservableService {

    private static final Logger logger = LoggerFactory.getLogger(ConfService.class);

    private final ZkClientUtil zkUtil = new ZkClientUtil();

    private final ServiceObserverManager<ConfObserverWrapper> observerManager = new ServiceObserverManager<ConfObserverWrapper>();

    public <T> void observe(String clusterId, String relativeConfPath, SimpleConfObserver<T> observer) {
        observe(PathType.CONF, clusterId, relativeConfPath, observer);
    }

    <T> void observe(PathType pathType, String clusterId, String relativeConfPath, SimpleConfObserver<T> observer) {

        String absolutePath = getPathScheme().getAbsolutePath(pathType,
                getPathScheme().joinPaths(clusterId, relativeConfPath));

        DataSerializer confSerializer = null;
        if (relativeConfPath.endsWith(".properties")) {
            confSerializer = new ConfPropertiesSerializer<ConfProperties>(false);
        } else if (relativeConfPath.endsWith(".json") || relativeConfPath.endsWith(".js")) {
            confSerializer = new JsonDataSerializer();
        } else {
            throw new IllegalArgumentException("Could not derive serializer from given information:  path="
                    + relativeConfPath);
        }

        Object result = getConfValue(absolutePath, confSerializer, false);

        this.observerManager.put(absolutePath, new ConfObserverWrapper(absolutePath, observer, confSerializer, result));
    }

    /**
     * Picks serializer based on path "file extension".
     * 
     * @param <T>
     * @param clusterId
     * @param relativeConfPath
     * @return
     */
    public <T> T getConf(String clusterId, String relativeConfPath) {
        DataSerializer confSerializer = null;
        if (relativeConfPath.endsWith(".properties")) {
            confSerializer = new ConfPropertiesSerializer<ConfProperties>(false);
        } else if (relativeConfPath.endsWith(".json") || relativeConfPath.endsWith(".js")) {
            confSerializer = new JsonDataSerializer<T>();
        } else {
            throw new IllegalArgumentException("Could not derive serializer from given information:  path="
                    + relativeConfPath);
        }
        return (T) getConfAbsolutePath(PathType.CONF, clusterId, relativeConfPath, confSerializer, null, true);

    }

    /**
     * 
     * @param relativePath
     * @param confSerializer
     * @return
     */
    public <T> T getConf(String clusterId, String relativeConfPath, DataSerializer<T> confSerializer) {
        return getConfAbsolutePath(PathType.CONF, clusterId, relativeConfPath, confSerializer, null, true);

    }

    /**
     * 
     * @param relativePath
     * @param confSerializer
     * @param observer
     * @return
     */
    public <T> T getConf(String clusterId, String relativeConfPath, DataSerializer<T> confSerializer,
            SimpleConfObserver<T> observer) {
        return getConfAbsolutePath(PathType.CONF, clusterId, relativeConfPath, confSerializer, observer, true);

    }

    /**
     * Picks serializer based on path "file extension".
     * 
     * @param <T>
     * @param clusterId
     * @param relativeConfPath
     * @param conf
     */
    public <T> void putConf(String clusterId, String relativeConfPath, T conf) {
        DataSerializer confSerializer = null;
        if (relativeConfPath.endsWith(".properties")) {
            confSerializer = new ConfPropertiesSerializer<ConfProperties>(false);
        } else if (relativeConfPath.endsWith(".json") || relativeConfPath.endsWith(".js")) {
            confSerializer = new JsonDataSerializer<T>();
        } else {
            throw new IllegalArgumentException("Could not derive serializer from given information:  path="
                    + relativeConfPath);
        }
        putConfAbsolutePath(PathType.CONF, clusterId, relativeConfPath, conf, confSerializer, getDefaultZkAclList());
    }

    /**
     * 
     * @param relativePath
     * @param conf
     * @param confSerializer
     */
    public <T> void putConf(String clusterId, String relativeConfPath, T conf, DataSerializer<T> confSerializer) {
        putConfAbsolutePath(PathType.CONF, clusterId, relativeConfPath, conf, confSerializer, getDefaultZkAclList());
    }

    /**
     * 
     * @param relativePath
     * @param conf
     * @param confSerializer
     * @param aclList
     */
    public <T> void putConf(String clusterId, String relativeConfPath, T conf, DataSerializer<T> confSerializer,
            List<ACL> aclList) {
        putConfAbsolutePath(PathType.CONF, clusterId, relativeConfPath, conf, confSerializer, aclList);
    }

    /**
     * 
     * @param relativePath
     */
    public void removeConf(String clusterId, String relativeConfPath) {
        String path = getPathScheme().getAbsolutePath(PathType.CONF,
                getPathScheme().joinPaths(clusterId, relativeConfPath));
        try {
            getZkClient().delete(path, -1);

            // set up watch for when path comes back if there are observers
            if (this.observerManager.isBeingObserved(path)) {
                getZkClient().exists(path, true);
            }

        } catch (KeeperException e) {
            if (e.code() != Code.NONODE) {
                logger.error("removeConf():  error trying to delete node:  " + e + ":  path=" + path, e);
            }
        } catch (Exception e) {
            logger.error("removeConf():  error trying to delete node:  " + e + ":  path=" + path, e);
        }
    }

    /**
     * 
     * @param <T>
     * @param pathContext
     * @param pathType
     * @param clusterId
     * @param relativeConfPath
     * @param confSerializer
     * @param observer
     * @param useCache
     * @return
     */
    <T> T getConfAbsolutePath(PathType pathType, String clusterId, String relativeConfPath,
            DataSerializer<T> confSerializer, SimpleConfObserver<T> observer, boolean useCache) {
        String absolutePath = getPathScheme().getAbsolutePath(pathType,
                getPathScheme().joinPaths(clusterId, relativeConfPath));
        return getConfAbsolutePath(absolutePath, confSerializer, observer, useCache);

    }

    /**
     * 
     * @param <T>
     * @param absolutePath
     * @param confSerializer
     * @param observer
     * @param useCache
     * @return
     */
    <T> T getConfAbsolutePath(String absolutePath, DataSerializer<T> confSerializer, SimpleConfObserver<T> observer,
            boolean useCache) {

        T result = getConfValue(absolutePath, confSerializer, useCache && observer == null);

        /** add observer if given **/
        if (observer != null) {
            this.observerManager.put(absolutePath, new ConfObserverWrapper<T>(absolutePath, observer, confSerializer,
                    result));

        }

        return result;
    }

    <T> T getConfValue(String absolutePath, DataSerializer<T> confSerializer, boolean useCache) {
        byte[] bytes = null;
        T result = null;

        try {
            PathCacheEntry pathCacheEntry = getPathCache().get(absolutePath);
            if (useCache && pathCacheEntry != null) {
                // found in cache
                bytes = pathCacheEntry.getBytes();
            } else {
                // not in cache, so load from ZK
                Stat stat = new Stat();
                bytes = getZkClient().getData(absolutePath, true, stat);

                // put in cache
                getPathCache().put(absolutePath, stat, bytes, null);
            }

            result = bytes != null ? confSerializer.deserialize(bytes) : null;

        } catch (KeeperException e) {
            if (e.code() == Code.NONODE) {
                // set up watch on that node
                try {
                    getZkClient().exists(absolutePath, true);
                } catch (Exception e1) {
                    logger.error("getConfValue():  error trying to watch node:  " + e1 + ":  path=" + absolutePath, e1);
                }

                logger.debug("getConfValue():  error trying to fetch node info:  {}:  node does not exist:  path={}",
                        e.getMessage(), absolutePath);
            } else {
                logger.error("getConfValue():  error trying to fetch node info:  " + e, e);
            }
        } catch (Exception e) {
            logger.error("getConfValue():  error trying to fetch node info:  " + e, e);
        }

        return result;
    }

    /**
     * 
     * @param <T>
     * @param pathContext
     * @param pathType
     * @param clusterId
     * @param relativeConfPath
     * @param conf
     * @param confSerializer
     * @param aclList
     */
    <T> void putConfAbsolutePath(PathType pathType, String clusterId, String relativeConfPath, T conf,
            DataSerializer<T> confSerializer, List<ACL> aclList) {
        String absolutePath = getPathScheme().getAbsolutePath(pathType,
                getPathScheme().joinPaths(clusterId, relativeConfPath));
        try {
            // write to ZK
            byte[] leafData = confSerializer.serialize(conf);
            String pathUpdated = zkUtil.updatePath(getZkClient(), getPathScheme(), absolutePath, leafData, aclList,
                    CreateMode.PERSISTENT, -1);

            logger.debug("putConfAbsolutePath():  saved configuration:  path={}", pathUpdated);
        } catch (Exception e) {
            logger.error("putConfAbsolutePath():  error while saving configuration:  " + e + ":  path=" + absolutePath,
                    e);
        }
    }

    @Override
    public void signalStateReset(Object o) {
        this.observerManager.signalStateReset(o);
    }

    @Override
    public void signalStateUnknown(Object o) {
        this.observerManager.signalStateUnknown(o);
    }

    @Override
    public boolean filterWatchedEvent(WatchedEvent event) {
        return !observerManager.isBeingObserved(event.getPath());

    }

    @Override
    public void nodeCreated(WatchedEvent event) {
        nodeDataChanged(event);
    }

    @Override
    public void nodeDataChanged(WatchedEvent event) {
        String path = event.getPath();
        ConfObserverWrapper<SimpleConfObserver> observerWrapper = observerManager.getObserverWrapperSet(path)
                .iterator().next();
        Object newValue = getConfAbsolutePath(path, observerWrapper.getDataSerializer(), null, true);
        if (newValue != null && !newValue.equals(observerWrapper.getCurrentValue())) {
            observerManager.signal(path, newValue);
        }
    }

    @Override
    public void nodeDeleted(WatchedEvent event) {
        observerManager.signal(event.getPath(), null);
    }

    @Override
    public void connected(WatchedEvent event) {
        this.signalStateReset(null);
    }

    @Override
    public void sessionExpired(WatchedEvent event) {
        this.signalStateUnknown(null);
    }

    @Override
    public void disconnected(WatchedEvent event) {
        this.signalStateUnknown(null);
    }

    @Override
    public void init() {
    }

    @Override
    public void destroy() {
    }

    /**
     * TODO: implement!
     */
    @Override
    public ResponseMessage handleMessage(RequestMessage requestMessage) {
        ResponseMessage responseMessage;
        try {
            /** preprocess request **/
            String requestBody = (String) requestMessage.getBody();
            int newlineIndex = requestBody.indexOf("\n");
            String resourceLine = requestBody;
            String confBody = null;
            if (newlineIndex != -1) {
                resourceLine = requestBody.substring(0, newlineIndex);
                confBody = requestBody.substring(newlineIndex + 1);
            }

            // get meta
            String meta = null;
            String resource = null;
            int hashLastIndex = resourceLine.lastIndexOf("#");
            if (hashLastIndex != -1) {
                meta = resourceLine.substring(hashLastIndex + 1);
                resource = resourceLine.substring(0, hashLastIndex);
            } else {
                resource = resourceLine;
            }

            // get resource; strip beginning and ending slashes "/"
            if (resource.startsWith("/")) {
                resource = resource.substring(1);
            }
            if (resource.endsWith("/")) {
                resource = resource.substring(0, resource.length() - 1);
            }

            /** get response **/
            String[] tokens = getPathScheme().tokenizePath(resource);
            responseMessage = new SimpleResponseMessage();

            String clusterId = null;
            String relativePath = null;
            if (tokens.length == 1) {
                // list configurations in cluster
                clusterId = tokens[0];

            } else if (tokens.length > 1) {
                clusterId = tokens[0];

                String[] relativePathTokens = Arrays.<String> copyOfRange(tokens, 1, tokens.length);
                relativePath = getPathScheme().joinTokens(relativePathTokens);

            }

            if (logger.isDebugEnabled()) {
                logger.debug("clusterId={}; relativePath={}; meta={}", new Object[] { clusterId, relativePath, meta });
            }

            /** decide what to do based on meta value **/
            if (meta == null) {
                // get conf or list conf(s) available
                if (clusterId != null && relativePath != null) {
                    Object conf = getConf(clusterId, relativePath);
                    if (conf != null) {
                        responseMessage.setBody(conf);
                    } else {
                        // no configuration data, so list configuration data available at path
                        String absolutePath = getPathScheme().getAbsolutePath(PathType.CONF,
                                getPathScheme().joinPaths(clusterId, relativePath));
                        List<String> childList = getZkClient().getChildren(absolutePath, false);
                        responseMessage.setBody(childList);
                    }

                } else if (clusterId != null && relativePath == null) {
                    // list configs in cluster
                    String absolutePath = getPathScheme().getAbsolutePath(PathType.CONF, clusterId);
                    List<String> childList = getZkClient().getChildren(absolutePath, false);
                    responseMessage.setBody(childList);

                } else {
                    // both clusterId and relativePath are null: just list available clusters
                    String absolutePath = getPathScheme().getAbsolutePath(PathType.CONF);
                    List<String> childList = getZkClient().getChildren(absolutePath, false);
                    responseMessage.setBody(childList);
                }

            } else if ("update".equals(meta)) {
                // edit conf

            } else if ("delete".equals(meta)) {
                // delete conf
                removeConf(clusterId, relativePath);

            } else {
                responseMessage.setComment("Invalid request (do not understand '" + meta + "'):  " + requestBody);
            }

            return responseMessage;

        } catch (Exception e) {
            logger.error("handleMessage():  " + e, e);
            return SimpleResponseMessage.DEFAULT_ERROR_RESPONSE;
        }

    }

    private static class ConfObserverWrapper<T> extends ServiceObserverWrapper<SimpleConfObserver<T>> {

        // private String path;

        private final DataSerializer<T> dataSerializer;

        private volatile T currentValue;

        public ConfObserverWrapper(String path, SimpleConfObserver<T> observer, DataSerializer<T> dataSerializer,
                T currentValue) {
            // from super class
            this.observer = observer;

            // this.path = path;
            this.dataSerializer = dataSerializer;
            this.currentValue = currentValue;
        }

        @Override
        public void signalObserver(Object o) {
            this.observer.updated((T) o, this.currentValue);

            // update current value for comparison against any future events
            // (sometimes we get a ZK event even if relevant value has not
            // changed: for example, when updating node data with the exact same
            // value)
            this.currentValue = (T) o;
        }

        // public String getPath() {
        // return path;
        // }

        public DataSerializer<T> getDataSerializer() {
            return dataSerializer;
        }

        public T getCurrentValue() {
            return currentValue;
        }

    }// private

}