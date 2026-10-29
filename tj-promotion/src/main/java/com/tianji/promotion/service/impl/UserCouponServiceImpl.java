package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constans.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockType;
import com.tianji.promotion.utils.RedisLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-25
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    private final IExchangeCodeService codeService;

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    @Override
    @Lock(name = "lock:coupon:uid:{couponId}")
    public void receiveCoupon(Long couponId) {

        //1.查询优惠卷
        Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        //2.校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime())
                || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        //3.校验库存
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        Long userId = UserContext.getUser();
        /*//4.1创建锁对象
        RLock lock = redissonClient.getLock(key);
        //4.2尝试获取锁
        boolean isLock = lock.tryLock();
        //4.3判断是否获取成功
        if (!isLock){
            throw new BizIllegalException("请求频繁，请稍后再试");
        }
        try {
            //4.4获取成功,执行业务
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreateUserCoupon(userId, coupon);
        } finally {
            //4.5释放锁对象
            lock.unlock();
        }*/
        //4.校验每个人限领数量
        //4.1查询领取数量
        String key = PromotionConstants.USER_COUPON_CACHE_MAP_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);

        //4.2判断校验限领数量
        if (count > coupon.getUserLimit()) {
            throw new BadRequestException("每人限领数量已到上限");
        }
        //5.扣减优惠卷库存
        redisTemplate.opsForHash().increment(key, couponId.toString(), -1);

        //6.发送MQ消息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE,MqConstants.Key.COUPON_RECEIVE,uc);



    }

    private Coupon queryCouponByCache(Long couponId) {
        //1.准备key
        String key = PromotionConstants.COUPON_CACHE_MAP_KEY_PREFIX + couponId;
        //2.查询
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            throw new BizIllegalException("优惠券不存在");
        }

        //3.数据的反序列化
        return BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());

    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {

        //1.查询优惠券
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }

        Long userId = uc.getUserId();
        //2.更新优惠卷的已经发放数量+1
        int r = couponMapper.incrIssueNum(uc.getCouponId());
        if (r == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }

        //3.新增一个用户优惠券记录
        saveUserCoupon(userId, coupon);

    }


    private void saveUserCoupon(Long userId, Coupon coupon) {
        //1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        //2.有效期
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();

        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        //3.保存
        save(uc);
    }


    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        //1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        //2.校验是否已经兑换 SETBIT KEY INDEX 1
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if (exchanged) {
            throw new BizIllegalException("兑换码已兑换");
        }

        try {
            //3.查询兑换码
            ExchangeCode exchangeCode = codeService.getById(serialNum);
            if (exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在");
            }
            //4.是否过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(exchangeCode.getExpiredTime())) {
                throw new BizIllegalException("兑换码已过期");
            }
            //4.1查询优惠卷
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            //4.2获取用户
            Long userId = UserContext.getUser();
//            checkAndCreateUserCoupon(userId, coupon);

            //5.更新兑换码状态  SETBIT KEY INDEX 1
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, exchangeCode.getId())
                    .update();

        } catch (Exception e) {
            //重置兑换的标记0
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }

    }

}