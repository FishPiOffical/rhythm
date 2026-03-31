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
<script src="${staticServePath}/js/lib/lottie.min.js"></script>
<script src="${staticServePath}/js/lib/logo.js"></script>
<div class="nav">
    <div class="wrapper">
        <div class="head-fn fn-left">
            <h1>
                <a href="${servePath}" aria-label="${symphonyLabel}">
                    <div style="width: 39px; height: auto" id="logo-animate"></div>
                    <script>
                        var params = {
                            container: document.getElementById('logo-animate'),
                            renderer: 'svg',
                            loop: false,
                            autoplay: true,
                            animationData: logoData
                        };

                        var anim;
                        anim = lottie.loadAnimation(params);
                    </script>
                </a>
            </h1>
        </div>

        <div class="user-nav">
            <#if isLoggedIn>
            <#if permissions["menuAdmin"].permissionGrant>
            <a href="${servePath}/admin" title="${adminLabel}" class="last"><svg><use xlink:href="#manage"></use></svg></a>
            </#if>
            <a href="${servePath}/activities" title="${activityLabel}"><svg><use xlink:href="#playgame"></use></svg></a>
            <a href="https://ext.adventext.fun/" target="_blank" rel="noopener noreferrer" title="插件市场">
                <svg><use xlink:href="#ext-empty"></use></svg>
            </a>
            <a href="javascript:void(0)" title="${colorModeLabel}" id="color-mode"><svg><use xlink:href="#color-sun"></use></svg></a>
            <#if permissions["commonAddArticle"].permissionGrant>
                <a href="${servePath}/pre-post" title="${addArticleLabel}"><svg><use xlink:href="#addpost"></use></svg></a>
            </#if>
            <a id="aChat" href="${servePath}/chat" class="tooltipped tooltipped-w no-msg" aria-label="私信">
                <svg style="height: 15px;vertical-align: -3px;">
                    <use xlink:href="#idleChat"></use>
                </svg>
            </a>
            <#if unreadChat?? && unreadChat gt 0>
            <script>
                if (window.location.pathname !== "/chat") {
                    setTimeout(function () {
                        Util.blingChat();
                    }, 1000);
                }
            </script>
            </#if>
            <a id="aNotifications" class="<#if unreadNotificationCount == 0>no-msg<#else>msg</#if>" href="${servePath}/notifications" title="${messageLabel}">${unreadNotificationCount}</a>
            <a href="${servePath}/member/${currentUser.userName}" title="Home" class="<#if 'adminRole' != userRole>last </#if>nav-avatar">
                <span class="avatar-small" style="background-image:url('${currentUser.userAvatarURL48}')"></span>
            </a>
            <#else>
                <a href="javascript: Util.goLogin();" title="${loginLabel}" class="unlogin">${loginLabel}</a>
                <a id="aRegister" href="javascript:Util.goRegister()" class="last ft-blue unlogin"
                 title="${registerLabel}">${registerLabel}</a>
                <a href="javascript:void(0)" title="${colorModeLabel}" id="color-mode"><svg><use xlink:href="#color-sun"></use></svg></a>
            </#if>
        </div>
    </div>
</div>
