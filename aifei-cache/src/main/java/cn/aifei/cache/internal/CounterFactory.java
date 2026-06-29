/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.internal;

import cn.aifei.cache.Counter;

/**
 * 由缓存实现提供同源计数器的内部装配接口。
 * <p>仅供插件装配代码使用，使计数器创建依附于具体缓存实现和配置，
 * 避免把计数器工厂方法加入 {@code Cache} 公共接口，或暴露底层客户端。</p>
 */
public interface CounterFactory {

    /**
     * 创建与当前缓存实现和配置对应的计数器。
     */
    Counter createCounter();
}
