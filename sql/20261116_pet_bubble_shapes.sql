-- 先执行 20261116_chat_bubble_style.sql，再升级四个原始草稿。非默认前缀请替换 symphony_。
-- 仅更新仍是初始版本的未发布草稿，保留运营编辑、发布配置、已购权益和已发消息。

UPDATE `symphony_chat_bubble_style`
 SET draftJson='{"name":"河湾手帐","access":"points","price7":128,"price30":0,"style":{"light":{"background":"#f8e8d1","ownBackground":"#f8e8d1","border":"#b18366","ink":"#b18366","text":"#473126"},"dark":{"background":"#3a2a22","ownBackground":"#3a2a22","border":"#a37657","ink":"#a37657","text":"#fff0db"},"radius":14,"borderWidth":1,"shadow":false,"motif":"paw","image":"https://file.fishpi.cn/yui/files/2026/11/13/271706.webp","shape":"memo-v1"}}', revision=revision+1
 WHERE oId='bubble-custom-momota' AND revision=1 AND publishedRevision=0 AND active=0
   AND draftJson='{"name":"河湾手帐","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#fff7f0","ownBackground":"#faebde","border":"#caa589","ink":"#986647","text":"#252a27"},"dark":{"background":"#322924","ownBackground":"#3d302a","border":"#967257","ink":"#caa589","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"paw","image":""}}';

UPDATE `symphony_chat_bubble_style`
 SET draftJson='{"name":"荷叶午后","access":"points","price7":128,"price30":0,"style":{"light":{"background":"#e0edbf","ownBackground":"#e0edbf","border":"#829a59","ink":"#829a59","text":"#314525"},"dark":{"background":"#2d3927","ownBackground":"#2d3927","border":"#839661","ink":"#839661","text":"#edf4dd"},"radius":14,"borderWidth":1,"shadow":false,"motif":"leaf","image":"https://file.fishpi.cn/yui/files/2026/11/13/271705.webp","shape":"lotus-v1"}}', revision=revision+1
 WHERE oId='bubble-custom-hexiaotun' AND revision=1 AND publishedRevision=0 AND active=0
   AND draftJson='{"name":"荷叶午后","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#f7faed","ownBackground":"#eaf1d7","border":"#a6ba80","ink":"#5e793e","text":"#252a27"},"dark":{"background":"#293226","ownBackground":"#303b29","border":"#75875b","ink":"#a6ba80","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"leaf","image":""}}';

UPDATE `symphony_chat_bubble_style`
 SET draftJson='{"name":"鱼饼铃铛","access":"points","price7":128,"price30":0,"style":{"light":{"background":"#eee0f3","ownBackground":"#eee0f3","border":"#ab8eb3","ink":"#ab8eb3","text":"#432e49"},"dark":{"background":"#382a40","ownBackground":"#382a40","border":"#a38bb1","ink":"#a38bb1","text":"#f8eafa"},"radius":14,"borderWidth":1,"shadow":false,"motif":"bell","image":"https://file.fishpi.cn/yui/files/2026/11/13/271708.webp","shape":"cat-v1"}}', revision=revision+1
 WHERE oId='bubble-custom-yubingmiao' AND revision=1 AND publishedRevision=0 AND active=0
   AND draftJson='{"name":"鱼饼铃铛","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#fff8eb","ownBackground":"#fff0d3","border":"#e8b870","ink":"#9a631c","text":"#252a27"},"dark":{"background":"#342c23","ownBackground":"#423425","border":"#a77e4f","ink":"#e8b870","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"bell","image":""}}';

UPDATE `symphony_chat_bubble_style`
 SET draftJson='{"name":"派派浪花","access":"points","price7":128,"price30":0,"style":{"light":{"background":"#ffd6be","ownBackground":"#ffd6be","border":"#c5825b","ink":"#c5825b","text":"#542d23"},"dark":{"background":"#472e25","ownBackground":"#472e25","border":"#b97b52","ink":"#b97b52","text":"#ffe9dd"},"radius":14,"borderWidth":1,"shadow":false,"motif":"fish","image":"https://file.fishpi.cn/yui/files/2026/11/13/271707.webp","shape":"fish-v1"}}', revision=revision+1
 WHERE oId='bubble-custom-paixiaoyu' AND revision=1 AND publishedRevision=0 AND active=0
   AND draftJson='{"name":"派派浪花","access":"free","price7":0,"price30":0,"style":{"light":{"background":"#fff5ed","ownBackground":"#ffe9d9","border":"#e8a56c","ink":"#aa521d","text":"#252a27"},"dark":{"background":"#3a2a24","ownBackground":"#442e24","border":"#a9724d","ink":"#e8a56c","text":"#f8f0e9"},"radius":14,"borderWidth":1,"shadow":true,"motif":"fish","image":""}}';
