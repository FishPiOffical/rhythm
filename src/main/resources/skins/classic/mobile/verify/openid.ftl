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
    <link rel="canonical" href="${servePath}/register">
</head>
<body>
<#include "../header.ftl">
<div class="main">
    <div class="wrapper verify">
        <div class="verify-wrap" >
            <div class="openid-info">
                <div class="tip1">登录到 ${realmName}</div>
                <div class="tip2">非 ${symphonyLabel} 站点</div>
                <div class="info-box">
                    <span class="avatar-small avatar-openid" style="background-image:url('${currentUser.userAvatarURL48}')"></span>
                    <div class="info-detail" style="flex:1">
                        <div style="color:#4285f4">${currentUser.userNickname}</div>
                        <div>${currentUser.userName}</div>
                    </div>
                    <a href="javascript:Util.logout()">这不是您？</a>
                </div>
                <form action="${servePath}/openid/confirm" method="post">
                    <input type="hidden" name="fishpi.authRequestId" value="${fishpi_auth_request_id}">
                    <input type="hidden" name="openid.ns" value="${openid_ns}">
                    <input type="hidden" name="openid.mode" value="${openid_mode}">
                    <input type="hidden" name="openid.return_to" value="${openid_return_to}">
                    <input type="hidden" name="openid.identity" value="${openid_identity}">
                    <input type="hidden" name="openid.claimed_id" value="${openid_claimed_id}">
                    <input type="hidden" name="openid.realm" value="${openid_realm}">
                    <div style="margin: 16px 0;text-align: left">
                        <#list fishpiScopes as scope>
                            <label style="align-items:center;display:flex;gap:8px;margin:8px 0">
                                <input type="checkbox" name="fishpi.scope.${scope.key}" value="true" <#if scope.requested>checked disabled</#if>>
                                <#if scope.requested>
                                    <input type="hidden" name="fishpi.scope.${scope.key}" value="true">
                                </#if>
                                <span>${scope.label}</span>
                                <#if scope.requested>
                                    <span class="ft-smaller ft-gray">必选</span>
                                </#if>
                            </label>
                        </#list>
                    </div>
                    <div style="display:flex;gap:10px">
                        <button class="green" style="flex: 1" type="submit">${loginLabel}</button>
                        <button style="flex: 1" type="submit" name="cancel" value="true">取消</button>
                    </div>
                </form>
            </div>
        </div>
        <div class="intro vditor-reset" style="padding: 20px">
            <div class="openid-intro">
                <div style="margin-bottom: 16px" class="openid-intro-title">授权内容</div>
                <ul>
                    <li>个人信息：头像、昵称、用户名</li>
                    <li>积分信息：余额、记录</li>
                    <li>发帖信息：公开发帖</li>
                </ul>
                <div class="openid-intro-title">登录即授权所选项</div>
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
