# aifei-cron4j

![Java](https://img.shields.io/badge/Java-8+-orange)
![License](https://img.shields.io/badge/License-Apache--2.0-blue)
![cron4j](https://img.shields.io/badge/cron4j-2.2.5-green)

**aifei-cron4j** 是 [aifei](https://aifei.cn) 生态下的轻量级定时任务插件，对经典的 [cron4j](http://www.sauronsoftware.it/projects/cron4j/) 调度库做了一层薄封装：用最常见的 **5 段式 Linux cron 表达式**驱动任务，API 极简，开箱即用。

> 本插件移植自 JFinal 的 `Cron4jPlugin`，并针对 aifei 的 `Plugin` 约定与 `PropKit` 配置体系做了适配。相比原版，本文档补充了独立构件的使用方式、可运行的最小示例、配置/API 速查表与 FAQ。

---

## 目录

- [一、特性](#一特性)
- [二、安装](#二安装)
- [三、30 秒上手](#三30-秒上手)
- [四、cron 表达式](#四cron-表达式)
- [五、三种使用方式](#五三种使用方式)
  - [1. Java 硬编码](#1-java-硬编码)
  - [2. 外部配置文件](#2-外部配置文件)
  - [3. 高级：调度外部程序（ProcessTask）](#3-高级调度外部程序processtask)
- [六、任务类型](#六任务类型)
- [七、生命周期与线程](#七生命周期与线程)
- [八、配置项速查](#八配置项速查)
- [九、API 速览](#九api-速览)
- [十、常见问题（FAQ）](#十常见问题faq)
- [十一、选型建议](#十一选型建议)

---

## 一、特性

- **5 段 cron 表达式**：与 Linux `crontab` 一致，会 Linux cron 即可上手（注意：**不是** Quartz 的 7 段表达式）。
- **多种任务类型**：`Runnable`、`CronTask`（带 `stop()` 回调）、cron4j 原生 `Task`、`ProcessTask`（调度外部命令/脚本）。
- **配置文件驱动**：一个 `.txt` 即可声明任意多个任务，改调度策略无需改代码、无需重新打包。
- **细粒度控制**：每个任务可单独配置 `cron`、`daemon`（守护线程）、`enable`（启停）。
- **aifei 原生**：实现 `cn.aifei.plugin.Plugin`，可直接用 `PropKit.use(...)` 加载配置、用 `cn.aifei.log.Log` 打日志。
- **零外部依赖心智**：`cron4j` 已作为本构件的传递依赖一并引入，使用者无需再单独声明。

---

## 二、安装

### Maven

```xml
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei-cron4j</artifactId>
    <version>1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'cn.aifei:aifei-cron4j:1.0'
```

> `cron4j`（`it.sauronsoftware.cron4j:cron4j:2.2.5`）与 aifei 核心会作为传递依赖自动引入，通常无需手动添加。

---

## 三、30 秒上手

最小可运行示例：定义一个 `Runnable` 任务，每分钟执行一次。

```java
package com.example;

import cn.aifei.cron4j.Cron4jPlugin;

public class Demo {
    public static void main(String[] args) {
        Cron4jPlugin cp = new Cron4jPlugin();
        cp.addTask("* * * * *", () -> System.out.println("心跳检测..."));

        cp.start();

        // 应用退出时优雅停止调度器，避免任务被中途打断（视情况可选）
        Runtime.getRuntime().addShutdownHook(new Thread(cp::stop));
    }
}
```

就这三步：**建插件 → 加任务 → `start()`**。

> 在 aifei 项目中：`Cron4jPlugin` 实现了 `cn.aifei.plugin.Plugin`（`start()`/`stop()` 生命周期），可按你项目既有的插件注册方式纳入管理；若你的 aifei 版本尚未提供集中式插件注册器，直接像上面这样手动 `start()` / `stop()` 即可，`start()` 内部幂等，重复调用安全。

---

## 四、cron 表达式

### 4.1 五段含义

| 段 | 含义 | 取值 | 别名 |
|----|------|------|------|
| 1 | 分 | `0–59` | — |
| 2 | 时 | `0–23` | — |
| 3 | 天（日） | `1–31` | `L` 表示当月最后一天 |
| 4 | 月 | `1–12` | `jan`…`dec` |
| 5 | 周 | `0–6`（0=周日，6=周六） | `sun`…`sat` |

### 4.2 特殊字符

| 字符 | 含义 | 示例 |
|------|------|------|
| `n`（数字） | 具体时间点 | `5 * * * *` → 每小时的第 5 分 |
| `,` | 枚举多个值 | `3,5 * * * *` → 第 3 分和第 5 分 |
| `-` | 区间 | `1-3 * * * *` → 第 1、2、3 分 |
| `*` | 任意（每一个） | `* * * * *` → 每分钟 |
| `/` | 步长（每隔 N） | `*/5 * * * *` → 每 5 分钟 |

### 4.3 常用速查表

| 需求 | 表达式 |
|------|--------|
| 每分钟 | `* * * * *` |
| 每 5 分钟 | `*/5 * * * *` |
| 每 30 分钟 | `*/30 * * * *` |
| 每小时的第 0 分 | `0 * * * *` |
| 每天 0 点 | `0 0 * * *` |
| 每天凌晨 2:30 | `30 2 * * *` |
| 每月 1 号 0 点 | `0 0 1 * *` |
| 每周一 0 点 | `0 0 * * 1` |
| 工作日（周一至周五）每 30 分钟 | `*/30 * * * 1-5` |
| 每月最后一天 23:59 | `59 23 L * *` |
| **从第 10 分起每 3 分（正确写法）** | `10-59/3 * * * *` |

> ⚠️ **关于 `/` 的常见坑（实测验证）**：cron4j 里 `m/n`（不带区间或星号，如 `10/3`、`0/2`）**不像** Linux 那样表示“从 m 开始每 n 分”。实测在 cron4j 中：
> - `10/3 * * * *` → 被解析成“每小时的第 10 分”：`00:10 01:10 02:10 …`
> - `0/2 * * * *` → 被解析成“每小时整点”：`01:00 02:00 …`
>
> 都**不是**你想要的步长。正确写法是显式给出区间或星号：`10-59/3 * * * *`（实测 `00:10 00:13 00:16 …`）、`*/2`（实测等价于 `0-59/2`，均为 `00:02 00:04 00:06 …`）。**诀窍：用 `/` 时始终带上区间或星号。**

---

## 五、三种使用方式

### 1. Java 硬编码

最直接，适合任务数量少、无需热调整的场景。

```java
Cron4jPlugin cp = new Cron4jPlugin();

// Runnable
cp.addTask("* * * * *", new MyTask());

// CronTask：多一个 stop() 回调，插件停止时会被调用（见“任务类型”）
cp.addTask("0 * * * *", new MyStoppableTask());

// 指定为守护线程（随 JVM 退出而终止，不等待执行完）
cp.addTask("*/10 * * * *", new HeartbeatTask(), true);

// 完整参数：cron, task, daemon, enable
cp.addTask("0 0 * * *", new DailyTask(), false, true);

cp.start();
```

`addTask` 支持链式调用（返回 `this`）：

```java
Cron4jPlugin cp = new Cron4jPlugin()
        .addTask("* * * * *", new TaskA())
        .addTask("0 * * * *", new TaskB());
cp.start();
```

### 2. 外部配置文件

生产中更推荐用配置文件声明任务，便于随时调整调度策略、按环境启停，而无需改动代码。

在 classpath 下新建 `cron4j.txt`：

```properties
# 入口：声明本次要调度哪些任务（逗号分隔）
cron4j=task1, task2

# 任务一：每分钟执行一次
task1.cron=* * * * *
task1.class=com.example.TaskAaa
task1.daemon=true
task1.enable=true

# 任务二：每小时第 0 分执行一次，且暂时停用
task2.cron=0 * * * *
task2.class=com.example.TaskBbb
task2.daemon=true
task2.enable=false
```

**配置说明：**

- 第 1 行的 `cron4j` 是配置入口名（`configName`），后面跟着任务名 `task1`、`task2`。
- 其后所有配置项都以**任务名**打头，共有四项：`cron`、`class`、`daemon`、`enable`。
- `cron4j` 这个入口名可自定义，在 `new Cron4jPlugin(...)` 时传入即可。

创建插件的四种等价写法：

```java
// ① 仅传文件名（入口名默认为 "cron4j"）
Cron4jPlugin cp = new Cron4jPlugin("cron4j.txt");

// ② 同时指定入口名（对应配置文件里 myCron4j=task1, task2）
Cron4jPlugin cp = new Cron4jPlugin("cron4j.txt", "myCron4j");

// ③ 用 PropKit 加载（推荐：自带缓存 + profile 自动合并）
Cron4jPlugin cp = new Cron4jPlugin(PropKit.use("cron4j.txt"));

// ④ 用 PropKit 加载 + 指定入口名
Cron4jPlugin cp = new Cron4jPlugin(PropKit.use("cron4j.txt"), "myCron4j");

cp.start();
```

> 💡 **aifei 增强点**：`PropKit.use(...)` 支持 profile 自动合并。例如 `cron4j.txt` 中配置 `aifei.profiles.active = pro`，则 `PropKit.use("cron4j.txt")` 会自动追加 `cron4j-pro.txt` 的内容，方便在 dev/pro 等环境间切换调度策略。

### 3. 高级：调度外部程序（ProcessTask）

`ProcessTask` 可以直接调度一个外部可执行程序或脚本，非常适合“每天凌晨备份数据库并打包”这类场景。

```java
import cn.aifei.cron4j.Cron4jPlugin;
import it.sauronsoftware.cron4j.ProcessTask;
import java.io.File;

Cron4jPlugin cp = new Cron4jPlugin();

// 命令、环境变量、工作目录
String[] command = { "/opt/app/bin/backup.sh" };
String[] envs    = { "APP_HOME=/opt/app", "JAVA_HOME=/usr/lib/jvm/java-8" };
File   directory = new File("/opt/app");

ProcessTask task = new ProcessTask(command, envs, directory);

// 每天凌晨 2 点执行备份
cp.addTask("0 2 * * *", task);

cp.start();
```

> 目前 `ProcessTask` 的构造参数（命令/环境/目录）只能通过 Java 代码创建并 `addTask`，配置文件方式暂不支持对 `ProcessTask` 构造参数的声明。更详细的构造方式见 `Cron4jPlugin.java` 源码注释。

---

## 六、任务类型

| 任务类型 | 接口/类 | 说明 | stop() 回调 |
|----------|---------|------|-------------|
| 普通任务 | `java.lang.Runnable` | 最常用，实现 `run()` 即可 | ❌ |
| 可停止任务 | `cn.aifei.cron4j.CronTask` | 继承 `Runnable`，多一个 `stop()` | ✅ |
| cron4j 原生 | `it.sauronsoftware.cron4j.Task` | 需要更细粒度控制（状态、状态监听等） | — |
| 外部程序 | `it.sauronsoftware.cron4j.ProcessTask` | 调度命令/脚本 | — |

### CronTask 示例（推荐用于需要释放资源的任务）

```java
import cn.aifei.cron4j.CronTask;

public class MyStoppableTask implements CronTask {

    @Override
    public void run() {
        // 业务逻辑：定期拉取数据、清理临时文件等
    }

    @Override
    public void stop() {
        // 插件停止时由 Cron4jPlugin 回调
        // 在这里关闭连接、释放资源、保存状态等
    }
}
```

只要任务类实现了 `CronTask`，`Cron4jPlugin.stop()` 就会自动回调它的 `stop()`；普通 `Runnable` 则不会被回调。

---

## 七、生命周期与线程

- **`start()`**：调度所有已启用（`enable=true`）的任务。**幂等**，重复调用不会重复启动。
- **`stop()`**：停止所有任务；若任务实现了 `CronTask`，会先回调其 `stop()`。
- **`daemon`（守护线程）**：
  - `false`（默认）：任务为**非守护线程**，JVM 退出时会**等待**当前正在执行的任务跑完。
  - `true`：任务为**守护线程**，JVM 退出时会**直接终止**，不保证任务执行完毕。
  - 因此：**必须跑完的重要任务（如对账、结算）建议 `daemon=false`**；可被安全打断的辅助任务（如心跳、监控上报）可用 `daemon=true`。
- **优雅关闭建议**：把 `cp.stop()` 注册到 `Runtime` 的 shutdown hook，确保应用被 `kill` 时调度器被正确关闭。
- **配置类校验时机**：`cron`、`class` 为空或任务类不可实例化时，构造阶段即抛异常，**fail-fast**，避免带着错误配置悄悄启动。

```java
Cron4jPlugin cp = new Cron4jPlugin("cron4j.txt");
cp.start();
Runtime.getRuntime().addShutdownHook(new Thread(cp::stop));
```

---

## 八、配置项速查

（适用于 [外部配置文件](#2-外部配置文件) 方式）

| 配置项 | 含义 | 必填 | 默认值 |
|--------|------|------|--------|
| `<任务名>.cron` | 5 段 cron 表达式 | ✅ 是 | — |
| `<任务名>.class` | 任务类全限定名（需有可访问的无参构造器） | ✅ 是 | — |
| `<任务名>.daemon` | 是否守护线程 | ❌ 否 | `false` |
| `<任务名>.enable` | 是否启用 | ❌ 否 | `true` |

任务类必须是 `Runnable`、`CronTask`、`ProcessTask` 或 cron4j `Task` 之一，否则启动时报错。

---

## 九、API 速览

### 构造器

| 构造器 | 说明 |
|--------|------|
| `Cron4jPlugin()` | 空构造，配合 `addTask` 使用 |
| `Cron4jPlugin(String configFile)` | 从 classpath 加载配置，入口名默认 `cron4j` |
| `Cron4jPlugin(Prop configProp)` | 使用已有 `Prop` 对象 |
| `Cron4jPlugin(String configFile, String configName)` | 加载配置 + 指定入口名 |
| `Cron4jPlugin(Prop configProp, String configName)` | 使用已有 `Prop` + 指定入口名 |

### `addTask` 重载

每个任务类型都有三档重载（链式返回 `this`）：

| 签名 | 说明 |
|------|------|
| `addTask(String cron, T task)` | `daemon=false, enable=true` |
| `addTask(String cron, T task, boolean daemon)` | 指定 `daemon`，`enable=true` |
| `addTask(String cron, T task, boolean daemon, boolean enable)` | 完整控制 |

其中 `T` ∈ { `Runnable`, `ProcessTask`, cron4j `Task` }（`CronTask` 作为 `Runnable` 的子接口，直接走 `Runnable` 重载）。

### 生命周期

| 方法 | 说明 |
|------|------|
| `void start()` | 启动调度（幂等） |
| `void stop()` | 停止调度，回调 `CronTask.stop()` |

---

## 十、常见问题（FAQ）

**Q1：任务执行抛了异常，下一次还会按计划执行吗？**
会。每次调度都是独立的，上一次是否抛异常、是否执行完，都与下一次调度无关。建议在任务内部用 `try/catch` 兜底并记录日志，避免异常被吞。

**Q2：上一次任务还没执行完，下一次调度时间又到了，会重复执行吗？**
会。cron4j 不会因为上次未完成而跳过本次。若任务可能长于调度间隔，需自行在任务内做并发控制（如加锁、状态标志位）。

**Q3：cron 表达式是 5 段还是 7 段？**
**5 段**（分 时 天 月 周），与 Linux `crontab` 一致，**不是** Quartz 的 7 段。网上搜到的 7 段表达式不要直接套用进来。

**Q4：`*/N` 和 `m/N` 有什么区别？为什么要写 `10-59/3` 而不是 `10/3`？**
cron4j 对 `/` 的处理要求**显式区间**：`10/3` 在 cron4j 中并不表示“从 10 开始每 3 分”。正确写法是 `10-59/3 * * * *`。诀窍：**用 `/` 时始终带上区间**，最简形式 `*/N` 也是合法的（等价于 `0-59/N`）。

**Q5：启动时报 “Properties file not found in classpath” 怎么办？**
`new Cron4jPlugin("cron4j.txt")` 与 `PropKit.use("cron4j.txt")` 都从 **classpath** 加载（不是文件系统路径）。请把 `cron4j.txt` 放到 `src/main/resources` 下；若在子目录，用 `subdir/cron4j.txt` 形式。

**Q6：怎么按 dev/pro 环境用不同的调度配置？**
利用 aifei `PropKit` 的 profile 能力：在 `cron4j.txt` 里写 `aifei.profiles.active=pro`，再放一个 `cron4j-pro.txt` 覆盖 `*.cron`/`*.enable` 等项，`PropKit.use("cron4j.txt")` 会自动合并。

**Q7：守护线程 `daemon` 该怎么选？**
重要、必须跑完的任务（对账、结算、入库）用默认 `false`；可被安全打断的辅助任务（心跳、指标上报）可用 `true`。

**Q8：能在 Spring / 非 aifei 项目里用吗？**
能。`Cron4jPlugin` 本质是一组带 `start()/stop()` 生命周期的调度器封装，不绑定 aifei 运行时。在任意 Java 项目中 `new` 出来 → `addTask` → `start()`，并把 `stop()` 挂到容器的关闭钩子即可。

---

## 十一、选型建议

aifei-cron4j 基于 cron4j，采用 **Linux `crontab` 风格的 5 段 cron 表达式**（分 时 天 月 周）——与 Linux 保持一致，会 `crontab` 即可上手，学习成本低；其代价是最小调度粒度止步于**分钟**，不支持秒级触发。它面向“轻量、够用、配置驱动”的常见定时场景。如果你的需求超出这个范围，可考虑：

- **需要精确到秒**：cron4j 的表达式无能为力，建议改用支持秒级表达式的 [cron-utils](https://github.com/jmrozanec/cron-utils)（配合你选定的调度器）等方案。
- **已熟悉 Quartz**：[Quartz Scheduler](https://www.quartz-scheduler.org/) 本身就是功能完整的调度器（支持秒级 cron、持久化、集群等），直接使用即可，无需再封装为 aifei 插件。

一句话：分钟级、轻量定时 → aifei-cron4j；秒级或复杂调度能力 → cron-utils / Quartz。

License: [Apache License 2.0](LICENSE)
