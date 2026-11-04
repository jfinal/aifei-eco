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

package cn.aifei.demo.common.arch;

import cn.aifei.aop.Interceptor;
import cn.aifei.core.Aifei;
import cn.aifei.router.Action;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ActionPrinter
 */
public class ActionPrinter {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String title = "\nAifei-" + Aifei.getVersion() + " action info (";

    public void print(Action action, In in) {
        StringBuilder sb = new StringBuilder(512);

        // 打印 title、path、action
        sb.append(title).append(LocalDateTime.now().format(formatter)).append(") --------------------------------");
        sb.append("\nPath        : ").append(action.getActionPath());
        sb.append("\nAction      : ").append(action.getBriefInfo());

        // 打印拦截器
        Interceptor[] inters = action.getInterceptors();
        if (inters.length > 0) {
            sb.append("\nInterceptor : ");
            for (int i = 0; i < inters.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(inters[i].getClass().getSimpleName());
            }
        }

        // 打印路径参数
        if (in.pathParaStr != null && !in.pathParaStr.isEmpty()) {
            sb.append("\nPathPara    : ").append(in.pathParaStr);
        }

        // 打印参数
        JSONObject data = in.getData(); // 触发参数解析
        JSONArray arrayData = in.getArrayData();
        sb.append("\nParameter   : ");
        if (arrayData == null) {
            sb.append(data.toJSONString());
        } else {
            sb.append(arrayData.toJSONString());
        }

        System.out.println(sb);
    }
}



