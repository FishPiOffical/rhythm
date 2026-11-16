-- 依赖 20261112_appearance_shop.sql。保留已有报价、下架状态及全部购买记录。
START TRANSACTION;
INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES
    ('bubble-night-sakura',256,0,1,1),
    ('frame-night-sakura',128,0,1,1);
COMMIT;
