package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-09-15
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "我的课表相关接口")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @GetMapping("/page")
    @ApiOperation(value = "分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {

        return lessonService.queryMyLessons(query);
    }

    @GetMapping("/now")
    @ApiOperation(value = "查询最近正在学习的课程")
    public LearningLessonVO queryNowLearningLesson() {
        return lessonService.queryNowLearningLesson();
    }

    @GetMapping("/{courseId}")
    @ApiOperation(value = "根据id查询指定课程的学习状态")
    public LearningLessonVO queryLessonStatus(@PathVariable Long courseId) {
        return lessonService.queryLessonStatus(courseId);
    }

    @ApiOperation("删除课表中的某课程")
    @DeleteMapping("/{courseId}")
    public void deleteByCourseId(@PathVariable("courseId") Long courseId) {
        lessonService.deleteCourse(courseId, UserContext.getUser());
    }

}
