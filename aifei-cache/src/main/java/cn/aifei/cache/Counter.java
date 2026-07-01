/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache;

import java.time.Duration;

/**
 * 本地计数和分布式计数共用接口。
 *
 * <p>计数名称和键区分大小写且不能为空白，均可用冒号分级。
 * TTL 精度为毫秒，因此有效期不能小于一毫秒。</p>
 *
 * <p>普通读取的计数名称或键非法时返回 {@code null}，删除的计数名称或键非法时不执行任何操作。
 * 其他调用参数校验失败时抛出 {@link IllegalArgumentException}。
 * 底层计数、连接或标准库运行时异常按原始类型传播。</p>
 *
 * <p>{@link #increase(String, String, long, Duration)} 和
 * {@link #decrease(String, String, long, Duration)} 用于固定窗口计数，命中时不重置 TTL。
 * {@link #increaseAndRefreshTtl(String, String, long, Duration)} 和
 * {@link #decreaseAndRefreshTtl(String, String, long, Duration)} 用于闲置过期计数，命中时会刷新 TTL。</p>
 */
public interface Counter {

    /**
     * 读取计数值。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @return 当前计数值；不存在、已过期或参数非法时返回 {@code null}
     */
    Long get(String counterName, String key);

    /**
     * 增加计数值并返回更新后的值。
     *
     * <p>计数项不存在或已过期时按 {@code 0} 处理，并使用指定 TTL 创建计数项。
     * 计数项存在且未过期时只更新计数值，不重置原 TTL。</p>
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次增加的步长，必须大于零
     * @param ttl 创建新计数项时使用的有效期，精度为毫秒
     * @return 更新后的计数值
     */
    long increase(String counterName, String key, long step, Duration ttl);

    /**
     * 使用秒为单位，增加计数值并返回更新后的值。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次增加的步长，必须大于零
     * @param ttlSeconds 创建新计数项时使用的有效秒数
     * @return 更新后的计数值
     */
    default long increase(String counterName, String key, long step, int ttlSeconds) {
        return increase(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 增加计数值，刷新 TTL，并返回更新后的值。
     *
     * <p>计数项不存在或已过期时按 {@code 0} 处理，并使用指定 TTL 创建计数项。
     * 计数项存在且未过期时，在更新计数值的同时将剩余有效期重置为指定 TTL。</p>
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次增加的步长，必须大于零
     * @param ttl 更新后使用的有效期，精度为毫秒
     * @return 更新后的计数值
     */
    long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);

    /**
     * 使用秒为单位，增加计数值，刷新 TTL，并返回更新后的值。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次增加的步长，必须大于零
     * @param ttlSeconds 更新后使用的有效秒数
     * @return 更新后的计数值
     */
    default long increaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds) {
        return increaseAndRefreshTtl(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 减少计数值并返回更新后的值。
     *
     * <p>计数项不存在或已过期时按 {@code 0} 处理，并使用指定 TTL 创建计数项。
     * 计数项存在且未过期时只更新计数值，不重置原 TTL。</p>
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次减少的步长，必须大于零
     * @param ttl 创建新计数项时使用的有效期，精度为毫秒
     * @return 更新后的计数值
     */
    long decrease(String counterName, String key, long step, Duration ttl);

    /**
     * 使用秒为单位，减少计数值并返回更新后的值。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次减少的步长，必须大于零
     * @param ttlSeconds 创建新计数项时使用的有效秒数
     * @return 更新后的计数值
     */
    default long decrease(String counterName, String key, long step, int ttlSeconds) {
        return decrease(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 减少计数值，刷新 TTL，并返回更新后的值。
     *
     * <p>计数项不存在或已过期时按 {@code 0} 处理，并使用指定 TTL 创建计数项。
     * 计数项存在且未过期时，在更新计数值的同时将剩余有效期重置为指定 TTL。</p>
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次减少的步长，必须大于零
     * @param ttl 更新后使用的有效期，精度为毫秒
     * @return 更新后的计数值
     */
    long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);

    /**
     * 使用秒为单位，减少计数值，刷新 TTL，并返回更新后的值。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 本次减少的步长，必须大于零
     * @param ttlSeconds 更新后使用的有效秒数
     * @return 更新后的计数值
     */
    default long decreaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds) {
        return decreaseAndRefreshTtl(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 删除指定计数项；计数项不存在或参数非法时不执行任何操作。
     *
     * @param counterName 计数名称
     * @param key 计数键
     */
    void remove(String counterName, String key);
}
