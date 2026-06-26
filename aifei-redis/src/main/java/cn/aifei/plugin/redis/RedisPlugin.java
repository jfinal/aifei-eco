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

package cn.aifei.plugin.redis;

import cn.aifei.plugin.Plugin;
import cn.aifei.plugin.redis.serializer.FurySerializer;
import cn.aifei.util.StrUtil;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import java.util.function.Consumer;
import cn.aifei.plugin.redis.serializer.Serializer;

/**
 * RedisPlugin.
 * RedisPlugin 支持多个 Redis 服务端，只需要创建多个 RedisPlugin 对象
 * 对应这多个不同的 Redis 服务端即可。也支持多个 RedisPlugin 对象对应同一
 * Redis 服务的不同 database，具体例子见 jfinal 手册
 */
public class RedisPlugin implements Plugin {

    protected volatile boolean started = false;

    protected String cacheName;

    protected String host;
    protected Integer port = null;
    protected Integer timeout = null;
    protected String password = null;
    protected Integer database = null;
    protected String clientName = null;

    protected Serializer serializer = null;
    protected KeyNamingPolicy keyNamingPolicy = null;
    protected JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();

    public RedisPlugin(String cacheName, String host) {
        if (StrUtil.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName can not be blank.");
        }
        if (StrUtil.isBlank(host)) {
            throw new IllegalArgumentException("host can not be blank.");
        }
        this.cacheName = cacheName.trim();
        this.host = host;
    }

    public RedisPlugin(String cacheName, String host, int port) {
        this(cacheName, host);
        this.port = port;
    }

    public RedisPlugin(String cacheName, String host, int port, int timeout) {
        this(cacheName, host, port);
        this.timeout = timeout;
    }

    public RedisPlugin(String cacheName, String host, int port, int timeout, String password) {
        this(cacheName, host, port, timeout);
        // 当 password 未指定时 jedis 底层不进行 auth 也可以进行操作
        // if (StrUtil.isBlank(password)) {
        //     throw new IllegalArgumentException("password can not be blank.");
        // }
        this.password = password;
    }

    public RedisPlugin(String cacheName, String host, int port, int timeout, String password, int database) {
        this(cacheName, host, port, timeout, password);
        this.database = database;
    }

    public RedisPlugin(String cacheName, String host, int port, int timeout, String password, int database, String clientName) {
        this(cacheName, host, port, timeout, password, database);
        if (StrUtil.isBlank(clientName)) {
            throw new IllegalArgumentException("clientName can not be blank.");
        }
        this.clientName = clientName;
    }

    public RedisPlugin(String cacheName, String host, int port, String password) {
        this(cacheName, host, port, Protocol.DEFAULT_TIMEOUT, password);
    }

    public RedisPlugin(String cacheName, String host, String password) {
        this(cacheName, host, Protocol.DEFAULT_PORT, Protocol.DEFAULT_TIMEOUT, password);
    }

    public void start() {
        if (started) {
            return;
        }

        JedisPool jedisPool;
        if (port != null && timeout != null && database != null && clientName != null) {
            jedisPool = new JedisPool(jedisPoolConfig, host, port, timeout, password, database, clientName);
        } else if (port != null && timeout != null && database != null) {
            jedisPool = new JedisPool(jedisPoolConfig, host, port, timeout, password, database);
        } else if (port != null && timeout != null) {
            jedisPool = new JedisPool(jedisPoolConfig, host, port, timeout, password);
        // } else if (port != null && timeout != null) {
        //     jedisPool = new JedisPool(jedisPoolConfig, host, port, timeout);
        } else if (port != null) {
            jedisPool = new JedisPool(jedisPoolConfig, host, port);
        } else {
            jedisPool = new JedisPool(jedisPoolConfig, host);
        }

        if (serializer == null) {
            serializer = FurySerializer.me;
        }
        if (keyNamingPolicy == null) {
            keyNamingPolicy = KeyNamingPolicy.defaultKeyNamingPolicy;
        }

        Cache cache = new Cache(cacheName, jedisPool, serializer, keyNamingPolicy);
        Redis.addCache(cache);

        started = true;
    }

    public void stop() {
        Cache cache = Redis.removeCache(cacheName);
        if (cache == Redis.mainCache) {
            Redis.mainCache = null;
        }
        cache.jedisPool.destroy();

        started = false;
    }

    /**
     * 当RedisPlugin 提供的设置属性仍然无法满足需求时，通过此方法获取到
     * JedisPoolConfig 对象，可对 redis 进行更加细致的配置
     * <pre>
     * 例如：
     * redisPlugin.getJedisPoolConfig().setMaxTotal(100);
     * </pre>
     */
    public JedisPoolConfig getJedisPoolConfig() {
        return jedisPoolConfig;
    }

    /**
     * lambda 方式配置 JedisPoolConfig
     * <pre>
     * 例子：
     *   RedisPlugin redisPlugin = new RedisPlugin(...);
     *   redisPlugin.config(c -> {
     *       c.setMaxIdle(123456);
     *   });
     * </pre>
     */
    public void config(Consumer<JedisPoolConfig> config) {
        config.accept(jedisPoolConfig);
    }

    // ---------

    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
        SerializerUtil.serializer = serializer;
    }

    public void setKeyNamingPolicy(KeyNamingPolicy keyNamingPolicy) {
        this.keyNamingPolicy = keyNamingPolicy;
    }

    // ---------

    public void setTestWhileIdle(boolean testWhileIdle) {
        jedisPoolConfig.setTestWhileIdle(testWhileIdle);
    }

    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        jedisPoolConfig.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
    }

    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        jedisPoolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
    }

    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        jedisPoolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }
}


