CREATE TABLE IF NOT EXISTS symphony_yuhu_book (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuBookTitle VARCHAR(255),
  yuhuBookIntro TEXT,
  yuhuBookAuthorProfileId VARCHAR(19),
  yuhuBookStatus VARCHAR(32),
  yuhuBookCoverURL VARCHAR(512),
  yuhuBookWordCount INT,
  yuhuBookLatestChapterId VARCHAR(19),
  yuhuBookScore DOUBLE,
  yuhuBookCreated BIGINT,
  yuhuBookUpdated BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_volume (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuVolumeBookId VARCHAR(19),
  yuhuVolumeIndex INT,
  yuhuVolumeTitle VARCHAR(255),
  yuhuVolumeIntro TEXT,
  yuhuVolumeCreated BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_chapter (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuChapterBookId VARCHAR(19),
  yuhuChapterVolumeId VARCHAR(19),
  yuhuChapterIndex INT,
  yuhuChapterTitle VARCHAR(255),
  yuhuChapterContentMD MEDIUMTEXT,
  yuhuChapterContentHTML MEDIUMTEXT,
  yuhuChapterWordCount INT,
  yuhuChapterPublishedAt BIGINT,
  yuhuChapterIsPaid TINYINT(1),
  yuhuChapterStatus VARCHAR(16),
  yuhuChapterToC TEXT,
  yuhuChapterParagraphAnchors TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_user_profile (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuUserProfileLinkedUserId VARCHAR(19),
  yuhuUserProfileRole VARCHAR(16),
  yuhuUserProfileNickname VARCHAR(64),
  yuhuUserProfileIntro TEXT,
  yuhuUserProfileAvatarURL VARCHAR(512),
  yuhuUserProfileDisplayOverrideEnabled TINYINT(1),
  yuhuUserProfilePrefTheme VARCHAR(16),
  yuhuUserProfilePrefFontSize INT,
  yuhuUserProfilePrefPageWidth INT,
  yuhuUserProfileCreated BIGINT,
  yuhuUserProfileUpdated BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_bookmark (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuBookmarkProfileId VARCHAR(19),
  yuhuBookmarkBookId VARCHAR(19),
  yuhuBookmarkChapterId VARCHAR(19),
  yuhuBookmarkParagraphId VARCHAR(64),
  yuhuBookmarkOffset INT,
  yuhuBookmarkCreated BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_reading_progress (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuReadingProgressProfileId VARCHAR(19),
  yuhuReadingProgressBookId VARCHAR(19),
  yuhuReadingProgressChapterId VARCHAR(19),
  yuhuReadingProgressPercent INT,
  yuhuReadingProgressUpdated BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_comment (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuCommentProfileId VARCHAR(19),
  yuhuCommentBookId VARCHAR(19),
  yuhuCommentChapterId VARCHAR(19),
  yuhuCommentParagraphId VARCHAR(64),
  yuhuCommentContent TEXT,
  yuhuCommentCreated BIGINT,
  yuhuCommentStatus VARCHAR(16),
  yuhuCommentLikeCnt INT,
  yuhuCommentDislikeCnt INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_tag (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuTagName VARCHAR(64),
  yuhuTagAliasEN VARCHAR(64),
  yuhuTagDesc VARCHAR(512)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_book_tag (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuBookTagBookId VARCHAR(19),
  yuhuBookTagTagId VARCHAR(19)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_subscription (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuSubscriptionProfileId VARCHAR(19),
  yuhuSubscriptionBookId VARCHAR(19),
  yuhuSubscriptionCreated BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symphony_yuhu_vote (
  oId VARCHAR(19) PRIMARY KEY,
  yuhuVoteProfileId VARCHAR(19),
  yuhuVoteTargetType INT,
  yuhuVoteTargetId VARCHAR(19),
  yuhuVoteType INT,
  yuhuVoteValue INT,
  yuhuVotePointsCost INT,
  yuhuVoteCreated BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
