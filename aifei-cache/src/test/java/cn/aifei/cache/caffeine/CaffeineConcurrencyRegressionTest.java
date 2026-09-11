package cn.aifei.cache.caffeine;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Real Caffeine with its package-private ticker seam; no fake cache or mocked expiration policy. */
public class CaffeineConcurrencyRegressionTest {
    private static final Duration HUNDRED_MS = Duration.ofMillis(100);

    private static final class Clock implements Ticker {
        final AtomicLong now = new AtomicLong(TimeUnit.SECONDS.toNanos(1));
        volatile Thread owner;
        volatile String gatedMethod;
        volatile boolean advanceAfterValueRead;
        volatile long advanceMillis = 50;
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        void millis(long millis) { now.set(TimeUnit.SECONDS.toNanos(1) + TimeUnit.MILLISECONDS.toNanos(millis)); }
        private boolean inMethod(String method) {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                if (frame.getClassName().startsWith("com.github.benmanes.caffeine.cache.BoundedLocalCache")
                        && frame.getMethodName().equals(method)) return true;
            }
            return false;
        }
        @Override public long read() {
            long result = now.get();
            if (Thread.currentThread() == owner) {
                if (advanceAfterValueRead && inMethod("getIfPresent")) {
                    advanceAfterValueRead = false;
                    now.addAndGet(TimeUnit.MILLISECONDS.toNanos(advanceMillis));
                    return result; // pause after reading the old value, before writing it back
                }
                String method = gatedMethod;
                if (method != null && inMethod(method)) {
                    gatedMethod = null;
                    reached.countDown();
                    try {
                        if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("gate timed out");
                    } catch (InterruptedException e) { throw new AssertionError(e); }
                    return now.get();
                }
            }
            return result;
        }
        void awaitGate() throws Exception { assertTrue("did not reach scheduled interleaving", reached.await(5, TimeUnit.SECONDS)); }
    }

    @Test public void exactMillisecondBoundaryAndTruncation() {
        Clock time = new Clock();
        CaffeineCache cache = new CaffeineCache(1000, time);
        cache.put("n", "k", "v", Duration.ofNanos(1999999));
        time.now.addAndGet(999999);
        assertEquals("v", cache.get("n", "k"));
        time.now.incrementAndGet();
        assertNull(cache.get("n", "k"));
        assertFalse(cache.expire("n", "k", 30));
        assertTrue(cache.putIfAbsent("n", "k", "new", 30));
    }

    @Test public void readsAndConditionalWritesDoNotShiftDeadline() {
        Clock time = new Clock();
        CaffeineCache cache = new CaffeineCache(1000, time);
        cache.put("n", "k", "v", HUNDRED_MS);
        for (int ms = 1; ms < 100; ms++) {
            time.millis(ms);
            assertEquals("v", cache.get("n", "k", 30, () -> "bad"));
            assertTrue(cache.exists("n", "k"));
            assertFalse(cache.putIfAbsent("n", "k", "bad", 30));
        }
        time.millis(100);
        assertNull(cache.get("n", "k"));
    }

    @Test public void counterFixedWindowMustNotRebasePreviouslySampledRemainingTtl() {
        Clock time = new Clock();
        CaffeineCounter counter = new CaffeineCounter(1000, time);
        counter.increase("n", "k", 1, HUNDRED_MS);
        time.owner = Thread.currentThread();
        time.advanceAfterValueRead = true;
        assertEquals(2, counter.increase("n", "k", 1, 30));
        assertFalse("the scheduled pause did not execute", time.advanceAfterValueRead);
        time.millis(110);
        assertNull("fixed deadline was 100 ms; update must not extend it to 150 ms", counter.get("n", "k"));
    }

    @Test public void counterExpiredBetweenReadAndReplaceStartsFromZero() {
        Clock time = new Clock();
        CaffeineCounter counter = new CaffeineCounter(1000, time);
        counter.increase("n", "k", 10, HUNDRED_MS);
        time.owner = Thread.currentThread();
        time.advanceMillis = 150;
        time.advanceAfterValueRead = true;
        assertEquals(1, counter.increase("n", "k", 1, 30));
        assertFalse("the scheduled pause did not execute", time.advanceAfterValueRead);
        assertEquals(Long.valueOf(1), counter.get("n", "k"));
        time.millis(30150);
        assertNull(counter.get("n", "k"));
    }

    @Test public void counterRefreshUsesCurrentTime() {
        Clock time = new Clock();
        CaffeineCounter counter = new CaffeineCounter(1000, time);
        counter.increase("n", "k", 1, HUNDRED_MS);
        time.millis(80);
        counter.increaseAndRefreshTtl("n", "k", 1, HUNDRED_MS);
        time.millis(179);
        assertEquals(Long.valueOf(2), counter.get("n", "k"));
        time.millis(180);
        assertNull(counter.get("n", "k"));
    }

    @Test public void counterRemoveAndIncreaseMustHaveAValidAtomicOrdering() throws Exception {
        Clock time = new Clock();
        CaffeineCounter counter = new CaffeineCounter(1000, time);
        counter.increase("n", "k", 10, 30);
        ExecutorService worker = Executors.newFixedThreadPool(2);
        try {
            Future<Long> result = worker.submit(() -> {
                time.owner = Thread.currentThread();
                time.gatedMethod = "getIfPresent";
                return counter.increase("n", "k", 1, 30);
            });
            time.awaitGate();
            Future<?> removal = worker.submit(() -> counter.remove("n", "k"));
            allowConcurrentCallToRun(removal);
            time.resume.countDown();
            long updated = result.get(5, TimeUnit.SECONDS);
            removal.get(5, TimeUnit.SECONDS);
            Long after = counter.get("n", "k");
            // increase -> remove: result 11 and absent; remove -> increase: result 1 and value 1.
            assertTrue("no atomic ordering: increase returned " + updated + ", final value=" + after,
                    (updated == 11 && after == null) || (updated == 1 && Long.valueOf(1).equals(after)));
        } finally { time.resume.countDown(); worker.shutdownNow(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test public void expireMustNotResurrectEntryObservedExpiredDuringCall() throws Exception {
        Clock time = new Clock();
        CaffeineCache cache = new CaffeineCache(1000, time);
        cache.put("n", "k", "v", HUNDRED_MS);
        ExecutorService worker = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> result = worker.submit(() -> {
                time.owner = Thread.currentThread();
                time.gatedMethod = "setExpiresAfter";
                return cache.expire("n", "k", 30);
            });
            time.awaitGate();
            time.millis(110);
            Future<String> reading = worker.submit(() -> cache.get("n", "k"));
            allowConcurrentCallToRun(reading);
            time.resume.countDown();
            boolean renewed = result.get(5, TimeUnit.SECONDS);
            String observed = reading.get(5, TimeUnit.SECONDS);
            if (renewed) {
                assertEquals("a successful renewal cannot be preceded by an observed miss", "v", observed);
                assertEquals("v", cache.get("n", "k"));
            } else {
                assertNull(observed);
                assertNull(cache.get("n", "k"));
            }
        } finally { time.resume.countDown(); worker.shutdownNow(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    private static void allowConcurrentCallToRun(Future<?> call) throws Exception {
        try {
            call.get(200, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expected) {
            // Waiting for the same key's lock is a valid atomic implementation.
        }
    }

    @Test public void expiredCounterRestartsInsteadOfUsingOldValue() {
        Clock time = new Clock();
        CaffeineCounter counter = new CaffeineCounter(1000, time);
        counter.increase("n", "k", Long.MAX_VALUE, HUNDRED_MS);
        time.millis(100);
        assertEquals(1, counter.increase("n", "k", 1, 30));
        time.millis(30100);
        assertEquals(-1, counter.decreaseAndRefreshTtl("n", "k", 1, 30));
    }

    @Test public void invalidMaximumSizeIsRejected() {
        for (long invalid : new long[] {0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CaffeineCache(invalid));
            assertThrows(IllegalArgumentException.class, () -> new CaffeineCounter(invalid));
        }
    }

    @Test public void configuredCapacityAlsoAppliesToAutomaticallyCreatedCounter() throws Exception {
        CaffeineCache cache = new CaffeineCache(5);
        cn.aifei.cache.Counter counter = cache.createCounter();
        for (int i = 0; i < 100; i++) {
            cache.put("n", "k" + i, i, 30);
            counter.increase("n", "k" + i, 1, 30);
        }
        // Explicitly finish Caffeine's asynchronous maintenance before observing the capacity.
        for (Object[] pair : new Object[][] {{cache, "cache"}, {counter, "counters"}}) {
            java.lang.reflect.Field field = pair[0].getClass().getDeclaredField((String) pair[1]);
            field.setAccessible(true);
            ((com.github.benmanes.caffeine.cache.Cache<?, ?>) field.get(pair[0])).cleanUp();
        }
        int cached = 0, counted = 0;
        for (int i = 0; i < 100; i++) {
            if (cache.get("n", "k" + i) != null) cached++;
            if (counter.get("n", "k" + i) != null) counted++;
        }
        assertTrue("cache retained " + cached, cached > 0 && cached <= 5);
        assertTrue("counter retained " + counted, counted > 0 && counted <= 5);
    }
}
