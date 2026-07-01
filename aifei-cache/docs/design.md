# aifei-cache 设计文档

本文档是 aifei-cache 项目设计目标与约束的事实来源。后续接口设计、实现和代码审查应以本文档为依据。

## 项目定位

aifei-cache 是为 aifei 提供的极简缓存与计数抽象。它让业务项目面向统一的 `Cache` 和 `Counter` 接口编程，并通过 aifei 的 `@Inject` 注解注入具体实现。

项目支持应用按部署阶段选择缓存方案：

- 单实例部署使用 `CaffeineCache`。
- 集群部署使用 `RedisCache`。
- 从单实例切换到集群部署时，依赖 `Cache` 接口的业务代码不需要修改。

## 核心设计目标

1. 设计极简的 `Cache` 和 `Counter` 接口。用户项目只依赖接口，不依赖具体实现。
2. 提供基于 Caffeine 的实现，用于单实例部署。
3. 提供基于 Redis 的实现，用于集群部署。
4. 保持两种实现可替换，使应用能够随流量增长从 Caffeine 平滑切换到 Redis。

## 设计原则

### 面向接口

业务代码只允许依赖 `cn.aifei.cache.Cache` 和 `cn.aifei.cache.Counter`。Caffeine、Jedis、Fury 等第三方库及其类型不能出现在公共 API 中。

### 实现可替换

`CaffeineCache` 和 `RedisCache` 必须遵循相同的缓存接口契约，`CaffeineCounter` 和 `RedisCounter` 必须遵循相同的计数接口契约。切换实现只能影响依赖注入或应用配置，不应要求修改业务调用代码。

### 保持极简

`Cache` 只提供业务缓存所需的最小能力，`Counter` 只提供业务计数所需的最小能力。新增方法、配置项或扩展点前，应确认它对两种实现均有清晰且一致的语义。

### 部署模式明确

`CaffeineCache` 是进程内本地缓存，不负责跨实例共享数据；`RedisCache` 是分布式缓存，用于多个应用实例共享缓存数据。两者不组成多级缓存。

### 兼容 Java 8

项目当前以 Java 8 为编译目标。代码、依赖版本和公开 API 都必须兼容 Java 8。

## 当前技术边界

- 缓存抽象：`Cache`
- 计数抽象：`Counter`
- 本地缓存实现：`CaffeineCache`
- 分布式缓存实现：`RedisCache`
- 本地计数实现：`CaffeineCounter`
- 分布式计数实现：`RedisCounter`
- 本地缓存引擎：Caffeine
- Redis 客户端：Jedis
- Redis value 序列化：默认 Fury，可通过 `RedisValueCodec` 自定义
- 依赖注入：aifei `@Inject`

上述第三方依赖属于内部实现细节，用户项目不应因使用 `Cache` 接口而直接依赖其 API。

## Cache 接口

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

接口提供读取、存在性判断、未命中加载、限时写入、条件限时写入、限时续期、删除和命名空间清理能力。第一版不提供永久写入、批量操作、统计或具体实现专属能力。

## Counter 接口

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

`Counter` 提供轻量计数读取、固定窗口增减、闲置过期增减和删除能力，不负责普通缓存 value 的序列化和读取。

### 参数与返回值

