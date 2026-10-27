package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 自定义分布式锁的 AOP 切面实现（针对 {@link MyLock} 注解）。
 *
 * <p>通过环绕通知 {@code @Around("@annotation(myLock)")} 拦截所有标注了 {@code @MyLock}
 * 的方法，在其前后自动完成：创建锁对象 -> 尝试获取锁 -> 执行业务 -> 释放锁。</p>
 *
 * <p>实现 {@code Ordered} 接口并指定 order，是为了控制切面的执行顺序：
 * 使其在事务切面（@Transactional）生效之前先拿到锁，保证"先加锁、再开启事务"。</p>
 *
 * <p>通过 {@code @Component} 标注，让该切面被 Spring 自动扫描注册为 Bean，
 * 无需再在配置类中手动 new 一个实例。</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MyLockAspect implements Ordered {

    /**
     * 锁工厂，用于按指定的锁类型和名称创建对应的 Redisson 锁对象（构造器注入）。
     */
    private final MyLockFactory myLockFactory;

    /**
     * 环绕通知核心方法：对标注了 {@code @MyLock} 的方法实现"获取锁-执行-释放锁"。
     *
     * @param pjp    连接点，可用来继续执行被拦截的目标方法
     * @param myLock 方法上的 {@code @MyLock} 注解对象，可读取 name / waitTime 等配置
     * @return 目标方法的返回值，原样透传
     * @throws Throwable 目标方法执行过程中的任何异常
     */
    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        //1.创建锁对象：委托锁工厂按注解指定的锁类型和锁名称生成对应的 Redisson 锁
        RLock lock = myLockFactory.getLock(myLock.lockType(), myLock.name());


        //2.尝试获取锁：委托注解配置的锁策略去获取，具体拿不到锁时的处理由策略决定
        boolean isLock = myLock.lockStrategy().tryLock(lock,myLock);
        //3.判断是否成功
        if (!isLock) {
            //3.1失败：策略选择静默跳过（未抛异常时此处返回 null），则不执行业务直接返回
            return null;
        }
        try {
            //3.2成功，继续执行真正的业务方法（拿到锁后才执行，确保临界区串行）
            return pjp.proceed();

        } finally {
            //4.释放锁：无论业务成功或抛异常，都保证锁被正确释放，避免死锁
            lock.unlock();
        }

    }

    /**
     * 返回切面的优先级。数值越小优先级越高，这里返回 0，
     * 确保切面在事务相关切面之前执行（先拿锁、再开事务）。
     *
     * @return 优先级数值
     */
    @Override
    public int getOrder() {
        return 0;
    }
}