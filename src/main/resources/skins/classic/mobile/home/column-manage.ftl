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
<#include "../common/index-nav.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="长篇管理 - ${symphonyLabel}">
        <meta name="description" content="长篇管理"/>
    </@head>
</head>
<body>
<div class="mobile-head">
    <#include "../header.ftl">
    <@indexNav 'column'/>
</div>
<div style="height: 74px;width: 1px;"></div>

<#assign showingSingleColumn = selectedColumnId?? && selectedColumnId?has_content>
<div class="main column-manage">
    <div class="module_new">
        <h2 class="module__title ft__fade fn__clear column-manage__heading"><#if showingSingleColumn>全书封面<#else>长篇管理</#if></h2>
    </div>
    <#if manageableColumns?? && manageableColumns?size != 0>
        <div class="column-manage__list">
        <#assign displayedColumnCount = 0>
        <#list manageableColumns as column>
            <#assign columnId = column.columnId!column.oId>
            <#if !showingSingleColumn || selectedColumnId == columnId>
                <#assign displayedColumnCount = displayedColumnCount + 1>
                <#assign columnCoverURL = column.columnCoverURL!''>
                <#assign columnHasCover = (column.columnHasCover!false) && columnCoverURL?has_content>
                <article class="column-manage__item<#if selectedColumnId?? && selectedColumnId == columnId> column-manage__item--current</#if>" data-column-id="${columnId}">
                   <a class="column-cover column-manage__cover<#if !columnHasCover> column-cover--default</#if>"
                      href="${servePath}/column/${columnId}"
                       <#if columnCoverURL?has_content>style="background-image:url('${columnCoverURL?html}')"</#if>>
                        <span>${column.columnTitle!''}</span>
                    </a>
                    <div class="column-manage__body">
                        <a class="column-manage__title" href="${servePath}/column/${columnId}">${column.columnTitle!''}</a>
                        <div class="column-manage__meta">${(column.columnArticleCount!0)?c} 章共用</div>
                        <input class="column-manage__input" type="text" maxlength="1024"
                               value="<#if columnHasCover>${columnCoverURL?html}</#if>"
                               placeholder="封面 URL"/>
                        <div class="column-manage__form">
                            <button type="button" class="column-manage__upload">上传</button>
                            <button type="button" class="green column-manage__save">保存</button>
                            <button type="button" class="column-manage__clear">清空</button>
                        </div>
                    </div>
                </article>
            </#if>
        </#list>
        <#if displayedColumnCount == 0>
            <div class="wrapper ft-center ft-gray" style="padding:20px 0;">暂无专栏</div>
        </#if>
        </div>
    <#else>
        <div class="wrapper ft-center ft-gray" style="padding:20px 0;">暂无专栏</div>
    </#if>
</div>

<#include "../footer.ftl">
<script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
<script src="${staticServePath}/js/home-personalize${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/column-cover-manage${miniPostfix}.js?${staticResourceVersion}"></script>
<script>
    HomePersonalize.initColumnManage()
</script>
</body>
</html>
