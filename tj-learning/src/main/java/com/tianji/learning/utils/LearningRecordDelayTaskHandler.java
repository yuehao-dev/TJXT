package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.el.lang.ELArithmetic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@RequiredArgsConstructor
@Component
public class LearningRecordDelayTaskHandler {

    //recordMapper , lessonService 用于更新学习记录和课表最近学习信息
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;

    //redisTemplate 用于缓存学习记录
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();

    //RECORD_KEY_TEMPLATE 用于生成缓存key
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";

    //queue 用于存储延迟任务
    private final StringRedisTemplate redisTemplate;

    //begin 用于控制是否继续处理到期的延迟任务
    //volatile 用于确保多线程环境下 begin 变量的可见性
    private static volatile boolean begin = true;

    //PostConstruct: 初始化方法,用于初始化延迟队列
    @PostConstruct
    public void init() {
        //启动异步线程,用于处理到期的延迟任务
        CompletableFuture.runAsync(this::handleDelayTask);
    }

    //PreDestroy: 销毁方法,用于停止处理到期的延迟任务
    @PreDestroy
    public void destroy() {
        begin = false;
        log.debug("延时任务停止执行");
    }


    public void handleDelayTask() {
        while (begin) {
            try {
                //1.获取到期的延迟任务
                DelayTask<RecordTaskData> take = queue.take();
                RecordTaskData data = take.getData();
                //2.查询redis缓存
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (record == null) {
                    continue;
                }
                //3.比较数据 moment值
                if (!Objects.equals(data.getMoment(), record.getMoment())) {
                    //不一致,说明用户还在持续提交播放进度,放弃旧数据
                    continue;
                }

                //4.一致,持久化播放进度数据到数据库
                //4.1更新学习记录的moment
                record.setFinished(null);
                recordMapper.updateById(record);

                //4.2更新课表最近学习信息
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);

            } catch (InterruptedException e) {
                log.error("处理到期的延迟任务失败，e={}", e);
                throw new RuntimeException(e);
            }

        }
    }

    public void addLearningRecordTask(LearningRecord record) {
        //1.添加数据到redis缓存
        writeRecordCache(record);
        //2.提交延迟任务到延迟队列 DelayQueue
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));

    }

    //将学习记录写入redis缓存
    public void writeRecordCache(LearningRecord record) {
        log.debug("将学习记录写入redis缓存，record={}", record);
        try {
            //1.数据转换
            RecordCacheData recordCacheData = new RecordCacheData(record);
            String json = JsonUtils.toJsonStr(recordCacheData);

            //2.写入redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);

            //3.添加缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("将学习记录写入redis缓存失败，record={}", record, e);
        }
    }
    //从redis缓存中读取学习记录
    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            //1.读取redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            //2.数据检查和转换
            if (cacheData == null) {
                return null;
            }
            LearningRecord record = JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
            return record;
        } catch (Exception e) {
            log.error("从redis缓存中读取学习记录失败，lessonId={},sectionId={}", lessonId, sectionId, e);
            return null;
        }
    }

    //删除redis缓存中的学习记录
    public void cleanRecordCache(Long lessonId, Long sectionId) {
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }


    //缓存数据
    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Boolean finished;
        private Integer moment;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.finished = record.getFinished();
            this.moment = record.getMoment();
        }
    }

    //延迟任务数据
    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }

}
