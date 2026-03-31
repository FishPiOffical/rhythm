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
<#include "../macro-head.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="${symphonyLabel}">
        <meta name="description" content="${registerLabel} ${symphonyLabel}"/>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"/>
    <#--        <link rel="stylesheet" href="${staticServePath}/css/theme/dark-index.css?${staticResourceVersion}" />-->
    <link rel="canonical" href="${servePath}/register">
</head>
<body>
<#include "../header.ftl">
<div class="main">
    <div class="wrapper verify" style="align-items: center;background-color: #f1f7fe;">
        <div class="verify-wrap" style="background-color: #fff" >

            <div class="openid-info">

                <div class="tip1">使用您的 ${visionLabel} 账户登录到 ${realmName} </div>
                <div class="tip2">请注意，${realmName} 不附属于 ${symphonyLabel}</div>
                <div class="info-box" >
                    <span class="avatar-mid" style="background-image:url('${currentUser.userAvatarURL48}')"></span>
                    <div class="info-detail" style="flex:1">
                        <div style="color:#4285f4">${currentUser.userNickname}</div>
                        <div>${currentUser.userName}</div>
                    </div>
                    <a href="javascript:Util.logout()">这不是您？</a>
                </div>
                <form action="${servePath}/openid/confirm" method="post">
                    <input type="hidden" name="openid.ns" value="${openid_ns}">
                    <input type="hidden" name="openid.mode" value="${openid_mode}">
                    <input type="hidden" name="openid.return_to" value="${openid_return_to}">
                    <input type="hidden" name="openid.identity" value="${openid_identity}">
                    <input type="hidden" name="openid.claimed_id" value="${openid_claimed_id}">
                    <input type="hidden" name="openid.realm" value="${openid_realm}">
                    <button class="green" type="submit">${loginLabel}</button>
                </form>
            </div>
        </div>
        <div class="intro vditor-reset" style="height: 100%">
            <div class="openid-intro">
                <div style="margin-bottom: 16px" class="openid-intro-title">通过  ${visionLabel} 账户登录到 ${realmName}：</div>
                <ul>
                    <li>您的 ${visionLabel} 登录凭据不会被共享。</li>
                    <li>将与 <b>${realmName}</b> 共享唯一的数字标识符。</li>
                    <li>${realmName} 可以获得您在 ${visionLabel} 上的<b>头像</b>，<b>昵称</b>，<b>用户名</b>。</li>
                </ul>
                <div class="openid-intro-title">点击“登录”表示您同意共享此数据。 </div>
            </div>
        </div>
    </div>
</div>
<#include "../footer.ftl">
<script src="${staticServePath}/js/verify${miniPostfix}.js?${staticResourceVersion}"></script>
<script>


</script>
</body>
</html>
