/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.redis;

import cn.aifei.cache.Cache;
import cn.aifei.cache.Counter;
import cn.aifei.cache.internal.CacheValidator;
import cn.aifei.cache.internal.CounterFactory;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的分布式缓存实现。
 */
public class RedisCache implements Cache, AutoCloseable, CounterFactory {

    private static final int SCAN_COUNT = 1_000;

    private final RedisClient client;
    private final RedisValueCodec codec;
    private boolean closed;

    /**
     * 连接默认 Redis 地址。
     */
    public RedisCache() {
        this(new RedisConfig());
    }

    /**
     * 使用指定主机和端口连接 Redis。
     */
    public RedisCache(String host, int port) {
        this(new RedisConfig().host(host).port(port));
    }

    /**
     * 使用指定 URI 连接 Redis。
     */
    public RedisCache(URI redisUri) {
        this(new RedisConfig().uri(redisUri));
    }

    /**
     * 使用指定配置连接 Redis。
     */
    public RedisCache(RedisConfig config) {
        RedisConfig configSnapshot = Objects.requireNonNull(config, "config can not be null").copy();
        this.codec = configSnapshot.createValueCodec();
        this.client = configSnapshot.createClient();
    }

    /**
     * 创建共享相同 Redis 客户端和连接池的计数器。
     */
    @Override
    public Counter createCounter() {
        return new RedisCounter(client);
    }

    /**
     * 从 Redis 读取并反序列化缓存值。
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return null;
        }
        byte[] redisKey = bytes(cacheName + ":" + key);
        byte[] bytes = client.get(redisKey);
        return bytes == null ? null : (T) codec.deserialize(bytes);
    }

    /**
     * 判断 Redis 中是否存在指定缓存项。
     */
    @Override
    public boolean exists(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return false;
        }
        return client.exists(bytes(cacheName + ":" + key));
    }

    /**
     * 序列化并写入带有效期的缓存值。
     */
    @Override
    public void put(String cacheName, String key, Object value, Duration ttl) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        String validKey = CacheValidator.requireKey(key);
        CacheValidator.requireValue(value);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        client.set(
                bytes(validCacheName + ":" + validKey),
                codec.serialize(value),
                SetParams.setParams().px(ttlMillis)
        );
    }

    /**
     * 当 Redis 中不存在指定缓存项时序列化并写入缓存值。
     */
    @Override
    public boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
        CacheValidator.requireCacheName(cacheName);
        CacheValidator.requireKey(key);
        CacheValidator.requireValue(value);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        String result = client.set(
                bytes(cacheName + ":" + key),
                codec.serialize(value),
                SetParams.setParams().px(ttlMillis).nx()
        );
        return result != null;
    }

    /**
     * 重设 Redis 中已有缓存项的剩余有效期。
     */
    @Override
    public boolean expire(String cacheName, String key, Duration ttl) {
        CacheValidator.requireCacheName(cacheName);
        CacheValidator.requireKey(key);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        return client.pexpire(bytes(cacheName + ":" + key), ttlMillis) == 1L;
    }

    /**
     * 删除指定缓存项。
     */
    @Override
    public void remove(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return;
        }
        client.del(bytes(cacheName + ":" + key));
    }

    /**
     * 扫描并清空指定名称下的缓存项。
     */
    @Override
    public void clear(String cacheName) {
        byte[] pattern = redisPattern(CacheValidator.requireCacheName(cacheName));
        byte[] cursor = ScanParams.SCAN_POINTER_START_BINARY;
        ScanParams scanParams = new ScanParams().match(pattern).count(SCAN_COUNT);
        ScanResult<byte[]> page;
        do {
            page = client.scan(cursor, scanParams);
            List<byte[]> keys = page.getResult();
            if (!keys.isEmpty()) {
                client.unlink(keys.toArray(new byte[keys.size()][]));
            }
            cursor = page.getCursorAsBytes();
        } while (!page.isCompleteIteration());
    }

    /**
     * 关闭 Redis 客户端；重复调用不会重复关闭。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        client.close();
        closed = true;
    }

    /**
     * 生成清理缓存时使用的匹配模式。
     */
    private byte[] redisPattern(String cacheName) {
        return bytes(escapeRedisGlob(cacheName) + ":*");
    }

    /**
     * 转义 Redis 匹配模式中的特殊字符。
     */
    private static String escapeRedisGlob(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '*' || character == '?' || character == '['
                    || character == ']' || character == '\\') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    /**
     * 将字符串转换为 UTF-8 字节数组。
     */
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
