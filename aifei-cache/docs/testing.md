# 测试说明与发布前检查记录

## 运行方式

使用 JDK 8 和 Maven。常规测试及实际发布包检查：

```sh
mvn clean verify
```

默认无需 Redis。需要只运行单元测试时使用 `mvn test`；`ReleaseArtifactIT` 由 Failsafe 在打包后执行，检查实际主 JAR、sources JAR 和 Javadoc JAR，因此发布前应运行到 `verify`。

启用真实 Redis 集成测试（Cache/Counter 契约与 Redis 专项覆盖 RESP2、RESP3）：

```sh
mvn -Dredis.integration=true -Dredis.uri=redis://127.0.0.1:6379/0 verify
```

`redis.integration` 沿用项目已有开关。显式开启后，连接失败会使测试失败，不会静默跳过。每个新增 Redis 用例使用随机 `aifei_regression_<UUID>:` 前缀，结束时用独立客户端的 `SCAN` / `UNLINK` 清理该前缀及对应计数前缀，不依赖被测 `clear()`，不执行全库清理或修改 Redis 配置。

2026-09-10 已将冒号命名造成的拼接重名和子命名空间清理影响接受为调用方命名限制，见 [设计文档](design.md) 和下文第 1 项。原 `KeyIsolationRegressionTest` 改为 `KeyNamingLimitationTest`，共 3 个方法、9 个参数化实例，记录清理已有子级、清理不存在的子级以及完整物理 key 重名时的现有实现差异。Caffeine 实例正常执行，Redis 实例使用普通 `redis.integration` 开关，不再使用 `redis.knownIssues`。单独运行：

```sh
mvn -Dredis.integration=true -Dtest=KeyNamingLimitationTest test
```

该类明确断言当前差异：Redis 按物理 key 或其前缀操作，可能影响父级条目；Caffeine 分别保存名称与业务 key，保留父级条目。测试通过表示已接受的限制仍可复现，不表示实现已经消除命名冲突。两种实现对遵守命名责任的用法继续使用相同的契约断言；原始失败场景及当时的隔离预期保留在下文历史记录中。

2026-09-10 发现的 Redis 计数 TTL 拒绝后仍改变数值问题已通过统一 TTL 上限修复：四个实现均在 Java 中拒绝超过 `Integer.MAX_VALUE` 秒的 TTL，Lua 不再包含反向恢复分支。相关用例随 Redis 集成测试执行，不再使用已知问题开关。根据同日的设计决定，内部计数前缀属于调用方必须遵守的保留约定，误用此前缀不再作为待修复问题；第 5 项保留历史发现与最小复现。

只复跑已修复问题的专项回归：

```sh
mvn -Dtest=CaffeineConcurrencyRegressionTest test
mvn -Dredis.integration=true -Dtest=RedisValueRegressionTest test
```

Maven 输出保存在 `target/surefire-reports/` 和 `target/failsafe-reports/`，由构建自动生成，无需提交。新旧 POJO 动态编译使用 JUnit `TemporaryFolder`，测试结束后自动删除。

## 独立用例覆盖与迁移核对

初次迁移完整保留了 **49 个测试方法、109 个参数化执行实例**，未继承原有契约测试，也未以原有用例替代独立预期。发布包检查仅调整为标准 Maven 生命周期和隔离类路径；自定义运行器与脚本已由 Surefire / Failsafe 替代。

2026-09-10 新增 5 个 TTL 失败处理方法、10 个参数化实例，以及 2 个最大 TTL 方法、6 个参数化实例；扩展原有参数测试覆盖上限以上的时长，并将超限 TTL 的预期统一为 `IllegalArgumentException`。按调用方保留前缀的设计决定，移除 2 个要求库防御前缀误用的方法、6 个实例，并调整非 String 计数的异常预期。原始发现仍保留在历史记录中。

同日按调用方命名责任的决定，将原来 1 个子命名空间隔离方法改为限制复现，并补充 2 个方法、6 个实例，覆盖不存在的子级和完整物理 key 重名。下表统计当前这批独立用例，Maven 还会运行其他测试。

