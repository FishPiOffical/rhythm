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
<@top "evolve">
<h2 class="sub-head">进化排行榜</h2>
<div class="module-header fn-clear">
    <span class="fn-left ft-fade">
        <a <#if type == "achievement">class="ft-gray" </#if> href="${servePath}/top/evolve?type=achievement">成就榜</a>
        &nbsp;|&nbsp;
        <a <#if type == "know">class="ft-gray" </#if> href="${servePath}/top/evolve?type=know">芝士榜</a>
        &nbsp;|&nbsp;
        <a <#if type == "reset">class="ft-gray" </#if> href="${servePath}/top/evolve?type=reset">重开榜</a>
        &nbsp;|&nbsp;
        <a <#if type == "days">class="ft-gray" </#if> href="${servePath}/top/evolve?type=days">爆肝榜</a>
    </span>
</div>
<div class="list">
    <ul>
        <#list topUsers as user>
        <li>
            <div class="fn-flex">
                <a rel="nofollow"
                   href="${servePath}/member/${user.profile.userName}"
                   ><div class="avatar" aria-label="${user.profile.userName}" style="background-image:url('${user.profile.userAvatarURL48}')"></div></a>
                <div class="has-view fn-flex-1">
                    <h2>
                        ${user_index + 1}.
                        <a rel="bookmark" href="${servePath}/member/${user.profile.userName}">${user.profile.userName}</a>
                    </h2>
                    <div class="ft-gray">
                        <#if user.profile.userIntro!="">
                        <div>
                            ${user.profile.userIntro}
                        </div>
                        </#if>
                        <#if user.profile.userURL!="">
                        <div>
                            <a target="_blank" rel="friend" href="${user.profile.userURL?html}">${user.profile.userURL?html}</a>
                        </div>
                        </#if>
                        <div>
                            ${symphonyLabelClear} ${user.profile.userNo?c} ${numMemberLabel},
                            <#if 0 == user.profile.userAppRole>${hackerLabel}<#else>${painterLabel}</#if>
                        </div>
                    </div>
                    <#if type == "achievement">
                        <div class="cmts tooltipped tooltipped-w" aria-label="共获得成就 ${user.data.top.achievement} 个">
                            获得成就 ${user.data.top.achievement} 个<br>
                        </div>
                    </#if>
                    <#if type == "know">
                        <div class="cmts tooltipped tooltipped-w" aria-label="累计知识消耗 ${user.data.top.know}; 当前周期: ${user.data.stats.know}">
                            知识消耗 ${user.data.top.know} <br>
                        </div>
                    </#if>
                    <#if type == "reset">
                        <div class="cmts tooltipped tooltipped-w" aria-label="核爆重置: ${user.data.stats.mad} 次; 播种重置: ${user.data.stats.bioseed} 次; 黑洞重置: ${user.data.stats.blackhole} 次; 飞升重置: ${user.data.stats.ascend} 次; 大灾变重置: ${user.data.stats.cataclysm} 次; 恶魔灌注: ${user.data.stats.descend} 次">
                            重置 ${user.data.top.reset} 次<br>
                        </div>
                    </#if>
                    <#if type == "days">
                        <div class="cmts tooltipped tooltipped-w" aria-label="累计游戏天数 ${user.data.top.days} 天; 当前周期: ${user.data.stats.days} 天">
                            游戏天数 ${user.data.top.days} 天<br>
                        </div>
                    </#if>
                </div>
            </div>
        </li>
        </#list>
    </ul>
    <br/>
</div>
</@top>
