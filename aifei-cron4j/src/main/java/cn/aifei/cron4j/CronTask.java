/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.cron4j;

/**
 * 被 Cron4jPlugin 调度的任务：在 Runnable 基础上增加 stop() 回调，
 * Cron4jPlugin.stop() 时会回调它，用于释放连接、保存状态等资源清理。
 */
public interface CronTask extends Runnable {
	void stop();
}
