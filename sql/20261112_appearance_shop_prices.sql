-- 已建库执行此调价迁移；新库种子价格已同步。非默认库前缀请替换 symphony_。
-- 仅修改指定商品的价格和报价版本，不改变已有购买记录、有效期和装备。
START TRANSACTION;

UPDATE `symphony_appearance_product`
SET `price7` = 256, `price30` = 896, `revision` = `revision` + 1
WHERE `oId` IN ('mount-paipai-scooter', 'mount-paper-plane', 'mount-deepsea-submarine')
  AND (`price7` <> 256 OR `price30` <> 896);

UPDATE `symphony_appearance_product`
SET `price7` = 64, `price30` = 224, `revision` = `revision` + 1
WHERE `oId` IN ('bubble-sea-soda', 'bubble-sunset-letter', 'bubble-star-route',
               'frame-tide', 'frame-paipai-stamp', 'frame-star-ring')
  AND (`price7` <> 64 OR `price30` <> 224);

COMMIT;
