-- 身价直接统计已保存的转手记录，按当前页的搭子编号查找。
SET @partner_value_index = IF(EXISTS (
  SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='symphony_partner_trade' AND index_name='idx_partner_trade_value'
), 'DO 0', 'ALTER TABLE symphony_partner_trade ADD INDEX idx_partner_trade_value (peer,buyer,createdAt,seller)');
PREPARE partner_index FROM @partner_value_index;
EXECUTE partner_index;
DEALLOCATE PREPARE partner_index;
