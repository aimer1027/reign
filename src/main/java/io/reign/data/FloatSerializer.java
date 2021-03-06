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

import java.nio.ByteBuffer;

/**
 * 
 * @author ypai
 * 
 */
public class FloatSerializer implements DataSerializer<Float> {
    @Override
    public byte[] serialize(Float data) throws RuntimeException {
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).putFloat(data);
        return bytes;
    }

    @Override
    public Float deserialize(byte[] bytes) throws RuntimeException {
        return ByteBuffer.wrap(bytes).getFloat();

    }
}
