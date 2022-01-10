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
<!DOCTYPE html>
<html>
<head>
    <@head title="${chatRoomLabel} - ${symphonyLabel}">
        <meta name="description" content="${chatRoomLabel}"/>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"/>
    <link rel="canonical" href="${servePath}/community">
    <link rel="stylesheet" href="${staticServePath}/css/viewer.min.css"/>
</head>
<body>
<#include "header.ftl">
<div class="main">
    <div class="wrapper">
        <div class="content chat-room">
            <div class="module">
                <div style="margin-bottom: 15px;">
                    <svg style="width: 12px; height: 12px;"><use xlink:href="#downloadIcon"></use></svg> 下载
                    <a style="line-height: 30px;text-decoration: none;color: #1296db;" href="https://fishpi.cn/article/1637143985245" target="_blank">PC客户端</a><span style="color: #577e8d">&nbsp;&nbsp;|&nbsp;</span>
                    <a style="line-height: 30px;text-decoration: none;color: #1296db;" href="https://fishpi.cn/article/1638189205758" target="_blank">IDEA插件</a><span style="color: #577e8d">&nbsp;&nbsp;|&nbsp;</span>
                    <a style="line-height: 30px;text-decoration: none;color: #1296db;" href="https://zhuanlan.zhihu.com/p/445372463" target="_blank">VSCode插件</a><span style="color: #577e8d">&nbsp;&nbsp;|&nbsp;</span>
                    <a style="line-height: 30px;text-decoration: none;color: #1296db;" href="https://fishpi.cn/article/1639648815789" target="_blank">Chrome插件</a><span style="color: #577e8d">&nbsp;&nbsp;|&nbsp;</span>
                    <a style="line-height: 30px;text-decoration: none;color: #1296db;" href="https://fishpi.cn/article/1641291342622" target="_blank">安卓APK</a>&nbsp;&nbsp;|&nbsp;</span>
                    <a style="line-height: 30px;text-decoration: none;color: #1296db;" href="https://fishpi.cn/article/1641135630423" target="_blank">Python客户端</a>
                </div>
                <#if hasSystemTitle>
                <h2>${systemTitle}</h2>
                <#else>
                <h2>${chatRoomLabel}</h2>
                </#if>
                <div class="fn-content">
                    <div class="reply">
                        <#if isLoggedIn>
                            <div id="chatContent"></div><br>
                            <div class="fn-clear" style="margin-bottom: 5px">
                                <svg id="redPacketBtn" style="width: 30px; height: 30px; cursor:pointer;">
                                    <use xlink:href="#redPacketIcon"></use>
                                </svg>
                                <svg id="emojiBtn" style="width: 30px; height: 30px; cursor:pointer;">
                                    <use xlink:href="#emojiIcon"></use>
                                </svg>
                                <div class="hide-list" id="emojiList">
                                    <div class="hide-list-emojis" id="emojis" style="max-height: 200px">
                                    </div>
                                    <div class="hide-list-emojis__tail">
                                        <span>
                                        <a onclick="ChatRoom.fromURL()">从URL导入表情包</a>
                                        </span>
                                        <span class="hide-list-emojis__tip"></span>
                                        <span>
                                            <a onclick="$('#uploadEmoji input').click()">上传表情包</a>
                                        </span>
                                        <form style="display: none" id="uploadEmoji" method="POST" enctype="multipart/form-data">
                                            <input type="file" name="file">
                                        </form>
                                    </div>
                                </div>
                                <div class="fn-right">
                                    <#if level3Permitted == true>
                                        <button id="groupRevoke" onclick="ChatRoom.startGroupRevoke()" class="button">
                                            <svg><use xlink:href="#userrole"></use></svg>
                                            批量撤回
                                        </button>
                                    </#if>
                                    <button class="red" onclick="$('#chats').empty();page=0;ChatRoom.more();">${cleanScreenLabel}</button>
                                    <button class="green" onclick="ChatRoom.send()">${postLabel}</button>
                                </div>
                            </div>
                            <div class="fn-clear comment-submit">
                                <div class="fn-left online-cnt">${onlineVisitorCountLabel} <span id="onlineCnt"></span>
                                </div>
                                <div class="tip fn-left" id="chatContentTip"></div>
                            </div>
                            <div id="chatRoomOnlineCnt" class="chats__users">
                            </div>
                        <#else>
                            <div class="comment-login">
                                <a rel="nofollow"
                                   href="javascript:window.scrollTo(0,0);Util.goLogin();">${loginDiscussLabel}</a>
                            </div>
                        </#if>
                    </div>
                </div>
            </div>
            <div class="list module pd__15" id="comments" style="height: 100%">
                <div id="chats">
                </div>
                <#if !isLoggedIn><div style="color:rgba(0,0,0,0.54);">登录后查看更多</div></#if>
            </div>
        </div>
        <div class="side">
            <#include "side.ftl">
        </div>
    </div>
</div>
<div id="goToTop" style="position:fixed;bottom:20px;right:10%;display:none;"><a href="#"><svg style="width:30px;height:30px;color:#626262;"><use xlink:href="#toTopIcon"></use></svg></a></div>
<#include "footer.ftl">
<script>
    Label.uploadLabel = "${uploadLabel}";
</script>
<script src="${staticServePath}/js/lib/jquery/file-upload-9.10.1/jquery.fileupload.min.js"></script>
<script src="${staticServePath}/js/channel${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/chat-room${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/lib/viewer.min.js"></script>
<script>
    Label.addBoldLabel = '${addBoldLabel}';
    Label.addItalicLabel = '${addItalicLabel}';
    Label.insertQuoteLabel = '${insertQuoteLabel}';
    Label.addBulletedLabel = '${addBulletedLabel}';
    Label.addNumberedListLabel = '${addNumberedListLabel}';
    Label.addLinkLabel = '${addLinkLabel}';
    Label.undoLabel = '${undoLabel}';
    Label.redoLabel = '${redoLabel}';
    Label.previewLabel = '${previewLabel}';
    Label.helpLabel = '${helpLabel}';
    Label.fullscreenLabel = '${fullscreenLabel}';
    Label.uploadFileLabel = '${uploadFileLabel}';
    Label.insertEmojiLabel = '${insertEmojiLabel}';
    Label.currentUser = '<#if currentUser??>${currentUser.userName}</#if>';
    Label.level3Permitted = ${level3Permitted?string("true", "false")};
    Label.chatRoomPictureStatus = "<#if 0 == chatRoomPictureStatus> blur</#if>";
    Label.latestMessage = "";
    Label.plusN = 0;
    Label.hasMore = true;
    ChatRoom.init();
    // Init [ChatRoom] channel
    ChatRoomChannel.init("${wsScheme}://${serverHost}:${serverPort}${contextPath}/chat-room-channel");
    var page = 0;
    ChatRoom.more();
</script>
<script>
    $(window).scroll(
        function() {
            var scrollTop = $(this).scrollTop();
            var scrollHeight = $(document).height();
            var windowHeight = $(this).height();
            if (scrollTop + windowHeight + 500 >= scrollHeight) {
                ChatRoom.more();
            }
        }
    );
</script>
<script type="text/javascript">
    $(document).ready(function(){
        $(function(){
            $(window).scroll(function(){
                if($(this).scrollTop()>1){
                    $("#goToTop").fadeIn();
                } else {
                    $("#goToTop").fadeOut();
                }
            });
        });
        $("#goToTop a").click(function(){
            $("html,body").animate({scrollTop:0},800);
            return false;
        });
    });
</script>
</body>
</html>
