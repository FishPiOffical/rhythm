-- 依赖 20261112_appearance_shop.sql。沿用旧樱花商品 ID，保留原价设置、购买记录和租期。
START TRANSACTION;
INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES
    ('bubble-night-sakura',256,0,1,1),
    ('frame-night-sakura',128,0,1,1),
    ('bubble-sakura-moon',256,0,1,1),
    ('frame-sakura-moon',128,0,1,1);
COMMIT;
