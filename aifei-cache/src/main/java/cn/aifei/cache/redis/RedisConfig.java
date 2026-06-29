/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cache.redis;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultRedisCredentials;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.JedisURIHelper;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/**
 * Redis 连接、连接池与 value 编解码配置。
 */
public class RedisConfig {

    private static final String DEFAULT_HOST = Protocol.DEFAULT_HOST;
    private static final int DEFAULT_PORT = Protocol.DEFAULT_PORT;
    private static final int DEFAULT_TIMEOUT_MILLIS = 2_000;
    private static final int DEFAULT_BLOCKING_SOCKET_TIMEOUT_MILLIS = 0;
    private static final int DEFAULT_MAX_TOTAL = 32;
    private static final int DEFAULT_MAX_IDLE = 16;
    private static final int DEFAULT_MIN_IDLE = 1;
    private static final long DEFAULT_MAX_WAIT_MILLIS = 3_000L;
    private static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = 60_000L;
    private static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 600_000L;
    private static final long DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 120_000L;
    private static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 8;
    private static final String DEFAULT_JMX_NAME_PREFIX = "Aifei-Cache-Redis";

    private URI redisUri;
    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String user;
    private String password;
    private Integer database;
    private String clientName;
    private Boolean ssl;
    private SSLSocketFactory sslSocketFactory;
    private SSLParameters sslParameters;
    private HostnameVerifier hostnameVerifier;
    private Boolean resp3;
    private Integer timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private Integer connectionTimeoutMillis;
    private Integer socketTimeoutMillis;
    private Integer blockingSocketTimeoutMillis = DEFAULT_BLOCKING_SOCKET_TIMEOUT_MILLIS;
    private int maxTotal = DEFAULT_MAX_TOTAL;
    private int maxIdle = DEFAULT_MAX_IDLE;
    private int minIdle = DEFAULT_MIN_IDLE;
    private boolean maxIdleConfigured;
    private boolean minIdleConfigured;
    private long maxWaitMillis = DEFAULT_MAX_WAIT_MILLIS;
    private Boolean blockWhenExhausted = true;
    private Boolean lifo = true;
    private Boolean fairness = false;
    private Boolean testOnCreate = false;
    private Boolean testOnBorrow = false;
    private Boolean testOnReturn = false;
    private Boolean testWhileIdle = true;
    private Long timeBetweenEvictionRunsMillis = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
    private Long minEvictableIdleTimeMillis = DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private Long softMinEvictableIdleTimeMillis = DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private Integer numTestsPerEvictionRun = DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
    private Boolean jmxEnabled = true;
    private String jmxNamePrefix = DEFAULT_JMX_NAME_PREFIX;
    private String jmxNameBase;
    private RedisValueCodec valueCodec;

    /**
     * 使用指定 Redis URI。
     */
    public RedisConfig uri(String redisUri) {
        return uri(URI.create(Objects.requireNonNull(redisUri, "redisUri can not be null")));
    }

    /**
     * 使用指定 Redis URI。
     */
    public RedisConfig uri(URI redisUri) {
        this.redisUri = Objects.requireNonNull(redisUri, "redisUri can not be null");
        return this;
    }

    /**
     * 设置 Redis 主机。
     */
    public RedisConfig host(String host) {
        this.host = requireText(host, "host");
        return this;
    }

    /**
     * 设置 Redis 端口。
     */
    public RedisConfig port(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        this.port = port;
        return this;
    }

    /**
     * 设置 Redis ACL 用户名。
     */
    public RedisConfig user(String user) {
        this.user = requireText(user, "user");
        return this;
    }

    /**
     * 设置 Redis 密码。
     */
    public RedisConfig password(String password) {
        this.password = requireText(password, "password");
        return this;
    }

    /**
     * 设置 Redis database。
     */
    public RedisConfig database(int database) {
        if (database < 0) {
            throw new IllegalArgumentException("database must be greater than or equal to 0");
        }
        this.database = database;
        return this;
    }

