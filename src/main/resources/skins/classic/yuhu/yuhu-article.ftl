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
        <meta name="keywords"
              content="<#list article.articleTagObjs as articleTag>${articleTag.tagTitle}<#if articleTag?has_next>,</#if></#list>"/>
        <meta name="description" content="${article.articlePreviewContent}"/>
        <#if 1 == article.articleStatus || 1 == article.articleAuthor.userStatus || 1 == article.articleType>
            <meta name="robots" content="NOINDEX,NOFOLLOW"/>
        </#if>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/yuhu.css?${staticResourceVersion}">
    <link rel="canonical"
          href="${servePath}${article.articlePermalink}?p=${paginationCurrentPageNum}&m=${userCommentViewMode}">
</head>

<body class="article">
<div class="article-body">
    <h1 class="article-title" itemprop="name">
        ${article.articleTitleEmoj}
    </h1>
    <div class="article-desc">
            <span class="article-desc-item">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <path fill="currentColor"
                          d="M20 22H6.5A3.5 3.5 0 0 1 3 18.5V5a3 3 0 0 1 3-3h14a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1m-1-2v-3H6.5a1.5 1.5 0 0 0 0 3z"/>
                </svg>
                &nbsp;合集名字
            </span>
        <span class="article-desc-item">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <path fill="currentColor"
                          d="M21 1.997c-15 0-17 14-18 20h1.998q.999-5 5.002-5.5c4-.5 7-4 8-7l-1.5-1l1-1c1-1 2.004-2.5 3.5-5.5"/>
                </svg>
                &nbsp;${article.articleAuthorName}
            </span>
        <span class="article-desc-item">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <path fill="currentColor"
                          d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2m3.3 14.71L11 12.41V7h2v4.59l3.71 3.71z"/>
                </svg>
                &nbsp;${article.timeAgo}
            </span>
    </div>
    <div class="vditor-reset article-content">
        ${article.articleContent}
    </div>
