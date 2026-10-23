package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import java.util.List;


@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecord() {
        //1.签到
        //1.1获取登录用户
        Long userId = UserContext.getUser();
        //1.2获取日期
        LocalDate now = LocalDate.now();
        //1.3拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        //1.4计算offset
        int offset = now.getDayOfMonth() - 1;
        //1.5保存签到信息

        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(exits)) {
            //已签到
            throw new IllegalArgumentException("已签到");
        }
        //2.计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());
        //3.计算签到的分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        //4.保存积分明细记录
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));
        //5.封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }


    private int countSignDays(String key, int len) {
        //1.获取本月从第一天开始,到今天为止的所有签到记录
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        int num = result.get(0).intValue();
        //2.定义计数器
        int count = 0;
        //3.循环,与1做与与运算,得到最后一个bit,判断是否为0,为0则终止,为1则继续
        while ((num & 1) == 1) {
            //4.计数器加1
            count++;
            //5.把数字右移1位,得到下一个bit
            num >>>= 1;
        }
        return count;

    }


    @Override
    public int[] getSignRecord() {
        //1.获取登录用户
        Long userId = UserContext.getUser();
        //2.查询签到记录
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + LocalDate.now().format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        //3.获取本月从第一天开始,到今天为止的所有签到记录
        int len = LocalDate.now().getDayOfMonth();
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return new int[0];
        }
        int num = result.get(0).intValue();
        //4.定义数组
        int[] signRecord = new int[len];
        //5.循环,与1做与与运算,得到最后一个bit,判断是否为0,为0则终止,为1则继续
        for (int i = 0; i < len; i++) {
            signRecord[i] = (num & 1) == 1 ? 1 : 0;
            num >>>= 1;
        }
        return signRecord;
    }
}
