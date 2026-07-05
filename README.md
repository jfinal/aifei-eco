# aifei-eco

`aifei-eco` 是 Aifei 项目的生态仓库。它包含独立演示、文档以及其他生态资源。

## 模块

- `aifei-doc`: Aifei 的文档。
- `aifei-demo`: Aifei 的独立 Maven 演示项目。
- `aifei-cache`: Aifei 的缓存插件，支持 Caffeine 与 Redis 实现。单体应用使用 Caffeine 实现，集群应用无缝切换到 Redis。
- `aifei-redis`: JFinal 项目 redis plugin 移植到 Aifei。建议优先使用 aifei-cache。
- `aifei-cron4j`: JFinal 项目 cron4j plugin 移植到 Aifei。使用 5 段式 Linux cron 表达式驱动任务。
