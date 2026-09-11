/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.internal;

import cn.aifei.util.StrUtil;
import java.time.Duration;

/**
 * 提供缓存接口共用的参数校验。
 */
public final class CacheValidator {

    /**
     * 禁止创建工具类实例。
     */
    private CacheValidator() {
    }

    /**
     * 判断缓存名称与缓存键是否有效。
     */
    public static boolean isValidCacheNameAndKey(String cacheName, String key) {
        return !StrUtil.isBlank(cacheName) && !StrUtil.isBlank(key);
    }

    /**
     * 判断计数名称与计数键是否有效。
     */
    public static boolean isValidCounterNameAndKey(String counterName, String key) {
        return !StrUtil.isBlank(counterName) && !StrUtil.isBlank(key);
    }

    /**
     * 校验缓存名称非空白。
     */
    public static String requireCacheName(String cacheName) {
        if (StrUtil.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName can not be blank");
        }
        return cacheName;
    }

    /**
     * 校验计数名称非空白。
     */
    public static String requireCounterName(String counterName) {
        if (StrUtil.isBlank(counterName)) {
            throw new IllegalArgumentException("counterName can not be blank");
        }
        return counterName;
    }

    /**
     * 校验缓存键非空白。
     */
    public static String requireKey(String key) {
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("key can not be blank");
        }
        return key;
    }

    /**
     * 校验有效期范围并返回毫秒值。
     */
    public static long requireTtl(Duration ttl) {
        if (ttl == null) {
            throw new IllegalArgumentException("ttl can not be null");
        }
        long seconds = ttl.getSeconds();
        int nanos = ttl.getNano();
        if (seconds < 0 || (seconds == 0 && nanos < 1_000_000)) {
            throw new IllegalArgumentException("ttl must be at least one millisecond");
        }
        if (seconds > Integer.MAX_VALUE || (seconds == Integer.MAX_VALUE && nanos > 0)) {
            throw new IllegalArgumentException("ttl must not exceed " + Integer.MAX_VALUE + " seconds");
        }
        // 秒数已限制在非负 int 范围内，转换为毫秒不会溢出。
        return seconds * 1000L + nanos / 1_000_000;
    }

    /**
     * 校验计数步长大于零。
     */
    public static long requireCounterStep(long step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be greater than zero");
        }
        return step;
    }

    /**
     * 校验缓存值不为 {@code null}。
     */
    public static void requireValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value can not be null");
        }
    }
}
