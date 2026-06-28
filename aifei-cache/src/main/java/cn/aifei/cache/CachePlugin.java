/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import java.util.Objects;

/**
 * 向 aifei 注册缓存实例并管理其生命周期。
 */
public class CachePlugin implements Plugin {

    private final Cache cache;
    private volatile boolean started;

    /**
     * 创建缓存插件。
     *
     * @param cache 需要注册和管理的缓存实例
     */
    public CachePlugin(Cache cache) {
        this.cache = Objects.requireNonNull(cache, "cache can not be null");
    }

    /**
     * 注册缓存单例；重复启动不会重复注册。
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        AopKit.get().addSingletonObject(Cache.class, cache);
        started = true;
    }

    /**
     * 关闭缓存实例；重复停止不会重复关闭。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        try {
            if (cache instanceof AutoCloseable) {
                ((AutoCloseable) cache).close();
            }
            started = false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to close cache", e);
        }
    }
}