- `cacheName` 和 `key` 是区分大小写的非空白字符串，均可用冒号分级。
- `counterName` 与 `cacheName` 遵循相同命名规则。
- `value` 不能为 `null`。
- `ttl` 不能为 `null`，转换为毫秒后必须大于零。
- `ttlSeconds` 必须大于零，秒重载通过 default 方法转为 `Duration`。
- `Counter` 的 `step` 必须大于零。
- TTL 统一按毫秒精度处理，不足一毫秒的时长不被接受。
- `get` 在缓存未命中或条目过期时返回 `null`。
- 普通 `get(cacheName, key)` 在 `cacheName` 或 `key` 非法时也返回 `null`，按不可能命中处理。
- `exists(cacheName, key)` 在缓存项存在且未过期时返回 `true`，缓存项不存在、已过期或 `cacheName`、`key` 非法时返回 `false`。
- 带 loader 的 `get` 在命中时直接返回缓存值，不调用 loader。
- 带 loader 的 `get` 在未命中时调用 loader；loader 返回非空值时按指定 TTL 写入并返回。
- 带 loader 的 `get` 在 `cacheName` 或 `key` 非法时抛出 `IllegalArgumentException`，且不调用 loader。
- loader 返回 `null` 时直接返回且不写入缓存；loader 抛出的异常原样传播。
- Loader 不提供防缓存击穿保证，并发未命中时可能重复执行。
- 调用方必须保证 `get` 的接收类型与此前写入的 value 类型一致。
- `putIfAbsent` 在缓存项不存在或已过期时写入并返回 `true`；缓存项存在且未过期时不覆盖原 value、不重置原 TTL，并返回 `false`。
- `expire` 在缓存项存在且未过期时只重设剩余有效期并返回 `true`；缓存项不存在或已过期时不写入、不复活缓存项，并返回 `false`。
- `Counter.get` 在计数项不存在、已过期或 `counterName`、`key` 非法时返回 `null`。
- `Counter.increase` 和 `Counter.decrease` 返回更新后的计数值。
- `Counter.increase` 和 `Counter.decrease` 在计数项不存在或已过期时按当前值 `0` 处理，使用传入 TTL 创建计数项。
- `Counter.increase` 和 `Counter.decrease` 在计数项存在且未过期时只更新计数值，保留原 TTL，不使用传入 TTL 重置有效期。
- `Counter.increaseAndRefreshTtl` 和 `Counter.decreaseAndRefreshTtl` 返回更新后的计数值。
- `Counter.increaseAndRefreshTtl` 和 `Counter.decreaseAndRefreshTtl` 在计数项不存在或已过期时按当前值 `0` 处理，使用传入 TTL 创建计数项。
- `Counter.increaseAndRefreshTtl` 和 `Counter.decreaseAndRefreshTtl` 在计数项存在且未过期时更新计数值，并在同一条计数项的原子更新操作中将剩余有效期重置为传入 TTL。
- `Counter` 的增减方法使用 JDK `long` 计数语义；超过 `long` 范围时抛出 `ArithmeticException`。
- `Counter.remove` 删除不存在的计数项，或在 `counterName`、`key` 非法时，正常返回且不删除任何计数项。
- `remove` 删除不存在的条目，或在 `cacheName`、`key` 非法时，正常返回且不删除任何条目。
- `clear` 清理不存在的 `cacheName` 时正常返回。

### 行为契约

- `cacheName` 是业务缓存命名空间；同一 `key` 在不同命名空间中互不影响。
- 再次写入相同 `cacheName` 和 `key` 时覆盖原 value，并从写入时刻重新计算 TTL。
- `putIfAbsent` 是单条缓存项的条件写入操作，不承诺分布式锁语义；`CaffeineCache` 只提供单进程内条件写入，`RedisCache` 依赖 Redis 单 key 条件写入。
- `expire` 是单条缓存项的显式续期操作，不读取、不反序列化、不修改 value；调用方可用于滑动时间窗口，但本接口不提供按访问自动续期。
- `exists` 只表示调用时缓存项存在，不保证后续 `get`、`remove` 或其他调用仍能命中。
- `Counter` 是独立计数抽象，不通过 `Cache` 读取、写入或删除计数值。
- `Counter.increase` 和 `Counter.decrease` 用于固定窗口计数：第一次创建计数项时确定过期时间，后续命中更新不延长窗口。
- `Counter.increaseAndRefreshTtl` 和 `Counter.decreaseAndRefreshTtl` 用于闲置过期计数：每次命中更新后从当前调用重新计算 TTL，但不等价于精确的最近 N 秒滑动窗口统计。
- `Counter` 的增减方法是单条计数项的原子更新操作；`CaffeineCounter` 保证单进程内同 key 原子更新，`RedisCounter` 保证 Redis 单 key 原子更新，不承诺跨 key 原子性。
- `clear(cacheName)` 清理指定命名空间及其下级命名空间。
- `clear` 是非原子、尽力清理操作；与并发写入同时发生时，不保证并发写入的条目最终保留或删除。
- 业务代码应将缓存值当作不可变数据使用。Caffeine 保存对象引用，Redis 保存序列化快照，修改原对象后的可见性不属于接口契约。
- 除普通 `get(cacheName, key)` 的非法 `cacheName` 或 `key` 按未命中返回 `null`，`exists(cacheName, key)` 的非法 `cacheName` 或 `key` 按未命中返回 `false`，以及 `remove(cacheName, key)` 的非法 `cacheName` 或 `key` 按未命中忽略外，参数校验失败统一抛出 `IllegalArgumentException`；Loader 自身异常原样传播。底层缓存、连接、序列化和标准库运行时异常直接向调用方传播。

