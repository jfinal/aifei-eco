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
        return !StrUtil.isBlank(cacheName) && !StrUtil.isBlank(key) && key.indexOf(':') == -1;
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
     * 校验缓存键非空且不包含冒号。
     */
    public static String requireKey(String key) {
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("key can not be blank");
        }
        if (key.indexOf(':') != -1) {
            throw new IllegalArgumentException("key can not contain ':'");
        }
        return key;
    }

    /**
     * 校验有效期并返回毫秒值。
     */
    public static long requireTtl(Duration ttl) {
        if (ttl == null) {
            throw new IllegalArgumentException("ttl can not be null");
        }
        long ttlMillis = ttl.toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttl must be at least one millisecond");
        }
        return ttlMillis;
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
