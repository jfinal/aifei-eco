# Aifei 框架深度分析文档

> 作者：詹波 (James Zhan) · 版本 1.0.0 · 定位："全球首个 AI 编码框架"

---

## 一、框架概述

Aifei 是 JFinal 作者詹波的新一代 Java Web 全栈框架，继承了 JFinal 的核心设计理念（轻量、极速、极简），同时在架构层面做了大量现代化重构。框架设计非常克制，API 简洁到极致，但扩展点丰富。

### 1.1 技术栈

| 模块 | 技术选型 |
|------|---------|
| 基准 JDK | Java 8+ |
| HTTP 服务器 | Undertow 2.2.39 |
| AOP 代理 | CGLib 3.3.0 / Javassist 3.30.2 |
| JSON 序列化 | Fastjson2 2.0.60 |
| 日志 | SLF4J 2.0.17 / Log4j2 2.25.3 |
| 连接池 | Druid 1.2.28 / HikariCP 4.0.3 |

### 1.2 模块架构

```
aifei(父POM)
├── aifei            核心模块（AOP/IoC、路由、参数注入、配置、扫描器）
├── aifei-db         数据库 ORM 模块（Db + Row、事务、代码生成器）
├── aifei-enjoy      模板引擎（Enjoy，独立可复用）
├── aifei-json       JSON 序列化（适配 fastjson2，对 Row/Model 增强）
├── aifei-proxy      AOP 代理工厂（CGLib/Javassist 实现）
├── aifei-log        日志适配层（Log4j2 / SLF4J 实现）
├── aifei-undertow   Undertow HTTP 服务器集成
└── aifei-all        全量聚合模块
```

---

## 二、启动流程：极简的生命周期

