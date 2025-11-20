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
<#include "macro-head.ftl">
<#include "macro-pagination-query.ftl">
<#include "common/title-icon.ftl">
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
    <link rel="stylesheet" href="${staticServePath}/js/lib/compress/article.min.css?${staticResourceVersion}">
    <link rel="stylesheet" href="${staticServePath}/css/yuhu.css?${staticResourceVersion}">
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"/>
    <link rel="canonical"
          href="${servePath}${article.articlePermalink}?p=${paginationCurrentPageNum}&m=${userCommentViewMode}">
</head>

<body class="article">
    <div class="article-body">
        <h1 class="article-title" itemprop="name">
            ${article.articleTitleEmoj}
        </h1>
        <div class="article-desc">${article.articleAuthorName} ${article.timeAgo}</div>
        <div class="vditor-reset article-content">
            ${article.articleContent}
        </div>
    </div>
    <div class="right-nav-box">
        <ul class="right-nav">
            <li class="right-nav-item">
                <svg style="width: 24px;height: 24px;">
                    <use xlink:href="#color-sun"></use>
                </svg>
                <span>目录</span>
            </li>
            <li class="right-nav-item">
                <svg style="width: 24px;height: 24px;">
                    <use xlink:href="#color-sun"></use>
                </svg>
                <span>详情</span>
            </li>
            <li class="right-nav-item">
                <svg style="width: 24px;height: 24px;">
                    <use xlink:href="#color-sun"></use>
                </svg>
                <span>投票</span>
            </li>
            <li id="color-mode" class="right-nav-item">
                <svg style="width: 24px;height: 24px;">
                    <use xlink:href="#color-sun"></use>
                </svg>
                <span>夜间</span>
            </li>
            <li class="right-nav-item">
                <svg style="width: 24px;height: 24px;">
                    <use xlink:href="#color-sun"></use>
                </svg>
                <span>设置</span>
            </li>
        </ul>
    </div>
<#include "footer.ftl">

<#if isLoggedIn && discussionViewable && article.articleCommentable>
    <div class="editor-panel">
        <div class="editor-bg"></div>
        <div class="wrapper">
            <div style="width: 100%">
                <div class="fn-flex">
                    <div id="replyUseName" class="fn-flex-1 fn-ellipsis"></div>
                    <span class="tooltipped tooltipped-w fn-pointer editor-hide" onclick="Comment._toggleReply()"
                          aria-label="${cancelLabel}"> <svg><use xlink:href="#chevron-down"></use></svg></span>
                </div>
                <div class="article-comment-content">
                    <div id="commentContent"></div>
                    <br>
                    <div class="comment-submit fn-clear">
                        <div>
                            <svg id="emojiBtn" style="width: 30px; height: 30px; cursor:pointer;">
                                <use xlink:href="#emojiIcon"></use>
                            </svg>
                            <div class="hide-list" id="emojiList">
                                <div class="hide-list-emojis" id="emojis" style="max-height: 200px">
                                </div>
                                <div class="hide-list-emojis__tail">
                                        <span>
                                        <a onclick="Comment.fromURL()">从URL导入表情包</a>
                                        </span>
                                    <span class="hide-list-emojis__tip"></span>
                                    <span>
                                            <a onclick="$('#uploadEmoji input').click()">上传表情包</a>
                                        </span>
                                    <form style="display: none" id="uploadEmoji" method="POST"
                                          enctype="multipart/form-data">
                                        <input type="file" name="file">
                                    </form>
                                </div>
                            </div>
                        </div>
                        <#if permissions["commonAddCommentAnonymous"].permissionGrant>
                            <label class="cmt-anonymous">${anonymousLabel}<input type="checkbox" id="commentAnonymous"></label>
                        </#if>
                        <label class="cmt-anonymous">${onlyArticleAuthorVisibleLabel}<input type="checkbox"
                                                                                            id="commentVisible"></label>
                        <div class="fn-flex-1"></div>
                        <div class="fn-right">
                            <div class="tip fn-left" id="addCommentTip"></div> &nbsp; &nbsp;
                            <a class="fn-pointer ft-a-title" href="javascript:Comment._toggleReply()">${cancelLabel}</a>
                            &nbsp; &nbsp;
                            <button id="articleCommentBtn" class="green"
                                    onclick="Comment.add('${article.oId}', '${csrfToken}', this)">${submitLabel}</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div id="thoughtProgressPreview"></div>
</#if>
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
