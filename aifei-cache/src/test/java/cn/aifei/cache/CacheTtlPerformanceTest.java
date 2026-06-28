package cn.aifei.cache;

import cn.aifei.cache.caffeine.CaffeineCache;
import org.junit.Assume;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 手动运行的 TTL 参数性能对比。
 *
 * <p>运行方式：</p>
 *
 * <pre>
 * mvn -Dtest=CacheTtlPerformanceTest -Daifei.cache.performanceTest=true test
 * </pre>
 *
 * <p>该测试只输出对比数据，不作为正确性断言。微基准结果受 JVM、CPU、温度、后台进程影响，
 * 因此默认跳过，避免影响日常构建稳定性。</p>
 */
public class CacheTtlPerformanceTest {

    private static final int[] TTL_SECONDS = {
            1, 2, 3, 5, 10, 30, 60, 120,
            300, 600, 900, 1800, 3600, 7200, 21600, 86400
    };
    private static final Duration[] TTL_DURATIONS = createDurations();
    private static final String CACHE_NAME = "bench";
    private static final String KEY = "key";
    private static final Object VALUE = "value";
    private static final Supplier<Object> SHOULD_NOT_LOAD = new Supplier<Object>() {
        @Override
        public Object get() {
            throw new AssertionError("loader should not be called");
        }
    };

    private static volatile long longSink;
    private static volatile Object objectSink;

