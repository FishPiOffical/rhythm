-- 已存在字段时请跳过这一句。
ALTER TABLE `symphony_long_article_column`
  ADD COLUMN `columnOrder` INT NOT NULL DEFAULT 0 AFTER `columnStatus`;
