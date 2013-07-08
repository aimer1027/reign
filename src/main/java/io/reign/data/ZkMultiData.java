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

package io.reign.data;

import io.reign.DataSerializer;
import io.reign.PathScheme;
import io.reign.ReignContext;
import io.reign.coord.DistributedReadWriteLock;

import java.util.List;
import java.util.Map;

import org.apache.zookeeper.data.ACL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author ypai
 * 
 * @param <V>
 */
public class ZkMultiData<V> implements MultiData<V> {

    private static final Logger logger = LoggerFactory.getLogger(ZkMultiData.class);

    private final DistributedReadWriteLock readWriteLock;

    private final String absoluteBasePath;

    private final PathScheme pathScheme;

    private final List<ACL> aclList;

    private final ZkClientMultiDataUtil zkClientMultiDataUtil;

    /**
     * 
     * @param typeClass
     * @param absoluteBasePath
     * @param readWriteLock
     *            can be null if inter-process safety is not desired
     * @param aclList
     * @param dataSerializerMap
     * @param context
     */
    public ZkMultiData(String absoluteBasePath, DistributedReadWriteLock readWriteLock, List<ACL> aclList,
            Map<String, DataSerializer> dataSerializerMap, ReignContext context) {

        this.absoluteBasePath = absoluteBasePath;

        this.pathScheme = context.getPathScheme();

        this.aclList = aclList;
        this.readWriteLock = readWriteLock;

        this.zkClientMultiDataUtil = new ZkClientMultiDataUtil(context.getZkClient(), context.getPathScheme(), context
                .getPathCache(), dataSerializerMap);

    }

    @Override
    public synchronized void destroy() {
        if (readWriteLock != null) {
            readWriteLock.destroy();
        }
    }

    @Override
    public synchronized void set(V value) {
        set(DataValue.DEFAULT_INDEX, value);
    }

    @Override
    public synchronized void set(String index, V value) {
        zkClientMultiDataUtil.lockForWrite(readWriteLock);
        try {
            zkClientMultiDataUtil.writeData(absoluteBasePath, index, value, aclList);
        } finally {
            zkClientMultiDataUtil.unlockForWrite(readWriteLock);
        }

    }

    @Override
    public synchronized V get(Class<V> typeClass) {
        return get(DataValue.DEFAULT_INDEX, -1, typeClass);

    }

    @Override
    public synchronized V get(int ttlMillis, Class<V> typeClass) {
        return get(DataValue.DEFAULT_INDEX, ttlMillis, typeClass);

    }

    @Override
    public synchronized V get(String index, Class<V> typeClass) {
        return get(index, -1, typeClass);

    }

    @Override
    public synchronized V get(String index, int ttlMillis, Class<V> typeClass) {
        zkClientMultiDataUtil.lockForRead(readWriteLock, pathScheme.joinPaths(absoluteBasePath, index), this);
        try {
            return zkClientMultiDataUtil.readData(absoluteBasePath, index, ttlMillis, typeClass, readWriteLock == null);
        } finally {
            zkClientMultiDataUtil.unlockForRead(readWriteLock);
        }

    }

    @Override
    public synchronized List<V> getAll(Class<V> typeClass) {
        return getAll(-1, typeClass);

    }

    @Override
    public synchronized List<V> getAll(int ttlMillis, Class<V> typeClass) {
        zkClientMultiDataUtil.lockForRead(readWriteLock, absoluteBasePath, this);
        try {
            return zkClientMultiDataUtil.readAllData(absoluteBasePath, ttlMillis, typeClass, readWriteLock == null);
        } finally {
            zkClientMultiDataUtil.unlockForRead(readWriteLock);
        }

    }

    @Override
    public synchronized String remove() {
        return remove(DataValue.DEFAULT_INDEX, -1);
    }

    @Override
    public synchronized String remove(int ttlMillis) {
        return remove(DataValue.DEFAULT_INDEX, ttlMillis);
    }

    @Override
    public synchronized String remove(String index) {
        return remove(index, -1);

    }

    @Override
    public synchronized String remove(String index, int ttlMillis) {
        zkClientMultiDataUtil.lockForWrite(readWriteLock);
        try {
            return zkClientMultiDataUtil.deleteData(absoluteBasePath, index, ttlMillis, readWriteLock == null);
        } finally {
            zkClientMultiDataUtil.unlockForWrite(readWriteLock);
        }

    }

    @Override
    public synchronized List<String> removeAll() {
        return removeAll(-1);
    }

    @Override
    public synchronized List<String> removeAll(int ttlMillis) {
        zkClientMultiDataUtil.lockForWrite(readWriteLock);
        try {
            return zkClientMultiDataUtil.deleteAllData(absoluteBasePath, ttlMillis, readWriteLock == null);
        } finally {
            zkClientMultiDataUtil.unlockForWrite(readWriteLock);
        }

    }

}
