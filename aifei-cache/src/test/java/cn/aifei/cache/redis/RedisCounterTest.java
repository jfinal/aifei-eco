package cn.aifei.cache.redis;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/**
 * 验证 Redis 计数实现的本地行为。
 */
public class RedisCounterTest {

    /**
     * 验证 RedisCounter 不暴露独立连接构造器，只由 RedisCache 创建。
     */
    @Test
    public void shouldNotExposePublicConstructors() {
        assertEquals(0, RedisCounter.class.getConstructors().length);
    }

    /**
     * 验证非法参数会在访问 Redis 前被拒绝。
     */
    @Test
    public void shouldRejectInvalidCounterArgumentsWithoutAccessingRedis() {
        RedisCache cache = new RedisCache("127.0.0.1", 1);
        RedisCounter counter = (RedisCounter) cache.createCounter();
        Duration ttl = Duration.ofMinutes(1);

        try {
            assertNull(counter.get(null, "key"));
            assertNull(counter.get("", "key"));
            assertNull(counter.get(" ", "key"));
            assertNull(counter.get("counter", null));
            assertNull(counter.get("counter", ""));
            assertNull(counter.get("counter", " "));
            assertNull(counter.get("counter", "key:1"));

            counter.remove(null, "key");
            counter.remove("", "key");
            counter.remove(" ", "key");
            counter.remove("counter", null);
            counter.remove("counter", "");
            counter.remove("counter", " ");
            counter.remove("counter", "key:1");

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
        } finally {
            cache.close();
        }
    }
}
