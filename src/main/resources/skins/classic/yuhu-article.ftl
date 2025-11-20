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
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"/>
    <link rel="canonical"
          href="${servePath}${article.articlePermalink}?p=${paginationCurrentPageNum}&m=${userCommentViewMode}">
    <#if articlePrevious??>
        <link rel="prev" title="${articlePrevious.articleTitleEmojUnicode}"
              href="${servePath}${articlePrevious.articlePermalink}">
    </#if>
    <#if articleNext??>
        <link rel="next" title="${articleNext.articleTitleEmojUnicode}"
              href="${servePath}${articleNext.articlePermalink}">
    </#if>
    <!-- Open Graph -->
    <meta property="og:locale" content="zh_CN"/>
    <meta property="og:type" content="article"/>
    <meta property="og:title" content="${article.articleTitle} - ${symphonyLabel}"/>
    <meta property="og:description" content="${article.articlePreviewContent}"/>
    <meta property="og:image" content="${article.articleAuthorThumbnailURL210}"/>
    <meta property="og:url" content="${servePath}${article.articlePermalink}"/>
    <meta property="og:site_name" content="${symphonyLabel}"/>
    <!-- Twitter Card -->
    <meta name="twitter:card" content="summary"/>
    <meta name="twitter:description" content="${article.articlePreviewContent}"/>
    <meta name="twitter:title" content="${article.articleTitle} - ${symphonyLabel}"/>
    <meta name="twitter:image" content="${article.articleAuthorThumbnailURL210}"/>
    <meta name="twitter:url" content="${servePath}${article.articlePermalink}"/>
    <meta name="twitter:site" content="@B3logOS"/>
    <meta name="twitter:creator" content="@B3logOS"/>
</head>
<body itemscope itemtype="http://schema.org/Product" class="article">
<img itemprop="image" class="fn-none" src="${article.articleAuthorThumbnailURL210}"/>
<p itemprop="description" class="fn-none">"${article.articlePreviewContent}"</p>
<div class="article-container">
<div class="article-body">
    <#if showTopAd>
        ${HeaderBannerLabel}
    </#if>
    <div class="wrapper">
        <h1 class="article-title" itemprop="name">
            <@icon article.articlePerfect article.articleType></@icon>
            ${article.articleTitleEmoj}
        </h1>
        <#if 0!= article.articleStatement>
        <div style="display: flex ;justify-content: center">
            <div class="article-statement">
                创作声明：
                <#if 1== article.articleStatement>${statementAILabel}</#if>
                <#if 2== article.articleStatement>${statementSpoilersLabel}</#if>
                <#if 3== article.articleStatement>${statementImaginaryLabel}</#if>
            </div>
        </div>
        </#if>


        <#if "" != article.articleAudioURL>
            <div id="articleAudio" data-url="${article.articleAudioURL}"
                 data-author="${article.articleAuthorName}" class="aplayer"></div>
        </#if>
        <#if 3 != article.articleType>
            <div class="vditor-reset article-content">
                ${article.articleContent}
            </div>
        <#else>
            <div id="thoughtProgress"><span class="bar"></span>
                <svg class="icon-video">
                    <use xlink:href="#video"></use>
                </svg>
            </div>
            <div class="vditor-reset article-content" id="articleThought" data-author="${article.articleAuthorName}"
                 data-link="${servePath}${article.articlePermalink}"></div>
        </#if>

        <#if 0 < article.articleRewardPoint>
            <div id="articleRewardContent">
                    <span class="icon-points <#if article.rewarded> ft-red<#else> ft-blue</#if>"
                          <#if !article.rewarded>onclick="Article.reward(${article.oId})"</#if>>
                    ${article.rewardedCnt} ${rewardLabel}</span>

                <div class="vditor-reset">
                    <#if !article.rewarded>
                        <span>
                            ${rewardTipLabel?replace("{articleId}", article.oId)?replace("{point}", article.articleRewardPoint)}
                        </span>
                    <#else>
                        ${article.articleRewardContent}
                    </#if>
                </div>
            </div>
        </#if>

        <div class="article-tail">
            <div class="article-actions action-btns fn-right">
                <#if "" != article.articleToC>
                    <span onclick="Article.toggleToc()" aria-label="${ToCLabel}"
                          class="tooltipped tooltipped-n"><svg class="ft-red icon-unordered-list"><use
                                    xlink:href="#unordered-list"></use></svg></span> &nbsp;
                </#if>

                <#if permissions["commonViewArticleHistory"].permissionGrant && article.articleRevisionCount &gt; 1>
                    <span onclick="Article.revision('${article.oId}')" aria-label="${historyLabel}"
                          class="tooltipped tooltipped-n"><svg class="icon-history"><use
                                    xlink:href="#history"></use></svg></span> &nbsp;
                </#if>

                <#if article.isMyArticle && permissions["commonStickArticle"].permissionGrant>
                    <a class="tooltipped tooltipped-n" aria-label="${stickLabel}"
                       href="javascript:Article.stick('${article.oId}')">
                        <svg class="icon-chevron-up">
                            <use xlink:href="#chevron-up"></use>
                        </svg>
                    </a> &nbsp;
                </#if>
                <#if article.isMyArticle && 3 != article.articleType && permissions["commonUpdateArticle"].permissionGrant>
                    <a href="${servePath}/update?id=${article.oId}" aria-label="${editLabel}"
                       class="tooltipped tooltipped-n">
                        <svg class="icon-edit">
                            <use xlink:href="#edit"></use>
                        </svg>
                    </a> &nbsp;
                </#if>
                <span aria-label="${reportLabel}" class="tooltipped tooltipped-n"
                      onclick="$('#reportDialog').data('type', 0).data('id', '${article.oId}').dialog('open')"
                ><svg><use xlink:href="#icon-report"></use></svg></span> &nbsp;
                <#if permissions["articleUpdateArticleBasic"].permissionGrant>
                    <a class="tooltipped tooltipped-n" href="${servePath}/admin/article/${article.oId}"
                       aria-label="${adminLabel}">
                        <svg class="icon-setting">
                            <use xlink:href="#setting"></use>
                        </svg>
                    </a> &nbsp;
                </#if>
            </div>
        </div>
    </div>
