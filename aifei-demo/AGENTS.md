# Repository Guidelines

## 项目定位
本仓库是基于 `aifei` 框架的独立 Maven demo，核心目标是演示任务清单的增删改查。

`aifei` 采用 Just Service 开发范式：框架完成路由、参数绑定、JSON 转换等所有操作，后端业务只需写 Service。

## 项目结构
- `src/main/java/cn/aifei/demo/service/TaskService.java`：任务清单 CRUD 示例。
- `src/main/java/cn/aifei/demo/common/db/`：生成器管理；`base/` 与 `ModelSet.java` 每次生成会覆盖，禁止手工修改，其它文件已存在则不覆盖。
- `src/main/webapp/view/task/index.html`：任务清单单页面入口。
- `src/main/webapp/assets/js/aifei-demo.js`、`assets/css/aifei-demo.css`：页面交互与样式。
- `必读/aifei_demo.sql`：数据库表结构与示例数据。

## 构建、运行与配置
- `mvn clean package`：编译并生成 `target/aifei-demo-release` 和 zip 包。
- `mvn test`：执行 Maven 测试阶段；当前未配置测试目录，新增测试时放入 `src/test/java`。
- `./aifei-demo.sh start|stop|restart`：在打包产物中启动、停止或重启服务；Windows 使用 `aifei-demo.bat`。

本地运行前创建 `aifei_demo` MySQL 数据库，导入 `必读/aifei_demo.sql`，并按本机环境修改 `src/main/resources/app-config-dev.txt`。

## Just Service 约定
以后端演示为主时，保持 `TaskService` 这类 Service 方法简洁直接。方法参数由框架从 query、form 或 JSON body 绑定，返回普通对象会自动输出 JSON，返回 `Out` 可携带状态与消息。不要为 demo 引入额外控制器、DTO 层或复杂抽象。

## 前端约定
任务清单页面应保持纯静态，通过 AJAX 与 `TaskService` 进行 JSON 交互。HTML 放结构，CSS 放展示，JavaScript 负责加载列表、提交新增、更新完成状态和删除任务。页面代码应尽量原生、短小，避免引入构建工具或前端框架。

## 代码风格与提交
Java 使用 4 空格缩进、UTF-8、Java 8 语法；类名使用 `PascalCase`，方法和字段使用 `camelCase`。保留现有中文注释和用户提示风格。提交信息保持简短，建议中文动宾短句，例如 `实现任务清单页面`、`修复任务列表查询`。PR 需说明变更点、验证命令，以及涉及的配置或数据库变化。
