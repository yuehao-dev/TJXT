package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import org.redisson.api.RLock;

/**
 * 自定义分布式锁的获取策略枚举（对应 {@link MyLock#lockStrategy()}）。
 *
 * <p>每种策略决定"抢不到锁时如何处理"：是立即放弃、抛异常，还是重试一段时间后再决定。
 * 由 {@link MyLockAspect} 在获取锁时委托该枚举实现具体的加锁动作。</p>
 *
 * <p>命名约定：SKIP_ 前缀表示拿不到锁时静默跳过（返回 false / 走 ret转 null）；
 * FAIL_ 前缀表示拿不到锁时直接抛出异常；KEEP_ 表示不断重试。</p>
 */
public enum MyLockStrategy {
    /**
     * 不重试：立即尝试拿一次锁，拿不到就返回 false（静默跳过，不执行业务）。
     */
    SKIP_FAST() {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            //waitTime=0：不等待，立即尝试；拿不到直接返回 false
            boolean b = lock.tryLock(0, prop.leaseTime(), prop.unit());
            return b;
        }
    },
    /**
     * 不重试：立即尝试拿一次锁，拿不到立即抛出异常（快速失败）。
     */
    FAIL_FAST() {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(0, prop.leaseTime(), prop.unit());
            if (!isLock) {
                //拿不到锁，立即抛异常对外提示
                throw new BizIllegalException("请求频繁，请稍后重试");
            }
            return true;
        }
    },

    /**
     * 不停重试，直到成功拿到锁为止（不设超时，会一直阻塞等待）。
     */
    KEEP_TRYING() {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            //lock() 会一直阻塞，直到拿到锁才返回
            lock.lock(prop.leaseTime(), prop.unit());
            return true;
        }
    },

    /**
     * 一直重试，直到超过 {@code waitTime} 超时后，静默返回 false（不执行业务）。
     */
    SKIP_AFTER_RETRY_TIMEOUT() {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            //持续等待 waitTime 时长抢锁，超时仍未拿到则返回 false
            boolean b = lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
            return b;
        }
    },

    /**
     * 一直重试，超过 {@code waitTime} 超时后仍未拿到锁则抛出异常（默认策略）。
     */
    FAIL_AFTER_RETRY_TIMEOUT() {
        @Override
        public boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.unit());
            if (!isLock) {
                //重试到超时仍未拿到锁，抛异常提示
                throw new BizIllegalException("请求频繁，请稍后重试");
            }
            return true;
        }
    },
    ;

    /**
     * 尝试获取锁的抽象方法，由各策略实现不同的抢锁逻辑。
     *
     * @param lock 锁对象
     * @param prop 注解配置（waitTime / leaseTime / unit）
     * @return 是否成功获取到锁
     * @throws InterruptedException 线程中断
     */
    public abstract boolean tryLock(RLock lock, MyLock prop) throws InterruptedException;

}