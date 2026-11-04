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

package cn.aifei.cache.caffeine;

import com.github.benmanes.caffeine.cache.Expiry;

/**
 * 保留每个计数项单独设置的有效期。
 */
final class CaffeineCounterExpiry implements Expiry<CaffeineCacheKey, Long> {

    static final CaffeineCounterExpiry INSTANCE = new CaffeineCounterExpiry();

    /**
     * 禁止创建额外实例。
     */
    private CaffeineCounterExpiry() {
    }

    /**
     * 新建计数项时先使用无限期，实际 TTL 由写入策略设置。
     */
    @Override
    public long expireAfterCreate(CaffeineCacheKey key, Long value, long currentTime) {
        return Long.MAX_VALUE;
    }

    /**
     * 更新计数项时保留当前有效期。
     */
    @Override
    public long expireAfterUpdate(CaffeineCacheKey key, Long value, long currentTime, long currentDuration) {
        return currentDuration;
    }

    /**
     * 读取计数项时不延长有效期。
     */
    @Override
    public long expireAfterRead(CaffeineCacheKey key, Long value, long currentTime, long currentDuration) {
        return currentDuration;
    }
}
