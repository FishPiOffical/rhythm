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
<#include "../macro-pagination-query.ftl">
<#include "../common/title-icon.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="${article.articleTitleEmojUnicode} - ${symphonyLabel}">
        <meta name="description" content="${article.articlePreviewContent}"/>
        <#if 1 == article.articleStatus || 1 == article.articleAuthor.userStatus>
            <meta name="robots" content="NOINDEX,NOFOLLOW"/>
        </#if>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/long-article.css?${staticResourceVersion}">
    <link rel="stylesheet" href="${staticServePath}/js/lib/compress/article.min.css?${staticResourceVersion}">
    <link rel="canonical" href="${servePath}/long/${article.oId}">
</head>

<body class="long-article-body">
<div class="long-article-container">
    <h1 class="long-article-title">${article.articleTitleEmoj}</h1>
    <div class="long-article-meta">
        <a rel="author" href="${servePath}/member/${article.articleAuthorName}">${article.articleAuthorName}</a>
        <span class="meta-dot">•</span>
        <span>${article.timeAgo}</span>
    </div>
    <div class="vditor-reset long-article-content">
        ${article.articleContent}
    </div>
</div>

<div class="long-article-sidebar">
    <ul class="sidebar-nav">
        <li class="sidebar-item" onclick="LongArticle.scrollToTop()" title="回到顶部">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor" d="M5 15h4v6h6v-6h4l-7-8zM4 3h16v2H4z"/>
            </svg>
        </li>
        <li class="sidebar-item" onclick="LongArticle.openSettings()" title="阅读设置">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor" d="M14.82 1H9.18l-.647 3.237a8.5 8.5 0 0 0-1.52.88l-3.13-1.059l-2.819 4.884l2.481 2.18a8.6 8.6 0 0 0 0 1.756l-2.481 2.18l2.82 4.884l3.129-1.058c.472.342.98.638 1.52.879L9.18 23h5.64l.647-3.237a8.5 8.5 0 0 0 1.52-.88l3.13 1.059l2.82-4.884l-2.482-2.18a8.6 8.6 0 0 0 0-1.756l2.481-2.18l-2.82-4.884l-3.128 1.058a8.5 8.5 0 0 0-1.52-.879zM12 16a4 4 0 1 1 0-8a4 4 0 0 1 0 8"/>
            </svg>
        </li>
        <li class="sidebar-item" onclick="LongArticle.toggleNight()" title="夜间模式">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor" d="M13.574 3.138a1.01 1.01 0 0 0-1.097 1.408a6 6 0 0 1-7.931 7.931a1.01 1.01 0 0 0-1.409 1.097A9 9 0 0 0 21 12a9 9 0 0 0-7.426-8.862"/>
            </svg>
        </li>
    </ul>

    <div class="settings-panel" id="settingsPanel">
        <div class="settings-item">
            <div class="settings-label">主题</div>
            <div class="settings-options">
                <span class="theme-btn light active" onclick="LongArticle.setTheme('light')">浅色</span>
                <span class="theme-btn night" onclick="LongArticle.setTheme('night')">深色</span>
            </div>
        </div>
        <div class="settings-item">
            <div class="settings-label">字号</div>
            <div class="font-size-ctrl">
                <span onclick="LongArticle.setFontSize(-2)">A-</span>
                <span class="font-size-value" id="fontSizeValue">18px</span>
                <span onclick="LongArticle.setFontSize(2)">A+</span>
            </div>
        </div>
        <div class="settings-item">
            <div class="settings-label">宽度</div>
            <div class="width-ctrl">
                <span class="width-btn" onclick="LongArticle.setWidth(700)">窄</span>
                <span class="width-btn active" onclick="LongArticle.setWidth(900)">中</span>
                <span class="width-btn" onclick="LongArticle.setWidth(1100)">宽</span>
            </div>
        </div>
    </div>
</div>

