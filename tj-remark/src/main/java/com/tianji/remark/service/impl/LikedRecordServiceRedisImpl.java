package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        //1.基于前端的参数判断是执行点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unLike(recordDTO);
        //2.判断是否执行成功,如果失败,直接结束
        if (!success) {
            return;
        }
        //3.如果执行成功,统计点赞总数
        Long likeTimes = redisTemplate.opsForSet()
                .size(RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId());

        if (likeTimes == null) {
            return;
        }

        //4.缓存点赞总数到Redis发送MQ通知
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMES_KEY_PREFIX + recordDTO.getBizType(),
                recordDTO.getBizId().toString(),
                likeTimes
        );
    }

    /**
     * 批量判断当前登录用户是否对指定业务对象点过赞
     * <p>
     * 使用 Redis Pipeline 批量执行 SISMEMBER 命令，一次网络往返即可查询所有 bizId 的点赞状态，
     * 避免逐条查询带来的网络延迟开销。
     *
     * @param bizIds 需要查询点赞状态的业务id列表（如课程id、评论id等）
     * @return 当前用户已点赞的业务id集合，未点赞的不会出现在集合中
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 1. 从 ThreadLocal 中获取当前登录用户的id
        Long userId = UserContext.getUser();

        // 2. 使用 Redis Pipeline 批量查询点赞状态
        // Pipeline 会将多条 SISMEMBER 命令打包成一次网络请求发送，大幅减少网络往返次数（RTT），
        // 提升批量查询性能。每条命令对应判断 userId 是否在 likes:set:biz:{bizId} 这个 Set 中。
        List<Object> objects = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                // 将 Redis 连接转为 String 类型连接，方便操作字符串命令
                StringRedisConnection src = (StringRedisConnection) connection;
                // 遍历所有业务id，逐个将 SISMEMBER 命令添加到 Pipeline 中（此时并未真正执行）
                for (Long bizId : bizIds) {
                    // 拼接 Redis Key：likes:set:biz:{bizId}
                    String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                    // 添加 SISMEMBER 命令到 Pipeline，判断 userId 是否在该 Set 中
                    // 返回的布尔值会在 Pipeline 执行后按顺序放入 objects 列表中
                    src.sIsMember(key, userId.toString());
                }
                // executePipelined 要求 return null，真正的结果通过 objects 列表获取
                return null;
            }
        });

        // 3. 解析 Pipeline 结果，收集当前用户已点赞的业务id
        // objects 列表中的元素顺序与上面 for 循环中添加 SISMEMBER 的顺序一一对应，
        // 即 objects.get(i) 对应 bizIds.get(i) 的查询结果
        Set<Long> set = new HashSet<>();
        for (int i = 0; i < objects.size(); i++) {
            // 每个元素是 SISMEMBER 的返回结果：true（是成员，已点赞）/ false（不是成员，未点赞）
            Boolean o = (Boolean) objects.get(i);
            if (o) {
                // 已点赞，将该 bizId 加入结果集
                set.add(bizIds.get(i));
            }
        }
        return set;
    }



    private boolean like(LikeRecordFormDTO recordDTO) {
        //1.获取登录用户的id
        Long userId = UserContext.getUser();

        //2.获取key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId();

        //3.执行SADD命令
        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        return result != null && result > 0;
    }

    /**
     * 取消点赞
     * @param recordDTO
     * @return
     */
    private boolean unLike(LikeRecordFormDTO recordDTO) {
        //1.获取登录用户的id
        Long userId = UserContext.getUser();
        //2.获取key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId();

        //3.remove命令
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        return result != null && result > 0;

    }


    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizIds) {
        //1.读取并移除Redis中缓存的点赞总数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizIds);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        //2.数据转换
        List<LikeTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if (bizId ==null || likedTimes ==null) {
                continue;
            }
            list.add(LikeTimesDTO.of(likedTimes.intValue(), Long.valueOf(bizId)));
        }
        //3.发送MQ消息
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE,bizType),
                list
        );

    }
}