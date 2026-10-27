package com.tianji.promotion.utils;

import com.tianji.common.utils.BooleanUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@RequiredArgsConstructor
public class RedisLock {

    private String key;

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(long leaseTime, TimeUnit unit) {
        //1.获取线程的名称
        String value = Thread.currentThread().getName();
        //2.获取锁
        boolean success = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, leaseTime, unit));
        //3.返回
        return BooleanUtils.isTrue(success);
    }

    public void unlock(){
        redisTemplate.delete(key);
    }
}
