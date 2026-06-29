package cn.aifei.cache.redis;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * 可选的真实 Redis 计数集成测试。
 *
 * <p>通过 {@code mvn -Dredis.integration=true -Dtest=RedisCounterIntegrationTest test} 运行。
 * 默认连接 {@code redis://127.0.0.1:6379}，可通过 {@code redis.uri} 修改。</p>
 */
public class RedisCounterIntegrationTest {

    private static final String COUNTER_PREFIX = "__aifei_cache_counter__:";

    private URI redisUri;
    private RedisCounter counter;

    /**
     * 未启用集成测试开关时跳过测试。
     */
    @BeforeClass
    public static void requireIntegrationFlag() {
        assumeTrue(Boolean.getBoolean("redis.integration"));
    }

    /**
     * 创建真实 Redis 计数实例。
     */
    @Before
    public void createCounter() {
        redisUri = URI.create(System.getProperty("redis.uri", "redis://127.0.0.1:6379"));
        counter = new RedisCounter(redisUri);
    }

    /**
     * 测试结束后关闭计数器。
     */
    @After
    public void closeCounter() {
        if (counter != null) {
            counter.close();
        }
    }

    /**
     * 验证真实 Redis 下的原生计数读写、TTL 保留和删除行为。
     */
    @Test
    public void shouldUpdateCounterAgainstRealRedis() throws InterruptedException {
        String counterName = "integration-counter-" + UUID.randomUUID();
        byte[] viewsKey = counterKey(counterName, "views");
        Jedis inspector = new Jedis(redisUri);

        try {
            inspector.del(viewsKey);

            assertEquals(1L, counter.increase(counterName, "views", 1L, Duration.ofMillis(300)));
            assertEquals(Long.valueOf(1L), counter.get(counterName, "views"));
            assertEquals("1", inspector.get(new String(viewsKey, StandardCharsets.UTF_8)));
            long ttlBefore = inspector.pttl(viewsKey);
            assertTrue(ttlBefore > 0L);

            Thread.sleep(50);
            assertEquals(4L, counter.increase(counterName, "views", 3L, Duration.ofSeconds(5)));
            assertEquals(Long.valueOf(4L), counter.get(counterName, "views"));
            long ttlAfter = inspector.pttl(viewsKey);
            assertTrue(ttlAfter > 0L);
            assertTrue(ttlAfter <= ttlBefore);

            assertEquals(2L, counter.decrease(counterName, "views", 2L, Duration.ofSeconds(5)));
            assertEquals(Long.valueOf(2L), counter.get(counterName, "views"));

            long ttlBeforeRefresh = inspector.pttl(viewsKey);
            assertTrue(ttlBeforeRefresh > 0L);
            assertEquals(7L, counter.increaseAndRefreshTtl(counterName, "views", 5L, Duration.ofSeconds(5)));
            long ttlAfterRefresh = inspector.pttl(viewsKey);
            assertTrue(ttlAfterRefresh > ttlBeforeRefresh);
            assertTrue(ttlAfterRefresh <= 5000L);

            Thread.sleep(20);
            long ttlBeforeDecreaseRefresh = inspector.pttl(viewsKey);
            assertTrue(ttlBeforeDecreaseRefresh > 0L);
            assertEquals(4L, counter.decreaseAndRefreshTtl(counterName, "views", 3L, Duration.ofSeconds(6)));
            long ttlAfterDecreaseRefresh = inspector.pttl(viewsKey);
            assertTrue(ttlAfterDecreaseRefresh > ttlBeforeDecreaseRefresh);
            assertTrue(ttlAfterDecreaseRefresh <= 6000L);

            counter.remove(counterName, "missing");
            counter.remove(counterName, "views");
            assertNull(counter.get(counterName, "views"));
        } finally {
            inspector.del(viewsKey);
            inspector.close();
        }
    }

    /**
     * 验证计数项过期后会重新创建。
     */
    @Test
    public void shouldRecreateCounterAfterExpirationAgainstRealRedis() throws InterruptedException {
        String counterName = "integration-counter-expire-" + UUID.randomUUID();

        assertEquals(-4L, counter.decrease(counterName, "quota", 4L, Duration.ofMillis(120)));
        waitUntilMissing(counterName, "quota");

        assertNull(counter.get(counterName, "quota"));
        assertEquals(2L, counter.increase(counterName, "quota", 2L, Duration.ofMinutes(1)));
        assertEquals(Long.valueOf(2L), counter.get(counterName, "quota"));
        counter.remove(counterName, "quota");
    }

    /**
     * 验证 RedisCounter 与 RedisCache 的同名 key 不冲突。
     */
    @Test
    public void shouldNotConflictWithRedisCacheAgainstRealRedis() {
        String name = "integration-counter-cache-" + UUID.randomUUID();
        RedisCache cache = new RedisCache(redisUri);

        try {
            cache.put(name, "same", "value", Duration.ofMinutes(1));
            assertEquals(1L, counter.increase(name, "same", 1L, Duration.ofMinutes(1)));

            assertEquals("value", cache.get(name, "same"));
            assertEquals(Long.valueOf(1L), counter.get(name, "same"));

            counter.remove(name, "same");
            assertEquals("value", cache.get(name, "same"));

            assertEquals(2L, counter.increase(name, "same", 2L, Duration.ofMinutes(1)));
            cache.remove(name, "same");
            assertNull(cache.get(name, "same"));
            assertEquals(Long.valueOf(2L), counter.get(name, "same"));
        } finally {
            counter.remove(name, "same");
            cache.remove(name, "same");
            cache.close();
        }
    }

    /**
     * 验证内部非法计数值会被拒绝。
     */
    @Test
    public void shouldRejectInvalidCounterStateAgainstRealRedis() {
        String counterName = "integration-counter-invalid-" + UUID.randomUUID();
        byte[] mixedKey = counterKey(counterName, "mixed");
        byte[] noTtlKey = counterKey(counterName, "no-ttl");
        Jedis inspector = new Jedis(redisUri);

        try {
            inspector.psetex(mixedKey, 60_000L, bytes("value"));
            long mixedTtlBefore = inspector.pttl(mixedKey);
            assertThrows(IllegalStateException.class, () -> counter.get(counterName, "mixed"));
            assertThrows(IllegalStateException.class,
                    () -> counter.increase(counterName, "mixed", 1L, Duration.ofMinutes(1)));
            assertThrows(IllegalStateException.class,
                    () -> counter.increaseAndRefreshTtl(counterName, "mixed", 1L, Duration.ofMinutes(1)));
            assertEquals("value", new String(inspector.get(mixedKey), StandardCharsets.UTF_8));
            long mixedTtlAfter = inspector.pttl(mixedKey);
            assertTrue(mixedTtlAfter > 0L);
            assertTrue(mixedTtlAfter <= mixedTtlBefore);

            inspector.set(noTtlKey, bytes("1"));
            assertThrows(IllegalStateException.class, () -> counter.get(counterName, "no-ttl"));
            assertThrows(IllegalStateException.class,
                    () -> counter.increase(counterName, "no-ttl", 1L, Duration.ofMinutes(1)));
            assertThrows(IllegalStateException.class,
                    () -> counter.increaseAndRefreshTtl(counterName, "no-ttl", 1L, Duration.ofMinutes(1)));
            assertEquals("1", new String(inspector.get(noTtlKey), StandardCharsets.UTF_8));
            assertEquals(-1L, inspector.pttl(noTtlKey));
        } finally {
            inspector.del(mixedKey, noTtlKey);
            inspector.close();
        }
    }

    /**
     * 验证 Redis integer 溢出映射为 {@link ArithmeticException}。
     */
    @Test
    public void shouldRejectCounterOverflowAgainstRealRedis() throws InterruptedException {
        String counterName = "integration-counter-overflow-" + UUID.randomUUID();
        byte[] maxKey = counterKey(counterName, "max");
        byte[] minKey = counterKey(counterName, "min");
        Jedis inspector = new Jedis(redisUri);

        try {
            assertEquals(Long.MAX_VALUE,
                    counter.increase(counterName, "max", Long.MAX_VALUE, Duration.ofSeconds(5)));
            Thread.sleep(20);
            long maxTtlBefore = inspector.pttl(maxKey);
            assertTrue(maxTtlBefore > 0L);
            assertThrows(ArithmeticException.class,
                    () -> counter.increase(counterName, "max", 1L, Duration.ofMinutes(1)));
            assertEquals(Long.valueOf(Long.MAX_VALUE), counter.get(counterName, "max"));
            long maxTtlAfter = inspector.pttl(maxKey);
            assertTrue(maxTtlAfter > 0L);
            assertTrue(maxTtlAfter <= maxTtlBefore);

            assertEquals(-Long.MAX_VALUE,
                    counter.decrease(counterName, "min", Long.MAX_VALUE, Duration.ofSeconds(5)));
            assertEquals(Long.MIN_VALUE,
                    counter.decrease(counterName, "min", 1L, Duration.ofMinutes(1)));
            Thread.sleep(20);
            long minTtlBefore = inspector.pttl(minKey);
            assertTrue(minTtlBefore > 0L);
            assertThrows(ArithmeticException.class,
                    () -> counter.decrease(counterName, "min", 1L, Duration.ofMinutes(1)));
            assertEquals(Long.valueOf(Long.MIN_VALUE), counter.get(counterName, "min"));
            long minTtlAfter = inspector.pttl(minKey);
            assertTrue(minTtlAfter > 0L);
            assertTrue(minTtlAfter <= minTtlBefore);
        } finally {
            counter.remove(counterName, "max");
            counter.remove(counterName, "min");
            inspector.close();
        }
    }

    /**
     * 验证 Lua number 无法精确表示的大整数也按 Redis integer 原值返回。
     */
    @Test
    public void shouldReturnExactLargeCounterValuesAgainstRealRedis() {
        String counterName = "integration-counter-large-" + UUID.randomUUID();
        long largeAmount = 9_007_199_254_740_993L;

        try {
            assertEquals(largeAmount,
                    counter.increase(counterName, "large", largeAmount, Duration.ofMinutes(1)));
            assertEquals(Long.valueOf(largeAmount), counter.get(counterName, "large"));

            assertEquals(0L,
                    counter.decrease(counterName, "large", largeAmount, Duration.ofMinutes(1)));
            assertEquals(Long.valueOf(0L), counter.get(counterName, "large"));
        } finally {
            counter.remove(counterName, "large");
        }
    }

    /**
     * 验证多个客户端并发更新同一个 Redis 计数项时不会丢更新。
     */
    @Test
    public void shouldUpdateCounterAtomicallyUnderConcurrencyAgainstRealRedis() throws InterruptedException {
        final int threads = 8;
        final int iterations = 200;
        final String counterName = "integration-counter-concurrent-" + UUID.randomUUID();
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        try {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    RedisCounter workerCounter = new RedisCounter(redisUri);
                    try {
                        start.await();
                        for (int j = 0; j < iterations; j++) {
                            workerCounter.increase(counterName, "views", 1L, Duration.ofMinutes(1));
                            workerCounter.decrease(counterName, "views", 1L, Duration.ofMinutes(1));
                            workerCounter.increase(counterName, "views", 1L, Duration.ofMinutes(1));
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        workerCounter.close();
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(20, TimeUnit.SECONDS));
            executor.shutdownNow();

            if (failure.get() != null) {
                throw new AssertionError("Concurrent Redis counter update failed", failure.get());
            }
            assertEquals(Long.valueOf((long) threads * iterations), counter.get(counterName, "views"));
        } finally {
            executor.shutdownNow();
            counter.remove(counterName, "views");
        }
    }

    /**
     * 等待计数项过期。
     */
    private void waitUntilMissing(String counterName, String key) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (counter.get(counterName, key) != null && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }

    /**
     * 生成测试中使用的 Redis 计数物理 key。
     */
    private static byte[] counterKey(String counterName, String key) {
        return bytes(COUNTER_PREFIX + counterName + ":" + key);
    }

    /**
     * 将字符串转换为 UTF-8 字节数组。
     */
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
