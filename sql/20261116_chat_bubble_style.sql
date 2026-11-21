-- 执行后加入四个未发布草稿。非默认 jdbc.tablePrefix 请替换 symphony_。
CREATE TABLE IF NOT EXISTS `symphony_chat_bubble_style` (
 `oId` VARCHAR(32) NOT NULL,
 `draftJson` VARCHAR(3072) NOT NULL,
 `publishedJson` VARCHAR(3072) NOT NULL,
 `revision` INT NOT NULL,
 `publishedRevision` INT NOT NULL,
 `active` INT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT IGNORE INTO `symphony_chat_bubble_style` (oId,draftJson,publishedJson,revision,publishedRevision,active) VALUES
 ('bubble-custom-momota','{"name":"河湾手帐","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#fff7f0","ownBackground":"#faebde","border":"#caa589","ink":"#986647","text":"#252a27"},"dark":{"background":"#322924","ownBackground":"#3d302a","border":"#967257","ink":"#caa589","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"paw","image":""}}','',1,0,0),
 ('bubble-custom-hexiaotun','{"name":"荷叶午后","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#f7faed","ownBackground":"#eaf1d7","border":"#a6ba80","ink":"#5e793e","text":"#252a27"},"dark":{"background":"#293226","ownBackground":"#303b29","border":"#75875b","ink":"#a6ba80","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"leaf","image":""}}','',1,0,0),
 ('bubble-custom-yubingmiao','{"name":"鱼饼铃铛","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#fff8eb","ownBackground":"#fff0d3","border":"#e8b870","ink":"#9a631c","text":"#252a27"},"dark":{"background":"#342c23","ownBackground":"#423425","border":"#a77e4f","ink":"#e8b870","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"bell","image":""}}','',1,0,0),
 ('bubble-custom-paixiaoyu','{"name":"派派浪花","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#fff5ed","ownBackground":"#ffe9d9","border":"#e8a56c","ink":"#aa521d","text":"#252a27"},"dark":{"background":"#3a2a24","ownBackground":"#442e24","border":"#a9724d","ink":"#e8a56c","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"fish","image":""}}','',1,0,0);