| 正式测试类（位于 `src/test/java/cn/aifei/cache/`） | 方法数 | 实例数 | 主要覆盖 |
| --- | ---: | ---: | --- |
| [regression/CacheRegressionTest.java](../src/test/java/cn/aifei/cache/regression/CacheRegressionTest.java) | 13 | 39 | 三种后端配置；参数、Loader、TTL 上下界、条件写入、层级清理、随机 Map 模型 |
| [regression/CounterRegressionTest.java](../src/test/java/cn/aifei/cache/regression/CounterRegressionTest.java) | 12 | 36 | 三种后端配置；TTL 上下界、long 边界、溢出、固定与刷新窗口、并发返回值、BigInteger 模型 |
| [regression/KeyNamingLimitationTest.java](../src/test/java/cn/aifei/cache/regression/KeyNamingLimitationTest.java) | 3 | 9 | 已接受的命名限制；已有/不存在子级清理、完整拼接重名及两种实现的现有差异 |
| [redis/RedisValueRegressionTest.java](../src/test/java/cn/aifei/cache/redis/RedisValueRegressionTest.java) | 13 | 26 | RESP2/RESP3 原生值与 TTL、Fury 对象图及并发、codec 异常、非法数据、连接关闭、TTL 拒绝后的数值与并发一致性 |
| [caffeine/CaffeineConcurrencyRegressionTest.java](../src/test/java/cn/aifei/cache/caffeine/CaffeineConcurrencyRegressionTest.java) | 10 | 10 | 确定性过期边界、并发顺序、读取后过期的重新创建、容量淘汰 |
| [release/ReleaseArtifactIT.java](../src/test/java/cn/aifei/cache/release/ReleaseArtifactIT.java) | 5 | 5 | 实际发布包、Java 8 字节码、公共接口类型、可选依赖隔离、插件装配、Fury 新旧字段双向读取 |
| **合计** | **56** | **125** | 含命名限制复现；不再保留待修复的已知失败实例 |

[RegressionTestSupport.java](../src/test/java/cn/aifei/cache/regression/RegressionTestSupport.java) 只提供真实后端的创建、随机 key 前缀、独立清理和并发启动，不提供业务预期。所有契约断言仍来自 [design.md](design.md) 与公共接口。

Caffeine 专项使用项目已有的包级 Ticker 构造入口，在真实 Caffeine 的时钟读取处通过门闩安排暂停；没有替换缓存实现、过期策略或读写返回值。并发用例允许同 key 的读取/删除等待锁，再检查合法的原子顺序和最终状态；这些回归用例在修复前源码上也已验证能够报错。调用栈定位针对当前锁定的 Caffeine 2.9.3，升级依赖时应复核定位点。

当前修复与异常契约分别由以下方法直接回归：

- 计数删除的原子性：`counterRemoveAndIncreaseMustHaveAValidAtomicOrdering`。
- 已观察到过期的缓存不得被续期复活：`expireMustNotResurrectEntryObservedExpiredDuringCall`。
- 固定窗口不延长：`counterFixedWindowMustNotRebasePreviouslySampledRemainingTtl`；另用 `counterExpiredBetweenReadAndReplaceStartsFromZero` 验证读取与替换之间过期的分支。
- 超限 TTL 在更新前被拒绝，保留数值与原截止点：`rejectedIncreaseRefreshTtlMustNotChangeCounter`、`rejectedDecreaseRefreshTtlMustNotChangeCounter`；另覆盖初次创建失败、固定窗口命中也拒绝超限 TTL，以及失败刷新与成功增量并发执行。
- 最大 TTL 可用且两种重载一致：`maximumTtlWorksForEveryCacheOperationAndSecondsOverload`、`maximumTtlWorksForEveryCounterOperationAndSecondsOverload`。原参数测试同时覆盖上限加 1 纳秒、加 1 毫秒、加 1 秒、`Long.MAX_VALUE` 毫秒及其减一，以及转换为毫秒会溢出的极大正负 `Duration`。
- 非 String 内部计数的 Redis 原始异常传播：`nonStringInternalCounterPropagatesRedisError`，核对 `JedisDataException` 和 `WRONGTYPE` 信息。

## 命名责任决定后的验证结果（2026-09-10）

使用 Oracle JDK 1.8.0_381、Maven 3.9.9 和 Redis 7.2.7，在独立临时 Redis 上执行启用 `redis.integration` 的 `mvn -o -B -ntp clean verify`。Redis 仅监听本机随机端口，关闭持久化，测试结束后自动关闭。

