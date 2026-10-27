package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-24
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateCode(Coupon coupon);

    boolean updateExchangeMark(long serialNum, boolean mark);
}
