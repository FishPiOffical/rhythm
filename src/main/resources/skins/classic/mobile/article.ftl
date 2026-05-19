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
        <@head title="${article.articleTitle} - ${symphonyLabel}">
        <meta name="keywords" content="<#list article.articleTagObjs as articleTag>${articleTag.tagTitle}<#if articleTag?has_next>,</#if></#list>" />
        <meta name="description" content="${article.articlePreviewContent}"/>
        <#if 1 == article.articleStatus || 1 == article.articleAuthor.userStatus || 1 == article.articleType>
        <meta name="robots" content="NOINDEX,NOFOLLOW" />
        </#if>
        </@head>
        <link rel="stylesheet" href="${staticServePath}/js/lib/compress/article.min.css?${staticResourceVersion}">
    </head>
    <body itemscope itemtype="http://schema.org/Product"<#if 6 == article.articleType> class="long-article-page"</#if>>
        <img itemprop="image" class="fn-none"  src="${staticServePath}/images/faviconH.png" />
        <p itemprop="description" class="fn-none">"${article.articlePreviewContent}"</p>
        <#include "header.ftl">
        <div class="main">
            <div class="article-actions fn-clear" style="margin-bottom: 10px;">
                    <span class="fn-right">
                        <span id="thankArticle" aria-label="${thankLabel}"
                              class="tooltipped tooltipped-n has-cnt<#if article.thanked> ft-red</#if>"
                              <#if permissions["commonThankArticle"].permissionGrant>
                            <#if !article.thanked>
                                onclick="Article.thankArticle('${article.oId}', ${article.articleAnonymous})"
                            </#if>
                        <#else>
                            onclick="Article.permissionTip(Label.noPermissionLabel)"
                                </#if>><svg class="fn-text-top"><use xlink:href="#heart"></use></svg> ${article.thankedCnt}</span>
                        <span class="tooltipped tooltipped-n has-cnt<#if isLoggedIn && 0 == article.articleVote> ft-red</#if>" aria-label="${upLabel}"
                            <#if permissions["commonGoodArticle"].permissionGrant>
                                onclick="Article.voteUp('${article.oId}', 'article', this)"
                            <#else>
                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                                </#if>><svg><use xlink:href="#thumbs-up"></use></svg> ${article.articleGoodCnt}</span>
                        <span  class="tooltipped tooltipped-n has-cnt<#if isLoggedIn && 1 == article.articleVote> ft-red</#if>" aria-label="${downLabel}"
                            <#if permissions["commonBadArticle"].permissionGrant>
                                onclick="Article.voteDown('${article.oId}', 'article', this)"
                            <#else>
                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                                </#if>><svg><use xlink:href="#thumbs-down"></use></svg> ${article.articleBadCnt}</span>
                        <#if isLoggedIn && isFollowing>
                            <span class="tooltipped tooltipped-n has-cnt ft-red" aria-label="${uncollectLabel}"
                                <#if permissions["commonFollowArticle"].permissionGrant>
                                    onclick="Util.unfollow(this, '${article.oId}', 'article', ${article.articleCollectCnt})"
                                <#else>
                                    onclick="Article.permissionTip(Label.noPermissionLabel)"
                                    </#if>><svg><use xlink:href="#star"></use></svg> ${article.articleCollectCnt}</span>
                        <#else>
                            <span class="tooltipped tooltipped-n has-cnt" aria-label="${collectLabel}"
                            <#if permissions["commonFollowArticle"].permissionGrant>
                                onclick="Util.follow(this, '${article.oId}', 'article', ${article.articleCollectCnt})"
                            <#else>
                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                                    </#if>><svg><use xlink:href="#star"></use></svg> ${article.articleCollectCnt}</span>
                        </#if>
                        <#if isLoggedIn && isWatching>
                            <span class="tooltipped tooltipped-n has-cnt ft-red" aria-label="${unfollowLabel}"
                            <#if permissions["commonWatchArticle"].permissionGrant>
                                onclick="Util.unfollow(this, '${article.oId}', 'article-watch', ${article.articleWatchCnt})"
                                <#else>
                                    onclick="Article.permissionTip(Label.noPermissionLabel)"
                                    </#if>><svg class="icon-view"><use xlink:href="#view"></use></svg> ${article.articleWatchCnt}</span>
                            <#else>
                            <span class="tooltipped tooltipped-n has-cnt" aria-label="${followLabel}"
                                <#if permissions["commonWatchArticle"].permissionGrant>
                                    onclick="Util.follow(this, '${article.oId}', 'article-watch', ${article.articleWatchCnt})"
                                    <#else>
                                        onclick="Article.permissionTip(Label.noPermissionLabel)"
                                    </#if>><svg class="icon-view"><use xlink:href="#view"></use></svg> ${article.articleWatchCnt}</span>
                        </#if>
                        <#if 0 < article.articleRewardPoint>
                            <span class="tooltipped tooltipped-n has-cnt<#if article.rewarded> ft-red</#if>"
                                  <#if !article.rewarded>onclick="Article.reward(${article.oId})"</#if>
                        aria-label="${rewardLabel}"><svg class="icon-points"><use xlink:href="#points"></use></svg> ${article.rewardedCnt}</span>
                        </#if>
                    </span>
            </div>
            <div class="article-main">
            <div class="wrapper">
                <#if showTopAd>
                    ${HeaderBannerLabel}
                </#if>
                <h1 class="article-title" itemprop="name">
                    <@icon article.articlePerfect article.articleType></@icon>
                    <a class="ft-a-title" href="${servePath}${article.articlePermalink}" rel="bookmark">
                        ${article.articleTitleEmoj}
                    </a>
                </h1>
                <div style="margin-bottom: 3px">
                    <#if 6 != article.articleType && article.sysMetal != "[]">
                        <#list article.sysMetal?eval as metal>
                            <img title="${metal.description}" src="${servePath}/gen?id=${metal.id}"/>
                        </#list>
                    </#if>
                </div>
                <div class="article-info">
                    <#if 6 == article.articleType>
                    <#else>
                    <a rel="author" href="${servePath}/member/${article.articleAuthorName}"
                       title="${article.articleAuthorName}"><div class="avatar" style="background-image:url('${article.articleAuthorThumbnailURL48}')"></div></a>
                    <div class="article-params">
                        <a rel="author" href="${servePath}/member/${article.articleAuthorName}" class="ft-gray"
                           title="${article.articleAuthorName}"><strong><#if article.articleAuthorNickName != "">${article.articleAuthorNickName}<#else>${article.articleAuthorName}</#if></strong></a>
                        <span class="ft-gray article-meta-line">
                        <#if article.articleCity != "">
                        &nbsp;•&nbsp;
                        <a href="${servePath}/city/${article.articleCity}" class="ft-gray">
                                <span class="article__cnt">${article.articleCity}</span>
                        </a>
                        </#if>
                        &nbsp;•&nbsp;
                        <a rel="nofollow" class="ft-gray" href="#comments">
                            <b class="article-level<#if article.articleCommentCount lt 40>${(article.articleCommentCount/10)?int}<#else>4</#if>">${article.articleCommentCount}</b> ${cmtLabel}</a>
                        &nbsp;•&nbsp;
                        <span class="article-level<#if article.articleViewCount lt 400>${(article.articleViewCount/100)?int}<#else>4</#if>">
                        <#if article.articleViewCount < 1000>
                        ${article.articleViewCount}
                        <#else>
                        ${article.articleViewCntDisplayFormat}
                        </#if>
                        </span>
                        ${viewLabel}
                             <#if article.articleQnAOfferPoint != 0>
                                &nbsp;•&nbsp;
                                <span class="article-level<#if article.articleQnAOfferPoint lt 400>${(article.articleQnAOfferPoint/100)?int}<#else>4</#if>">${article.articleQnAOfferPoint?c}</span>
                                 ${qnaOfferLabel}
                             </#if>
                        &nbsp;•&nbsp;
                        ${article.timeAgo}
                    </span>
                        <#if articleVisitSourceStats?? && 0 < articleVisitSourceStats?size>
                        <span class="article-visit-sources">
                            <#list articleVisitSourceStats as sourceStat>
                            <span class="article-visit-source article-visit-source--${sourceStat.sourceCss}" aria-label="${sourceStat.sourceName}">
                                <span class="article-visit-source__icon"><svg><use xlink:href="#${sourceStat.sourceIcon}"></use></svg></span>${sourceStat.visitCount}
                            </span>
                            </#list>
                        </span>
                        </#if>
                        <div class="article-tags">
                        <#list article.articleTagObjs as articleTag>
                        <a rel="tag" class="tag" href="${servePath}/tag/${articleTag.tagURI}">${articleTag.tagTitle}</a>&nbsp;
                        </#list>
                        </div>
                    </div>
                    </#if>
                    <div class="article-reaction-shell"
                         data-target-id="${article.oId}"
                         data-current-user-reaction="${article.currentUserReaction!''}"
                         data-summary='${(article.reactionSummary!'[]')?html}'></div>
                    <div class="article-actions fn-clear" style="margin-top: 8px;">
                        <span class="fn-right">
                            <#if permissions["commonViewArticleHistory"].permissionGrant && article.articleRevisionCount &gt; 1>
                                <span onclick="Article.revision('${article.oId}')" aria-label="${historyLabel}"
                                      class="tooltipped tooltipped-w"><svg class="icon-history"><use xlink:href="#history"></use></svg></span>
                            </#if>
                            <span aria-label="${reportLabel}" class="tooltipped tooltipped-n"
                                  onclick="$('#reportDialog').data('type', 0).data('id', '${article.oId}').dialog('open')">
                                <svg><use xlink:href="#icon-report"></use></svg>
                            </span>
                            <#if article.isMyArticle && 3 != article.articleType && permissions["commonUpdateArticle"].permissionGrant>
                                <a href="${servePath}/update?id=${article.oId}"><svg><use xlink:href="#edit"></use></svg></a>
                            </#if>
                            <#if article.isMyArticle && permissions["commonStickArticle"].permissionGrant>
                                <a class="tooltipped tooltipped-n" aria-label="${stickLabel}"
                                   href="javascript:Article.stick('${article.oId}')"><svg><use xlink:href="#chevron-up"></use></svg></a>
                            </#if>
                            <#if permissions["articleUpdateArticleBasic"].permissionGrant>
                                <a class="tooltipped tooltipped-n" href="${servePath}/admin/article/${article.oId}" aria-label="${adminLabel}"><svg><use xlink:href="#setting"</svg></a>
                            </#if>
                        </span>
                    </div>
                </div>
                <#if 0!= article.articleStatement>
                <div style="display: flex ;margin-bottom: 10px">
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
                         data-author="${article.articleAuthorName}" class="aplayer article-content"></div>
                </#if>
                <#if 3 != article.articleType>
                <div class="vditor-reset article-content<#if 6 == article.articleType> long-article-content</#if>">${article.articleContent}</div>
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
                <div class="vditor-reset<#if !article.rewarded> reward</#if>" id="articleRewardContent">
                     <#if !article.rewarded>
                     <span>
                        ${rewardTipLabel?replace("{articleId}", article.oId)?replace("{point}", article.articleRewardPoint)}
                    </span>
                    <#else>
                    ${article.articleRewardContent}
                    </#if>
                </div>
                <div class="fn-hr10"></div>
                </#if>
                <#include "common/article-adjacent-nav.ftl">

                <#if 6 == article.articleType>
                <div class="long-article-meta">
                    <a href="${servePath}/member/${article.articleAuthorName}" class="long-article-author">${article.articleAuthorName}</a>
                    <span class="long-article-time">${article.timeAgo}</span>
                </div>
                <#if article.isMyArticle && article.longArticleReadStat??>
                <div class="module" style="padding:12px;margin-top:8px;">
                    <div class="ft__smaller ft__fade">长文阅读激励</div>
                    <div class="fn-hr5"></div>
                    <div class="fn-flex" style="justify-content: space-between;">
                        <div>
                            <div class="ft__smaller ft__fade">未结算</div>
                            <div>注册 ${article.longArticleReadStat.registeredUnsettledCnt} / 未注册 ${article.longArticleReadStat.anonymousUnsettledCnt}</div>
                        </div>
                        <div>
                            <div class="ft__smaller ft__fade">总计</div>
                            <div>注册 ${article.longArticleReadStat.registeredTotalCnt} / 未注册 ${article.longArticleReadStat.anonymousTotalCnt}</div>
                        </div>
                    </div>
                    <div class="fn-hr5"></div>
                    <div class="ft__smaller ft__fade">未注册以 IP+UA 去重，当窗封顶 100</div>
                </div>
                </#if>
                </#if>
                <#if article.offered>
                <div class="module nice">
                    <div class="module-header">
                        <svg class="ft-blue"><use xlink:href="#iconAdopt"></use></svg>
                        ${adoptLabel}
                    </div>
                    <div class="module-panel list comments">
                        <ul>
                            <li>
                                <div class="fn-flex">
                                    <a rel="nofollow" href="${servePath}/member/${article.articleOfferedComment.commentAuthorName}">
                                        <div class="avatar tooltipped tooltipped-se"
                                             aria-label="${article.articleOfferedComment.commentAuthorName}" style="background-image:url('${article.articleOfferedComment.commentAuthorThumbnailURL}')"></div>
                                    </a>
                                    <div class="fn-flex-1">
                                        <div class="fn-clear comment-info ft-smaller">
                                            <span class="fn-left">
                                                <a rel="nofollow" href="${servePath}/member/${article.articleOfferedComment.commentAuthorName}" class="ft-gray"><span class="ft-gray">${article.articleOfferedComment.commentAuthorName}</span></a>
                                                <span class="ft-fade">• ${article.articleOfferedComment.timeAgo}</span>

                                                <#if article.articleOfferedComment.rewardedCnt gt 0>
                                                    <#assign hasRewarded = isLoggedIn && article.articleOfferedComment.commentAuthorId != currentUser.oId && article.articleOfferedComment.rewarded>
                                                <span aria-label="<#if hasRewarded>${thankedLabel}<#else>${thankLabel} ${article.articleOfferedComment.rewardedCnt}</#if>"
                                                      class="tooltipped tooltipped-n rewarded-cnt <#if hasRewarded>ft-red<#else>ft-fade</#if>">
                                                    <svg class="fn-text-top"><use xlink:href="#heart"></use></svg> ${article.articleOfferedComment.rewardedCnt}
                                                </span>
                                                </#if>
                                            </span>
                                            <a class="ft-a-title fn-right tooltipped tooltipped-nw" aria-label="${goCommentLabel}"
                                               href="javascript:Comment.goComment('${servePath}/article/${article.oId}?p=${article.articleOfferedComment.paginationCurrentPageNum}&m=${userCommentViewMode}<#if commentSort == "hot">&sort=hot</#if><#if commentAuthorFilter>&author=1</#if>#${article.articleOfferedComment.oId}')"><svg><use xlink:href="#down"></use></svg></a>
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
                            <svg class="ft-blue"><use xlink:href="#thumbs-up"></use></svg>
                            ${niceCommentsLabel}
                        </div>
                        <div class="module-panel list comments">
                            <ul>
                            <#list article.articleNiceComments as comment>
                            <li>
                                    <div class="fn-flex">
                                        <a rel="nofollow" href="${servePath}/member/${comment.commentAuthorName}">
                                            <div class="avatar tooltipped tooltipped-se"
                                                 aria-label="${comment.commentAuthorName}" style="background-image:url('${comment.commentAuthorThumbnailURL}')"></div>
                                        </a>
                                        <div class="fn-flex-1">
                                            <div class="fn-clear comment-info ft-smaller">
                                                <span class="fn-left">
                                                    <a rel="nofollow" href="${servePath}/member/${comment.commentAuthorName}" class="ft-gray"><span class="ft-gray"><#if comment.commentAuthorNickName != "">${comment.commentAuthorNickName} (${comment.commentAuthorName})<#else>${comment.commentAuthorName}</#if></span></a>
                                                    <span class="ft-fade">• ${comment.timeAgo}</span>

                                                    <#if comment.rewardedCnt gt 0>
                                                    <#assign hasRewarded = isLoggedIn && comment.commentAuthorId != currentUser.oId && comment.rewarded>
                                                    <span aria-label="<#if hasRewarded>${thankedLabel}<#else>${thankLabel} ${comment.rewardedCnt}</#if>"
                                                          class="tooltipped tooltipped-n rewarded-cnt <#if hasRewarded>ft-red<#else>ft-fade</#if>">
                                                        <svg class="fn-text-top"><use xlink:href="#heart"></use></svg> ${comment.rewardedCnt}
                                                    </span>
                                                    </#if>
                                                </span>
                                                &nbsp;<#list comment.sysMetal?eval as metal>
                                                <img title="${metal.description}" src="${servePath}/gen?id=${metal.id}"/>
                                                </#list>
                                                <a class="ft-a-title fn-right tooltipped tooltipped-nw" aria-label="${goCommentLabel}"
                                                   href="javascript:Comment.goComment('${servePath}/article/${article.oId}?p=${comment.paginationCurrentPageNum}&m=${userCommentViewMode}<#if commentSort == "hot">&sort=hot</#if><#if commentAuthorFilter>&author=1</#if>#${comment.oId}')"><svg><use xlink:href="#down"></use></svg></a>
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

            </div>
            <div>
                <#if pjax><!---- pjax {#comments} start ----></#if>
                <div class="fn-clear" id="comments">
                    <div class="list comments">
                            <#if discussionViewable && article.articleCommentable>
                                <div class="comment__reply">
                                    <#if isLoggedIn && permissions["commonAddComment"].permissionGrant>
                                        <div class="fn-flex">
                                            <span class="avatar"
                                                  style="background-image: url('${currentUser.userAvatarURL48}');"></span>
                                            <span class="reply__text fn-flex-1 commentToggleEditorBtn"
                                                  onclick="Comment._toggleReply();">请输入回帖内容 ...</span>
                                        </div>
                                    <#else>
                                        <div class="reply__text fn-flex-1 commentToggleEditorBtn" onclick="Util.goLogin();">${loginDiscussLabel}</div>
                                    </#if>
                                </div>
                            </#if>
                            <div class="comments-header fn-clear">
                                <div class="comments-header__main">
                                    <span class="article-cmt-cnt">${commentDisplayCount!article.articleCommentCount} ${cmtLabel}</span>
                                    <span class="fn-right<#if article.articleComments?size == 0> fn-none</#if>">
                                        <a class="tooltipped tooltipped-nw" href="#bottomComment" aria-label="${jumpToBottomCommentLabel}"><svg><use xlink:href="#chevron-down"></use></svg></a>
                                    </span>
                                </div>
                                <#if article.articleCommentCount != 0>
                                    <div class="comment-filterbar">
                                        <div class="comment-segment">
                                            <a class="comment-segment__item<#if !commentAuthorFilter> comment-segment__item--active</#if>"
                                               href="${servePath}${article.articlePermalink}?${commentAllQuery}">全部</a>
                                            <a class="comment-segment__item<#if commentAuthorFilter> comment-segment__item--active</#if>"
                                               href="${servePath}${article.articlePermalink}?${commentAuthorQuery}">楼主</a>
                                        </div>
                                        <div class="comment-segment">
                                            <a class="comment-segment__item<#if commentSort == 'hot'> comment-segment__item--active</#if>"
                                               href="${servePath}${article.articlePermalink}?${commentHotQuery}">热门</a>
                                            <a class="comment-segment__item<#if commentSort != 'hot' && 0 == userCommentViewMode> comment-segment__item--active</#if>"
                                               href="${servePath}${article.articlePermalink}?${commentAscQuery}">正序</a>
                                            <a class="comment-segment__item<#if commentSort != 'hot' && 1 == userCommentViewMode> comment-segment__item--active</#if>"
                                               href="${servePath}${article.articlePermalink}?${commentDescQuery}">倒序</a>
                                        </div>
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
                                <div id="bottomComment"></div>
                            </ul>
                        </div>
                    <@pagination url=article.articlePermalink query="${commentPaginationQuery}" />
                </div>
                <#if pjax><!---- pjax {#comments} end ----></#if>
            </div>
            </div>
            <div class="side wrapper">
                <#if 6 != article.articleType>
                <#if showSideAd>
                <#if ADLabel!="">
                <div class="module">
                    <div class="module-header">
                        <h2>
                            ${sponsorLabel}
                            <a href="${servePath}/settings/system" class="fn-right ft-13 ft-gray" target="_blank">${wantPutOnLabel}</a>
                        </h2>
                    </div>
                    <div class="module-panel ad fn-clear">
                        ${ADLabel}
                    </div>
                </div>
                </#if>
                </#if>
                <#if sideRelevantArticles?size != 0>
                <div class="module">
                    <div class="module-header">
                        <h2>
                            ${relativeArticleLabel}
                        </h2>
                    </div>
                    <div class="module-panel">
                        <ul class="module-list">
                            <#list sideRelevantArticles as relevantArticle>
                            <li<#if !relevantArticle_has_next> class="last"</#if>>
                                <a rel="nofollow"
                               href="${servePath}/member/${relevantArticle.articleAuthorName}">
                                    <span class="avatar-small slogan" style="background-image:url('${relevantArticle.articleAuthorThumbnailURL20}')"></span>
                                </a>
                                <a rel="nofollow" class="title" href="${servePath}${relevantArticle.articlePermalink}">${relevantArticle.articleTitleEmoj}</a>
                            </li>
                            </#list>
                        </ul>
                    </div>
                </div>
                </#if>
                <#if sideRandomArticles?size != 0>
                <div class="module">
                    <div class="module-header">
                        <h2>
                            ${randomArticleLabel}
                        </h2>
                    </div>
                    <div class="module-panel">
                        <ul class="module-list">
                            <#list sideRandomArticles as randomArticle>
                            <li<#if !randomArticle_has_next> class="last"</#if>>
                                <a  rel="nofollow"
                                href="${servePath}/member/${randomArticle.articleAuthorName}">
                                    <span class="avatar-small slogan" style="background-image:url('${randomArticle.articleAuthorThumbnailURL20}')"></span>
                                </a>
                                <a class="title" rel="nofollow" href="${servePath}${randomArticle.articlePermalink}">${randomArticle.articleTitleEmoj}</a>
                            </li>
                            </#list>
                        </ul>
                    </div>
                </div>
                </#if>
                </#if>
            </div>
        </div>
        <div id="heatBar">
            <i class="heat" style="width:${article.articleHeat*3}px"></i>
        </div>
        <div id="revision"><div id="revisions"></div></div>
        <div id="reportDialog">
            <div class="form fn-clear">
                <div class="fn-clear"><label><input type="radio" value="0" name="report" checked> ${spamADLabel}</label></div>
                <div class="fn-clear"><label><input type="radio" value="1" name="report"> ${pornographicLabel}</label></div>
                <div class="fn-clear"><label><input type="radio" value="2" name="report"> ${violationOfRegulationsLabel}</label></div>
                <div class="fn-clear"><label><input type="radio" value="3" name="report"> ${allegedlyInfringingLabel}</label></div>
                <div class="fn-clear"><label><input type="radio" value="4" name="report"> ${personalAttacksLabel}</label></div>
                <div class="fn-clear"><label><input type="radio" value="49" name="report"> ${miscLabel}</label></div>
                <br>
                <textarea id="reportTextarea" placeholder="${reportContentLabel}" rows="3"></textarea><br><br>
                <button onclick="Comment.report(this)" class="fn-right green">${reportLabel}</button>
            </div>
        </div>
        <#if 6 == article.articleType>
        <div class="long-article-settings">
            <button class="long-article-toggle-btn" onclick="document.querySelectorAll('.long-article-settings-btn').forEach(function(btn){btn.classList.toggle('hide')});this.classList.toggle('active')">
                ···
            </button>
            <button class="long-article-settings-btn hide" onclick="window.scrollTo({top:0,behavior:'smooth'})" title="回到顶部">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path fill="currentColor" d="M5 15h4v6h6v-6h4l-7-8zM4 3h16v2H4z"/>
                </svg>
            </button>
            <button class="long-article-settings-btn hide has-cnt" onclick="document.getElementById('comments').scrollIntoView({behavior:'smooth'})" title="评论">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                    <path fill="currentColor" d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/>
                </svg>
                <span class="count">${article.articleCommentCount}</span>
            </button>
            <button class="long-article-settings-btn hide" onclick="(function(){var c=document.querySelector('.long-article-content');var s=parseInt(c.style.fontSize)||16;s=Math.max(12,s-2);c.style.fontSize=s+'px';localStorage.setItem('longArticleFontSize',s)})()" title="减小字号">
                <span>A-</span>
            </button>
            <button class="long-article-settings-btn hide" onclick="(function(){var c=document.querySelector('.long-article-content');var s=parseInt(c.style.fontSize)||16;s=Math.min(24,s+2);c.style.fontSize=s+'px';localStorage.setItem('longArticleFontSize',s)})()" title="增大字号">
                <span>A+</span>
            </button>
            <#if permissions["commonThankArticle"].permissionGrant>
            <button class="long-article-settings-btn hide has-cnt<#if article.thanked> ft-red</#if>" onclick="<#if !article.thanked>Article.thankArticle('${article.oId}', ${article.articleAnonymous})</#if>" title="${thankLabel}">
                <svg><use xlink:href="#heart"></use></svg>
                <span class="count">${article.thankedCnt}</span>
            </button>
            </#if>
            <#if permissions["commonGoodArticle"].permissionGrant>
            <button class="long-article-settings-btn hide has-cnt<#if isLoggedIn && 0 == article.articleVote> ft-red</#if>" onclick="Article.voteUp('${article.oId}', 'article', this)" title="${upLabel}">
                <svg><use xlink:href="#thumbs-up"></use></svg>
                <span class="count">${article.articleGoodCnt}</span>
            </button>
            </#if>
            <#if permissions["commonBadArticle"].permissionGrant>
            <button class="long-article-settings-btn hide has-cnt<#if isLoggedIn && 1 == article.articleVote> ft-red</#if>" onclick="Article.voteDown('${article.oId}', 'article', this)" title="${downLabel}">
                <svg><use xlink:href="#thumbs-down"></use></svg>
                <span class="count">${article.articleBadCnt}</span>
            </button>
            </#if>
            <#if isLoggedIn && isFollowing>
            <button class="long-article-settings-btn hide has-cnt ft-red" onclick="Util.unfollow(this, '${article.oId}', 'article')" title="${uncollectLabel}">
                <svg><use xlink:href="#star"></use></svg>
                <span class="count">${article.articleCollectCnt}</span>
            </button>
            <#else>
            <button class="long-article-settings-btn hide has-cnt" onclick="Util.follow(this, '${article.oId}', 'article')" title="${collectLabel}">
                <svg><use xlink:href="#star"></use></svg>
                <span class="count">${article.articleCollectCnt}</span>
            </button>
            </#if>
            <#if permissions["commonViewArticleHistory"].permissionGrant && article.articleRevisionCount &gt; 1>
            <button class="long-article-settings-btn hide" onclick="Article.revision('${article.oId}')" title="${historyLabel}">
                <svg class="icon-history"><use xlink:href="#history"></use></svg>
            </button>
            </#if>
            <button class="long-article-settings-btn hide" onclick="$('#reportDialog').data('type', 0).data('id', '${article.oId}').dialog('open')" title="${reportLabel}">
                <svg><use xlink:href="#icon-report"></use></svg>
            </button>
            <#if article.isMyArticle && 3 != article.articleType && permissions["commonUpdateArticle"].permissionGrant>
            <button class="long-article-settings-btn hide" onclick="location.href='${servePath}/update?id=${article.oId}'" title="${editLabel}">
                <svg><use xlink:href="#edit"></use></svg>
            </button>
            </#if>
            <#if article.isMyArticle && permissions["commonStickArticle"].permissionGrant>
            <button class="long-article-settings-btn hide" onclick="Article.stick('${article.oId}')" title="${stickLabel}">
                <svg><use xlink:href="#chevron-up"></use></svg>
            </button>
            </#if>
            <#if permissions["articleUpdateArticleBasic"].permissionGrant>
            <button class="long-article-settings-btn hide" onclick="location.href='${servePath}/admin/article/${article.oId}'" title="${adminLabel}">
                <svg><use xlink:href="#setting"></use></svg>
            </button>
            </#if>
        </div>
        <script>
        (function(){
            var fontSize = localStorage.getItem('longArticleFontSize');
            if (fontSize) {
                var content = document.querySelector('.long-article-content');
                if (content) content.style.fontSize = fontSize + 'px';
            }
        })();
        </script>
        </#if>
        <#include "footer.ftl">
        <#if isLoggedIn && discussionViewable && article.articleCommentable && permissions["commonAddComment"].permissionGrant>
            <div class="editor-panel">
                <div class="editor-bg"></div>
                <div class="wrapper">
                    <div class="editor-panel__head fn-flex">
                        <div id="replyUseName" class="fn-flex-1 fn-ellipsis"></div>
                        <span class="tooltipped tooltipped-w fn-pointer editor-hide" onclick="Comment._toggleReply()"
                              aria-label="${cancelLabel}"> <svg><use xlink:href="#chevron-down"></use></svg></span>
                    </div>
                    <div class="article-comment-content">
                        <div id="commentContent"></div>
                        <div class="tip" id="addCommentTip"></div>
                        <div class="comment-submit fn-clear">
                            <div>
                                <svg id="emojiBtn" style="width: 30px; height: 30px; cursor:pointer;">
                                    <use xlink:href="#emojiIcon"></use>
                                </svg>
                                <div class="hide-list" id="emojiList">
                                    <div style="display: flex;">
                                        <div id="emojiGroupBoxNew" style="width: 100px; border-right: 1px solid #eee; overflow-y: auto;">
                                        </div>
                                        <div class="hide-list-emojis" id="emojisNew" style="max-height: 250px; flex: 1; padding: 10px;">
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <#if permissions["commonAddCommentAnonymous"].permissionGrant>
                                <label class="anonymous-check">${anonymousLabel}<input type="checkbox" id="commentAnonymous"></label>
                            </#if>
                            <label class="anonymous-check">${onlyArticleAuthorVisibleLabel}<input type="checkbox" id="commentVisible"></label>
                            <div class="fn-flex-1"></div>
                            <button id="articleCommentBtn" type="button" class="red fn-right" onclick="Comment.add('${article.oId}', '${csrfToken}', this)">${submitLabel}</button>
                        </div>
                    </div>
                </div>
            </div>
        </#if>
        <div id="thoughtProgressPreview"></div>
        <script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
        <script src="${staticServePath}/js/lib/compress/article-libs.min.js?${staticResourceVersion}"></script>
        <script src="${staticServePath}/js/emoji-groups${miniPostfix}.js?${staticResourceVersion}"></script>
        <script src="${staticServePath}/js/m-article${miniPostfix}.js?${staticResourceVersion}"></script>
        <script src="${staticServePath}/js/channel${miniPostfix}.js?${staticResourceVersion}"></script>
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
            Label.upLabel = "${upLabel}";
            Label.downLabel = "${downLabel}";
            Label.confirmRemoveLabel = "${confirmRemoveLabel}";
            Label.removedLabel = "${removedLabel}";
            Label.uploadLabel = "${uploadLabel}";
            Label.userCommentViewMode = ${userCommentViewMode};
            Label.commentSort = '${commentSort}';
            Label.commentAuthorFilter = ${commentAuthorFilter?c};
            Label.commentQueryExtra = '<#if commentSort == "hot">&sort=hot</#if><#if commentAuthorFilter>&author=1</#if>';
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
            Label.articleAuthorName = '${article.articleAuthorName}';
            Label.replyLabel = '${replyLabel}';
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
            Label.insertEmojiLabel = '${insertEmojiLabel}';
            Label.commonAtUser = '${permissions["commonAtUser"].permissionGrant?c}';
            Label.noPermissionLabel = '${noPermissionLabel}';
            Label.commonUpdateCommentPermissionLabel = '${commonUpdateCommentPermissionLabel}';
            Label.reportLabel = '${reportLabel}';
            Label.canThankComment = ${permissions["commonThankComment"].permissionGrant?c};
            Label.canGoodComment = ${permissions["commonGoodComment"].permissionGrant?c};
            Label.canBadComment = ${permissions["commonBadComment"].permissionGrant?c};
            Label.canAddComment = ${permissions["commonAddComment"].permissionGrant?c};
            Label.articleChannel = "${wsScheme}://${serverHost}:${serverPort}${contextPath}/article-channel?articleId=${article.oId}&articleType=${article.articleType}";
            <#if isLoggedIn>
                Label.currentUserName = '${currentUser.userName}';
                Label.currentUserId = '${currentUser.oId}';
                Label.notificationCmtIds = '${notificationCmtIds}';
            </#if>
            <#if 3 == article.articleType>
                Article.playThought('${article.articleContent}');
            </#if>
            <#if 6 == article.articleType>
                MLongArticle.init();
            </#if>

            $(".editor-bg").click(Comment._toggleReply)
        </script>
    </body>
</html>
