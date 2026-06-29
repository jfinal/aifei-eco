package cn.aifei.cache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/**
 * 验证所有计数实现必须遵守的公共行为。
 */
public abstract class CounterContractTest {

    protected Counter counter;
    private final String testPrefix = "counter-test-" + UUID.randomUUID();

    /**
     * 创建待测试的计数实例。
     */
    protected abstract Counter createCounter();

    /**
     * 推进测试时钟。
     */
    protected abstract void advanceTime(Duration duration) throws Exception;

    /**
     * 在每个测试前创建计数器。
     */
    @Before
    public void setUpCounter() {
        counter = createCounter();
    }

    /**
     * 在每个测试后关闭计数器。
     */
    @After
    public void closeCounter() throws Exception {
        if (counter instanceof AutoCloseable) {
            ((AutoCloseable) counter).close();
        }
    }

    /**
     * 验证读取不存在的计数项时返回 {@code null}。
     */
    @Test
    public void shouldReturnNullWhenCounterIsMissing() {
        assertNull(counter.get(counterName("counters"), "views"));
    }

    /**
     * 验证读取参数非法时按未命中处理。
     */
    @Test
    public void shouldReturnNullWhenReadArgumentsAreInvalid() {
        assertNull(counter.get(null, "key"));
        assertNull(counter.get("", "key"));
        assertNull(counter.get(" ", "key"));
        assertNull(counter.get("counter", null));
        assertNull(counter.get("counter", ""));
        assertNull(counter.get("counter", " "));
        assertNull(counter.get("counter", "key:1"));
    }

    /**
     * 验证删除参数非法时按未命中处理。
     */
    @Test
    public void shouldIgnoreInvalidRemoveArguments() {
        String counterName = counterName("counters");
        counter.increase(counterName, "views", 1L, Duration.ofMinutes(1));

        counter.remove(null, "views");
        counter.remove("", "views");
        counter.remove(" ", "views");
        counter.remove(counterName, null);
        counter.remove(counterName, "");
        counter.remove(counterName, " ");
        counter.remove(counterName, "key:1");

        assertEquals(Long.valueOf(1L), counter.get(counterName, "views"));
    }

    /**
     * 验证缺失时增加计数并按 TTL 过期。
     */
    @Test
    public void shouldIncreaseMissingCounterAndExpire() throws Exception {
        String counterName = counterName("counters");

        assertEquals(3L, counter.increase(counterName, "views", 3L, Duration.ofMillis(40)));
        assertEquals(Long.valueOf(3L), counter.get(counterName, "views"));

        advanceTime(Duration.ofMillis(41));

        assertNull(counter.get(counterName, "views"));
    }

    /**
     * 验证缺失时减少计数并按 TTL 过期。
     */
    @Test
    public void shouldDecreaseMissingCounterAndExpire() throws Exception {
        String counterName = counterName("counters");

        assertEquals(-2L, counter.decrease(counterName, "quota", 2L, Duration.ofMillis(40)));
        assertEquals(Long.valueOf(-2L), counter.get(counterName, "quota"));

        advanceTime(Duration.ofMillis(41));

        assertNull(counter.get(counterName, "quota"));
    }

    /**
     * 验证命中时更新计数并保留原 TTL。
     */
    @Test
    public void shouldUpdateExistingCounterWithoutResettingTtl() throws Exception {
        String counterName = counterName("counters");

        assertEquals(2L, counter.increase(counterName, "views", 2L, Duration.ofMillis(120)));
        advanceTime(Duration.ofMillis(80));

        assertEquals(5L, counter.increase(counterName, "views", 3L, Duration.ofMillis(500)));
        assertEquals(4L, counter.decrease(counterName, "views", 1L, Duration.ofMillis(500)));
        assertEquals(Long.valueOf(4L), counter.get(counterName, "views"));

        advanceTime(Duration.ofMillis(39));
        assertEquals(Long.valueOf(4L), counter.get(counterName, "views"));

        advanceTime(Duration.ofMillis(2));
        assertNull(counter.get(counterName, "views"));
    }

