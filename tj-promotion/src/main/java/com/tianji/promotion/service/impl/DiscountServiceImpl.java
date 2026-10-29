package com.tianji.promotion.service.impl;

import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import io.swagger.v3.oas.models.security.Scopes;
import javassist.runtime.DotClass;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {

    private final UserCouponMapper userCouponMapper;

    private final ICouponScopeService couponScopeService;

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {
        //1.查询我的所有可用的优惠卷
        List<Coupon> coupons = userCouponMapper.queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return List.of();
        }
        //2.初筛
        //2.1计算订单总价
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        //2.2筛选可用卷
        List<Coupon> availableCoupons = coupons.stream().filter(c -> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalAmount, c)).collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) {
            return List.of();
        }
        //3.排列组合素有方案
        //3.1细筛 找出每一个优惠卷的可用的课程,判断课程总价是否达到优惠卷的使用金额
        Map<Coupon, List<OrderCourseDTO>> availableCouponmap = findAvailableCoupon(availableCoupons, orderCourses);

        if (CollUtils.isEmpty(availableCouponmap)) {
            return List.of();
        }
        //3.2排列组合
        availableCoupons = new ArrayList<>(availableCouponmap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        //3.3添加单券的方案
        for (Coupon availableCoupon : availableCoupons) {
            solutions.add(List.of(availableCoupon));
        }
        //4.计算方案的优惠明细
        List<CouponDiscountDTO> list = new ArrayList<>();
        for (List<Coupon> solution : solutions) {
            list.add(calculateSolutionDiscount(solution, availableCouponmap, orderCourses));
        }

        //5.筛选最优解

        return List.of();
    }

    private CouponDiscountDTO calculateSolutionDiscount(
            List<Coupon> solution, Map<Coupon, List<OrderCourseDTO>> couponMap, List<OrderCourseDTO> courses) {
        //1.初始化DTO
        CouponDiscountDTO dto = new CouponDiscountDTO();
        //2.初始化折扣明细的映射
        Map<Long, Integer> detailMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, oc -> 0));
        //3.计算折扣
        for (Coupon coupon : solution) {
            //3.1获取优惠卷限定范围的课程
            List<OrderCourseDTO> availableCourses = couponMap.get(coupon);
            //3.2计算课程总价(课程原价-折扣明细)
            int totalAmount = availableCourses.stream().mapToInt(
                    oc -> oc.getPrice() - detailMap.get(oc.getId())
            ).sum();
            //3.3判断是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) {
                // 优惠卷的可用范围为空,则跳过
                continue;
            }
            //3.4计算优惠
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);

            //3.4计算优惠明细
            calculateDiscountDetails(detailMap, availableCourses, totalAmount, discountAmount);
            //3.5更新DTO数据
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());
        }
        return null;
    }

    private void calculateDiscountDetails(Map<Long, Integer> detailMap, List<OrderCourseDTO> courses, int totalAmount, int discountAmount) {

        int times = 0;
        int remainDiscount = discountAmount;
        for (OrderCourseDTO course : courses) {
            //计算优惠明细的次数
            times++;
            int discount = 0;
            if (times == courses.size()) {
                // 是最后一个课程,总折扣金额-之前所有商品的折扣金额之和
                discount = remainDiscount;
            } else {
                //计算优惠明细
                discount = discountAmount * course.getPrice() / totalAmount;
                remainDiscount -= discount;
            }
            //更新优惠明细
            detailMap.put(course.getId(), discount + detailMap.get(course.getId()));
        }
    }

    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(
            List<Coupon> coupons, List<OrderCourseDTO> courses
    ) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>(coupons.size());
        for (Coupon coupon : coupons) {
            List<OrderCourseDTO> availableCourses = courses;
            //1.找出优惠卷的可用的课程
            if (coupon.getSpecific()) {
                //1.1查询卷的可用范围
                List<CouponScope> couponScopes = couponScopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                //1.2获取范围对应的分类id
                Set<Long> scopesIds = couponScopes.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                //1.3筛选课程
                availableCourses = courses.stream().filter(c -> scopesIds.contains(c.getCateId())).collect(Collectors.toList());

            }
            if (CollUtils.isEmpty(availableCourses)) {
                // 优惠卷的可用范围为空,则跳过
                continue;
            }
            //2.计算课程总价
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            //3判断是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                map.put(coupon, availableCourses);
            }
        }
        return map;
    }
}
