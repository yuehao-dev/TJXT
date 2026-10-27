package com.tianji.common.autoconfigure.redisson.enums;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 分布式锁的类型枚举。
 *
 * <p>用以决定使用 Redisson 中哪一种锁实现：可重入锁、公平锁、读写锁。
 * 通过 {@link Lock#lockType()} 指定，默认可重入锁。</p>
 */
public enum LockType {
    /**
     * 可重入锁（默认可重入锁）。
     * 同一个线程可重复获取同一把锁，适合绝大多数普通互斥场景。
     */
    DEFAULT(){
        @Override
        public RLock getLock(RedissonClient redissonClient, String name) {
            return redissonClient.getLock(name);
        }
    },
    /**
     * 公平锁：保证线程按照请求锁的顺序（FIFO）依次获得锁，
     * 避免饥饿，但吞吐略低于普通可重入锁。
     */
    FAIR_LOCK(){
        @Override
        public RLock getLock(RedissonClient redissonClient, String name) {
            return redissonClient.getFairLock(name);
        }
    },
    /**
     * 读锁：多个线程可同时持有读锁（共享读），适合读多写少、读不影响彼此的场景。
     */
    READ_LOCK(){
        @Override
        public RLock getLock(RedissonClient redissonClient, String name) {
            return redissonClient.getReadWriteLock(name).readLock();
        }
    },
    /**
     * 写锁：独占锁，与读锁互斥、与写锁互斥，适合写操作必须独占资源的场景。
     */
    WRITE_LOCK(){
        @Override
        public RLock getLock(RedissonClient redissonClient, String name) {
            return redissonClient.getReadWriteLock(name).writeLock();
        }
    },
    ;

    /**
     * 根据锁类型从 RedissonClient 中创建对应的锁对象。
     *
     * @param redissonClient Redisson 客户端
     * @param name 锁名称（Redis key）
     * @return 对应类型的锁对象
     */
    public abstract RLock getLock(RedissonClient redissonClient, String name);
}