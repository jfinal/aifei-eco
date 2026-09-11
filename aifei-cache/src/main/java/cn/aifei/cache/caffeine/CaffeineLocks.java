/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
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
