package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回答或评论信息
 */
@Data
@ApiModel(description = "回答或评论信息")
public class ReplyVO {

    @ApiModelProperty("回答或评论id")
    private Long id;

    @ApiModelProperty("所属问题id")
    private Long questionId;

    @ApiModelProperty("上级回答id，0表示当前记录是回答")
    private Long answerId;

    @ApiModelProperty("回答者id")
    private Long userId;

    @ApiModelProperty("回答或评论内容")
    private String content;

    @ApiModelProperty("回复的目标用户id")
    private Long targetUserId;

    @ApiModelProperty("回复的目标回复id")
    private Long targetReplyId;

    @ApiModelProperty("该回答下的评论数量")
    private Integer replyTimes;

    @ApiModelProperty("点赞数量")
    private Integer likedTimes;

    @ApiModelProperty("是否被隐藏")
    private Boolean hidden;

    @ApiModelProperty("是否匿名")
    private Boolean anonymity;

    @ApiModelProperty(value = "创建时间", example = "2022-7-18 19:52:36")
    private LocalDateTime createTime;

    @ApiModelProperty("回答者昵称，匿名时为空")
    private String userName;

    @ApiModelProperty("回答者头像，匿名时为空")
    private String userIcon;

    @ApiModelProperty("回答者类型：1-其他员工，2-普通学员，3-老师")
    private Integer userType;

    @ApiModelProperty("被回复用户昵称")
    private String targetUserName;
}
