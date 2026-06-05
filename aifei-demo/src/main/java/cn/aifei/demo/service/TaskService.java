/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.service;

import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.aifei.demo.common.db.model.Task;
import java.util.Date;
import java.util.List;

/**
 * TaskService
 *
 * <pre>
 *  Just Service 开发范式：
 *   1: 框架搭好后只需专注写 Service
 *   2: 极小化 Token 消耗、极大化 Attention 浓度，稳定生成高品质代码
 * </pre>
 */
@Path("/")
public class TaskService {

    public String index() {
        return "task/index.html";
    }

    /**
     * 订阅 VIP 获取极简查询、排序、分页功能，订阅入口：https://aifei.cn
     */
    public List<Task> list() {
        return Task.sql("select * from task order by created desc").find();
    }

    public Out insert(Task task) {
        task.created(new Date()).insert();
        return Out.ok("插入成功");
    }

    public Out delete(int id) {
        Task.deleteById(id);
        return Out.ok("删除成功");
    }

    public Out update(Task task) {
        task.update();
        return Out.ok("更新成功");
    }
}