- 构建成功，耗时 13.248 秒。Surefire 224 个实例、Failsafe 5 个实例，合计 **226 个通过、3 个跳过、0 个失败或错误**。
- `KeyNamingLimitationTest` 的 3 个方法、9 个实例全部通过，覆盖 Caffeine、Redis RESP2 和 RESP3；没有使用已知问题开关，也没有跳过命名限制的复现。
- 3 个跳过项为 2 个默认关闭的手动性能测试，以及要求连接本机默认 6379 端口的 `shouldUseDefaultRedisConfigAgainstLocalRedis`；本轮使用独立随机端口，因此未执行该默认地址用例。
- 主 JAR、sources JAR、Javadoc JAR、Java 8 字节码和可选依赖检查通过；实际 Javadoc JAR 中已包含 `Cache` 的命名责任及子级清理示例。生产源码仅修改说明注释，运行逻辑与 key 格式保持原样。
- 文档本地链接及 `git diff --check` 检查通过；测试结束时临时 Redis 的 DB0 没有遗留 key。

## TTL 上限与数值校验的验证结果（2026-09-10，命名责任决定前）

当时的 TTL 修复代码使用 Oracle JDK 1.8.0_381、Maven 3.9.9 和本机 Redis 7.2.7 完成验证：

```sh
mvn -o -B -ntp -Dredis.integration=true \
    -Dredis.uri=redis://127.0.0.1:6379/0 clean verify
```

- 构建成功，耗时 29.973 秒。Surefire 218 个实例、Failsafe 5 个实例，合计 **219 个通过、4 个跳过、0 个失败或错误**。
- 跳过项为子命名空间清理问题的 2 个 Redis 实例，以及 2 个手动性能测试。本轮新增的 TTL 校验性能对照已单独运行两次并通过，见下节。这批独立回归用例共 119 个实例，117 个通过、2 个已知问题跳过。
- 上限本身与 `int` 秒重载在 Caffeine、Redis RESP2、Redis RESP3 的 Cache/Counter 操作中均通过；超限时不调用 loader、不创建或修改条目。原 Redis 失败刷新回归继续检查原始数值与 TTL 不变，并发回归通过。
- 主 JAR、sources JAR、Javadoc JAR、Java 8 字节码、公共接口类型、可选依赖隔离及 Fury 新旧 POJO 字段兼容性检查通过。
- 新增测试的普通缓存前缀和内部计数前缀均无遗留 Redis key。旧 `audit` 目录没有恢复；当前复现代码位于正式测试目录，历史发现仍保留在本文。

## TTL 校验性能复核（2026-09-10）

本机 Oracle JDK 1.8.0_381（macOS aarch64）的 `Duration.compareTo` 只比较秒数，相等时再比较纳秒数，不创建对象。`Duration.toMillis()` 则包含 `Math.multiplyExact` 与 `Math.addExact` 的通用溢出检查。

当前 `requireTtl` 直接检查秒数和纳秒数，随后执行 `seconds * 1000L + nanos / 1_000_000`。秒数已经限制在非负 int 范围内，因此该换算不会溢出；保留不足 1 毫秒、超过上限 1 纳秒及极大正负 `Duration` 的拒绝行为，不保留用于比较的 `Duration` 常量。

手动对照测试保存在 [CacheTtlPerformanceTest.java](../src/test/java/cn/aifei/cache/CacheTtlPerformanceTest.java)，默认跳过，不以耗时阈值判断构建成功。运行方式：

```sh
mvn '-Dtest=CacheTtlPerformanceTest#shouldCompareTtlValidationCost' \
    -Daifei.cache.performanceTest=true test
```

三个版本使用相同的 32 个已创建 `Duration`，包含秒级、小数秒及最大值；计时前另以 JDK `toMillis()` 为预期，核对 10,000 个随机合法时长的转换结果。两次独立 JVM 进程的测量结果如下，单位为每次调用的中位耗时（纳秒）：

| 写法 | 1,000 万次/轮，预热 5 轮、测量 7 轮 | 200 万次/轮，预热 8 轮、测量 9 轮 |
| --- | ---: | ---: |
| 原来的 `toMillis()` 加正数检查，不含上限 | 136.09 | 134.16 |
| `Duration.compareTo` 上下界检查后 `toMillis()` | 119.46 | 117.20 |
| 当前数值范围检查及换算 | 0.88 | 0.87 |

在该环境和输入下，没有观察到 `compareTo` 版本比原版本更慢，当前数值换算版本的耗时更低。这是手动微基准，结果受 JVM 编译和机器环境影响，不能将比例外推为缓存整体吞吐提升，也不作为其他 JDK 或机器的性能承诺。

## TTL 反向恢复方案验证（2026-09-10 历史记录）

使用 Oracle JDK 1.8.0_381、Maven 3.9.9 和本机 Redis 7.2.7 完成完整验证：

