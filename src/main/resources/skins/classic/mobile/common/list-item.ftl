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
<li class="list__item">
    <div class="list__meta fn__flex-inline">
        <a rel="nofollow" class="ft__gray" href="${servePath}/member/${article.articleAuthorName}"><div
                    class="list-avatar" aria-label="${article.articleAuthorName}" style="background-image:url('${article.articleAuthorThumbnailURL48}')"></div></a>
        <#if article.articleViewCount != 0>
            &nbsp;
            &nbsp;
            <a class="ft__level1" href="${servePath}${article.articlePermalink}"><span class="article-level<#if article.articleViewCount lt 400>${(article.articleViewCount/100)?int}<#else>4</#if>"><#if article.articleViewCount < 1000>${article.articleViewCount}<#else>${article.articleViewCntDisplayFormat}</#if></span> ${viewLabel}</a>
        </#if>
        <#if (article.articleCommentCount)??>
            <#if article.articleCommentCount != 0>
                &nbsp;• &nbsp;
                <a class="ft-fade" href="${servePath}${article.articlePermalink}#comments"><b class="article-level<#if article.articleCommentCount lt 40>${(article.articleCommentCount/10)?int}<#else>4</#if>">${article.articleCommentCount}</b> ${cmtLabel}</a>
            </#if>
        </#if>
        <#if (article.articleQnAOfferPoint)??>
        <#if article.articleQnAOfferPoint != 0>
            &nbsp;• &nbsp;
            <a class="ft-fade" href="${servePath}${article.articlePermalink}">
                <span class="article-level<#if article.articleQnAOfferPoint lt 400>${(article.articleQnAOfferPoint/100)?int}<#else>4</#if>">${article.articleQnAOfferPoint?c}</span>
                ${qnaOfferLabel}
            </a>
        </#if>
        </#if>


    </div>
    <div class="fn__5"></div>
    <div class="fn-flex">
        <div class="fn__flex-1 list__title" style="flex-direction: column" >
            <h2 class="list__title">
                <#if (article.articleQnAOfferPoint)??>
                    <#if article.articleQnAOfferPoint!= 0 >
                           <svg><use xlink:href="#iconAsk"></use></svg>
                    </#if>

                </#if>
                <a rel="bookmark" href="${servePath}${article.articlePermalink}">
                    ${article.articleTitleEmoj}
                </a>
                <#if article.articleType?? && 6 == article.articleType && article.columnId?? && article.columnId?has_content && article.columnTitle?? && article.columnTitle?has_content>
                    <a class="ft__smaller" href="${servePath}/column/${article.columnId}" style="display:inline-block;margin-left:6px;padding:0 6px;border-radius:10px;background:#eef4ff;color:#2b5db9;line-height:18px;vertical-align:middle;text-decoration:none;">专栏 · ${article.columnTitle}</a>
                </#if>
            </h2>
            <div class="fn__5"></div>
            <div class="fn__flex">
                <a class="fn__flex-1 list__content"  href="${servePath}${article.articlePermalink}">
                    ${article.articlePreviewContent}
                </a>

            </div>
        </div>


        <#if "" != article.articleThumbnailURL>
            <a href="${servePath}${article.articlePermalink}" class="list__img fn__flex-inline" >
                <img src ="${article.articleThumbnailURL}" style="background-image: none; background-color: transparent;">
            </a>
        </#if>
    </div>
    <div>
        <#list article.articleTagObjs as articleTag>
            <a rel="tag" class="list__tag" href="${servePath}/tag/${articleTag.tagURI}">${articleTag.tagTitle}</a> &nbsp;
        </#list>
    </div>

</li>