## 实现约束

### CaffeineCache

- 使用 Caffeine 的逐条过期能力实现每个条目独立 TTL。
- 默认最多保存 10000 个条目，并允许通过构造参数覆盖上限。
- `CachePlugin` 基于 `CaffeineCache` 自动创建 `CaffeineCounter` 时，复用相同的最大条目数量配置。
- value 直接保存在本地缓存中，不通过序列化复制对象。
- 本地缓存操作不额外捕获 Caffeine 运行时异常，异常按原始类型传播。
- `exists` 通过本地缓存项是否可读取判断存在性。
- `putIfAbsent` 使用 Caffeine 逐条过期策略的条件写入能力。
- `expire` 使用 Caffeine 逐条过期策略重设已有条目的剩余有效期。
- `clear` 遍历本地 key 并清理指定命名空间及其下级。

### CaffeineCounter

- 使用独立的 Caffeine 存储 `Long` 计数值，不与 `CaffeineCache` 共享存储。
- 使用 Caffeine 的逐条过期能力实现每个计数项独立 TTL。
- 默认最多保存 10000 个计数项，并允许通过构造参数覆盖上限。
- `increase` 和 `decrease` 使用 128 把 striped locks 按 `counterName` 和 `key` 分散加锁，在锁内读取、计算并写回计数值。
- 命中更新时保留原剩余 TTL；缺失创建时使用调用传入的 TTL。
- `increaseAndRefreshTtl` 和 `decreaseAndRefreshTtl` 复用相同的 striped locks，在锁内读取、计算、写回计数值并将剩余 TTL 重置为调用传入的 TTL。

### RedisCache

