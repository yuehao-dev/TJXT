package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.Constant;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-08
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;

    /**
     * 新增回答或评论
     * 业务规则：
     * 1. answerId 为空或0：这是一条回答，需要累加问题的回答数并更新最近回答id
     * 2. answerId 不为空：这是一条评论，需要累加上级回答的评论数，并记录被回复的目标用户
     * 3. 学员提交的回答或评论，需要把问题状态改回"未查看"，提醒管理员处理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReply(ReplyFormDTO dto) {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }
        if (dto == null || StringUtils.isBlank(dto.getContent())) {
            throw new BadRequestException("回答或评论内容不能为空");
        }

        Long answerId = dto.getAnswerId();
        boolean isComment = answerId != null && answerId > 0;
        Long questionId = dto.getQuestionId();

        // 2. 封装回答或评论数据
        InteractionReply reply = new InteractionReply();
        reply.setUserId(userId);
        reply.setContent(dto.getContent().trim());
        reply.setAnonymity(Boolean.TRUE.equals(dto.getAnonymity()));
        reply.setHidden(false);
        reply.setReplyTimes(0);
        reply.setLikedTimes(0);
        // 回答的 answerId 固定为0，评论为上级回答id
        reply.setAnswerId(isComment ? answerId : 0L);
        // 默认没有回复目标，评论时再补充
        reply.setTargetUserId(0L);
        reply.setTargetReplyId(0L);

        if (isComment) {
            // 3.1 评论：校验上级回答，并补全问题id、目标用户
            InteractionReply parent = getById(answerId);
            if (parent == null || Boolean.TRUE.equals(parent.getHidden())) {
                throw new BadRequestException("要评论的回答不存在");
            }
            if (questionId == null) {
                questionId = parent.getQuestionId();
            } else if (!questionId.equals(parent.getQuestionId())) {
                throw new BadRequestException("该回答不属于当前问题");
            }

            Long targetReplyId = dto.getTargetReplyId();
            if (targetReplyId != null && targetReplyId > 0) {
                // 回复某一条具体的评论
                InteractionReply target = getById(targetReplyId);
                if (target == null) {
                    throw new BadRequestException("要回复的评论不存在");
                }
                reply.setTargetReplyId(target.getId());
                reply.setTargetUserId(target.getUserId());
            } else {
                // 默认回复上级回答的作者
                reply.setTargetReplyId(parent.getId());
                reply.setTargetUserId(parent.getUserId());
            }
        } else {
            // 3.2 回答：必须指定问题，且问题存在
            if (questionId == null) {
                throw new BadRequestException("问题id不能为空");
            }
            InteractionQuestion question = questionMapper.selectById(questionId);
            if (question == null) {
                throw new BadRequestException("问题不存在");
            }
        }
        reply.setQuestionId(questionId);

        // 4. 保存回答或评论
        save(reply);

        // 5. 维护冗余统计字段
        if (isComment) {
            // 评论：累加上级回答的评论数量
            lambdaUpdate()
                    .eq(InteractionReply::getId, answerId)
                    .setSql("reply_times = reply_times + 1")
                    .update();
        } else {
            // 回答：更新问题的最新回答id，并累加回答数量
            questionMapper.update(null, new LambdaUpdateWrapper<InteractionQuestion>()
                    .eq(InteractionQuestion::getId, questionId)
                    .set(InteractionQuestion::getLatestAnswerId, reply.getId())
                    .setSql("answer_times = answer_times + 1"));
        }

        // 6. 学员提交后，问题状态改回未查看
        if (Boolean.TRUE.equals(dto.getIsStudent())) {
            questionMapper.update(null, new LambdaUpdateWrapper<InteractionQuestion>()
                    .eq(InteractionQuestion::getId, questionId)
                    .set(InteractionQuestion::getStatus, QuestionStatus.UN_CHECK));
        }
    }

    /**
     * 分页查询回答或评论
     * 1. 不传 answerId：查询问题下的一级回答（answer_id = 0）
     * 2. 传 answerId：查询该回答下的评论
     */
    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        // 1. 参数校验：问题id和回答id至少要有一个
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }
        if (answerId != null && answerId <= 0) {
            answerId = null;
        }

        // 2. 分页查询：点赞数倒序，点赞数相同按时间正序
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, answerId == null ? 0L : answerId)
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPage(
                        new OrderItem(Constant.DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(Constant.DATA_FIELD_NAME_CREATE_TIME, true)));

        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3. 收集需要查询的用户id和被回复的回复id
        Set<Long> userIds = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();
        Set<Long> targetUserIdsFromField = new HashSet<>();
        for (InteractionReply record : records) {
            // 匿名回答不展示用户信息，也不需要查询
            if (!Boolean.TRUE.equals(record.getAnonymity())) {
                addId(userIds, record.getUserId());
                if (record.getTargetReplyId() == null || record.getTargetReplyId() <= 0) {
                    // 没有目标回复id的评论老数据，仍然按目标用户id查询；
                    // 一级回答不展示被回复人，避免历史脏数据带出无关昵称
                    if (record.getAnswerId() != null && record.getAnswerId() > 0) {
                        addId(targetUserIdsFromField, record.getTargetUserId());
                    }
                }
            }
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                targetReplyIds.add(record.getTargetReplyId());
            }
        }

        // 4. 查询目标回复，只有非匿名回复才展示作者昵称
        Map<Long, Long> targetReplyUserMap = new HashMap<>();
        if (CollUtils.isNotEmpty(targetReplyIds)) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            for (InteractionReply target : targetReplies) {
                if (!Boolean.TRUE.equals(target.getAnonymity())) {
                    targetReplyUserMap.put(target.getId(), target.getUserId());
                    addId(userIds, target.getUserId());
                }
            }
        }
        userIds.addAll(targetUserIdsFromField);
        userIds.remove(null);
        userIds.remove(0L);

        // 5. 批量查询用户信息，避免N+1
        Map<Long, UserDTO> userMap = new HashMap<>();
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            if (CollUtils.isNotEmpty(users)) {
                userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
            }
        }

        // 6. 组装VO
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);

            // 6.1 回答者信息，匿名时不展示
            if (!Boolean.TRUE.equals(record.getAnonymity())) {
                UserDTO user = userMap.get(record.getUserId());
                if (user != null) {
                    vo.setUserName(user.getName());
                    vo.setUserIcon(user.getIcon());
                    vo.setUserType(user.getType());
                }
            }

            // 6.2 被回复者昵称：优先按目标回复的作者取，匿名目标不展示
            Long targetUserId = null;
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                targetUserId = targetReplyUserMap.get(record.getTargetReplyId());
            } else if (record.getAnswerId() != null && record.getAnswerId() > 0
                    && record.getTargetUserId() != null && record.getTargetUserId() > 0) {
                // 评论但没有目标回复id的老数据，兜底用目标用户id
                targetUserId = record.getTargetUserId();
            }
            if (targetUserId != null) {
                UserDTO targetUser = userMap.get(targetUserId);
                if (targetUser != null) {
                    vo.setTargetUserName(targetUser.getName());
                }
            }

            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }

    private void addId(Set<Long> ids, Long id) {
        if (id != null && id > 0) {
            ids.add(id);
        }
    }
}