    /**
     * 验证增加已有计数项并刷新 TTL。
     */
    @Test
    public void shouldIncreaseExistingCounterAndRefreshTtl() throws Exception {
        String counterName = counterName("counters");

        assertEquals(2L, counter.increase(counterName, "views", 2L, Duration.ofMillis(120)));
        advanceTime(Duration.ofMillis(80));

        assertEquals(5L, counter.increaseAndRefreshTtl(counterName, "views", 3L, Duration.ofMillis(200)));
        assertEquals(Long.valueOf(5L), counter.get(counterName, "views"));

        advanceTime(Duration.ofMillis(199));
        assertEquals(Long.valueOf(5L), counter.get(counterName, "views"));

        advanceTime(Duration.ofMillis(2));
        assertNull(counter.get(counterName, "views"));
    }

    /**
     * 验证减少已有计数项并刷新 TTL。
     */
    @Test
    public void shouldDecreaseExistingCounterAndRefreshTtl() throws Exception {
        String counterName = counterName("counters");

        assertEquals(2L, counter.increase(counterName, "quota", 2L, Duration.ofMillis(120)));
        advanceTime(Duration.ofMillis(80));

        assertEquals(-1L, counter.decreaseAndRefreshTtl(counterName, "quota", 3L, Duration.ofMillis(200)));
        assertEquals(Long.valueOf(-1L), counter.get(counterName, "quota"));

        advanceTime(Duration.ofMillis(199));
        assertEquals(Long.valueOf(-1L), counter.get(counterName, "quota"));

        advanceTime(Duration.ofMillis(2));
        assertNull(counter.get(counterName, "quota"));
    }

    /**
     * 验证秒重载行为正确。
     */
    @Test
    public void shouldUpdateCounterWithTtlSeconds() throws Exception {
        String counterName = counterName("counters");

        assertEquals(2L, counter.increase(counterName, "views", 2L, 2));
        assertEquals(-3L, counter.decrease(counterName, "quota", 3L, 2));
        assertEquals(Long.valueOf(2L), counter.get(counterName, "views"));
        assertEquals(Long.valueOf(-3L), counter.get(counterName, "quota"));

        advanceTime(Duration.ofMillis(1999));
        assertEquals(Long.valueOf(2L), counter.get(counterName, "views"));
        assertEquals(Long.valueOf(-3L), counter.get(counterName, "quota"));

        advanceTime(Duration.ofMillis(2));
        assertNull(counter.get(counterName, "views"));
        assertNull(counter.get(counterName, "quota"));
    }

    /**
     * 验证刷新 TTL 的秒重载行为正确。
     */
    @Test
    public void shouldUpdateCounterAndRefreshTtlWithTtlSeconds() throws Exception {
        String counterName = counterName("counters");

        assertEquals(2L, counter.increaseAndRefreshTtl(counterName, "views", 2L, 2));
        assertEquals(-3L, counter.decreaseAndRefreshTtl(counterName, "quota", 3L, 2));
        assertEquals(Long.valueOf(2L), counter.get(counterName, "views"));
        assertEquals(Long.valueOf(-3L), counter.get(counterName, "quota"));

        advanceTime(Duration.ofMillis(1500));
        assertEquals(5L, counter.increaseAndRefreshTtl(counterName, "views", 3L, 2));
        assertEquals(-4L, counter.decreaseAndRefreshTtl(counterName, "quota", 1L, 2));

        advanceTime(Duration.ofMillis(1999));
        assertEquals(Long.valueOf(5L), counter.get(counterName, "views"));
        assertEquals(Long.valueOf(-4L), counter.get(counterName, "quota"));

        advanceTime(Duration.ofMillis(2));
        assertNull(counter.get(counterName, "views"));
        assertNull(counter.get(counterName, "quota"));
    }

