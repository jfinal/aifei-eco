package cn.aifei.cache.redis;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.JedisURIHelper;

import java.net.URISyntaxException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * 可选的真实 Redis 集成测试。
 *
 * <p>通过 {@code mvn -Dredis.integration=true test} 运行。
 * 默认连接 {@code redis://127.0.0.1:6379}，可通过 {@code redis.uri} 修改。</p>
 */
public class RedisCacheIntegrationTest {

    private URI redisUri;
    private RedisCache cache;

    /**
     * 未启用集成测试开关时跳过测试。
     */
    @BeforeClass
    public static void requireIntegrationFlag() {
        assumeTrue(Boolean.getBoolean("redis.integration"));
    }

    /**
     * 创建真实 Redis 缓存实例。
     */
    @Before
    public void createCache() {
        redisUri = URI.create(System.getProperty("redis.uri", "redis://127.0.0.1:6379"));
        cache = new RedisCache(redisUri);
    }

    /**
     * 测试结束后关闭缓存。
     */
    @After
    public void closeCache() {
        if (cache != null) {
            cache.close();
        }
    }

    /**
     * 验证真实 Redis 下的读写、过期和清理行为。
     */
    @Test
    public void shouldExecuteCacheContractAgainstRealRedis() throws InterruptedException {
        String testId = UUID.randomUUID().toString();
        String rootCacheName = "integration-" + testId;
        String cacheName = rootCacheName + ":用户*?[x]\\路径/空 格";
        String key = "键:分段*?[x]/空 格";

        cache.put(cacheName, key, "value", Duration.ofMillis(150));
        assertEquals("value", cache.get(cacheName, key));
        assertTrue(cache.exists(cacheName, key));
        assertFalse(cache.expire(cacheName, "missing", Duration.ofMillis(100)));

        assertTrue(cache.expire(cacheName, key, Duration.ofMillis(500)));
        Thread.sleep(250);
        assertEquals("value", cache.get(cacheName, key));
        assertTrue(cache.exists(cacheName, key));

        assertTrue(cache.expire(cacheName, key, Duration.ofMillis(100)));

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (cache.exists(cacheName, key) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertNull(cache.get(cacheName, key));
        assertFalse(cache.exists(cacheName, key));
        assertFalse(cache.expire(cacheName, key, Duration.ofMillis(100)));

        assertTrue(cache.putIfAbsent(cacheName, "absent", "first", Duration.ofMinutes(1)));
        assertFalse(cache.putIfAbsent(cacheName, "absent", "second", Duration.ofMinutes(1)));
        assertEquals("first", cache.get(cacheName, "absent"));

        cache.put(cacheName, "1", "one", Duration.ofMinutes(1));
        cache.put(cacheName, "2", "two", Duration.ofMinutes(1));
        cache.put(cacheName, "manager:123", "manager", Duration.ofMinutes(1));
        String otherCacheName = rootCacheName + ":other";
        String siblingCacheName = rootCacheName + "-sibling";
        cache.put(otherCacheName, "1", "other", Duration.ofMinutes(1));
        cache.put(siblingCacheName, "1", "sibling", Duration.ofMinutes(1));

        cache.clear(cacheName);

        assertNull(cache.get(cacheName, "1"));
        assertNull(cache.get(cacheName, "2"));
        assertNull(cache.get(cacheName, "manager:123"));
        assertFalse(cache.exists(cacheName, "1"));
        assertFalse(cache.exists(cacheName, "2"));
        assertEquals("other", cache.get(otherCacheName, "1"));
        assertEquals("sibling", cache.get(siblingCacheName, "1"));

        cache.clear(rootCacheName);
        assertNull(cache.get(otherCacheName, "1"));
        assertEquals("sibling", cache.get(siblingCacheName, "1"));

        cache.clear(siblingCacheName);
        assertNull(cache.get(siblingCacheName, "1"));

        cache.close();
        cache.close();
        cache = null;
    }

    /**
     * 验证真实 Redis 下 loader 只在未命中时执行。
     */
    @Test
    public void shouldUseLoaderAgainstRealRedis() {
        String cacheName = "integration-loader-" + UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();

        try {
            String loaded = cache.get(cacheName, "1", Duration.ofMinutes(1), () -> {
                loads.incrementAndGet();
                return "loaded";
            });
            String hit = cache.get(cacheName, "1", Duration.ofMinutes(1), () -> {
                loads.incrementAndGet();
                return "second";
            });

            assertEquals("loaded", loaded);
            assertEquals("loaded", hit);
            assertEquals(1, loads.get());
        } finally {
            cache.clear(cacheName);
        }
    }

    /**
     * 验证 clear 可以跨多个 SCAN 页清理缓存项。
     */
    @Test
    public void shouldClearMoreThanOneScanPageAgainstRealRedis() {
        String cacheName = "integration-scan-" + UUID.randomUUID();

        try {
            for (int i = 0; i < 2500; i++) {
                cache.put(cacheName, "key-" + i, "value-" + i, Duration.ofMinutes(5));
            }

            cache.clear(cacheName);

            for (int i = 0; i < 2500; i++) {
                assertNull(cache.get(cacheName, "key-" + i));
            }
        } finally {
            cache.clear(cacheName);
        }
    }

    /**
     * 验证 RedisConfig 的 host/port、clientName 和连接池配置能在真实 Redis 上工作。
     */
    @Test
    public void shouldUseRedisConfigAgainstRealRedis() {
        String clientName = "aifei-cache-" + UUID.randomUUID();
        RedisConfig config = configuredHostPort(redisUri, 0)
                .clientName(clientName)
                .timeoutMillis(2000)
                .blockingSocketTimeoutMillis(0)
                .maxTotal(4)
                .maxIdle(2)
                .minIdle(0)
                .maxWaitMillis(1000)
                .testOnBorrow(true)
                .testWhileIdle(true);
        String cacheName = "integration-config-" + UUID.randomUUID();
        RedisCache configuredCache = new RedisCache(config);
        Jedis inspector = new Jedis(redisUri);

        try {
            configuredCache.put(cacheName, "key", "value", Duration.ofMinutes(1));

            assertEquals("value", configuredCache.get(cacheName, "key"));
            assertClientListContainsName(inspector.clientList(), clientName);
        } finally {
            configuredCache.clear(cacheName);
            configuredCache.close();
            inspector.close();
        }
    }

    /**
     * 验证默认 RedisConfig 可以连接本机默认 Redis。
     */
    @Test
    public void shouldUseDefaultRedisConfigAgainstLocalRedis() {
        assumeTrue(isDefaultLocalRedis(redisUri));
        String cacheName = "integration-default-config-" + UUID.randomUUID();
        RedisCache defaultCache = new RedisCache(new RedisConfig());

        try {
            defaultCache.put(cacheName, "key", "value", Duration.ofMinutes(1));

            assertEquals("value", defaultCache.get(cacheName, "key"));
        } finally {
            defaultCache.clear(cacheName);
            defaultCache.close();
        }
    }

    /**
     * 验证显式 database 会覆盖 URI 中的 database。
     */
    @Test
    public void shouldOverrideDatabaseFromUriAgainstRealRedis() {
        String cacheName = "integration-db-" + UUID.randomUUID();
        RedisCache db0 = new RedisCache(configuredUri(redisUri, 0));
        RedisCache db1 = new RedisCache(configuredUri(redisUri, 0).database(1));

        try {
            db0.clear(cacheName);
            db1.clear(cacheName);

            db1.put(cacheName, "key", "db1", Duration.ofMinutes(1));

            assertNull(db0.get(cacheName, "key"));
            assertEquals("db1", db1.get(cacheName, "key"));
        } finally {
            db0.clear(cacheName);
            db1.clear(cacheName);
            db0.close();
            db1.close();
        }
    }

    /**
     * 使用 URI 创建配置。
     */
    private static RedisConfig configuredUri(URI redisUri, int database) {
        return new RedisConfig().uri(uriWithDatabase(redisUri, database));
    }

    /**
     * 使用 host、port 和认证信息创建配置。
     */
    private static RedisConfig configuredHostPort(URI redisUri, int database) {
        RedisConfig config = new RedisConfig()
                .host(redisUri.getHost())
                .port(redisUri.getPort() == -1 ? 6379 : redisUri.getPort())
                .database(database);
        String user = JedisURIHelper.getUser(redisUri);
        String password = JedisURIHelper.getPassword(redisUri);
        if (user != null && !user.isEmpty()) {
            config.user(user);
        }
        if (password != null && !password.isEmpty()) {
            config.password(password);
        }
        if (JedisURIHelper.isRedisSSLScheme(redisUri)) {
            config.ssl(true);
        }
        return config;
    }

    /**
     * 替换 URI 中的 database。
     */
    private static URI uriWithDatabase(URI redisUri, int database) {
        try {
            return new URI(
                    redisUri.getScheme(),
                    redisUri.getUserInfo(),
                    redisUri.getHost(),
                    redisUri.getPort() == -1 ? 6379 : redisUri.getPort(),
                    "/" + database,
                    null,
                    null
            );
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid redisUri", e);
        }
    }

    /**
     * 判断当前集成测试目标是否是无认证的本机默认 Redis。
     */
    private static boolean isDefaultLocalRedis(URI redisUri) {
        String host = redisUri.getHost();
        int port = redisUri.getPort() == -1 ? 6379 : redisUri.getPort();
        return JedisURIHelper.isRedisScheme(redisUri)
                && redisUri.getUserInfo() == null
                && port == 6379
                && ("127.0.0.1".equals(host) || "localhost".equals(host));
    }

    /**
     * 确认 Redis 客户端列表中包含指定连接名称。
     */
    private static void assertClientListContainsName(String clientList, String clientName) {
        String expected = "name=" + clientName;
        if (!clientList.contains(expected)) {
            throw new AssertionError("Expected Redis client list to contain " + expected);
        }
    }
}