- `RedisCache` 允许通过 `RedisConfig` 配置 Redis 连接、连接池参数和 value 编解码器。该配置类属于具体实现的装配入口，不进入 `Cache` 公共接口，也不改变业务代码只依赖 `Cache` 的约束。
- `RedisConfig` 的公共 API 除本项目定义的 `RedisValueCodec` 扩展点外，只能使用 JDK 类型、字符串和基本类型，不暴露 Jedis、连接池、Fury 或其他第三方序列化库类型。
- `RedisConfig` 支持配置 URI、host、port、user、password、database、clientName、RESP3、SSL、JDK SSL 组件、连接超时、socket 超时、阻塞命令 socket 超时、maxTotal、maxIdle、minIdle、maxWaitMillis、连接池耗尽策略、连接池校验、空闲连接扫描、JMX 基础参数和 `RedisValueCodec`。
- `CachePlugin` 基于 `RedisCache` 自动创建 `RedisCounter`，并复用相同的 Redis 客户端和连接池，避免缓存与计数各自创建连接池；`RedisValueCodec` 仍只影响普通缓存 value。
- `RedisConfig` 默认 host 使用 Jedis 的 `Protocol.DEFAULT_HOST`，当前为 `127.0.0.1`。不默认使用 `localhost`，避免受本机 hosts、DNS 或 IPv6 优先级影响。
- `RedisConfig` 默认值面向普通中小型 Web 应用：连接与 socket 超时默认 2000 毫秒，阻塞命令 socket 超时默认 0，连接池默认 `maxTotal=32`、`maxIdle=16`、`minIdle=1`、`maxWaitMillis=3000`。
- `RedisConfig` 默认在连接池耗尽时最多等待 3000 毫秒后失败，不无限等待；默认使用 LIFO、非公平等待队列，优先吞吐和低调度开销。
- 当用户只降低 `maxTotal` 或 `maxIdle` 时，未显式配置的默认 `maxIdle` 或 `minIdle` 会自动收缩到有效上限内；用户显式配置出互相冲突的连接池上下限时仍抛出 `IllegalArgumentException`。
- `RedisConfig` 默认不在创建、借出或归还连接时执行校验，避免每次缓存操作增加额外 Redis 往返；默认启用空闲连接校验，扫描间隔 60000 毫秒，每轮检查 8 条空闲连接，硬空闲淘汰时间 600000 毫秒，超过 `minIdle` 的软空闲淘汰时间 120000 毫秒。`numTestsPerEvictionRun` 必须大于零。
- `RedisConfig` 默认启用连接池 JMX，默认 JMX 名称前缀为 `Aifei-Cache-Redis`。
- `RedisConfig.maxTotal(-1)` 表示连接池最大连接数不限制。`maxWaitMillis`、`timeBetweenEvictionRunsMillis`、`minEvictableIdleTimeMillis` 和 `softMinEvictableIdleTimeMillis` 的 `-1` 语义与 commons-pool 保持一致。
- 当同时配置 URI 和其他客户端参数时，URI 提供基础连接信息，显式设置的 user、password、database、clientName、SSL 和超时参数覆盖 URI 中对应的客户端配置。
- `RedisCache` 的无参、host/port 与 URI 便捷构造器均复用 `RedisConfig` 默认装配，避免便捷构造器与配置对象构造器出现不同默认行为。`RedisCounter` 不提供独立连接构造器，只由 `RedisCache` 在插件装配链路中创建。
- 物理 key 格式为 `{cacheName}:{key}`。`clear` 扫描时会转义 Redis glob 特殊字符；清理父级名称时也会清理其下级名称。调用方应避免使用会在 Redis 物理 key 上产生相同拼接结果的 `cacheName` 和 `key` 组合。
- 默认 value codec 使用 Fury `CompatibleMode.COMPATIBLE` 序列化为二进制数据，支持滚动发布期间常见的 POJO 字段增删。
- 默认 Fury codec 启用引用跟踪，支持缓存快照中的共享引用和循环引用；数字编码使用 Fury 默认压缩策略，不关闭 number compression。
- 默认 Fury codec 未强制类注册，因此使用默认 codec 时 Redis 必须是可信内部服务，不允许不可信方写入缓存数据。
- 自定义 `RedisValueCodec` 必须是线程安全的；共享同一 Redis 的所有应用实例必须使用互相兼容的 codec 和数据格式。
- `RedisValueCodec` 只负责 value 与二进制数据之间的转换，不参与 key 生成、TTL、命名空间清理或参数校验。
- `RedisValueCodec.serialize` 接收的 value 不会为 `null`，并且必须返回非 `null` 字节数组；`deserialize` 对本 codec 写入的字节数组必须返回非 `null` 对象。
- 切换 value codec 时，调用方需要自行处理 Redis 中已有缓存数据的兼容性、清理或迁移。
- 写入使用毫秒 TTL。
- `exists` 使用 Redis `EXISTS` 命令判断物理 key 是否存在，不反序列化 value。
- `putIfAbsent` 使用 Redis `SET` 命令的 `NX` 和 `PX` 参数实现条件写入和毫秒 TTL。
- `expire` 使用 Redis `PEXPIRE` 命令重设已有物理 key 的毫秒 TTL，不反序列化 value。
- Redis 连接、命令执行和 value codec 运行时异常不额外捕获，异常按原始类型传播。
- `clear` 使用 `SCAN` 查找指定命名空间并通过 `UNLINK` 分批异步回收，不使用阻塞 Redis 的 `KEYS` 命令。
- `clear` 默认使用 `SCAN COUNT 1000`。该值是 Redis 每轮扫描工作量提示，不保证每页返回数量；默认选择以减少网络往返和控制单次 Redis 事件循环占用为目标，不作为 `Cache` 公共 API 暴露。
- 第一版面向多个应用实例共享同一 Redis 服务的部署方式，不承诺 Redis Cluster 分片场景下的跨节点清理能力。

