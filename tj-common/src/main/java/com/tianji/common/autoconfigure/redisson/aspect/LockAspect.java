package com.tianji.common.autoconfigure.redisson.aspect;

import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分布式锁的 AOP 切面实现（针对 {@link Lock} 注解的增强版）。
 *
 * <p>与自定义 {@code MyLockAspect} 的区别在于功能更完善：
 * ① 支持 SPEL 解析动态锁名称；② 通过 {@link LockStrategy} 支持 5 种获取锁策略；
 * ③ 通过 {@link LockType} 支持多种锁类型；④ 支持自动释放或到期释放两种解锁方式。</p>
 *
 * <p>核心流程：解析锁名 -> 创建锁 -> 按策略尝试获取锁（失败则按策略结束/抛异常）
 * -&gt; 执行目标方法 -> （按需自动）释放锁，全部在环绕通知里统一完成。</p>
 */
@Aspect
public class LockAspect {

    /**
     * Redisson 客户端，用于创建锁对象（构造器注入）。
     */
    private final RedissonClient redissonClient;

    public LockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 环绕通知：拦截所有标注 {@code @Lock} 的方法，统一完成加锁、执行、解锁。
     *
     * @param pjp       连接点，用于继续执行被拦截的方法
     * @param properties 方法上的 {@code @Lock} 注解，读取锁名、策略、类型等配置
     * @return 目标方法的返回值
     * @throws Throwable 目标方法过程中的异常
     */
    //通过环绕加锁，方法执行前加锁，方法执行后根据注解使用解锁
    @Around("@annotation(properties)")
    public Object handleLock(ProceedingJoinPoint pjp, Lock properties) throws Throwable {
        // 0.校验配置：若选择"不自动释放锁"，则必须显式指定 leaseTime，否则没人/没时间释放锁
        if (!properties.autoUnlock() && properties.leaseTime() <= 0) {
            // 不手动释放锁时，必须指定leaseTime时间
            throw new BizIllegalException("leaseTime不能为空");
        }
        // 1.基于SPEL表达式解析锁的 name：例如把 #{userId} 替换成真实参数值
        String name = getLockName(properties.name(), pjp);
        // 2.得到锁对象：按锁类型（可重入/公平/读写）创建对应的 RLock
        RLock rLock = properties.lockType().getLock(redissonClient, name);
        // 3.尝试获取锁：按配置的策略获取，失败时该策略决定是返回 false 还是抛异常
        boolean success = properties.lockStrategy().tryLock(rLock, properties);
        if (!success) {
            // 获取锁失败，直接结束，不执行目标方法
            return null;
        }
        try {
            // 4.执行被代理方法：拿到锁之后才真正执行业务临界区
            return pjp.proceed();
        } finally {
            // 5.释放锁：仅在配置为自动解锁时释放；否则交由 leaseTime 到期自动释放
            if (properties.autoUnlock()) {
                rLock.unlock();
            }
        }
    }

    /**
     * SPEL 表达式的正则规则：匹配 #{...} 形式的片段
     */
    private static final Pattern pattern = Pattern.compile("\\#\\{([^\\}]*)\\}");
    /**
     * 方法参数解析器：用于通过反射获得方法形参的真实名称（如 userId），
     * 供 SPEL 表达式在解析变量时使用。Spring 编译目标默认不保留参数名，需借助该类探测。
     */
    private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析锁名称：若名称中包含 #{...} 形式的 SPEL 表达式，则计算结果并替换到名称中。
     *
     * @param name 原始锁名称
     * @param pjp 切入点
     * @return 解析后的锁名称
     */
    private String getLockName(String name, ProceedingJoinPoint pjp) {
        // 1.判断是否存在spel表达式（名称为空或不含 # 则无需解析）
        if (StringUtils.isBlank(name) || !name.contains("#")) {
            // 不存在，直接返回
            return name;
        }
        // 2.构建context：基于目标方法及其参数构建 SpEL 求值上下文
        EvaluationContext context = new MethodBasedEvaluationContext(
                TypedValue.NULL, resolveMethod(pjp), pjp.getArgs(), parameterNameDiscoverer);
        // 3.构建解析器：用于将 SpEL 表达式解析并求值
        ExpressionParser parser = new SpelExpressionParser();
        // 3.循环处理：把名称里每个 #{...} 片段都替换成对应的参数值
        Matcher matcher = pattern.matcher(name);
        while (matcher.find()) {
            // 2.1.获取表达式：如 #{userId} 的完整片段
            String tmp = matcher.group();
            // 2.2.尝试解析：取出括号内的表达式主体进行求值
            Expression expression = parser.parseExpression("#" + matcher.group(1));
            Object value = expression.getValue(context);
            name = name.replace(tmp, ObjectUtils.nullSafeToString(value));
        }
        return name;
    }

    /**
     * 解析出连接点对应的目标方法（可能需要在父类中查找）。
     *
     * @param pjp 切入点
     * @return 目标方法
     */
    private Method resolveMethod(ProceedingJoinPoint pjp) {
        // 1.获取方法签名
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        // 2.获取字节码
        Class<?> clazz = pjp.getTarget().getClass();
        // 3.方法名称
        String name = signature.getName();
        // 4.方法参数列表
        Class<?>[] parameterTypes = signature.getMethod().getParameterTypes();
        return tryGetDeclaredMethod(clazz, name, parameterTypes);
    }

    /**
     * 尝试通过反射获取指定方法（在本类找不到时向父类递归查找）。
     *
     * @param clazz 目标类
     * @param name 方法名
     * @param parameterTypes 方法参数类型列表
     * @return 找到的方法；找不到则返回 null
     */
    private Method tryGetDeclaredMethod(Class<?> clazz, String name, Class<?> ... parameterTypes){
        try {
            // 5.反射获取方法
            return clazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                // 尝试从父类寻找
                return tryGetDeclaredMethod(superClass, name, parameterTypes);
            }
        }
        return null;
    }
}