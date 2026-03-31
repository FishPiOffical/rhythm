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
<@top "donate">
    <h2 class="sub-head">鱼排续命师</h2>
    <div class="module-header fn-clear">
        <span class="fn-left">
            感谢你们为社区运营续航 <b>${totalData.donateMakeDays} 天</b>
        </span>
    </div>
    <div class="list">
        <ul>
            <#list data as user>
                <li>
                    <div class="fn-flex">
                        <a rel="nofollow"
                           href="${servePath}/member/${user.profile.userName}"
                        >
                            <div class="avatar" aria-label="${user.profile.userName}"
                                 style="background-image:url('${user.profile.userAvatarURL48}')"></div>
                        </a>
                        <div class="has-view fn-flex-1">
                            <h2>
                                ${user_index + 1}.
                                <a rel="bookmark"
                                   href="${servePath}/member/${user.profile.userName}">${user.profile.userName}</a>
                            </h2>
                            <div class="ft-gray">
                                <#if user.profile.userIntro!="">
                                    <div>
                                        ${user.profile.userIntro}
                                    </div>
                                </#if>
                                <#if user.profile.userURL!="">
                                    <div>
                                        <a target="_blank" rel="friend"
                                           href="${user.profile.userURL?html}">${user.profile.userURL?html}</a>
                                    </div>
                                </#if>
                                <div>
                                    ${symphonyLabelClear} ${user.profile.userNo?c} ${numMemberLabel},
                                    <#if 0 == user.profile.userAppRole>${hackerLabel}<#else>${painterLabel}</#if>
                                </div>
                            </div>
                            <div class="cmts tooltipped tooltipped-w" aria-label="共计捐助 ${user.totalCount}笔 ${user.total}❤️，谢谢老板！">
                                ${user.total} ❤️
                            </div>
                        </div>
                    </div>
                </li>
            </#list>
        </ul>
        <br/>
    </div>
</@top>
