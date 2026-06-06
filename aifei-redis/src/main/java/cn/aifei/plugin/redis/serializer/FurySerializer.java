/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.aifei.plugin.redis.serializer;

import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.Language;
import redis.clients.jedis.util.SafeEncoder;

/**
 * FurySerializer
 */
public class FurySerializer implements Serializer {

    public static final Serializer me = new FurySerializer();

    private static ThreadSafeFury fury;

    static {
        fury = Fury.builder()
                .withLanguage(Language.JAVA)
                .withRefTracking(true)
                .requireClassRegistration(false)
                .withNumberCompressed(false)
                // .withAsyncCompilation(true)
                .buildThreadSafeFury();
                // .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
                // .buildThreadSafeFuryPool(8, 32, 5, TimeUnit.MINUTES);
                // .buildThreadLocalFury();
    }

    @Override
    public byte[] keyToBytes(String key) {
        return SafeEncoder.encode(key);
    }

    @Override
    public String keyFromBytes(byte[] bytes) {
        return SafeEncoder.encode(bytes);
    }

    @Override
    public byte[] fieldToBytes(Object field) {
        return SafeEncoder.encode(field.toString());
    }

    @Override
    public Object fieldFromBytes(byte[] bytes) {
        return SafeEncoder.encode(bytes);
    }

    @Override
    public byte[] valueToBytes(Object value) {
        return fury.serialize(value);
    }

    @Override
    public Object valueFromBytes(byte[] bytes) {
        return bytes != null ? fury.deserialize(bytes) : null;
    }
}

