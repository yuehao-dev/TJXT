package com.tianji.learning.utils;

import jodd.time.TimeUtil;
import lombok.Data;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
public class DelayTask<D> implements Delayed {

    //任务的截止时间，单位为纳秒
    private long deadlineNanos;

    private D data;

    public DelayTask(D data, Duration delayTime) {
        //这个的具体步骤是：
        //1. 计算任务的截止时间，单位为纳秒
        //2. 将任务的截止时间赋值给deadlineNanos
        this.deadlineNanos = System.nanoTime() + delayTime.toNanos();
        this.data = data;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        //转换时间的具体步骤是：
        //1. 计算任务的延迟时间，单位为纳秒
        //2. 转换为指定的时间单位
        //3. 返回转换后的结果
        //4. 如果任务的延迟时间小于等于0，返回0
        return unit.convert(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        //该方法的具体步骤是：
        //1. 计算当前任务的延迟时间，单位为纳秒
        //2. 计算o任务的延迟时间，单位为纳秒
        //3. 对比两个任务的延迟时间，返回对比结果
        //4. 如果两个任务的延迟时间相等，返回0
        long l = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
        if (l > 0){
            return 1;
        } else if (l < 0) {
            return -1;
        }else {
            return 0;
        }
    }
}
