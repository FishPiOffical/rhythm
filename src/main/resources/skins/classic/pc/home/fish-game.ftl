<#include "../macro-head.ftl">
<!DOCTYPE html>
<html><head><@head title="${fishGame.fishGameName?html}"></@head><link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"></head>
<body><#include "../header.ftl"><div class="main"><div class="wrapper"><div class="content activity"><div class="module fish-game-detail">
<header class="fish-game-detail__hero">
  <div class="fish-game-detail__identity">
    <img src="${fishGame.fishGameIconUrl?html}" alt="${fishGame.fishGameName?html}">
    <div class="fish-game-detail__copy">
      <span class="fish-game-detail__eyebrow">社区鱼游</span>
      <h1>${fishGame.fishGameName?html}</h1>
      <p>${fishGame.fishGameDescription?html}</p>
    </div>
  </div>
  <a class="fish-game-detail__open" href="${fishGame.fishGameUrl?html}" target="_blank" rel="noopener"><span>打开鱼游</span><span aria-hidden="true">↗</span></a>
</header>
<div class="fish-game-detail__layout">
  <aside class="fish-game-detail__sidebar">
    <section class="fish-game-detail__panel">
      <h2>社区反馈</h2>
      <div class="fish-game-detail__actions">
        <button class="fish-game-vote fish-game-vote--like <#if fishGameUserVote == 'like'>is-selected</#if>" aria-pressed="${(fishGameUserVote == 'like')?string('true','false')}" type="button" data-fish-game-vote="like" data-fish-game-id="${fishGame.oId?html}"><span aria-hidden="true">👍</span><span>点赞</span><strong class="fish-game-vote__count">${fishGame.fishGameLikeCount!0}</strong></button>
        <button class="fish-game-vote fish-game-vote--dislike <#if fishGameUserVote == 'dislike'>is-selected</#if>" aria-label="点踩" aria-pressed="${(fishGameUserVote == 'dislike')?string('true','false')}" type="button" data-fish-game-vote="dislike" data-fish-game-id="${fishGame.oId?html}"><span aria-hidden="true">🦶</span><span>点踩</span><strong class="fish-game-vote__count fish-game-dislike-indicator"><#if fishGameUserVote == 'dislike'>1</#if></strong></button>
      </div>
    </section>
    <#if fishGameCanEdit>
      <section class="fish-game-detail__panel fish-game-detail__owner">
        <#if fishGameEditPending>
          <div class="fish-game-edit-status"><span class="fish-game-edit-status__indicator" aria-hidden="true"></span><div><strong>修改申请已提交</strong><small>等待管理员审核</small></div></div>
        <#else>
          <details class="fish-game-edit-disclosure">
            <summary><span><strong>网站维护</strong><small>更新名称、网址、图标和介绍</small></span><b>申请编辑</b></summary>
            <form data-fish-game-edit-form>
              <label>名称<input name="fishGameName" value="${fishGame.fishGameName?html}" maxlength="80" required></label>
              <label>目标网址<input name="fishGameUrl" type="url" value="${fishGame.fishGameUrl?html}" required></label>
              <label>图标网址<input name="fishGameIconUrl" type="url" value="${fishGame.fishGameIconUrl?html}" required></label>
              <label>描述<textarea name="fishGameDescription" maxlength="1000" required>${fishGame.fishGameDescription?html}</textarea></label>
              <button class="btn green" type="submit">提交修改</button>
            </form>
          </details>
        </#if>
      </section>
    </#if>
  </aside>
  <section class="fish-game-detail__comments">
    <div class="fish-game-detail__section-head"><h2>评论</h2></div>
    <div class="fish-game-comments"><#list fishGameComments as comment><article class="fish-game-comment"><b>${comment.authorName?html}</b><p>${comment.fishGameCommentContent?html}</p></article><#else><p class="fish-game-comments__empty">暂无评论</p></#list></div>
    <#if isLoggedIn><form class="fish-game-comment-form" data-fish-game-comment><textarea name="content" maxlength="500" placeholder="写下你的看法" required></textarea><button class="btn green" type="submit">发表评论</button></form><#else><a class="fish-game-login" href="${servePath}/login">登录后发表评论</a></#if>
  </section>
</div>
</div></div></div></div><#include "../footer.ftl"><script>window.fishGameId='${fishGame.oId?html}'</script><script src="${staticServePath}/js/fish-game.js?${staticResourceVersion}"></script></body></html>
