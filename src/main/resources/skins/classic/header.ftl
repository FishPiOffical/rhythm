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
    <h1 aria-label="${symphonyLabel}" class="tooltipped tooltipped-s">
        <a href="${servePath}">
            <div style="height: 55px;width: 55px;padding-bottom: 6px;" id="logo-animate"></div>
            <script>
                var params = {
                    container: document.getElementById('logo-animate'),
                    renderer: 'svg',
                    loop: false,
                    autoplay: false,
                    animationData: logoData
                };

                var anim;
                anim = lottie.loadAnimation(params);
                anim.goToAndPlay(27, true)
                setTimeout(function () {
                    $('#logo-animate').on('mouseenter', function(){
                        anim.goToAndPlay(10, true)
                    });
                }, 1000);
            </script>
        </a>
    </h1>
    <div class="responsive-hide nav-tabs">
        <a pjax-title="${latestLabel} - ${symphonyLabel}"
           href="${servePath}/recent"<#if selected?? && 'recent' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#refresh"></use>
            </svg> ${latestLabel}</a>
        <a pjax-title="${hotLabel} - ${symphonyLabel}"
           href="${servePath}/hot"<#if selected?? && 'hot' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#fire"></use>
            </svg> ${hotLabel}</a>
        <a pjax-title="${chatRoomLabel} - ${symphonyLabel}"
           href="${servePath}/cr"<#if selected?? && 'cr' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#chat"></use>
            </svg> ${chatRoomLabel}</a>
        <a pjax-title="${domainLabel} - ${symphonyLabel}"
           href="${servePath}/domains"<#if selected?? && 'domains' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#domain"></use>
            </svg> ${domainLabel}</a>
        <a pjax-title="${breezemoonLabel} - ${symphonyLabel}"
           href="${servePath}/breezemoons"<#if selected?? && 'breezemoons' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#breezemoons"></use>
            </svg> ${breezemoonLabel}</a>
        <a pjax-title="${qnaLabel} - ${symphonyLabel}"
           href="${servePath}/qna"<#if selected?? && 'qna' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#iconAsk"></use>
            </svg> ${qnaLabel}</a>
        <a href="${servePath}/perfect"<#if selected?? && 'perfect' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#perfect"></use>
            </svg> ${perfectLabel}</a>
        <#if isLoggedIn && "" != currentUser.userCity>
        <a href="${servePath}/city/my"<#if selected?? && 'city' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#local"></use>
            </svg> ${currentUser.userCity}</a>
        </#if>
        <a href="${servePath}/top"<#if selected?? && 'top' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#rank"></use>
            </svg> ${totalRankLabel}</a>
        <#if isLoggedIn>
        <a href="${servePath}/watch"<#if selected?? && 'watch' == selected> class="current"</#if>>
            <svg>
                <use xlink:href="#view"></use>
            </svg> ${followLabel}</a>
        </#if>
    </div>
    <#if esEnabled || algoliaEnabled>
    <form class="responsive-hide fn-left" target="_blank" action="/search">
        <input class="search" placeholder="搜索你感兴趣的内容" type="text" name="key" id="search"
               value="<#if key??>${key}</#if>">
        <input type="submit" class="fn-none" value=""/>
    </form>
    </#if>
    <div class="user-nav">
        <#if isLoggedIn>
            <#if permissions["menuAdmin"].permissionGrant>
            <a href="${servePath}/admin" aria-label="${adminLabel}" class="tooltipped tooltipped-w nav-btn-admin">
                <svg>
                    <use xlink:href="#manage"></use>
                </svg>
            </a>
            </#if>
            <a href="${servePath}/activities" aria-label="${activityLabel}" class="tooltipped tooltipped-w nav-btn-admin">
                <svg>
                    <use xlink:href="#playgame"></use>
                </svg>
            </a>
            <a href="javascript:void(0)" title="${colorModeLabel}" id="color-mode">
                <svg>
                    <use xlink:href="#color-sun"></use>
                </svg>
            </a>
            <a id="aNotifications"
               class="tooltipped tooltipped-w <#if unreadNotificationCount == 0>no-msg<#else>msg</#if>"
               href="${servePath}/notifications" aria-label="${messageLabel}">
                <svg style="height: 15px;pointer-events: none;">
                    <use xlink:href="#notification"></use>
                </svg>
                &nbsp;${unreadNotificationCount}
            </a>
            <a id="aChat" href="${servePath}/chat" class="tooltipped tooltipped-w no-msg" aria-label="私信">
                <svg style="height: 15px;">
                    <use xlink:href="#idleChat"></use>
                </svg>
                &nbsp;<span id="aChatCount"><#if unreadChat?? && unreadChat gt 0>${unreadChat}<#else>0</#if></span>
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
            <a href="${servePath}/pre-post" class="pre-post">
                <svg style="height: 16px">
                    <use xlink:href="#addpost"></use>
                    &nbsp;
                    <span>发帖</span>
                </svg>
            </a>
            <a href="javascript:void(0)" id="aPersonListPanel"
               data-url="${servePath}/member/${currentUser.userName}">
                <span class="avatar-small" style="background-image:url('${currentUser.userAvatarURL48}')"></span>

            </a>
            <div class="module person-list" id="personListPanel">
                <ul>
                    <li>
                        <a href="${servePath}/member/${currentUser.userName}">${goHomeLabel}</a>
                    </li>
                    <li>
                        <a href="${servePath}/settings">${settingsLabel}</a>
                    </li>
                    <li>
                        <a href="javascript:void(0)" onclick="Count.settings();">⏰ 下班倒计时</a>
                    </li>
                    <li>
                        <a href="${servePath}/settings/help">${helpLabel}</a>
                    </li>
                    <li>
                        <a href="javascript:Util.logout()">${logoutLabel}</a>
                    </li>
                </ul>
            </div>
        <#else>
            <a href="javascript: Util.goLogin();" class="unlogin">${loginLabel}</a>
            <a href="javascript:Util.goRegister()" class="unlogin">${registerLabel}</a>
            <a href="javascript:void(0)" title="${colorModeLabel}" id="color-mode">
                <svg>
                    <use xlink:href="#color-sun"></use>
                </svg>
            </a>
        </#if>
    </div>
</div>
