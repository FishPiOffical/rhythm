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
<#assign threadReplyCount = comment.commentThreadReplyCount!comment.commentReplyCnt>
<#assign isArticleAuthorComment = (comment.commentIsArticleAuthor!false) || (comment.commentAuthorId?? && article.articleAuthorId?? && comment.commentAuthorId == article.articleAuthorId)>
<#assign isCurrentUserComment = (comment.commentIsCurrentUser!false) || (isLoggedIn && comment.commentAuthorId?? && comment.commentAuthorId == currentUser.oId)>
<li id="${comment.oId}"
    data-author="${comment.commentAuthorName}"
    data-comment-type="${comment.commentType!0}"
    data-comment-paragraph-id="${comment.commentParagraphId!}"
    data-comment-paragraph-status="${comment.commentParagraphStatus!0}"
    data-is-author="<#if isArticleAuthorComment>true<#else>false</#if>"
    class="<#if comment.commentStatus == 1>cmt-shield</#if><#if comment.commentNice || comment.commentQnAOffered == 1> cmt-perfect</#if><#if threadReplyCount != 0> cmt-selected</#if>">
    <div class="fn-flex">
        <div>
            <a rel="nofollow" href="${servePath}/member/${comment.commentAuthorName}">
            <div class="avatar tooltipped tooltipped-se"
                 aria-label="${comment.commentAuthorName}" style="background-image:url('${comment.commentAuthorThumbnailURL}')"></div>
            </a>
        </div>
        <div class="fn-flex-1">
            <div class="comment-get-comment list"></div>
            <div class="fn-clear comment-info">
                <span class="fn-left ft-smaller">
                    <a rel="nofollow" href="${servePath}/member/${comment.commentAuthorName}" class="ft-gray"><span class="ft-gray"><#if comment.commentAuthorNickName != "">${comment.commentAuthorNickName} (${comment.commentAuthorName})<#else>${comment.commentAuthorName}</#if></span></a>
                    <#list comment.sysMetal?eval as metal>
                        <img title="${metal.description}" src="${servePath}/gen?id=${metal.id}"/>
                    </#list>
                    <span class="ft-fade">• ${comment.timeAgo}</span>
                </span>
                <span class="fn-right">
                    <#if isLoggedIn && comment.commentAuthorName == currentUser.userName && permissions["commonRemoveComment"].permissionGrant>
                        <span onclick="Comment.remove('${comment.oId}')" aria-label="${removeCommentLabel}"
                              class="tooltipped tooltipped-n ft-a-title hover-show fn-hidden">
                         <svg class="ft-red"><use xlink:href="#remove"></use></svg></span>&nbsp;
                    </#if>
                    <#if permissions["commonViewCommentHistory"].permissionGrant>
                        <span onclick="Article.revision('${comment.oId}', 'comment')" aria-label="${historyLabel}"
                              class="tooltipped tooltipped-n ft-a-title hover-show fn-hidden
                          <#if comment.commentRevisionCount &lt; 2>fn-none</#if>">
                        <svg class="icon-history"><use xlink:href="#history"></use></svg></span> &nbsp;
                    </#if>
                    <#if isLoggedIn && comment.commentAuthorName == currentUser.userName && permissions["commonUpdateComment"].permissionGrant>
                        <span class="tooltipped tooltipped-n ft-a-title hover-show fn-hidden" onclick="Comment.edit('${comment.oId}')"
                              aria-label="${editLabel}"><svg><use xlink:href="#edit"></use></svg></span> &nbsp;
                    </#if>
                    <#if permissions["commentUpdateCommentBasic"].permissionGrant>
                    <a class="tooltipped tooltipped-n ft-a-title hover-show fn-hidden" href="${servePath}/admin/comment/${comment.oId}"
                       aria-label="${adminLabel}"><svg class="icon-setting"><use xlink:href="#setting"></use></svg></a> &nbsp;
                    </#if>
                    <#if comment.commentOriginalCommentId != ''>
                        <#assign originalAuthorLabel = comment.commentOriginalAuthorNickName!''>
                        <#if originalAuthorLabel == ''><#assign originalAuthorLabel = comment.commentOriginalAuthorName!'原评论'></#if>
                        <span class="comment-origin fn-pointer ft-a-title tooltipped tooltipped-nw" aria-label="${goCommentLabel}"
                              onclick="Comment.showReply('${comment.commentOriginalCommentId}', this, 'comment-get-comment')">
                            <svg class="icon-reply-to"><use xlink:href="#reply-to"></use></svg>
                            <span>回复 @${originalAuthorLabel?html}</span>
                        </span>
                    </#if>
                </span>
            </div>
            <div class="vditor-reset comment">
            <#if (comment.commentType!0) == 1 && (comment.commentParagraphStatus!0) == 1 && (comment.commentParagraphSnapshot!'') != "">
            <div class="comment-paragraph-context">${comment.commentParagraphSnapshot?html}</div>
            </#if>
            ${comment.commentContent}
            </div>
            <#if threadReplyCount gt 0>
                <div class="comment-thread" data-root-id="${comment.oId}">
                    <div class="comment-thread__list">
                        <#list comment.commentThreadReplies![] as threadReply>
                            <#assign threadAuthorLabel = threadReply.commentAuthorNickName!''>
                            <#if threadAuthorLabel != ''><#assign threadAuthorLabel = threadAuthorLabel + ' (' + threadReply.commentAuthorName + ')'><#else><#assign threadAuthorLabel = threadReply.commentAuthorName></#if>
                            <#assign threadOriginalAuthorLabel = threadReply.commentOriginalAuthorNickName!''>
                            <#if threadOriginalAuthorLabel == ''><#assign threadOriginalAuthorLabel = threadReply.commentOriginalAuthorName!'原评论'></#if>
                            <#assign threadDepth = threadReply.commentThreadDepth!0>
                            <div id="${threadReply.oId}" class="comment-thread__reply<#if threadDepth gt 0> comment-thread__reply--nested</#if>"
                                 data-thread-depth="${threadDepth}" style="--comment-thread-indent:${threadDepth * 16}px"
                                 <#if (threadReply.commentParagraphId!'') != ''>data-comment-paragraph-id="${threadReply.commentParagraphId?html}"
                                 data-comment-paragraph-status="${threadReply.commentParagraphStatus!0}"</#if>>
                                <a rel="nofollow" href="${servePath}/member/${threadReply.commentAuthorName}" class="comment-thread__avatar"
                                   aria-label="${threadReply.commentAuthorName}"
                                   style="background-image:url('${threadReply.commentAuthorThumbnailURL}')"></a>
                                <div class="comment-thread__body">
                                    <div class="comment-thread__meta">
                                        <a rel="nofollow" href="${servePath}/member/${threadReply.commentAuthorName}">${threadAuthorLabel?html}</a>
                                        <span class="comment-origin-inline">回复 @${threadOriginalAuthorLabel?html}</span>
                                        <span class="ft-fade">• ${threadReply.timeAgo}</span>
                                    </div>
                                    <div class="comment-thread__content vditor-reset">${threadReply.commentContent}</div>
                                    <div class="comment-thread__actions comment-action__bar">
                                        <div class="comment-action__left">
                                            <div class="comment-reaction-shell"
                                                 data-target-id="${threadReply.oId}"
                                                 data-current-user-reaction="${threadReply.currentUserReaction!''}"
                                                 data-summary='${(threadReply.reactionSummary!'[]')?html}'></div>
                                        </div>
                                        <span class="action-btns comment-thread__action-menu">
                                            <#assign threadIsCurrentUserComment = (threadReply.commentIsCurrentUser!false) || (isLoggedIn && threadReply.commentAuthorId?? && threadReply.commentAuthorId == currentUser.oId)>
                                            <#assign threadHasRewarded = isLoggedIn && !threadIsCurrentUserComment && (threadReply.rewarded!false)>
                                            <#if isLoggedIn && permissions["commonAddComment"].permissionGrant>
                                                <span aria-label="${replyLabel}" class="icon-reply-btn tooltipped tooltipped-n"
                                                      onclick="Comment.reply('${threadReply.commentAuthorName}', '${threadReply.oId}')">
                                                    <svg class="icon-reply"><use xlink:href="#reply"></use></svg>
                                                </span>
                                            </#if>
                                            <#if permissions["commonViewCommentHistory"].permissionGrant && (threadReply.commentRevisionCount!0) gte 2>
                                                <span aria-label="${historyLabel}" onclick="Article.revision('${threadReply.oId}', 'comment')"
                                                      class="comment-history-action tooltipped tooltipped-n">
                                                    <svg class="icon-history"><use xlink:href="#history"></use></svg>
                                                </span>
                                            </#if>
                                            <#if threadIsCurrentUserComment && permissions["commonUpdateComment"].permissionGrant>
                                                <span aria-label="${editLabel}" class="comment-edit-action tooltipped tooltipped-n"
                                                      onclick="Comment.edit('${threadReply.oId}')">
                                                    <svg><use xlink:href="#edit"></use></svg>
                                                </span>
                                            </#if>
                                            <span class="tooltipped tooltipped-n<#if threadHasRewarded> ft-red</#if>" aria-label="${thankLabel}"
                                            <#if !threadHasRewarded && permissions["commonThankComment"].permissionGrant>
                                                onclick="Comment.thank('${threadReply.oId}', '${csrfToken}', '${threadReply.commentThankLabel!''}', ${threadReply.commentAnonymous!0}, this)"
                                            <#elseif !threadHasRewarded>
                                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                                            </#if>><svg class="fn-text-top icon-heart"><use xlink:href="#heart"></use></svg> ${threadReply.rewardedCnt!0}</span>
                                            <span class="tooltipped tooltipped-n<#if isLoggedIn && 0 == (threadReply.commentVote!-1)> ft-red</#if>"
                                                  aria-label="${upLabel}"
                                            <#if permissions["commonGoodComment"].permissionGrant>
                                                onclick="Article.voteUp('${threadReply.oId}', 'comment', this)"
                                            <#else>
                                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                                            </#if>><svg class="icon-thumbs-up"><use xlink:href="#thumbs-up"></use></svg> ${threadReply.commentGoodCnt!0}</span>
                                            <span class="tooltipped tooltipped-n<#if isLoggedIn && 1 == (threadReply.commentVote!-1)> ft-red</#if>"
                                                  aria-label="${downLabel}"
                                            <#if permissions["commonBadComment"].permissionGrant>
                                                onclick="Article.voteDown('${threadReply.oId}', 'comment', this)"
                                            <#else>
                                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                                            </#if>><svg class="icon-thumbs-down"><use xlink:href="#thumbs-down"></use></svg> ${threadReply.commentBadCnt!0}</span>
                                            <span aria-label="${reportLabel}" class="tooltipped tooltipped-n"
                                                  onclick="$('#reportDialog').data('type', 1).data('id', '${threadReply.oId}').dialog('open')"
                                            ><svg><use xlink:href="#icon-report"></use></svg></span>
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </#list>
                    </div>
                    <#if comment.commentThreadHasMore!false>
                        <button type="button" class="comment-thread__more" onclick="Comment.showThreadReplies('${comment.oId}', this)">
                            查看全部 ${threadReplyCount} 条回复
                        </button>
                    </#if>
                </div>
            </#if>
            <div class="comment-action">
                <div class="ft-fade fn-clear comment-action__bar"><div class="comment-action__left"><div class="comment-reaction-shell"
                             data-target-id="${comment.oId}"
                             data-current-user-reaction="${comment.currentUserReaction!''}"
                             data-summary='${(comment.reactionSummary!'[]')?html}'></div></div><!--
                 --><span class="fn-hidden hover-show action-btns comment-action-menu">
                    <#if isLoggedIn && permissions["commonAddComment"].permissionGrant>
                        <span aria-label="${replyLabel}" class="icon-reply-btn tooltipped tooltipped-n"
                              onclick="Comment.reply('${comment.commentAuthorName}', '${comment.oId}')">
                        <svg class="icon-reply"><use xlink:href="#reply"></use></svg></span>
                    </#if>
                        <#assign hasRewarded = isLoggedIn && !isCurrentUserComment && comment.rewarded>
                        <span class="tooltipped tooltipped-n <#if hasRewarded>ft-red</#if>" aria-label="${thankLabel}"
                        <#if !hasRewarded && permissions["commonThankComment"].permissionGrant>
                            onclick="Comment.thank('${comment.oId}', '${csrfToken}', '${comment.commentThankLabel}', ${comment.commentAnonymous}, this)"
                        <#elseif !hasRewarded>
                              onclick="Article.permissionTip(Label.noPermissionLabel)"
                        </#if>><svg class="fn-text-top icon-heart"><use xlink:href="#heart"></use></svg> ${comment.rewardedCnt}</span>
                        <span class="tooltipped tooltipped-n<#if isLoggedIn && 0 == comment.commentVote> ft-red</#if>"
                              aria-label="${upLabel}"
                    <#if permissions["commonGoodComment"].permissionGrant>
                          onclick="Article.voteUp('${comment.oId}', 'comment', this)"
                    <#else>
                            onclick="Article.permissionTip(Label.noPermissionLabel)"
                    </#if>><svg class="icon-thumbs-up"><use xlink:href="#thumbs-up"></use></svg> ${comment.commentGoodCnt}</span>
                        <span class="tooltipped tooltipped-n<#if isLoggedIn && 1 == comment.commentVote> ft-red</#if>"
                              aria-label="${downLabel}"
                    <#if permissions["commonBadComment"].permissionGrant>
                          onclick="Article.voteDown('${comment.oId}', 'comment', this)"
                    <#else>
                            onclick="Article.permissionTip(Label.noPermissionLabel)"
                    </#if>><svg class="icon-thumbs-down"><use xlink:href="#thumbs-down"></use></svg> ${comment.commentBadCnt}</span>

                   <#if isLoggedIn && !article.offered && article.articleAuthorId == currentUser.oId && comment.commentAuthorName != currentUser.userName && article.articleQnAOfferPoint != 0>
                    <span aria-label="${adoptLabel}" class="icon-reply-btn tooltipped tooltipped-n"
                          onclick="Comment.accept('${adoptTipLabel?replace('{point}', article.articleQnAOfferPoint)}', '${comment.oId}', this)"
                    ><svg><use xlink:href="#icon-accept"></use></svg></span>
                   </#if>
                    <span aria-label="${reportLabel}" class="tooltipped tooltipped-n"
                          onclick="$('#reportDialog').data('type', 1).data('id', '${comment.oId}').dialog('open')"
                    ><svg><use xlink:href="#icon-report"></use></svg></span>
                    </span></div>
                <div class="comment-replies list"></div>
            </div>
        </div>
    </div>
</li>