    /**
     * 对比 {@link Duration} TTL 与 int 秒级 TTL 的转换成本和 Caffeine 热路径调用成本。
     */
    @Test
    public void shouldCompareDurationAndIntTtlCost() {
        Assume.assumeTrue("manual performance test is disabled",
                Boolean.getBoolean("aifei.cache.performanceTest"));

        int conversionIterations = Integer.getInteger(
                "aifei.cache.performance.conversionIterations", 10_000_000);
        int cacheIterations = Integer.getInteger(
                "aifei.cache.performance.cacheIterations", 1_000_000);
        int warmupRounds = Integer.getInteger("aifei.cache.performance.warmupRounds", 5);
        int measureRounds = Integer.getInteger("aifei.cache.performance.measureRounds", 7);

        System.out.println();
        System.out.println("Cache TTL performance comparison");
        System.out.println("conversionIterations=" + conversionIterations
                + ", cacheIterations=" + cacheIterations
                + ", warmupRounds=" + warmupRounds
                + ", measureRounds=" + measureRounds);

        Result durationCreate = benchmark("Duration.ofSeconds(...).toMillis()",
                conversionIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return durationCreateToMillis(iterations);
                    }
                });
        Result durationReuse = benchmark("cached Duration.toMillis()",
                conversionIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return durationReuseToMillis(iterations);
                    }
                });
        Result intToMillis = benchmark("int ttlSeconds -> millis",
                conversionIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return intSecondsToMillis(iterations);
                    }
                });
        Result durationEscapes = benchmark("Duration.ofSeconds(...) escapes",
                conversionIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return durationCreateAndEscape(iterations);
                    }
                });

        printSection("TTL conversion only");
        printResult(durationCreate, intToMillis);
        printResult(durationReuse, intToMillis);
        printResult(durationEscapes, intToMillis);
        printResult(intToMillis, intToMillis);

        Result caffeinePutDurationCreate = benchmark("Caffeine put Duration.ofSeconds(...)",
                cacheIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return caffeinePutDurationCreate(iterations);
                    }
                });
        Result caffeinePutDurationReuse = benchmark("Caffeine put cached Duration",
                cacheIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return caffeinePutDurationReuse(iterations);
                    }
                });
        Result caffeinePutInt = benchmark("Caffeine put int ttlSeconds",
                cacheIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return caffeinePutInt(iterations);
                    }
                });

        printSection("Caffeine put hot path");
        printResult(caffeinePutDurationCreate, caffeinePutInt);
        printResult(caffeinePutDurationReuse, caffeinePutInt);
        printResult(caffeinePutInt, caffeinePutInt);

        Result caffeineGetHitDurationCreate = benchmark("Caffeine get hit Duration.ofSeconds(...)",
                cacheIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return caffeineGetHitDurationCreate(iterations);
                    }
                });
        Result caffeineGetHitDurationReuse = benchmark("Caffeine get hit cached Duration",
                cacheIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return caffeineGetHitDurationReuse(iterations);
                    }
                });
        Result caffeineGetHitInt = benchmark("Caffeine get hit int ttlSeconds",
                cacheIterations, warmupRounds, measureRounds, new Task() {
                    @Override
                    public long run(int iterations) {
                        return caffeineGetHitInt(iterations);
                    }
                });

        printSection("Caffeine get hit hot path");
        printResult(caffeineGetHitDurationCreate, caffeineGetHitInt);
        printResult(caffeineGetHitDurationReuse, caffeineGetHitInt);
        printResult(caffeineGetHitInt, caffeineGetHitInt);
    }

    /**
     * 每次创建 Duration 并转换为毫秒。
     */
    private static long durationCreateToMillis(int iterations) {
        long total = 0L;
        for (int i = 0; i < iterations; i++) {
            total += Duration.ofSeconds(TTL_SECONDS[i & 15]).toMillis();
        }
        return total;
    }

    /**
     * 复用已创建的 Duration 并转换为毫秒。
     */
    private static long durationReuseToMillis(int iterations) {
        long total = 0L;
        for (int i = 0; i < iterations; i++) {
            total += TTL_DURATIONS[i & 15].toMillis();
        }
        return total;
    }

    /**
     * 直接将秒数转换为毫秒。
     */
    private static long intSecondsToMillis(int iterations) {
        long total = 0L;
        for (int i = 0; i < iterations; i++) {
            total += ttlSecondsToMillis(TTL_SECONDS[i & 15]);
        }
        return total;
    }

    /**
     * 测试内使用的 int 秒数转毫秒逻辑，不依赖生产优化代码。
     */
    private static long ttlSecondsToMillis(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be greater than zero");
        }
        return ttlSeconds * 1000L;
    }

    /**
     * 模拟 Duration 对象逃逸时的创建成本上界。
     */
    private static long durationCreateAndEscape(int iterations) {
        long total = 0L;
        for (int i = 0; i < iterations; i++) {
            Duration ttl = Duration.ofSeconds(TTL_SECONDS[i & 15]);
            objectSink = ttl;
            total += ttl.toMillis();
        }
        return total;
    }

    /**
     * 每次创建 Duration 后写入 Caffeine。
     */
    private static long caffeinePutDurationCreate(int iterations) {
        CaffeineCache cache = new CaffeineCache();
        for (int i = 0; i < iterations; i++) {
            cache.put(CACHE_NAME, KEY, VALUE, Duration.ofSeconds(TTL_SECONDS[i & 15]));
        }
        return cache.get(CACHE_NAME, KEY) == null ? 0L : iterations;
    }

    /**
     * 复用 Duration 后写入 Caffeine。
     */
    private static long caffeinePutDurationReuse(int iterations) {
        CaffeineCache cache = new CaffeineCache();
        for (int i = 0; i < iterations; i++) {
            cache.put(CACHE_NAME, KEY, VALUE, TTL_DURATIONS[i & 15]);
        }
        return cache.get(CACHE_NAME, KEY) == null ? 0L : iterations;
    }

    /**
     * 使用 int 秒数写入 Caffeine。
     */
    private static long caffeinePutInt(int iterations) {
        CaffeineCache cache = new CaffeineCache();
        for (int i = 0; i < iterations; i++) {
            cache.put(CACHE_NAME, KEY, VALUE, TTL_SECONDS[i & 15]);
        }
        return cache.get(CACHE_NAME, KEY) == null ? 0L : iterations;
    }

    /**
     * 每次创建 Duration 后命中读取 Caffeine。
     */
    private static long caffeineGetHitDurationCreate(int iterations) {
        CaffeineCache cache = new CaffeineCache();
        cache.put(CACHE_NAME, KEY, VALUE, Duration.ofMinutes(1));
        long hits = 0L;
        for (int i = 0; i < iterations; i++) {
            if (cache.get(CACHE_NAME, KEY, Duration.ofSeconds(TTL_SECONDS[i & 15]), SHOULD_NOT_LOAD) != null) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 复用 Duration 后命中读取 Caffeine。
     */
    private static long caffeineGetHitDurationReuse(int iterations) {
        CaffeineCache cache = new CaffeineCache();
        cache.put(CACHE_NAME, KEY, VALUE, Duration.ofMinutes(1));
        long hits = 0L;
        for (int i = 0; i < iterations; i++) {
            if (cache.get(CACHE_NAME, KEY, TTL_DURATIONS[i & 15], SHOULD_NOT_LOAD) != null) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 使用 int 秒数命中读取 Caffeine。
     */
    private static long caffeineGetHitInt(int iterations) {
        CaffeineCache cache = new CaffeineCache();
        cache.put(CACHE_NAME, KEY, VALUE, Duration.ofMinutes(1));
        long hits = 0L;
        for (int i = 0; i < iterations; i++) {
            if (cache.get(CACHE_NAME, KEY, TTL_SECONDS[i & 15], SHOULD_NOT_LOAD) != null) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 执行一个微基准任务。
     */
    private static Result benchmark(String name, int iterations, int warmupRounds,
                                    int measureRounds, Task task) {
        for (int i = 0; i < warmupRounds; i++) {
            longSink ^= task.run(iterations);
        }

        long[] elapsedNanos = new long[measureRounds];
        for (int i = 0; i < measureRounds; i++) {
            long start = System.nanoTime();
            longSink ^= task.run(iterations);
            elapsedNanos[i] = System.nanoTime() - start;
        }

        Arrays.sort(elapsedNanos);
        long medianNanos = elapsedNanos[elapsedNanos.length / 2];
        long bestNanos = elapsedNanos[0];
        return new Result(name, iterations, bestNanos, medianNanos);
    }

    /**
     * 输出测试分组标题。
     */
    private static void printSection(String title) {
        System.out.println();
        System.out.println(title);
        System.out.println(String.format(Locale.ROOT, "%-42s %12s %12s %12s",
                "case", "best ns/op", "median ns/op", "vs int"));
    }

    /**
     * 输出单个测试结果。
     */
    private static void printResult(Result result, Result intBaseline) {
        System.out.println(String.format(Locale.ROOT, "%-42s %12.2f %12.2f %11.2fx",
                result.name,
                result.bestNanosPerOperation(),
                result.medianNanosPerOperation(),
                result.medianNanosPerOperation() / intBaseline.medianNanosPerOperation()));
    }

    /**
     * 创建可复用 Duration 数组。
     */
    private static Duration[] createDurations() {
        Duration[] durations = new Duration[TTL_SECONDS.length];
        for (int i = 0; i < TTL_SECONDS.length; i++) {
            durations[i] = Duration.ofSeconds(TTL_SECONDS[i]);
        }
        return durations;
    }

    /**
     * 可计时任务。
     */
    private interface Task {

        /**
         * 执行指定次数并返回防止 JIT 删除循环的结果。
         */
        long run(int iterations);
    }

    /**
     * 微基准结果。
     */
    private static final class Result {

        private final String name;
        private final int iterations;
        private final long bestNanos;
        private final long medianNanos;

        /**
         * 创建结果。
         */
        private Result(String name, int iterations, long bestNanos, long medianNanos) {
            this.name = name;
            this.iterations = iterations;
            this.bestNanos = bestNanos;
            this.medianNanos = medianNanos;
        }

        /**
         * 最好单次耗时。
         */
        private double bestNanosPerOperation() {
            return (double) bestNanos / iterations;
        }

        /**
         * 中位数单次耗时。
         */
        private double medianNanosPerOperation() {
            return (double) medianNanos / iterations;
        }
    }
}
