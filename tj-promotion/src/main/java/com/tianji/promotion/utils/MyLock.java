package com.tianji.promotion.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 自定义分布式锁注解（本项目教学用实现）
 *
 * <p>用法：把该注解标注在需要加锁的方法上即可。方法被调用时，会由
 * {@code MyLockAspect}（AOP 切面）自动完成"获取锁 -> 执行业务 -> 释放锁"的整个过程，
 * 无需再在方法体里手动编写加锁/解锁代码。</p>
 *
 * <p>限制：该注解只给方法级使用，且只能标注在能被 Spring 代理拦截的方法上。
 * 若在同类内部直接调用（this.xxx()），不会经过代理，注解将不会生效。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyLock {

    /**
     * 锁的名称（Redis 中锁的 key）。
     *
     * <p>建议按业务粒度设计名称，例如 "lock:coupon" 表示所有优惠券共锁一把，
     * 粒度较粗、并发串行化；若想细粒度，可在方法参数中加入业务标识（如 userId），
     * 但当前自定义实现不支持 SPEL 动态解析，需要在上层拼接 key。</p>
     */
    String name();

    /**
     * 获取锁的等待超时时间，默认 1 秒。
     *
     * <p>在这段时间内会不断尝试获取锁；超过该时长仍未拿到锁则放弃（最终表现为
     * MyLockAspect 中抛出的"请求太频繁"异常）。</p>
     */
    long waitTime() default 1;

    /**
     * 锁的自动过期时间，默认 -1。
     *
     * <p>当前自定义切面未使用该字段（仅作预留扩展）：若为 -1 表示不设置过期时间，
     * 等待 Redisson 看门狗（watchdog）机制自动续期；若大于 0 则表示锁的最长持有时间。</p>
     */
    long leaseTime() default -1;

    /**
     * {@link #waitTime()} 与 {@link #leaseTime()} 的时间单位，默认秒。
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 锁的类型，默认可重入锁 {@link MyLockType#RE_ENTRANT_LOCK}。
     *
     * <p>由 {@link MyLockFactory} 依据该类型创建对应的 Redisson 锁对象，
     * 可选：可重入锁、公平锁、读锁、写锁。</p>
     */
    MyLockType lockType() default MyLockType.RE_ENTRANT_LOCK;

    /**
     * 锁的获取策略，默认 {@link MyLockStrategy#FAIL_AFTER_RETRY_TIMEOUT}。
     *
     * <p>决定抢不到锁时的行为：立即失败、静默跳过、重试到超时、还是不停重试，
     * 由切面 {@code MyLockAspect} 委托 {@link MyLockStrategy} 执行。</p>
     */
    MyLockStrategy lockStrategy() default MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT;
}