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
<!DOCTYPE html>
<html>
<head>
    <@head title="专栏 - ${symphonyLabel}">
        <meta name="description" content="长篇专栏推荐与阅读入口"/>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
</head>
<body>
<#include "header.ftl">
<#macro columnBook column>
    <#assign columnId = column.columnId!column.oId>
    <a class="column-book<#if !(column.columnHasCover!false)> column-book--default</#if>"
       href="${servePath}/column/${columnId}" title="${column.columnTitle}">
        <span class="column-book__cover" style="background-image:url('${column.columnCoverURL?html}')"></span>
        <span class="column-book__title">${column.columnTitle}</span>
    </a>
</#macro>
<div class="main">
    <div class="wrapper">
        <div class="content fn-clear">
            <div class="module column-bookstore">
                <div class="module-header"><h2>专栏书城</h2></div>
                <div class="module-panel column-bookstore__panel">
                    <div class="column-bookstore__sections">
                        <section class="column-bookstore__section">
                            <h3>最新专栏</h3>
                            <#if latestLongColumns?? && latestLongColumns?size != 0>
                                <div class="column-bookstore__grid">
                                <#list latestLongColumns as column>
                                    <@columnBook column=column />
                                </#list>
                                </div>
                            <#else>
                                <div class="column-bookstore__empty">暂无专栏</div>
                            </#if>
                        </section>
                        <section class="column-bookstore__section column-bookstore__section--hot">
                            <h3>热门专栏</h3>
                            <#if hotLongColumns?? && hotLongColumns?size != 0>
                                <div class="column-bookstore__grid">
                                <#list hotLongColumns as column>
                                    <@columnBook column=column />
                                </#list>
                                </div>
                            <#else>
                                <div class="column-bookstore__empty">暂无专栏</div>
                            </#if>
                        </section>
                    </div>
                </div>
            </div>
        </div>

        <div class="side">
            <#if isLoggedIn && longColumnRecentReadHistory?? && longColumnRecentReadHistory?size != 0>
                <div class="module">
                    <div class="module-header"><h2>最近阅读</h2></div>
                    <div class="module-panel">
                        <ul class="module-list long-column-module-list">
                            <#list longColumnRecentReadHistory as history>
                                <li>
                                    <a class="title fn-ellipsis" href="${servePath}${history.articlePermalink}">第 ${history.chapterNo?c} 章 · ${history.articleTitleEmoj}</a>
                                    <a class="ft-smaller" style="color:#2b5db9;text-decoration:none;" href="${servePath}/column/${history.columnId}">${history.columnTitle}</a>
                                </li>
                            </#list>
                        </ul>
                    </div>
                </div>
            </#if>
            <#include "side.ftl">
        </div>
    </div>
</div>
<#include "footer.ftl">
</body>
</html>
