ALTER TABLE `symphony_comment`
  ADD COLUMN `commentType` INT NOT NULL DEFAULT 0,
  ADD COLUMN `commentParagraphId` VARCHAR(64) NOT NULL DEFAULT '',
  ADD COLUMN `commentParagraphKind` VARCHAR(16) NOT NULL DEFAULT '',
  ADD COLUMN `commentParagraphIndex` INT NOT NULL DEFAULT -1,
  ADD COLUMN `commentParagraphSnapshot` VARCHAR(1024) NOT NULL DEFAULT '',
  ADD COLUMN `commentParagraphStatus` INT NOT NULL DEFAULT 0,
  ADD INDEX `idx_cmt_article_type_paragraph_status_oid`
    (`commentOnArticleId`, `commentType`, `commentParagraphId`, `commentParagraphStatus`, `commentCreateTime`, `oId`);
