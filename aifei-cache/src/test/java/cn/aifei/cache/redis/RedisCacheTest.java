package cn.aifei.cache.redis;

import org.junit.Test;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.providers.ConnectionProvider;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 验证 Redis 缓存的本地行为。
 */
public class RedisCacheTest {

    private static final long DEFAULT_MAX_WAIT_MILLIS = 1_500L;

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
     * 验证 RedisConfig 未显式配置连接池时，除 maxWaitMillis 外使用 Jedis 默认配置。
     */
    @Test
    public void shouldApplyDefaultPoolConfigToRedisClient() {
        RedisClient client = new RedisConfig().createClient();
        try {
            assertDefaultPoolConfig(client);
        } finally {
            client.close();
        }
    }

    /**
     * 验证 RedisConfig 未显式配置客户端参数时使用 Jedis 默认配置。
     */
    @Test
    public void shouldApplyDefaultClientConfigToRedisClient() throws Exception {
        RedisClient client = new RedisConfig().createClient();
        try {
            assertDefaultClientConfig(client, "127.0.0.1", 6379);
        } finally {
            client.close();
        }
    }

    /**
     * 验证 RedisCache 便捷构造器复用 RedisConfig 的默认装配。
     */
    @Test
    public void shouldApplyDefaultPoolConfigToRedisCacheConvenienceConstructors() throws Exception {
        RedisCache redisCache = new RedisCache(URI.create("redis://127.0.0.1:1"));
        try {
            assertDefaultPoolConfig(readClient(redisCache));
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证 RedisCache 便捷构造器复用 RedisConfig 的默认客户端配置。
     */
    @Test
    public void shouldApplyDefaultClientConfigToRedisCacheConvenienceConstructors() throws Exception {
        RedisCache redisCache = new RedisCache("redis.example.com", 6380);
        try {
            assertDefaultClientConfig(readClient(redisCache), "redis.example.com", 6380);
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证同一 JVM 内多个默认连接池不会发生 JMX 名称冲突。
     */
    @Test
    public void shouldCreateMultipleDefaultClientsWithoutJmxConflict() {
        RedisClient first = new RedisConfig().createClient();
        RedisClient second = new RedisConfig().createClient();
        try {
            if (new ConnectionPoolConfig().getJmxEnabled()) {
                assertNotNull(first.getPool().getJmxName());
                assertNotNull(second.getPool().getJmxName());
                assertFalse(first.getPool().getJmxName().equals(second.getPool().getJmxName()));
            } else {
                assertNull(first.getPool().getJmxName());
                assertNull(second.getPool().getJmxName());
            }
        } finally {
            first.close();
            second.close();
        }
    }

    /**
     * 验证 URI 提供基础连接信息，显式配置会覆盖 URI 中对应的客户端配置。
     */
    @Test
    public void shouldApplyExplicitClientConfigOverUri() throws Exception {
        URI redisUri = URI.create("rediss://uri-user:uri-password@redis.example.com:6380/2?protocol=3");
        RedisClient client = new RedisConfig()
                .uri(redisUri)
                .user("explicit-user")
                .password("explicit-password")
                .database(5)
                .clientName("aifei-cache-test")
                .ssl(false)
                .timeoutMillis(4000)
                .connectionTimeoutMillis(1500)
                .socketTimeoutMillis(2500)
                .blockingSocketTimeoutMillis(3500)
                .createClient();
        try {
            assertHostAndPort(client, "redis.example.com", 6380);

            JedisClientConfig clientConfig = readClientConfig(client);
            assertEquals(1500, clientConfig.getConnectionTimeoutMillis());
            assertEquals(2500, clientConfig.getSocketTimeoutMillis());
            assertEquals(3500, clientConfig.getBlockingSocketTimeoutMillis());
            assertEquals("explicit-user", clientConfig.getUser());
            assertEquals("explicit-password", clientConfig.getPassword());
            assertEquals(5, clientConfig.getDatabase());
            assertEquals("aifei-cache-test", clientConfig.getClientName());
            assertFalse(clientConfig.isSsl());
            assertSame(RedisProtocol.RESP3, clientConfig.getRedisProtocol());
        } finally {
            client.close();
        }
    }

    /**
     * 验证只显式覆盖一项认证信息时，另一项仍沿用 URI。
     */
    @Test
    public void shouldPreserveUriCredentialPartWhenOnlyOnePartIsExplicit() throws Exception {
        URI redisUri = URI.create("redis://uri-user:uri-password@redis.example.com:6380/0");
        RedisClient userOverrideClient = new RedisConfig()
                .uri(redisUri)
                .user("explicit-user")
                .createClient();
        RedisClient passwordOverrideClient = new RedisConfig()
                .uri(redisUri)
                .password("explicit-password")
                .createClient();

        try {
            JedisClientConfig userOverride = readClientConfig(userOverrideClient);
            assertEquals("explicit-user", userOverride.getUser());
            assertEquals("uri-password", userOverride.getPassword());

            JedisClientConfig passwordOverride = readClientConfig(passwordOverrideClient);
            assertEquals("uri-user", passwordOverride.getUser());
            assertEquals("explicit-password", passwordOverride.getPassword());
        } finally {
            userOverrideClient.close();
            passwordOverrideClient.close();
        }
    }

    /**
     * 验证 RedisCache 自动创建的 RedisCounter 共享同一个 RedisClient 和连接池。
     */
    @Test
    public void shouldCreateCounterSharingRedisClientPool() throws Exception {
        RedisCache redisCache = new RedisCache(URI.create("redis://127.0.0.1:1"));
        RedisCounter counter = (RedisCounter) redisCache.createCounter();
        RedisClient client = readClient(redisCache);

        try {
            assertSame(client, readClient(counter));

            assertFalse(counter instanceof AutoCloseable);
            assertFalse(client.getPool().isClosed());

            redisCache.close();
            assertTrue(client.getPool().isClosed());
        } finally {
            redisCache.close();
        }
    }

    /**
     * 验证只显式配置最大连接数时，不覆盖 Jedis 的其他容量默认值。
     */
    @Test
    public void shouldOnlyApplyExplicitMaxTotal() {
        ConnectionPoolConfig jedisDefault = new ConnectionPoolConfig();
        RedisClient client = new RedisConfig().maxTotal(4).createClient();
        try {
            assertEquals(4, client.getPool().getMaxTotal());
            assertEquals(jedisDefault.getMaxIdle(), client.getPool().getMaxIdle());
            assertEquals(jedisDefault.getMinIdle(), client.getPool().getMinIdle());
        } finally {
            client.close();
        }
    }

    /**
     * 验证只显式配置最大空闲连接数时，不覆盖 Jedis 的其他容量默认值。
     */
    @Test
    public void shouldOnlyApplyExplicitMaxIdle() {
        ConnectionPoolConfig jedisDefault = new ConnectionPoolConfig();
        RedisClient client = new RedisConfig().maxIdle(0).createClient();
        try {
            assertEquals(jedisDefault.getMaxTotal(), client.getPool().getMaxTotal());
            assertEquals(0, client.getPool().getMaxIdle());
            assertEquals(jedisDefault.getMinIdle(), client.getPool().getMinIdle());
        } finally {
            client.close();
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
     * 验证连接池等待时间允许显式恢复 commons-pool 的无限等待语义。
     */
    @Test
    public void shouldAllowInfiniteMaxWaitMillis() {
        RedisClient client = new RedisConfig().maxWaitMillis(-1).createClient();
        try {
            assertEquals(-1, client.getPool().getMaxWaitDuration().toMillis());
        } finally {
            client.close();
        }

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new RedisConfig().maxWaitMillis(-2));
        assertEquals("maxWaitMillis must be greater than or equal to -1", negative.getMessage());
    }

    /**
     * 验证每轮空闲连接扫描数量允许 Jedis 默认的 -1，但拒绝其他非正数。
     */
    @Test
    public void shouldRejectInvalidNumTestsPerEvictionRun() {
        RedisClient client = new RedisConfig().numTestsPerEvictionRun(-1).createClient();
        try {
            assertEquals(-1, client.getPool().getNumTestsPerEvictionRun());
        } finally {
            client.close();
        }

        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> new RedisConfig().numTestsPerEvictionRun(0));
        assertEquals("numTestsPerEvictionRun must be -1 or greater than 0", zero.getMessage());

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new RedisConfig().numTestsPerEvictionRun(-2));
        assertEquals("numTestsPerEvictionRun must be -1 or greater than 0", negative.getMessage());
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
     * 读取 RedisCache 内部 RedisClient，用于验证便捷构造器装配结果。
     */
    private static RedisClient readClient(RedisCache redisCache) throws Exception {
        Field field = RedisCache.class.getDeclaredField("client");
        field.setAccessible(true);
        return (RedisClient) field.get(redisCache);
    }

    /**
     * 读取 RedisCounter 内部 RedisClient，用于验证共享连接池。
     */
    private static RedisClient readClient(RedisCounter redisCounter) throws Exception {
        Field field = RedisCounter.class.getDeclaredField("client");
        field.setAccessible(true);
        return (RedisClient) field.get(redisCounter);
    }

    /**
     * 读取 RedisClient 的客户端配置。
     */
    private static JedisClientConfig readClientConfig(RedisClient client) throws Exception {
        Object factory = client.getPool().getFactory();
        Field field = factory.getClass().getDeclaredField("clientConfig");
        field.setAccessible(true);
        return (JedisClientConfig) field.get(factory);
    }

    /**
     * 读取 RedisClient 的目标主机和端口。
     */
    private static HostAndPort readHostAndPort(RedisClient client) throws Exception {
        Field field = UnifiedJedis.class.getDeclaredField("provider");
        field.setAccessible(true);
        ConnectionProvider provider = (ConnectionProvider) field.get(client);
        return (HostAndPort) provider.getConnectionMap().keySet().iterator().next();
    }

    /**
     * 验证默认 Redis 客户端配置。
     */
    private static void assertDefaultClientConfig(RedisClient client, String host, int port) throws Exception {
        assertHostAndPort(client, host, port);
        JedisClientConfig clientConfig = readClientConfig(client);
        JedisClientConfig jedisDefault = DefaultJedisClientConfig.builder().build();
        assertEquals(jedisDefault.getConnectionTimeoutMillis(), clientConfig.getConnectionTimeoutMillis());
        assertEquals(jedisDefault.getSocketTimeoutMillis(), clientConfig.getSocketTimeoutMillis());
        assertEquals(jedisDefault.getBlockingSocketTimeoutMillis(), clientConfig.getBlockingSocketTimeoutMillis());
        assertEquals(jedisDefault.getUser(), clientConfig.getUser());
        assertEquals(jedisDefault.getPassword(), clientConfig.getPassword());
        assertEquals(jedisDefault.getDatabase(), clientConfig.getDatabase());
        assertEquals(jedisDefault.getClientName(), clientConfig.getClientName());
        assertEquals(jedisDefault.isSsl(), clientConfig.isSsl());
        assertSame(jedisDefault.getRedisProtocol(), clientConfig.getRedisProtocol());
    }

    /**
     * 验证 RedisClient 的目标主机和端口。
     */
    private static void assertHostAndPort(RedisClient client, String host, int port) throws Exception {
        HostAndPort hostAndPort = readHostAndPort(client);
        assertEquals(host, hostAndPort.getHost());
        assertEquals(port, hostAndPort.getPort());
    }

    /**
     * 验证默认 Redis 连接池配置除 maxWaitMillis 外与 Jedis ConnectionPoolConfig 保持一致。
     */
    private static void assertDefaultPoolConfig(RedisClient client) {
        ConnectionPoolConfig jedisDefault = new ConnectionPoolConfig();
        assertEquals(jedisDefault.getMaxTotal(), client.getPool().getMaxTotal());
        assertEquals(jedisDefault.getMaxIdle(), client.getPool().getMaxIdle());
        assertEquals(jedisDefault.getMinIdle(), client.getPool().getMinIdle());
        assertEquals(DEFAULT_MAX_WAIT_MILLIS, client.getPool().getMaxWaitDuration().toMillis());
        assertEquals(jedisDefault.getBlockWhenExhausted(), client.getPool().getBlockWhenExhausted());
        assertEquals(jedisDefault.getLifo(), client.getPool().getLifo());
        assertEquals(jedisDefault.getFairness(), client.getPool().getFairness());
        assertEquals(jedisDefault.getTestOnCreate(), client.getPool().getTestOnCreate());
        assertEquals(jedisDefault.getTestOnBorrow(), client.getPool().getTestOnBorrow());
        assertEquals(jedisDefault.getTestOnReturn(), client.getPool().getTestOnReturn());
        assertEquals(jedisDefault.getTestWhileIdle(), client.getPool().getTestWhileIdle());
        assertEquals(jedisDefault.getDurationBetweenEvictionRuns().toMillis(),
                client.getPool().getDurationBetweenEvictionRuns().toMillis());
        assertEquals(jedisDefault.getMinEvictableIdleDuration().toMillis(),
                client.getPool().getMinEvictableIdleDuration().toMillis());
        assertEquals(jedisDefault.getSoftMinEvictableIdleDuration().toMillis(),
                client.getPool().getSoftMinEvictableIdleDuration().toMillis());
        assertEquals(jedisDefault.getNumTestsPerEvictionRun(), client.getPool().getNumTestsPerEvictionRun());
        if (jedisDefault.getJmxEnabled()) {
            assertNotNull(client.getPool().getJmxName());
        } else {
            assertNull(client.getPool().getJmxName());
        }
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
