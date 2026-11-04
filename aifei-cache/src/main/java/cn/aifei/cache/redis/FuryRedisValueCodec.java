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

package cn.aifei.cache.redis;

import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.Language;

/**
 * 使用 Fury 序列化和反序列化 Redis 缓存值。
 */
final class FuryRedisValueCodec implements RedisValueCodec {

    private static final ThreadSafeFury FURY = Fury.builder()
            .withLanguage(Language.JAVA)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .suppressClassRegistrationWarnings(true)
            .buildThreadSafeFury();

    /**
     * 将对象序列化为字节数组。
     */
    @Override
    public byte[] serialize(Object value) {
        return FURY.serialize(value);
    }

    /**
     * 将字节数组反序列化为对象。
     */
    @Override
    public Object deserialize(byte[] bytes) {
        return FURY.deserialize(bytes);
    }
}
