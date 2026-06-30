# aifei-redis

从 JFinal 项目移植过来的 redis 插件，绝大多数用法与 JFinal 保持一致。

分布式锁 API 由 withLock 更名为 tryExecuteWithLock：

```
    Redis.use().tryExecuteWithLock("lock:stock", 120, () -> {
        // 业务代码
    });
```

注意：缓存尽可能使用全新设计的 aifei-cache 而非 aifei-redis。前者面向应用场景而设计，后者仅针对 jedis 已有 API 进行薄封装。
