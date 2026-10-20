package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyFormDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-08
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    /**
     * 新增回答或评论
     * @param replyFormDTO 回答或评论表单
     */
    void saveReply(ReplyFormDTO replyFormDTO);

    /**
     * 分页查询回答或评论
     * @param query 查询条件
     * @return 回答或评论分页结果
     */
    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query);
}
