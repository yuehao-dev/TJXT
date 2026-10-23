package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.service.impl.PointsRecordServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-10-21
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
@Api(tags = "积分相关接口")
public class PointsRecordController {

    private final IPointsRecordService recordService;


    @GetMapping("today")
    @ApiOperation("查询今日积分统计")
    public List<PointsStatisticsVO> queryMyPointsToday(){
        return recordService.queryMyPointsToday();
    }
}
