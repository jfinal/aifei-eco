/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo;

import cn.aifei.core.Aifei;
import cn.aifei.demo.common.AppConfig;

/**
 * 启动入口
 */
public class AifeiDemo {
    public static void main(String[] args) {
        Aifei.start(new AppConfig(), args);
    }
}