    /**
     * 验证删除计数项。
     */
    @Test
    public void shouldRemoveCounter() {
        String counterName = counterName("counters");
        counter.increase(counterName, "views", 1L, Duration.ofMinutes(1));

        counter.remove(counterName, "missing");
        counter.remove(counterName, "views");

        assertNull(counter.get(counterName, "views"));
    }

    /**
     * 验证不同计数名称之间互不影响。
     */
    @Test
    public void shouldIsolateCounterNamespaces() {
        String counters = counterName("counters");
        String articles = counterName("articles");

        counter.increase(counters, "42", 1L, Duration.ofMinutes(1));
        counter.increase(articles, "42", 2L, Duration.ofMinutes(1));

        assertEquals(Long.valueOf(1L), counter.get(counters, "42"));
        assertEquals(Long.valueOf(2L), counter.get(articles, "42"));
    }

    /**
     * 验证计数溢出会抛出异常。
     */
    @Test
    public void shouldRejectCounterOverflow() {
        String counterName = counterName("counters");

        assertEquals(Long.MAX_VALUE,
                counter.increase(counterName, "max", Long.MAX_VALUE, Duration.ofMinutes(1)));
        assertThrows(ArithmeticException.class,
                () -> counter.increase(counterName, "max", 1L, Duration.ofMinutes(1)));

        assertEquals(-Long.MAX_VALUE,
                counter.decrease(counterName, "min", Long.MAX_VALUE, Duration.ofMinutes(1)));
        assertEquals(Long.MIN_VALUE,
                counter.decrease(counterName, "min", 1L, Duration.ofMinutes(1)));
        assertThrows(ArithmeticException.class,
                () -> counter.decrease(counterName, "min", 1L, Duration.ofMinutes(1)));

        assertEquals(Long.MAX_VALUE,
                counter.increaseAndRefreshTtl(counterName, "refresh-max", Long.MAX_VALUE, Duration.ofMinutes(1)));
        assertThrows(ArithmeticException.class,
                () -> counter.increaseAndRefreshTtl(counterName, "refresh-max", 1L, Duration.ofMinutes(1)));

        assertEquals(-Long.MAX_VALUE,
                counter.decreaseAndRefreshTtl(counterName, "refresh-min", Long.MAX_VALUE, Duration.ofMinutes(1)));
        assertEquals(Long.MIN_VALUE,
                counter.decreaseAndRefreshTtl(counterName, "refresh-min", 1L, Duration.ofMinutes(1)));
        assertThrows(ArithmeticException.class,
                () -> counter.decreaseAndRefreshTtl(counterName, "refresh-min", 1L, Duration.ofMinutes(1)));
    }

    /**
     * 验证非法参数会抛出预期异常。
     */
    @Test
    public void shouldRejectInvalidArguments() {
        Duration ttl = Duration.ofMinutes(1);

        assertThrows(IllegalArgumentException.class,
                () -> counter.increase(null, "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase(" ", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", null, 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", " ", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key:1", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key", 0L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key", -1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key", 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key", 1L, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key", 1L, Duration.ofNanos(999_999)));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key", 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("counter", "key", 1L, -1));

        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl(null, "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl(" ", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", null, 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", " ", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key:1", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key", 0L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key", -1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key", 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key", 1L, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key", 1L, Duration.ofNanos(999_999)));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key", 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> counter.increaseAndRefreshTtl("counter", "key", 1L, -1));

        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease(null, "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease(" ", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", null, 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", " ", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key:1", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key", 0L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key", -1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key", 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key", 1L, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key", 1L, Duration.ofNanos(999_999)));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key", 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decrease("counter", "key", 1L, -1));

        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl(null, "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl(" ", "key", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", null, 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", " ", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key:1", 1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key", 0L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key", -1L, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key", 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key", 1L, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key", 1L, Duration.ofNanos(999_999)));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key", 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> counter.decreaseAndRefreshTtl("counter", "key", 1L, -1));
    }

    /**
     * 生成当前测试独立的计数名称。
     */
    protected String counterName(String name) {
        return testPrefix + ":" + name;
    }
}
