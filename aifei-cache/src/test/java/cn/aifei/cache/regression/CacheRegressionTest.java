package cn.aifei.cache.regression;

import cn.aifei.cache.Cache;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Expected behavior comes only from docs/design.md and Cache's public contract. */
@RunWith(Parameterized.class)
public class CacheRegressionTest {
    @Parameterized.Parameters(name="{0}") public static Object[] implementations() {
        return new Object[] {"caffeine", "redis-resp2", "redis-resp3"};
    }
    @Parameterized.Parameter public String kind;
    private RegressionTestSupport.Backend b;
    private Cache c;
    private String n;
    @Before public void open() { b = new RegressionTestSupport.Backend(kind); c = b.cache; n = b.name("cache"); }
    @After public void close() throws Exception { if (b != null) b.close(); }

    @Test public void roundTripValuesAndOverwrite() {
        List<Object> values = Arrays.asList("中文🙂\u0000", 0, Long.MIN_VALUE, Long.MAX_VALUE,
                false, Double.MAX_VALUE, Duration.ofNanos(123456789), Arrays.asList("a", "b"),
                Collections.singletonMap("k", 42));
        for (Object value : values) {
            c.put(n, "key", value, 30);
            assertEquals(value, b.peerCache.get(n, "key"));
            assertTrue(c.exists(n, "key"));
        }
        byte[] bytes = new byte[1024 * 1024];
        new Random(9173).nextBytes(bytes);
        c.put(n, "binary", bytes, 30);
        assertArrayEquals(bytes, b.peerCache.get(n, "binary"));
        c.remove(n, "key");
        assertNull(c.get(n, "key"));
        assertFalse(c.exists(n, "key"));
        c.remove(n, "missing");
    }

    @Test public void invalidReadsAndDeletesAreHarmless() {
        c.put(n, "valid", "keep", 30);
        for (String invalid : new String[] {null, "", " ", "\t\r\n"}) {
            assertNull(c.get(invalid, "valid")); assertNull(c.get(n, invalid));
            assertFalse(c.exists(invalid, "valid")); assertFalse(c.exists(n, invalid));
            c.remove(invalid, "valid"); c.remove(n, invalid);
        }
        assertEquals("keep", c.get(n, "valid"));
    }

