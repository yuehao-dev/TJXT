package com.tianji.promotion.config;

import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.utils.MyLockAspect;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class PromotionConfig {

    /**
     * 兑换码生成的线程池对象。
     *
     * <p>用于异步批量生成兑换码，配置了核心线程、最大线程、队列长度、
     * 线程名前缀以及拒绝策略（CallerRunsPolicy：队列满时由调用线程自己执行）。</p>
     *
     * @return 兑换码处理用的线程池
     */
    @Bean
    public Executor generateExchangeCodeExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //1.核心线程池大小
        executor.setCorePoolSize(2);
        //2.最大线程池大小
        executor.setMaxPoolSize(4);
        //3.队列大小
        executor.setQueueCapacity(100);
        //4.线程名称前缀
        executor.setThreadNamePrefix("exchange-code-handler-");
        //5.拒绝策略  调用者运行策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

   /* *//**
     * 注册自定义分布式锁切面 {@link MyLockAspect} 为 Spring Bean，
     * 使其能够被 AOP 扫描并生效，从而拦截所有标注了 {@code @MyLock} 的方法。
     *
     * @param redissonClient 由外部注入的 Redisson 客户端，用于切面内创建锁
     * @return 自定义锁切面实例
     *//*
    @Bean
    public MyLockAspect myLockAspect(RedissonClient redissonClient){
        return new MyLockAspect(redissonClient);
    }*/
}