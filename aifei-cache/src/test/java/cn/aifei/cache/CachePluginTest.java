package cn.aifei.cache;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.cache.caffeine.CaffeineCache;
import cn.aifei.cache.caffeine.CaffeineCounter;
import cn.aifei.cache.internal.CounterFactory;
import cn.aifei.cache.redis.RedisCache;
import cn.aifei.cache.redis.RedisCounter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 验证缓存插件的注册和关闭行为。
 */
public class CachePluginTest {

    private AopFactory previousFactory;

    /**
     * 在测试前替换 AOP 工厂。
     */
    @Before
    public void replaceAopFactory() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
    }

    /**
     * 在测试后恢复原 AOP 工厂。
     */
    @After
    public void restoreAopFactory() {
        AopKit.get().setAopFactory(previousFactory);
    }

    /**
     * 验证缓存单例只注册一次。
     */
    @Test
    public void shouldRegisterCacheForInjectionOnlyOnce() {
        CloseTrackingCache cache = new CloseTrackingCache(false);
        CachePlugin plugin = new CachePlugin(cache);

        plugin.start();
        plugin.start();

        assertSame(cache, Aop.get(Cache.class));
    }

    /**
     * 验证 Caffeine 缓存可以自动创建对应计数器。
     */
    @Test
    public void shouldCreateCaffeineCounterFromCache() {
        CaffeineCache cache = new CaffeineCache();
        CachePlugin plugin = new CachePlugin(cache);

        try {
            plugin.start();

            assertSame(cache, Aop.get(Cache.class));
            assertTrue(Aop.get(Counter.class) instanceof CaffeineCounter);
        } finally {
            plugin.stop();
        }
    }

    /**
     * 验证 Redis 缓存可以自动创建对应计数器。
     */
    @Test
    public void shouldCreateRedisCounterFromCache() {
        RedisCache cache = new RedisCache("127.0.0.1", 1);
        CachePlugin plugin = new CachePlugin(cache);

        try {
            plugin.start();

            assertSame(cache, Aop.get(Cache.class));
            assertTrue(Aop.get(Counter.class) instanceof RedisCounter);
        } finally {
            plugin.stop();
        }
    }

    /**
     * 验证不支持的缓存实现不能自动创建计数器。
     */
    @Test
    public void shouldRejectUnsupportedCacheForAutomaticCounterCreation() {
        UnsupportedCache cache = new UnsupportedCache();

        assertThrows(IllegalArgumentException.class, () -> new CachePlugin(cache));
    }

    /**
     * 验证公共 API 只暴露接收缓存的构造方法。
     */
    @Test
    public void shouldExposeOnlyCacheConstructor() {
        Constructor<?>[] constructors = CachePlugin.class.getConstructors();

        assertEquals(1, constructors.length);
        Class<?>[] parameterTypes = constructors[0].getParameterTypes();
        assertEquals(1, parameterTypes.length);
        assertSame(Cache.class, parameterTypes[0]);
    }

    /**
     * 验证可关闭缓存和自动创建的计数器只关闭一次。
     */
    @Test
    public void shouldCloseAutoCloseableInstancesOnlyOnce() {
        CloseTrackingCache cache = new CloseTrackingCache(false);
        CachePlugin plugin = new CachePlugin(cache);

        plugin.start();
        plugin.stop();
        plugin.stop();

        assertEquals(1, cache.closeCount);
        assertEquals(1, cache.createdCounter.closeCount);
    }

    /**
     * 验证关闭失败后可以重试。
     */
    @Test
    public void shouldRetryCloseAfterFailure() {
        CloseTrackingCache cache = new CloseTrackingCache(true);
        CachePlugin plugin = new CachePlugin(cache);

        plugin.start();
        RuntimeException exception = assertThrows(RuntimeException.class, plugin::stop);
        plugin.stop();
        plugin.stop();

        assertEquals("close failed", exception.getCause().getMessage());
        assertEquals(2, cache.closeCount);
    }

    /**
     * 记录关闭次数的测试缓存。
     */
    private static final class CloseTrackingCache implements Cache, AutoCloseable, CounterFactory {

        private final boolean failFirstClose;
        private CloseTrackingCounter createdCounter;
        private int closeCount;

        /**
         * 创建关闭跟踪缓存。
         */
        private CloseTrackingCache(boolean failFirstClose) {
            this.failFirstClose = failFirstClose;
        }

        /**
         * 固定返回未命中。
         */
        @Override
        public <T> T get(String cacheName, String key) {
            return null;
        }

        /**
         * 忽略写入操作。
         */
        @Override
        public void put(String cacheName, String key, Object value, Duration ttl) {
        }

        /**
         * 固定返回未写入。
         */
        @Override
        public boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
            return false;
        }

        /**
         * 固定返回续期失败。
         */
        @Override
        public boolean expire(String cacheName, String key, Duration ttl) {
            return false;
        }

        /**
         * 忽略删除操作。
         */
        @Override
        public void remove(String cacheName, String key) {
        }

        /**
         * 忽略清空操作。
         */
        @Override
        public void clear(String cacheName) {
        }

        /**
         * 创建用于插件自动注册的测试计数器。
         */
        @Override
        public Counter createCounter() {
            createdCounter = new CloseTrackingCounter(false);
            return createdCounter;
        }

        /**
         * 记录关闭次数，并按需模拟首次失败。
         */
        @Override
        public void close() throws Exception {
            if (++closeCount == 1 && failFirstClose) {
                throw new Exception("close failed");
            }
        }
    }

    /**
     * 不支持自动创建计数器的测试缓存。
     */
    private static final class UnsupportedCache implements Cache {

        /**
         * 固定返回未命中。
         */
        @Override
        public <T> T get(String cacheName, String key) {
            return null;
        }

        /**
         * 忽略写入操作。
         */
        @Override
        public void put(String cacheName, String key, Object value, Duration ttl) {
        }

        /**
         * 固定返回未写入。
         */
        @Override
        public boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
            return false;
        }

        /**
         * 固定返回续期失败。
         */
        @Override
        public boolean expire(String cacheName, String key, Duration ttl) {
            return false;
        }

        /**
         * 忽略删除操作。
         */
        @Override
        public void remove(String cacheName, String key) {
        }

        /**
         * 忽略清空操作。
         */
        @Override
        public void clear(String cacheName) {
        }
    }

    /**
     * 记录关闭次数的测试计数器。
     */
    private static final class CloseTrackingCounter implements Counter, AutoCloseable {

        private final boolean failFirstClose;
        private int closeCount;

        /**
         * 创建关闭跟踪计数器。
         */
        private CloseTrackingCounter(boolean failFirstClose) {
            this.failFirstClose = failFirstClose;
        }

        /**
         * 固定返回未命中。
         */
        @Override
        public Long get(String counterName, String key) {
            return null;
        }

        /**
         * 固定返回增加量。
         */
        @Override
        public long increase(String counterName, String key, long step, Duration ttl) {
            return step;
        }

        /**
         * 固定返回增加量。
         */
        @Override
        public long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
            return step;
        }

        /**
         * 固定返回减少量的负数。
         */
        @Override
        public long decrease(String counterName, String key, long step, Duration ttl) {
            return -step;
        }

        /**
         * 固定返回减少量的负数。
         */
        @Override
        public long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
            return -step;
        }

        /**
         * 忽略删除操作。
         */
        @Override
        public void remove(String counterName, String key) {
        }

        /**
         * 记录关闭次数，并按需模拟首次失败。
         */
        @Override
        public void close() throws Exception {
            if (++closeCount == 1 && failFirstClose) {
                throw new Exception("close failed");
            }
        }
    }
}
