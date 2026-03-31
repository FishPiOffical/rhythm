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
<!DOCTYPE HTML>
<html>
<head>
    <#if hasSystemTitle>
        <title>${systemTitle}</title>
    <#else>
        <title>进化 - Evolve</title>
    </#if>
    <meta http-equiv="Content-type" content="text/html;charset=utf-8">
    <link rel="icon" href="https://file.fishpi.cn/evolve/evolved-light.ico" type="images/x-icon" media="(prefers-color-scheme:dark)">
    <link rel="icon" href="https://file.fishpi.cn/evolve/evolved.ico" type="images/x-icon" media="(prefers-color-scheme:light)">
    <link rel="icon" href="https://file.fishpi.cn/evolve/evolved.ico" type="images/x-icon" media="(prefers-color-scheme:no-preference)">
    <link href="https://file.fishpi.cn/evolve/lib/googlelato.css" rel="stylesheet">
    <link rel="stylesheet" href="https://file.fishpi.cn/evolve/lib/buefy.min.css">
    <link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/evolve/lib/weather-icons.min.css">

    <script src="https://file.fishpi.cn/evolve/lib/jquery.min.js"></script>
    <script src="https://file.fishpi.cn/evolve/lib/vue.min.js"></script>
    <script src="https://file.fishpi.cn/evolve/lib/buefy.min.js"></script>
    <script src="https://file.fishpi.cn/evolve/lib/popper.min.js"></script>
    <script src="https://file.fishpi.cn/evolve/lib/Sortable.min.js"></script>
    <script src="https://file.fishpi.cn/evolve/lib/lodash.min.js"></script>
    <script src="https://file.fishpi.cn/evolve/lib/PlayFabClientApi.js"></script>
    <script src="https://file.fishpi.cn/evolve/lib/moment.js"></script>
<!--
    <script src="https://code.jquery.com/jquery-3.4.1.min.js" integrity="sha256-CSXorXvZcTkaix6Yvo6HppcZGetbYMGWSFlBw8HfCJo=" crossorigin="anonymous"></script>
    <script src="https://unpkg.com/vue@2.6.11/dist/vue.min.js"></script>
    <script src="https://unpkg.com/buefy@0.9.3/dist/buefy.min.js"></script>
    <script src="https://unpkg.com/popper.js@1.16.1-lts/dist/umd/popper.min.js"></script>
    <script src="https://unpkg.com/sortablejs@1.10.2/Sortable.min.js"></script>
    <script src="https://unpkg.com/lodash@4.17.15/lodash.min.js"></script>
-->
    <link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/evolve/evolve/evolve.css?r=20210605">
    <script src="https://file.fishpi.cn/evolve/lib/lz-string.min.js"></script>
    <script src="https://file.fishpi.cn/main.js" type="module"></script>
</head>
<body>
<!--          <script src="zh/core.js"></script> -->
    <style>
        .loading {
            text-align: center;
            margin-top: 10rem;
        }
        .lds-dual-ring {
            display: inline-block;
            width: 64px;
            height: 64px;
        }
        .lds-dual-ring:after {
            content: " ";
            display: block;
            width: 5rem;
            height: 5rem;
            margin: 1px;
            border-radius: 50%;
            border: 5px solid #fff;
            border-color: #fff transparent #fff transparent;
            animation: lds-dual-ring 1.2s linear infinite;
            }
            @keyframes lds-dual-ring {
            0% {
                transform: rotate(0deg);
            }
            100% {
                transform: rotate(360deg);
            }
        }
    </style>
    <div class="loading"><div class="lds-dual-ring"></div></div>
</body>
</html>
<script src="${staticServePath}/js/lib/reconnecting-websocket.min.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/symbol-defs${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/common${miniPostfix}.js?${staticResourceVersion}"></script>
<script>
    var Label = {
        saveData: '',
        commentEditorPlaceholderLabel: '${commentEditorPlaceholderLabel}',
        langLabel: '${langLabel}',
        luteAvailable: ${luteAvailable?c},
        reportSuccLabel: '${reportSuccLabel}',
        breezemoonLabel: '${breezemoonLabel}',
        confirmRemoveLabel: "${confirmRemoveLabel}",
        reloginLabel: "${reloginLabel}",
        invalidPasswordLabel: "${invalidPasswordLabel}",
        loginNameErrorLabel: "${loginNameErrorLabel}",
        followLabel: "${followLabel}",
        unfollowLabel: "${unfollowLabel}",
        symphonyLabel: "${symphonyLabel}",
        visionLabel: "${visionLabel}",
        cmtLabel: "${cmtLabel}",
        collectLabel: "${collectLabel}",
        uncollectLabel: "${uncollectLabel}",
        desktopNotificationTemplateLabel: "${desktopNotificationTemplateLabel}",
        servePath: "${servePath}",
        staticServePath: "${staticServePath}",
        isLoggedIn: ${isLoggedIn?c},
        funNeedLoginLabel: '${funNeedLoginLabel}',
        notificationCommentedLabel: '${notificationCommentedLabel}',
        notificationReplyLabel: '${notificationReplyLabel}',
        notificationAtLabel: '${notificationAtLabel}',
        notificationFollowingLabel: '${notificationFollowingLabel}',
        pointLabel: '${pointLabel}',
        sameCityLabel: '${sameCityLabel}',
        systemLabel: '${systemLabel}',
        newFollowerLabel: '${newFollowerLabel}',
        makeAsReadLabel: '${makeAsReadLabel}',
        imgMaxSize: ${imgMaxSize?c},
        fileMaxSize: ${fileMaxSize?c},
        <#if isLoggedIn>
        currentUserName: '${currentUser.userName}',
        </#if>
        <#if csrfToken??>
        csrfToken: '${csrfToken}'
        </#if>
    }

    <#if isLoggedIn>
    Label.userKeyboardShortcutsStatus = '${currentUser.userKeyboardShortcutsStatus}'
    </#if>

    <#if isLoggedIn>
    // Init [User] channel
    Util.initUserChannel("${wsScheme}://${serverHost}:${serverPort}${contextPath}/user-channel")
    </#if>
</script>
<script>
    var _hmt = _hmt || [];
    (function() {
        var hm = document.createElement("script");
        hm.src = "https://hm.baidu.com/hm.js?bab35868f6940b3c4bfc020eac6fe61f";
        var s = document.getElementsByTagName("script")[0];
        s.parentNode.insertBefore(hm, s);
    })();
</script>