</div>
<div class="main">
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

                                                <#if article.articleOfferedComment.rewardedCnt gt 0>
                                                    <#assign hasRewarded = isLoggedIn && article.articleOfferedComment.commentAuthorId != currentUser.oId && article.articleOfferedComment.rewarded>
                                                    <span aria-label="<#if hasRewarded>${thankedLabel}<#else>${thankLabel} ${article.articleOfferedComment.rewardedCnt}</#if>"
                                                          class="tooltipped tooltipped-n rewarded-cnt <#if hasRewarded>ft-red<#else>ft-fade</#if>">
                                                    <svg class="fn-text-top"><use
                                                                xlink:href="#heart"></use></svg> ${article.articleOfferedComment.rewardedCnt}
                                                </span>
                                                </#if>
                                                <#if 0 == article.articleOfferedComment.commenter.userUAStatus><span
                                                    class="cmt-via ft-fade"
                                                    data-ua="${article.articleOfferedComment.commentUA}"></span></#if>
                                            </span>
                                        <a class="ft-a-title fn-right tooltipped tooltipped-nw"
                                           aria-label="${goCommentLabel}"
                                           href="javascript:Comment.goComment('${servePath}/article/${article.oId}?p=${article.articleOfferedComment.paginationCurrentPageNum}&m=${userCommentViewMode}#${article.articleOfferedComment.oId}')">
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
        <#if article.articleNiceComments?size != 0>
            <div class="module nice">
                <div class="module-header">
                    <svg class="ft-blue">
                        <use xlink:href="#thumbs-up"></use>
                    </svg>
                    ${niceCommentsLabel}
                </div>
                <div class="module-panel list comments">
                    <ul>
                        <#list article.articleNiceComments as comment>
                            <li>
                                <div class="fn-flex">
                                    <a rel="nofollow" href="${servePath}/member/${comment.commentAuthorName}">
                                        <div class="avatar"
                                             aria-label="${comment.commentAuthorName}"
                                             style="background-image:url('${comment.commentAuthorThumbnailURL}')"></div>
                                    </a>
                                    <div class="fn-flex-1">
                                        <div class="fn-clear comment-info ft-smaller">
                                            <span class="fn-left">
                                                <a rel="nofollow"
                                                   href="${servePath}/member/${comment.commentAuthorName}"
                                                   class="ft-gray"><span
                                                            class="ft-gray"><#if comment.commentAuthorNickName != "">${comment.commentAuthorNickName} (${comment.commentAuthorName})<#else>${comment.commentAuthorName}</#if></span></a>
                                                <span class="ft-fade">• ${comment.timeAgo}</span>

                                                <#if comment.rewardedCnt gt 0>
                                                    <#assign hasRewarded = isLoggedIn && comment.commentAuthorId != currentUser.oId && comment.rewarded>
                                                    <span aria-label="<#if hasRewarded>${thankedLabel}<#else>${thankLabel} ${comment.rewardedCnt}</#if>"
                                                          class="tooltipped tooltipped-n rewarded-cnt <#if hasRewarded>ft-red<#else>ft-fade</#if>">
                                                    <svg class="fn-text-top"><use
                                                                xlink:href="#heart"></use></svg> ${comment.rewardedCnt}
                                                </span>
                                                </#if>
                                                <#if 0 == comment.commenter.userUAStatus><span class="cmt-via ft-fade"
                                                                                               data-ua="${comment.commentUA}"></span></#if>
                                            </span>
                                            &nbsp;<#list comment.sysMetal?eval as metal>
                                                <img title="${metal.description}" src="https://fishpi.cn/gen?ver=0.1&scale=0.79&txt=${metal.name}&${metal.attr}"/>
                                            </#list>
                                            <a class="ft-a-title fn-right tooltipped tooltipped-nw"
                                               aria-label="${goCommentLabel}"
                                               href="javascript:Comment.goComment('${servePath}/article/${article.oId}?p=${comment.paginationCurrentPageNum}&m=${userCommentViewMode}#${comment.oId}')">
                                                <svg>
                                                    <use xlink:href="#down"></use>
                                                </svg>
                                            </a>
                                        </div>
                                        <div class="vditor-reset comment">
                                            ${comment.commentContent}
                                        </div>
                                    </div>
                                </div>
                            </li>
                        </#list>
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
                        <#include 'common/comment.ftl' />
                    </#list>
                </ul>
                <div id="bottomComment"></div>
            </div>
            <@pagination url="${servePath}${article.articlePermalink}" query="m=${userCommentViewMode}#comments" pjaxTitle="${article.articleTitle} - ${symphonyLabel}" />
        </div>
        <#if pjax><!---- pjax {#comments} end ----></#if>
    </div>
