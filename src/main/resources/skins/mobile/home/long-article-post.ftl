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
    </head>
    <body>
        <#include "../header.ftl">
        <div class="main">
            <div class="wrapper post">
                <div class="fn-hr10"></div>
                <div class="fn-flex-1 fn-clear">
                    <div class="form">
                        <input type="text" id="articleTitle" tabindex="1"
                               value="<#if article??>${article.articleTitle}</#if>" placeholder="${titleLabel}" />
                    </div>
                    <div class="article-content">
                        <div id="articleContent"
                             data-placeholder="${addLongArticleEditorPlaceholderLabel}"></div>
                        <textarea class="fn-none"><#if article??>${article.articleContent?html}</#if></textarea>
                    </div>
                    <div class="fn-hr10"></div>
                    <div class="tip" id="addArticleTip"></div>
                    <div class="fn-hr10"></div>
                    <div class="fn-clear">
                        <#if article??>
                            <#if permissions["commonUpdateArticle"].permissionGrant>
                                <button class="fn-right" tabindex="10" onclick="AddArticle.add('${csrfToken}', this)">${submitLabel}</button>
                            </#if>
                        <#else>
                            <#if permissions["commonAddArticle"].permissionGrant>
                                <button class="fn-right" tabindex="10" onclick="AddArticle.confirmAdd('${csrfToken}', this)">${postLabel}</button>
                            </#if>
                        </#if>
                        <#if article?? && permissions["commonRemoveArticle"].permissionGrant>
                            <button class="red fn-right" tabindex="11" onclick="AddArticle.remove('${csrfToken}', this)">${removeArticleLabel}</button>
                        </#if>
                    </div>
                    <br/>
                    <div class="fn-clear">
                        <svg><use xlink:href="#book"></use></svg> ${longArticleLabel}
                        <span class="ft-gray">${longArticleTipLabel}</span>
                    </div>
                </div>
            </div>
        </div>
        <#include "../footer.ftl"/>
        <script src="${staticServePath}/js/lib/sound-recorder/SoundRecorder.js"></script>
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
            Label.discussionLabel = '${discussionLabel}';
            Label.insertEmojiLabel = '${insertEmojiLabel}';
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
