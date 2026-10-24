package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-21
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;


    @Override
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {
        int maxPoints = type.getMaxPoints();
        LocalDateTime now = LocalDateTime.now();

        //1.判断当前方式有没有积分上限
        int realPoints = points;
        if (maxPoints > 0) {
            //2.有,则需要判断是否超过上限
            LocalDateTime begin = DateUtils.getDayStartTime(now);
            LocalDateTime end = DateUtils.getDayEndTime(now);
            //2.1查询今日已得积分
            int currentPoints = queryUserPointsByTypeAndDate(userId, type, begin, end);
            //2.2判断是否超过上限
            if (currentPoints >= maxPoints) {
                //2.3超过上限,则提示用户直接结束
                return;
            }
            //2.4未超过上限,则保存积分记录
            if (currentPoints + points > maxPoints) {
                realPoints = maxPoints - currentPoints;
            }
        }
        //3.没有,直接保存积分记录
        PointsRecord p = new PointsRecord();
        p.setPoints(realPoints);
        p.setUserId(userId);
        p.setType(type);
        save(p);

        //4.累计积分数据到Redis的SortedSet中
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_DATE_SUFFIX_FORMATTER);
        Double v = redisTemplate.opsForZSet().incrementScore(key, userId.toString(), realPoints);

    }


    private int queryUserPointsByTypeAndDate(Long userId, PointsRecordType type, LocalDateTime begin, LocalDateTime end) {

        //1.查询条件
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .eq(type != null, PointsRecord::getType, type)
                .between(begin != null && end != null, PointsRecord::getCreateTime, begin, end);

        //2.调用mapper,查询结果
        Integer points = getBaseMapper().queryUserPointsByTypeAndDate(wrapper);

        //3.判断返回
        return points == null ? 0 : points;
    }


    /**
     * 查询用户今日积分统计
     * <p>
     * 业务背景：积分是平台对用户学习行为的奖励机制，不同类型的操作（如签到、看课、做题等）
     * 对应不同积分类型（PointsRecordType），每种类型有单日积分上限（maxPoints）和每次奖励分值。
     * 该方法用于统计用户"今天"获得了哪些类型的积分、各得了多少分，供前端展示今日积分明细。
     *
     * @return 今日积分统计列表，按积分类型逐条返回（类型描述、单日上限、实际得分）
     */
    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        //1. 获取当前登录用户
        //UserContext 是从请求上下文（登录态）中取出当前用户标识，后续查询均以该用户为维度
        Long user = UserContext.getUser();

        //2. 计算今天的时间区间 [begin, end]
        //注意：积分记录按创建时间（createTime）筛选，因此必须拿到"今天的零点"和"明天的零点"作为闭区间
        LocalDateTime now = LocalDateTime.now();                      // 当前时刻
        LocalDateTime begin = DateUtils.getDayStartTime(now);         // 今日 00:00:00
        LocalDateTime end = DateUtils.getDayEndTime(now);             // 今日 23:59:59.999（近似）

        //3. 构建查询条件（只查当前用户、且创建时间落在今天的记录）
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, user)                    // 用户维度过滤
                .between(PointsRecord::getCreateTime, begin, end);    // 时间区间闭区间过滤

        //4. 执行查询，获取今日所有积分记录
        List<PointsRecord> list = getBaseMapper().queryUserPointsByDate(wrapper);

        //4.1 空结果快速返回：今天还没有任何积分记录时，直接返回空列表，避免后续无用计算
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }

        //5. 将实体列表转换为 VO 列表（把数据脱敏/精简后交给前端）
        List<PointsStatisticsVO> vos = new ArrayList<>(list.size()); // 预分配容量，避免扩容开销
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());       // 积分类型的中文描述（如"签到"）
            vo.setMaxPoints(p.getType().getMaxPoints()); // 该类积分今日可获得的最高分（用于前端展示上限）
            vo.setPoints(p.getPoints());             // 本次实际获得的积分数
            vos.add(vo);
        }

        //6. 返回封装好的结果
        return vos;
    }
}
