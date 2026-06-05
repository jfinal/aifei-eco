/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.db.core.AifeiDbPlugin;
import cn.aifei.db.dialect.MysqlDialect;
import cn.aifei.db.ext.DruidSupplier;
import cn.aifei.demo.common.arch.*;
import cn.aifei.demo.common.db.ModelSet;
import cn.aifei.json.JsonKit;
import cn.aifei.log.log4j.Log4jLogFactory;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.Prop;
import cn.aifei.util.PropKit;

/**
 * Aifei 配置中心
 */
public class AppConfig implements AifeiConfig<In, Out> {

    Prop p;

    /**
     * 偏好配置
     */
    public void config(Settings<In, Out> settings) {
        // 加载配置（命令行传递参数需放此处，例如：--aifei.profiles.active=pro）
        p = PropKit.use("app-config.txt");

        // 配置日志
        settings.setLogFactory(new Log4jLogFactory());

        // 配置 Server、Dispatcher、Handler
        settings.setServer(new UndertowServer(), new IoDispatcher())
                // .addHandler(new CorsHandler())       // 支持跨域时放开这一行
                .addHandler(new IoHandler(p.getBoolean("printAction")));

        // 配置对象转 json 的默认日期格式
        JsonKit.get().setWriteDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 路由配置
     */
    public void config(Routes routes) {
        routes.scan("cn.aifei.demo");
    }

    /**
     * 插件配置
     */
    public void config(Plugins plugins) {
        // 数据源
        DruidSupplier ds = new DruidSupplier(p.get("jdbcUrl"), p.get("user"), p.get("password"));

        // 数据库插件
        AifeiDbPlugin dbPlugin = new AifeiDbPlugin("main", ds);

        dbPlugin.setDialect(new MysqlDialect());    // 配置方言
        dbPlugin.addModelSet(new ModelSet());       // 添加生成的 Model 集合
        dbPlugin.setPrintSql(true);                 // 配置 SQL 打印
        dbPlugin.setFormatSql(false);               // 配置 SQL 格式化
        // dbPlugin.setPrintSqlToLog(true);         // 配置 SQL 打印到日志文件，便于找到慢 SQL 进行优化
        // dbPlugin.config(c -> c.setXxx(...));     // 通过 config 配置内部各组件

        // 添加插件
        plugins.add(dbPlugin);
    }
}

