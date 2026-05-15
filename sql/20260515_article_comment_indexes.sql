ALTER TABLE `symphony_article`
  ADD COLUMN `articleHotScore` INT
    GENERATED ALWAYS AS (
      COALESCE(`articleThankCnt`, 0)
      + COALESCE(`articleGoodCnt`, 0)
      + COALESCE(`articleCollectCnt`, 0)
      + COALESCE(`articleWatchCnt`, 0)
      - COALESCE(`articleBadCnt`, 0)
    ) STORED,
  ADD INDEX `idx_article_author_status_show_oid`
    (`articleAuthorId`, `articleStatus`, `articleShowInList`, `oId`),
  ADD INDEX `idx_article_status_show_oid`
    (`articleStatus`, `articleShowInList`, `oId`),
  ADD INDEX `idx_article_hot_status_show_score_oid`
    (`articleStatus`, `articleShowInList`, `articleHotScore`, `oId`);

ALTER TABLE `symphony_comment`
  ADD INDEX `idx_cmt_article_oid`
    (`commentOnArticleId`, `oId`),
  ADD INDEX `idx_cmt_article_score_oid`
    (`commentOnArticleId`, `commentScore`, `oId`),
  ADD INDEX `idx_cmt_article_parent_oid`
    (`commentOnArticleId`, `commentOriginalCommentId`, `oId`),
  ADD INDEX `idx_cmt_article_parent_score_oid`
    (`commentOnArticleId`, `commentOriginalCommentId`, `commentScore`, `oId`),
  ADD INDEX `idx_cmt_original_status_oid`
    (`commentOriginalCommentId`, `commentStatus`, `oId`);