    /**
     * 设置 Redis 客户端名称。
     */
    public RedisConfig clientName(String clientName) {
        this.clientName = requireText(clientName, "clientName");
        return this;
    }

    /**
     * 是否使用 SSL。
     */
    public RedisConfig ssl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    /**
     * 设置 SSL socket 工厂。
     */
    public RedisConfig sslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = Objects.requireNonNull(sslSocketFactory, "sslSocketFactory can not be null");
        return this;
    }

    /**
     * 设置 SSL 参数。
     */
    public RedisConfig sslParameters(SSLParameters sslParameters) {
        this.sslParameters = Objects.requireNonNull(sslParameters, "sslParameters can not be null");
        return this;
    }

    /**
     * 设置 SSL 主机名校验器。
     */
    public RedisConfig hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = Objects.requireNonNull(hostnameVerifier, "hostnameVerifier can not be null");
        return this;
    }

    /**
     * 使用 RESP3 协议。
     */
    public RedisConfig resp3() {
        this.resp3 = true;
        return this;
    }

    /**
     * 同时设置连接超时和 socket 读写超时时间，单位毫秒。
     */
    public RedisConfig timeoutMillis(int timeoutMillis) {
        this.timeoutMillis = requirePositive(timeoutMillis, "timeoutMillis");
        return this;
    }

    /**
     * 设置连接超时时间，单位毫秒。
     */
    public RedisConfig connectionTimeoutMillis(int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = requirePositive(connectionTimeoutMillis, "connectionTimeoutMillis");
        return this;
    }

    /**
     * 设置 socket 读写超时时间，单位毫秒。
     */
    public RedisConfig socketTimeoutMillis(int socketTimeoutMillis) {
        this.socketTimeoutMillis = requirePositive(socketTimeoutMillis, "socketTimeoutMillis");
        return this;
    }

    /**
     * 设置阻塞命令 socket 超时时间，单位毫秒。0 表示不超时。
     */
    public RedisConfig blockingSocketTimeoutMillis(int blockingSocketTimeoutMillis) {
        this.blockingSocketTimeoutMillis =
                requireNonNegative(blockingSocketTimeoutMillis, "blockingSocketTimeoutMillis");
        return this;
    }

    /**
     * 设置连接池最大连接数。-1 表示不限制。
     */
    public RedisConfig maxTotal(int maxTotal) {
        if (maxTotal != -1 && maxTotal <= 0) {
            throw new IllegalArgumentException("maxTotal must be -1 or greater than 0");
        }
        this.maxTotal = maxTotal;
        return this;
    }

    /**
     * 设置连接池最大空闲连接数。
     */
    public RedisConfig maxIdle(int maxIdle) {
        if (maxIdle < 0) {
            throw new IllegalArgumentException("maxIdle must be greater than or equal to 0");
        }
        this.maxIdle = maxIdle;
        this.maxIdleConfigured = true;
        return this;
    }

    /**
     * 设置连接池最小空闲连接数。
     */
    public RedisConfig minIdle(int minIdle) {
        if (minIdle < 0) {
            throw new IllegalArgumentException("minIdle must be greater than or equal to 0");
        }
        this.minIdle = minIdle;
        this.minIdleConfigured = true;
        return this;
    }

    /**
     * 设置连接池等待可用连接的最长时间，单位毫秒。-1 表示一直等待。
     */
    public RedisConfig maxWaitMillis(long maxWaitMillis) {
        if (maxWaitMillis < -1) {
            throw new IllegalArgumentException("maxWaitMillis must be greater than or equal to -1");
        }
        this.maxWaitMillis = maxWaitMillis;
        return this;
    }

    /**
     * 连接池耗尽时是否阻塞等待。
     */
    public RedisConfig blockWhenExhausted(boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
        return this;
    }

    /**
     * 借出连接时是否优先使用最近归还的空闲连接。
     */
    public RedisConfig lifo(boolean lifo) {
        this.lifo = lifo;
        return this;
    }

    /**
     * 连接池耗尽时是否使用公平等待队列。
     */
    public RedisConfig fairness(boolean fairness) {
        this.fairness = fairness;
        return this;
    }

    /**
     * 创建连接时是否执行校验。
     */
    public RedisConfig testOnCreate(boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
        return this;
    }

    /**
     * 借出连接时是否执行校验。
     */
    public RedisConfig testOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        return this;
    }

    /**
     * 归还连接时是否执行校验。
     */
    public RedisConfig testOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        return this;
    }

    /**
     * 空闲连接扫描时是否执行校验。
     */
    public RedisConfig testWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
        return this;
    }

    /**
     * 设置空闲连接扫描间隔，单位毫秒。-1 表示不启用后台扫描。
     */
    public RedisConfig timeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis =
                requireMillisGreaterThanOrEqualToMinusOne(
                        timeBetweenEvictionRunsMillis,
                        "timeBetweenEvictionRunsMillis"
                );
        return this;
    }

    /**
     * 设置空闲连接最小可淘汰时间，单位毫秒。-1 表示不按该条件淘汰。
     */
    public RedisConfig minEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis =
                requireMillisGreaterThanOrEqualToMinusOne(
                        minEvictableIdleTimeMillis,
                        "minEvictableIdleTimeMillis"
                );
        return this;
    }

    /**
     * 设置保留 minIdle 时的空闲连接最小可淘汰时间，单位毫秒。-1 表示不按该条件淘汰。
     */
    public RedisConfig softMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
        this.softMinEvictableIdleTimeMillis =
                requireMillisGreaterThanOrEqualToMinusOne(
                        softMinEvictableIdleTimeMillis,
                        "softMinEvictableIdleTimeMillis"
                );
        return this;
    }

    /**
     * 设置每次空闲连接扫描检查的连接数量。
     */
    public RedisConfig numTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = requirePositive(numTestsPerEvictionRun, "numTestsPerEvictionRun");
        return this;
    }

    /**
     * 是否启用连接池 JMX。
     */
    public RedisConfig jmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
        return this;
    }

    /**
     * 设置连接池 JMX 名称前缀。
     */
    public RedisConfig jmxNamePrefix(String jmxNamePrefix) {
        this.jmxNamePrefix = requireText(jmxNamePrefix, "jmxNamePrefix");
        return this;
    }

    /**
     * 设置连接池 JMX 名称基础部分。
     */
    public RedisConfig jmxNameBase(String jmxNameBase) {
        this.jmxNameBase = requireText(jmxNameBase, "jmxNameBase");
        return this;
    }

    /**
     * 设置 Redis value 编解码器。
     */
    public RedisConfig valueCodec(RedisValueCodec valueCodec) {
        this.valueCodec = Objects.requireNonNull(valueCodec, "valueCodec can not be null");
        return this;
    }

    /**
     * 复制当前配置，供缓存与计数器共享同一组装配参数。
     */
    RedisConfig copy() {
        RedisConfig copy = new RedisConfig();
        copy.redisUri = redisUri;
        copy.host = host;
        copy.port = port;
        copy.user = user;
        copy.password = password;
        copy.database = database;
        copy.clientName = clientName;
        copy.ssl = ssl;
        copy.sslSocketFactory = sslSocketFactory;
        copy.sslParameters = sslParameters;
        copy.hostnameVerifier = hostnameVerifier;
        copy.resp3 = resp3;
        copy.timeoutMillis = timeoutMillis;
        copy.connectionTimeoutMillis = connectionTimeoutMillis;
        copy.socketTimeoutMillis = socketTimeoutMillis;
        copy.blockingSocketTimeoutMillis = blockingSocketTimeoutMillis;
        copy.maxTotal = maxTotal;
        copy.maxIdle = maxIdle;
        copy.minIdle = minIdle;
        copy.maxIdleConfigured = maxIdleConfigured;
        copy.minIdleConfigured = minIdleConfigured;
        copy.maxWaitMillis = maxWaitMillis;
        copy.blockWhenExhausted = blockWhenExhausted;
        copy.lifo = lifo;
        copy.fairness = fairness;
        copy.testOnCreate = testOnCreate;
        copy.testOnBorrow = testOnBorrow;
        copy.testOnReturn = testOnReturn;
        copy.testWhileIdle = testWhileIdle;
        copy.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        copy.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        copy.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
        copy.numTestsPerEvictionRun = numTestsPerEvictionRun;
        copy.jmxEnabled = jmxEnabled;
        copy.jmxNamePrefix = jmxNamePrefix;
        copy.jmxNameBase = jmxNameBase;
        copy.valueCodec = valueCodec;
        return copy;
    }

    /**
     * 根据当前配置创建 RedisClient。
     */
    RedisClient createClient() {
        RedisClient.Builder builder = RedisClient.builder();
        DefaultJedisClientConfig.Builder clientConfigBuilder;
        if (redisUri == null) {
            builder.hostAndPort(host, port);
            clientConfigBuilder = DefaultJedisClientConfig.builder();
        } else {
            builder.hostAndPort(JedisURIHelper.getHostAndPort(redisUri));
            clientConfigBuilder = DefaultJedisClientConfig.builder(redisUri);
        }
        applyClientConfig(clientConfigBuilder);
        builder.clientConfig(clientConfigBuilder.build());
        builder.poolConfig(createPoolConfig());
        return builder.build();
    }

    /**
     * 根据当前配置选择 Redis value 编解码器。
     */
    RedisValueCodec createValueCodec() {
        return valueCodec == null ? defaultValueCodec() : valueCodec;
    }

    /**
     * 返回默认 Redis value 编解码器。
     */
    static RedisValueCodec defaultValueCodec() {
        return DefaultValueCodecHolder.INSTANCE;
    }

    /**
     * 转换为 Jedis 连接池配置。
     */
    private ConnectionPoolConfig createPoolConfig() {
        int effectiveMaxIdle = effectiveMaxIdle();
        int effectiveMinIdle = effectiveMinIdle(effectiveMaxIdle);
        validatePoolBounds(maxTotal, effectiveMaxIdle, effectiveMinIdle);
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(effectiveMaxIdle);
        poolConfig.setMinIdle(effectiveMinIdle);
        poolConfig.setMaxWait(Duration.ofMillis(maxWaitMillis));
        if (blockWhenExhausted != null) {
            poolConfig.setBlockWhenExhausted(blockWhenExhausted);
        }
        if (lifo != null) {
            poolConfig.setLifo(lifo);
        }
        if (fairness != null) {
            poolConfig.setFairness(fairness);
        }
        if (testOnCreate != null) {
            poolConfig.setTestOnCreate(testOnCreate);
        }
        if (testOnBorrow != null) {
            poolConfig.setTestOnBorrow(testOnBorrow);
        }
        if (testOnReturn != null) {
            poolConfig.setTestOnReturn(testOnReturn);
        }
        if (testWhileIdle != null) {
            poolConfig.setTestWhileIdle(testWhileIdle);
        }
        if (timeBetweenEvictionRunsMillis != null) {
            poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
        }
        if (minEvictableIdleTimeMillis != null) {
            poolConfig.setMinEvictableIdleDuration(Duration.ofMillis(minEvictableIdleTimeMillis));
        }
        if (softMinEvictableIdleTimeMillis != null) {
            poolConfig.setSoftMinEvictableIdleDuration(Duration.ofMillis(softMinEvictableIdleTimeMillis));
        }
        if (numTestsPerEvictionRun != null) {
            poolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        }
        if (jmxEnabled != null) {
            poolConfig.setJmxEnabled(jmxEnabled);
        }
        if (jmxNamePrefix != null) {
            poolConfig.setJmxNamePrefix(jmxNamePrefix);
        }
        if (jmxNameBase != null) {
            poolConfig.setJmxNameBase(jmxNameBase);
        }
        return poolConfig;
    }

    /**
     * 应用 Redis 客户端配置。
     */
    @SuppressWarnings("deprecation")
    private void applyClientConfig(DefaultJedisClientConfig.Builder builder) {
        if (resp3 != null && resp3) {
            builder.resp3();
        }
        if (timeoutMillis != null) {
            builder.timeoutMillis(timeoutMillis);
        }
        if (user != null || password != null) {
            builder.credentials(new DefaultRedisCredentials(effectiveUser(), effectivePassword()));
        }
        if (database != null) {
            builder.database(database);
        }
        if (clientName != null) {
            builder.clientName(clientName);
        }
        if (ssl != null) {
            builder.ssl(ssl);
        } else if (sslSocketFactory != null || sslParameters != null || hostnameVerifier != null) {
            builder.ssl(true);
        }
        if (sslSocketFactory != null) {
            builder.sslSocketFactory(sslSocketFactory);
        }
        if (sslParameters != null) {
            builder.sslParameters(sslParameters);
        }
        if (hostnameVerifier != null) {
            builder.hostnameVerifier(hostnameVerifier);
        }
        if (connectionTimeoutMillis != null) {
            builder.connectionTimeoutMillis(connectionTimeoutMillis);
        }
        if (socketTimeoutMillis != null) {
            builder.socketTimeoutMillis(socketTimeoutMillis);
        }
        if (blockingSocketTimeoutMillis != null) {
            builder.blockingSocketTimeoutMillis(blockingSocketTimeoutMillis);
        }
    }

    /**
     * 校验非空白文本。
     */
    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " can not be blank");
        }
        return value;
    }

    /**
     * 校验正整数。
     */
    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        return value;
    }

    /**
     * 校验非负整数。
     */
    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to 0");
        }
        return value;
    }

    /**
     * 校验毫秒数不小于 -1。
     */
    private static long requireMillisGreaterThanOrEqualToMinusOne(long value, String name) {
        if (value < -1) {
            throw new IllegalArgumentException(name + " must be greater than or equal to -1");
        }
        return value;
    }

    /**
     * 返回显式配置覆盖 URI 后的 Redis 用户名。
     */
    private String effectiveUser() {
        if (user != null || redisUri == null) {
            return user;
        }
        return JedisURIHelper.getUser(redisUri);
    }

    /**
     * 返回显式配置覆盖 URI 后的 Redis 密码。
     */
    private String effectivePassword() {
        if (password != null || redisUri == null) {
            return password;
        }
        return JedisURIHelper.getPassword(redisUri);
    }

    /**
     * 校验连接池上下限关系。
     */
    private static void validatePoolBounds(int maxTotal, int maxIdle, int minIdle) {
        if (maxTotal != -1 && maxIdle > maxTotal) {
            throw new IllegalArgumentException("maxIdle must be less than or equal to maxTotal");
        }
        if (minIdle > maxIdle) {
            throw new IllegalArgumentException("minIdle must be less than or equal to maxIdle");
        }
    }

    /**
     * 计算有效最大空闲连接数，避免未显式配置的默认值阻止用户降低 maxTotal。
     */
    private int effectiveMaxIdle() {
        if (!maxIdleConfigured && maxTotal != -1 && maxIdle > maxTotal) {
            return maxTotal;
        }
        return maxIdle;
    }

    /**
     * 计算有效最小空闲连接数，避免未显式配置的默认值阻止用户降低 maxIdle。
     */
    private int effectiveMinIdle(int effectiveMaxIdle) {
        if (!minIdleConfigured && minIdle > effectiveMaxIdle) {
            return effectiveMaxIdle;
        }
        return minIdle;
    }

    /**
     * 惰性创建默认 codec，避免自定义 codec 场景强制加载 Fury。
     */
    private static final class DefaultValueCodecHolder {
        private static final RedisValueCodec INSTANCE = new FuryRedisValueCodec();
    }
}
