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
<div class="main">
    <div class="wrapper">
        <div class="content fn-clear">
            <div class="module">
                <div class="module-header"><h2>最新专栏</h2></div>
                <div class="module-panel">
                    <#if latestLongColumns?? && latestLongColumns?size != 0>
                        <ul class="module-list long-column-module-list">
                            <#list latestLongColumns as column>
                                <#assign columnId = column.columnId!column.oId>
                                <li>
                                    <a class="title" href="${servePath}/column/${columnId}">${column.columnTitle}</a>
                                    <div class="ft-smaller ft-gray" style="margin-top:6px;">
                                        共 ${column.columnArticleCount?c} 章
                                        <#if column.latestChapter?? && column.latestChapter.articlePermalink??>
                                            · 最新：<a href="${servePath}${column.latestChapter.articlePermalink}">第 ${column.latestChapter.chapterNo?c} 章</a>
                                        </#if>
                                    </div>
                                </li>
                            </#list>
                        </ul>
                    <#else>
                        <div class="ft-center ft-gray" style="padding:20px 0;">暂无专栏数据</div>
                    </#if>
                </div>
            </div>

            <div class="module">
                <div class="module-header"><h2>热门专栏</h2></div>
                <div class="module-panel">
                    <#if hotLongColumns?? && hotLongColumns?size != 0>
                        <ul class="module-list long-column-module-list">
                            <#list hotLongColumns as column>
                                <#assign columnId = column.columnId!column.oId>
                                <li>
                                    <a class="title" href="${servePath}/column/${columnId}">${column.columnTitle}</a>
                                    <div class="ft-smaller ft-gray" style="margin-top:6px;">共 ${column.columnArticleCount?c} 章</div>
                                </li>
                            </#list>
                        </ul>
                    <#else>
                        <div class="ft-center ft-gray" style="padding:20px 0;">暂无专栏数据</div>
                    </#if>
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
