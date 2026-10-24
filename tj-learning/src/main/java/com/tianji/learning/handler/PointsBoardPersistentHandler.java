package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate redisTemplate;

    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        //1.获取上月的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        //2.查询赛季Id
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            //没有上月的赛季
            return;
        }
        //3.创建表
        pointsBoardService.createPointsBoardTableBySeason(season);

    }


    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB() {
        //1.获取上月的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        //2.计算动态表名
        //2.1查询赛季信息
        Integer season = seasonService.querySeasonByTime(time);
        //2.2存入ThreadLocal
        TableInfoContext.setInfo("points_board_" + season);
        //3.查询榜单数据
        //3.1拼接KEY
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_DATE_SUFFIX_FORMATTER);
        //3.2查询数据
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1;
        int pageSize = 1000;
        while (true) {
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                //没有数据
                break;
            }
            //4.持久化到数据库
            //4.1把排名信息写入id
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            //4.2持久化
            pointsBoardService.saveBatch(boardList);
            //5.翻页
            pageNo += total;

        }
        //6.删除ThreadLocal
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        //1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        //2.计算key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_DATE_SUFFIX_FORMATTER);

        //3.删除
        redisTemplate.unlink(key);
    }

}
