package com.tianji.common.autoconfigure.redisson.enums;

import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import org.redisson.api.RLock;

/**
 * 分布式锁的获取策略枚举。
 *
 * <p>每种策略决定了"获取不到锁时如何处理"：
 * 要么直接放弃（不执行业务），要么继续重试一段时间，且在最终失败时选择静默返回还是抛异常。
 * 通过在 {@link Lock#lockStrategy()} 中指定具体的某一种来选用。</p>
 */
public enum LockStrategy {
    /**
     * 不重试，立即尝试一次拿锁，拿不到就返回 false（静默跳过，不执行业务）。
     */
    SKIP_FAST() {
        @Override
        public boolean tryLock(RLock lock, Lock properties) throws InterruptedException {
            // waitTime=0：不等待，立即尝试；拿不到直接返回 false
            return lock.tryLock(0, properties.leaseTime(), properties.timeUnit());
        }
    },
    /**
     * 不重试，立即尝试一次拿锁，拿不到直接抛异常（快速失败）。
     */
    FAIL_FAST() {
        @Override
        public boolean tryLock(RLock lock, Lock properties) throws InterruptedException {
            boolean success = lock.tryLock(0, properties.leaseTime(), properties.timeUnit());
            if (!success) {
                // 拿不到锁，立即抛异常对外提示
                throw new RuntimeException("请求太频繁");
            }
            return true;
        }
    },
    /**
     * 一直重试，直到超过 waitTime 超时后，静默返回 false（不执行业务）。
     */
    SKIP_AFTER_RETRY_TIMEOUT() {
        @Override
        public boolean tryLock(RLock lock, Lock properties) throws InterruptedException {
            // 持续等待 waitTime 时长去抢锁，超时仍未拿到则返回 false
            return lock.tryLock(properties.waitTime(), properties.leaseTime(), properties.timeUnit());
        }
    },
    /**
     * 一直重试，超过 waitTime 超时后仍未拿到锁则抛出异常（默认策略）。
     */
    FAIL_AFTER_RETRY_TIMEOUT() {
        @Override
        public boolean tryLock(RLock lock, Lock properties) throws InterruptedException {
            boolean success = lock.tryLock(properties.waitTime(), properties.leaseTime(), properties.timeUnit());
            if (!success) {
                // 重试到超时仍未拿到锁，抛异常提示
                throw new RuntimeException("请求超时");
            }
            return true;
        }
    },
    /**
     * 不停重试，直到成功拿到锁为止（不超时）。
     */
    KEEP_RETRY() {
        @Override
        public boolean tryLock(RLock lock, Lock properties) throws InterruptedException {
            // lock() 会一直阻塞等待，直到拿到锁才返回
            lock.lock(properties.leaseTime(), properties.timeUnit());
            return true;
        }
    },
    ;

    /**
     * 尝试获取锁的抽象方法，由各个策略实现不同的抢锁逻辑。
     *
     * @param lock      锁对象
     * @param properties 注解配置（waitTime / leaseTime / timeUnit）
     * @return 是否成功获取到锁
     * @throws InterruptedException 线程中断
     */
    public abstract boolean tryLock(RLock lock, Lock properties) throws InterruptedException;
}