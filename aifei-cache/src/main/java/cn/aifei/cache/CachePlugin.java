/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache;

import cn.aifei.aop.AopKit;
import cn.aifei.cache.internal.CounterFactory;
import cn.aifei.plugin.Plugin;
import java.util.Objects;

/**
 * 向 aifei 注册缓存实例并管理其生命周期。
 */
public class CachePlugin implements Plugin {

    private final Cache cache;
    private final Counter counter;
    private volatile boolean started;

    /**
     * 创建缓存插件，并根据缓存实现创建对应的计数器。
     *
     * @param cache 需要注册和管理的缓存实例
     */
    public CachePlugin(Cache cache) {
        this(Objects.requireNonNull(cache, "cache can not be null"), createCounter(cache));
    }

    /**
     * 创建缓存与计数插件。
     */
    private CachePlugin(Cache cache, Counter counter) {
        this.cache = Objects.requireNonNull(cache, "cache can not be null");
        this.counter = Objects.requireNonNull(counter, "counter can not be null");
    }

    /**
     * 根据缓存实现创建同源计数器。
     */
    private static Counter createCounter(Cache cache) {
        if (cache instanceof CounterFactory) {
            Counter counter = ((CounterFactory) cache).createCounter();
            return Objects.requireNonNull(counter, "counter can not be null");
        }
        throw new IllegalArgumentException(
                "Unsupported cache implementation for automatic counter creation: " + cache.getClass().getName()
        );
    }

    /**
     * 注册缓存和计数单例；重复启动不会重复注册。
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        AopKit.get().addSingletonObject(Cache.class, cache);
        AopKit.get().addSingletonObject(Counter.class, counter);
        started = true;
    }

    /**
     * 关闭缓存和计数实例；重复停止不会重复关闭。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        try {
            close(cache);
            if (counter instanceof AutoCloseable && counter != cache) {
                ((AutoCloseable) counter).close();
            }
            started = false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to close cache plugin", e);
        }
    }

    /**
     * 按需关闭实例。
     */
    private static void close(Object target) throws Exception {
        if (target instanceof AutoCloseable) {
            ((AutoCloseable) target).close();
        }
    }
}
