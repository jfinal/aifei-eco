/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
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
