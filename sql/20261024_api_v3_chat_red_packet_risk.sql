-- Apply after 20261024_api_v3_chat_red_packet.sql and before enabling risky packet types.
-- Existing safe packets keep their original settlement and acquire neutral gesture/refund fields.
-- One-time ALTER migration; skip an ADD COLUMN clause if that column has already been applied.
ALTER TABLE `symphony_chat_red_packet`
 MODIFY COLUMN `type` VARCHAR(17) NOT NULL,
 ADD COLUMN `senderGesture` INT NOT NULL DEFAULT -1;
ALTER TABLE `symphony_chat_red_packet_slot`
 ADD COLUMN `gesture` INT NOT NULL DEFAULT -1,
 ADD COLUMN `senderRefundPoints` INT NOT NULL DEFAULT 0,
 ADD COLUMN `senderTransferId` VARCHAR(19) NOT NULL DEFAULT '';
