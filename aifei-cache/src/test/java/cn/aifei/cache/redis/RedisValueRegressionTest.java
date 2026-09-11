package cn.aifei.cache.redis;

import cn.aifei.cache.regression.RegressionTestSupport;
import cn.aifei.cache.Counter;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.SetParams;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class RedisValueRegressionTest {
    @Parameterized.Parameters(name="{0}") public static Object[] protocols() { return new Object[] {"redis-resp2", "redis-resp3"}; }
    @Parameterized.Parameter public String kind;
    private RegressionTestSupport.Backend b;
    @Before public void open() { b = new RegressionTestSupport.Backend(kind); }
    @After public void close() throws Exception { if (b != null) b.close(); }
    private RedisConfig config() {
        RedisConfig config = RegressionTestSupport.config();
        return kind.equals("redis-resp3") ? config.resp3() : config;
    }

    public static class Graph {
        public String name;
        public Graph self;
        public Object left;
        public Object right;
        public List<Object> values;
        public Graph() { }
    }

    @Test public void furyPreservesPojoCyclesSharedReferencesAndNumericBoundaries() {
        Graph original = new Graph();
        original.name = "中文🙂";
        original.self = original;
        original.left = new ArrayList<>(Arrays.asList("shared", 42));
        original.right = original.left;
        original.values = Arrays.asList(Long.MIN_VALUE, Long.MAX_VALUE, new BigInteger("99999999999999999999999999999999"),
                new BigDecimal("1.234567890123456789"), LocalDateTime.of(2026, 9, 9, 12, 34), UUID.randomUUID());
        b.cache.put(b.name("graph"), "k", original, 30);
        Graph copy = b.peerCache.get(b.name("graph"), "k");
        assertNotSame(original, copy);
        assertEquals(original.name, copy.name);
        assertSame(copy, copy.self);
        assertSame(copy.left, copy.right);
        assertEquals(original.values, copy.values);
    }

    @Test public void concurrentFuryRoundTripsDoNotMixValues() throws Exception {
        RegressionTestSupport.parallel(12, worker -> {
            for (int i = 0; i < 100; i++) {
                Graph value = new Graph(); value.name = worker + ":" + i; value.self = value;
                String key = "k" + worker;
                b.cache.put(b.name("codec-race"), key, value, 60);
                Graph copy = b.peerCache.get(b.name("codec-race"), key);
                assertEquals(value.name, copy.name); assertSame(copy, copy.self);
            }
        });
    }

    @Test public void ttlAndRawIntegerCanBeVerifiedWithoutUsingImplementationGet() {
        String n = b.name("raw");
        b.cache.put(n, "k", "value", Duration.ofMillis(12345));
        long ttl = b.raw.pttl(n + ":k");
        assertTrue("TTL=" + ttl, ttl > 11000 && ttl <= 12345);
        b.counter.increase(n, "k", 9007199254740993L, Duration.ofMillis(12345));
        String physical = b.counterKey(n, "k");
        assertEquals("9007199254740993", b.raw.get(physical));
        long before = b.raw.pttl(physical);
        b.counter.decrease(n, "k", 1, 120);
        assertTrue(b.raw.pttl(physical) <= before);
        b.counter.decreaseAndRefreshTtl(n, "k", 1, 120);
        assertTrue(b.raw.pttl(physical) > 119000);
    }

    @Test public void corruptIntegerTextAndMissingTtlAreRejectedWithoutMutation() {
        String n = b.name("corrupt"), key = b.counterKey(n, "k");
        for (String text : Arrays.asList("hello", "1.2", "+1", "-", "", "9223372036854775808", "-9223372036854775809")) {
            b.raw.set(key, text, SetParams.setParams().px(30000));
            assertThrows(text, IllegalStateException.class, () -> b.counter.get(n, "k"));
            assertThrows(text, IllegalStateException.class, () -> b.counter.increase(n, "k", 1, 30));
            assertEquals(text, b.raw.get(key));
        }
        b.raw.set(key, "42");
        assertThrows(IllegalStateException.class, () -> b.counter.get(n, "k"));
        assertThrows(IllegalStateException.class, () -> b.counter.increaseAndRefreshTtl(n, "k", 1, 30));
        assertEquals("42", b.raw.get(key));
        assertEquals(-1, b.raw.pttl(key));
    }

    @Test public void nonStringInternalCounterPropagatesRedisError() {
        String n = b.name("wrongtype"), key = b.counterKey(n, "k");
        b.raw.rpush(key, "42"); b.raw.pexpire(key, 30000);
        JedisDataException failure = assertThrows(JedisDataException.class, () -> b.counter.get(n, "k"));
        assertTrue(failure.getMessage().startsWith("WRONGTYPE "));
    }

    @Test public void rejectedIncreaseRefreshTtlMustNotChangeCounter() {
        verifyRejectedRefreshTtlDoesNotChangeCounter(true);
    }

    @Test public void rejectedDecreaseRefreshTtlMustNotChangeCounter() {
        verifyRejectedRefreshTtlDoesNotChangeCounter(false);
    }

    private void verifyRejectedRefreshTtlDoesNotChangeCounter(boolean increase) {
        String n = b.name("ttl-limit"), key = b.counterKey(n, "k");
        Duration excessiveTtl = Duration.ofMillis(Long.MAX_VALUE);
        long[][] cases = {{10, 1}, {0, Long.MAX_VALUE},
                {increase ? -Long.MAX_VALUE : Long.MAX_VALUE, Long.MAX_VALUE},
                {increase ? Long.MIN_VALUE : Long.MAX_VALUE, 1}, {10, 9007199254740993L}};
        for (long[] change : cases) {
            String before = Long.toString(change[0]);
            b.raw.set(key, before, SetParams.setParams().px(30000));
            long ttlBefore = b.raw.pttl(key);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
                if (increase) b.counter.increaseAndRefreshTtl(n, "k", change[1], excessiveTtl);
                else b.counter.decreaseAndRefreshTtl(n, "k", change[1], excessiveTtl);
            });
            assertTrue(failure.getMessage().contains("must not exceed"));
            assertEquals("a rejected TTL must not change the counter", before, b.raw.get(key));
            long ttlAfter = b.raw.pttl(key);
            assertTrue("a rejected TTL must preserve the original deadline", ttlAfter > 0 && ttlAfter <= ttlBefore);
        }
    }

    @Test public void rejectedInitialTtlMustNotCreateCounter() {
        String n = b.name("initial-ttl-limit"), key = b.counterKey(n, "k");
        Duration excessiveTtl = Duration.ofMillis(Long.MAX_VALUE);
        for (int op = 0; op < 4; op++) {
            final int method = op;
            assertThrows(IllegalArgumentException.class, () -> {
                switch (method) {
                    case 0: b.counter.increase(n, "k", 1, excessiveTtl); break;
                    case 1: b.counter.decrease(n, "k", 1, excessiveTtl); break;
                    case 2: b.counter.increaseAndRefreshTtl(n, "k", 1, excessiveTtl); break;
                    default: b.counter.decreaseAndRefreshTtl(n, "k", 1, excessiveTtl);
                }
            });
            assertNull("a rejected TTL must not leave an initialized zero", b.raw.get(key));
        }
    }

    @Test public void fixedWindowRejectsTtlAboveMaximumWithoutMutation() {
        String n = b.name("unused-ttl"), key = b.counterKey(n, "k");
        b.counter.increase(n, "k", 10, 30);
        long ttlBefore = b.raw.pttl(key);
        Duration excessiveTtl = Duration.ofMillis(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> b.counter.increase(n, "k", 1, excessiveTtl));
        assertThrows(IllegalArgumentException.class, () -> b.counter.decrease(n, "k", 1, excessiveTtl));
        assertEquals("10", b.raw.get(key));
        long ttlAfter = b.raw.pttl(key);
        assertTrue(ttlAfter > 0 && ttlAfter <= ttlBefore);
    }

    @Test public void failedRefreshDoesNotAffectConcurrentIncrements() throws Exception {
        String n = b.name("ttl-failure-concurrency"), key = b.counterKey(n, "k");
        b.raw.set(key, "0", SetParams.setParams().px(120000));
        Duration excessiveTtl = Duration.ofMillis(Long.MAX_VALUE);
        Set<Long> results = ConcurrentHashMap.newKeySet();
        RegressionTestSupport.parallel(8, worker -> {
            Counter counter = worker % 2 == 0 ? b.counter : b.peerCounter;
            for (int i = 0; i < 200; i++) {
                if (worker < 4) {
                    assertTrue(results.add(counter.increase(n, "k", 1, 120)));
                } else {
                    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
                        if (worker % 2 == 0) counter.increaseAndRefreshTtl(n, "k", 1, excessiveTtl);
                        else counter.decreaseAndRefreshTtl(n, "k", 1, excessiveTtl);
                    });
                    assertTrue(failure.getMessage().contains("must not exceed"));
                }
            }
        });
        assertEquals("800", b.raw.get(key));
        assertEquals(800, results.size());
        for (long value = 1; value <= 800; value++) assertTrue(results.contains(value));
    }

    @Test public void existsExpireAndCounterNeverInvokeValueCodec() {
        AtomicInteger writes = new AtomicInteger(), reads = new AtomicInteger();
        RuntimeException sentinel = new RuntimeException("codec sentinel");
        RedisValueCodec codec = new RedisValueCodec() {
            @Override public byte[] serialize(Object value) { writes.incrementAndGet(); return new byte[] {1, 2}; }
            @Override public Object deserialize(byte[] value) { reads.incrementAndGet(); throw sentinel; }
        };
        try (RedisCache cache = new RedisCache(config().valueCodec(codec))) {
            String n = b.name("custom");
            cache.put(n, "k", "v", 30);
            assertTrue(cache.exists(n, "k"));
            assertTrue(cache.expire(n, "k", 30));
            Counter counter = cache.createCounter();
            assertEquals(1, counter.increase(n, "k", 1, 30));
            assertEquals(Long.valueOf(1), counter.get(n, "k"));
            assertEquals(1, writes.get()); assertEquals(0, reads.get());
            assertSame(sentinel, assertThrows(RuntimeException.class, () -> cache.get(n, "k")));
            cache.remove(n, "k"); assertFalse(cache.exists(n, "k"));
        }
    }

    @Test public void serializationFailurePreservesStoredBytesAndTtl() {
        RuntimeException sentinel = new RuntimeException("serialize sentinel");
        String n = b.name("serialize-fail");
        b.cache.put(n, "k", "original", 30);
        byte[] before = b.raw.get((n + ":k").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RedisValueCodec codec = new RedisValueCodec() {
            @Override public byte[] serialize(Object v) { throw sentinel; }
            @Override public Object deserialize(byte[] v) { return "unused"; }
        };
        try (RedisCache cache = new RedisCache(config().valueCodec(codec))) {
            assertSame(sentinel, assertThrows(RuntimeException.class, () -> cache.put(n, "k", "bad", 120)));
            assertArrayEquals(before, b.raw.get((n + ":k").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertTrue(b.raw.pttl(n + ":k") <= 30000);
        }
    }

    @Test public void closeIsIdempotentAndClosesSharedCounterClient() {
        RedisCache cache = new RedisCache(config());
        Counter counter = cache.createCounter();
        cache.put(b.name("close"), "k", "v", 30);
        cache.close(); cache.close();
        assertThrows(RuntimeException.class, () -> cache.put(b.name("close"), "k", "v", 30));
        assertThrows(RuntimeException.class, () -> counter.increase(b.name("close"), "k", 1, 30));
    }
}
