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
    <@head title="鱼乎 - ${symphonyLabel}">
        <meta name="robots" content="none"/>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"/>
    <link rel="stylesheet" href="${staticServePath}/css/home.css?${staticResourceVersion}"/>
    <link rel="stylesheet" href="${staticServePath}/css/yuhu.css?${staticResourceVersion}">
</head>
<body>
<#include "../header.ftl">
<div class="dashboard-container">
     <div class="dashboard-left"> 
      <div class="dashboard-content">
            <div class="dashboard-content-title">个人信息</div>
            <div class="dashboard-content-box">
            <img src="${user.userAvatarURL}"/>
            </div>
      </div>
    </div>
    <div class="dashboard-right">
      <ul>
        <li>工作台</li>
        <li>作品管理</li>
        <li>数据中心</li>
        <li>互动管理</li>
        <li>消息通知</li>
      </ul>
    </div>
</div>
<#include "../footer.ftl">
<script src="${staticServePath}/js/common${miniPostfix}.js?${staticResourceVersion}"></script>
<script>
    var Label = {
        servePath: "${servePath}",
        makeAsReadLabel: '${makeAsReadLabel}',
        notificationCommentedLabel: '${notificationCommentedLabel}',
        notificationReplyLabel: '${notificationReplyLabel}',
        notificationAtLabel: '${notificationAtLabel}',
        notificationFollowingLabel: '${notificationFollowingLabel}',
        pointLabel: '${pointLabel}',
        sameCityLabel: '${sameCityLabel}',
        systemLabel: '${systemLabel}',
        newFollowerLabel: '${newFollowerLabel}',
        <#if isLoggedIn>
        currentUserName: '${currentUser.userName}',
        </#if>

    }
</script>
</body>
</html>