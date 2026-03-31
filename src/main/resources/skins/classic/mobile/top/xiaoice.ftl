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
<@top "xiaoice">
    <h2 class="sub-head">小冰游戏排行榜</h2>
    <div class="module-header fn-clear">
    <span class="fn-left ft-fade">
        <a<#if type == "0"> class="ft-gray"</#if> href="${servePath}/top/xiaoice?type=0">等级排行</a>
        &nbsp;|&nbsp;
        <a<#if type == "1"> class="ft-gray"</#if> href="${servePath}/top/xiaoice?type=1">死亡排行</a>
        &nbsp;|&nbsp;
        <a<#if type == "2"> class="ft-gray"</#if> href="${servePath}/top/xiaoice?type=2">历练排行</a>
        &nbsp;|&nbsp;
        <a<#if type == "3"> class="ft-gray"</#if> href="${servePath}/top/xiaoice?type=3">吐纳排行</a>
    </span>
    </div>
    <div class="list">
        <ul>
            <#list data as user>
                <li>
                    <div class="fn-flex">
                        <a rel="nofollow"
                           href="${servePath}/member/${user.uname}"
                        ><div class="avatar" aria-label="${user.uname}" style="background-image:url('${user.userAvatarURL48}')"></div></a>
                        <div class="has-view fn-flex-1">
                            <h2>
                                ${user_index + 1}.
                                <a rel="bookmark" href="${servePath}/member/${user.uname}">${user.uname}</a>
                            </h2>
                            <div class="ft-gray">
                                <#if user.userIntro!="">
                                    <div>
                                        ${user.userIntro}
                                    </div>
                                </#if>
                                <#if user.userURL!="">
                                    <div>
                                        <a target="_blank" rel="friend" href="${user.userURL?html}">${user.userURL?html}</a>
                                    </div>
                                </#if>
                                <div>
                                    ${symphonyLabelClear} ${user.userNo?c} ${numMemberLabel},
                                    <#if 0 == user.userAppRole>${hackerLabel}<#else>${painterLabel}</#if>
                                </div>
                            </div>
                            <#if type == "0">
                                <div class="cmts tooltipped tooltipped-w" aria-label="经验值 ${user.exp} 点">
                                    ${user.exp} 经验值<br>
                                </div>
                            </#if>
                            <#if type == "1">
                                <div class="cmts tooltipped tooltipped-w" aria-label="死亡次数 ${user.dieTimes} 次">
                                    ${user.dieTimes} 次<br>
                                </div>
                            </#if>
                            <#if type == "2">
                                <div class="cmts tooltipped tooltipped-w" aria-label="历练 ${user.allExTimes} 次">
                                    ${user.allExTimes} 次<br>
                                </div>
                            </#if>
                            <#if type == "3">
                                <div class="cmts tooltipped tooltipped-w" aria-label="吐纳 ${user.allBsTimes} 次">
                                    ${user.allBsTimes} 次<br>
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
