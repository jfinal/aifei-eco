# aifei-redis

从 JFinal 项目移植过来的 redis 插件，绝大多数用法与 JFinal 保持一致。

分布式锁 API 由 withLock 更名为 tryRunWithLock：

```
    Redis.use().tryRunWithLock("lock:stock", 120, 3.5, () -> {
        // 业务代码
    });
```
