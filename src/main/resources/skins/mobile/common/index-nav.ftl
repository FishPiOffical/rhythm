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
<#macro indexNav type>
    <div class="index-top__nav">
        <a href="${servePath}/recent"<#if 'recent' == type> class="item--current"</#if>>最新</a>
        <a href="${servePath}/hot"<#if 'hot' == type> class="item--current"</#if>>热门</a>
        <a href="${servePath}/column"<#if 'column' == type> class="item--current"</#if>>专栏</a>
        <a href="${servePath}/cr"<#if 'cr' == type> class="item--current"</#if>>聊天室</a>
        <a href="${servePath}/qna"<#if 'qna' == type> class="item--current"</#if>>问答</a>
        <#if isLoggedIn && "" != currentUser.userCity>
            <a href="${servePath}/city/my"<#if 'city' == type> class="item--current"</#if>>${currentUser.userCity}</a>
        </#if>
        <#if isLoggedIn>
            <a href="${servePath}/watch" <#if 'watch' == type> class="item--current"</#if>>${followLabel}</a>
        </#if>
    </div>
</#macro>
