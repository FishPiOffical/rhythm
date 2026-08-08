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
<#include "macro-top.ftl">
<@top "profession">
<section class="profession-ranking">
    <header class="profession-ranking__head">
        <div><h2>职业排行</h2><p>按已公开职业记录排序</p></div>
        <form class="profession-ranking__filter" method="get" action="${servePath}/top/profession">
            <label><span>职业</span><select name="professionId" onchange="this.form.submit()">
                <#list professionRankingDefinitions as profession>
                <option value="${profession.professionId}"<#if profession.professionId == selectedProfessionId> selected</#if>>${profession.displayName}</option>
                </#list>
            </select></label>
        </form>
    </header>
    <#if professionRankingEntries?size == 0>
    <p class="profession-ranking__empty">暂无可展示的职业记录</p>
    <#else>
    <ol class="profession-ranking__list">
        <#list professionRankingEntries as entry>
        <li class="profession-ranking__item">
            <span class="profession-ranking__rank">${entry.rank}</span>
            <a class="profession-ranking__user" href="${servePath}/member/${entry.userName}">
                <span class="avatar" style="background-image:url('${entry.userAvatarURL48}')"></span>
                <span><strong>${entry.userNickname!entry.userName}</strong><small>@${entry.userName}</small></span>
            </a>
            <span class="profession-ranking__profession">
                <#if entry.iconUrl != ""><img src="${entry.iconUrl}" alt=""/></#if>
                <span style="--profession-primary:${entry.primaryColor!'#2563eb'}"><strong>${entry.professionName}</strong><small>${entry.levelName!"未定级"}</small></span>
            </span>
            <#if entry.totalExperience??><strong class="profession-ranking__experience">${entry.totalExperience?c} 经验</strong></#if>
        </li>
        </#list>
    </ol>
    </#if>
</section>
</@top>
