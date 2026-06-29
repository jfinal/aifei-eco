/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.redis;

/**
 * 定义 Redis 缓存值的编解码方式。
 *
 * <p>该接口是 {@link RedisCache} 的实现级扩展点，只用于 {@link RedisConfig} 装配，
 * 不属于顶层 {@code Cache} 缓存抽象。实现必须是线程安全的；共享同一 Redis 的应用实例
 * 必须使用互相兼容的数据格式。</p>
 */
public interface RedisValueCodec {

    /**
     * 将非空对象序列化为非空字节数组。
     *
     * @param value 缓存值，不会为 {@code null}
     * @return 序列化后的字节数组，不能为 {@code null}
     */
    byte[] serialize(Object value);

    /**
     * 将字节数组反序列化为非空对象。
     *
     * @param bytes 此 codec 写入的字节数组，不会为 {@code null}
     * @return 反序列化后的缓存值，不能为 {@code null}
     */
    Object deserialize(byte[] bytes);
}
