-- 只读汇总最近七天已经结束的停车。同一次收车重试只对应一条结算。
SET @parking_since = (UNIX_TIMESTAMP() - 7 * 86400) * 1000;

SELECT car, ending, COUNT(*) AS settlements, COUNT(DISTINCT driverId) AS drivers,
       SUM(paid) AS credited, ROUND(AVG(LEAST(endedAt-startedAt, 28800000)) / 60000, 1) AS mean_paid_minutes
FROM symphony_parking_session
WHERE endedAt >= @parking_since
GROUP BY car, ending ORDER BY credited DESC;

-- 供人工复核的行为样本。正常通勤、相约停车也可能呈现规律，不能据此直接处罚玩家。
SELECT driverId, COUNT(*) AS starts, COUNT(DISTINCT hostId) AS different_hosts,
       SUM(restartGapMs BETWEEN 0 AND 60000) AS quick_restarts,
       SUM(verified) AS verified_starts,
       ROUND(AVG(IF(restartGapMs>=0, restartGapMs/1000, NULL)), 1) AS mean_restart_seconds
FROM symphony_parking_session
WHERE startedAt >= @parking_since
GROUP BY driverId ORDER BY quick_restarts DESC, starts DESC LIMIT 100;

-- 服务日志 parking_event 的 command 为摘要；统计唯一操作按 actor、action、command 去重。
-- outcome 区分 committed、replayed、业务错误码以及要求验证或限流；报告无需凭据、IP、设备指纹。
