package com.tianji.learning.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DelayTaskTest {

    @Test
    void testDelayQueue() throws InterruptedException {
        //1.初始化延迟队列
        DelayQueue<DelayTask<String>> queue = new DelayQueue<>();
        log.info("初始化延迟队列...");
        //2.向队列中添加延迟执行的任务
        queue.add(new DelayTask<>("延迟任务3", Duration.ofSeconds(3)));
        queue.add(new DelayTask<>("延迟任务1", Duration.ofSeconds(1)));
        queue.add(new DelayTask<>("延迟任务2", Duration.ofSeconds(2)));
        //3.尝试执行任务
        log.info("尝试执行任务...");
        while (true) {
            //尝试从队列中获取任务
            DelayTask<String> take = queue.take();
            log.info("获取到任务: {}", take.getData());

        }
    }
}