```sh
mvn -o -B -ntp -Dredis.integration=true \
    -Dredis.uri=redis://127.0.0.1:6379/0 clean verify
```

- 构建成功，耗时 31.690 秒。Surefire 211 个实例、Failsafe 5 个实例，合计 **213 个通过、3 个跳过、0 个失败或错误**。
- 跳过项只有子命名空间清理问题的 2 个 Redis 实例，以及 1 个原有的可选性能测试。这批独立用例的 113 个实例中，111 个通过、2 个已知问题跳过。
- Redis 专项 26 个实例全部通过，包括 RESP2/RESP3 下的并发回归。每个协议同时执行 800 次成功增量和 800 次被拒绝的刷新，最终计数为 800，成功返回序号恰好覆盖 1 至 800。
- 主 JAR、sources JAR、Javadoc JAR 生成成功，Java 8 字节码、公共接口类型、可选依赖隔离和 Fury 新旧 POJO 字段兼容性检查通过。
- 独立扫描新增测试的普通缓存前缀与内部计数前缀，均未发现遗留 key。旧 `audit` 目录及同名残留文件不存在；文档链接与 `git diff --check` 检查通过。

## 迁移后的验证结果（2026-09-09 历史记录）

2026-09-09 使用 Oracle JDK 1.8.0_381、Maven 3.9.9 和本机 Redis 7.2.7 验证：

- `mvn clean verify` 成功，无需连接 Redis。
- `mvn -Dredis.integration=true -Dredis.uri=redis://127.0.0.1:6379/0 clean verify` 成功：Surefire 207 个实例、Failsafe 5 个实例，合计 205 个通过、7 个跳过、0 个失败。跳过项为 6 个已知 Redis key 问题实例和 1 个原有的可选性能测试。
- 单独开启 `KeyIsolationRegressionTest` 的已知问题开关：9 个实例中 Caffeine 的 3 个通过，Redis 的 6 个失败（4 个断言失败、2 个计数被覆盖后的读取异常），与迁移前逐项一致。
- 按方法名和后端参数核对迁移前后的全部 109 个实例：原有 103 个通过项仍全部通过；44 个 Cache/Counter/Redis/Caffeine 测试方法的主体仅替换辅助类或变量名称，业务断言保持原样。其余 5 个发布包方法改为 Maven 构建产物与标准测试类路径，已在打包后全部通过。
- 最终清理检查：旧目录、运行脚本引用和构建残留均已移除；新旧测试前缀及对应计数前缀在 Redis 中均为 0 个遗留 key。

## Redis TTL 上界复核与修复（2026-09-10）

当前方案将最大 TTL 统一为 `Integer.MAX_VALUE` 秒，约 68 年，由 Java 公共参数校验在访问缓存或执行 Lua 前抛出 `IllegalArgumentException`。不缩短超限 TTL，不依赖客户端与 Redis 的时钟比较，Lua 恢复原来的 11 行和 3 个参数。

复核 `RedisCounter.UPDATE_SCRIPT` 时发现一个此前未覆盖的边界：已有计数调用 `increaseAndRefreshTtl` / `decreaseAndRefreshTtl`，传入 `Duration.ofMillis(Long.MAX_VALUE)`，原参数校验接受该正毫秒数，但 Redis 拒绝该 TTL。修复前脚本先执行 `INCRBY`，之后 `PEXPIRE` 报错，已经完成的增减不会回滚。

修复前通过真实 Java 公共接口及 Redis 7.2.7 的 RESP2/RESP3 复现：初始计数为 10，增加并刷新报错后实际为 11；减少并刷新报错后实际为 9。两个方法、两个协议共 4 个失败实例，断言目标为“拒绝 TTL 时不得修改已有计数”。这属于极端 TTL 输入的失败处理问题，不代表正常 TTL 下的并发更新会丢失。

后续用本机 Redis 对比原脚本与恢复方案，`Long.MAX_VALUE - 1` 和 `Long.MAX_VALUE - 1000` 毫秒也能复现，因而“只对最大值减一”不能修复。Redis 将 TTL 加到当前毫秒时间以计算绝对截止点；超过 `Long.MAX_VALUE - 当前毫秒时间` 的 TTL 均存在该溢出问题。

最小复现（`counter` 为 Redis 实现）：

```java
counter.increase("ttl-limit", "k", 10, 30);
try {
    counter.increaseAndRefreshTtl("ttl-limit", "k", 1,
            Duration.ofMillis(Long.MAX_VALUE));
} catch (RuntimeException rejectedTtl) {
    // 原校验输出 11；当前由 Java 拒绝 TTL，仍输出 10，原过期截止点不变。
    System.out.println(counter.get("ttl-limit", "k"));
} finally {
    counter.remove("ttl-limit", "k");
}
```

