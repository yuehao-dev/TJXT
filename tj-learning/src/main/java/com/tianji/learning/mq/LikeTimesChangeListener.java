package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesChangeListener {


    private final IInteractionReplyService replyService;

    /**
     * 监听问答回复的点赞总数变化消息
     * <p>
     * 当 remark 服务（点赞服务）中某个问答回复的点赞数发生变化时，
     * 会通过 RabbitMQ 发送消息，本方法负责接收并更新 learning 库中对应回复的点赞数字段。
     * <p>
     * 交换机类型为 TOPIC，通过 RoutingKey 精准匹配，确保只有问答回复（QA）类型的点赞消息被消费。
     *
     * @param likeTimesDTOS 点赞数变化列表，每个 DTO 包含业务id（bizId）和最新的点赞总数（likeTimes）
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    name = "qa.liked.times.queue",  // 队列名称：qa（问答）.liked（点赞）.times（次数）
                    durable = "true"                  // 队列持久化，服务重启后队列仍存在，消息不丢失
            ),
            exchange = @Exchange(
                    name = LIKE_RECORD_EXCHANGE,      // 交换机名称（来自 MqConstants 常量）
                    type = ExchangeTypes.TOPIC         // TOPIC 模式：支持 RoutingKey 通配符匹配，灵活路由
            ),
            key = QA_LIKED_TIMES_KEY                  // 绑定的 RoutingKey，只消费问答回复类型的点赞次数消息
    ))
    public void listenReplyLikedTimesChange(List<LikeTimesDTO> likeTimesDTOS) {
        log.debug("收到点赞总数变化通知");

        // 预分配 List 容量，避免扩容带来的性能开销
        List<InteractionReply> replies = new ArrayList<>(likeTimesDTOS.size());
        // 遍历消息体，将 DTO 转换为 InteractionReply 实体
        for (LikeTimesDTO likeTimesDTO : likeTimesDTOS) {
            InteractionReply r = new InteractionReply();
            // 设置主键id：bizId 即问答回复id
            r.setId(likeTimesDTO.getBizId());
            // 设置最新的点赞总数，将覆盖数据库中的原值
            r.setLikedTimes(likeTimesDTO.getLikeTimes());
            replies.add(r);
        }
        // 批量更新：MyBatis-Plus 的 updateBatchById，一条 SQL 语句更新多条记录
        // 只更新实体中非 null 的字段（likedTimes），不会影响其他字段
        replyService.updateBatchById(replies);

    }

}