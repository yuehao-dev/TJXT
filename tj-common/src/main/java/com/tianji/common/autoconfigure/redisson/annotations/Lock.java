package com.tianji.common.autoconfigure.redisson.annotations;

import com.tianji.common.autoconfigure.redisson.enums.LockStrategy;
import com.tianji.common.autoconfigure.redisson.enums.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解（通用模块提供的增强版实现）
 *
 * <p>比自定义 {@code @MyLock} 功能更全：支持 SPEL 动态拼接锁名称（如按 userId
 * 细分锁粒度）、支持配置锁类型（可重入/公平/读写）、支持 5 种获取锁策略、支持
 * 自动释放或到期自动释放（watchdog）。标注该注解的方法会被
 * {@code LockAspect} 环绕拦截，自动完成加锁与解锁。</p>
 *
 * <p>锁名称中使用 {@code #{} } 包裹 SPEL 表达式时，<b>必须把对应参数声明到方法
 * 形参列表</b>中，切面才能根据方法参数解析出变量值。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Lock {

    /**
     * 加锁 key 的表达式，支持 SPEL 表达式。
     *
     * <p>例如 {@code "lock:coupon:uid:#{userId}"}，其中 #{userId} 会被解析成方法
     * 参数 userId 的实际值，从而实现"每个用户各自一把锁"的细粒度控制。</p>
     */
    String name();

    /**
     * 阻塞超时时长（等待获取锁的最长时间），单位为 {@link #timeUnit()}。
     * 不指定 waitTime 则按照 Redisson 默认时长等待。
     */
    long waitTime() default 1;

    /**
     * 锁自动释放时长，默认是 -1。
     * 为 -1 时配合 Redisson 看门狗（watchdog）机制，实际约 30 秒后自动续期直至方法结束。
     */
    long leaseTime() default -1;

    /**
     * {@link #waitTime()} 与 {@link #leaseTime()} 的时间单位，默认为秒。
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 是否在方法结束后自动释放锁，默认 true。
     * 若设为 false，则方法结束后不释放，而是等待 {@link #leaseTime()} 之后自动释放。
     */
    boolean autoUnlock() default true;

    /**
     * 锁的类型，包括：可重入锁、公平锁、读锁、写锁，默认可重入锁。
     */
    LockType lockType() default LockType.DEFAULT;

    /**
     * 锁策略，包括 5 种。默认策略 {code FAIL_AFTER_RETRY_TIMEOUT}：
     * 不断尝试获取锁直到超时，超时后抛出异常。
     */
    LockStrategy lockStrategy() default LockStrategy.FAIL_AFTER_RETRY_TIMEOUT;
}