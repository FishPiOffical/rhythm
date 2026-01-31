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
<#include "macro-head.ftl">
<#include "macro-pagination.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="私信 - ${symphonyLabel}">
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/base.css?${staticResourceVersion}"/>
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"/>
    <style>
        .ft__center {
            text-align: center;
        }

        .ft__gray {
            color: var(--text-gray-color);
        }

        .form {
            border: 1px solid rgba(0,0,0,.38);
            background-color: #fafafa;
            border-radius: 3px;
            box-shadow: 0 1px 2px rgb(0 0 0 / 8%) inset;
            padding: 7px 8px;
            width: 130px;
            line-height: 17px;
            box-sizing: border-box;
            -moz-box-sizing: border-box;
            -webkit-box-sizing: border-box;
        }

        .form:focus {
            background-color: #fff;
            box-shadow: 0 1px 2px rgb(0 0 0 / 8%) inset, 0 0 5px rgb(81 167 232 / 50%);
            border: 1px solid #51a7e8;
        }
    </style>
</head>
<body>
<#include "header.ftl">
<div class="main">
    <div class="wrapper">
        <div class="side" style="margin-bottom: 25px">
            <div class="module person-info" style="padding: 0">
                <div class="module-panel" id="chatToFileTransfer" style="padding: 10px 15px;cursor: pointer"
                     onclick="Chat.init('FileTransfer')">
                    <nav class="home-menu">
                        <div class="avatar"
                             style="display: inline-block; background-image:url('https://file.fishpi.cn/2022/06/e1541bfe4138c144285f11ea858b6bf6-ba777366.jpeg')">
                        </div>
                        <div style="display: inline-block; vertical-align: -12px;">
                            文件传输助手<br>
                            <span id="fileTransferMsg" style="color: #868888">跨端传输文本/文件</span>
                        </div>
                    </nav>
                </div>
            </div>
            <span id="messageListPanel" style="display: none">
            <div>
                <details class="details action__item fn__flex-center" style="margin-left: 93%">
                    <summary>
                        ···
                    </summary>
                    <details-menu class="fn__layer">
                        <a onclick="Chat.markAllAsRead()" class="item"><svg><use xlink:href="#check"></use></svg> 全部标为已读</a>
                    </details-menu>
                </details>
            </div>
            <div class="module person-info" id="chatMessageList"  style="padding: 0; height: 700px; overflow-y: auto;">
            </div>
            </span>
        </div>
        <div class="content chat-room" style="margin-left: 15px; padding: 20px 10px;background-color: #fff">
            <div class="ft__gray ft__center" id="chatStatus" style="padding-top: 10px">
            </div>
            <br>
            <div id="messageContent"></div>
            <br>
            <div class="tip fn-left" id="chatContentTip"></div>
            <div class="fn-clear" id="buttons" style="display: none">
                <svg id="emojiBtn" style="width: 30px; height: 30px; cursor:pointer;">
                    <use xlink:href="#emojiIcon"></use>
                </svg>
                <#-- z-index 130 因为猜拳红包是128 会覆盖表情包 所以这里改成130 oh-yeh！ -->
                <div class="hide-list" id="emojiList" style="z-index: 130;  width: 440px;max-width: 440px;">
                    <div style="display: flex;width: 440px">
                        <div id="emojiGroupBoxNew" style="width: 100px; border-right: 1px solid #eee; overflow-y: auto;">
                        </div>
                        <div class="hide-list-emojis" id="emojisNew" style="max-height: 250px; flex: 1; padding: 10px;">
                        </div>
                    </div>

<#--                    <div class="hide-list-emojis__tail">-->
<#--                        <span>-->
<#--                            <a onclick="Chat.fromURL()">从URL导入表情包</a>-->
<#--                        </span>-->
<#--                        <span class="hide-list-emojis__tip"></span>-->
<#--                        <span>-->
<#--                            <a onclick="$('#uploadEmoji input').click()">上传表情包</a>-->
<#--                        </span>-->
<#--                        <form style="display: none" id="uploadEmoji" method="POST" enctype="multipart/form-data">-->
<#--                            <input type="file" name="file">-->
<#--                        </form>-->
<#--                    </div>-->
                </div>
                <div class="fn-right">
                    <button class="green" id="sendChatBtn">发送</button>
                </div>
            </div>
            <br>
            <div class="module" style="min-height: 300px; margin-top: 20px;box-shadow: none">
                <div id="chats">
                </div>
            </div>
        </div>
    </div>
    <div id="goToTop" style="position:fixed;bottom:20px;right:10%;display:none;"><a href="#"><svg style="width:30px;height:30px;color:#626262;"><use xlink:href="#toTopIcon"></use></svg></a></div>
</div>
<#include "footer.ftl">
</body>
</html>
<script src="${staticServePath}/js/emoji-groups.js${miniPostfix}?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/chat${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/channel${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
<script>
    var Label = {
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
    var apiKey = '${apiKey}';
    var chatChannelURL = "${wsScheme}://${serverHost}:${serverPort}${contextPath}/chat-channel?apiKey=" + apiKey + "&toUser=";
</script>
