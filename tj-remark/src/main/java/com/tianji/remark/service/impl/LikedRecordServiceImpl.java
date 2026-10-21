package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-20
 */
//@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;


    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        //1.基于前端的参数判断是执行点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unLike(recordDTO);
        //2.判断是否执行成功,如果失败,直接结束
        if (!success) {
            return;
        }
        //3.如果执行成功,统计点赞总数
        Integer likeTimes = lambdaQuery().eq(LikedRecord::getBizId, recordDTO.getBizId()).count();

        //4.发送MQ通知
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE,recordDTO.getBizType()),
                LikeTimesDTO.of(likeTimes, recordDTO.getBizId())
                );

    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        //1.获取登录用户的id
        Long userId = UserContext.getUser();
        //2.查询点赞状态
        List<LikedRecord> list = lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();

        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizIds) {

    }

    private boolean like(LikeRecordFormDTO recordDTO) {
        //1.查询点赞记录
        Integer count = lambdaQuery()
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .eq(LikedRecord::getBizId, recordDTO.getBizId())
                .count();

        //2.判断是否存在,如果已经存在,直接结束
        if (count > 0) {
            return false;
        }

        //3.如果不存在,则新增点赞记录
        LikedRecord likedRecord = BeanUtils.copyProperties(recordDTO, LikedRecord.class);
        likedRecord.setUserId(UserContext.getUser());
        likedRecord.setCreateTime(LocalDateTime.now());
        likedRecord.setUpdateTime(LocalDateTime.now());
        save(likedRecord);
        return true;
    }

    /**
     * 取消点赞
     * @param recordDTO
     * @return
     */
    private boolean unLike(LikeRecordFormDTO recordDTO) {
        return remove(new QueryWrapper<LikedRecord>().lambda()
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .eq(LikedRecord::getBizId, recordDTO.getBizId()));

    }
}
