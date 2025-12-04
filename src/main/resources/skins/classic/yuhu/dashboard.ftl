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
        <div class="dashboard-box active">
            <div class="dashboard-content">
                <div class="dashboard-content-title">个人信息</div>
                <div class="dashboard-content-box">
                    <img src="${user.userAvatarURL}" width="64px" height="64px" style="border-radius: 50%"/>
                    <span style="font-size: 18px;font-weight: bold;margin: 0 10px">${user.userName}</span>
                    <span class="author-tag normal">作家</span>
                </div>
            </div>
            <div class="dashboard-content">
                <div class="dashboard-content-title">作品管理</div>
                <div class="dashboard-content-box" style="display: flex">
                    <img src="${user.userAvatarURL}" class="books-avatar"/>
                    <div class="" style="margin-left: 20px;">
                        <div>作品名称</div>
                        <div>最新章节-xxxx</div>
                        <div>更新时间 xxxx-xx-xx</div>
                    </div>
                </div>
            </div>
        </div>
        <div class="dashboard-box">2</div>
        <div class="dashboard-box">3</div>
        <div class="dashboard-box">4</div>
        <div class="dashboard-box">5</div>
    </div>
    <div class="dashboard-right">
        <ul class="dashboard-manu">
            <li class="dashboard-manu-item active" onclick="changeType('dashboard-box','dashboard-manu-item',0)">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <path fill="currentColor"
                          d="M21 16V4H3v12zm0-14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-7v2h2v2H8v-2h2v-2H3a2 2 0 0 1-2-2V4c0-1.11.89-2 2-2zM5 6h9v5H5zm10 0h4v2h-4zm4 3v5h-4V9zM5 12h4v2H5zm5 0h4v2h-4z"/>
                </svg>
                &nbsp;工作台
            </li>
            <li class="dashboard-manu-item" onclick="changeType('dashboard-box','dashboard-manu-item',1)">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <path fill="currentColor" fill-rule="evenodd" d="M6 2a2 2 0 0 0-2 2v15a3 3 0 0 0 3 3h12a1 1 0 1 0 0-2h-2v-2h2a1 1 0 0 0 1-1V4a2 2 0 0 0-2-2h-8v16h5v2H7a1 1 0 1 1 0-2h1V2z" clip-rule="evenodd"/>
                </svg>
                &nbsp;作品管理
            </li>
            <li class="dashboard-manu-item" onclick="changeType('dashboard-box','dashboard-manu-item',2)">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 16 16">
                    <path fill="currentColor" d="M3.5 2.75a.75.75 0 0 0-1.5 0v8.5A2.75 2.75 0 0 0 4.75 14h8.5a.75.75 0 0 0 0-1.5h-8.5c-.69 0-1.25-.56-1.25-1.25zm6.25 2c0 .414.336.75.75.75h.94L9 7.94L7.53 6.47a.75.75 0 0 0-1.06 0l-1.5 1.5a.75.75 0 0 0 1.06 1.06L7 8.06l1.47 1.47a.75.75 0 0 0 1.06 0l2.97-2.97v1.017a.75.75 0 0 0 1.5 0V4.75a.75.75 0 0 0-.75-.75H10.5a.75.75 0 0 0-.75.75"/>
                </svg>
                &nbsp;数据中心
            </li>
            <li class="dashboard-manu-item" onclick="changeType('dashboard-box','dashboard-manu-item',3)">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <g fill="none" stroke="currentColor" stroke-linejoin="round" stroke-width="1.5">
                        <path stroke-linecap="round" d="M7.5 12h6m-6-4h3m-2 12c1.05.87 2.315 1.424 3.764 1.519c1.141.075 2.333.075 3.473 0a4 4 0 0 0 1.188-.268c.41-.167.614-.25.719-.237c.104.012.255.122.557.342c.533.388 1.204.666 2.2.643c.503-.012.755-.019.867-.208c.113-.19-.027-.452-.308-.977c-.39-.728-.636-1.561-.262-2.229c.643-.954 1.19-2.083 1.27-3.303c.043-.655.043-1.334 0-1.99A6.7 6.7 0 0 0 21.4 11"/>
                        <path d="M12.345 17.487c3.556-.234 6.388-3.08 6.62-6.653c.046-.699.046-1.423 0-2.122c-.232-3.572-3.064-6.418-6.62-6.652c-1.213-.08-2.48-.08-3.69 0c-3.556.234-6.388 3.08-6.62 6.652c-.046.7-.046 1.423 0 2.122c.084 1.302.665 2.506 1.349 3.524c.397.712.135 1.6-.279 2.377c-.298.56-.447.84-.327 1.042s.387.209.922.221c1.057.026 1.77-.271 2.336-.685c.321-.234.482-.351.593-.365c.11-.013.328.075.763.253c.392.16.846.258 1.263.286c1.21.08 2.477.08 3.69 0Z"/>
                    </g>
                </svg>
                &nbsp;互动管理
            </li>
            <li class="dashboard-manu-item" onclick="changeType('dashboard-box','dashboard-manu-item',4)">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                    <g fill="none" fill-rule="evenodd">
                        <path d="m12.594 23.258l-.012.002l-.071.035l-.02.004l-.014-.004l-.071-.036q-.016-.004-.024.006l-.004.01l-.017.428l.005.02l.01.013l.104.074l.015.004l.012-.004l.104-.074l.012-.016l.004-.017l-.017-.427q-.004-.016-.016-.018m.264-.113l-.014.002l-.184.093l-.01.01l-.003.011l.018.43l.005.012l.008.008l.201.092q.019.005.029-.008l.004-.014l-.034-.614q-.005-.019-.02-.022m-.715.002a.02.02 0 0 0-.027.006l-.006.014l-.034.614q.001.018.017.024l.015-.002l.201-.093l.01-.008l.003-.011l.018-.43l-.003-.012l-.01-.01z"/>
                        <path fill="currentColor" d="M5 9a7 7 0 0 1 14 0v3.764l1.822 3.644A1.1 1.1 0 0 1 19.838 18h-3.964a4.002 4.002 0 0 1-7.748 0H4.162a1.1 1.1 0 0 1-.984-1.592L5 12.764zm5.268 9a2 2 0 0 0 3.464 0zM12 4a5 5 0 0 0-5 5v3.764a2 2 0 0 1-.211.894L5.619 16h12.763l-1.17-2.342a2 2 0 0 1-.212-.894V9a5 5 0 0 0-5-5"/>
                    </g>
                </svg>
                &nbsp;消息通知
            </li>
        </ul>
    </div>
</div>
<#include "../footer.ftl">
<script src="${staticServePath}/js/common${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/skins/classic/yuhu/js/common.js?${staticResourceVersion}"></script>
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