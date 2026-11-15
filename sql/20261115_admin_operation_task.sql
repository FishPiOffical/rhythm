-- 后台外部操作状态。表前缀按部署 jdbc.tablePrefix 调整；先迁移再启动新代码。
CREATE TABLE IF NOT EXISTS `symphony_admin_operation_task` (
  `oId` varchar(64) NOT NULL,
  `actorId` varchar(19) NOT NULL,
  `kind` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL,
  `payload` mediumtext NOT NULL,
  `result` mediumtext NOT NULL,
  `createdAt` bigint NOT NULL,
  `updatedAt` bigint NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_admin_task_queue` (`status`, `createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 调度锁只用于短事务选取任务，不在外部调用期间持有数据库事务。
INSERT IGNORE INTO `symphony_admin_operation_task`
(`oId`,`actorId`,`kind`,`status`,`payload`,`result`,`createdAt`,`updatedAt`)
VALUES ('dispatcher','','DISPATCHER','INTERNAL','{}','{}',0,0);