[Aifei.start()](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/core/Aifei.java#L46-L95) 是整个框架的入口，其启动流程设计精炼：

```
1. 命令行参数 → 系统属性（支持 --aifei.profiles.active=pro）
2. 创建 Settings / Routes / Plugins 配置对象
3. 回调 AifeiConfig.config(settings)    → 配置 Server、Handler、拦截器
4. 回调 AifeiConfig.config(routes)      → 配置路由扫描
5. 回调 AifeiConfig.config(plugins)     → 配置插件
6. 校验 Server、Handler 是否已配置
7. 启动所有 Plugin（DB、缓存等在此阶段初始化）
8. 回调 onStart()                       → 用户启动后逻辑
9. 构造 Handler 责任链
10. Dispatcher.init(handler)            → 连接 Server 与 Handler
11. Server.start()                      → 启动 HTTP 服务
12. 注册 ShutdownHook → Aifei.stop()
```

关闭流程严格对称：`Server.stop() → onStop() → Plugin.stop()`

### 核心设计思想

- **配置即回调**：`AifeiConfig` 是一个接口，三个 `config()` 方法在启动时顺序回调，用户无需理解内部启动细节
- **泛型一致性**：`AifeiConfig<I, O>` 的泛型从配置层一直传递到 Server → Dispatcher → Handler，编译期保证类型安全
- **命令行参数设计**：约定 `--key=value` 格式转系统属性，支持 `--debug` 这种开关型（默认 `true`），设计精巧

---

## 三、路由系统：精准匹配与扩展设计

路由系统是整个框架最精彩的部分之一，核心类 [Router](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/router/Router.java)。

### 3.1 多层拦截器体系

拦截器优先级（从高到低）：

```
Routes 级拦截器  →  全局拦截器  →  Class 级拦截器  →  Method 级拦截器
```

这是一个精心设计的拦截器分层：Routes 级最高优先级对应"路由组"概念，全局拦截器适用于跨模块横切关注点（如日志、权限），Class/Method 级别让拦截器粒度自由可控。

**关键实现**：[InterceptorKit.buildAifeiInterceptor()](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/aop/InterceptorKit.java) 按序合并四个层级的拦截器数组。

### 3.2 路由扫描机制

```
Router.scan(basePackage, routesInterceptors, skip)
  → Scanner.scan(basePackage, filter)     // 通用类扫描
    → 遍历 classpath 目录和 jar 文件
    → Class.forName(className, false, classLoader)  // false 避免触发类初始化
    → filter.test(clazz)                   // 仅收集带 @Path 注解的类
  → 去重（同一 class 被多次扫描以第一次为准）
  → buildRoute(targetPath, target)        // 构建路由
```

[Scanner](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/scanner/Scanner.java) 是一个**通用类扫描器**，不限定路由用途。支持文件系统和 JAR 包扫描，使用 `Predicate<Class<?>>` 过滤，`Class.forName(name, false, loader)` 避免触发静态初始化块，设计考究。

### 3.3 路由匹配算法

路由匹配是整个路由系统最精妙的部分，分两条路径：

**路径匹配（单 Action）**：
```
1. 精确匹配 mapping.get(path)
2. 回退匹配：取最后一个 '/' 之前的部分作为 actionPath
   ，'/' 之后作为 pathPara
3. 验证 pathPara 存在性（Action.getPathParaCount() > 0 时）
```

**参数匹配（ActionGroup 多 Action 共享路由）**：
```
1. 严格按参数数量、名称匹配（不匹配类型）
2. ActionGroup 内 Action 已按参数数量降序排列
3. 优先匹配参数多的 Action（设计精妙：避免模糊匹配导致的错误调用）
4. 命名参数的 match 属性可配置为 false 跳过匹配
```

**为什么路径参数必须参与匹配而命名参数可选？**

[Router 注释](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/router/Router.java#L51-L58) 中有清晰的说明：路径参数是路由的一部分，几乎总是必须存在；命名参数（query/form/body）大量是可缺省的（筛选条件、分页、排序等）。

### 3.4 Action 重载机制

当 `actionOverload = true` 时，同一 path 可映射到多个 Action，通过 [ActionGroup](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/router/ActionGroup.java) 管理。参数匹配不按类型，因为路由匹配时无法确定最终类型（类型转换发生在 Action 调用阶段），仅按参数名称+数量匹配。这是一个务实的设计决策。

### 3.5 扩展钩子

`Router.setOnActionCreated(Consumer<Action>)` 是一个极好的扩展点设计。可用于：
- MCP 服务端 Tool 注册
- 权限表自动生成
- 自定义 Action 元数据注入

---

## 四、AOP 与 IoC：自研轻量容器

### 4.1 核心架构

```
Aop (门面)
  → AopFactory (实现)
    → AopKit (配置入口)
    → Proxy (获取代理对象)
      → ProxyFactory (接口，CGLib/Javassist 实现)
      → InstanceFactory (LambdaMetaFactory 高性能实例化)
    → 依赖注入 (@Inject)
    → 单例/原型管理 (@Singleton + ThreadLocal 循环注入检测)
```

### 4.2 依赖注入设计

[AopFactory](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/aop/AopFactory.java) 的 IoC 实现极为精简但功能完备：

- `@Inject` 注解支持指定实现类：`@Inject(UserServiceImpl.class)`
- 默认单例，`@Singleton(false)` 覆盖为原型
- `injectSuperClass` 控制是否注入父类字段
- `addMapping(interface, impl)` 接口到实现类的映射
- `addMapping(name, impl)` 命名映射，支持 `Aop.get("serviceAaa")`

### 4.3 循环注入检测

使用 `ThreadLocal<HashMap<Class<?>, Object>>` 在一条注入链上跟踪已创建的实例。当发现 `map.get(targetClass) != null` 时，说明出现循环引用，返回已缓存的半成品对象。这是 JFinal 中经典设计的延续。

### 4.4 实例工厂：JIT 级别的性能优化

[InstanceFactory](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/proxy/InstanceFactory.java) 是整个框架中最能体现"极致性能"追求的代码：

```java
// LambdaMetaFactory 将构造器包装为 Supplier.get()
// 在 JIT 预热后，supplier.get() 被内联为 new YourClass()，开销几乎为 0
MethodHandle handle = lookup.findConstructor(type, METHOD_TYPE_VOID);
CallSite callSite = LambdaMetafactory.metafactory(lookup, "get", ...);
return (Supplier<T>) callSite.getTarget().invokeExact();
```

对 JPMS（Java 模块系统）的兼容也考虑到了：JDK 9+ 使用 `MethodHandles.privateLookupIn()`，JDK 8 回退到 `MethodHandles.lookup()`。

### 4.5 Aop.get() vs Aop.inject() 的区别

这是 AOP 文档中强调的核心概念：
- `Aop.inject(obj)` — 仅对已有对象的 `@Inject` 字段注入。对象不是 AOP 代理，拦截器不生效
- `Aop.get(Class)` — 创建代理对象并注入。拦截器全部生效（包括目标对象自身的方法拦截器）

---

## 五、参数注入（Argument）：Method 级依赖注入

[Argument](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/argument/Argument.java) 体系是框架最具创新性的模块，本质是 "Method 级依赖注入"，但比传统 DI 更强大——注入时可以利用当前请求的上下文 `Input/Output`。

### 5.1 设计理念

```
传统 DI：  容器创建对象 → 注入字段 → 调用方法
aifei DI： 请求到达 → 解析 Input/Output → Argument.getValue(in, out) → 注入实参 → 调用方法
```

核心优势在于注入时拥有请求上下文，可以实现普通 DI 无法做到的注入，如：
- 注入当前登录用户对象（从 Session 中获取）
- 注入 SSE 流式对象
- 注入文件上传/下载的 OutputStream
- 注入 Lambda 延迟处理器

### 5.2 Argument 类型体系

```
Argument (抽象基类)
├── BeanArgument      兜底：JSON 反序列化为 Bean
├── BasicArguments    基础类型：String/Integer/Long/Double/Boolean/BigDecimal/Date
├── EnumArgument      枚举类型注入
├── ListArgument      集合类型注入
├── MapArgument       Map 类型注入
├── ArrayArgument     数组类型注入
└── NoMatch (接口)    标记不参与路由匹配
```

### 5.3 扩展机制

[ArgumentKit](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/argument/ArgumentKit.java) 提供多层次的扩展：

1. `register(type, argument)` — 按类型注册 Argument 实现
2. `registerInputArgumentFun(fun)` — 接管 Input 参数注入
3. `registerOutputArgumentFun(fun)` — 接管 Output 参数注入
4. `registerBeanArgumentFun(fun)` — 接管兜底 Bean 注入
5. `setFactory(factory)` — 完全替换 ArgumentFactory

### 5.4 @Para 注解设计

```java
@Para(
    path = false,           // 是否为路径参数
    name = "\u0000",        // 参数名（"\u0000" 是哨兵值，表示未配置）
    defaultValue = "\u0000",// 默认值
    match = true            // 多 Action 同 path 时是否参与参数匹配
)
```

注意：不支持 `value` 属性（用 `name` 代替），这是有意的设计决策——避免 `@Para("name")` 被误解为参数值而非参数名。

---

## 六、数据库模块（aifei-db）：Db + Row 模式

### 6.1 设计哲学

aifei-db 的核心设计是 **Db + Row** 模式，这是一种 "无 Model 也可操作数据库" 的思路。Row 本质上是一个携带表名和主键名的灵活 Map。

```java
// 插入
Row.of("user").set("name", "James").insert();
// 更新
Row.of("user").id(123).set("name", "James Zhan").update();
// 查询
Row user = Db.findById("user", 123);
// 删除
Db.deleteById("user", 123);
```

### 6.2 架构层次

```
Db (静态门面，所有静态方法委托给 Dao)
  → Db.use() → DbConfig.createDao() → Dao
    → DbKit (管理 DbConfig 注册、ThreadLocal 多租户)
    → DbConfig (持有 DataSource、Dialect、所有 Executor)
      → InsertExecutor / DeleteExecutor / UpdateExecutor
      → FindExecutor / QueryExecutor / PaginateExecutor
      → BatchExecutor / BatchInsertExecutor / BatchUpdateExecutor
      → TransactionExecutor
      → SqlKit (Enjoy SQL 模板引擎)
```

### 6.3 Enjoy SQL：模板引擎与 SQL 的融合

这是框架最具特色的设计——用 Enjoy 模板引擎来写 SQL：

```java
// 基本用法
Db.sql("select * from user where id = ?", 123).find();

// 带 sqlId（支持缓存）
Db.sql("|user.findById| select * from user where id = ?", 123).find();

// 命名参数
Db.sql("select * from user where id = #para(id)", Kv.of("id", 123)).find();

// 外部 SQL 文件 + 动态条件
// user.sql:
//   #sql("findByCondition")
//     select * from user where 1=1
//     #where(name, '=', name)    -- 当 name 不为空时追加 and name = #para(name)
//     #and(age, '=', age)       -- 当 age 不为空时追加 and age = #para(age)
//   #end
Db.sqlById("findByCondition", Kv.of("name", "James",).set("age", 25)).find();
```

`#where`、`#and`、`#orderBy` 三个自定义 Enjoy 指令专门用于动态 SQL 构建，设计极其优雅。`#para` 指令自动收集预处理参数防 SQL 注入。

### 6.4 事务设计：隐式提交

[Transaction](file:///Volumes/zdh/projects/alun/aifei/aifei-db/src/main/java/cn/aifei/db/transaction/Transaction.java) 的事务设计遵循一个铁律：

> **一切正常才会提交，任何非正常情况都回滚。**

- 用户可以控制的是 `rollback()` / `rollbackIf(condition)`
- 不存在 `commit()` 或 `commitIf()` 方法
- 异常抛出自动回滚
- `onCommitSuccess` 回调用于事务提交后的异步操作（如更新缓存）

嵌套事务支持通过 `ThreadLocal` 检测，内层事务的隔离级别不能低于外层。

### 6.5 Hook 机制

[DbHookKit](file:///Volumes/zdh/projects/alun/aifei/aifei-db/src/main/java/cn/aifei/db/hook/DbHookKit.java) 提供 CRUD 全生命周期的 Hook：
- InsertHook / UpdateHook / DeleteHook
- FindHook / QueryHook / PaginateHook

支持 `before` / `after` 拦截，可用于数据审计、字段自动填充等场景。

### 6.6 多数据源与多租户

通过 `DbKit` 的 `configIdToConfig` 管理多个 DbConfig：
- `Db.use("configId")` 指定数据源
- `ThreadLocal<DbConfig>` 支持线程级数据源切换，用于多租户场景

---

## 七、Enjoy 模板引擎

Enjoy 是独立于 Web 框架的模板引擎（仅依赖自身），设计一致性极高。

### 7.1 Engine 管理

```
Engine.use()               → 使用主引擎
Engine.use("engineName")   → 使用命名引擎
Engine.create("name")      → 创建新引擎
Engine.createIfAbsent()     → 不存在则创建
```

每个 Engine 拥有独立的配置、模板缓存、指令集、共享函数。

### 7.2 ISource 与 SourceFactory

通过 [ISource](file:///Volumes/zdh/projects/alun/aifei/aifei-enjoy/src/main/java/cn/aifei/enjoy/source/ISource.java) 抽象模板来源：
- `FileSource` — 文件系统
- `ClassPathSource` — Classpath
- `StringSource` — 字符串

`cacheKey` 为 null 表示不缓存，这是精巧的契约设计。

### 7.3 扩展机制

| 扩展类型 | 方法 |
|---------|------|
| 自定义指令 | `engine.addDirective(name, class, keepLineBlank)` |
| 共享方法 | `engine.addSharedMethod(obj/class)` |
| 共享对象 | `engine.addSharedObject(name, obj)` |
| 共享模板函数 | `engine.addSharedFunction(file)` |
| FieldGetter | `Engine.addFieldGetter(index, getter)` |
| 扩展方法 | `Engine.addExtensionMethod(target, ext)` |

Template 的 [Func<T>](file:///Volumes/zdh/projects/alun/aifei/aifei-enjoy/src/main/java/cn/aifei/enjoy/Template.java#L144-L146) 接口允许接管 AST 执行，实现 `Ctrl.setAttachment()` 传递任意附加参数，设计灵活度高。

---

## 八、服务器抽象层

### 8.1 解耦设计

```
Server<P1, P2>  ──P1, P2──→  Dispatcher<P1, P2, I, O>  ──I, O──→  Handler<I, O>
```

- **Server** — HTTP 服务器接口（`start/stop/init`）
- **Dispatcher** — 将 Server 的原始请求对象（如 `HttpServerExchange`）转化为框架的 `Input/Output`
- **Handler** — 责任链处理请求

这个三层解耦的优势：切换 Undertow → Tomcat → Netty 只需实现 Server + Dispatcher，业务代码完全不受影响。

### 8.2 Undertow 集成

[UndertowServer](file:///Volumes/zdh/projects/alun/aifei/aifei-undertow/src/main/java/cn/aifei/server/undertow/UndertowServer.java) 作为唯一的内置实现，支持：

- HTTP/HTTPS（内置 SSL 配置）
- HTTP/2
- Gzip 压缩
- HTTP 自动跳转 HTTPS
- 静态资源管理（CompositeResourceManager）
- IO/Worker 线程数可配

### 8.3 Input / Output

[Input](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/core/Input.java) 接口定义了完备的参数获取方法：
- 按名称（`getStr/getInt/getLong/...`）
- 按路径参数位置（`getStr(int)/getInt(int)/...`）
- Bean/List/Map/Array 获取（支持 JSON 顶层转换）
- 全部提供带默认值的 default 方法

[Output](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/core/Output.java) 目前是一个空接口，作为扩展锚点。

---

## 九、JSON 序列化模块

基于 Fastjson2，对其不可知的 Row/Model 类型做了专门适配：

- **RowReader/RowWriter**：Row → JSON 时下划线自动转驼峰
- **ModelReaderModule/ModelWriterModule**：Model 可选择按 Row 模式序列化（取出内部 Map）或按 Getter 模式
- 全局配置通过 [JsonKit](file:///Volumes/zdh/projects/alun/aifei/aifei-json/src/main/java/cn/aifei/json/JsonKit.java) 统一管理

API 统一入口：
```java
Json.of(str).toBean(User.class);        // json → bean
Json.of(bean).toStr();                   // bean → json
```

---

## 十、插件体系

[Plugin](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/plugin/Plugin.java) 只定义两个方法：`start()` / `stop()`，极简。

[AifeiDbPlugin](file:///Volumes/zdh/projects/alun/aifei/aifei-db/src/main/java/cn/aifei/db/core/AifeiDbPlugin.java) 就是一个典型实现：继承 `AifeiDb` 实现 `Plugin`，从而可在 `AifeiConfig.config(plugins)` 中一行代码集成。

---

## 十一、实用工具

### 11.1 AppHome 自动探测

[PathUtil](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/util/PathUtil.java) 通过 classpath 探测应用的根目录（appHome），支持手动指定。所有路径配置（upload/download/template）都基于 appHome。

### 11.2 ComputeCache

[ComputeCache](file:///Volumes/zdh/projects/alun/aifei/aifei/src/main/java/cn/aifei/util/ComputeCache.java) 是 `ConcurrentHashMap.computeIfAbsent` 的增强版，用于 InstanceFactory 缓存构造器。

### 11.3 Prop / PropKit

轻量属性文件读取工具，支持 `aifei.profiles.active` 多环境 Profile 切换。

---

## 十二、设计模式总结

| 模式 | 体现 |
|------|------|
| **门面模式** | `Aop`、`Db`、`Json`、`Log` 都是静态门面 |
| **责任链模式** | `Handler` 通过 `next` 组装成链 |
| **模板方法** | `Argument.init()` 设置公共属性，子类实现 `getValue()` |
| **策略模式** | `Dialect` 接口 + 7 种数据库方言实现 |
| **工厂模式** | `DaoFactory`、`RowFactory`、`BatchFactory`、各 `*Executor` |
| **单例+ThreadLocal** | `AopFactory` 的循环注入检测 |
| **回调/观察者** | `onActionCreated`、`onBeforeTransactionCommit`、Hook 体系 |
| **适配器模式** | `Callback` 将 CGLib/Javassist 适配到统一的 `Invocation` |

---

## 十三、对比 JFinal 的改进

| 方面 | JFinal | Aifei |
|------|--------|-------|
| 泛型设计 | Controller 无泛型 | `AifeiConfig<I,O>` 全链路泛型 |
| 服务器 | Jetty 默认 | Undertow（更现代、更高性能） |
| 路由匹配 | 简单的 Path + Method | Path + pathPara + 参数匹配三阶段 |
| 拦截器分层 | 3 层（Global/Class/Method） | 4 层（新增 Routes 级，最高优先） |
| 参数注入 | getPara/getModel | Argument 体系，可扩展 Method 级 DI |
| 实例化 | `Class.newInstance()` | LambdaMetaFactory + JIT 内联 |
| JDK 版本 | 长期 Java 8 | 支持 Java 8+，兼容 JPMS |

---

## 十四、核心精华提炼

1. **"配置即回调"的启动设计**：`AifeiConfig` 的三个 `config()` 方法让用户无需编写任何启动代码，只需填充配置

2. **全链路泛型一致**：`AifeiConfig<I,O>` → `Settings<I,O>` → `Dispatcher<P1,P2,I,O>` → `Handler<I,O>`，编译期保证 Server 切换时类型安全

3. **路由匹配的三阶段算法**：精确匹配 → 路径参数回退 → 命名参数匹配（ActionGroup），兼顾性能与灵活性

4. **LambdaMetaFactory 构造器**：让 `newInstance()` 被 JIT 内联为 `new T()`，追求零开销

5. **Enjoy 模板引擎做 SQL 模板**：`#where`/`#and`/`#orderBy` 三个指令让动态 SQL 条件拼接变得优雅且防注入

6. **事务隐式提交**：用户不能主动 commit，只能 rollback，消除误用可能

7. **Argument 即 Method 级 DI**：注入时拥有请求上下文，可实现普通 DI 做不到的"注入当前登录用户""注入 SSE 流"等场景

8. **Scanner 通用化**：类扫描不绑定路由用途，通过 `Predicate<Class<?>>` 过滤，一个扫描器可同时用于路由、AOP 注入发现、Plugin 发现等多种场景

9. **内外 SQL 热加载统一管理**：通过 `sqlFromSqlFile` 精准区分内外 SQL，热加载时只重载外部文件 SQL，不影响通过 `Db.sql()` 缓存的 SQL

10. **扩展点无处不在**：`onActionCreated`、`onCreateProxy`、Argument 注册、Hook 体系、自定义指令、FieldGetter，几乎每个模块都有可插拔的扩展钩子

---

> 总体评价：Aifei 是一个设计极端克制的框架——核心代码量极少但设计密度极高。它不追求功能大而全，而是把每个模块的核心路径打磨到极致，同时在关键节点提供恰到好处的扩展点。这是一个真正理解"少即是多"的框架作品。
