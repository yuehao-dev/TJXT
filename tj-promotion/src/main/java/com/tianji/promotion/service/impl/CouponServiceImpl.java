package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constans.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-24
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {


    private final ICouponScopeService scopeService;

    private final IExchangeCodeService codeService;

    private final IUserCouponService userCouponService;

    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        //1.保存优惠卷
        //1.1转PO
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);

        if (!dto.getSpecific()) {
            //没有限定范围
            return;
        }
        Long couponId = coupon.getId();
        //2.保存限定范围
        List<Long> scopes = dto.getScopes();

        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("优惠券作用范围不能为空");
        }

        //2.1转换PO
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId).setType(1)/*todo天机学堂1.0默认类型是1,没加分辨的代码*/)
                .collect(Collectors.toList());
        //2.2保存
        scopeService.saveBatch(list);


    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        Integer status = query.getStatus();
        String name = query.getName();
        Integer type = query.getType();

        //1.分页查询
        Page<Coupon> page = lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        //2.处理vo
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        //3.返回
        return PageDTO.of(page, list);
    }


    @Override
    @Transactional
    public void beginIssue(CouponIssueFormDTO dto) {
        //1.查询优惠卷
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        //2.判断优惠卷的状态,是否暂停或待发放
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException("优惠券状态错误");
        }

        //3.判断是否立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        //开始发放时间为null或不大于当前时间,则立刻发放
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);
        //4.更新优惠卷
        //4.1拷贝属性到PO
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        //4.2更新状态
        if (isBegin) {
            c.setStatus(CouponStatus.ISSUING);
            //todo 自己改的 立即发放时,若未指定开始时间,则记录为当前时刻,避免 issue_begin_time 为空
            c.setIssueBeginTime(issueBeginTime == null ? now : issueBeginTime);
        } else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        //4.3写入数据库
        updateById(c);

        //5添加缓存
        if (isBegin) {
            //立刻发放,则缓存1天
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        //5.判断是否需要生成兑换码,优惠卷类型必须是兑换码,状态为待发放
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            //生成兑换码
            codeService.asyncGenerateCode(coupon);
        }


    }

    private void cacheCouponInfo(Coupon coupon) {
        //1.组织数据
        Map<String, String> map = new HashMap<>(4);
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum", String.valueOf(coupon.getTotalNum()));
        map.put("usedLimit", String.valueOf(coupon.getUserLimit()));
        redisTemplate.opsForHash().putAll(PromotionConstants.COUPON_CACHE_MAP_KEY_PREFIX + coupon.getId(), map);
    }

    @Override
    @Transactional
    public void pauseIssue(Long id) {
        //1.查询优惠券
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        //2.校验优惠券状态，仅"发放中"的优惠券才能暂停
        if (coupon.getStatus() != CouponStatus.ISSUING) {
            throw new BizIllegalException("优惠券状态错误");
        }
        //3.更新状态为暂停
        Coupon c = new Coupon();
        c.setId(id);
        c.setStatus(CouponStatus.PAUSE);
        updateById(c);
        //4.删除缓存
        redisTemplate.delete(PromotionConstants.COUPON_CACHE_MAP_KEY_PREFIX + id);
    }

    /**
     * ============================================================================
     *  查询当前处于"发放中"状态、且领取方式为"公开发放"的优惠券；
     *  并结合当前登录用户的领取/使用情况，标注每张券"是否可领取"和"是否已持有"。
     * ============================================================================
     *  业务场景：优惠券领取页面，向用户展示当前可领的公开券及个人领取进度。
     *
     * @return 发放中的公开优惠券 VO 列表；若无满足条件的券，则返回空列表(不可变)
     */
    @Override
    public List<CouponVO> queryIssuingCoupons() {
        /*
         * ================= 第 1 步：查询符合条件的优惠券 =================
         * 通过 MyBatis-Plus 的条件构造器拼接两个查询条件：
         *  - status    : 状态必须为 ISSUING(发放中)，保证只展示当前正在发行的券
         *  - obtainWay : 领取方式必须为 PUBLIC(公开发放)，排除"兑换码"等其他获取方式的券
         */
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();

        // 若没有任何符合条件的券，直接返回空列表，避免后续无意义的统计查询
        if (CollUtils.isEmpty(coupons)) {
            return List.of();
        }

        /*
         * ================= 第 2 步：统计当前用户对这些券的领取情况 =================
         */

        // 2.1 取出所有券的 id，构造后续批量查询所需的 id 集合
        List<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());

        // 2.2 查询当前用户已领取的这些券的记录
        //     - userId  : 当前登录用户id(通过 UserContext 从认证上下文获取)
        //     - couponId: 属于上一步的 couponIds 范围内
        //     注：同一用户同一张券可能有多条领取记录(每种券可领多张)，故记录数 >= 券张数
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();

        // 2.3 按 couponId 分组计数，得到当前用户"已领取的总数量"
        //     groupingBy: 以 couponId 为 key；counting: 累计每组记录数(即领取张数)
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 2.4 先过滤出 UNUSED(未使用) 的记录，再按 couponId 分组计数
        //     得到当前用户"已领取且未使用"的数量，用于判断是否已持有该券(可去使用)
        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        /*
         * ================= 第 3 步：组装最终的 VO 结果列表 =================
         * 预先指定容量为 coupons.size()，避免集合扩容开销
         */
        List<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {

            // 3.1 将数据库实体 Coupon 的字段拷贝到展示对象 CouponVO 中
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            list.add(vo); // 先放入列表，后续仅做属性补充

            // 3.2 计算"是否可以领取"(available)，需同时满足两个条件：
            //     (1) 该券已发放数量 < 优惠券总量  -> 券未领完，库存充足
            //     (2) 当前用户已领取数量 < 单人限领数 -> 未达到领取上限
            //     用 getOrDefault 缺省为 0，避免空指针
            vo.setAvailable(
                    c.getIssueNum() < c.getTotalNum()
                            && issuedMap.getOrDefault(c.getId(), 0L) < c.getUserLimit()
            );

            // 3.3 计算"是否已持有/可使用"(received)：
            //     当前用户针对该券"已领取且未使用"的数量 > 0，说明手里还有这张券，可直接去使用
            vo.setReceived(unusedMap.getOrDefault(c.getId(), 0L) > 0);
        }

        return list; // 4. 返回组装好的优惠券VO列表
    }
}