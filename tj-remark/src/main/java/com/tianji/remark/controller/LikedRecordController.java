package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-20
 */
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Api(tags = "点赞记录接口")
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;

    @PostMapping
    @ApiOperation(value = "点赞或取消点赞")
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO recordDTO) {
        likedRecordService.addLikeRecord(recordDTO);
    }

    @GetMapping("/list")
    @ApiOperation(value = "查询指定业务ID点赞记录")
    public Set<Long> isBizLiked(@RequestParam List<Long> bizIds) {
        return likedRecordService.isBizLiked(bizIds);
    }

}