### RedisCounter

- 使用独立 Redis key 命名空间和 Redis 原生 integer value，不经过 `RedisValueCodec`。
- 不直接创建、拥有或关闭 Redis 客户端；Redis 连接生命周期由创建它的 `RedisCache` 管理。
- 计数物理 key 使用内部前缀，与 `RedisCache` 的普通缓存 key 隔离。调用方应避免使用会在 Redis 计数物理 key 上产生相同拼接结果的 `counterName` 和 `key` 组合。
- `increase` 和 `decrease` 使用 Lua 脚本把缺失初始化、TTL 设置和 `INCRBY` 计数更新合成一次 Redis 单 key 原子操作。
- 命中更新时保留原剩余 TTL；缺失创建时使用调用传入的 TTL。
- `increaseAndRefreshTtl` 和 `decreaseAndRefreshTtl` 使用同一类 Lua 脚本把缺失初始化、`INCRBY` 计数更新和命中 TTL 刷新合成一次 Redis 单 key 原子操作。
- `get` 只解析原生 signed long 文本；如果内部计数 key 存在但不是整数，或没有 TTL，抛出 `IllegalStateException`。
- `RedisConfig.valueCodec` 只影响 `RedisCache`，不影响 `RedisCounter`。

## aifei 生命周期

- `CachePlugin(Cache)` 接收已经配置好的 `Cache` 实例，并基于内置支持的缓存实现自动创建对应 `Counter`。`CaffeineCache` 对应 `CaffeineCounter`，`RedisCache` 对应 `RedisCounter`；其他缓存实现不属于项目支持范围，单参构造会拒绝自动创建。
- 插件启动时将缓存和计数实例分别注册为 `Cache` 和 `Counter` 单例，使业务代码可以通过 `@Inject` 注入接口。
- 插件停止时关闭实现了 `AutoCloseable` 的缓存和计数实例；内置 `RedisCounter` 不持有独立 Redis 客户端，Redis 连接随 `RedisCache` 关闭。
- 插件启动和停止操作均为幂等操作，避免重复注册和重复关闭。

## 异常模型

除普通 `get(cacheName, key)` 的非法 `cacheName` 或 `key` 按未命中返回 `null`，`exists(cacheName, key)` 的非法 `cacheName` 或 `key` 按未命中返回 `false`，`remove(cacheName, key)` 的非法 `cacheName` 或 `key` 按未命中忽略，`Counter.get(counterName, key)` 的非法参数按未命中返回 `null`，以及 `Counter.remove(counterName, key)` 的非法参数按未命中忽略外，参数校验失败统一抛出 `IllegalArgumentException`，包括参数为 `null`、空白字符串、TTL 非法、`step` 非法等场景。

项目不提供缓存专用公共异常类型。Caffeine、Jedis、Redis value codec、JDK 标准库或其他底层库抛出的运行时异常按原始类型传播。缓存未命中不是异常。

带 loader 的 `get` 会在调用 loader 前校验 `cacheName`、`key` 和 TTL。Loader 自身抛出的异常原样向外传播。

## 演进约束

- 优先保证 `Cache` 接口稳定、清晰和实现无关。
- 优先保证 `Counter` 接口稳定、清晰和实现无关。
- 两种缓存实现的公共行为必须一致，两种计数实现的公共行为必须一致；无法一致实现的能力不应直接加入公共接口。
- 不为尚未出现的需求预先增加复杂抽象。
- 任何会改变公共行为的设计决策都应先更新本文档，再落实到代码和测试。
- README 只保留面向使用者的说明，详细设计决策统一维护在本文档中。
- Caffeine、Jedis 和 Fury 保持 Maven optional 依赖，README 必须明确不同实现、默认 Fury codec 和自定义 codec 场景所需的运行时依赖。

## 暂不支持

- 无 TTL 的永久缓存条目。
- Loader 防缓存击穿。
- 批量读写、统计、刷新、按访问自动续期。
- Counter 的存在性判断、续期和命名空间清理。
- 原子命名空间清理。
- Redis Cluster 分片节点的遍历清理。
