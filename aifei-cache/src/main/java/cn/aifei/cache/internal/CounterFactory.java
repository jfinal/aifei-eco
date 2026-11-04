/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
