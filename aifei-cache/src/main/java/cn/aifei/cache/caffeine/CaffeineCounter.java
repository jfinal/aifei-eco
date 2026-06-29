/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.caffeine;

import cn.aifei.cache.Counter;
import cn.aifei.cache.internal.CacheValidator;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的进程内计数实现。
 */
public class CaffeineCounter implements Counter {

    public static final long DEFAULT_MAXIMUM_SIZE = 10_000L;
    private static final int COUNTER_LOCK_COUNT = 128;

    private final com.github.benmanes.caffeine.cache.Cache<CaffeineCacheKey, Long> counters;
    private final Policy.VarExpiration<CaffeineCacheKey, Long> expiration;
    private final Object[] counterLocks = createCounterLocks();

    /**
     * 使用默认容量创建计数器。
     */
    public CaffeineCounter() {
        this(DEFAULT_MAXIMUM_SIZE);
    }

    /**
     * 使用指定容量创建计数器。
     *
     * @param maximumSize 最大计数项数量
     */
    public CaffeineCounter(long maximumSize) {
        this(maximumSize, Ticker.systemTicker());
    }

    /**
     * 使用指定容量和时钟创建计数器。
     */
    CaffeineCounter(long maximumSize, Ticker ticker) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be greater than zero");
        }

        this.counters = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(Objects.requireNonNull(ticker, "ticker can not be null"))
                .expireAfter(CaffeineCounterExpiry.INSTANCE)
                .build();

        this.expiration = counters.policy()
                .expireVariably()
                .orElseThrow(() -> new IllegalStateException("Variable expiration is not available"));
    }

    /**
     * 从 Caffeine 读取计数值。
     */
    @Override
    public Long get(String counterName, String key) {
        if (!CacheValidator.isValidCounterNameAndKey(counterName, key)) {
            return null;
        }
        return counters.getIfPresent(new CaffeineCacheKey(counterName, key));
    }

    /**
     * 增加 Caffeine 中的计数值。
     */
    @Override
    public long increase(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, true, false);
    }

    /**
     * 增加 Caffeine 中的计数值，并刷新 TTL。
     */
    @Override
    public long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, true, true);
    }

    /**
     * 减少 Caffeine 中的计数值。
     */
    @Override
    public long decrease(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, false, false);
    }

    /**
     * 减少 Caffeine 中的计数值，并刷新 TTL。
     */
    @Override
    public long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, false, true);
    }

    /**
     * 从 Caffeine 删除指定计数项。
     */
    @Override
    public void remove(String counterName, String key) {
        if (!CacheValidator.isValidCounterNameAndKey(counterName, key)) {
            return;
        }
        counters.invalidate(new CaffeineCacheKey(counterName, key));
    }

    /**
     * 使用分段锁原子更新或创建计数值。
     */
    private long updateCounter(String counterName, String key, long step, Duration ttl,
                               boolean increase, boolean refreshTtl) {
        String validCounterName = CacheValidator.requireCounterName(counterName);
        String validKey = CacheValidator.requireKey(key);
        long validStep = CacheValidator.requireCounterStep(step);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        CaffeineCacheKey counterKey = new CaffeineCacheKey(validCounterName, validKey);

        synchronized (counterLock(validCounterName, validKey)) {
            OptionalLong existingTtl = expiration.getExpiresAfter(counterKey, TimeUnit.NANOSECONDS);
            Long value = existingTtl.isPresent() ? counters.getIfPresent(counterKey) : null;
            if (value == null) {
                long initialValue = increase ? validStep : Math.subtractExact(0L, validStep);
                expiration.put(counterKey, Long.valueOf(initialValue), ttlMillis, TimeUnit.MILLISECONDS);
                return initialValue;
            }

            long newValue = increase
                    ? Math.addExact(value.longValue(), validStep)
                    : Math.subtractExact(value.longValue(), validStep);
            if (refreshTtl) {
                expiration.put(counterKey, Long.valueOf(newValue), ttlMillis, TimeUnit.MILLISECONDS);
            } else {
                expiration.put(counterKey, Long.valueOf(newValue), existingTtl.getAsLong(), TimeUnit.NANOSECONDS);
            }
            return newValue;
        }
    }

    /**
     * 选择计数 key 对应的分段锁。
     */
    private Object counterLock(String counterName, String key) {
        int hash = 31 * counterName.hashCode() + key.hashCode();
        return counterLocks[hash & (COUNTER_LOCK_COUNT - 1)];
    }

    /**
     * 创建计数分段锁。
     */
    private static Object[] createCounterLocks() {
        Object[] locks = new Object[COUNTER_LOCK_COUNT];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }
}
