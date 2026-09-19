package com.tianji.learning.mq;

import cn.hutool.log.Log;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService lessonService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.kessib.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order) {
        //1.健壮性处理
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())){
            //数据有误，无需处理
            log.error("订单数据有误，无需处理，订单id：{}数据为空", order.getOrderId());
            return;
        }
        //2.添加课程
        log.debug("监听到用户{}的订单{}，添加课程{}", order.getUserId(), order.getOrderId(), order.getCourseIds());
        lessonService.addUserLessons(order.getUserId(),order.getCourseIds());
    }

    /**
     * 监听课程退款或删除课程的消息
     *
     * @param dto 订单信息
     **/
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE,
                    type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenCourseRefund(OrderBasicDTO dto) {
//1、健壮性处理
        if (dto == null || dto.getUserId() == null || CollUtils.isEmpty(dto.getCourseIds())) {
//数据误，无需处理
            log.error("接收到MQ消息有误，订单数据为空");
            return;
        }
//2.调用service，删除课程
        log.debug("监听到用户{}的订单{}要退款，需要从课表中删除课程{}",
                dto.getUserId(), dto.getOrderId(), dto.getCourseIds());
        lessonService.deleteCourse(dto.getCourseIds().get(0), dto.getUserId());
    }


}
