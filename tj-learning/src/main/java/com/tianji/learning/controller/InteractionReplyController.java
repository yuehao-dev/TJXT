package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyFormDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-08
 */
@RestController
@RequestMapping("/replies")
@Api(tags = "回答或评论相关接口")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @ApiOperation("新增回答或评论")
    @PostMapping
    public void saveReply(@RequestBody @Valid ReplyFormDTO replyFormDTO) {
        replyService.saveReply(replyFormDTO);
    }

    @ApiOperation("分页查询回答或评论")
    @GetMapping("page")
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        return replyService.queryReplyPage(query);
    }
}
