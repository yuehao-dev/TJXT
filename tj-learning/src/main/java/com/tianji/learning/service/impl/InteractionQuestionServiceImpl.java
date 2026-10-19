package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-08
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;
    private final UserClient userClient;

    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;


    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        //1.获取当前登录的用户id
        Long userId = com.tianji.common.utils.UserContext.getUser();
        //2.封装数据
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setUserId(userId);
        //3.写入数据库
        save(question);
    }

    /**
     * 分页查询问题表
     * @param query
     * @return
     */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        /* ==================== 阶段一：参数校验 ==================== */
        // 取出课程id和小节id两个查询条件
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        // 业务规则：课程id 和 小节id 至少要传一个，否则没有任何范围可查
        if (courseId == null && sectionId == null) {
            throw new IllegalArgumentException("课程id和小结id不能都为空");
        }

        /* ==================== 阶段二：分页查询问题表 ==================== */
        // lambdaQuery()：MyBatis-Plus 提供的链式条件构造器
        Page<InteractionQuestion> page = lambdaQuery()
                // 排除description大文本字段，列表页不需要，减少传输量、提升查询性能
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                // onlyMine=true 时（用 Boolean.TRUE.equals 避免 null 拆箱抛NPE），只查当前登录用户
                // 说明：eq第一个参数是基本类型boolean，若直接传null的Boolean会报空指针
                .eq(Boolean.TRUE.equals(query.getOnlyMine()),
                        InteractionQuestion::getUserId,      // 数据库字段
                        UserContext.getUser())               // 当前登录用户id
                // courseId不为空时才追加"按课程过滤"条件
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                // sectionId不为空时才追加"按小节过滤"条件
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                // 恒等条件：只查未被隐藏的问题
                .eq(InteractionQuestion::getHidden, false)
                // 按创建时间倒序（最新在前）分页
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        // 取出当前页的问题记录集合
        List<InteractionQuestion> records = page.getRecords();
        // 防御：这一页没有任何数据，直接返回空分页，避免后面空集合遍历
        if (records.isEmpty()) {
            return PageDTO.empty(page);
        }

        /* ==================== 阶段三：收集关联查询所需的id ==================== */
        // 用HashSet保存"提问者id去重集合"，后面批量查用户信息用
        HashSet<Long> userIds = new HashSet<>();
        // 用HashSet保存"最近回答id去重集合"，后面批量查回答内容用
        HashSet<Long> answerIds = new HashSet<>();

        // 3.1 遍历本页每一条问题，把需要的id收集起来
        for (InteractionQuestion q : records) {
            // 非匿名的问题才记录提问者id；匿名问题前端不展示真实用户，跳过
            if (!q.getAnonymity()) {
                userIds.add(q.getUserId());
            }
            // 无论是否匿名，最近回答的id都要记录（回答内容还是要展示）
            answerIds.add(q.getLatestAnswerId());
        }

        /* ==================== 阶段四：根据id批量查"最近回答" ==================== */
        // 排空：剔除根本没人回答的问题（latestAnswerId为null），避免查出一个null
        answerIds.remove(null);
        // 预分配容量的空Map，为后面"回答id -> 回答对象"的映射做准备
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if (CollUtils.isNotEmpty(answerIds)) {
            // 批量查出所有最近回答记录（一次SQL，避免N+1问题）
            List<InteractionReply> replies = replyService.listByIds(answerIds);
            replyMap = new HashMap<>(answerIds.size());
            for (InteractionReply reply : replies) {
                // 建立"回答id -> 回答对象"映射，方便后面O(1)查找
                replyMap.put(reply.getId(), reply);
                // 回答者若非匿名，也要把他的id收进userIds，
                // 这样阶段五会把"提问者 + 回答者"的用户信息一次性全查出来
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            }
        }

        /* ==================== 阶段五：根据id批量查"用户信息" ==================== */
        // 排空处理的id集合
        userIds.remove(null);
        // 预分配容量的空Map，后面存放"用户id -> 用户对象"
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (CollUtils.isNotEmpty(userIds)) {
            // 通过Feign调用用户服务的远程接口，批量查用户
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            // 把List转成 Map<id, 用户>，方便按id快速取用户
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        /* ==================== 阶段六：组装VO返回 ==================== */
        // 按记录数量预分配容量的目标列表
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 4.1 属性复制：把问题PO对象转成VO对象（相同字段自动拷贝）
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            voList.add(vo);

            // 4.2 填充"提问者昵称/头像"
            // 非匿名的问题才展示真实提问者信息
            if (!r.getAnonymity()) {
                UserDTO userDTO = userMap.get(r.getUserId()); // 按提问者id去userMap取
                if (userDTO != null) { // 防御：用户数据可能不存在
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }

            // 4.3 填充"最近一次回答的内容 + 回答者昵称"
            InteractionReply reply = replyMap.get(r.getLatestAnswerId()); // 按回答id取回答
            if (reply != null) { // 防御：该问题可能没有回答
                vo.setLatestReplyContent(reply.getContent()); // 回答内容
                if (!reply.getAnonymity()) { // 回答者也非匿名时才展示昵称
                    UserDTO user = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(user.getName());
                }
            }
        }

        // 把"分页信息 + 本页VO列表"封装成 PageDTO 返回给前端
        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        //1.根据id查询数据
        InteractionQuestion question = getById(id);
        //2.数据校验
        if (question == null || question.getAnonymity()) {
            // 没有数据或匿名问题前端不展示真实用户，跳过
            throw new IllegalArgumentException("问题不存在");
        }
        //3.查询提问者信息
        UserDTO user = null;
        if (!question.getAnonymity()) {
            user = userClient.queryUserById(question.getId());
        }
        //4.封装vo
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        return vo;
    }

    /**
     * 管理员端分页查询互动问题
     *
     * <p>整体流程：先根据可选的课程名称关键字查出具体的课程id集合，
     * 再按课程/状态/时间段进行分页查询问题主表；
     * 最后把本页涉及到的用户、课程、章节（含小节）数据批量补齐，拼装成分页VO返回。
     * 采用"批量查询 + 内存组装"的方式，避免对每条问题逐条发起远程调用造成N+1查询。</p>
     *
     * @param query 管理员端分页查询条件
     * @return 包含本页问题及其作者、课程、章节、分类等完整信息的VO分页结果
     */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        /* ==================== 阶段一：处理可选课程名称，得到课程id集合 ==================== */
        List<Long> courseIds = null;
        // 仅当用户按课程名称搜索时，才需要提前把名称转换成课程id
        if (StringUtils.isNotEmpty(query.getCourseName())) {
            // 调用搜索服务，根据课程名称模糊匹配，返回匹配的所有课程id
            // 注意：没有匹配到任何课程时，searchService 返回的是空集合（CollUtils.emptyList()），而非null
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            // 判断条件必须用 isEmpty：
            // 1. 若搜不到课程（courseIds为空集合），说明该名称下没有问题可查，直接返回空分页，避免用空集合拼 IN () 导致SQL错误
            // 2. 若搜到了课程（courseIds非空），才需要拿这些id去过滤问题，不能提前返回
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }

        /* ==================== 阶段二：分页查询问题主表 ==================== */
        // 取出状态、起止时间是三个可选筛选条件
        Integer status = query.getStatus();          // 问题状态（待回答/已解决等）
        LocalDateTime begin = query.getBeginTime();  // 更新时间开始范围
        LocalDateTime end = query.getEndTime();      // 更新时间结束范围
        // lambdaQuery()：MyBatis-Plus 链式条件构造器，依据"条件是否为null"决定是否拼接对应SQL
        Page<InteractionQuestion> page = lambdaQuery()
                // courseIds != null 时，按课程id集合过滤（in 条件的第一个参数为boolean，控制是否拼接）
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                // status != null 时，精确匹配问题状态
                .eq(status != null, InteractionQuestion::getStatus, status)
                // begin != null 时，update_time 大于 begin（gt: 大于）
                .gt(begin != null, InteractionQuestion::getUpdateTime, begin)
                // end != null 时，update_time 小于 end（lt: 小于）
                .lt(end != null, InteractionQuestion::getUpdateTime, end)
                // 按创建时间倒序（最新在前）分页
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        // 取出本页的问题记录集合
        List<InteractionQuestion> records = page.getRecords();
        // 防御：本页没有任何数据，直接返回空分页，避免后续空集合遍历和多余的远程调用
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        /* ==================== 阶段三：收集并批量查询关联数据 ==================== */
        // 记录过程中需要补齐的各类数据的id去重集合，方便一次批量查回
        Set<Long> userIds = new HashSet<>();     // 作者id
        Set<Long> cIds = new HashSet<>();        // 课程id
        Set<Long> chapterIds = new HashSet<>();  // 章节id（同时存放章节id与小节id，两者共用一套目录库）

        // 3.1 遍历本页每一条问题，把待补全数据所属的id全部收集起来
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());      // 收集提问者id
            cIds.add(q.getCourseId());       // 收集所属课程id
            // 注意：小节(section)也是挂在课程目录上的节点，因此把小节id也归入章节id集合统一查询
            chapterIds.add(q.getChapterId());
            chapterIds.add(q.getSectionId());
        }

        // 3.2 批量查询用户信息，构建 id -> 用户 的映射（供后续按作者id取姓名）
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(users)) {
            // 使用 Collectors.toMap 以用户id为key、用户对象为value转成Map
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 3.3 批量查询课程信息，构建 id -> 课程 的映射（供后续取课程名和分类id）
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cInfos.size());
        if (CollUtils.isNotEmpty(cInfos)) {
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }

        // 3.4 批量查询章节（目录）信息，构建 id -> 章节名 的映射（章节名和小节名都从这里取）
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(chapterIds);
        Map<Long, String> cataMap = new HashMap<>(catas.size());
        if (CollUtils.isNotEmpty(catas)) {
            cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }

        /* ==================== 阶段四：组装VO分页结果 ==================== */
        // 预分配容量，避免扩容开销（容量=本页记录数）
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            // 4.1 将PO属性拷贝到VO（按同名属性自动映射）
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            voList.add(vo);

            // 4.2 补全作者姓名；作者查不到时跳过（保留null，前端自行兜底）
            UserDTO user = userMap.get(q.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
            }

            // 4.3 补全课程名与分类名；一并借助课程信息中携带的分类id列表解析分类名称
            CourseSimpleInfoDTO cInfo = cInfoMap.get(q.getCourseId());
            if (cInfo != null) {
                vo.setCourseName(cInfo.getName());
                // categoryCache 缓存分类，避免逐条远程查询分类导致性能问题
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }

            // 4.4 补全章节名和小节名；名称不存在时兜底为空字符串
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(), ""));
        }

        // 5. 封装成通用分页对象返回（分页信息 + 本页VO列表）
        return PageDTO.of(page, voList);
    }
}