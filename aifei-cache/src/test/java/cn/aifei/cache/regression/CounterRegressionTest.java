package cn.aifei.cache.regression;

import cn.aifei.cache.Counter;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CounterRegressionTest {
    @Parameterized.Parameters(name="{0}") public static Object[] implementations() {
        return new Object[] {"caffeine", "redis-resp2", "redis-resp3"};
    }
    @Parameterized.Parameter public String kind;
    private RegressionTestSupport.Backend b;
    private Counter c;
    private String n;
    @Before public void open() { b = new RegressionTestSupport.Backend(kind); c = b.counter; n = b.name("counter"); }
    @After public void close() throws Exception { if (b != null) b.close(); }

    private long change(int op, String name, String key, long step, Duration ttl) {
        switch (op) {
            case 0: return c.increase(name, key, step, ttl);
            case 1: return c.decrease(name, key, step, ttl);
            case 2: return c.increaseAndRefreshTtl(name, key, step, ttl);
            default: return c.decreaseAndRefreshTtl(name, key, step, ttl);
        }
    }

    @Test public void signedArithmeticAndRemove() {
        assertNull(c.get(n, "k"));
        assertEquals(3, c.increase(n, "k", 3, 30));
        assertEquals(-4, c.decrease(n, "k", 7, 30));
        assertEquals(0, c.increaseAndRefreshTtl(n, "k", 4, 30));
        assertEquals(-9, c.decreaseAndRefreshTtl(n, "k", 9, 30));
        assertEquals(Long.valueOf(-9), b.peerCounter.get(n, "k"));
        c.remove(n, "k");
        assertNull(c.get(n, "k"));
        assertEquals(-3, c.decrease(n, "k", 3, 30));
        c.remove(n, "missing");
    }

    @Test public void invalidReadsAndDeletesDoNotTouchValidState() {
        c.increase(n, "k", 7, 30);
        for (String invalid : new String[] {null, "", " ", "\t\r\n"}) {
            assertNull(c.get(invalid, "k")); assertNull(c.get(n, invalid));
            c.remove(invalid, "k"); c.remove(n, invalid);
        }
        assertEquals(Long.valueOf(7), c.get(n, "k"));
    }

    @Test public void allMutatorsRejectInvalidArgumentsOnHitAndMiss() {
        c.increase(n, "hit", 10, 30);
        for (int op = 0; op < 4; op++) {
            final int method = op;
            for (String key : Arrays.asList("hit", "miss")) {
                for (long step : new long[] {0, -1, Long.MIN_VALUE})
                    assertThrows(IllegalArgumentException.class, () -> change(method, n, key, step, Duration.ofSeconds(30)));
                for (Duration ttl : Arrays.asList(null, Duration.ZERO, Duration.ofNanos(999999), Duration.ofMillis(-1),
                        Duration.ofSeconds(Long.MIN_VALUE),
                        Duration.ofSeconds(Integer.MAX_VALUE).plusNanos(1),
                        Duration.ofSeconds(Integer.MAX_VALUE).plusMillis(1),
                        Duration.ofSeconds((long) Integer.MAX_VALUE + 1),
                        Duration.ofMillis(Long.MAX_VALUE - 1), Duration.ofMillis(Long.MAX_VALUE),
                        Duration.ofSeconds(Long.MAX_VALUE)))
                    assertThrows(IllegalArgumentException.class, () -> change(method, n, key, 1, ttl));
                for (String invalid : new String[] {null, "", " \t"}) {
                    assertThrows(IllegalArgumentException.class, () -> change(method, invalid, key, 1, Duration.ofSeconds(30)));
                    assertThrows(IllegalArgumentException.class, () -> change(method, n, invalid, 1, Duration.ofSeconds(30)));
                }
            }
        }
        for (int seconds : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> c.increase(n, "hit", 1, seconds));
            assertThrows(IllegalArgumentException.class, () -> c.decrease(n, "hit", 1, seconds));
            assertThrows(IllegalArgumentException.class, () -> c.increaseAndRefreshTtl(n, "hit", 1, seconds));
            assertThrows(IllegalArgumentException.class, () -> c.decreaseAndRefreshTtl(n, "hit", 1, seconds));
        }
        assertEquals(Long.valueOf(10), c.get(n, "hit"));
        assertNull(c.get(n, "miss"));
    }

    @Test public void maximumTtlWorksForEveryCounterOperationAndSecondsOverload() {
        Duration maximum = Duration.ofSeconds(Integer.MAX_VALUE);
        for (int op = 0; op < 4; op++) {
            String key = "duration-" + op;
            long direction = op % 2 == 0 ? 1 : -1;
            assertEquals(direction, change(op, n, key, 1, maximum));
            assertEquals(direction * 2, change(op, n, key, 1, maximum));
            assertEquals(Long.valueOf(direction * 2), b.peerCounter.get(n, key));
        }
        assertEquals(1, c.increase(n, "seconds-0", 1, Integer.MAX_VALUE));
        assertEquals(-1, c.decrease(n, "seconds-1", 1, Integer.MAX_VALUE));
        assertEquals(1, c.increaseAndRefreshTtl(n, "seconds-2", 1, Integer.MAX_VALUE));
        assertEquals(-1, c.decreaseAndRefreshTtl(n, "seconds-3", 1, Integer.MAX_VALUE));
        if (b.raw != null) {
            for (String prefix : Arrays.asList("duration-", "seconds-")) {
                for (int op = 0; op < 4; op++) {
                    long ttl = b.raw.pttl(b.counterKey(n, prefix + op));
                    assertTrue("TTL=" + ttl, ttl > maximum.toMillis() - 5000 && ttl <= maximum.toMillis());
                }
            }
        }
        assertEquals(1, c.increase(n, "below-maximum", 1, maximum.minusMillis(1)));
    }

    @Test public void largeIntegersStayExactAndOverflowPreservesValue() {
        long[] steps = {9007199254740991L, 9007199254740992L, 9007199254740993L, Long.MAX_VALUE};
        for (long step : steps) {
            c.remove(n, "k");
            assertEquals(step, c.increase(n, "k", step, 30));
            assertEquals(Long.valueOf(step), c.get(n, "k"));
            assertEquals(0, c.decrease(n, "k", step, 30));
        }
        for (int op = 0; op < 4; op++) {
            c.remove(n, "k");
            final int method = op;
            long limit = op % 2 == 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
            if (limit > 0) c.increase(n, "k", Long.MAX_VALUE, 30);
            else { c.decrease(n, "k", Long.MAX_VALUE, 30); c.decrease(n, "k", 1, 30); }
            assertThrows(ArithmeticException.class, () -> change(method, n, "k", 1, Duration.ofSeconds(60)));
            assertEquals(Long.valueOf(limit), c.get(n, "k"));
        }
    }

    @Test public void overflowDoesNotRefreshExpiration() throws Exception {
        c.increase(n, "k", Long.MAX_VALUE, Duration.ofMillis(200));
        assertThrows(ArithmeticException.class, () -> c.increaseAndRefreshTtl(n, "k", 1, 30));
        Thread.sleep(300);
        assertNull(c.get(n, "k"));
    }

    @Test public void fixedWindowDoesNotRefreshAndRestartsAtZero() throws Exception {
        for (int op = 0; op < 2; op++) change(op, n, "k" + op, 7, Duration.ofMillis(300));
        Thread.sleep(100);
        for (int op = 0; op < 2; op++) assertEquals(op == 0 ? 9 : -9, change(op, n, "k" + op, 2, Duration.ofSeconds(30)));
        Thread.sleep(300);
        for (int op = 0; op < 2; op++) {
            assertNull(c.get(n, "k" + op));
            assertEquals(op == 0 ? 2 : -2, change(op, n, "k" + op, 2, Duration.ofSeconds(30)));
        }
    }

    @Test public void refreshMethodsExtendAndShortenWindow() throws Exception {
        c.increaseAndRefreshTtl(n, "up", 2, Duration.ofMillis(200));
        c.decreaseAndRefreshTtl(n, "down", 2, Duration.ofMillis(200));
        assertEquals(3, c.increaseAndRefreshTtl(n, "up", 1, 30));
        assertEquals(-3, c.decreaseAndRefreshTtl(n, "down", 1, 30));
        Thread.sleep(300);
        assertEquals(Long.valueOf(3), c.get(n, "up"));
        assertEquals(Long.valueOf(-3), c.get(n, "down"));
        c.increaseAndRefreshTtl(n, "up", 1, Duration.ofMillis(80));
        c.decreaseAndRefreshTtl(n, "down", 1, Duration.ofMillis(80));
        Thread.sleep(180);
        assertNull(c.get(n, "up")); assertNull(c.get(n, "down"));
    }

    @Test public void concurrentIncrementsReturnEverySequenceNumberOnce() throws Exception {
        Set<Long> results = ConcurrentHashMap.newKeySet();
        RegressionTestSupport.parallel(12, worker -> {
            Counter target = worker % 2 == 0 ? c : b.peerCounter;
            for (int i = 0; i < 500; i++) {
                long value = i % 2 == 0 ? target.increase(n, "k", 1, 120)
                        : target.increaseAndRefreshTtl(n, "k", 1, 120);
                assertTrue("duplicate result " + value, results.add(value));
            }
        });
        assertEquals(6000, results.size());
        assertEquals(Long.valueOf(6000), c.get(n, "k"));
        for (long i = 1; i <= 6000; i++) assertTrue(results.contains(i));
    }

    @Test public void concurrentMixedDirectionsAndHashCollisions() throws Exception {
        RegressionTestSupport.parallel(12, worker -> {
            Counter target = worker % 2 == 0 ? c : b.peerCounter;
            for (int i = 0; i < 500; i++) {
                String key = i % 2 == 0 ? "Aa" : "BB"; // JDK String hash collision.
                if (worker % 2 == 0) target.increase(n, key, 3, 120);
                else target.decreaseAndRefreshTtl(n, key, 3, 120);
            }
        });
        assertEquals(Long.valueOf(0), c.get(n, "Aa"));
        assertEquals(Long.valueOf(0), c.get(n, "BB"));
    }

    @Test public void randomArithmeticUsesBigIntegerAsIndependentOracle() {
        Random random = new Random(20260909);
        Map<String, BigInteger> model = new HashMap<>();
        for (int i = 0; i < 3000; i++) {
            String key = "k" + random.nextInt(12);
            if (random.nextInt(12) == 0) { c.remove(n, key); model.remove(key); continue; }
            int op = random.nextInt(4);
            long step = random.nextInt(5) == 0 ? Long.MAX_VALUE : (random.nextLong() & Long.MAX_VALUE);
            if (step == 0) step = 1;
            BigInteger before = model.getOrDefault(key, BigInteger.ZERO);
            BigInteger delta = BigInteger.valueOf(step);
            BigInteger after = op % 2 == 0 ? before.add(delta) : before.subtract(delta);
            final long validStep = step;
            if (after.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0 || after.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                assertThrows(ArithmeticException.class, () -> change(op, n, key, validStep, Duration.ofSeconds(120)));
            } else {
                assertEquals(after.longValueExact(), change(op, n, key, step, Duration.ofSeconds(120)));
                model.put(key, after);
            }
            assertEquals(model.containsKey(key) ? Long.valueOf(model.get(key).longValueExact()) : null, c.get(n, key));
        }
    }

    @Test public void cacheAndCounterAreIndependent() {
        b.cache.put(n, "k", "cache", 30);
        c.increase(n, "k", 11, 30);
        assertEquals("cache", b.cache.get(n, "k"));
        b.cache.clear(n);
        assertEquals(Long.valueOf(11), c.get(n, "k"));
        b.cache.put(n, "k", "cache", 30);
        c.remove(n, "k");
        assertEquals("cache", b.cache.get(n, "k"));
    }

}
