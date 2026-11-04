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

import cn.aifei.core.Handler;
import cn.aifei.log.Log;
import cn.aifei.server.Dispatcher;
import io.undertow.server.HttpServerExchange;

/**
 * IoDispatcher 承接 Server 请求，并将请求参数转换为 Input、Output 派发给 Aifei Handler
 */
public class IoDispatcher implements Dispatcher<HttpServerExchange, Void, In, Out> {

    static final Log log = Log.get(IoDispatcher.class);

    Handler<In, Out> handler;

    @Override
    public void init(Handler<In, Out> handler) {
        this.handler = handler;
    }

    @Override
    public void dispatch(HttpServerExchange exchange, Void v) {
        try {
            handler.handle(exchange.getRequestPath(), new In(exchange), null);
        } catch(Throwable e) {
            log.error(e.getMessage(), e);
            exchange.endExchange();
        }
    }
}

