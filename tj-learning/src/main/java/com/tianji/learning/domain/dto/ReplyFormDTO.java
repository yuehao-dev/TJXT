package com.tianji.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

/**
 * 回答或评论表单信息
 * 对应视频中的 ReplyDTO
 */
@Data
@ApiModel(description = "回答或评论表单信息")
public class ReplyFormDTO {

    @ApiModelProperty("问题id，评论时可不填，会从上级回答中推导")
    private Long questionId;

    @ApiModelProperty("上级回答id，为空或0表示这是一条回答，否则表示评论")
    private Long answerId;

    @ApiModelProperty("回答或评论的内容")
    @NotBlank(message = "回答或评论内容不能为空")
    @Length(max = 255, message = "回答或评论内容不能超过255个字符")
    private String content;

    @ApiModelProperty("是否匿名")
    private Boolean anonymity;

    @ApiModelProperty("回复的目标回复id，可为空")
    private Long targetReplyId;

    @ApiModelProperty("回复的目标用户id，可为空，服务端会根据目标回复校验")
    private Long targetUserId;

    @ApiModelProperty("是否学员提交，true时把问题状态改为未查看")
    private Boolean isStudent;
}
