package com.tianji.learning.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 回答或评论分页查询条件
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "回答或评论分页查询条件")
public class ReplyPageQuery extends PageQuery {

    @ApiModelProperty(value = "问题id，查询问题下的回答时使用")
    private Long questionId;

    @ApiModelProperty(value = "回答id，查询某个回答下的评论时使用；不传则查询回答列表")
    private Long answerId;
}