正式用例位于 `RedisValueRegressionTest.rejectedIncreaseRefreshTtlMustNotChangeCounter` 和 `rejectedDecreaseRefreshTtlMustNotChangeCounter`，沿用独立随机前缀与清理，不修改共享 Redis 配置。这两个方法还覆盖大于 2^53 的步长、`Long.MAX_VALUE` 步长及计数的 long 边界。其他新增方法覆盖首次创建失败不留下零值、固定窗口命中也拒绝超限 TTL，以及两个客户端中成功增量与失败刷新并发执行时，最终值和每个成功返回序号仍然正确。Redis 专项的 26 个实例全部启用：

```sh
mvn -Dredis.integration=true -Dtest=RedisValueRegressionTest test
```

发现该问题时，其余 56 个计数与 Redis 专项实例通过，包含两协议下的 long 边界、计数溢出不修改值/TTL、固定与刷新窗口、BigInteger 随机模型和多客户端并发唯一序号。此前的测试覆盖了计数值的 long 上下界，没有覆盖 TTL 本身的 long 上限。

Redis 的[脚本原子执行保证](https://redis.io/docs/latest/develop/programmability/eval-intro/)保护命令执行期间不被其他客户端插入操作；它不提供脚本出错后的自动回滚，本次复现直接验证了这一点。

初步修复曾使用 `redis.pcall('pexpire', ...)` 取得错误，再用 `ARGV[4]` 的反向步长恢复数值；此前的测试结果见历史记录。根据约 68 年已经足够的设计决定，当前用统一 TTL 上限替代这一恢复方案，删除该分支及第四个脚本参数。

合法 TTL 下，固定窗口命中时不执行 TTL 命令，计数溢出仍发生在 TTL 刷新之前。超限参数在 Java 中直接被拒绝，初次创建和已有计数都不会被修改。该方案不增加 Redis 脚本通用自动回滚的承诺。

`get` 的 `WRONGTYPE` 包装与原子性无关。根据调用方保留内部前缀的决定，已去掉该 `try/catch`，Redis 读取错误原样传播。原有的整数文本与缺失 TTL 检查仍保留；该调整与当前 [设计文档](design.md) 一致。

## 2026-09-09 独立检查记录

首次检查日期：2026-09-09。以下构建、依赖和远端查询结果均为该次检查的历史记录；当前测试入口见上文。生产代码基线：`a7c5c1fe2dcb4a63cdbc1872904af44777c4184b`。

## 首轮修复状态（2026-09-09 历史记录）

当时按用户要求修复下文第 2、3、4、6 项；第 1、5 项涉及 Redis key 规则，暂缓处理。2026-09-10 对第 1、5、6 项的后续决定见上文与各项说明。

- `CaffeineCache` 的同 key 操作共用分段锁，读取不会在续期的存活检查与 TTL 更新之间观察到过期空档。
- `CaffeineCounter` 的读取和删除加入同一分段锁；固定窗口使用 Caffeine 原生 `replace` 保持原截止点。若条目在读取与替换之间过期或被淘汰，则从 0 开始创建。
- `RedisCounter.get` 仅把 `WRONGTYPE` 转换为 `IllegalStateException`，保留原始 cause；其他 Redis 读取错误继续原样传播。
- 生产代码只修改 3 个已有类，新增一个 26 行的包内 `CaffeineLocks` 工具，合计增加 85 行、删除 62 行，净增 23 行（含注释和空行）。公共接口、依赖、Redis key 格式、value 存储形式均未改变。实现约束已同步到 [design.md](design.md)。

首轮修复后的完整独立测试：**109 项，103 项通过，6 项失败，0 项跳过**，耗时 24.489 秒（不含编译与打包）。6 项失败全部来自当时暂缓的两个 Redis key 问题；Caffeine 专项 10 项和 Redis 专项 16 项全部通过。当时迁入 Maven 后显式跳过这 6 个已知失败实例；后续一度仅保留子命名空间清理的 2 个已知失败实例，另一个问题按调用方保留前缀的决定退出缺陷回归。当前子命名空间场景也已按命名责任决定转为限制复现，运行方式见本文开头。首轮 JDK 8 下主 JAR、源码 JAR、Javadoc JAR 构建成功。

并发回归用例已允许读取/删除等待同 key 的锁，同时继续验证合法原子顺序和禁止“先未命中、再出现旧值”。修改后的用例另以基线源码单独编译运行，原缺陷仍然失败；新增的“读取与替换之间过期必须从 0 创建”也在旧实现上失败。修复后的相同用例全部通过。

读操作现在也会参与分段锁竞争；这是保证同 key 原子可见性的代价。每个缓存/计数实例仍使用 128 把锁，Loader 仍在锁外运行。当前验证包含并发正确性测试，没有把结果当作吞吐量基准。

## 初次审计结论（修复前）

**基线版本不建议直接发布。独立测试确认 6 类问题，其中 3 类涉及误删、计数原子性或过期数据复活，应优先修复。** 以下保留初次检查时的结果与复现记录；源码行号均指上述基线，并非修复后的当前行号。

## 测试方法与结果

首次独立检查没有读取、引用、继承、编译或运行当时已有的 `src/test` 测试。迁入正式测试目录后，这些测试仍保持独立的断言与模型，由 Maven 与已有测试一起运行。独立断言来自 [design.md](design.md) 与两个核心接口。使用真实 Caffeine 2.9.3、真实 Redis 7.2.7 standalone、Jedis 7.5.2、Fury 0.10.3；编译和运行环境为 Oracle JDK `1.8.0_381`、Maven 3.9.9。

| 独立测试组 | 执行数 | 通过 | 失败 |
| --- | ---: | ---: | ---: |
| Cache 契约：Caffeine / Redis RESP2 / Redis RESP3 | 39 | 37 | 2 |
| Counter 契约：Caffeine / Redis RESP2 / Redis RESP3 | 39 | 35 | 4 |
| Redis 数据、codec、连接专项 | 16 | 14 | 2 |
| Caffeine 时间、并发、容量专项 | 9 | 6 | 3 |
| 发布 JAR、可选依赖、插件及滚动序列化 | 5 | 5 | 0 |
| **合计** | **108** | **97** | **11** |

11 个失败是相同缺陷在不同协议或不同操作下的重复验证，对应下文 6 类问题；没有跳过的测试。最终完整测试耗时 24.196 秒（不含编译与打包）。Caffeine 的三个确定性缺陷在增加容量用例之前另外连续复跑 10 次，每次均复现相同 3 个失败。

覆盖内容包括：

- Cache 的读写覆盖、删除、exists、两个 TTL 重载、非法参数的命中/未命中分支、Loader 命中不调用、返回 null 不写入、原异常传播。
- 毫秒过期、续期延长/缩短、过期后不复活、失败的 putIfAbsent 不改变值和 TTL；16 线程竞争同一 key，确认唯一写入者。
- 层级清理、glob 特殊字符、中文/emoji/包含 NUL 的名称、2400 个条目的跨页 SCAN 清理和相邻命名空间保留。
- 每种运行配置 2500 步缓存随机操作，与独立 Map 模型比对。
- 四种计数更新、负数和零计数、Long 最大/最小值、超过 2^53 的精确返回、溢出不改变值和 TTL。
- 每种运行配置 3000 步随机计数操作，以 BigInteger 为独立算术预期；12 线程执行 6000 次增量更新，验证每个返回序号恰好出现一次；另外覆盖并发混合增减与 String hash 冲突。
- Redis 两个独立客户端共享状态、原生 GET/PTTL 核对、codec 异常、缓存与计数隔离、连接重复关闭。
- 1 MiB 二进制值、POJO 循环引用及共享引用、集合与数值边界、12 线程 Fury 读写、两个独立类加载器中的新旧 POJO 字段增删双向读取。
- 实际 JAR 中的 Java 8 字节码、源码和 Javadoc 内容、可选依赖移除后的真实运行、CachePlugin 启停和接口单例装配。

## 确认的问题

### 1. [P1] RedisCache.clear 清理子命名空间时误删父级条目（历史发现，现为调用方命名限制）

位置：`src/main/java/cn/aifei/cache/redis/RedisCache.java:153`、`:189`。

最小复现：

```java
cache.put("user", "profile:42", "parent", 30);
cache.put("user:profile", "99", "child", 30);
cache.clear("user:profile");
// 预期：父级 user 的 profile:42 仍为 "parent"。
// Redis 实际返回 null；Caffeine 正确保留 "parent"。
```

Redis 将逻辑名称和 key 直接用冒号拼接，随后使用 `user:profile:*` 清理，无法区分父级的业务 key 前缀与真正的子命名空间。上例两个条目的完整物理 key 不同，因此没有违反文档中“避免相同拼接结果”的约束。即使被清理的子命名空间根本不存在，也可能误删父级条目。

当时的失败用例：`KeyIsolationRegressionTest.childClearMustPreserveParentEntryWithColonInBusinessKey`，在原隔离契约下 RESP2/RESP3 均失败。

当时建议明确命名空间与业务 key 的编码或边界；单纯增加 glob 转义不能解决此问题。

2026-09-10 的决定：保留原始 key、Redis 客户端的冒号目录展示和现有实现，将该场景记为已接受的设计限制。调用方负责避免完整物理 key 重名和清理前缀重叠；冲突组合不在共同隔离与实现可替换保证范围内，库不增加检查、转义或额外索引。限制的触发取决于命名与清理调用，不以“低概率”作为保证。

现有复现位于 `KeyNamingLimitationTest.childClearShowsBackendDifferenceForOverlappingKeyPrefix`：保留上述父子物理 key 不同的场景，显式核对 Redis 清理父级、Caffeine 保留父级，并检查无前缀重叠的冒号业务 key 仍保留。另有 `missingChildClearShowsBackendDifferenceForOverlappingKeyPrefix` 覆盖没有子级条目也触发清理，以及 `concatenatedKeyCollisionShowsBackendDifference` 覆盖完整拼接重名后的覆盖、读取和删除。这些用例记录已接受的限制，不表示代码完成了隔离修复。

### 2. [P1] CaffeineCounter.remove 不参与增减操作的锁，导致删除丢失

位置：`src/main/java/cn/aifei/cache/caffeine/CaffeineCounter.java:116`、`:133`。

确定性交错：先写入计数 10；增量线程读到 10 后暂停；另一线程删除并读取确认不存在；增量线程继续，返回 11，并将计数重新写成 11。

这不符合任何有效原子顺序：若增量先发生，最终应已删除；若删除先发生，增量应从 0 开始，返回并保存 1。实际的“返回 11、最终仍为 11”说明删除被旧值写回覆盖。

失败用例：`CaffeineConcurrencyRegressionTest.counterRemoveAndIncreaseMustHaveAValidAtomicOrdering`。

修复方向：至少让删除与增减使用同一条目的同步机制，并对所有计数写入保持一致的原子顺序。

### 3. [P1] CaffeineCache.expire 可复活已经过期并被读操作观察为不存在的条目

位置：`src/main/java/cn/aifei/cache/caffeine/CaffeineCache.java:142`–`:147`。

`getExpiresAfter` 检查与 `setExpiresAfter` 更新是两次操作。检查时条目未过期，更新前时间经过原截止点；另一个读取已经返回 null，续期仍返回 true，后续读取又得到旧值。实际 Caffeine 的 `setExpiresAfter` 会更新尚未清理的过期节点。

测试用真实 Caffeine 与可控 Ticker，将原有效期设为 100 ms，在续期中间推进到 110 ms，并通过另一个读取确认未命中；释放续期线程后旧值重新出现。

失败用例：`CaffeineConcurrencyRegressionTest.expireMustNotResurrectEntryObservedExpiredDuringCall`。

修复方向：把存活检查和 TTL 更新放在同一个原子操作中，保证过期节点不会被直接续期复活。

### 4. [P2] CaffeineCounter 固定窗口更新会延长原截止时间

位置：`src/main/java/cn/aifei/cache/caffeine/CaffeineCounter.java:134`、`:148`。

实现先采样剩余 TTL，稍后把同样的时长作为一次新写入的相对 TTL。两次操作之间经过的时间被额外加到了原截止点上。分段锁只能限制其他更新线程，不能阻止时间推进或线程被调度暂停。

测试中原截止时间为 100 ms；读取剩余 TTL 后经过 50 ms，再写回值。当前实现把截止时间推到了 150 ms，110 ms 时仍能读取到计数 2。预期此时已经过期。这会使固定窗口限流/计数延长，频繁更新可累计偏移。

失败用例：`CaffeineConcurrencyRegressionTest.counterFixedWindowMustNotRebasePreviouslySampledRemainingTtl`。

修复方向：保留绝对截止点，或使用保持现有截止点的原子值更新；同时处理更新跨越过期点时重新从 0 创建的语义。

### 5. [P2] Redis 的内部计数前缀没有与公共 Cache 名称真正隔离（历史发现，现为调用方约束）

位置：`src/main/java/cn/aifei/cache/redis/RedisCounter.java:23`；`RedisCache.java` 中所有直接拼接名称的 key 操作。

最小复现：

```java
counter.increase("quota", "u1", 11, 30);
cache.remove("_Aifei_Counter_:quota", "u1");
// Redis 的计数被删除；Caffeine 的计数仍然为 11。
```

`_Aifei_Counter_:quota` 能通过公共参数的非空白名称校验。普通缓存使用它即可删除内部计数；使用 `put` 则会把计数覆盖成 Fury 字节，后续 `Counter.get` 抛异常。初次检查时尚未明确调用方不得占用此前缀，API 也没有校验此前缀。

当时的失败用例：`KeyIsolationRegressionTest.ordinaryCacheNameCannotDeleteInternalCounter` 和 `ordinaryCacheNameCannotOverwriteInternalCounter`，RESP2/RESP3 均失败。

2026-09-10 的决定：此前缀是调用方必须遵守的保留约定，库不防御调用方通过普通 Cache 或外部命令占用此前缀，也不修改物理 key 格式。因此移除上述两个要求库防御误用的方法；这里保留事实、原方法名和最小复现，避免丢失初次发现。

### 6. [P2] RedisCounter.get 对非 String 类型的内部值抛出了错误的异常类型（历史契约已调整）

位置：`src/main/java/cn/aifei/cache/redis/RedisCounter.java:60`。

在对应内部计数 key 写入带 TTL 的 Redis list 后，`Counter.get` 实际抛出 `JedisDataException: WRONGTYPE`。当时设计文档要求：内部计数 key 存在但不是整数时抛出 `IllegalStateException`。基线代码只处理 GET 成功后的文本格式，遗漏 GET 命令本身的 WRONGTYPE。

当时的失败用例：`RedisValueRegressionTest.nonStringInternalCounterIsReportedAsInvalidState`，RESP2/RESP3 均失败。

此问题需要外部误写或污染内部 key 才触发。首轮曾加入 `WRONGTYPE` 转换；2026-09-10 按调用方保留前缀的决定移除该包装，改为原样传播 Redis 读取错误，并同步设计文档。当前用例名为 `nonStringInternalCounterPropagatesRedisError`，验证原始异常与错误信息。

## Maven Central 发布检查

依据 [Sonatype 发布要求](https://central.sonatype.org/publish/requirements/) 核对了构件及元数据要求；依据 [Central Maven 插件文档](https://central.sonatype.org/publish/publish-portal-maven/) 核对发布插件用途。

- JDK 8 下生产构建成功；发布 profile 执行 `mvn -o -Pcentral-release -Dmaven.test.skip=true -Dgpg.skip=true verify` 成功。
- 已实际生成主 JAR、sources JAR、javadoc JAR；检查过主包的 class、sources 中的两个接口源码、Javadoc 中的两个接口页面。审计代码未被打入主包。
- 生产 JAR 的 17 个 class 的 major version 均为 52。当前解析到的所有依赖的基础 class 版本均不高于 52；扫描排除了 `META-INF/versions/` 和 `module-info.class`。另以真实 JDK 8 执行了全部审计测试。
- POM 中已包含项目名称、描述、URL、license、developers、SCM；发布 profile 中已有 GPG 插件和 Central 插件。
- 只读访问 Maven Central：`cn.aifei:aifei:1.1.0` 与 `redis.clients:jedis:7.5.2` 的 POM 均为 HTTP 200；本次检查 `cn.aifei:aifei-cache:1.0` POM 为 HTTP 404。该结果只代表查询时点，不证明命名空间发布权限或没有其他待发布任务。
- 已验证 Caffeine 场景移除 Jedis/Fury 后仍能从实际发布 JAR 运行并完成插件装配；自定义 Redis codec 场景移除 Caffeine/Fury 后仍可读写缓存和计数。
- 未进行真实 GPG 签名、账号/命名空间授权验证、远端 staging 验证或上传发布。这些步骤不包含在本次构建成功的结论中。

## 验证范围

首次及修复后运行结束时，已独立扫描普通缓存和计数测试前缀，均未发现遗留 Redis key。当前测试的复跑与清理方式见本文开头。

测试对象为本机 Redis standalone 的 RESP2/RESP3 和当前锁定依赖版本。没有测试 Redis Cluster（设计明确不承诺）、TLS/ACL 的外部环境、网络分区、服务端故障转移或任意业务对象类型；测试通过的部分也不能构成“没有其他 bug”的证明。现有文档已明确承认的冒号完整拼接冲突没有重复列为新缺陷。
