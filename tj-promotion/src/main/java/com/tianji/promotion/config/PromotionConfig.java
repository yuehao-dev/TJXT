package com.tianji.promotion.config;

import com.tianji.promotion.enums.ExchangeCodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class PromotionConfig {

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
}
