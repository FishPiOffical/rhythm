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
<#include "macro-admin.ftl">
<@admin "petLogs">
<div class="admin">
    <div class="module list">
        <div class="module-header">
            <a href="${servePath}/admin/pet-logs?tab=activity"<#if petLogTab == "activity"> aria-current="page"</#if>>活动日志</a>
            ·
            <a href="${servePath}/admin/pet-logs?tab=risk"<#if petLogTab == "risk"> aria-current="page"</#if>>风控日志</a>
        </div>
        <div class="module-panel">
            <form method="get" action="${servePath}/admin/pet-logs">
                <input type="hidden" name="tab" value="${petLogTab?html}">
                <p><label>用户 <input type="text" name="user" maxlength="64" value="${petLogUser?html}" placeholder="用户名或 UID"></label></p>
                <p><label>开始日期 <input type="date" name="from" value="${petLogFrom?html}" required></label></p>
                <p><label>结束日期 <input type="date" name="to" value="${petLogTo?html}" required></label></p>
                <p>
                    <label>操作类型
                        <select name="operation">
                            <option value="">全部</option>
                            <#list petLogOperations?keys?sort as key>
                            <option value="${key?html}"<#if petLogOperation == key> selected</#if>>${petLogOperations[key]?html}</option>
                            </#list>
                        </select>
                    </label>
                    <button type="submit">筛选</button>
                    <a href="${servePath}/admin/pet-logs?tab=${petLogTab?html}">重置</a>
                </p>
                <p class="ft-smaller ft-gray">日期按北京时间计算，包含首尾两天，每次最多查询 31 天。纯数字用户名请加 @。</p>
            </form>
            <#if petLogError??>
                <p role="alert">${petLogError?html}</p>
            <#else>
                <ul class="notification">
                    <#list petLogRows as row>
                        <li>
                            <strong>${row.operation?html}</strong>
                            <#if row.detail?has_content> · ${row.detail?html}</#if>
                            <#if row.status?has_content> · ${row.status?html}</#if>
                            <div class="ft-smaller ft-gray">
                                ${row.time?html} · UID ${row.userId?html}
                                <#if row.petId?has_content> · 宠物 ID ${row.petId?html}</#if>
                            </div>
                        </li>
                    <#else>
                        <li>当前筛选条件下没有日志。</li>
                    </#list>
                </ul>
                <#assign filterQuery = "tab=" + petLogTab?url("UTF-8") + "&user=" + petLogUser?url("UTF-8")
                    + "&from=" + petLogFrom?url("UTF-8") + "&to=" + petLogTo?url("UTF-8")
                    + "&operation=" + petLogOperation?url("UTF-8")>
                <nav aria-label="宠物日志分页">
                    <#if petLogPage gt 1>
                        <a href="${servePath}/admin/pet-logs?${filterQuery?html}&amp;p=${(petLogPage - 1)?c}">上一页</a>
                    </#if>
                    <span>第 ${petLogPage?c} 页</span>
                    <#if petLogHasNext>
                        <a href="${servePath}/admin/pet-logs?${filterQuery?html}&amp;p=${(petLogPage + 1)?c}">下一页</a>
                    </#if>
                </nav>
            </#if>
        </div>
    </div>
</div>
</@admin>
