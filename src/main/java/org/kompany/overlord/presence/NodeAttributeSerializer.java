package org.kompany.overlord.presence;

import java.util.Map;

/**
 * Used to serialize NodeInfo. Should be re-usable.
 * 
 * @author ypai
 * 
 */
public interface NodeAttributeSerializer {

    public byte[] serialize(Map<String, Object> map) throws Throwable;

    public Map<String, Object> deserialize(byte[] bytes) throws Throwable;
}