/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.aifei.demo.common;

import cn.aifei.db.dialect.MysqlDialect;
import cn.aifei.db.ext.DruidSupplier;
import cn.aifei.util.Prop;
import cn.aifei.util.PropKit;
import javax.sql.DataSource;

/**
 * aifei db 生成器
 */
public class Generator {

    /**
     * 移植到其它项目，一般仅需修改 basePackage 变量，其它变量基于该变量自动调整。
     */
    public static void main(String[] args) {

        // model 生成的基础包名
        String basePackage = "cn.aifei.demo.common.db";

        // model 生成的基础路径
        String basePath = System.getProperty("user.dir") + "/src/main/java/" + basePackage.replace('.', '/');

        new cn.aifei.db.generator.Generator(new MysqlDialect(), getDataSource(), basePackage, basePath)
                .configMetaReader(mr -> {
                    // 是否读取视图 view（读取后会生成 view 对应的 model）
                    // mr.setReadView(true);

                    // 黑名单跳过指定 table
                    // mr.addBlacklist("temp");

                    // 白名单生成指定 table
                    // mr.addWhitelist("白名单", "可实现仅生成指定 table");
                })
                .configBaseModelGenerator(bmg -> {
                    // 是否生成 short setter
                    // bmg.setGenerateShortSetter(false);
                })
                .generate();
    }

    static DataSource getDataSource() {
        Prop p = PropKit.useFirstFound("app-config-mac.txt", "app-config-dev.txt");
        DruidSupplier druidSupplier = new DruidSupplier(p.get("jdbcUrl"), p.get("user"), p.get("password"));
        druidSupplier.start();
        return druidSupplier.get();
    }
}