<div class="wrapper" id="articleCommentsPanel">
    <#if article.offered>
        <div class="module nice">
            <div class="module-header">
                <svg class="ft-blue">
                    <use xlink:href="#iconAdopt"></use>
                </svg>
                ${adoptLabel}
            </div>
            <div class="module-panel list comments">
                <ul>
                    <li>
                        <div class="fn-flex">
                            <a rel="nofollow"
                               href="${servePath}/member/${article.articleOfferedComment.commentAuthorName}">
                                <div class="avatar tooltipped tooltipped-se"
                                     aria-label="${article.articleOfferedComment.commentAuthorName}"
                                     style="background-image:url('${article.articleOfferedComment.commentAuthorThumbnailURL}')"></div>
                                </a>
                            <div class="fn-flex-1">
                                <div class="fn-clear comment-info ft-smaller">
                                        <span class="fn-left">
                                            <a rel="nofollow"
                                               href="${servePath}/member/${article.articleOfferedComment.commentAuthorName}"
                                               class="ft-gray"><span
                                                        class="ft-gray">${article.articleOfferedComment.commentAuthorName}</span></a>
                                            <span class="ft-fade">• ${article.articleOfferedComment.timeAgo}</span>
                                        </span>
                                    <a class="ft-a-title fn-right tooltipped tooltipped-nw"
                                       aria-label="${goCommentLabel}"
                                       href="javascript:Comment.goComment('${servePath}/long/${article.oId}?p=${article.articleOfferedComment.paginationCurrentPageNum}&m=${userCommentViewMode}#${article.articleOfferedComment.oId}')">
                                        <svg>
                                            <use xlink:href="#down"></use>
                                        </svg>
                                    </a>
                                </div>
                                <div class="vditor-reset comment">
                                    ${article.articleOfferedComment.commentContent}
                                </div>
                            </div>
                        </div>
                    </li>
                </ul>
            </div>
        </div>
    </#if>

    <#if pjax><!---- pjax {#comments} start ----></#if>
    <div class="module comments" id="comments">
        <div class="comments-header module-header">
            <span class="article-cmt-cnt">${article.articleCommentCount} ${cmtLabel}</span>
            <span class="fn-right<#if article.articleComments?size == 0> fn-none</#if>">
                        <a class="tooltipped tooltipped-nw"
                           href="javascript:Comment.exchangeCmtSort(${userCommentViewMode})"
                           aria-label="<#if 0 == userCommentViewMode>${changeToLabel}${realTimeLabel}${cmtViewModeLabel}<#else>${changeToLabel}${traditionLabel}${cmtViewModeLabel}</#if>"><span
                                    class="icon-<#if 0 == userCommentViewMode>sortasc<#else>time</#if>"></span></a>&nbsp;
                        <a class="tooltipped tooltipped-nw" href="javascript:Comment._bgFade($('#bottomComment'))"
                           aria-label="${jumpToBottomCommentLabel}"><svg><use
                                        xlink:href="#chevron-down"></use></svg></a>
                    </span>
        </div>
        <div class="list">
            <div class="comment__reply">
                <#if isLoggedIn>
                    <div class="fn__flex">
                        <span class="avatar"
                              style="background-image: url('${currentUser.userAvatarURL48}');"></span>
                        <span class="reply__text fn-flex-1 commentToggleEditorBtn"
                              onclick="Comment._toggleReply();">请输入回帖内容
                                    ...
                                </span>
                    </div>
                <#else>
                    <div class="reply__text fn-flex-1 commentToggleEditorBtn" onclick="Util.goLogin();">登录参与讨论
                        ...
                    </div>
                </#if>
            </div>
            <ul>
                <#assign notificationCmtIds = "">
                <#list article.articleComments as comment>
                    <#assign notificationCmtIds = notificationCmtIds + comment.oId>
                    <#if comment_has_next><#assign notificationCmtIds = notificationCmtIds + ","></#if>
                    <#include '../common/comment.ftl' />
                </#list>
            </ul>
            <div id="bottomComment"></div>
        </div>
        <@pagination url="${servePath}/long/${article.oId}" query="m=${userCommentViewMode}#comments" pjaxTitle="${article.articleTitle} - ${symphonyLabel}" />
    </div>
    <#if pjax><!---- pjax {#comments} end ----></#if>
</div>

<#include "../footer.ftl">
<script src="${staticServePath}/js/lib/compress/article-libs.min.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/channel${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/article${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/long-article${miniPostfix}.js?${staticResourceVersion}"></script>
<script>
    Label.commentErrorLabel = "${commentErrorLabel}";
    Label.articleOId = "${article.oId}";
    Label.articleTitle = "${article.articleTitle}";
    Label.csrfToken = "${csrfToken}";
    Label.userCommentViewMode = ${userCommentViewMode};
    Label.goCommentLabel = '${goCommentLabel}';
    Label.commonAtUser = '${permissions["commonAtUser"].permissionGrant?c}';
    Label.articleAuthorName = '${article.articleAuthorName}';
    <#if isLoggedIn>
    Label.currentUserName = '${currentUser.userName}';
    Label.notificationCmtIds = '${notificationCmtIds}';
    </#if>
</script>
<script>
    LongArticle.init();
    setInterval(function () {
        Util.listenUserCard();
    }, 1000);
</script>
</body>
</html>
