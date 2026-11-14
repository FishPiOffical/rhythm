-- Apply after 20260908_fish_game_remove_oauth.sql. Values remain empty for legacy submissions.
ALTER TABLE `symphony_fish_game`
  ADD COLUMN `fishGameOauthLoginUrl` VARCHAR(4096) NOT NULL DEFAULT '',
  ADD COLUMN `fishGamePendingOauthLoginUrl` VARCHAR(4096) NOT NULL DEFAULT '',
  ADD KEY `idx_fish_game_pending_url` (`fishGamePendingUrl`(191),`fishGameEditPending`);

-- Internal namespace row serializes v3 submission and edit URL reservations; not a business setting.
INSERT INTO `symphony_option` (`oId`,`optionValue`,`optionCategory`)
VALUES ('apiV3FishGameSubmission','','internal')
ON DUPLICATE KEY UPDATE `oId`=`oId`;
