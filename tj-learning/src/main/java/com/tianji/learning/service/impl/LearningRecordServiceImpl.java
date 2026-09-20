package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.ognl.BooleanExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-09-20
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final CourseClient courseClient;

    private final ILearningLessonService lessonService;

    // 查询指定课程的学习记录
    @Override
    public LearningLessonDTO queryLearningLessonByCourse(Long courseId) {
        //1.获取登录用户
        Long userId = UserContext.getUser();
        //2.查询课表
        LearningLesson lesson = lessonService.queryByUserIdAndCourseId(userId, courseId);
        if (lesson == null) {
            return null;
        }
        //3.查询学习记录
        //select * from learning_record where lesson_id = #{lessonId}
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        List<LearningRecordDTO> list = BeanUtils.copyList(records, LearningRecordDTO.class);
        //4.封装结果
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(lesson.getId());
        learningLessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        learningLessonDTO.setRecords(list);
        return learningLessonDTO;
    }


    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDto) {
        //1.获取登录用户
        Long userId = UserContext.getUser();
        //2.处理学习记录
        boolean finished = false;
        if (recordDto.getSectionType() == SectionType.VIDEO) {
            //2.1处理视频
            finished = handleVideoRecord(userId, recordDto);
        } else {
            //2.2处理考试
            finished = handleExamRecord(userId, recordDto);
        }

        //3.处理课表数据
        handleLearningLessonsChanges(recordDto, finished);
    }

    //处理视频
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordDto) {
        //1.查询旧的学习记录
        LearningRecord old = lambdaQuery()
                .eq(LearningRecord::getLessonId, recordDto.getLessonId())
                .eq(LearningRecord::getSectionId, recordDto.getSectionId())
                .one();
        //2.判断是否存在
        if (old == null) {
            //3.不存在则新增
            //3.1转换po
            LearningRecord record = BeanUtils.copyBean(recordDto, LearningRecord.class);
            //3.2填充数据
            record.setUserId(userId);
            //3.3写入数据库
            boolean success = save(record);
            if (!success) {
                throw new DbException("添加视频学习记录失败");
            }
            return false;
        }
        //4.存在则更新
        //4.1判断是否是第一次完成
        //如果old完成状态为false，且时长到了总时长一半时，说明是第一次完成
        boolean finished = !old.getFinished() && recordDto.getMoment() * 2 >= recordDto.getDuration();
        //4.2更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDto.getMoment())
                .set(finished, LearningRecord::getFinished, true)
                .set(finished, LearningRecord::getFinishTime, recordDto.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if (!success) {
            throw new DbException("更新视频学习记录失败");
        }
        return finished;
    }

    //处理考试的学习记录
    //该流程包括：1.转换dto为po 2.填充数据 3.写入数据库 4.返回结果
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO recordDto) {
        //1.转换dto为po
        LearningRecord record = BeanUtils.copyBean(recordDto, LearningRecord.class);
        //2.填充数据
        record.setUserId(userId);
        record.setFinished(true);
        record.setCreateTime(LocalDateTime.now());
        //3.写入数据库
        boolean success = save(record);
        if (!success) {
            throw new DbException("添加考试学习记录失败");
        }
        return true;
    }

    //处理课表数据
    private void handleLearningLessonsChanges(LearningRecordFormDTO recordDto, boolean finished) {
        //1.查询课表已经学习的小结数量
        LearningLesson lesson = lessonService.getById(recordDto.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在,无法更新数据!");
        }

        boolean allLearned = false;

        //2.判断是否有新的完成小结
        if (finished) {
            //3.如果有新完成的小结,则需要查询课程数据
            //这里查询到课程的信息
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cInfo == null) {
                throw new BizIllegalException("课程不存在,无法更新数据!");
            }
            //4.比较课程是否全部学完,已学习小结 >= 总小节
            allLearned = lesson.getLatestSectionId() + 1 >= cInfo.getSectionNum();
        }
        //5.更新课表
        lessonService.lambdaUpdate()
                //如果allLearned为true,则更新课表状态为已完成
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                //如果finished为true,则更新已学习小结数量
                .setSql(finished, "learned_sections = learned_sections + 1")
                //如果finished为false,则更新最新学习时间
                .set(!finished, LearningLesson::getLatestLearnTime, recordDto.getCommitTime())
                //当为第一次学习这个课表中的这个小结时,将该课表状态设置为学习中
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .eq(LearningLesson::getId, recordDto.getLessonId())
                .update();

    }
}
