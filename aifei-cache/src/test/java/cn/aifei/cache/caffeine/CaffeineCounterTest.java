package cn.aifei.cache.caffeine;

import cn.aifei.cache.Counter;
import cn.aifei.cache.CounterContractTest;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 验证 Caffeine 计数实现。
 */
public class CaffeineCounterTest extends CounterContractTest {

    private FakeTicker ticker;

    /**
     * 创建使用测试时钟的计数器。
     */
    @Override
    protected Counter createCounter() {
        ticker = new FakeTicker();
        return new CaffeineCounter(CaffeineCounter.DEFAULT_MAXIMUM_SIZE, ticker);
    }

    /**
     * 推进测试时钟。
     */
    @Override
    protected void advanceTime(Duration duration) {
        ticker.advance(duration);
    }

    /**
     * 验证容量上限必须大于零。
     */
    @Test
    public void shouldRejectNonPositiveMaximumSize() {
        assertThrows(IllegalArgumentException.class, () -> new CaffeineCounter(0));
        assertThrows(IllegalArgumentException.class, () -> new CaffeineCounter(-1));
    }

    /**
     * 验证默认容量上限。
     */
    @Test
    public void shouldExposeExpectedDefaultMaximumSize() {
        assertEquals(10_000L, CaffeineCounter.DEFAULT_MAXIMUM_SIZE);
    }

    /**
     * 验证缓存条目不超过配置上限。
     */
    @Test
    public void shouldEnforceConfiguredMaximumSize() throws InterruptedException {
        CaffeineCounter boundedCounter = new CaffeineCounter(1, ticker);
        String counterName = counterName("bounded");

        boundedCounter.increase(counterName, "1", 1L, Duration.ofMinutes(1));
        boundedCounter.increase(counterName, "2", 1L, Duration.ofMinutes(1));

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        int presentEntries;
        do {
            presentEntries = countPresentEntries(boundedCounter, counterName);
            if (presentEntries <= 1) {
                break;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);

        assertEquals(1, presentEntries);
    }

    /**
     * 验证同 key 并发加减不会丢更新。
     */
    @Test
    public void shouldUpdateCounterAtomicallyUnderConcurrency() throws InterruptedException {
        final int threads = 8;
        final int iterations = 1_000;
        final String counterName = counterName("concurrent");
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        counter.increase(counterName, "views", 1L, Duration.ofMinutes(1));
                        counter.decrease(counterName, "views", 1L, Duration.ofMinutes(1));
                        counter.increase(counterName, "views", 1L, Duration.ofMinutes(1));
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("Concurrent counter update failed", failure.get());
        }
        assertEquals(Long.valueOf((long) threads * iterations), counter.get(counterName, "views"));
    }

    /**
     * 统计当前仍可读取的测试计数项。
     */
    private static int countPresentEntries(CaffeineCounter counter, String counterName) {
        int count = 0;
        if (counter.get(counterName, "1") != null) {
            count++;
        }
        if (counter.get(counterName, "2") != null) {
            count++;
        }
        return count;
    }

    /**
     * 可手动推进的测试时钟。
     */
    private static final class FakeTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        /**
         * 返回当前纳秒时间。
         */
        @Override
        public long read() {
            return nanos.get();
        }

        /**
         * 推进指定时长。
         */
        private void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
