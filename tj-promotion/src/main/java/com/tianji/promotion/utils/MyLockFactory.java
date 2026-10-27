package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.tianji.promotion.utils.MyLockType.*;

/**
 * 分布式锁工厂：根据 {@link MyLockType} 类型创建对应的 Redisson 锁对象。
 *
 * <p>内部维护了一张"锁类型 -> 创建函数"的映射表 {@code Map<MyLockType, Function<String, RLock>>}，
 * 每种锁类型对应一个使用 {@link RedissonClient} 生产 {@link RLock} 的工厂函数。
 * 这样把"锁类型的实例化逻辑"从切面中剥离出来，职责单一、便于扩展新的锁类型。</p>
 */
@Component

public class MyLockFactory {
    
    /**
     * 锁类型与创建函数的映射表：
     * key 是锁类型枚举，value 是根据锁名称 name 创建对应 RLock 的函数。
     */
    private final Map<MyLockType, Function<String, RLock>> lockHandlers;

    /**
     * 构造器：注入 RedissonClient，并初始化各锁类型对应的创建函数。
     *
     * @param redissonClient Redisson 客户端
     */
    public MyLockFactory(RedissonClient redissonClient) {
        this.lockHandlers = new EnumMap<MyLockType,Function<String,RLock>>(MyLockType.class);
        //可重入锁：普通 lock
        this.lockHandlers.put(RE_ENTRANT_LOCK,name -> redissonClient.getLock(name));
        //公平锁：保证按请求顺序获取
        this.lockHandlers.put(FAIL_LOCK,name -> redissonClient.getFairLock(name));
        //读锁：共享读锁
        this.lockHandlers.put(READ_LOCK,name -> redissonClient.getReadWriteLock(name).readLock());
        //写锁：独占写锁
        this.lockHandlers.put(WRITE_LOCK,name -> redissonClient.getReadWriteLock(name).writeLock());
    }

    /**
     * 根据指定的锁类型和锁名称，创建对应的锁对象。
     *
     * @param lockType 锁类型枚举
     * @param name     锁名称（Redis key）
     * @return 对应类型的 Redisson 锁对象
     */
    public RLock getLock(MyLockType lockType, String name) {
        //1.从映射表中取出该类型的创建函数
        Function<String, RLock> lockHandler = lockHandlers.get(lockType);
        //2.执行创建函数，传入锁名称得到锁对象
        RLock apply = lockHandler.apply(name);
        return apply;
    }
}