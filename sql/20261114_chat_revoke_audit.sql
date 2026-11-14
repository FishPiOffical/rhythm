-- 聊天室撤回保存完整消息外壳；审计容量不能小于 chat_room.content。
-- 更新服务前在已初始化的数据库执行，先备份并按实际配置替换 symphony_ 表前缀。
-- 已是 MEDIUMTEXT/LONGTEXT 时跳过，避免重复扩列或降低现有容量。
-- 扩列保留既有字符集、排序规则、空值、默认值及字段注释；不改写原日志。
SET @chat_revoke_audit_ddl = (
  SELECT IF(DATA_TYPE IN ('mediumtext', 'longtext'), 'SELECT 1',
    CONCAT('ALTER TABLE `symphony_logs` MODIFY COLUMN `data` MEDIUMTEXT',
      IF(CHARACTER_SET_NAME IS NULL, '', CONCAT(' CHARACTER SET ', CHARACTER_SET_NAME)),
      IF(COLLATION_NAME IS NULL, '', CONCAT(' COLLATE ', COLLATION_NAME)),
      IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL'),
      IF(COLUMN_DEFAULT IS NULL, '', CONCAT(' DEFAULT (',
        IF(LOCATE('DEFAULT_GENERATED', EXTRA) > 0, COLUMN_DEFAULT, QUOTE(COLUMN_DEFAULT)), ')')),
      ' COMMENT ', QUOTE(COLUMN_COMMENT)))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_logs' AND COLUMN_NAME = 'data'
);
-- 缺少基础表或字段时 PREPARE 明确失败，不能把未执行迁移当成成功。
PREPARE chat_revoke_audit_stmt FROM @chat_revoke_audit_ddl;
EXECUTE chat_revoke_audit_stmt;
DEALLOCATE PREPARE chat_revoke_audit_stmt;
