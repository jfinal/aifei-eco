package cn.aifei.cache.caffeine;

import cn.aifei.cache.Cache;
import cn.aifei.cache.CacheContractTest;
import cn.aifei.cache.Counter;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/**
 * 验证 Caffeine 缓存实现。
 */
public class CaffeineCacheTest extends CacheContractTest {

    private FakeTicker ticker;

    /**
     * 创建使用测试时钟的缓存。
     */
    @Override
    protected Cache createCache() {
        ticker = new FakeTicker();
        return new CaffeineCache(CaffeineCache.DEFAULT_MAXIMUM_SIZE, ticker);
    }

    /**
     * 推进测试时钟。
     */
    @Override
    protected void advanceTime(Duration duration) {
        ticker.advance(duration);
    }

    /**
     * 验证缓存直接保存对象引用。
     */
    @Test
    public void shouldKeepObjectReferenceWithoutSerializationCopy() {
        MutableValue value = new MutableValue("old");

        cache.put("values", "1", value, Duration.ofMinutes(1));
        value.text = "new";

        assertSame(value, cache.get("values", "1"));
    }

    /**
     * 验证缓存条目不超过配置上限。
     */
    @Test
    public void shouldEnforceConfiguredMaximumSize() throws InterruptedException {
        CaffeineCache boundedCache = new CaffeineCache(1, ticker);

        boundedCache.put("values", "1", "one", Duration.ofMinutes(1));
        boundedCache.put("values", "2", "two", Duration.ofMinutes(1));

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        int presentEntries;
        do {
            presentEntries = countPresentEntries(boundedCache);
            if (presentEntries <= 1) {
                break;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);

        assertEquals(1, presentEntries);
    }

    /**
     * 验证缓存创建的计数器复用相同 Caffeine 配置。
     */
    @Test
    public void shouldCreateCounterWithSameCaffeineSettings() throws InterruptedException {
        FakeTicker counterTicker = new FakeTicker();
        CaffeineCache boundedCache = new CaffeineCache(1, counterTicker);
        Counter counter = boundedCache.createCounter();

        counter.increase("derived", "1", 1L, Duration.ofMillis(10));
        counter.increase("derived", "2", 1L, Duration.ofMillis(10));

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        int presentEntries;
        do {
            presentEntries = countPresentCounterEntries(counter);
            if (presentEntries <= 1) {
                break;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);

        assertEquals(1, presentEntries);

        counterTicker.advance(Duration.ofMillis(11));

        assertNull(counter.get("derived", "1"));
        assertNull(counter.get("derived", "2"));
    }

    /**
     * 统计当前仍可读取的测试条目。
     */
    private static int countPresentEntries(CaffeineCache cache) {
        int count = 0;
        if (cache.get("values", "1") != null) {
            count++;
        }
        if (cache.get("values", "2") != null) {
            count++;
        }
        return count;
    }

    /**
     * 统计当前仍可读取的测试计数项。
     */
    private static int countPresentCounterEntries(Counter counter) {
        int count = 0;
        if (counter.get("derived", "1") != null) {
            count++;
        }
        if (counter.get("derived", "2") != null) {
            count++;
        }
        return count;
    }

    /**
     * 验证容量上限必须大于零。
     */
    @Test
    public void shouldRejectNonPositiveMaximumSize() {
        assertThrows(IllegalArgumentException.class, () -> new CaffeineCache(0));
        assertThrows(IllegalArgumentException.class, () -> new CaffeineCache(-1));
    }

    /**
     * 验证默认容量上限。
     */
    @Test
    public void shouldExposeExpectedDefaultMaximumSize() {
        assertEquals(10_000L, CaffeineCache.DEFAULT_MAXIMUM_SIZE);
    }

    /**
     * 验证 Caffeine 运行时异常会按原始类型传播。
     */
    @Test
    public void shouldPropagateCaffeineRuntimeFailureAsOriginalException() {
        IllegalStateException failure = new IllegalStateException("ticker failed");
        CaffeineCache brokenCache = new CaffeineCache(
                CaffeineCache.DEFAULT_MAXIMUM_SIZE,
                new FailingTicker(failure)
        );

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> brokenCache.put("values", "1", "one", Duration.ofMinutes(1)));

        assertSame(failure, thrown);
    }

    /**
     * 验证内部组合键提供可读的调试信息。
     */
    @Test
    public void shouldExposeReadableCacheKeyForDebugging() {
        CaffeineCacheKey key = new CaffeineCacheKey("users:active", "42");
        assertEquals("{cacheName='users:active', key='42'}", key.toString());
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

    /**
     * 始终抛出异常的测试时钟。
     */
    private static final class FailingTicker implements Ticker {

        private final RuntimeException failure;

        /**
         * 创建失败时钟。
         */
        private FailingTicker(RuntimeException failure) {
            this.failure = failure;
        }

        /**
         * 抛出预设异常。
         */
        @Override
        public long read() {
            throw failure;
        }
    }

    /**
     * 用于验证对象引用行为的可变值。
     */
    private static final class MutableValue {

        private String text;

        /**
         * 创建可变测试值。
         */
        private MutableValue(String text) {
            this.text = text;
        }
    }
}
