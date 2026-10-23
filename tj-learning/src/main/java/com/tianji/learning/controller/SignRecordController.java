package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "签到相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("sign-records")
public class SignRecordController {
        private final ISignRecordService signRecordService;

        @PostMapping("/sign")
        @ApiOperation("签到功能接口")
        public SignResultVO addSignRecord() {
                return signRecordService.addSignRecord();
        }

        /**
         * 获取签到记录
         * @return
         */
        @GetMapping
        @ApiOperation("获取签到记录接口")
        public int[] getSignRecord() {
                return signRecordService.getSignRecord();
        }

}
