# aifei-cache

aifei-cache 是为 [aifei](https://aifei.cn) 提供的极简缓存与计数组件，支持应用从单实例部署平滑演进到集群部署。

## 设计目标

- 业务代码只依赖统一的 `Cache` 和 `Counter` 接口，不依赖具体实现。
- 通过 aifei 的 `@Inject` 注解注入所选的缓存和计数实现。
- 单实例部署时使用基于 Caffeine 的本地缓存和计数实现。
- 集群部署时使用基于 Redis 的分布式缓存和计数实现。
- 切换实现时，不需要修改依赖接口的业务代码。

## 缓存实现

### CaffeineCache

适用于应用生命周期前期或流量较低时的单实例部署。缓存数据保存在当前应用进程内，无须部署额外的缓存服务。

### CaffeineCounter

适用于单实例部署下的临时计数。计数数据保存在当前应用进程内，支持同 key 原子加减和独立 TTL。

### RedisCache

适用于流量增长后的集群部署。多个应用实例通过 Redis 共享缓存数据，保持集群内缓存访问的一致性。

### RedisCounter

适用于集群部署下的临时计数。计数数据使用 Redis 原生 integer value，与普通缓存序列化值隔离。

## 部署演进

```text
单实例应用                           集群应用
业务代码 -> Cache/Counter -> Caffeine 实现   业务代码 -> Cache/Counter -> Redis 实现
```

两种部署方式使用同一组公共接口。业务代码保持不变，只需在应用配置或依赖注入层切换具体实现。

## Maven 依赖

```xml
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0</version>
</dependency>
```

Caffeine、Jedis 和 Fury 是可选依赖。使用 `CaffeineCache` 时还需添加：

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>2.9.3</version>
</dependency>
```

使用 `RedisCache` 默认 Fury 编解码器时还需添加：

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>7.5.2</version>
</dependency>
<dependency>
    <groupId>org.apache.fury</groupId>
    <artifactId>fury-core</artifactId>
    <version>0.10.3</version>
</dependency>
```

如果通过 `RedisConfig.valueCodec(...)` 使用自定义 Redis value 编解码器，可以不添加 `fury-core`，
但需要自行添加该编解码器所需的运行时依赖。

## Cache API

```java
public interface Cache {

    <T> T get(String cacheName, String key);

    <T> T get(String cacheName, String key, Duration ttl, Supplier<T> loader);

    <T> T get(String cacheName, String key, int ttlSeconds, Supplier<T> loader);

    boolean exists(String cacheName, String key);

    void put(String cacheName, String key, Object value, Duration ttl);

    void put(String cacheName, String key, Object value, int ttlSeconds);

    boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl);

    boolean putIfAbsent(String cacheName, String key, Object value, int ttlSeconds);

    boolean expire(String cacheName, String key, Duration ttl);

    boolean expire(String cacheName, String key, int ttlSeconds);

    void remove(String cacheName, String key);

    void clear(String cacheName);
}
```

常规用法：

```java
@Inject
Cache cache;

cache.put("user", "42", user, 1800);
User cached = cache.get("user", "42");
boolean exists = cache.exists("user", "42");
boolean created = cache.putIfAbsent("job", "daily-report", "running", 300);
boolean renewed = cache.expire("user", "42", 1800);
cache.remove("user", "42");
```

### 写入后固定 TTL / 绝对过期

缓存场景里，“固定窗口”更准确地说是写入后固定 TTL 或绝对过期：缓存项在写入或首次创建时确定过期时间，后续读取、存在性判断或 `putIfAbsent` 命中都不会延长有效期。它适合验证码、一次性 token、提交幂等标记、发送冷却标记、短期任务状态等必须在固定时间后自然失效的场景。

```java
@Inject
Cache cache;

cache.put("sms:code", phone, code, 300);

String expectedCode = cache.get("sms:code", phone);
if (expectedCode == null || !expectedCode.equals(submittedCode)) {
    throw new InvalidCodeException();
}

cache.remove("sms:code", phone);
```

`putIfAbsent` 也常用于固定 TTL 的冷却或去重标记。标记已存在时不会覆盖原值，也不会重置原 TTL：

```java
boolean allowed = cache.putIfAbsent("sms:cooldown", phone, "1", 60);
if (!allowed) {
    throw new TooManyRequestsException();
}
```

通过 Loader 加载的数据也是固定 TTL：命中时直接返回缓存值，不调用 Loader，也不会因为读取而续期。

```java
UserProfile profile = cache.get(
        "user:profile",
        userId,
        1800,
        () -> userService.loadProfile(userId)
);
```

### 访问续期 / 闲置过期

用户常说的 session 滑动过期，在缓存语境里更准确地说是访问续期或闲置过期：缓存项每次被业务确认仍然有效后，显式调用 `expire` 把剩余有效期重设为指定 TTL。它适合登录 session、临时会话状态、在线用户上下文、编辑草稿等“最后一次访问后 N 秒过期”的场景。

```java
@Inject
Cache cache;

Session session = cache.get("login:session", sessionId);
if (session == null || !session.isValid()) {
    throw new UnauthorizedException();
}

cache.expire("login:session", sessionId, 1800);
```

`expire` 只续期已存在且未过期的缓存项，不读取、不修改缓存值；缓存项不存在或已过期时返回 `false`。因此访问续期通常放在业务校验通过之后：

```java
CartDraft draft = cache.get("cart:draft", userId);
if (draft != null) {
    cache.expire("cart:draft", userId, 7200);
}
```

`cacheName` 和 `key` 不能为空白，均可使用冒号分级。`clear("user")` 会同时清理 `user:profile` 等下级缓存。所有写入和续期都必须指定大于零的 TTL。缓存未命中、条目过期，或普通 `get` 收到非法 `cacheName`/`key` 时，返回 `null`。`exists` 在缓存项存在且未过期时返回 `true`，缓存项不存在、已过期或收到非法 `cacheName`/`key` 时返回 `false`。`putIfAbsent` 只在缓存项不存在或已过期时写入并返回 `true`；缓存项存在且未过期时不覆盖原值、不重置原 TTL，并返回 `false`。`expire` 只在缓存项存在且未过期时重设剩余有效期并返回 `true`，不读取、不修改缓存值；缓存项不存在或已过期时返回 `false`。`remove` 收到非法 `cacheName`/`key` 时按未命中处理，不删除任何缓存项。除普通 `get`、`exists` 和 `remove` 的非法 `cacheName`/`key` 外，参数校验失败统一抛出 `IllegalArgumentException`。

通过 Loader 可以在缓存未命中时加载、缓存并返回数据：

```java
User user = cache.get(
        "user",
        userId,
        1800,
        () -> userService.findById(userId)
);
```

带 Loader 的 `get` 会先校验 `cacheName`、`key` 和 TTL，参数非法时不会调用 Loader。Loader 返回 `null` 时不会写入缓存，其异常会直接向调用方传播。该机制不提供防缓存击穿保证，并发未命中时 Loader 可能被重复执行。

秒是常用单位；需要毫秒等精度时使用 `Duration` 重载。

项目不提供缓存专用公共异常类型。Caffeine、Redis 连接、命令执行、Redis value 编解码器和 JDK 标准库运行时异常会按原始类型传播。

## Counter API

```java
public interface Counter {

    Long get(String counterName, String key);

    long increase(String counterName, String key, long step, Duration ttl);

    long increase(String counterName, String key, long step, int ttlSeconds);

    long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);

    long increaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds);

    long decrease(String counterName, String key, long step, Duration ttl);

    long decrease(String counterName, String key, long step, int ttlSeconds);

    long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);

    long decreaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds);

    void remove(String counterName, String key);
}
```

`counterName` 和 `key` 不能为空白，均可使用冒号分级。计数项不存在或已过期时，`get` 返回 `null`，增减方法按当前值 `0` 创建新计数项并使用传入 TTL。`step` 必须大于零，超过 `long` 范围时抛出 `ArithmeticException`。

### 固定窗口计数

`increase` 和 `decrease` 用于固定窗口计数：计数项第一次创建时确定过期时间，后续命中只更新数值，不延长窗口。它适合“从窗口开始后的 N 秒内累计多少次”这类场景，例如固定窗口限流、固定周期配额、验证码发送次数、文章当日阅读数等。

```java
@Inject
Counter counter;

long requests = counter.increase("api:request", userId, 1, 60);
if (requests > 100) {
    throw new TooManyRequestsException();
}
```

按自然周期统计时，推荐把周期放进 `counterName`，再用 TTL 控制历史计数自动清理：

```java
String day = LocalDate.now().toString();
long views = counter.increase("article:views:" + day, articleId, 1, 172800);
```

### 滑动窗口/闲置过期计数

`increaseAndRefreshTtl` 和 `decreaseAndRefreshTtl` 用于闲置过期计数：每次更新计数值时都会把 TTL 刷新为传入值。它适合“最后一次活动后 N 秒过期”这类场景，例如登录失败次数、会话内操作次数、用户活跃状态计数、临时冷却计数等。

```java
@Inject
Counter counter;

long failures = counter.increaseAndRefreshTtl("login:fail", userId, 1, 900);
if (failures >= 5) {
    accountService.lockTemporarily(userId);
}
```

这种语义常被业务上称为“滑动过期”或“闲置过期”，但它不等价于精确的最近 N 秒滑动窗口统计。如果用户持续活跃，计数项会持续续期并继续累计；如果需要严格统计“最近 N 秒内发生了多少次”，应使用专门的时间桶或时间序列算法。

```java
long operations = counter.increaseAndRefreshTtl("session:ops", sessionId, 1, 1800);
Long currentOperations = counter.get("session:ops", sessionId);
```

固定窗口和闲置过期可以在同一个应用中同时使用，关键是根据业务语义选择不同方法：

```java
long dailyViews = counter.increase("article:views:" + day, articleId, 1, 172800);
long recentFailures = counter.increaseAndRefreshTtl("login:fail", userId, 1, 900);
counter.remove("login:fail", userId);
```

`Counter` 与 `Cache` 是独立接口。精确计数需求应使用 `Counter`，不要通过 `Cache` 的 `get + put` 自行实现原子计数。

## 配置与注入

单实例部署使用 Caffeine。默认最多缓存 10000 个条目，也可以通过构造参数覆盖：

```java
@Override
public void config(Plugins plugins) {
    plugins.add(new CachePlugin(new CaffeineCache()));
}
```

```java
plugins.add(new CachePlugin(new CaffeineCache(50_000)));
```

集群部署使用 Redis：

```java
@Override
public void config(Plugins plugins) {
    URI redisUri = URI.create("redis://user:password@127.0.0.1:6379/0");
    plugins.add(new CachePlugin(new RedisCache(redisUri)));
}
```

生产环境可以使用 `RedisConfig` 配置连接、认证、连接池和 value 编解码器。大多数应用只需要设置 Redis 地址和认证信息：

```java
@Override
public void config(Plugins plugins) {
    RedisConfig config = new RedisConfig()
            .uri("redis://127.0.0.1:6379/0")
            .user("default")
            .password("password");

    plugins.add(new CachePlugin(new RedisCache(config)));
}
```

也可以不用 URI，分别设置主机、端口和 database：

```java
RedisConfig config = new RedisConfig()
        .host("127.0.0.1")
        .port(6379)
        .password("password")
        .database(0);
```

默认 host 与 Jedis 保持一致，为 `127.0.0.1`。没有默认使用 `localhost`，这样可以避免本机 hosts、DNS 或 IPv6 优先级差异影响连接行为。

`RedisConfig` 的默认值按普通中小型 Web 应用设计：

- 连接和 socket 超时默认 2000 毫秒；阻塞命令 socket 超时默认 0。
- 连接池默认 `maxTotal=32`、`maxIdle=16`、`minIdle=1`、`maxWaitMillis=3000`。
- 连接池耗尽时默认最多等待 3000 毫秒，不无限等待。
- 只降低 `maxTotal` 或 `maxIdle` 时，未显式配置的默认空闲连接上下限会自动收缩到有效范围内。
- 默认不在创建、借出或归还连接时校验，避免每次缓存操作额外发送校验命令。
- 默认启用空闲连接校验：每 60000 毫秒扫描一次，每轮检查 8 条空闲连接；空闲 600000 毫秒后可硬淘汰，超过 `minIdle` 的连接空闲 120000 毫秒后可软淘汰。
- 默认启用连接池 JMX，JMX 名称前缀为 `Aifei-Cache-Redis`。

高并发、慢网络、代理层超时或连接数受限的部署，可以按实际容量覆盖这些值：

```java
RedisConfig config = new RedisConfig()
        .uri("redis://127.0.0.1:6379/0")
        .maxTotal(128)
        .maxIdle(32)
        .minIdle(4)
        .maxWaitMillis(1000)
        .timeoutMillis(1000)
        .testOnBorrow(true);
```

`RedisConfig` 还支持 `clientName`、RESP3、JDK SSL 组件、`connectionTimeoutMillis`、`socketTimeoutMillis`、`blockWhenExhausted`、`lifo`、`fairness`、`testOnCreate`、`testOnReturn`、空闲连接淘汰时间和 JMX 基础参数。`maxTotal(-1)` 表示连接池最大连接数不限制。

需要自定义 Redis value 的二进制格式时，通过 `RedisConfig` 配置 `RedisValueCodec`：

```java
RedisConfig config = new RedisConfig()
        .uri("redis://127.0.0.1:6379/0")
        .valueCodec(new MyRedisValueCodec());
```

自定义 `RedisValueCodec` 必须是线程安全的，并且共享同一 Redis 的所有应用实例必须使用互相兼容的
codec 和数据格式。切换 codec 前，需要自行处理 Redis 中已有缓存数据的兼容性、清理或迁移。

`CachePlugin(Cache)` 会根据 `CaffeineCache` 或 `RedisCache` 自动创建对应 `Counter`，并将 `Cache` 和 `Counter` 注册为单例。自动创建的 `RedisCounter` 与 `RedisCache` 共享同一个 Redis 客户端和连接池，因此默认连接池上限仍按一组 `maxTotal=32` 计算。Redis 客户端生命周期由 `RedisCache` 管理，应用不需要单独创建或关闭 `RedisCounter`。配置完成后业务代码只需注入接口：

```java
@Inject
Cache cache;

@Inject
Counter counter;
```

Redis value 默认使用 Fury 兼容模式序列化。使用默认 codec 时，Redis 必须是应用可信的内部服务，不能允许不可信方写入缓存数据。`RedisCounter` 使用 Redis 原生 integer value，不受 `RedisValueCodec` 影响。

## 测试

普通单元测试不要求本地 Redis：

```shell
mvn test
```

可选的真实 Redis 集成测试：

```shell
mvn -Dredis.integration=true \
    -Dredis.uri=redis://127.0.0.1:6379 \
    test
```

完整的行为契约与实现约束详见 [docs/design.md](docs/design.md)。
