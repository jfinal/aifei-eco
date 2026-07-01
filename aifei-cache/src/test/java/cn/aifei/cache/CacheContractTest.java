package cn.aifei.cache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 验证所有缓存实现必须遵守的公共行为。
 */
public abstract class CacheContractTest {

    protected Cache cache;

    /**
     * 创建待测试的缓存实例。
     */
    protected abstract Cache createCache();

    /**
     * 推进测试时钟。
     */
    protected abstract void advanceTime(Duration duration);

    /**
     * 在每个测试前创建缓存。
     */
    @Before
    public void setUpCache() {
        cache = createCache();
    }

    /**
     * 在每个测试后关闭缓存。
     */
    @After
    public void closeCache() throws Exception {
        if (cache instanceof AutoCloseable) {
            ((AutoCloseable) cache).close();
        }
    }

    /**
     * 验证读取不存在的缓存项时返回 {@code null}。
     */
    @Test
    public void shouldReturnNullWhenEntryIsMissing() {
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证普通读取参数非法时按未命中处理。
     */
    @Test
    public void shouldReturnNullWhenReadArgumentsAreInvalid() {
        assertNull(cache.get(null, "key"));
        assertNull(cache.get("", "key"));
        assertNull(cache.get(" ", "key"));
        assertNull(cache.get("cache", null));
        assertNull(cache.get("cache", ""));
        assertNull(cache.get("cache", " "));
    }

    /**
     * 验证删除参数非法时按未命中处理。
     */
    @Test
    public void shouldIgnoreInvalidRemoveArguments() {
        cache.put("cache", "key", "value", Duration.ofMinutes(1));

        cache.remove(null, "key");
        cache.remove("", "key");
        cache.remove(" ", "key");
        cache.remove("cache", null);
        cache.remove("cache", "");
        cache.remove("cache", " ");

        assertEquals("value", cache.get("cache", "key"));
    }

    /**
     * 验证缓存值可以正常写入和读取。
     */
    @Test
    public void shouldPutAndGetValue() {
        cache.put("users", "42", "James", Duration.ofMinutes(1));

        assertEquals("James", cache.get("users", "42"));
    }

    /**
     * 验证缓存项不存在时可以写入。
     */
    @Test
    public void shouldPutValueWhenEntryIsAbsent() {
        assertTrue(cache.putIfAbsent("users", "42", "James", Duration.ofMinutes(1)));

        assertEquals("James", cache.get("users", "42"));
    }

    /**
     * 验证缓存项已存在时不会覆盖原值或重置 TTL。
     */
    @Test
    public void shouldNotOverwriteExistingValueWhenPuttingIfAbsent() {
        assertTrue(cache.putIfAbsent("users", "42", "James", Duration.ofMillis(120)));
        advanceTime(Duration.ofMillis(80));

        assertFalse(cache.putIfAbsent("users", "42", "Other", Duration.ofMillis(220)));
        assertEquals("James", cache.get("users", "42"));

        advanceTime(Duration.ofMillis(41));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证缓存项过期后可以再次按不存在写入。
     */
    @Test
    public void shouldPutValueIfAbsentAfterEntryExpires() {
        assertTrue(cache.putIfAbsent("users", "42", "James", Duration.ofMillis(40)));
        advanceTime(Duration.ofMillis(41));

        assertTrue(cache.putIfAbsent("users", "42", "Lucy", Duration.ofMinutes(1)));

        assertEquals("Lucy", cache.get("users", "42"));
    }

    /**
     * 验证秒重载可以在缓存项不存在时写入并按秒过期。
     */
    @Test
    public void shouldPutValueIfAbsentWithTtlSeconds() {
        assertTrue(cache.putIfAbsent("users", "42", "James", 2));

        advanceTime(Duration.ofMillis(1999));
        assertEquals("James", cache.get("users", "42"));

        advanceTime(Duration.ofMillis(2));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证可以判断缓存项是否存在。
     */
    @Test
    public void shouldReturnWhetherEntryExists() {
        assertFalse(cache.exists("users", "42"));

        cache.put("users", "42", "James", Duration.ofMillis(40));
        assertTrue(cache.exists("users", "42"));

        advanceTime(Duration.ofMillis(41));
        assertFalse(cache.exists("users", "42"));
    }

    /**
     * 验证存在性判断参数非法时按未命中处理。
     */
    @Test
    public void shouldReturnFalseWhenExistsArgumentsAreInvalid() {
        assertFalse(cache.exists(null, "key"));
        assertFalse(cache.exists("", "key"));
        assertFalse(cache.exists(" ", "key"));
        assertFalse(cache.exists("cache", null));
        assertFalse(cache.exists("cache", ""));
        assertFalse(cache.exists("cache", " "));
    }

    /**
     * 验证秒重载可以写入并按秒过期。
     */
    @Test
    public void shouldPutValueWithTtlSeconds() {
        cache.put("users", "42", "James", 2);

        advanceTime(Duration.ofMillis(1999));
        assertEquals("James", cache.get("users", "42"));

        advanceTime(Duration.ofMillis(2));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证未命中时会加载并缓存值。
     */
    @Test
    public void shouldLoadCacheAndReturnValueOnMiss() {
        AtomicInteger loads = new AtomicInteger();

        String value = cache.get("users", "42", Duration.ofMinutes(1), () -> {
            loads.incrementAndGet();
            return "James";
        });

        assertEquals("James", value);
        assertEquals("James", cache.get("users", "42"));
        assertEquals(1, loads.get());
    }

    /**
     * 验证秒重载可以加载并缓存值。
     */
    @Test
    public void shouldLoadValueWithTtlSeconds() {
        AtomicInteger loads = new AtomicInteger();

        assertEquals("James", cache.get("users", "42", 2, () -> {
            loads.incrementAndGet();
            return "James";
        }));
        assertEquals("James", cache.get("users", "42", 2, () -> {
            loads.incrementAndGet();
            return "Other";
        }));

        assertEquals(1, loads.get());
    }

    /**
     * 验证命中缓存时不会调用加载器。
     */
    @Test
    public void shouldNotInvokeLoaderOnHit() {
        cache.put("users", "42", "cached", Duration.ofMinutes(1));
        AtomicInteger loads = new AtomicInteger();

        String value = cache.get("users", "42", Duration.ofMinutes(1), () -> {
            loads.incrementAndGet();
            return "loaded";
        });

        assertEquals("cached", value);
        assertEquals(0, loads.get());
    }

    /**
     * 验证加载器返回 {@code null} 时不会写入缓存。
     */
    @Test
    public void shouldNotCacheNullLoadedValue() {
        AtomicInteger loads = new AtomicInteger();

        assertNull(cache.get("users", "42", Duration.ofMinutes(1), () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(cache.get("users", "42", Duration.ofMinutes(1), () -> {
            loads.incrementAndGet();
            return null;
        }));

        assertEquals(2, loads.get());
    }

    /**
     * 验证加载器异常会直接向外抛出。
     */
    @Test
    public void shouldPropagateLoaderException() {
        IllegalStateException failure = new IllegalStateException("load failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> cache.get("users", "42", Duration.ofMinutes(1), () -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证加载值按指定 TTL 过期。
     */
    @Test
    public void shouldExpireLoadedValueUsingSpecifiedTtl() {
        AtomicInteger loads = new AtomicInteger();

        assertEquals("value-1", cache.get("users", "42", Duration.ofMillis(40),
                () -> "value-" + loads.incrementAndGet()));
        advanceTime(Duration.ofMillis(41));
        assertEquals("value-2", cache.get("users", "42", Duration.ofMillis(40),
                () -> "value-" + loads.incrementAndGet()));
    }

    /**
     * 验证不同缓存名称之间互不影响。
     */
    @Test
    public void shouldIsolateCacheNamespaces() {
        cache.put("users", "42", "user", Duration.ofMinutes(1));
        cache.put("articles", "42", "article", Duration.ofMinutes(1));

        assertEquals("user", cache.get("users", "42"));
        assertEquals("article", cache.get("articles", "42"));
    }

    /**
     * 验证缓存键可以包含冒号。
     */
    @Test
    public void shouldSupportColonInCacheKeys() {
        cache.put("user", "manager:123", "James", Duration.ofMinutes(1));

        assertEquals("James", cache.get("user", "manager:123"));
        assertTrue(cache.exists("user", "manager:123"));
        assertFalse(cache.putIfAbsent("user", "manager:123", "Other", Duration.ofMinutes(1)));
        assertTrue(cache.expire("user", "manager:123", Duration.ofMinutes(2)));

        cache.remove("user", "manager:123");

        assertNull(cache.get("user", "manager:123"));
    }

    /**
     * 验证覆盖写入会重置 TTL。
     */
    @Test
    public void shouldOverwriteValueAndResetTtl() {
        cache.put("users", "42", "old", Duration.ofMillis(120));
        advanceTime(Duration.ofMillis(80));
        cache.put("users", "42", "new", Duration.ofMillis(220));
        advanceTime(Duration.ofMillis(80));

        assertEquals("new", cache.get("users", "42"));

        advanceTime(Duration.ofMillis(141));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证续期已有缓存项会重置 TTL 且不修改缓存值。
     */
    @Test
    public void shouldExpireExistingEntryByResettingTtl() {
        cache.put("users", "42", "James", Duration.ofMillis(120));
        advanceTime(Duration.ofMillis(80));

        assertTrue(cache.expire("users", "42", Duration.ofMillis(220)));
        advanceTime(Duration.ofMillis(219));
        assertEquals("James", cache.get("users", "42"));

        advanceTime(Duration.ofMillis(2));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证续期操作也可以缩短已有缓存项的剩余有效期。
     */
    @Test
    public void shouldExpireExistingEntryWithShorterTtl() {
        cache.put("users", "42", "James", Duration.ofMillis(220));
        advanceTime(Duration.ofMillis(40));

        assertTrue(cache.expire("users", "42", Duration.ofMillis(50)));
        advanceTime(Duration.ofMillis(49));
        assertEquals("James", cache.get("users", "42"));

        advanceTime(Duration.ofMillis(2));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证不存在或已过期的缓存项不会被续期或复活。
     */
    @Test
    public void shouldReturnFalseWhenExpiringMissingOrExpiredEntry() {
        assertFalse(cache.expire("users", "missing", Duration.ofMinutes(1)));

        cache.put("users", "42", "James", Duration.ofMillis(40));
        advanceTime(Duration.ofMillis(41));

        assertFalse(cache.expire("users", "42", Duration.ofMinutes(1)));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证秒重载可以续期已有缓存项。
     */
    @Test
    public void shouldExpireExistingEntryWithTtlSeconds() {
        cache.put("users", "42", "James", Duration.ofMinutes(1));

        assertTrue(cache.expire("users", "42", 2));
        advanceTime(Duration.ofMillis(1999));
        assertEquals("James", cache.get("users", "42"));

        advanceTime(Duration.ofMillis(2));
        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证缓存项到期后不可读取。
     */
    @Test
    public void shouldExpireEntry() {
        cache.put("users", "42", "James", Duration.ofMillis(40));

        advanceTime(Duration.ofMillis(41));

        assertNull(cache.get("users", "42"));
    }

    /**
     * 验证删除指定缓存项。
     */
    @Test
    public void shouldRemoveEntry() {
        cache.put("users", "42", "James", Duration.ofMinutes(1));

        cache.remove("users", "42");
        cache.remove("users", "missing");

        assertNull(cache.get("users", "42"));
        assertFalse(cache.exists("users", "42"));
    }

    /**
     * 验证清空指定名称及其下级缓存项。
     */
    @Test
    public void shouldClearSpecifiedNamespaceAndChildren() {
        cache.put("users", "1", "James", Duration.ofMinutes(1));
        cache.put("users", "2", "Lucy", Duration.ofMinutes(1));
        cache.put("users", "manager:123", "Manager", Duration.ofMinutes(1));
        cache.put("users:profile", "1", "Profile", Duration.ofMinutes(1));
        cache.put("users2", "1", "Other User", Duration.ofMinutes(1));
        cache.put("articles", "1", "Cache Design", Duration.ofMinutes(1));

        cache.clear("users");
        cache.clear("missing");

        assertNull(cache.get("users", "1"));
        assertNull(cache.get("users", "2"));
        assertNull(cache.get("users", "manager:123"));
        assertNull(cache.get("users:profile", "1"));
        assertEquals("Other User", cache.get("users2", "1"));
        assertEquals("Cache Design", cache.get("articles", "1"));
    }

    /**
     * 验证特殊字符不会造成缓存名称冲突。
     */
    @Test
    public void shouldSupportSpecialCharactersWithoutNamespaceCollision() {
        String cacheName = "业务:用户*?[x]/空 格";
        String otherCacheName = "业务:其他用户*?[x]/空 格";
        String key = "键*?[x]/空 格";

        cache.put(cacheName, key, "value", Duration.ofMinutes(1));
        cache.put(otherCacheName, key, "other", Duration.ofMinutes(1));

        cache.clear(cacheName);

        assertNull(cache.get(cacheName, key));
        assertEquals("other", cache.get(otherCacheName, key));
    }

    /**
     * 验证非法参数会抛出预期异常。
     */
    @Test
    public void shouldRejectInvalidArguments() {
        Duration ttl = Duration.ofMinutes(1);

        assertThrows(IllegalArgumentException.class, () -> cache.put("cache", "key", null, ttl));
        assertThrows(IllegalArgumentException.class, () -> cache.put("cache", "key", "value", null));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("cache", "key", "value", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("cache", "key", "value", Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("cache", "key", "value", Duration.ofNanos(999_999)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("cache", "key", "value", 0));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("cache", "key", "value", -1));

        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("cache", "key", null, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("cache", "key", "value", null));
        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("cache", "key", "value", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("cache", "key", "value", Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("cache", "key", "value", Duration.ofNanos(999_999)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("cache", "key", "value", 0));
        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("cache", "key", "value", -1));

        assertThrows(IllegalArgumentException.class,
                () -> cache.expire(null, "key", ttl));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("", "key", ttl));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire(" ", "key", ttl));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", null, ttl));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", "", ttl));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", " ", ttl));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", "key", null));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", "key", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", "key", Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", "key", Duration.ofNanos(999_999)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", "key", 0));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("cache", "key", -1));

        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", "key", ttl, null));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", "key", null, () -> "value"));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", "key", Duration.ZERO, () -> "value"));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", "key", 0, () -> "value"));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", "key", -1, () -> "value"));

        AtomicInteger loads = new AtomicInteger();
        assertThrows(IllegalArgumentException.class,
                () -> cache.get(null, "key", ttl, () -> "value-" + loads.incrementAndGet()));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("", "key", ttl, () -> "value-" + loads.incrementAndGet()));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get(" ", "key", ttl, () -> "value-" + loads.incrementAndGet()));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", null, ttl, () -> "value-" + loads.incrementAndGet()));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", "", ttl, () -> "value-" + loads.incrementAndGet()));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("cache", " ", ttl, () -> "value-" + loads.incrementAndGet()));
        assertEquals(0, loads.get());

        assertThrows(IllegalArgumentException.class, () -> cache.clear(null));
        assertThrows(IllegalArgumentException.class, () -> cache.clear(""));
    }
}
