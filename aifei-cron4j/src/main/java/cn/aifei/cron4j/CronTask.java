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

package cn.aifei.cron4j;

/**
 * 被 Cron4jPlugin 调度的任务：在 Runnable 基础上增加 stop() 回调，
 * Cron4jPlugin.stop() 时会回调它，用于释放连接、保存状态等资源清理。
 */
public interface CronTask extends Runnable {
	void stop();
}
