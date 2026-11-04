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

/**
 * 让同一缓存项的操作共用一把锁，不同项分散到 128 把锁。
 */
final class CaffeineLocks {

    // 使用位掩码选锁，数量必须为 2 的幂。
    private final Object[] locks = new Object[128];

    CaffeineLocks() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    Object forKey(CaffeineCacheKey key) {
        return locks[key.hashCode() & (locks.length - 1)];
    }
}
