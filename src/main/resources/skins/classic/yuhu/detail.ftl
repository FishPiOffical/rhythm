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
<div class="container">
    这是书的详情

</div>
<div class="dashboard-container" style="width: 80%;margin: 20px 10%">
    <div class="dashboard-left" style="width: 75%">
        <div class="yuhu-container">
            <div class="yuhu-title">简介</div>
        </div>
        <div class="yuhu-container">
            <div class="yuhu-title">投票</div>
        </div>
        <div class="yuhu-container">
            <div class="yuhu-title">目录</div>
        </div>
    </div >
    <div class="dashboard-right" style="width: 24%;">
        <div class="yuhu-container">
            <div class="yuhu-title">荣誉</div>
        </div>
        <div class="yuhu-container">
            <div class="yuhu-title">我的</div>
        </div>
        <div class="yuhu-container">
            <div class="yuhu-title">推荐</div>
        </div>
        <div class="yuhu-container">
            <div class="yuhu-title">书友榜</div>
        </div>
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