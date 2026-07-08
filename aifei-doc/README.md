# Aifei 文档

# 什么是 Aifei

Aifei 是一款用于 AI Coding 的 Java 服务端框架。

其核心设计目标是极小化 Token 消耗、极大化 Attention 浓度，让 AI 稳定生成高品质代码。


# Just Service 范式

前后端分离架构在近十年间逐步成为主流。Web 应用中的页面路由、交互编排与渲染职责大量转移到了前端，react-router、vue-router 这样的前端路由库已经接管了过去由服务端 Controller 承担的职责。

路由逻辑转移至前端，促使 Aifei 开创 Just Service 范式。该范式也是 Aifei 成为 AI Coding 框架的关键设计之一。

Just Service 开发范式之下，无需编写 Controller、Render、Repository、Mapper 等等这类冗余代码，让 AI 集中注意力写业务代码。


# 快速上手

## Aifei Demo

想要快速体验 Aifei，可以直接试试 [aifei-demo](https://gitee.com/jfinal/aifei-eco/tree/main/aifei-demo) 。

aifei-demo 是一个标准的 maven 工程，按说明文件初始化数据库，导入 IDEA 运行 AifeiDemo 内的 main 方法即可体验。

## 创建 Aifei 应用

### 创建配置中心

继承 AifeiConfig 抽象类，创建配置中心。

```java
/**
 * Aifei 配置中心
 */
public class AppConfig implements AifeiConfig<In, Out> {

    // 偏好配置
    public void config(Settings<In, Out> settings) {
        // 配置 Server、Dispatcher、Handler
        settings.setServer(new UndertowServer(), new IoDispatcher()).addHandler(new IoHandler());
    }

    // 路由配置
    public void config(Routes routes) {
        routes.scan("cn.aifei.demo");
    }

    // 插件配置
    public void config(Plugins plugins) {}
}
```

配置中心 settings.setServer(...) 方法用于配置上游 Server、中游 Dispatcher、下游 Handler。

UndertowServer 为 Aifei Server 接口官方 undertow 实现，该实现扔掉了 Servlet，性能更高，代码更简洁，启动快至毫秒级。你也可以自己定制自己的 Server 实现，如 TomcatServer、NettyServe。

IoDispatcher 为 UndertowServer 配置套的 IO 调度器，负责将上游 IO 传递给下游 Handler。

IoHandler 为用户掌控的处理流程，In, Out 为用户掌控的输入输出数据结构。

配置中心 routes.scan(...) 方法用于扫描指定包及其子包下的路由，路由通过 @Path 注解配置。

### 实现 HIO

Aifei 采用 HIO 自主架构（Handler + Input + Output），让用户自主掌控处理流程、数据结构。

Handler 用于掌控处理流程，Input、Output 用于掌控输入输出数据结构。

HIO 的实现需要由开发者自主掌控，可参考 Aifei Demo 实现自己的 HIO，初次使用建议复制 Aifei Demo 工程中的 HIO 代码。

HIO 源于这十多年来我对框架设计的思考与实践，篇幅关系当下只做一个列表形式的概要罗列：

- 框架的扩展性需要增加大量新概念代码量，如 JFinal 时代的整个 render 模块。通过 HIO 可消除这些概念、接口、代码。

- 扩展性更强、更简单、更灵活。如增加业务层返回值类型处理，只需在 IoHandler 直接添加处理逻辑即可。

- 反直觉的观察：如果框架什么都做好喂给用户，用户的学习成本反而更高，边际收益递减。

- 将极简推至全新高度。内核仅 3333 行代码，内核核心仅 260 行代码。


### 创建 Service

框架搭好之后，只需要专注编写业务代码：

```java
@Path("/")
public class TaskService {
    public List<Task> list() {
        return Task.sql("select * from task").find();
    }
}
```

### 启动

创建入口类与 main 方法即可启动 Aifei 项目：

```java
/**
 * 启动入口
 */
public class AifeiDemo {
    public static void main(String[] args) {
        Aifei.start(new AppConfig(), args);
    }
}
```


# 配置

Aifei 提供 AifeiConfig 接口，将配置集中式管理。

## AifeiConfig 接口

AifeiConfig 接口共有三个待实现方法以及两个 default 方法:

```java
public interface AifeiConfig<I extends Input, O extends Output> {
    void config(Settings<I, O> settings);
    void config(Routes routes);
    void config(Plugins plugins);
    default void onStart() {}
    default void onStop() {}
}
```

### Input、Output 泛型

Input、Output 泛型用于统一系统 IO 数据类型。

具体来说是统一 config(Settings<I, O>) 内 Settings.setServer()、Settings.addHandler() 方法所涉及的 Dispatcher、Handler 的 IO 类型。

### config(Settings<I, O>)

用于配置上游 Server、中游 Dispatcher、下游 Handler 链条，以及配置日志、全局拦截器、文件上传路径、文件下载路径。

### config(Routes)

用于配置路由。可以配置为路径扫描，也可以配置为手动添加路由。

```java
    public void config(Routes routes) {
        // 扫描 "cn.aifei.vip" 包添加路由，被添加路由应用 AuthInterceptor 拦截器
        routes.scan("cn.aifei.vip", new AuthInterceptor());
    
        // 手动添加路由
        routes.add("/vip", VipService.class);
    }
```

如上例所示，routes.scan 方法将扫描 "cn.aifei.vip" 包，将其中使用了 @Path 注解的类添加为路由，并应用 AuthInterceptor 拦截器。

对于性能有极致需求的应用，可通过 routes.add 方法手动添加路由。

如需支持 action 重载可以通过 routes.setActionOverload(true) 开启。

### config(Plugins)

用于添加插件，插件需实现 Plugin 接口，该接口仅有 start()、stop() 两个简单接口方法，两者分别在 Aifei 启动后、关闭前被回调。

### onStart()

onStart 会在 Aifei 启动完插件之后被回调，可当成启动时的钩子函数使用。

### onStop()

onStart 会在 Aifei 关闭插件之前被回调，可当成关闭时的钩子函数使用。


# Service

Aifei 开创 Just Service 范式，在配置完成之后，可以直接开始写 Service。

通过注解 @Path 为 Service 添加路由之后即可被访问。

```java
@Path("/vip")
public class VipService {
    public Vip findById(int id) {
        return Task.findById(id);
    }
}
```

以上 findById 方法可通过 "/vip/findById" 并传入 id 参数访问到。

如果不希望 Service 中的部分方法被访问，可以去掉 public 修饰，或者使用 @NoPath 注解。


# 数据库访问

## AifeiDbPlugin

AifeiDbPlugin 作为 Aifei 插件，用于配置与启动数据库功能。

```java
// 在 AifeiConfig 接口实现类中添加 AifeiDbPlugin
public void config(Plugins plugins) {
    // 数据源
    DruidSupplier ds = new DruidSupplier(jdbcUrl, user, password);

    // Aifei Db 插件
    AifeiDbPlugin dbPlugin = new AifeiDbPlugin("main", ds);
    
    dbPlugin.setDialect(new MysqlDialect());    // 配置方言
    dbPlugin.addModelSet(new ModelSet());       // 添加生成的 Model 集合
    dbPlugin.setPrintSql(true);                 // 配置打印 SQL，便于排错与优化
    dbPlugin.setFormatSql(false);               // 配置 SQL 格式化
    // dbPlugin.setPrintSqlToLog(true);         // 配置 SQL 打印到日志文件，便于找到慢 SQL 进行优化
    // dbPlugin.config(c -> c.setXxx(...));     // 通过 config 配置内部各组件
    
    // 添加插件
    plugins.add(dbPlugin);
}
```

## Db + Row 模式

Aifei 数据库模块采用 Db + Row 模式。Db 为数据库操作入口工具，Row 为操作对象和操作结果。

Db + Row 模式与 JFinal 的 Db + Record 模式非常类似，只需注意前者传递 SQL + Para 是通过独立的 sql(sql, para) 方法，
会用 JFinal 的同学知道了这点即可立即学会 95% 的用法。

```
    // Aifei 用法
    Db.sql(sql, para).find();
    
    // JFinal 用法
    Db.find(sql, prara);
```

## 生成器

Aifei 没有 Model 概念，对于 Model 的支持完全由生成器实现。使用官方生成器一键生成 Model 以及相关文件之后即可通过 Model 操作数据库。

生成器使用：

```
    // model 生成的基础包名
    String basePackage = "cn.aifei.vip.common.db";
    
    // model 生成的基础路径
    String basePath = System.getProperty("user.dir") + "/src/main/java/" + basePackage.replace('.', '/');
    
    // 调用生成器生成文件
    new Generator(new MysqlDialect(), dataSource, basePackage, basePath).generate();
```

生成器最基本的用法如上例所示，基本用法仅三行代码，默认配置之外的用法参考 aifei-demo 工程。生成器生成完 Model 之后的用法见后续章节中相关代码示例。

## 插入数据

通过 Model 插入

``` 
    // Model 已绑定表名、主键名，只需关心字段
    new User().name("Aifei").insert();
    
    // Model 已绑定表名、复合主键名，只需关心字段
    new UserRole().userId(123).roleId(456).insert();
```

实际项目中尽可能使用 Model 插入，避免用字符串方式书写字段名，便于修改 table 字段名后重构代码。

通过 Row 插入

```
    // of 方法指定表名，主键名默认为相关 Dialect 中的 defaultPrimaryKey 值
    Row.of("user").set("name", "Aifei").insert();
    
    // of 方法同时指定表名、主键名
    Row.of("user", "id").set("name", "Aifei").insert();
    
    // of 方法同时指定表名、复合主键名，compositeId 为两个复合主键值赋值
    Row.of("user_role", "user_id", "role_id").compositeId(123, 456).insert();
```

## 删除数据

通过 Row 与 Model 对象删除

```
    // id 方法赋予单主键值
    Row.of("user").id(123).delete();
    
    // compositeId 方法赋予复合主键值
    Row.of("user_role", "user_id", "role_id").compositeId(123, 456).delete();
    
    // id 方法赋予单主键值
    User.of(123).delete();
    
    // userId、roleId 方法赋予复合主键值
    new UserRole().userId(123).roleId(456).delete();
```

通过 deleteById 删除

```
    // 需传入表名 table，主键名默认为相关 Dialect 中的 defaultPrimaryKey 值
    Db.deleteById("user", 123);
    
    // 主键名不为 "id" 需指定
    Db.deleteById("some_table", "code", 123);
    
    // 复合主键删除，分别指定：表名、主键名1、主键名2、主键值1、主键值2
    Db.deleteByCompositeId("user_role", "user_id", "role_id", 123, 345);
    
    // Model 已绑定表名、主键名，只需关心字段
    User.deleteById(123);
    
    // 复合主键 Model 需明确指定主键名、主键值，并保持次序
    UserRole.deleteByCompositeId("user_id", "role_id", 123, 456);
```

通过 deleteInIds 删除

```
    // 删除 user 表中 id 为 12、34 的数据
    Db.deleteInIds("user", 12, 34);
    
    // Model 已绑定表名、主键名，只需关心 id 值
    User.deleteInIds(12, 34);
```

通过 deleteBy 使用 where 条件批量删除

```
    // 传入表名、带问号的 where 条件以及对应的参数值
    Db.deleteBy("user", "age < ? and age > ?", 18, 25);

     // Model 已绑定表名，无需传入表名
    User.deleteBy("age < ? and age > ?", 18, 25);
```

## 更新数据

通过 Model 更新

``` 
    User.of(123).name("James").update();
```

实际项目中尽可能使用 Model 更新，避免用字符串方式书写字段名，便于修改 table 字段名后重构代码。

通过 Row 更新

```
    Row.of("user").id(123).set("name", "James").update();
```

## 查询

### 查询分类

以 sql(...) 方法为入口归为一类，其它为另一类。

前者需传入完整 SQL，后者无需传入完整 SQL。

```
    // 第一类：以 sql 方法为入口，需传入完整 SQL
    Db.sql("select * from girl where age >= ?", 18).find();
    
    // 第二类：不以 sql 方法为入口，无需传入完整 SQL，此处只需传入 SQL 中的表名 "girl"
    Db.findById("girl", 123);
```

以 Db 为入口归为一类，以生成的 Model 为入口归为另一类。

前者查询时要传入表名，后者不需要，因为后者已与表名进行过绑定。

后续例子中的 Girl 为生成的器生成的 Model：

```
    // Db 为入口需传入表名 girl
    Db.findById("girl", 123);

    // Girl 已绑定表名 girl，无需传入
    Girl.findById(123);
    
    // 在 girl 表查询 id 值为 1、2、3 的数据，并只返回 id、name 字段
    Db.select("id, name").findInIds("girl", 1, 2, 3);
    
    // Girl 已绑定表名 girl，无需传入
    Girl.select("id, name").findInIds(1, 2, 3);
```

### 常用查询

sql(...) 方法为入口的常用查询：

```
    // 先定义完整 sql，后续共享
    String sql = "select id, name from girl where age >= ? and age <= ?";

    // 完整 sql 查询
    Girl.sql(sql, 18, 25).find();
    
    // 查第 1 条数据
    Girl.sql(sql, 18, 25).findFirst();
    
    // 查 1 条数据。如果数据不为 1 条，则抛出异常
    Girl.sql(sql, 18, 25).findOne();
    
    // 查 1 条或 0 条数据。允许查到 1 条或 0 条数据，否则抛出异常
    Girl.sql(sql, 18, 25).findOneOrNull();
    
    // 分页
    Girl.sql(sql, 18, 25).paginate(1, 30);
    
    // 遍历查询结果，打印 name 字段
    Girl.sql(sql, 18, 25).forEach( girl -> {
        System.out.println(girl.getName());
        return true;    // 返回 true 继续遍历，否则终止
    });
    
    // 遍历所有分页，每页 100 条记录
    Girl.sql(sql, 18, 25).forEachPage(100, page -> {
        // 输出当前页的页号
        System.out.println(page.getPageNum());

        // 遍历每一页数据并输出
        for (Girl girl : page.getRows()) {
            System.out.println(girl.getName());
        }

        // 返回 true 继续遍历下一页，否则终止
        return true;
    });
    
```

非 sql(...) 方法为入口的常用查询：

```
    // 查询 girl 表中指定 id 值的数据
    List<Object> ids = Arrays.asList(1, 2, 3);
    Girl.findInIds(ids);              // 支持 List
    Girl.findInIds(4, 5, 6);          // 支持 Object...
    
    // 使用 select 方法，仅获取 id、name 字段
    Girl.select("id, name").findInIds(4, 5, 6);
    
    // findBy 支持传入 where、order by 子句
    Girl.findBy("age >= ? and age <= ? order by age", 18, 25);
    
    // 使用 select 方法，仅获取 id、name 字段
    Girl.select("id, name").findBy("age >= ? and age <= ? order by age", 18, 25);
    
    // 查询符合条件的第 1 条记录
    Girl.findFirstBy("age >= ?", 18);
```

### where 与 and 指令

Db.sql 方法支持 enjoy 模板引擎，模板用法与 JFinal 完全一样。除了支持 JFinal 已有指令之外，新增了 where、and、orderBy 三个指令。

```
    // 演示 where、and 指令。
    String sql = "select * from user #where(age, '>', age) #and(create_date, '>', createDate)"
    Kv kv = Kv.of("age", 18).set("createDate", '2026-01-23');
    List<Row> list = Db.sql(sql, kv).find();
    
    //  #orderBy(updated)
    
```

where 与 and 指令参数用法完全一样，用法上的唯一区别是 where 指令放在查询条件第一的位置。

where 与 and 指令参数说明:

- 第一个参数直接生成到 SQL 之中。如果需要使用别名前缀可使用字符串，例如：

```
    select * from user as u join dept d on u.dep_id = d.id 
    #where('u.age', '>' age)
    #and('u.created_date', '>', createDate)
```

以上用法中的 'u.age' 与 'u.created_date' 为字符串类型的参数，会直接当成字符串生成到 SQL 中。

- 第二个参数是操作符，必须是字符串，支持的操作符有：

```
    =
    !=
    <>
    >
    >=
    <
    <=
    between
    not between
    in
    not in
    is null
    is not null
    like
    not like
    contains       等价于 like %关键词%
    notContains    等价于 not like %关键词%
    startsWith     等价于 like 关键词%
    endsWith       等价于 like %关键词
```

- 第三个参数对接 Db.sql(sql, para) 方法的 para 参数，将从该参数中取值，可以任意取名例如：

```
    String sql = "select * from user #where(age, '>', xyz)";
    
    // 由于 where 指令的第三个参数名为 xyz，所以下面构建的参数名也必须为 "xyz" 
    Kv para = Kv.of("xyz", 18);
    
    Db.sql(sql, para).find();
```

第三个参数用于生成 SQL 参数对应的值，可任意取值，在实际使用时可使用 "驼峰" 命名与客户端传递的参数保持一致。

### orderBy 指令

orderBy 指令用于生成 SQL 中的 ORDER BY 子句，该指令接收一个或多个参数，参数仅作为可用于排序字段的白名单，
具体使用哪些字段用于排序依赖实际传入的参数。

orderBy 所需参数值约定格式为(以 JSON 表达)：

```
    {
        orderBy: {field: 字段名, order: 排序方向}
    }
```

orderBy 指令使用实例：

```java
public List<Row> search(Map<?, ?> filter) {
    // 如果 filter 参数为 orderBy: {field: 'updated', order: 'desc'}
    // 生成的 SQL order by 子句为： ORDER BY updated DESC
    String sql = "select * from user #orderBy(id, updated)";    // id、updated 仅作为可用于排序的白名单
    return Db.sql(sql, filter).find(); 
}
```

以上代码 orderBy 指令会从 filter 中读取出 orderBy 变量，根据内部的 field、order 值生成 ORDER BY 子句，生成之前会检测 field 是否在白名单之内。

如果有用于生成 order by 的字段名与传递的参数名不同，可以使用 'field name: para name' 这种参数格式来映射：

```java
public List<Row> search(Map<?, ?> filter) {
    // 如果 filter 参数为 orderBy: {field: 'payTime', order: 'asc'}
    // 生成的 SQL order by 子句为： ORDER BY pay_time ASC
    String sql = "select * from user #orderBy('pay_time : payTime', expire)";
    return Db.sql(sql, filter).find();
}
```

上例中的字段名 pay_time 是数据库表字段名，而 payTime 是传入的参数名，通过 'pay_time : payTime' 做了字段名到参数名的映射。

此外，如果需要支持多字段排序，可以为 orderBy 给定数组，以下以 JSON 格式表达其结构：

```
    {
      orderBy: [
        {field: 'updated', order: 'desc'},
        {field: 'age', order: 'asc'}
      ]
    }
```

以上结构参数生成的 order by 子句为： ORDER BY updated DESC, age ASC


# JFinal 用户

JFinal 发布十五年来被证明最有价值的设计被保留到了 Aifei 之中，并做了进一步优化。本章内容介绍 JFinal 用户如何快速掌握 Aifei。

## 配置中心

AifeiConfig 为 JFinalConfig 设计的保留，设计思想一致: 将配置集中管理。

AifeiConfig 中只保留了 config(Settings)、config(Routes)、config(Plugins) 这三个抽象方法。以及 onStart()、onStop() 两个 default 方法，用法与 JFinal 基本一致。

## AOP

拦截器基本上完整保留了 JFinal 的设计与用法。一个比较小的变化是控制层、业务层全局拦截器合并为了 "全局拦截器"，因为 Just Service 范式下没有控制层这个概念。

全局拦截器的配置转移到了 config(Settings) 之中。Interceptor、Inject、@Before、@Clear 用法完全一样。

新增 Implement 注解用于标识接口的实现类，新增 AopKit.get().scanImplement(...) 可用扫描方式为接口注册实现类。

## 数据库模块

aifei-db 在拥有 JFinal Active Record 几乎所有功能的前提下，对整个系统架构进行了全新设计。

API 的变化在于改为了链式调用，例如 Db.find(String sql, Object...) 改成了 Db.sql(String sql, Object...).find()

所有查询统一使用 sql(sql, para) 方法先传入 SQL 与 Para，然后再链式调用功能性 API，如 find、paginate、query 等等获取数据。

此外，aifei-db 没有 Model 的概念，而是通过 Generator 生成 Model，生成的 Model 无需创建 dao 实例即可使用。

以下例子展示生成器生成 User 后的用法:

```
    new User().name("James").insert();
    
    User.deleteById(123);
    
    User.of(123).name("Zhanbo").update();
    
    User.sql("select * from user").find();
```

生成器已在 aifei-db 中提供，具体用法见 [aifei-demo](https://gitee.com/jfinal/aifei-eco/tree/main/aifei-demo)


### 未完待续 ...


