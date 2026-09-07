<#--

    Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
    Modified version from Symphony, Thanks Symphony :)
    Copyright (C) 2012-present, b3log.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

-->
<#include "../macro-head.ftl">
<!DOCTYPE html>
<html>
<head><@head title="鱼游管理 - ${symphonyLabel}"></@head><link rel="stylesheet" href="${staticServePath}/css/mobile-base.css?${staticResourceVersion}"></head>
<body>
<#include "../header.ftl">
<#assign sideHotArticles = sideHotArticles![]>
<div class="main"><div class="content"><div class="module fish-game-admin">
    <div class="fish-game-admin__header"><div><h1>鱼游管理</h1><p>审核投稿，维护已收录鱼游。</p></div><button class="fish-game-admin__button fish-game-admin__button--secondary" type="button" data-fish-game-export>导出 JSON</button></div>
    <output class="fish-game-admin__result" data-fish-game-admin-result aria-live="polite"></output>
    <div class="fish-game-admin__disclosures">
        <details class="fish-game-admin__disclosure"><summary><span class="fish-game-admin__disclosure-copy"><strong>手动添加</strong><small>录入一个鱼游</small></span><span class="fish-game-admin__chevron" aria-hidden="true"></span></summary><div class="fish-game-admin__disclosure-body"><form class="fish-game-admin__form" data-admin-add><label>名称<input name="fishGameName" maxlength="80" required></label><label>目标网址<input name="fishGameUrl" type="url" required></label><label>图标网址<input name="fishGameIconUrl" type="url" required></label><label class="fish-game-form__wide">描述<textarea name="fishGameDescription" maxlength="1000" required></textarea></label><button class="fish-game-admin__button fish-game-admin__button--primary" type="submit">添加鱼游</button></form></div></details>
        <details class="fish-game-admin__disclosure"><summary><span class="fish-game-admin__disclosure-copy"><strong>导入 JSON</strong><small>导入旧版清单或备份</small></span><span class="fish-game-admin__chevron" aria-hidden="true"></span></summary><div class="fish-game-admin__disclosure-body"><p class="fish-game-admin__hint">选择文件或直接粘贴 JSON 内容。</p><input class="fish-game-admin__file-input" id="fishGameImportFile" type="file" data-fish-game-import-file accept=".json,application/json"><label class="fish-game-admin__file-picker" for="fishGameImportFile"><span class="fish-game-admin__file-button">选择文件</span><span class="fish-game-admin__file-name" data-fish-game-import-file-name>未选择文件</span></label><label class="fish-game-admin__json-label">JSON 内容<textarea data-fish-game-import rows="8" maxlength="2000000" placeholder="粘贴包含 games 数组的 JSON"></textarea></label><button class="fish-game-admin__button fish-game-admin__button--primary" type="button" data-fish-game-import-submit>开始导入</button></div></details>
    </div>
    <section class="fish-game-admin__list"><div class="fish-game-admin__list-heading"><h2>鱼游列表</h2></div><#list fishGames as game><article class="fish-game-admin__row"><img src="${game.fishGameIconUrl?html}" alt="${game.fishGameName?html}"><div class="fish-game-admin__main"><div class="fish-game-admin__game-heading"><h2>${game.fishGameName?html}</h2><span class="fish-game-admin__status fish-game-admin__status--${game.fishGameStatus}"><#if game.fishGameStatus == 0>待审核<#elseif game.fishGameStatus == 1>已通过<#elseif game.fishGameStatus == 2>已拒绝<#else>已停用</#if></span></div><span class="fish-game-admin__meta">👍 ${game.fishGameLikeCount!0}　👎 ${game.fishGameDislikeCount!0}</span><p>${game.fishGameDescription?html}</p><a class="fish-game-admin__site-link" href="${game.fishGameUrl?html}" target="_blank" rel="noopener">打开网站</a><#if game.fishGameEditPending == 1><div class="fish-game-admin__pending"><strong>待审核修改</strong><p>名称：${game.fishGamePendingName?html}</p><p>网址：${game.fishGamePendingUrl?html}</p><p>图标：${game.fishGamePendingIconUrl?html}</p><p>描述：${game.fishGamePendingDescription?html}</p></div></#if><details class="fish-game-admin__edit-disclosure"><summary><span>编辑资料</span><span class="fish-game-admin__chevron" aria-hidden="true"></span></summary><form class="fish-game-admin__edit" data-admin-edit-form data-id="${game.oId?html}"><label>名称<input name="fishGameName" value="${game.fishGameName?html}" maxlength="80" required></label><label>目标网址<input name="fishGameUrl" type="url" value="${game.fishGameUrl?html}" required></label><label>图标网址<input name="fishGameIconUrl" type="url" value="${game.fishGameIconUrl?html}" required></label><label>描述<textarea name="fishGameDescription" maxlength="1000" required>${game.fishGameDescription?html}</textarea></label><button class="fish-game-admin__button fish-game-admin__button--primary" type="submit">保存修改</button></form></details></div><div class="fish-game-admin__actions"><#if game.fishGameEditPending == 1><button class="fish-game-admin__button fish-game-admin__button--approve" type="button" data-admin-review="${game.oId?html}" data-status="1">通过修改</button><button class="fish-game-admin__button fish-game-admin__button--reject" type="button" data-admin-review="${game.oId?html}" data-status="2">拒绝修改</button><#else><button class="fish-game-admin__button fish-game-admin__button--approve" type="button" data-admin-review="${game.oId?html}" data-status="1">通过投稿</button><button class="fish-game-admin__button fish-game-admin__button--reject" type="button" data-admin-review="${game.oId?html}" data-status="2">拒绝投稿</button></#if><button class="fish-game-admin__button fish-game-admin__button--disable" type="button" data-admin-review="${game.oId?html}" data-status="3">停用</button></div></article><#else><p class="fish-game-admin__empty">暂无鱼游记录。</p></#list></section>
</div></div></div>
<#include "../footer.ftl"><script src="${staticServePath}/js/fish-game.js?${staticResourceVersion}"></script>
</body></html>
