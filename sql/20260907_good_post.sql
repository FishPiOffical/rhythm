ALTER TABLE `symphony_article`
  ADD COLUMN `isGoodArticle` VARCHAR(3) NOT NULL DEFAULT '' AFTER `articlePerfect`;
