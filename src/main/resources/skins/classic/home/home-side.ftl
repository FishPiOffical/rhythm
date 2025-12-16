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
<div class="ft-center module profile-module">
    <div id="avatarURLDom"
         class="avatar-big"
         aria-label="${user.userName}"
         style="background-image:url('${user.userAvatarURL210}')"></div>
    <div>
        <div class="user-name">
            <div id="userNicknameDom"><b>${user.userNickname}</b></div>
            <div class="ft-gray">${user.userName}</div>

            <div id="metal">
            </div>

            <div>
                <#if isLoggedIn && (currentUser.userName != user.userName)>
                    <button class="green small"
                            onclick="window.location.href = '${servePath}/chat?toUser=${user.userName}'">
                        ${privateMessageLabel}
                    </button>
                </#if>
                <#if user.mbti != "">
                    <a target="_blank" style="text-decoration: none"
                       href="https://www.16personalities.com/ch/${user.mbti[0..3]}-%E4%BA%BA%E6%A0%BC"
                       class="tooltipped-new tooltipped__n" rel="nofollow" aria-label="TA是 ${user.mbti}">
                        <span class="mbti"><svg style="vertical-align: -3px"><use
                                        xlink:href="#mbti"></use></svg> ${user.mbti}</span>
                    </a>
                </#if>
                <#if (isLoggedIn && ("adminRole" == currentUser.userRole || currentUser.userName == user.userName)) || 0 == user.userOnlineStatus>
                    <span class="tooltipped tooltipped-n"
                          aria-label="<#if user.userOnlineFlag>${onlineLabel}<#else>${offlineLabel}</#if>">
                        <span class="<#if user.userOnlineFlag>online<#else>offline</#if>"><#if user.userOnlineFlag>在线<#else>离线</#if></span>
                    </span>
                </#if>
                <#if permissions["userAddPoint"].permissionGrant ||
                permissions["userAddUser"].permissionGrant ||
                permissions["userExchangePoint"].permissionGrant ||
                permissions["userDeductPoint"].permissionGrant ||
                permissions["userUpdateUserAdvanced"].permissionGrant ||
                permissions["userUpdateUserBasic"].permissionGrant>
                    <a class="ft-13 tooltipped tooltipped-n ft-a-title" href="${servePath}/admin/user/${user.oId}"
                       aria-label="${adminLabel}">
                        <svg class="fn-text-top">
                            <use xlink:href="#setting"></use>
                        </svg>
                    </a>
                </#if>
                <span aria-label="${reportLabel}" class="tooltipped tooltipped-n"
                      onclick="$('#reportDialog').data('id', '${user.oId}').dialog('open')"
                ><svg><use xlink:href="#icon-report"></use></svg></span>
            </div>
            <span class="game-badge "  data-oid="${user.oId}"></span>
            <div>
                <a href="https://fishpi.cn/article/1630575841478" target="_blank">
                    <img style="height: 26px;margin-top: 5px;" src="
                    <#if user.roleName == '管理员'>
                    https://file.fishpi.cn/adminRole.png
                    <#elseif user.roleName == 'OP'>
                    https://file.fishpi.cn/opRole.png
                    <#elseif user.roleName == '纪律委员'>
                    https://file.fishpi.cn/policeRole.png
                    <#elseif user.roleName == '超级会员'>
                    https://file.fishpi.cn/svipRole.png
                    <#elseif user.roleName == '成员'>
                    https://file.fishpi.cn/vipRole.png
                    <#else>
                    https://file.fishpi.cn/newRole.png
                    </#if>
                    "/>
                </a>
            </div>

            <#if isLoggedIn && (currentUser.userName != user.userName)>
                <#if isFollowing>
                    <button class="follow" onclick="Util.unfollow(this, '${followingId}', 'user')">
                        ${unfollowLabel}
                    </button>
                <#else>
                    <button class="follow" onclick="Util.follow(this, '${followingId}', 'user')">
                        ${followLabel}
                    </button>
                </#if>
            </#if>
        </div>

        <div class="user-details">
            <#if user.userIntro!="">
                <div class="user-intro" id="userIntroDom">
                    ${user.userIntro}
                </div>
            </#if>
            <div class="user-info">
                <span class="ft-gray">${symphonyLabelClear}</span>
                ${user.userNo?c}
                <span class="ft-gray">${numMemberLabel}</span>, <#if 0 == user.userAppRole>${hackerLabel}<#else>${painterLabel}</#if>
                <span class="ft-gray">${pointLabel}</span>
                <a href="${servePath}/member/${user.userName}/points" class="tooltipped tooltipped-n"
                   aria-label="${user.userPoint?c}">
                    <#if 0 == user.userAppRole>
                        0x${user.userPointHex}
                    <#else>
                        <div class="painter-point" style="background-color: #${user.userPointCC}"></div>
                    </#if>
                </a>
            </div>
            <#if "" != user.userTags>
                <div class="user-info">
                    <span class="ft-gray">${selfTagLabel}</span>
                    <span id="userTagsDom"><#list user.userTags?split(',') as tag> ${tag?html}<#if tag_has_next>,</#if></#list></span>
                </div>
            </#if>
            <#if "" != user.userCountry && "" != user.userProvince && "" != user.userCity && 0 == user.userGeoStatus>
                <div class="user-info">
                    <#if user.userCountry == "中国">
                        <span class="ft-gray">${geoLabel}</span> ${user.userProvince} ${user.userCity}
                    <#else>
                        <span class="ft-gray">${geoLabel}</span> ${user.userCountry} ${user.userProvince} ${user.userCity}
                    </#if>
                </div>
            <#elseif "" != user.userCountry && "" != user.userProvince && 0 == user.userGeoStatus>
                <div class="user-info">
                    <#if user.userCountry == "中国">
                        <span class="ft-gray">${geoLabel}</span> ${user.userProvince}
                    <#else>
                        <span class="ft-gray">${geoLabel}</span> ${user.userCountry} ${user.userProvince}
                    </#if>
                </div>
            </#if>
            <#if user.userURL!="">
                <div class="user-info">
                    <a id="userURLDom" target="_blank" rel="friend" href="${user.userURL?html}">${user.userURL?html}</a>
                </div>
            </#if>
            <div class="user-info">
                <span class="ft-gray">${joinTimeLabel}</span> ${user.userCreateTime?string('yyyy-MM-dd HH:mm')}
            </div>
            <div class="user-info">
                <span class="ft-gray">最后登录</span> ${user.userLatestLoginTime?string('yyyy-MM-dd HH:mm')}
            </div>
            <div class="user-info">
                <span class="ft-gray">${checkinStreakPart0Label}</span>
                ${user.userLongestCheckinStreak?c}
                <span class="ft-gray">${checkinStreakPart1Label}</span>
                ${user.userCurrentCheckinStreak?c}
                <span class="ft-gray">${checkinStreakPart2Label}</span>
            </div>
        </div>
        <ul class="status fn-flex" style="padding-bottom: 0">
            <li id="userTags">
                <strong>${user.userTagCount?c}</strong>
                <span class="ft-gray">${tagLabel}</span>
            </li>
            <li id="userArticles">
                <strong>${user.userArticleCount?c}</strong>
                <span class="ft-gray">${articleLabel}</span>
            </li>
            <li id="userComments">
                <strong>${user.userCommentCount?c}</strong>
                <span class="ft-gray">${cmtLabel}</span>
            </li>
            <li id="userFollower" style="cursor:pointer;">
                <strong>${followerCount}</strong>
                <span class="ft-gray">${followersLabel}</span>
            </li>
            <li id="userFollowing" style="cursor:pointer;">
                <strong>${followingUserCount}</strong>
                <span class="ft-gray">${followingUsersLabel}</span>
            </li>
        </ul>
        <ul class="status fn-flex" style="padding-top: 0">
            <li id="onlineMinute" style="cursor:pointer;">
                <strong>
                    <#assign x=(user.onlineMinute?c)>
                    <#if onlineTimeUnit??>
                        <#if onlineTimeUnit == 'h'>
                            <#assign t=(x?number/60)>
                            ${t} 小时
                        <#elseif onlineTimeUnit == 'd'>
                            <#assign t=(x?number/60/24)>
                            ${t} 天
                        <#else>
                            ${user.onlineMinute} 分钟
                        </#if>
                    <#else>
                        ${user.onlineMinute} 分钟
                    </#if>
                </strong>
                <span class="ft-gray">在线时间</span>
            </li>
        </ul>
    </div>