</div>
<div class="right-nav-box">
    <ul class="right-nav">
        <li class="right-nav-item">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor"
                      d="M14 9.9V8.2q.825-.35 1.688-.525T17.5 7.5q.65 0 1.275.1T20 7.85v1.6q-.6-.225-1.213-.337T17.5 9q-.95 0-1.825.238T14 9.9m0 5.5v-1.7q.825-.35 1.688-.525T17.5 13q.65 0 1.275.1t1.225.25v1.6q-.6-.225-1.213-.338T17.5 14.5q-.95 0-1.825.225T14 15.4m0-2.75v-1.7q.825-.35 1.688-.525t1.812-.175q.65 0 1.275.1T20 10.6v1.6q-.6-.225-1.213-.338T17.5 11.75q-.95 0-1.825.238T14 12.65m-1 4.4q1.1-.525 2.213-.788T17.5 16q.9 0 1.763.15T21 16.6V6.7q-.825-.35-1.713-.525T17.5 6q-1.175 0-2.325.3T13 7.2zM12 20q-1.2-.95-2.6-1.475T6.5 18q-1.05 0-2.062.275T2.5 19.05q-.525.275-1.012-.025T1 18.15V6.1q0-.275.138-.525T1.55 5.2q1.175-.575 2.413-.888T6.5 4q1.45 0 2.838.375T12 5.5q1.275-.75 2.663-1.125T17.5 4q1.3 0 2.538.313t2.412.887q.275.125.413.375T23 6.1v12.05q0 .575-.487.875t-1.013.025q-.925-.5-1.937-.775T17.5 18q-1.5 0-2.9.525T12 20"/>
            </svg>
            <span>目录</span>
        </li>
        <li class="right-nav-item">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor" fill-rule="evenodd"
                      d="M6.271 2.112c-.81.106-1.238.301-1.544.6c-.305.3-.504.72-.613 1.513C4.002 5.042 4 6.124 4 7.675v8.57a4.2 4.2 0 0 1 1.299-.593c.528-.139 1.144-.139 2.047-.138H20V7.676c0-1.552-.002-2.634-.114-3.451c-.109-.793-.308-1.213-.613-1.513c-.306-.299-.734-.494-1.544-.6c-.834-.11-1.938-.112-3.522-.112H9.793c-1.584 0-2.688.002-3.522.112m.488 4.483c0-.448.37-.811.827-.811h8.828a.82.82 0 0 1 .827.81a.82.82 0 0 1-.827.811H7.586a.82.82 0 0 1-.827-.81m.827 2.973a.82.82 0 0 0-.827.81c0 .448.37.811.827.811h5.517a.82.82 0 0 0 .828-.81a.82.82 0 0 0-.828-.811z"
                      clip-rule="evenodd"/>
                <path fill="currentColor"
                      d="M7.473 17.135H20c-.003 1.13-.021 1.974-.113 2.64c-.109.793-.308 1.213-.613 1.513c-.306.299-.734.494-1.544.6c-.834.11-1.938.112-3.522.112H9.793c-1.584 0-2.688-.002-3.522-.111c-.81-.107-1.238-.302-1.544-.601c-.305-.3-.504-.72-.613-1.513c-.041-.3-.068-.637-.084-1.02a2.46 2.46 0 0 1 1.697-1.537c.29-.076.667-.083 1.746-.083"/>
            </svg>
            <span>详情</span>
        </li>
        <li class="right-nav-item">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor"
                      d="M6.5 2A2.5 2.5 0 0 0 4 4.5v15A2.5 2.5 0 0 0 6.5 22h6.31a6.5 6.5 0 0 1-1.078-1.5H6.5a1 1 0 0 1-1-1h5.813a6.5 6.5 0 0 1 9.187-7.768V4.5A2.5 2.5 0 0 0 18 2zM8 5h8a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1m15 12.5a5.5 5.5 0 1 0-11 0a5.5 5.5 0 0 0 11 0m-5 .5l.001 2.503a.5.5 0 1 1-1 0V18h-2.505a.5.5 0 0 1 0-1H17v-2.5a.5.5 0 1 1 1 0V17h2.497a.5.5 0 0 1 0 1z"/>
            </svg>
            <span>收藏</span>
        </li>
        <li class="right-nav-item">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor"
                      d="M21.005 14v7a1 1 0 0 1-1 1h-16a1 1 0 0 1-1-1v-7a2 2 0 1 0 0-4V3a1 1 0 0 1 1-1h16a1 1 0 0 1 1 1v7a2 2 0 1 0 0 4m-12-8v2h6V6zm0 10v2h6v-2z"/>
            </svg>
            <span>投票</span>
        </li>
        <li class="right-nav-item" onclick="toggleNight()">
            <span class="night">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <g fill="none" fill-rule="evenodd">
                        <path d="m12.593 23.258l-.011.002l-.071.035l-.02.004l-.014-.004l-.071-.035q-.016-.005-.024.005l-.004.01l-.017.428l.005.02l.01.013l.104.074l.015.004l.012-.004l.104-.074l.012-.016l.004-.017l-.017-.427q-.004-.016-.017-.018m.265-.113l-.013.002l-.185.093l-.01.01l-.003.011l.018.43l.005.012l.008.007l.201.093q.019.005.029-.008l.004-.014l-.034-.614q-.005-.018-.02-.022m-.715.002a.02.02 0 0 0-.027.006l-.006.014l-.034.614q.001.018.017.024l.015-.002l.201-.093l.01-.008l.004-.011l.017-.43l-.003-.012l-.01-.01z"/>
                        <path fill="currentColor"
                              d="M13.574 3.138a1.01 1.01 0 0 0-1.097 1.408a6 6 0 0 1-7.931 7.931a1.01 1.01 0 0 0-1.409 1.097A9 9 0 0 0 21 12a9 9 0 0 0-7.426-8.862"/>
                    </g>
                </svg>
            </span>
            <span class="day">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <path fill="currentColor" d="M12 7a5 5 0 1 0 0 10a5 5 0 0 0 0-10"/>
                    <path fill="currentColor" fill-rule="evenodd"
                          d="M12 1a1 1 0 0 1 1 1v1a1 1 0 1 1-2 0V2a1 1 0 0 1 1-1M3.293 3.293a1 1 0 0 1 1.414 0l1.5 1.5a1 1 0 0 1-1.414 1.414l-1.5-1.5a1 1 0 0 1 0-1.414m17.414 0a1 1 0 0 1 0 1.414l-1.5 1.5a1 1 0 1 1-1.414-1.414l1.5-1.5a1 1 0 0 1 1.414 0M1 12a1 1 0 0 1 1-1h1a1 1 0 1 1 0 2H2a1 1 0 0 1-1-1m19 0a1 1 0 0 1 1-1h1a1 1 0 1 1 0 2h-1a1 1 0 0 1-1-1M6.207 17.793a1 1 0 0 1 0 1.414l-1.5 1.5a1 1 0 0 1-1.414-1.414l1.5-1.5a1 1 0 0 1 1.414 0m11.586 0a1 1 0 0 1 1.414 0l1.5 1.5a1 1 0 0 1-1.414 1.414l-1.5-1.5a1 1 0 0 1 0-1.414M12 20a1 1 0 0 1 1 1v1a1 1 0 1 1-2 0v-1a1 1 0 0 1 1-1"
                          clip-rule="evenodd"/>
                </svg>
            </span>
            <span class="day-night-label">夜间</span>
        </li>
        <li class="right-nav-item" onclick="openSetting()">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor"
                      d="M14.82 1H9.18l-.647 3.237a8.5 8.5 0 0 0-1.52.88l-3.13-1.059l-2.819 4.884l2.481 2.18a8.6 8.6 0 0 0 0 1.756l-2.481 2.18l2.82 4.884l3.129-1.058c.472.342.98.638 1.52.879L9.18 23h5.64l.647-3.237a8.5 8.5 0 0 0 1.52-.88l3.13 1.059l2.82-4.884l-2.482-2.18a8.6 8.6 0 0 0 0-1.756l2.481-2.18l-2.82-4.884l-3.128 1.058a8.5 8.5 0 0 0-1.52-.879zM12 16a4 4 0 1 1 0-8a4 4 0 0 1 0 8"/>
            </svg>
            <span>设置</span>
        </li>
        <li class="right-nav-item top" style="display: none" onclick="scrollToTop()">
            <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24">
                <path fill="currentColor" d="M5 15h4v6h6v-6h4l-7-8zM4 3h16v2H4z"/>
            </svg>
        </li>
    </ul>

    <#--  设置弹窗  -->
    <div class="setting-box">
        <h1>设置</h1>
        <div class="setting-item">
            <div class="setting-item-label">颜色主题</div>
            <div class="color-item light" onclick="changeTheme('light')"></div>
            <div class="color-item night" onclick="changeTheme('night')"></div>
        </div>
        <div class="setting-item">
            <div class="setting-item-label">页面宽度</div>
            <div style="width: 280px;display: flex;justify-content: space-between">
                <div class="page-width w-0" onclick="changeWidth(0)">自动</div>
                <div class="page-width w-600" onclick="changeWidth(600)">600</div>
                <div class="page-width w-800" onclick="changeWidth(800)">800</div>
                <div class="page-width w-1000" onclick="changeWidth(1000)">1000</div>
                <div class="page-width w-1200" onclick="changeWidth(1200)">1200</div>
            </div>
        </div>
        <div class="setting-item">
            <div class="setting-item-label">字体大小</div>
            <div class="font-size-controller-box">
                <div class="font-size-controller" onclick="changeFontSize(-1)">A-</div>
                <div class="font-size-input">18</div>
                <div class="font-size-controller" onclick="changeFontSize(1)">A+</div>
            </div>
        </div>
    </div>
