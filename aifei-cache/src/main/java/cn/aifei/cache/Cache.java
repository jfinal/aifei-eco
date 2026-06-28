/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache;

import cn.aifei.cache.internal.CacheValidator;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 本地缓存和分布式缓存共用接口。
 *
 * <p>缓存名称和键区分大小写且不能为空白，缓存名称可用冒号分级，键不能包含冒号，缓存值不能为 {@code null}。
 * TTL 精度为毫秒，因此有效期不能小于一毫秒。</p>
 *
 * <p>普通读取的缓存名称或键非法时返回 {@code null}，存在性判断的缓存名称或键非法时返回 {@code false}，
 * 删除的缓存名称或键非法时不执行任何操作。
 * 其他调用参数校验失败时抛出 {@link IllegalArgumentException}。加载器自身异常会原样向外抛出。
 * 底层缓存、连接、序列化或标准库运行时异常按原始类型传播。</p>
 */
public interface Cache {

    /**
     * 读取缓存值。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param <T> 期望的值类型
     * @return 缓存值；不存在、已过期或参数非法时返回 {@code null}
     */
    <T> T get(String cacheName, String key);

    /**
     * 读取缓存；未命中时加载并写入缓存。
     *
     * <p>该方法会先校验缓存名称、键和 TTL。参数非法时抛出 {@link IllegalArgumentException}，
     * 且不会调用 {@code loader}。该方法不防止缓存击穿，并发未命中时可能多次调用 {@code loader}。</p>
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param ttl 加载值的有效期，精度为毫秒
     * @param loader 缓存未命中时调用的加载器
     * @param <T> 期望的值类型
     * @return 缓存值或加载值；加载器返回 {@code null} 时也返回 {@code null}
     */
    default <T> T get(String cacheName, String key, Duration ttl, Supplier<T> loader) {
        if (loader == null) {
            throw new IllegalArgumentException("loader can not be null");
        }
        CacheValidator.requireCacheName(cacheName);
        CacheValidator.requireKey(key);
        CacheValidator.requireTtl(ttl);

        T value = get(cacheName, key);
        if (value != null) {
            return value;
        }

        value = loader.get();
        if (value != null) {
            put(cacheName, key, value, ttl);
        }
        return value;
    }

    /**
     * 使用秒为单位读取并加载缓存。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param ttlSeconds 加载值的有效秒数
     * @param loader 缓存未命中时调用的加载器
     * @param <T> 期望的值类型
     * @return 缓存值或加载值；加载器返回 {@code null} 时也返回 {@code null}
     */
    default <T> T get(String cacheName, String key, int ttlSeconds, Supplier<T> loader) {
        return get(cacheName, key, Duration.ofSeconds(ttlSeconds), loader);
    }

    /**
     * 判断缓存项是否存在且未过期。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @return 缓存项存在且未过期时返回 {@code true}；不存在、已过期或参数非法时返回 {@code false}
     */
    default boolean exists(String cacheName, String key) {
        return get(cacheName, key) != null;
    }

    /**
     * 写入带有效期的非空缓存值。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 有效期，精度为毫秒
     */
    void put(String cacheName, String key, Object value, Duration ttl);

    /**
     * 使用秒为单位写入非空缓存值。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 有效秒数
     */
    default void put(String cacheName, String key, Object value, int ttlSeconds) {
        put(cacheName, key, value, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 当缓存项不存在或已过期时写入带有效期的非空缓存值。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 有效期，精度为毫秒
     * @return 成功写入时返回 {@code true}；缓存项已存在且未过期时返回 {@code false}
     */
    boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl);

    /**
     * 使用秒为单位，在缓存项不存在或已过期时写入非空缓存值。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 有效秒数
     * @return 成功写入时返回 {@code true}；缓存项已存在且未过期时返回 {@code false}
     */
    default boolean putIfAbsent(String cacheName, String key, Object value, int ttlSeconds) {
        return putIfAbsent(cacheName, key, value, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 重设已有缓存项的剩余有效期。
     *
     * <p>缓存项存在且未过期时只重设 TTL，不读取、不修改缓存值，并返回 {@code true}。
     * 缓存项不存在或已过期时返回 {@code false}。</p>
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param ttl 新的剩余有效期，精度为毫秒
     * @return 成功重设有效期时返回 {@code true}；缓存项不存在或已过期时返回 {@code false}
     */
    boolean expire(String cacheName, String key, Duration ttl);

    /**
     * 使用秒为单位，重设已有缓存项的剩余有效期。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param ttlSeconds 新的剩余有效秒数
     * @return 成功重设有效期时返回 {@code true}；缓存项不存在或已过期时返回 {@code false}
     */
    default boolean expire(String cacheName, String key, int ttlSeconds) {
        return expire(cacheName, key, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 删除指定缓存项；缓存项不存在或参数非法时不执行任何操作。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     */
    void remove(String cacheName, String key);

    /**
     * 清空指定名称及其下级名称的缓存项。
     *
     * <p>该操作与并发写入之间不保证原子性。</p>
     *
     * @param cacheName 缓存名称
     */
    void clear(String cacheName);
}
