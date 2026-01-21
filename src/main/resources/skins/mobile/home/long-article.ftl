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
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
    <@head title="${article.articleTitleEmojUnicode} - ${symphonyLabel}">
        <meta name="description" content="${article.articlePreviewContent}"/>
        <#if 1 == article.articleStatus || 1 == article.articleAuthor.userStatus>
            <meta name="robots" content="NOINDEX,NOFOLLOW"/>
        </#if>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/m-long-article.css?${staticResourceVersion}">
</head>

<body class="m-long-article-body">
    <div class="m-long-article-header">
        <a href="javascript:history.back()" class="back-btn">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                <path fill="currentColor" d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
            </svg>
        </a>
        <h1 class="m-long-article-title">${article.articleTitleEmoj}</h1>
        <div class="m-header-actions">
            <span onclick="MLongArticle.toggleNight()" class="m-action-btn">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24">
                    <path fill="currentColor" d="M13.574 3.138a1.01 1.01 0 0 0-1.097 1.408a6 6 0 0 1-7.931 7.931a1.01 1.01 0 0 0-1.409 1.097A9 9 0 0 0 21 12a9 9 0 0 0-7.426-8.862"/>
                </svg>
            </span>
            <span onclick="MLongArticle.openSettings()" class="m-action-btn">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24">
                    <path fill="currentColor" d="M14.82 1H9.18l-.647 3.237a8.5 8.5 0 0 0-1.52.88l-3.13-1.059l-2.819 4.884l2.481 2.18a8.6 8.6 0 0 0 0 1.756l-2.481 2.18l2.82 4.884l3.129-1.058c.472.342.98.638 1.52.879L9.18 23h5.64l.647-3.237a8.5 8.5 0 0 0 1.52-.88l3.13 1.059l2.82-4.884l-2.482-2.18a8.6 8.6 0 0 0 0-1.756l2.481-2.18l-2.82-4.884l-3.128 1.058a8.5 8.5 0 0 0-1.52-.879zM12 16a4 4 0 1 1 0-8a4 4 0 0 1 0 8"/>
                </svg>
            </span>
        </div>
    </div>

    <div class="m-long-article-container">
        <div class="m-long-article-meta">
            <a rel="author" href="${servePath}/member/${article.articleAuthorName}">${article.articleAuthorName}</a>
            <span>•</span>
            <span>${article.timeAgo}</span>
        </div>
        <div class="vditor-reset m-long-article-content">
            ${article.articleContent}
        </div>
    </div>

    <div class="m-settings-panel" id="mSettingsPanel">
        <div class="m-settings-item">
            <div class="m-settings-label">主题</div>
            <div class="m-settings-options">
                <span class="m-theme-btn light active" onclick="MLongArticle.setTheme('light')">浅色</span>
                <span class="m-theme-btn night" onclick="MLongArticle.setTheme('night')">深色</span>
            </div>
        </div>
        <div class="m-settings-item">
            <div class="m-settings-label">字号</div>
            <div class="m-font-size-ctrl">
                <span onclick="MLongArticle.setFontSize(-2)">A-</span>
                <span class="m-font-size-value" id="mFontSizeValue">16px</span>
                <span onclick="MLongArticle.setFontSize(2)">A+</span>
            </div>
        </div>
    </div>

<#include "footer.ftl">
<script src="${staticServePath}/js/m-long-article${miniPostfix}.js?${staticResourceVersion}"></script>
<script>
    Label.articleOId = "${article.oId}";
</script>
<script>
    MLongArticle.init();
</script>
</body>
</html>
