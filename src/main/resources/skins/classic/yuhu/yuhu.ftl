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
        <div class="yuhu-main-title">鱼乎/YUHU</div>
        <div class="yuhu-search">
            <input class="yuhu-search-input" type="text" placeholder="搜索鱼乎">
            <div class="yuhu-search-label">搜索</div>
        </div>
    </div>
    <div class="yuhu-container">
        <div class="yuhu-title">推荐</div>
    </div>
    <div class="yuhu-container">
        <div class="yuhu-title">分类</div>
    </div>
    <div class="yuhu-container">
        <div class="yuhu-title">我的收藏</div>
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