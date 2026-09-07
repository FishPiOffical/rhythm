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
<html><head><@head title="${activityLabel} - ${symphonyLabel}"></@head><link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"></head>
<body><#include "../header.ftl"><div class="main"><div class="wrapper"><div class="content activity"><div class="module fish-games-page">
<div class="fish-games-hero"><div><h1>摸鱼鱼游</h1><p>发现社区精选鱼游，也可以分享你的网站。</p></div><button class="btn green" type="button" data-fish-game-open>投稿鱼游</button></div>
<div class="fish-games-grid"><#list fishGames as game><#assign userVote = fishGameUserVotes[game.oId]!''><article class="fish-game-card" data-fish-game-url="${game.fishGameUrl?html}"><a class="fish-game-card__link" href="${game.fishGameUrl?html}" target="_blank" rel="noopener"><span class="fish-game-card__icon"><img src="${game.fishGameIconUrl?html}" alt="${game.fishGameName?html}"></span><span class="fish-game-card__body"><h2>${game.fishGameName?html}</h2><p>${game.fishGameDescription?html}</p></span></a><div class="fish-game-card__meta"><button aria-pressed="${(userVote == 'like')?string('true','false')}" class="<#if userVote == 'like'>is-selected</#if>" type="button" data-fish-game-vote="like" data-fish-game-id="${game.oId?html}">👍 <span>${game.fishGameLikeCount!0}</span></button><button aria-pressed="${(userVote == 'dislike')?string('true','false')}" class="<#if userVote == 'dislike'>is-selected</#if>" type="button" aria-label="踩" data-fish-game-vote="dislike" data-fish-game-id="${game.oId?html}">🦶 <span class="fish-game-dislike-indicator"><#if userVote == 'dislike'>1</#if></span></button><a href="${servePath}/activities/game/${game.oId?html}">评论</a></div></article><#else><p class="fish-games-empty">暂时没有已通过审核的鱼游。</p></#list></div>
<section class="fish-game-submissions"><div class="fish-game-submissions__header"><h2>我的投稿</h2><button class="btn green" type="button" data-fish-game-open>再次投稿</button></div><#list myFishGames as game><article class="fish-game-submission"><div><strong>${game.fishGameName?html}</strong><p>${game.fishGameDescription?html}</p></div><span class="fish-game-status fish-game-status--${game.fishGameStatus}"><#if game.fishGameStatus == 0>待审核<#elseif game.fishGameStatus == 1>已通过<#elseif game.fishGameStatus == 2>已拒绝<#else>已停用</#if></span></article><#else><p class="fish-games-empty">暂无投稿记录。</p></#list></section>
</div></div></div></div>
<div class="fish-game-modal" data-fish-game-modal hidden><div class="fish-game-modal__backdrop" data-fish-game-close></div><section class="fish-game-modal__dialog" role="dialog" aria-modal="true" aria-labelledby="fish-game-submit-title"><button class="fish-game-modal__close" type="button" aria-label="关闭" data-fish-game-close>×</button><h2 id="fish-game-submit-title">投稿鱼游</h2><p class="fish-game-notice">管理员人工审核后公开展示。</p><form class="fish-game-form" data-fish-game-submit><label>名称<input name="fishGameName" maxlength="80" required></label><label>目标网址<input name="fishGameUrl" type="url" placeholder="https://example.com" required></label><label>图标网址<input name="fishGameIconUrl" type="url" placeholder="https://example.com/icon.png" required></label><label class="fish-game-form__wide">描述<textarea name="fishGameDescription" maxlength="1000" required></textarea></label><button class="btn green" type="submit">提交审核</button></form></section></div>
<#include "../footer.ftl"><script src="${staticServePath}/js/fish-game.js?${staticResourceVersion}"></script></body></html>
