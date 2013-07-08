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
public class ShortSerializer implements DataSerializer<Short> {
    @Override
    public synchronized byte[] serialize(Short data) throws Exception {
        byte[] bytes = new byte[2];
        ByteBuffer.wrap(bytes).putShort(data);
        return bytes;
    }

    @Override
    public synchronized Short deserialize(byte[] bytes) throws Exception {
        return ByteBuffer.wrap(bytes).getShort();

    }
}