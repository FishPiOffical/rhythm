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
<@top "balance">
<h2 class="sub-head">
    <span>♠</span> ${wealthRankLabel}
</h2>
<div class="list">
    <ul>
        <#list topBalanceUsers as user>
        <li>
            <div class="fn-flex">
                <a rel="nofollow"
                   class="tooltipped tooltipped-se"
                   href="${servePath}/member/${user.userName}" 
                   aria-label="${user.userName}"><div class="avatar" style="background-image:url('${user.userAvatarURL}')"></div></a>
                <div class="has-view fn-flex-1">
                    <h2>
                        ${user_index + 1}.
                        <a rel="bookmark" href="${servePath}/member/${user.userName}">${user.userName}</a>
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
                            ${symphonyLabel} ${user.userNo?c} ${numMemberLabel},
                            <#if 0 == user.userAppRole>${hackerLabel}<#else>${painterLabel}</#if>
                        </div>
                    </div>
                    <div class="cmts tooltipped tooltipped-w" aria-label="${user.userPoint?c}">
                        <a href="${servePath}/member/${user.userName}/points">
                            ${user.userPoint?c}
                        </a>
                        ~ ${yuanLabel}${user.money?c}
                    </div>
                </div>
            </div>
        </li>
        </#list>
    </ul>
</div>
</@top>