    @Test public void invalidWritesValidateBeforeLoaderOrMutation() {
        for (String invalid : new String[] {null, "", " \t"}) {
            for (String[] pair : new String[][] {{invalid, "k"}, {n, invalid}}) {
                assertThrows(IllegalArgumentException.class, () -> c.put(pair[0], pair[1], "v", 30));
                assertThrows(IllegalArgumentException.class, () -> c.putIfAbsent(pair[0], pair[1], "v", 30));
                assertThrows(IllegalArgumentException.class, () -> c.expire(pair[0], pair[1], 30));
                assertThrows(IllegalArgumentException.class, () -> c.get(pair[0], pair[1], 30,
                        () -> { throw new AssertionError("loader ran"); }));
            }
            assertThrows(IllegalArgumentException.class, () -> c.clear(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> c.put(n, "k", null, 30));
        assertThrows(IllegalArgumentException.class, () -> c.putIfAbsent(n, "k", null, 30));
        assertThrows(IllegalArgumentException.class, () -> c.get(n, "k", 30, null));
    }

    @Test public void ttlValidationCoversHitMissAndSecondsOverloads() {
        c.put(n, "hit", "original", 30);
        for (String key : Arrays.asList("hit", "miss")) {
            for (Duration invalid : Arrays.asList(null, Duration.ZERO, Duration.ofMillis(-1),
                    Duration.ofNanos(999999), Duration.ofNanos(-1), Duration.ofSeconds(Long.MIN_VALUE),
                    Duration.ofSeconds(Integer.MAX_VALUE).plusNanos(1),
                    Duration.ofSeconds(Integer.MAX_VALUE).plusMillis(1),
                    Duration.ofSeconds((long) Integer.MAX_VALUE + 1),
                    Duration.ofMillis(Long.MAX_VALUE - 1), Duration.ofMillis(Long.MAX_VALUE),
                    Duration.ofSeconds(Long.MAX_VALUE))) {
                assertThrows(IllegalArgumentException.class, () -> c.put(n, key, "v", invalid));
                assertThrows(IllegalArgumentException.class, () -> c.putIfAbsent(n, key, "v", invalid));
                assertThrows(IllegalArgumentException.class, () -> c.expire(n, key, invalid));
                assertThrows(IllegalArgumentException.class, () -> c.get(n, key, invalid,
                        () -> { throw new AssertionError("loader ran"); }));
            }
            for (int seconds : new int[] {0, -1, Integer.MIN_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> c.put(n, key, "v", seconds));
                assertThrows(IllegalArgumentException.class, () -> c.putIfAbsent(n, key, "v", seconds));
                assertThrows(IllegalArgumentException.class, () -> c.expire(n, key, seconds));
                assertThrows(IllegalArgumentException.class, () -> c.get(n, key, seconds, () -> "bad"));
            }
        }
        assertEquals("original", c.get(n, "hit"));
        assertNull(c.get(n, "miss"));
        c.put(n, "long", "v", Integer.MAX_VALUE);
        assertEquals("v", c.get(n, "long"));
    }

    @Test public void maximumTtlWorksForEveryCacheOperationAndSecondsOverload() {
        Duration maximum = Duration.ofSeconds(Integer.MAX_VALUE);
        for (boolean seconds : new boolean[] {false, true}) {
            String prefix = seconds ? "seconds-" : "duration-";
            c.put(n, prefix + "expire", "v", 30);
            if (seconds) {
                c.put(n, prefix + "put", "v", Integer.MAX_VALUE);
                assertTrue(c.putIfAbsent(n, prefix + "absent", "v", Integer.MAX_VALUE));
                assertTrue(c.expire(n, prefix + "expire", Integer.MAX_VALUE));
                assertEquals("v", c.get(n, prefix + "load", Integer.MAX_VALUE, () -> "v"));
            } else {
                c.put(n, prefix + "put", "v", maximum);
                assertTrue(c.putIfAbsent(n, prefix + "absent", "v", maximum));
                assertTrue(c.expire(n, prefix + "expire", maximum));
                assertEquals("v", c.get(n, prefix + "load", maximum, () -> "v"));
            }
            for (String suffix : Arrays.asList("put", "absent", "expire", "load")) {
                String key = prefix + suffix;
                assertEquals("v", b.peerCache.get(n, key));
                if (b.raw != null) {
                    long ttl = b.raw.pttl(n + ":" + key);
                    assertTrue("TTL=" + ttl, ttl > maximum.toMillis() - 5000 && ttl <= maximum.toMillis());
                }
            }
        }
        c.put(n, "below-maximum", "v", maximum.minusMillis(1));
        assertEquals("v", c.get(n, "below-maximum"));
    }

    @Test public void loaderNullExceptionsAndHit() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals("loaded", c.get(n, "k", 30, () -> { calls.incrementAndGet(); return "loaded"; }));
        assertEquals("loaded", c.get(n, "k", 30, () -> { throw new AssertionError("hit called loader"); }));
        assertEquals(1, calls.get());
        assertNull(c.get(n, "null", 30, () -> null));
        assertFalse(c.exists(n, "null"));
        RuntimeException expected = new RuntimeException("loader sentinel");
        assertSame(expected, assertThrows(RuntimeException.class, () -> c.get(n, "throws", 30, () -> { throw expected; })));
        assertNull(c.get(n, "throws"));
    }

    @Test public void readsAndFailedPutIfAbsentDoNotRefreshTtl() throws Exception {
        c.put(n, "k", "v", Duration.ofMillis(300));
        Thread.sleep(100);
        assertEquals("v", c.get(n, "k"));
        assertTrue(c.exists(n, "k"));
        assertFalse(c.putIfAbsent(n, "k", "bad", 30));
        assertEquals("v", c.get(n, "k", 30, () -> "bad"));
        Thread.sleep(300);
        assertNull(c.get(n, "k"));
        assertFalse(c.exists(n, "k"));
        assertTrue(c.putIfAbsent(n, "k", "new", 30));
        assertEquals("new", c.get(n, "k"));
    }

    @Test public void overwriteResetsTtlAndEntriesExpireIndependently() throws Exception {
        c.put(n, "short", "old", Duration.ofMillis(150));
        c.put(n, "other", "v", Duration.ofMillis(150));
        c.put(n, "short", "new", 30);
        Thread.sleep(250);
        assertNull(c.get(n, "other"));
        assertEquals("new", c.get(n, "short"));
    }

    @Test public void expireExtendsShortensAndDoesNotCreate() throws Exception {
        assertFalse(c.expire(n, "missing", 30));
        c.put(n, "k", "v", Duration.ofMillis(200));
        assertTrue(c.expire(n, "k", 30));
        Thread.sleep(300);
        assertEquals("v", c.get(n, "k"));
        assertTrue(c.expire(n, "k", Duration.ofMillis(80)));
        Thread.sleep(180);
        assertFalse(c.expire(n, "k", 30));
        assertNull(c.get(n, "k"));
    }

    @Test public void conditionalWriteHasOneWinnerAcrossClients() throws Exception {
        AtomicInteger winners = new AtomicInteger();
        RegressionTestSupport.parallel(16, worker -> {
            Cache target = worker % 2 == 0 ? c : b.peerCache;
            if (target.putIfAbsent(n, "race", worker, 30)) winners.incrementAndGet();
        });
        assertEquals(1, winners.get());
        assertNotNull(c.get(n, "race"));
    }

    @Test public void clearOnlySelectedHierarchyAndEscapesGlob() {
        for (String suffix : Arrays.asList("a", "a*", "a?", "a[bc]", "a\\", "a]", "a\u0000", "缓存🙂", "a:")) {
            String root = b.name("glob:" + suffix);
            c.put(root, "k:child", "root", 30);
            c.put(root + ":child", "k", "child", 30);
            c.put(root + "sibling", "k", "sibling", 30);
            c.put(b.name("control"), "k", "control", 30);
            c.clear(root);
            assertNull(root, c.get(root, "k:child"));
            assertNull(root, c.get(root + ":child", "k"));
            assertEquals("sibling", c.get(root + "sibling", "k"));
            assertEquals("control", c.get(b.name("control"), "k"));
        }
    }

    @Test public void clearMultipleScanPages() {
        for (int i = 0; i < 2400; i++) c.put(n + ":child", "k" + i, i, 60);
        c.put(n + "Sibling", "keep", "v", 60);
        c.clear(n);
        for (int i = 0; i < 2400; i++) assertFalse("key " + i, c.exists(n + ":child", "k" + i));
        assertEquals("v", c.get(n + "Sibling", "keep"));
    }

    @Test public void randomOperationSequenceAgainstIndependentModel() {
        Random random = new Random(918234);
        Map<String, Integer> model = new HashMap<>();
        for (int i = 0; i < 2500; i++) {
            String name = n + ":" + random.nextInt(6), key = "k" + random.nextInt(30), id = name + "/" + key;
            int value = random.nextInt();
            switch (random.nextInt(7)) {
                case 0: c.put(name, key, value, 120); model.put(id, value); break;
                case 1: assertEquals(!model.containsKey(id), c.putIfAbsent(name, key, value, 120));
                    model.putIfAbsent(id, value); break;
                case 2: c.remove(name, key); model.remove(id); break;
                case 3: assertEquals(model.get(id), c.get(name, key)); break;
                case 4: assertEquals(model.containsKey(id), c.expire(name, key, 120)); break;
                case 5: assertEquals(model.containsKey(id), c.exists(name, key)); break;
                default: c.clear(name); model.keySet().removeIf(k -> k.startsWith(name + "/"));
            }
        }
        for (Map.Entry<String, Integer> e : model.entrySet()) {
            String[] pair = e.getKey().split("/");
            assertEquals(e.getValue(), c.get(pair[0], pair[1]));
        }
    }
}
