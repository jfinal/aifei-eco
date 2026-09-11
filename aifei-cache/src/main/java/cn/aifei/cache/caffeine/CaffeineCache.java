/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.caffeine;

import cn.aifei.cache.Cache;
import cn.aifei.cache.Counter;
import cn.aifei.cache.internal.CacheValidator;
import cn.aifei.cache.internal.CounterFactory;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的进程内缓存实现。
 */
public class CaffeineCache implements Cache, CounterFactory {

    public static final long DEFAULT_MAXIMUM_SIZE = 10_000L;

    private final com.github.benmanes.caffeine.cache.Cache<CaffeineCacheKey, Object> cache;
    private final Policy.VarExpiration<CaffeineCacheKey, Object> expiration;
    private final CaffeineLocks locks = new CaffeineLocks();
    private final long maximumSize;
    private final Ticker ticker;

    /**
     * 使用默认容量创建缓存。
     */
    public CaffeineCache() {
        this(DEFAULT_MAXIMUM_SIZE);
    }

    /**
     * 使用指定容量创建缓存。
     *
     * @param maximumSize 最大缓存项数量
     */
    public CaffeineCache(long maximumSize) {
        this(maximumSize, Ticker.systemTicker());
    }

    /**
     * 使用指定容量和时钟创建缓存。
     */
    CaffeineCache(long maximumSize, Ticker ticker) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be greater than zero");
        }
        this.maximumSize = maximumSize;
        this.ticker = Objects.requireNonNull(ticker, "ticker can not be null");

        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(this.ticker)
                .expireAfter(CaffeineExpiry.INSTANCE)
                .build();

        this.expiration = cache.policy()
                .expireVariably()
                .orElseThrow(() -> new IllegalStateException("Variable expiration is not available"));
    }

    /**
     * 创建使用相同 Caffeine 配置的计数器。
     */
    @Override
    public Counter createCounter() {
        return new CaffeineCounter(maximumSize, ticker);
    }

    /**
     * 从 Caffeine 读取缓存值。
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return null;
        }
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            return (T) cache.getIfPresent(cacheKey);
        }
    }

    /**
     * 判断 Caffeine 中是否存在指定缓存项。
     */
    @Override
    public boolean exists(String cacheName, String key) {
        return get(cacheName, key) != null;
    }

    /**
     * 向 Caffeine 写入带有效期的缓存值。
     */
    @Override
    public void put(String cacheName, String key, Object value, Duration ttl) {
        CacheValidator.requireCacheName(cacheName);
        CacheValidator.requireKey(key);
        CacheValidator.requireValue(value);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            expiration.put(cacheKey, value, ttlMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 当 Caffeine 中不存在指定缓存项时写入缓存值。
     */
    @Override
    public boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
        CacheValidator.requireCacheName(cacheName);
        CacheValidator.requireKey(key);
        CacheValidator.requireValue(value);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            return expiration.putIfAbsent(cacheKey, value, ttlMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 重设 Caffeine 中已有缓存项的有效期。
     */
    @Override
    public boolean expire(String cacheName, String key, Duration ttl) {
        CacheValidator.requireCacheName(cacheName);
        CacheValidator.requireKey(key);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            // 检查与续期共用锁，其他调用不会在两者之间观察到条目过期。
            OptionalLong existingTtl = expiration.getExpiresAfter(cacheKey, TimeUnit.NANOSECONDS);
            if (!existingTtl.isPresent()) {
                return false;
            }
            expiration.setExpiresAfter(cacheKey, ttlMillis, TimeUnit.MILLISECONDS);
            return true;
        }
    }

    /**
     * 从 Caffeine 删除指定缓存项。
     */
    @Override
    public void remove(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return;
        }
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            cache.invalidate(cacheKey);
        }
    }

    /**
     * 清空指定名称下的缓存项。
     */
    @Override
    public void clear(String cacheName) {
        CacheValidator.requireCacheName(cacheName);
        List<CaffeineCacheKey> keys = new ArrayList<>();
        for (CaffeineCacheKey key : cache.asMap().keySet()) {
            if (key.belongsTo(cacheName)) {
                keys.add(key);
            }
        }
        for (CaffeineCacheKey key : keys) {
            synchronized (locks.forKey(key)) {
                cache.invalidate(key);
            }
        }
    }
}
