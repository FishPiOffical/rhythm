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
        <#if !article??><#assign postTitle = addLongArticleLabel><#else><#assign postTitle = updateArticleLabel></#if>
        <@head title="${postTitle} - ${symphonyLabel}">
        <meta name="robots" content="none" />
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/home.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "../header.ftl">
        <div class="main post" style="padding-bottom: 20px">
            <div class="fn-flex-1 fn-clear">
                <input type="text" id="articleTitle" autocomplete="off" tabindex="1"<#if requisite> readonly disabled</#if>
                       value="<#if article??>${article.articleTitle}</#if>" placeholder="${titleLabel}" />
                <div class="post-article-content">
                    <div id="articleContent"
                         data-placeholder="${addLongArticleEditorPlaceholderLabel}"></div>
                    <textarea class="fn-none"><#if article??>${article.articleContent?html}</#if><#if at??>@${at}</#if></textarea>
                </div>

                <div class="wrapper">
                    <br>
                    <#if requisite>
                        <div class="tip error">
                            <ul>
                                <li>${requisiteMsg}</li>
                            </ul>
                        </div>
                    <#else>
                        <div class="tip" id="addArticleTip"></div>
                    </#if>
                </div>
                <div class="tip" style="margin:12px 8px;padding:12px 14px;border-radius:10px;background:linear-gradient(135deg,#f8fbff 0%,#f1f6ff 100%);border:1px solid #dce8ff;color:#304050;box-shadow:0 6px 20px rgba(0,40,120,0.06);">
                    <div style="font-weight:600;font-size:14px;margin-bottom:6px;">长文章奖励提示</div>
                    <div style="line-height:1.6;font-size:13px;">
                        · 默认计入好帖奖励：45% 活跃度（每日首篇有效）<br>
                        · 如为非原创或 AI 生成，审核后将撤回奖励并降级为普通帖子
                    </div>
                </div>
                <br/>
                <div class="fn-clear wrapper">
                    <div class="fn-right">
                        <#if article?? && permissions["commonRemoveArticle"].permissionGrant>
                            <button class="red" tabindex="11" onclick="AddArticle.remove('${csrfToken}', this)">${removeArticleLabel}</button>
                        </#if>
                        <#if article??>
                            <#if permissions["commonUpdateArticle"].permissionGrant>
                            <button class="green" id="addArticleBtn" tabindex="10"<#if requisite> readonly disabled</#if>
                                    onclick="AddArticle.add('${csrfToken}', this)">${submitLabel}</button>
                            </#if>
                        <#else>
                            <#if permissions["commonAddArticle"].permissionGrant>
                            <button class="green" id="addArticleBtn" tabindex="10"<#if requisite> readonly disabled</#if>
                                    onclick="AddArticle.confirmAdd('${csrfToken}', this)">${postLabel}</button>
                            </#if>
                        </#if>
                    </div>
                </div>
            </div>
        </div>
        <#include "../footer.ftl">
        <script>
            Label.articleTitleErrorLabel = "${articleTitleErrorLabel}";
            Label.articleContentErrorLabel = "${articleContentErrorLabel}";
            Label.tagsErrorLabel = "${tagsErrorLabel}";
            Label.userName = "${currentUser.userName}";
            Label.recordDeniedLabel = "${recordDeniedLabel}";
            Label.recordDeviceNotFoundLabel = "${recordDeviceNotFoundLabel}";
            Label.uploadLabel = "${uploadLabel}";
            Label.audioRecordingLabel = '${audioRecordingLabel}';
            Label.uploadingLabel = '${uploadingLabel}';
            Label.articleRewardPointErrorLabel = '${articleRewardPointErrorLabel}';
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
            Label.discussionLabel = '${discussionLabel}';
            Label.insertEmojiLabel = '${insertEmojiLabel}';
            Label.commonAtUser = '${permissions["commonAtUser"].permissionGrant?c}';
            Label.requisite = ${requisite?c};
            <#if article??>Label.articleOId = '${article.oId}' ;</#if>
            Label.articleType = 6;
            Label.confirmRemoveLabel = '${confirmRemoveLabel}';
        </script>
        <script src="${staticServePath}/js/add-article${miniPostfix}.js?${staticResourceVersion}"></script>
        <script src="${staticServePath}/js/lib/sweetalert2.all.min.js"></script>
    </body>
</html>
