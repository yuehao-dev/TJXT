package com.tianji.promotion.utils;

/**
 * 自定义分布式锁的类型枚举（对应 {@link MyLock#lockType()}）。
 *
 * <p>用于决定使用 Redisson 中的哪一种锁实现：可重入锁、公平锁、读写锁，
 * 由 {@link MyLockFactory} 根据该枚举从 {@link org.redisson.api.RedissonClient}
 * 创建对应的 {@link org.redisson.api.RLock} 锁对象。</p>
 */
public enum MyLockType {
    /**
     * 可重入锁：同一个线程可重复获取同一把锁，适用于绝大多数普通互斥场景。
     */
    RE_ENTRANT_LOCK,
    /**
     * 公平锁：保证线程按照请求锁的顺序（FIFO）依次获得锁，避免饥饿，吞吐略低。
     */
    FAIL_LOCK,
    /**
     * 读锁：多个线程可同时持有（共享读），适合读多写少、读之间互不影响的场景。
     */
    READ_LOCK,
    /**
     * 写锁：独占锁，与读锁、写锁都互斥，适合写操作必须独占资源的场景。
     */
    WRITE_LOCK,;

}