</div>

<script>
    window.onload = function() {
        const gameEmbed = new GameEmbed();
        gameEmbed.listen('.game-badge', 'oid');
    };

    document.getElementById("userFollower").addEventListener("click", function () {
        window.location.href = "${servePath}/member/${user.userName}/followers";
    });
    document.getElementById("userFollowing").addEventListener("click", function () {
        window.location.href = "${servePath}/member/${user.userName}/following/users";
    });
    document.getElementById("onlineMinute").addEventListener("click", function () {
        window.location.href = "${servePath}/top/online";
    });
    let user = ${user}
    <#if user.membership??>
    let membership = ${user.membership};
    let userNameDom = document.querySelector('.user-name #userNicknameDom');
    let userDom = document.querySelector('.user-name .ft-gray');
    if (typeof membership.configJson == 'string' && membership.configJson != '') {
        membership.configJson = JSON.parse(membership.configJson)
    }
    const targetDom = user.userNickname === '' ? userDom : userNameDom;
    if (membership.configJson.bold) {
        targetDom.style.fontWeight = 'bold';
    }
    if (membership.configJson.underline) {
        targetDom.style.textDecoration = 'underline';
    }
    if (membership.configJson.color) {
        if (membership.configJson.color.startsWith('#')) {
            targetDom.style.color = membership.configJson.color;
        } else {
            targetDom.classList.add(membership.configJson.color)
        }
    }

    </#if>

</script>

<div id="reportDialog">
    <div class="form fn-clear">
        <div class="fn-clear"><label><input type="radio" value="5" name="report" checked> ${posingAccountLabel}</label>
        </div>
        <div class="fn-clear"><label><input type="radio" value="6" name="report"> ${spamADAccountLabel}</label></div>
        <div class="fn-clear"><label><input type="radio" value="7" name="report"> ${personalInfoViolationLabel}</label>
        </div>
        <div class="fn-clear"><label><input type="radio" value="49" name="report"> ${miscLabel}</label></div>
        <br>
        <textarea id="reportTextarea" placeholder="${reportContentLabel}" rows="3"></textarea><br><br>
        <button onclick="Settings.report(this)" class="fn-right green">${reportLabel}</button>
    </div>
</div>
