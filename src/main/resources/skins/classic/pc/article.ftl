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
<#function medalTypeStyle type>
    <#switch type>
        <#case "精良">
            <#return "color:#1d4ed8;">
        <#case "稀有">
            <#return "color:#8b5cf6;">
        <#case "史诗">
            <#return "color:#ea580c;font-weight:600;">
        <#case "传说">
            <#return "color:#eab308;font-weight:700;">
        <#case "神话">
            <#return "color:#f59e0b;font-weight:700;text-shadow:0 0 3px rgba(245,158,11,0.8);">
        <#case "限定">
            <#return "color:#ef4444;font-weight:700;text-shadow:0 0 6px rgba(239,68,68,0.9);">
        <#default>
            <#return "color:#111827;">
    </#switch>
</#function>
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
    <style>
        .long-article-nav{display:flex;gap:16px;margin:24px 0;padding:16px 0;border-top:1px solid #eee;border-bottom:1px solid #eee;flex-wrap:wrap}
        .long-article-nav__item{flex:1 1 300px;min-width:0}
        .long-article-nav__item--right{text-align:right}
        .long-article-nav__link{display:block;padding:12px 14px;border-radius:10px;border:1px solid #f0f0f0;background:#fafafa;transition:all .2s ease;color:#111;text-decoration:none}
        .long-article-nav__link:hover{border-color:#d0d7de;background:#fff;text-decoration:none}
        .long-article-nav__link--disabled{color:#999;cursor:default}
        .long-article-nav__label{font-size:12px;color:#888;margin-bottom:6px}
        .long-article-nav__title{font-weight:700;margin-bottom:6px;line-height:1.3}
        .long-article-nav__preview{color:#555;font-size:13px;line-height:1.5;max-height:3.9em;overflow:hidden}
        .long-column-card{margin:16px 0;padding:12px;border:1px solid #eceff5;border-radius:10px;background:#fbfdff}
        .long-column-card__title{font-weight:600;color:#2b3a55}
        .long-column-card__meta{margin-top:4px;font-size:12px;color:#7b8798}
        .long-column-card__chapters{margin-top:10px;max-height:260px;overflow:auto;border-top:1px dashed #e6eaf0;padding-top:8px}
        .long-column-card__chapter{display:block;padding:6px 8px;border-radius:8px;color:#3d4a5c;text-decoration:none}
        .long-column-card__chapter:hover{background:#f1f6ff;text-decoration:none}
        .long-column-card__chapter--active{background:#e8f0ff;color:#1d4ed8;font-weight:600}
    </style>
</head>
<body itemscope itemtype="http://schema.org/Product" class="article<#if 6 == article.articleType> long-article-page</#if>">
<img itemprop="image" class="fn-none" src="${article.articleAuthorThumbnailURL210}"/>
<p itemprop="description" class="fn-none">"${article.articlePreviewContent}"</p>
<#include "header.ftl">
<div class="article-container">
<div class="article-body">
    <#if showTopAd && 6 != article.articleType>
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
            <div class="vditor-reset article-content<#if 6 == article.articleType> long-article-content</#if>">
                ${article.articleContent}
            </div>
            <#if 6 == article.articleType>
                <div class="long-article-nav">
                    <div class="long-article-nav__item">
                        <#if longArticlePrevious??>
                            <a href="${servePath}${longArticlePrevious.articlePermalink}" class="long-article-nav__link">
                                <div class="long-article-nav__label">上一篇</div>
                                <div class="long-article-nav__title">${longArticlePrevious.articleTitleEmoj}</div>
                                <div class="long-article-nav__preview">${longArticlePrevious.articlePreviewContent}</div>
                            </a>
                        <#else>
                            <div class="long-article-nav__link long-article-nav__link--disabled">
                                <div class="long-article-nav__label">上一篇</div>
                                <div class="long-article-nav__preview">没有更多了</div>
                            </div>
                        </#if>
                    </div>
                    <div class="long-article-nav__item long-article-nav__item--right">
                        <#if longArticleNext??>
                            <a href="${servePath}${longArticleNext.articlePermalink}" class="long-article-nav__link">
                                <div class="long-article-nav__label">下一篇</div>
                                <div class="long-article-nav__title">${longArticleNext.articleTitleEmoj}</div>
                                <div class="long-article-nav__preview">${longArticleNext.articlePreviewContent}</div>
                            </a>
                        <#else>
                            <div class="long-article-nav__link long-article-nav__link--disabled">
                                <div class="long-article-nav__label">下一篇</div>
                                <div class="long-article-nav__preview">没有更多了</div>
                            </div>
                        </#if>
                    </div>
                </div>
                <#if longArticleColumn?? && longArticleChapters?? && (longArticleChapters?size > 0)>
                    <div class="long-column-card">
                        <div class="long-column-card__title">所属专栏：<a href="${servePath}/column/${longArticleColumn.oId!longArticleColumn.columnId}" style="color:inherit;text-decoration:none;">${longArticleColumn.columnTitle}</a></div>
                        <div class="long-column-card__meta">当前第 ${article.longArticleChapterNo?c} 章 · 共 ${longArticleColumn.columnArticleCount?c} 章</div>
                        <div class="long-column-card__chapters">
                            <#list longArticleChapters as chapter>
                                <a href="${servePath}${chapter.articlePermalink}" class="long-column-card__chapter<#if chapter.articleId == article.oId> long-column-card__chapter--active</#if>">
                                    第 ${chapter.chapterNo?c} 章 · ${chapter.articleTitleEmoj}
                                </a>
                            </#list>
                        </div>
                    </div>
                </#if>
            </#if>
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
            <#if 6 == article.articleType>
                <div class="long-article-meta">
                    <a href="${servePath}/member/${article.articleAuthorName}" class="long-article-author">${article.articleAuthorName}</a>
                    <span class="long-article-time">${article.timeAgo}</span>
                </div>
                <#if article.isMyArticle && article.longArticleReadStat??>
                    <div class="fn__flex-column" style="gap:8px;margin:12px 0;padding:12px;border:1px solid #f0f0f0;border-radius:8px;background:#fafafa;">
                        <div class="ft__smaller ft__fade">长文阅读激励</div>
                        <div class="fn__flex" style="gap:12px;flex-wrap:wrap;">
                            <div class="fn__flex-1">
                                <div class="ft__smaller ft__fade">未结算</div>
                                <div>注册 ${article.longArticleReadStat.registeredUnsettledCnt} / 未注册 ${article.longArticleReadStat.anonymousUnsettledCnt}</div>
                            </div>
                            <div class="fn__flex-1">
                                <div class="ft__smaller ft__fade">总计</div>
                                <div>注册 ${article.longArticleReadStat.registeredTotalCnt} / 未注册 ${article.longArticleReadStat.anonymousTotalCnt}</div>
                            </div>
                        </div>
                        <div class="ft__smaller ft__fade">未注册以 IP+UA 去重，当窗封顶 100</div>
                    </div>
                </#if>
            </#if>
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
            <#if 6 != article.articleType>
            <div class="fn-flex">
                <ul class="tag-desc fn-flex-1 tag-desc--right">
                    <#list article.articleTagObjs as articleTag>
                        <li data-id="${articleTag.oId}">
                            <a href="${servePath}/tag/${articleTag.tagURI}">
                                <#if articleTag.tagIconPath != "">
                                    <div alt="${articleTag.tagTitle}"
                                         style="background-image: url('${articleTag.tagIconPath}');"></div>
                                </#if>
                                ${articleTag.tagTitle}
                            </a>
                            <div style="width: auto;min-width: auto;white-space: nowrap;">
                                <div class="vditor-reset ft__smaller">
                                    <p>${articleTag.tagDescription}</p>
                                </div>
                                <span class="fn-right ft__fade">
                            <span class="article-level0">${articleTag.tagReferenceCount}</span>
                            引用
                            </span>
                            </div>
                        </li>
                    </#list>
                </ul>
            </div>
            </#if>
            <div id="articleActionPanel" class="comment__action"></div>
            <#if 6 == article.articleType>
            <#else>
            <div class="article__meta">
                <div>
                    <a rel="author" href="${servePath}/member/${article.articleAuthorName}">
                        <div class="avatar fn__left tooltipped-user" aria-name="${article.articleAuthorName}"
                             style="background-image: url('${article.articleAuthorThumbnailURL210}');"></div>
                    </a>
                </div>
                <div class="fn__flex-1">
                    <div id="articleMeta" class="fn__clear" style="height: auto; overflow: visible; display: flow-root;">
                        <a rel="author" href="${servePath}/member/${article.articleAuthorName}"
                           class="article__stats article__stats--a tooltipped tooltipped-e"
                           aria-label="${article.oId?number?number_to_datetime}">
                            <span class="article__cnt"><#if article.articleAuthorNickName != "">${article.articleAuthorNickName}<#else>${article.articleAuthorName}</#if></span>
                            <time>${article.timeAgo}</time>
                            <#if 0 == article.articleAuthor.userUAStatus>
                                <span id="articltVia" class="via" data-ua="${article.articleUA}"></span>
                            </#if>
                        </a>
                        <#if article.articleCity != "">
                            <a href="${servePath}/city/${article.articleCity}" target="_blank" class="article__stats article__stats--a">
                                <span class="article__cnt">${article.articleCity}</span>
                                位置
                            </a>
                        </#if>
                        <div id="articleStats">
                            <div id="articleGoodCnt" class="article__stats usersInteracts article__stats--a">
                                <span class="article__cnt">${article.articleGoodCnt}</span>
                                点赞
                            </div>
                            <div id="articleFollowCnt" class="article__stats usersInteracts article__stats--a">
                                <span class="article__cnt">${article.articleWatchCnt}</span>
                                关注
                            </div>
                            <div id="articleCollectCnt" class="article__stats usersInteracts article__stats--a">
                                <span class="article__cnt">${article.articleCollectCnt}</span>
                                收藏
                             </div>
                             <#if article.articleQnAOfferPoint?c != "0">
                                 <div id="articleQnAOfferCnt" class="article__stats usersInteracts article__stats--a">
                                     <span class="article__cnt">${article.articleQnAOfferPoint?c}</span>
                                     ${qnaOfferLabel}
                                 </div>
                             </#if>
                             <div class="article__stats usersInteracts article__stats--a">
                                <#assign articleMedals = (article.sysMetal?is_string)?then(article.sysMetal?eval, article.sysMetal)![]>
                                <#if articleMedals?size != 0>
                                    <span class="article__cnt">作者勋章</span>
                                    <#list articleMedals as metal>
                                        <#assign medalType = metal.type!''>
                                        <#assign medalName = metal.name!''>
                                        <#assign medalDesc = metal.description!''>
                                        <span class="tip-wrapper">
                                            <img src="${servePath}/gen?id=${metal.id}"/>
                                            <span class="tip-text">
                                                <#if medalType != "">
                                                    <span style="${medalTypeStyle(medalType)}">[${medalType}]</span>
                                                    <#if medalName != "" || medalDesc != "">&nbsp;</#if>
                                                </#if>
                                                <#if medalName != "">${medalName}<#if medalDesc != ""> - </#if></#if>${medalDesc}
                                            </span>
                                        </span>
                                    </#list>
                                </#if>
                             </div>
                         </div>
                        <br>
                        <#if article.thankedCnt != 0>
                            <div class="fn-clear"></div>
                            <br>
                            <div id="articleThanksCnt" class="article__stats usersInteracts article__stats--a">
                                <span class="article__cnt">${article.thankedCnt}</span>
                                <span class="fn__flex-inline">感谢&nbsp;<svg><use xlink:href="#iconHeart"></use></svg></span>
                            </div>
                            <br>
                        </#if>
                        <#if article.articleCommentCount != 0>
                            <div class="fn-clear"></div>
                            <br>
                            <div class="article__stats usersInteracts article__stats--a">
                                <span class="article__cnt">${article.articleCommentCount}</span>
                                <span class="fn__flex-inline">${cmtLabel}&nbsp;<svg><use xlink:href="#replyIcon"></use></svg></span>
                            </div>
                            <#list article.commentors as user>
                                <a target="_blank" href="${servePath}/member/${user.userName}">
                                    <div class="article__participant">
                                        <div class="avatar-small" aria-label="${user.userName}" style="background-image: url('${user.userAvatarURL48}');"></div>
                                    </div>
                                </a>
                            </#list>
                        </#if>
                    </div>
                    <div class="fn__clear article__view">
                        <span class="fn__flex-inline tooltipped__n tooltipped-n tooltipped" aria-label="总访问计数">
                            <svg><use xlink:href="#iconTop"></use></svg>
                            <#if article.articleViewCount < 1000>
                                ${article.articleViewCount}
                            <#else>
                                ${article.articleViewCntDisplayFormat}
                            </#if>
                        </span>
                    </div>
                </div>
            </div>
        </div>
        </#if>
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
                                             <#assign commentMedals = (comment.sysMetal?is_string)?then(comment.sysMetal?eval, comment.sysMetal)![]>
                                             <#if commentMedals?size != 0>
                                                 &nbsp;
                                                 <#list commentMedals as metal>
                                                     <#assign medalType = metal.type!''>
                                                     <#assign medalName = metal.name!''>
                                                     <#assign medalDesc = metal.description!''>
                                                     <span class="tip-wrapper">
                                                         <img src="${servePath}/gen?id=${metal.id}"/>
                                                         <span class="tip-text">
                                                             <#if medalType != "">
                                                                 <span style="${medalTypeStyle(medalType)}">[${medalType}]</span>
                                                                 <#if medalName != "" || medalDesc != "">&nbsp;</#if>
                                                             </#if>
                                                             <#if medalName != "">${medalName}<#if medalDesc != ""> - </#if></#if>${medalDesc}
                                                         </span>
                                                     </span>
                                                 </#list>
                                             </#if>
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
<div class="wrapper article-footer">
    <#if 6 != article.articleType>
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
                            <a rel="nofollow" href="${servePath}/member/${relevantArticle.articleAuthorName}">
                                <span class="avatar-small slogan tooltipped tooltipped-se"
                                      aria-label="${relevantArticle.articleAuthorName}"
                                      style="background-image:url('${relevantArticle.articleAuthorThumbnailURL20}')"></span>
                            </a>
                            <a rel="nofollow" class="title fn-ellipsis"
                               href="${servePath}${relevantArticle.articlePermalink}">${relevantArticle.articleTitleEmoj}</a>
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
                            <a rel="nofollow" href="${servePath}/member/${randomArticle.articleAuthorName}">
                                    <span class="avatar-small slogan tooltipped tooltipped-se"
                                          aria-label="${randomArticle.articleAuthorName}"
                                          style="background-image:url('${randomArticle.articleAuthorThumbnailURL20}')"></span>
                            </a>
                            <a class="title fn-ellipsis" rel="nofollow"
                               href="${servePath}${randomArticle.articlePermalink}">${randomArticle.articleTitleEmoj}</a>
                        </li>
                    </#list>
                </ul>
            </div>
        </div>
    </#if>
    </#if>

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

<div class="share">
            <span id="thankArticle" aria-label="${thankLabel}"
                  class="tooltipped tooltipped-e<#if article.thanked> ft-red<#else> ft-blue</#if>"
            <#if permissions["commonThankArticle"].permissionGrant>
                <#if !article.thanked>
                    onclick="Article.thankArticle('${article.oId}', ${article.articleAnonymous})"
                </#if>
            <#else>
                onclick="Article.permissionTip(Label.noPermissionLabel)"
                    </#if>><svg><use xlink:href="#heart"></use></svg> <span
                        class="ft-13">${article.thankedCnt}</span></span>
</div>
</div>
<div class="article-header">
    <h1 aria-label="返回上一页" class="tooltipped tooltipped-s" style="display: flex; align-items: center;">
        <a href="javascript:history.back()">
            <svg style="width: 26px;height: 26px;color: #666;display: block;"><use xlink:href="#arrow-left"></use></svg>
        </a>
    </h1>
    <h2 class="fn-ellipsis fn-pointer" id="bigTitle" style="transition: .5s;max-width: 600px" onclick="Util.goTop()">
        ${article.articleTitleEmojUnicode}
    </h2>
    <div class="user-nav">
        <#if "" != article.articleToC>
            <span onclick="Article.toggleToc()" aria-label="${ToCLabel}"
                  class="tooltipped tooltipped-w"><svg class="ft-red icon-unordered-list"><use
                            xlink:href="#unordered-list"></use></svg></span>
        </#if>
        <#if permissions["commonViewArticleHistory"].permissionGrant && article.articleRevisionCount &gt; 1>
            <span onclick="Article.revision('${article.oId}')" aria-label="${historyLabel}"
                  class="tooltipped tooltipped-w"><svg class="icon-history"><use
                            xlink:href="#history"></use></svg></span>
        </#if>

        <#if articlePrevious??>
            <a rel="prev" class="tooltipped tooltipped-w"
               aria-label="${prevPostLabel}: ${articlePrevious.articleTitleEmojUnicode}"
               href="${servePath}${articlePrevious.articlePermalink}">
                <svg>
                    <use xlink:href="#chevron-left"></use>
                </svg>
            </a>
        </#if>

        <#if articleNext??>
            <a rel="next" class="tooltipped tooltipped-w"
               aria-label="${nextPostLabel}: ${articleNext.articleTitleEmojUnicode}"
               href="${servePath}${articleNext.articlePermalink}">
                <svg>
                    <use xlink:href="#chevron-right"></use>
                </svg>
            </a>
        </#if>

        <span class="tooltipped tooltipped-w<#if isLoggedIn && 0 == article.articleVote> ft-red</#if>"
              aria-label="${upLabel}"
                <#if permissions["commonGoodArticle"].permissionGrant>
                    onclick="Article.voteUp('${article.oId}', 'article', this)"
                    <#else>
                        onclick="Article.permissionTip(Label.noPermissionLabel)"
                </#if>><svg class="icon-thumbs-up"><use
                        xlink:href="#thumbs-up"></use></svg> ${article.articleGoodCnt}</span>

        <span class="tooltipped tooltipped-w<#if isLoggedIn && 1 == article.articleVote> ft-red</#if>"
              aria-label="${downLabel}"
                <#if permissions["commonBadArticle"].permissionGrant>
                    onclick="Article.voteDown('${article.oId}', 'article', this)"
                <#else>
                    onclick="Article.permissionTip(Label.noPermissionLabel)"
                </#if>><svg class="icon-thumbs-down"><use
                        xlink:href="#thumbs-down"></use></svg> ${article.articleBadCnt}</span>

        <#if isLoggedIn && isFollowing>
            <span class="tooltipped tooltipped-w ft-red" aria-label="${uncollectLabel}"
                    <#if permissions["commonFollowArticle"].permissionGrant>
                        onclick="Util.unfollow(this, '${article.oId}', 'article', ${article.articleCollectCnt})"
                        <#else>
                            onclick="Article.permissionTip(Label.noPermissionLabel)"
                    </#if>><svg class="icon-star"><use
                            xlink:href="#star"></use></svg> ${article.articleCollectCnt}</span>
        <#else>
            <span class="tooltipped tooltipped-w" aria-label="${collectLabel}"
                        <#if permissions["commonFollowArticle"].permissionGrant>
                            onclick="Util.follow(this, '${article.oId}', 'article', ${article.articleCollectCnt})"
                            <#else>
                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                    </#if>><svg class="icon-star"><use
                            xlink:href="#star"></use></svg> ${article.articleCollectCnt}</span>
        </#if>

        <#if isLoggedIn && isWatching>
            <span class="tooltipped tooltipped-w ft-red" aria-label="${unfollowLabel}"
                    <#if permissions["commonWatchArticle"].permissionGrant>
                        onclick="Util.unfollow(this, '${article.oId}', 'article-watch', ${article.articleWatchCnt})"
                        <#else>
                            onclick="Article.permissionTip(Label.noPermissionLabel)"
                    </#if>><svg class="icon-view"><use xlink:href="#view"></use></svg> ${article.articleWatchCnt}</span>
        <#else>
            <span class="tooltipped tooltipped-w" aria-label="${followLabel}"
                        <#if permissions["commonWatchArticle"].permissionGrant>
                            onclick="Util.follow(this, '${article.oId}', 'article-watch', ${article.articleWatchCnt})"
                            <#else>
                                onclick="Article.permissionTip(Label.noPermissionLabel)"
                    </#if>><svg class="icon-view"><use xlink:href="#view"></use></svg> ${article.articleWatchCnt}</span>
        </#if>
    </div>
</div>

<#if 6 == article.articleType>
<div class="long-article-settings">
    <button class="long-article-settings-btn" onclick="window.scrollTo({top:0,behavior:'smooth'})" title="回到顶部">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24">
            <path fill="currentColor" d="M5 15h4v6h6v-6h4l-7-8zM4 3h16v2H4z"/>
        </svg>
    </button>
    <button class="long-article-settings-btn" onclick="document.getElementById('comments').scrollIntoView({behavior:'smooth'})" title="评论 ${article.articleCommentCount}">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24">
            <path fill="currentColor" d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/>
        </svg>
        <#if article.articleCommentCount gt 0>
        <span style="position:absolute;top:-4px;right:-4px;background:#f44336;color:#fff;border-radius:10px;padding:2px 5px;font-size:10px;min-width:18px;text-align:center;">${article.articleCommentCount}</span>
        </#if>
    </button>
    <button class="long-article-settings-btn" onclick="(function(){var c=document.querySelector('.long-article-content');var s=parseInt(c.style.fontSize)||18;s=Math.max(12,s-2);c.style.fontSize=s+'px';localStorage.setItem('longArticleFontSize',s);})()" title="减小字号">
        A-
    </button>
    <button class="long-article-settings-btn" onclick="(function(){var c=document.querySelector('.long-article-content');var s=parseInt(c.style.fontSize)||18;s=Math.min(28,s+2);c.style.fontSize=s+'px';localStorage.setItem('longArticleFontSize',s);})()" title="增大字号">
        A+
    </button>
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
                            <div class="hide-list" id="emojiList" style="width: 440px;max-width: 440px;">
                                <div style="display: flex;width: 440px">
                                    <div id="emojiGroupBoxNew" style="width: 100px; border-right: 1px solid #eee; overflow-y: auto;">
                                    </div>
                                    <div class="hide-list-emojis" id="emojisNew" style="max-height: 250px; flex: 1; padding: 10px;">
                                    </div>
                                </div>
<#--                                <div class="hide-list-emojis__tail">-->
<#--                                        <span>-->
<#--                                        <a onclick="Comment.fromURL()">从URL导入表情包</a>-->
<#--                                        </span>-->
<#--                                    <span class="hide-list-emojis__tip"></span>-->
<#--                                    <span>-->
<#--                                            <a onclick="$('#uploadEmoji input').click()">上传表情包</a>-->
<#--                                        </span>-->
<#--                                    <form style="display: none" id="uploadEmoji" method="POST" enctype="multipart/form-data">-->
<#--                                        <input type="file" name="file">-->
<#--                                    </form>-->
<#--                                </div>-->
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
<script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
<script src="${staticServePath}/js/lib/compress/article-libs.min.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/emoji-groups${miniPostfix}.js?${staticResourceVersion}"></script>
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

    <#if 6 == article.articleType>
    LongArticle.init();
    </#if>

    setInterval(function () {
        Util.listenUserCard();
    }, 1000);
</script>
<script>
    $(document).ready(function () {
        $(window).scroll(function () {
            let title = "${article.articleTitleEmojUnicode}";
            if ($(document).scrollTop() > 500) {
                $("#bigTitle").css('opacity','0');
            } else {
                $("#bigTitle").css('opacity','1');
            }
        });
        $(".editor-bg").click(Comment._toggleReply)
    })
</script>
</body>
</html>
