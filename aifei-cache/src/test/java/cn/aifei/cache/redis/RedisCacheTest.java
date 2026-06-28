package cn.aifei.cache.redis;

import org.junit.Test;
import redis.clients.jedis.RedisClient;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 验证 Redis 缓存的本地行为。
 */
public class RedisCacheTest {

    /**
     * 验证非法普通读取不会访问 Redis。
     */
    @Test
    public void shouldReturnNullWhenReadArgumentsAreInvalid() {
        RedisCache redisCache = new RedisCache(URI.create("redis://127.0.0.1:1"));
        try {
            assertNull(redisCache.get(null, "key"));
            assertNull(redisCache.get("", "key"));
            assertNull(redisCache.get(" ", "key"));
            assertNull(redisCache.get("cache", null));
            assertNull(redisCache.get("cache", ""));
            assertNull(redisCache.get("cache", " "));
            assertNull(redisCache.get("cache", "key:1"));
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证非法存在性判断不会访问 Redis。
     */
    @Test
    public void shouldReturnFalseWhenExistsArgumentsAreInvalid() {
        RedisCache redisCache = new RedisCache(URI.create("redis://127.0.0.1:1"));
        try {
            assertFalse(redisCache.exists(null, "key"));
            assertFalse(redisCache.exists("", "key"));
            assertFalse(redisCache.exists(" ", "key"));
            assertFalse(redisCache.exists("cache", null));
            assertFalse(redisCache.exists("cache", ""));
            assertFalse(redisCache.exists("cache", " "));
            assertFalse(redisCache.exists("cache", "key:1"));
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证非法条件写入不会访问 Redis。
     */
    @Test
    public void shouldRejectInvalidPutIfAbsentArgumentsWithoutAccessingRedis() {
        RedisCache redisCache = new RedisCache(URI.create("redis://127.0.0.1:1"));
        Duration ttl = Duration.ofMinutes(1);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent(null, "key", "value", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("", "key", "value", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent(" ", "key", "value", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("cache", null, "value", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("cache", "", "value", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("cache", " ", "value", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("cache", "key:1", "value", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("cache", "key", null, ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("cache", "key", "value", null));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.putIfAbsent("cache", "key", "value", Duration.ZERO));
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证非法续期不会访问 Redis。
     */
    @Test
    public void shouldRejectInvalidExpireArgumentsWithoutAccessingRedis() {
        RedisCache redisCache = new RedisCache(URI.create("redis://127.0.0.1:1"));
        Duration ttl = Duration.ofMinutes(1);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire(null, "key", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire("", "key", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire(" ", "key", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire("cache", null, ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire("cache", "", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire("cache", " ", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire("cache", "key:1", ttl));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire("cache", "key", null));
            assertThrows(IllegalArgumentException.class,
                    () -> redisCache.expire("cache", "key", Duration.ZERO));
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证重复关闭 RedisCache 不会重复抛错。
     */
    @Test
    public void shouldCloseRedisCacheMoreThanOnce() {
        RedisCache redisCache = new RedisCache(URI.create("redis://127.0.0.1:1"));

        redisCache.close();
        redisCache.close();
    }

    /**
     * 验证 RedisCache 可以使用配置对象创建并重复关闭。
     */
    @Test
    public void shouldCreateRedisCacheWithConfig() {
        RedisConfig config = new RedisConfig()
                .uri("redis://127.0.0.1:1")
                .password("password")
                .database(0)
                .resp3()
                .timeoutMillis(1000)
                .blockingSocketTimeoutMillis(0)
                .maxTotal(4)
                .maxIdle(2)
                .minIdle(1)
                .maxWaitMillis(1000)
                .testOnBorrow(true);
        RedisCache redisCache = new RedisCache(config);

        redisCache.close();
        redisCache.close();
    }

    /**
     * 验证 RedisConfig 可以配置 Redis value 编解码器。
     */
    @Test
    public void shouldUseConfiguredValueCodec() throws Exception {
        RedisValueCodec codec = new TextRedisValueCodec();
        RedisConfig config = new RedisConfig()
                .uri("redis://127.0.0.1:1")
                .valueCodec(codec);
        RedisCache redisCache = new RedisCache(config);

        try {
            assertSame(codec, config.createValueCodec());
            assertSame(codec, readCodec(redisCache));
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证 Redis value 编解码器不能为 null。
     */
    @Test
    public void shouldRejectNullValueCodec() {
        try {
            new RedisConfig().valueCodec(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            assertEquals("valueCodec can not be null", expected.getMessage());
        }
    }

    /**
     * 验证连接池配置会应用到 RedisClient。
     */
    @Test
    public void shouldApplyPoolConfigToRedisClient() {
        RedisConfig config = new RedisConfig()
                .maxTotal(-1)
                .maxIdle(16)
                .minIdle(4)
                .maxWaitMillis(3000)
                .blockWhenExhausted(false)
                .lifo(false)
                .fairness(true)
                .testOnCreate(true)
                .testOnBorrow(true)
                .testOnReturn(true)
                .testWhileIdle(true)
                .timeBetweenEvictionRunsMillis(60000)
                .minEvictableIdleTimeMillis(120000)
                .softMinEvictableIdleTimeMillis(90000)
                .numTestsPerEvictionRun(3)
                .jmxEnabled(false)
                .jmxNamePrefix("aifei-cache-test")
                .jmxNameBase("aifei-cache");
        RedisClient client = config.createClient();
        try {
            assertEquals(-1, client.getPool().getMaxTotal());
            assertEquals(16, client.getPool().getMaxIdle());
            assertEquals(4, client.getPool().getMinIdle());
            assertEquals(3000, client.getPool().getMaxWaitDuration().toMillis());
            assertFalse(client.getPool().getBlockWhenExhausted());
            assertFalse(client.getPool().getLifo());
            assertTrue(client.getPool().getFairness());
            assertTrue(client.getPool().getTestOnCreate());
            assertTrue(client.getPool().getTestOnBorrow());
            assertTrue(client.getPool().getTestOnReturn());
            assertTrue(client.getPool().getTestWhileIdle());
            assertEquals(60000, client.getPool().getDurationBetweenEvictionRuns().toMillis());
            assertEquals(120000, client.getPool().getMinEvictableIdleDuration().toMillis());
            assertEquals(90000, client.getPool().getSoftMinEvictableIdleDuration().toMillis());
            assertEquals(3, client.getPool().getNumTestsPerEvictionRun());
            assertNull(client.getPool().getJmxName());
        } finally {
            client.close();
        }
    }

    /**
     * 验证非法连接池上下限在创建客户端时被拒绝。
     */
    @Test
    public void shouldRejectInvalidPoolBounds() {
        RedisConfig config = new RedisConfig()
                .maxTotal(4)
                .maxIdle(8);

        try {
            config.createClient();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("maxIdle must be less than or equal to maxTotal", expected.getMessage());
        }
    }

    /**
     * 验证连接池最大连接数只接受 -1 或正数。
     */
    @Test
    public void shouldRejectInvalidMaxTotal() {
        try {
            new RedisConfig().maxTotal(0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("maxTotal must be -1 or greater than 0", expected.getMessage());
        }
    }

    /**
     * 验证 Fury 可以往返处理对象和集合。
     */
    @Test
    public void shouldRoundTripPojoAndCollectionsWithFury() {
        FuryRedisValueCodec codec = new FuryRedisValueCodec();
        CacheValue value = new CacheValue("James", Arrays.asList("cache", "redis"));

        CacheValue restored = (CacheValue) codec.deserialize(codec.serialize(value));

        assertEquals(value.name, restored.name);
        assertEquals(value.tags, restored.tags);
    }

    /**
     * 验证 Fury 会保留缓存快照内部的共享引用和循环引用。
     */
    @Test
    public void shouldPreserveReferencesWithFury() {
        FuryRedisValueCodec codec = new FuryRedisValueCodec();
        ReferenceNode shared = new ReferenceNode("shared");
        ReferenceValue value = new ReferenceValue(shared, shared);
        value.self = value;

        ReferenceValue restored = (ReferenceValue) codec.deserialize(codec.serialize(value));

        assertEquals("shared", restored.left.name);
        assertSame(restored.left, restored.right);
        assertSame(restored, restored.self);
    }

    /**
     * 读取 RedisCache 内部 codec，用于验证配置装配结果。
     */
    private static RedisValueCodec readCodec(RedisCache redisCache) throws Exception {
        Field field = RedisCache.class.getDeclaredField("codec");
        field.setAccessible(true);
        return (RedisValueCodec) field.get(redisCache);
    }

    /**
     * 用于验证自定义 codec 装配的简单文本 codec。
     */
    private static final class TextRedisValueCodec implements RedisValueCodec {

        /**
         * 转换为 UTF-8 字节数组。
         */
        @Override
        public byte[] serialize(Object value) {
            return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        }

        /**
         * 从 UTF-8 字节数组恢复字符串。
         */
        @Override
        public Object deserialize(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * 用于验证 Fury 兼容序列化的对象。
     */
    private static final class CacheValue {

        private String name;
        private List<String> tags;

        /**
         * 供 Fury 反序列化使用。
         */
        private CacheValue() {
        }

        /**
         * 创建测试对象。
         */
        private CacheValue(String name, List<String> tags) {
            this.name = name;
            this.tags = tags;
        }
    }

    /**
     * 用于验证引用跟踪的对象。
     */
    private static final class ReferenceValue {

        private ReferenceNode left;
        private ReferenceNode right;
        private ReferenceValue self;

        /**
         * 供 Fury 反序列化使用。
         */
        private ReferenceValue() {
        }

        /**
         * 创建测试对象。
         */
        private ReferenceValue(ReferenceNode left, ReferenceNode right) {
            this.left = left;
            this.right = right;
        }
    }

    /**
     * 用于验证共享引用的子对象。
     */
    private static final class ReferenceNode {

        private String name;

        /**
         * 供 Fury 反序列化使用。
         */
        private ReferenceNode() {
        }

        /**
         * 创建测试对象。
         */
        private ReferenceNode(String name) {
            this.name = name;
        }
    }
}
