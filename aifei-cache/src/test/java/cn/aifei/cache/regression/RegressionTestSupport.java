package cn.aifei.cache.regression;

import cn.aifei.cache.Cache;
import cn.aifei.cache.Counter;
import cn.aifei.cache.caffeine.CaffeineCache;
import cn.aifei.cache.redis.RedisCache;
import cn.aifei.cache.redis.RedisConfig;
import org.junit.Assert;
import org.junit.Assume;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

public final class RegressionTestSupport {
    public static final String REDIS_URI = System.getProperty("redis.uri", "redis://127.0.0.1:6379/0");
    public static final String COUNTER_PREFIX = "_Aifei_Counter_:";

    public static class Backend implements AutoCloseable {
        public final String prefix = "aifei_regression_" + UUID.randomUUID().toString().replace("-", "");
        public final Cache cache;
        public final Counter counter;
        public final Cache peerCache;
        public final Counter peerCounter;
        public final RedisClient raw;

        public Backend(String kind) {
            if (kind.equals("caffeine")) {
                CaffeineCache local = new CaffeineCache(100_000);
                cache = local;
                counter = local.createCounter();
                peerCache = cache;
                peerCounter = counter;
                raw = null;
            } else {
                Assume.assumeTrue("Redis integration tests: enable -Dredis.integration=true",
                        Boolean.getBoolean("redis.integration"));
                RedisConfig first = config(), second = config();
                if (kind.equals("redis-resp3")) { first.resp3(); second.resp3(); }
                RedisCache redis = new RedisCache(first);
                RedisCache peer = new RedisCache(second);
                cache = redis;
                counter = redis.createCounter();
                peerCache = peer;
                peerCounter = peer.createCounter();
                raw = RedisClient.create(URI.create(REDIS_URI));
                Assert.assertEquals("PONG", raw.ping());
            }
        }

        public String name(String suffix) { return prefix + ":" + suffix; }
        public String counterKey(String name, String key) { return COUNTER_PREFIX + name + ":" + key; }
        @Override public void close() throws Exception {
            if (raw == null) return;
            try {
                // Independent cleanup: do not rely on the clear() implementation under test.
                cleanup(raw, prefix + ":*");
                cleanup(raw, COUNTER_PREFIX + prefix + ":*");
            } finally {
                try { ((RedisCache) peerCache).close(); }
                finally { try { ((RedisCache) cache).close(); } finally { raw.close(); } }
            }
        }
    }

    public static RedisConfig config() {
        return new RedisConfig().uri(REDIS_URI).maxTotal(24).maxIdle(24)
                .timeoutMillis(2000).maxWaitMillis(5000).jmxEnabled(false);
    }

    public static void cleanup(RedisClient raw, String pattern) {
        String cursor = "0";
        do {
            ScanResult<String> page = raw.scan(cursor, new ScanParams().match(pattern).count(1000));
            if (!page.getResult().isEmpty()) raw.unlink(page.getResult().toArray(new String[0]));
            cursor = page.getCursor();
        } while (!cursor.equals("0"));
    }

    public static void parallel(int workers, IntConsumer work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                final int worker = i;
                futures.add(pool.submit(() -> {
                    try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                    work.accept(worker);
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(40, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            Assert.assertTrue("worker threads did not stop", pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
