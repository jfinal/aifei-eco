/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.redis;

import cn.aifei.cache.Counter;
import cn.aifei.cache.internal.CacheValidator;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * 基于 Redis 的分布式计数实现。
 */
public class RedisCounter implements Counter {

    private static final String COUNTER_PREFIX = "_Aifei_Counter_:";

    /*
     * Redis Lua 会把嵌套命令的 integer reply 转为 Lua number，大整数直接返回可能丢精度。
     * 因此脚本执行 INCRBY 后读取 Redis 原始字符串作为返回值。
     */
    private static final String UPDATE_SCRIPT =
            "local ttl = redis.call('pttl', KEYS[1]); "
                    + "if ttl == -2 then "
                    + "redis.call('psetex', KEYS[1], ARGV[2], '0'); "
                    + "elseif ttl == -1 then "
                    + "return redis.error_reply('counter value must have ttl'); "
                    + "end; "
                    + "redis.call('incrby', KEYS[1], ARGV[1]); "
                    + "if ARGV[3] == '1' then "
                    + "redis.call('pexpire', KEYS[1], ARGV[2]); "
                    + "end; "
                    + "return redis.call('get', KEYS[1]);";

    private final RedisClient client;

    /**
     * 使用指定 Redis 客户端创建计数器。
     */
    RedisCounter(RedisClient client) {
        this.client = Objects.requireNonNull(client, "client can not be null");
    }

    /**
     * 从 Redis 读取计数值。
     */
    @Override
    public Long get(String counterName, String key) {
        if (!CacheValidator.isValidCounterNameAndKey(counterName, key)) {
            return null;
        }
        String redisKey = counterKey(counterName, key);
        String value = client.get(redisKey);
        if (value == null) {
            return null;
        }
        long ttlMillis = client.pttl(redisKey);
        if (ttlMillis == -2L) {
            return null;
        }
        if (ttlMillis == -1L) {
            throw new IllegalStateException("counter value must have ttl");
        }
        return parseCounterValue(value);
    }

    /**
     * 增加 Redis 原生计数值。
     */
    @Override
    public long increase(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, true, false);
    }

    /**
     * 增加 Redis 原生计数值，并刷新 TTL。
     */
    @Override
    public long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, true, true);
    }

    /**
     * 减少 Redis 原生计数值。
     */
    @Override
    public long decrease(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, false, false);
    }

    /**
     * 减少 Redis 原生计数值，并刷新 TTL。
     */
    @Override
    public long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, false, true);
    }

    /**
     * 从 Redis 删除指定计数项。
     */
    @Override
    public void remove(String counterName, String key) {
        if (!CacheValidator.isValidCounterNameAndKey(counterName, key)) {
            return;
        }
        client.del(counterKey(counterName, key));
    }

    /**
     * 使用 Redis 原生 integer 原子更新或创建计数值。
     */
    private long updateCounter(String counterName, String key, long step, Duration ttl, boolean increase, boolean refreshTtl) {
        String validCounterName = CacheValidator.requireCounterName(counterName);
        String validKey = CacheValidator.requireKey(key);
        long validStep = CacheValidator.requireCounterStep(step);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        long delta = increase ? validStep : Math.negateExact(validStep);

        try {
            Object result = client.eval(
                    UPDATE_SCRIPT,
                    Collections.singletonList(counterKey(validCounterName, validKey)),
                    Arrays.asList(Long.toString(delta), Long.toString(ttlMillis), refreshTtl ? "1" : "0")
            );
            return toLong(result);
        } catch (JedisDataException e) {
            throw translateCounterException(e);
        }
    }

    /**
     * 生成 Redis 计数物理 key。
     */
    private static String counterKey(String counterName, String key) {
        return COUNTER_PREFIX + counterName + ":" + key;
    }

    /**
     * 转换 Redis 脚本返回值。
     */
    private static long toLong(Object result) {
        if (result instanceof Long) {
            return (Long) result;
        }
        return Long.parseLong(String.valueOf(result));
    }

    /**
     * 解析 Redis 原生 signed long 文本。
     */
    private static Long parseCounterValue(String value) {
        if (!isSignedLongText(value)) {
            throw new IllegalStateException("counter value must be Redis integer with ttl");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("counter value must be Redis integer with ttl", e);
        }
    }

    /**
     * 判断字符串是否为 Redis 计数值格式。
     */
    private static boolean isSignedLongText(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int index = value.charAt(0) == '-' ? 1 : 0;
        if (index == value.length()) {
            return false;
        }
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            index++;
        }
        return true;
    }

    /**
     * 将 Redis integer 错误转换为接口层更稳定的异常。
     */
    private static RuntimeException translateCounterException(JedisDataException e) {
        String message = e.getMessage();
        if (message != null && message.contains("overflow")) {
            ArithmeticException overflow = new ArithmeticException(message);
            overflow.initCause(e);
            return overflow;
        }
        if (message != null && (message.contains("not an integer")
                || message.contains("out of range")
                || message.contains("counter value must have ttl"))) {
            return new IllegalStateException("counter value must be Redis integer with ttl", e);
        }
        return e;
    }
}
