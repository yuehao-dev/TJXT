package com.tianji.learning.service;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-09-15
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);

    // 分页查询我的课表
    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);


    // 查询最近正在学习的课程
    LearningLessonVO queryNowLearningLesson();

    // 根据课程id查询课程的学习状态
    LearningLessonVO queryLessonStatus(Long courseId);

    // 删除课表中的课程
    void deleteCourse(Long courseId, Long user);

    // 根据用户id和课程id查询课程表信息
    LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId);
}
