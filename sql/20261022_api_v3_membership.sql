-- Apply before enabling v3 membership purchases on an existing database.
-- Parent transfer rows serialize retry repair; this index keeps each lookup
-- restricted to that transfer's recipients instead of scanning all history.
ALTER TABLE `symphony_pointtransfer_redc`
  ADD INDEX `idx_pointtransfer_redc_raw_user` (`rawId`, `userId`);