</div>

<div id="heatBar" class="tooltipped tooltipped-s" aria-label="${postActivityLabel}">
    <i class="heat" style="width:${article.articleHeat*3}px"></i>
</div>
<div id="revision">
    <div id="revisions"></div>
</div>
<div id="reportDialog">
    <div class="form fn-clear">
        <div class="fn-clear"><label><input type="radio" value="0" name="report" checked> ${spamADLabel}</label></div>
        <div class="fn-clear"><label><input type="radio" value="1" name="report"> ${pornographicLabel}</label></div>
        <div class="fn-clear"><label><input type="radio" value="2" name="report"> ${violationOfRegulationsLabel}</label>
        </div>
        <div class="fn-clear"><label><input type="radio" value="3" name="report"> ${allegedlyInfringingLabel}</label>
        </div>
        <div class="fn-clear"><label><input type="radio" value="4" name="report"> ${personalAttacksLabel}</label></div>
        <div class="fn-clear"><label><input type="radio" value="49" name="report"> ${miscLabel}</label></div>
        <br>
        <textarea id="reportTextarea" placeholder="${reportContentLabel}" rows="3"></textarea><br><br>
        <button onclick="Comment.report(this)" class="fn-right green">${reportLabel}</button>
    </div>
</div>
</div>

<#include "footer.ftl">
<#if "" != article.articleToC && 3 != article.articleType>
    <div class="module" id="articleToC">
        <div class="module-panel">
            ${article.articleToC}
        </div>
    </div>
</#if>

<!--<#if discussionViewable>
        <span class="radio-btn" onclick="Comment._toggleReply()"
              data-hasPermission="${permissions['commonAddComment'].permissionGrant?c}">${cmtLabel}</span>
        </#if>-->

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
                                    <form style="display: none" id="uploadEmoji" method="POST" enctype="multipart/form-data">
                                        <input type="file" name="file">
                                    </form>
                                </div>
                            </div>
                        </div>
                        <#if permissions["commonAddCommentAnonymous"].permissionGrant>
                            <label class="cmt-anonymous">${anonymousLabel}<input type="checkbox" id="commentAnonymous"></label>
                        </#if>
                        <label class="cmt-anonymous">${onlyArticleAuthorVisibleLabel}<input type="checkbox" id="commentVisible"></label>
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
<script src="${staticServePath}/js/lib/compress/article-libs.min.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/channel${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/article${miniPostfix}.js?${staticResourceVersion}"></script>
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
    Label.notificationCmtIds = '${notificationCmtIds}';
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
