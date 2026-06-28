package cn.aifei.cache;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

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
     * 验证可关闭缓存只关闭一次。
     */
    @Test
    public void shouldCloseAutoCloseableCacheOnlyOnce() {
        CloseTrackingCache cache = new CloseTrackingCache(false);
        CachePlugin plugin = new CachePlugin(cache);

        plugin.start();
        plugin.stop();
        plugin.stop();

        assertEquals(1, cache.closeCount);
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
    private static final class CloseTrackingCache implements Cache, AutoCloseable {

        private final boolean failFirstClose;
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
