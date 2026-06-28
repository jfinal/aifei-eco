/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.caffeine;

/**
 * 表示 Caffeine 中由缓存名称和业务键组成的键。
 */
final class CaffeineCacheKey {

    private final String cacheName;
    private final String key;

    /**
     * 创建组合键。
     */
    CaffeineCacheKey(String cacheName, String key) {
        this.cacheName = cacheName;
        this.key = key;
    }

    /**
     * 判断该键是否属于指定缓存名称或其下级。
     */
    boolean belongsTo(String cacheName) {
        int length = cacheName.length();
        return this.cacheName.startsWith(cacheName)
                && (this.cacheName.length() == length || this.cacheName.charAt(length) == ':');
    }

    /**
     * 比较缓存名称和业务键是否相同。
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CaffeineCacheKey)) {
            return false;
        }
        CaffeineCacheKey other = (CaffeineCacheKey) object;
        return cacheName.equals(other.cacheName) && key.equals(other.key);
    }

    /**
     * 计算组合键的哈希值。
     */
    @Override
    public int hashCode() {
        int result = cacheName.hashCode();
        return 31 * result + key.hashCode();
    }

    /**
     * 返回便于调试查看的组合键内容。
     */
    @Override
    public String toString() {
        return "{cacheName='" + cacheName + "', key='" + key + "'}";
    }
}
