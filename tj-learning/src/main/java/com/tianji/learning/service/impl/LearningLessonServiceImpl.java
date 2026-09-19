package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.Temperature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-09-15
 */
@Service
@Slf4j
//为所有 final 字段（以及 @NonNull 标注的未初始化字段）自动生成一个构造方法。
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            //课程不存在，无法添加
            log.error("课程不存在，无法添加");
            return;
        }
        //2.循环便利，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            //2.1获取过期的时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null || validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            //2.2填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        //3.批量新增
        saveBatch(list);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        //1.获取到当前登录用户
        Long userId = UserContext.getUser();
        //2.分页查询
        //select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 0,5
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId) //where user_id = #{userId}
                .page(query.toMpPage(" latest_learn_time", false));//拼接分页信息按照latest_learn_time降序
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            //课程不存在，无法添加
            return PageDTO.empty(page);
        }
        //3.查询课程信息
        //3.1获取课程id列表
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //3.2根据课程id列表查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)) {
            //课程不存在，无法添加
            throw new BadRequestException("课程不存在，无法添加");
        }
        //3.3把课程集合处理成Map,key是courseId,值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        //4.封装vo返回
        List<LearningLessonVO> list = new ArrayList<>(records.size());//集合初始化
        //4.1循环遍历,把learningLesson转为vo
        for (LearningLesson r : records) {
            //4.2拷贝基础属性到VO
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            //4.3获取课程信息,填充到vo
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }

        return PageDTO.of(page, list);
    }

    // 查询最近正在学习的课程
    @Override
    public LearningLessonVO queryNowLearningLesson() {
        Long user = UserContext.getUser();
        //1.查询最近正在学习的课程
        //select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 1
        LearningLesson lessonPo = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("LIMIT 1")
                //.one() 的语义是：“我确信最多只有一条结果，如果多于一条就报错
                .one();

        if (lessonPo == null) {
            //课程不存在，无法添加
            throw new BadRequestException("课程不存在，无法添加");
        }
        LearningLessonVO lessonVO = new LearningLessonVO();
        BeanUtils.copyProperties(lessonPo, lessonVO);
        //2.查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(List.of(lessonPo.getCourseId()));
        if (CollUtils.isEmpty(cInfoList)) {
            //课程不存在，无法添加
            throw new BadRequestException("课程不存在，无法添加");
        }
        CourseSimpleInfoDTO cInfo = cInfoList.get(0);
        lessonVO.setCourseName(cInfo.getName());
        lessonVO.setCourseCoverUrl(cInfo.getCoverUrl());
        lessonVO.setSections(cInfo.getSectionNum());

        return lessonVO;
    }

    // 根据课程id查询课程的学习状态
    @Override
    public LearningLessonVO queryLessonStatus(Long courseId) {
        Long user = UserContext.getUser();
        //根据课程id查询课程的学习状态
        LearningLesson lessonPo = lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .last("LIMIT 1")
                .one();
        if (lessonPo == null) {
            //课程不存在
            return null;
        }
        LearningLessonVO lessonVO = new LearningLessonVO();
        BeanUtils.copyProperties(lessonPo, lessonVO);
        return lessonVO;
    }

    /**
     * 删除用户课表中的课程
     *
     * @param courseId
     * @param userId
     */
    @Override
    public void deleteCourse(Long courseId, Long userId) {
        //1.判断当前登录用户id是否为null
        //调用这个方法有两种情况：
        //                      用户直接删除已失效的课程 -> 在controller中调用，没有获取用户id，只传了null值
        //                      用户退款后触发课表自动删除 -> 在listener中调用，直接获取了OrderBasicDTO中的用户id
        //listenCourseRefund已有健壮性判断，这里目的是在直接删除已失效的课程时，获取用户id
        if (userId == null) {
            userId = UserContext.getUser();
        }
        //2、删除
        //        remove(lambdaQuery().eq(LearningLesson::getUserId, userId)
        //                .eq(LearningLesson::getCourseId, userId));
        lambdaUpdate()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .remove();
    }



}
