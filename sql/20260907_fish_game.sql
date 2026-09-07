CREATE TABLE IF NOT EXISTS `symphony_fish_game` (
  `oId` VARCHAR(19) NOT NULL,
  `fishGameName` VARCHAR(80) NOT NULL,
  `fishGameDescription` VARCHAR(1000) NOT NULL,
  `fishGameUrl` VARCHAR(512) NOT NULL,
  `fishGameIconUrl` VARCHAR(512) NOT NULL,
  `fishGameOauthUrl` VARCHAR(512) NOT NULL,
  `fishGameOauthVerified` TINYINT NOT NULL DEFAULT 0,
  `fishGameAuthorId` VARCHAR(19) NOT NULL,
  `fishGameStatus` INT NOT NULL DEFAULT 0,
  `fishGameEditPending` TINYINT NOT NULL DEFAULT 0,
  `fishGamePendingName` VARCHAR(80) NOT NULL DEFAULT '',
  `fishGamePendingDescription` VARCHAR(1000) NOT NULL DEFAULT '',
  `fishGamePendingUrl` VARCHAR(512) NOT NULL DEFAULT '',
  `fishGamePendingIconUrl` VARCHAR(512) NOT NULL DEFAULT '',
  `fishGamePendingOauthUrl` VARCHAR(512) NOT NULL DEFAULT '',
  `fishGameLikeCount` INT NOT NULL DEFAULT 0,
  `fishGameDislikeCount` INT NOT NULL DEFAULT 0,
  `fishGameCreatedTime` BIGINT NOT NULL,
  `fishGameUpdatedTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_fish_game_url` (`fishGameUrl`),
  KEY `idx_fish_game_status_like` (`fishGameStatus`, `fishGameLikeCount`, `fishGameCreatedTime`),
  KEY `idx_fish_game_author` (`fishGameAuthorId`, `fishGameStatus`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_fish_game_vote` (
  `oId` VARCHAR(19) NOT NULL,
  `fishGameVoteGameId` VARCHAR(19) NOT NULL,
  `fishGameVoteUserId` VARCHAR(19) NOT NULL,
  `fishGameVoteValue` VARCHAR(16) NOT NULL,
  `fishGameVoteCreatedTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_fish_game_vote_user_game` (`fishGameVoteUserId`, `fishGameVoteGameId`),
  KEY `idx_fish_game_vote_game` (`fishGameVoteGameId`, `fishGameVoteValue`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_fish_game_comment` (
  `oId` VARCHAR(19) NOT NULL,
  `fishGameCommentGameId` VARCHAR(19) NOT NULL,
  `fishGameCommentAuthorId` VARCHAR(19) NOT NULL,
  `fishGameCommentContent` VARCHAR(500) NOT NULL,
  `fishGameCommentCreatedTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_fish_game_comment_game_time` (`fishGameCommentGameId`, `fishGameCommentCreatedTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
