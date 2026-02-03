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
<#include "macro-home.ftl">
<#include "../macro-pagination.ftl">
<@home "${type}">
<div class="tabs-sub fn-clear">
    <a pjax-title="${articleLabel} - ${user.userName} - ${symphonyLabel}" href="${servePath}/member/${user.userName}"<#if type == "home"> class="current"</#if>>${articleLabel}<#if type == "home"> &nbsp;<span class="count">${paginationRecordCount?c}</span></#if></a>
    <a pjax-title="${longArticleLabel} - ${user.userName} - ${symphonyLabel}" href="${servePath}/member/${user.userName}/long"<#if type == "long"> class="current"</#if>>${longArticleLabel}<#if type == "long"> &nbsp;<span class="count">${paginationRecordCount?c}</span></#if></a>
    <a pjax-title="${cmtLabel} - ${user.userName} - ${symphonyLabel}" href="${servePath}/member/${user.userName}/comments"<#if type == "comments"> class="current"</#if>>${cmtLabel}</a>
    <#if currentUser?? && currentUser.userName == user.userName>
    <a pjax-title="${anonymousArticleLabel} - ${user.userName} - ${symphonyLabel}"<#if type == "articlesAnonymous"> class="current"</#if> href="${servePath}/member/${user.userName}/articles/anonymous">${anonymousArticleLabel}<#if type == "articlesAnonymous"> &nbsp;<span class="count">${paginationRecordCount?c}</span></#if></a>
    <a pjax-title="${anonymousCommentLabel} - ${user.userName} - ${symphonyLabel}"<#if type == "commentsAnonymous"> class="current"</#if> href="${servePath}/member/${user.userName}/comments/anonymous">${anonymousCommentLabel}</a>
    </#if>
</div>
<#if 0 == user.userArticleStatus || (isLoggedIn && ("adminRole" == currentUser.userRole || currentUser.userName == user.userName))>
<div class="list">
    <#if userHomeArticles?size == 0>
        <p class="ft-center ft-gray home-invisible">${chickenEggLabel}</p>
    </#if>
    <ul>
        <#list userHomeArticles as article>
        <li<#if !(paginationPageCount?? && paginationPageCount!=0 && paginationPageCount!=1) && article_index == userHomeArticles?size - 1>
            class="last"
        </#if>>
            <div class="has-view fn-flex-1">
                <h2>
                    <@icon article.articlePerfect article.articleType></@icon>
                    <a rel="bookmark" href="${servePath}${article.articlePermalink}">${article.articleTitleEmoj}</a>
                </h2>
                <span class="ft-fade ft-smaller">
                    <#assign showTags = 6 != article.articleType && article.articleTagObjs?size gt 0>
                    <#if showTags>
                        <#list article.articleTagObjs as articleTag>
                        <a rel="tag" class="tag" href="${servePath}/tag/${articleTag.tagURI}">
                            ${articleTag.tagTitle}</a>
                        </#list>
                    </#if>
                       <span class="ft-smaller ft__fade">
                            <#if showTags><span class="fn__space5"></span>•<span class="fn__space5"></span></#if>
                                <a rel="nofollow" class="ft-a-title" href="${servePath}${article.articlePermalink}#comments">
                                     <span class="article-level<#if article.articleCommentCount lt 40>${(article.articleCommentCount/10)?int}<#else>4</#if>">${article.articleCommentCount}</span> 回帖</a>
                                     <span class="fn__space5"></span>•<span class="fn__space5"></span>
                                     <span class="article-level<#if article.articleViewCount lt 400>${(article.articleViewCount/100)?int}<#else>4</#if>"><#if article.articleViewCount < 1000>${article.articleViewCount}<#else>${article.articleViewCntDisplayFormat}</#if></span>
                                    浏览
                                    <span class="fn__space5"></span>•<span class="fn__space5"></span>
                                    ${article.articleCreateTime?string('yyyy-MM-dd HH:mm')}
                      </span>
                </span>
            </div>
            <#if isMyArticle && 3 != article.articleType && permissions["commonUpdateArticle"].permissionGrant>
            <div class="cmts">
                <a class="ft-a-title tooltipped tooltipped-w" href="${servePath}/update?id=${article.oId}" aria-label="${editLabel}"><svg><use xlink:href="#edit"></use></svg></a>
            </div>
            <#else>
            <#if article.articleCommentCount != 0>
            <div class="cmts tooltipped tooltipped-w" aria-label="${cmtLabel}${quantityLabel}">
                <a class="count ft-gray" href="${servePath}${article.articlePermalink}">${article.articleCommentCount}</a>
            </div>
            </#if>
            </#if>
        </li>
        </#list>
    </ul>
</div>
<#assign homeUrl = servePath + '/member/' + user.userName + (type == "long")?string("/long", "")>
<#assign homeTitle = (type == "long")?string(longArticleLabel, articleLabel) + ' - ' + user.userName + ' - ' + symphonyLabel>
<@pagination url=homeUrl pjaxTitle=homeTitle/>
<#else>
<p class="ft-center ft-gray home-invisible">${setinvisibleLabel}</p>
</#if>
</@home>
