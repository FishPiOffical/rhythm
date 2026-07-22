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
<#assign hasColumnNav = 6 == article.articleType && longArticleColumn?? && longArticleChapters?? && (longArticleChapters?size > 0)>
<#macro articleAdjacentItem item label right=false>
    <div class="article-adjacent-nav__item<#if right> article-adjacent-nav__item--right</#if>">
        <#if item.articlePermalink??>
            <a href="${servePath}${item.articlePermalink}" class="article-adjacent-nav__link">
                <div class="article-adjacent-nav__label">${label}</div>
                <div class="article-adjacent-nav__title">${item.articleTitleEmoj!item.articleTitle}</div>
                <#if item.articlePreviewContent?? && "" != item.articlePreviewContent>
                    <div class="article-adjacent-nav__preview">${item.articlePreviewContent}</div>
                </#if>
            </a>
        <#else>
            <div class="article-adjacent-nav__link article-adjacent-nav__link--disabled">
                <div class="article-adjacent-nav__label">${label}</div>
                <div class="article-adjacent-nav__preview">没有更多了</div>
            </div>
        </#if>
    </div>
</#macro>
<#macro articleAdjacentPair previous next>
    <div class="article-adjacent-nav">
        <@articleAdjacentItem item=previous label="上一篇" />
        <@articleAdjacentItem item=next label="下一篇" right=true />
    </div>
</#macro>
<#macro articleNavList title articles>
    <#if articles?? && (articles?size > 0)>
        <div class="article-nav-list-card">
            <div class="article-nav-list-card__title">${title}</div>
            <div class="article-nav-list-card__items">
                <#list articles as navArticle>
                    <a href="${servePath}${navArticle.articlePermalink}" class="article-nav-list-card__item<#if navArticle.oId == article.oId> article-nav-list-card__item--active</#if>">
                        ${navArticle.articleTitleEmoj!navArticle.articleTitle}
                    </a>
                </#list>
            </div>
        </div>
    </#if>
</#macro>
<#macro articleSortPanel sort title previous next articles>
    <div class="article-nav-sort__panel article-nav-sort__panel--${sort}" data-article-nav-sort-panel="${sort}">
        <@articleAdjacentPair previous=previous next=next />
        <@articleNavList title=title articles=articles />
    </div>
</#macro>
<div class="article-nav-scope<#if hasColumnNav> article-nav-scope--switchable</#if>">
    <#if hasColumnNav>
        <input class="article-nav-scope__input" type="radio" name="articleNavScope" id="articleNavScopeColumn" checked>
        <input class="article-nav-scope__input" type="radio" name="articleNavScope" id="articleNavScopeAll">
        <div class="article-nav-scope__tabs" aria-label="阅读范围">
            <label class="article-nav-scope__tab article-nav-scope__tab--column" for="articleNavScopeColumn">专栏</label>
            <label class="article-nav-scope__tab article-nav-scope__tab--all" for="articleNavScopeAll">全部文章</label>
        </div>
    </#if>
    <div class="article-nav-scope__panels">
        <#if hasColumnNav>
            <div class="article-nav-scope__panel article-nav-scope__panel--column">
                <@articleAdjacentPair previous=(longArticlePrevious!{}) next=(longArticleNext!{}) />
            </div>
        </#if>
        <div class="article-nav-scope__panel article-nav-scope__panel--all">
            <div class="article-nav-sort" data-article-nav-sort>
                <input class="article-nav-sort__input" type="radio" name="articleNavSort" id="articleNavSortTime" value="time" checked>
                <input class="article-nav-sort__input" type="radio" name="articleNavSort" id="articleNavSortAuthor" value="author">
                <input class="article-nav-sort__input" type="radio" name="articleNavSort" id="articleNavSortHot" value="hot">
                <div class="article-nav-sort__tabs" aria-label="排序">
                    <label class="article-nav-sort__tab article-nav-sort__tab--time" for="articleNavSortTime">时间</label>
                    <label class="article-nav-sort__tab article-nav-sort__tab--author" for="articleNavSortAuthor">作者</label>
                    <label class="article-nav-sort__tab article-nav-sort__tab--hot" for="articleNavSortHot">热门</label>
                </div>
                <div class="article-nav-sort__panels">
                    <@articleSortPanel sort="time" title="时间排序" previous=(articlePrevious!{}) next=(articleNext!{}) articles=(articleTimeNavArticles![]) />
                    <@articleSortPanel sort="author" title="当前作者" previous=(articleAuthorPrevious!{}) next=(articleAuthorNext!{}) articles=(articleAuthorNavArticles![]) />
                    <@articleSortPanel sort="hot" title="热门排序" previous=(articleHotPrevious!{}) next=(articleHotNext!{}) articles=(articleHotNavArticles![]) />
                </div>
            </div>
        </div>
    </div>
</div>
