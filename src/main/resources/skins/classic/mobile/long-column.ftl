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
<#include "common/index-nav.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="专栏：${longColumn.columnTitle} - ${symphonyLabel}">
        <meta name="description" content="专栏 ${longColumn.columnTitle} 章节目录"/>
    </@head>
</head>
<body>
<div class="mobile-head">
    <#include "header.ftl">
    <@indexNav 'column'/>
</div>
<div style="height: 74px;width: 1px;"></div>

<div class="main">
    <div class="module_new">
        <h2 class="module__title ft__fade fn__clear">专栏：${longColumn.columnTitle}
            <span class="fn__right ft__smaller">共 ${longColumn.columnArticleCount?c} 章</span>
        </h2>
    </div>

    <#if longColumnChapters?? && longColumnChapters?size != 0>
        <ul>
            <#list longColumnChapters as chapter>
                <li class="list__item">
                    <a class="list__title" href="${servePath}${chapter.articlePermalink}">第 ${chapter.chapterNo?c} 章 · ${chapter.articleTitleEmoj}</a>
                    <div class="ft__smaller ft__fade" style="margin-top:6px;line-height:1.6;">${chapter.articlePreviewContent}</div>
                </li>
            </#list>
        </ul>
    <#else>
        <div class="wrapper ft-center ft-gray" style="padding:20px 0;">该专栏暂无有效章节</div>
    </#if>

    <#if latestLongColumns?? && latestLongColumns?size != 0>
        <div class="module_new">
            <h2 class="module__title ft__fade fn__clear">最新专栏</h2>
        </div>
        <ul>
            <#list latestLongColumns as column>
                <#assign columnId = column.columnId!column.oId>
                <li class="list__item fn__flex">
                    <a class="fn__flex-1" href="${servePath}/column/${columnId}">${column.columnTitle}</a>
                    <span class="ft__smaller ft__fade">${column.columnArticleCount?c} 章</span>
                </li>
            </#list>
        </ul>
    </#if>

    <#if hotLongColumns?? && hotLongColumns?size != 0>
        <div class="module_new">
            <h2 class="module__title ft__fade fn__clear">热门专栏</h2>
        </div>
        <ul>
            <#list hotLongColumns as column>
                <#assign columnId = column.columnId!column.oId>
                <li class="list__item fn__flex">
                    <a class="fn__flex-1" href="${servePath}/column/${columnId}">${column.columnTitle}</a>
                    <span class="ft__smaller ft__fade">${column.columnArticleCount?c} 章</span>
                </li>
            </#list>
        </ul>
    </#if>

    <#if isLoggedIn && longColumnRecentReadHistory?? && longColumnRecentReadHistory?size != 0>
        <div class="module_new">
            <h2 class="module__title ft__fade fn__clear">最近阅读</h2>
        </div>
        <ul>
            <#list longColumnRecentReadHistory as history>
                <li class="list__item">
                    <a class="list__title" href="${servePath}${history.articlePermalink}">第 ${history.chapterNo?c} 章 · ${history.articleTitleEmoj}</a>
                    <a class="ft__smaller" style="color:#2b5db9;text-decoration:none;" href="${servePath}/column/${history.columnId}">${history.columnTitle}</a>
                </li>
            </#list>
        </ul>
    </#if>
</div>

<#include "footer.ftl">
</body>
</html>
