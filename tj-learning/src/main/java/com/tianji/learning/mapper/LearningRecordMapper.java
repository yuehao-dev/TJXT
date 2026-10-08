package com.tianji.learning.mapper;

import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2026-09-20
 */
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    /**
     * 统计用户在指定时间范围内学习的小节数量
     * @param userId
     * @param begin
     * @param end
     * @return
     */
    List<IdAndNumDTO> countLearnedSections(@Param("userId") Long userId,
                                           @Param("begin") LocalDateTime begin,
                                           @Param("end") LocalDateTime end);
}
