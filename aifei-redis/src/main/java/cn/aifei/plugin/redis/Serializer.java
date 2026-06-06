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

package cn.aifei.plugin.redis;

import cn.aifei.plugin.redis.serializer.FstSerializer;

/**
 * Serializer 用于 Redis.call(...)、Redis.use().call(...) 对数据进行序列化与反序列化
 */
public class Serializer {
    
    /*
     * 与 RedisPlugin.setSerializer(...) 同步持有序列化策略类
     */
    static cn.aifei.plugin.redis.serializer.Serializer serializer = FstSerializer.me;
    
    /**
     * 序列化
     */
    public static byte[] to(Object value) {
        try {
            return serializer.valueToBytes(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 反序列化
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> T from(byte[] bytes) {
        try {
            return (T) serializer.valueFromBytes(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}