</div>
<#include "../footer.ftl">
<script src="${staticServePath}/js/lib/jquery/file-upload-9.10.1/jquery.fileupload.min.js"></script>
<script src="${staticServePath}/js/yuhu-article${miniPostfix}.js?${staticResourceVersion}"></script>
<script>
    Label.commentErrorLabel = "${commentErrorLabel}";
    Label.symphonyLabel = "${symphonyLabel}";
    Label.rewardConfirmLabel = "${rewardConfirmLabel?replace('{point}', article.articleRewardPoint)}";
    Label.thankArticleConfirmLabel = "${thankArticleConfirmLabel?replace('{point}', pointThankArticle)}";
    Label.thankSentLabel = "${thankSentLabel}";
    Label.articleOId = "${article.oId}";
    Label.articleTitle = "${article.articleTitle}";
    Label.recordDeniedLabel = "${recordDeniedLabel}";
    Label.recordDeviceNotFoundLabel = "${recordDeviceNotFoundLabel}";
    Label.csrfToken = "${csrfToken}";
    Label.notAllowCmtLabel = "${notAllowCmtLabel}";
    Label.upLabel = "${upLabel}";
    Label.downLabel = "${downLabel}";
    Label.confirmRemoveLabel = "${confirmRemoveLabel}";
    Label.removedLabel = "${removedLabel}";
    Label.uploadLabel = "${uploadLabel}";
    Label.userCommentViewMode = ${userCommentViewMode};
    Label.stickConfirmLabel = "${stickConfirmLabel}";
    Label.audioRecordingLabel = '${audioRecordingLabel}';
    Label.uploadingLabel = '${uploadingLabel}';
    Label.copiedLabel = '${copiedLabel}';
    Label.copyLabel = '${copyLabel}';
    Label.noRevisionLabel = "${noRevisionLabel}";
    Label.thankedLabel = "${thankedLabel}";
    Label.thankLabel = "${thankLabel}";
    Label.isAdminLoggedIn = ${isAdminLoggedIn?c};
    Label.adminLabel = '${adminLabel}';
    Label.thankSelfLabel = '${thankSelfLabel}';
    Label.replyLabel = '${replyLabel}';
    Label.articleAuthorName = '${article.articleAuthorName}';
    Label.referenceLabel = '${referenceLabel}';
    Label.goCommentLabel = '${goCommentLabel}';
    Label.addBoldLabel = '${addBoldLabel}';
    Label.addItalicLabel = '${addItalicLabel}';
    Label.insertQuoteLabel = '${insertQuoteLabel}';
    Label.addBulletedLabel = '${addBulletedLabel}';
    Label.addNumberedListLabel = '${addNumberedListLabel}';
    Label.addLinkLabel = '${addLinkLabel}';
    Label.undoLabel = '${undoLabel}';
    Label.redoLabel = '${redoLabel}';
    Label.previewLabel = '${previewLabel}';
    Label.helpLabel = '${helpLabel}';
    Label.fullscreenLabel = '${fullscreenLabel}';
    Label.uploadFileLabel = '${uploadFileLabel}';
    Label.commonUpdateCommentPermissionLabel = '${commonUpdateCommentPermissionLabel}';
    Label.insertEmojiLabel = '${insertEmojiLabel}';
    Label.commonAtUser = '${permissions["commonAtUser"].permissionGrant?c}';
    Label.noPermissionLabel = '${noPermissionLabel}';
    Label.rewardLabel = '${rewardLabel}';
    Label.articleChannel = "${wsScheme}://${serverHost}:${serverPort}${contextPath}/article-channel?articleId=${article.oId}&articleType=${article.articleType}";
    <#if isLoggedIn>
    Label.currentUserName = '${currentUser.userName}';
    </#if>
    <#if 3 == article.articleType>
    Article.playThought('${article.articleContent}');
    </#if>

    setInterval(function () {
        Util.listenUserCard();
    }, 1000);
</script>
<script>
    $('.footer').hide();
</script>
</body>
</html>
