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
    <link rel="stylesheet" href="${staticServePath}/js/lib/barrager/barrager.css">
    <style>
        [id^="weather_"] svg{
            stroke-width:0;
        }
    </style>
</head>
<body>
<#include "header.ftl">
<div class="main" style="margin: 10px 0 0 0; padding: 0">
    <div class="wrapper">
        <div class="content chat-room chat-room--qq-layout">
            <#-- 顶部头部栏：与 module 同级，左标题 + 右按钮 -->
            <div class="chat-room__header">
                <div class="chat-room__header-title">
                    <button class="button chat-room__top-btn" id="nodeButton" onclick="ChatRoom.switchNode()">
                        <svg style='vertical-align: -2px;'><use xlink:href="#server"></use></svg> 选择大区
                    </button>
                </div>
                <#if isLoggedIn>
                    <div class="chat-room__header-actions">
                        <#if level3Permitted == true>
                            <button id="groupRevoke" onclick="ChatRoom.startGroupRevoke()" class="button chat-room__top-btn">
                                批量撤回
                            </button>
                        </#if>
                        <button class="button chat-room__top-btn" onclick="switchTheme()">切换样式：经典</button>
                        <button class="button chat-room__top-btn" onclick="ChatRoom.toggleSmoothMode()">流畅模式: <span id="smoothMode">关闭</span></button>
                        <button class="button chat-room__top-btn" onclick="ChatRoom.showSiGuoYar()">思过崖</button>
                        <button class="button chat-room__top-btn" onclick="ChatRoom.flashScreen()">返回底部并清屏</button>
                        <script>
                            function switchTheme() {
                                document.cookie = "theme=classic; path=/; max-age=" + 60 * 60 * 24 * 365;
                                location.href = '/cr';
                            }
                        </script>
                    </div>
                </#if>
            </div>

            <div class="module" style="margin-bottom: 0">
                <div class="fn-content" style="padding-top: 0;">
                    <div class="chat-room__layout">
                        <div class="chat-room__main">
                            <#-- 消息列表区域 -->
                            <div class="chat-room__messages">
                                <div class="list" id="comments" style="height: auto; padding: 20px 30px 5px 30px">
                                    <div id="chats">
                                    </div>
                                    <#if !isLoggedIn>
                                        <div style="color:rgba(0,0,0,0.54);">登录后查看更多</div>
                                    </#if>
                                </div>
                            </div>

                            <#-- 输入区域：在消息列表下方 -->
                            <div class="chat-room__input">
                                <div class="reply">
                                    <#if isLoggedIn>
                                        <div id="chatContent"> </div>
                                        <div class="fn-clear chat-room__toolbar" style="padding: 16px 0 8px 0;">
                                            <svg id="redPacketBtn" style="width: 30px; height: 30px; cursor:pointer;">
                                                <use xlink:href="#redPacketIcon"></use>
                                            </svg>
                                            <svg id="emojiBtn" style="width: 30px; height: 30px; cursor:pointer;">
                                                <use xlink:href="#emojiIcon"></use>
                                            </svg>
                                            <svg id="barragerBtn" style="width: 30px; height: 30px; cursor:pointer;">
                                                <use xlink:href="#danmu"></use>
                                            </svg>
                                <#-- z-index 130 因为猜拳红包是128 会覆盖表情包 所以这里改成130 oh-yeh！ -->
                                <div class="hide-list" id="emojiList" style="z-index: 130">
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
                                <#if nightDisableMode == true>
                                    <br>
                                    <div class="discuss_title" style="border-radius: 10px; padding: 10px 0 0 0">
                                        <a style="text-decoration: none; display: inline-block; cursor: default; font-weight: normal; background-color: #f6f6f670;">
                                            <span style="color: #616161">💤 现在是聊天室宵禁时间 (19:30-08:00)，您发送的消息将不会产生活跃度，请早点下班休息 :)</span>
                                        </a>
                                    </div>
                                </#if>
                                <div class="fn-right chat-room__actions">
                                    <button class="green" onclick="ChatRoom.send()">发送</button>
                                </div>
                                <div id="paintContent" style="display: none;">
                                    <div style="margin: 20px 0 0 0;display: flex">
                                        <div id="selectColor" style="margin:0 10px;border:1px solid #000"></div>
                                        <input id="selectWidth" type="number" inputmode="decimal" pattern="[0-9]*" min="1" value="3" style="width: 50px">
                                    </div>
                                    <canvas id="paintCanvas" width="500" height="490"></canvas>
                                    <div class="fn-right">
                                        <button onclick="ChatRoom.revokeChatacter('paintCanvas')">撤销</button>
                                        <button class="red" onclick="ChatRoom.clearCharacter('paintCanvas')">${clearLabel}</button>
                                        <button class="green" onclick="ChatRoom.submitCharacter('paintCanvas')">${submitLabel}</button>
                                    </div>
                                </div>
                                <div id="barragerContent" style="display:none;
                                                                 background-color: var(--layer-background-color);
                                                                 padding: 8px 34px 22px 34px;
                                                                 box-shadow: 0px 0px 4px 0px rgba(0,0,0,.2);
                                                                 margin: 19px 10px 10px 10px;
                                                                 border-radius: 49px;
                                                                ">
                                    <div style="margin: 20px 0 0 0;">
                                        <div>
                                            <div class="module-panel">
                                                <div class="module-header form" style="border: none;">
                                                    <input id="barragerInput" type="text" class="comment__text breezemoon__input" placeholder="友善弹幕，最多32个字哦">
                                                    <span id="barragerPostBtn" onclick="ChatRoom.sendBarrager();" class="btn breezemoon__btn">发射!</span>
                                                </div>
                                            </div>
                                        </div>

                                        <div class="ft__smaller ft__fade" style="margin-top: 10px; margin-bottom: 10px;">发送弹幕每次将花费 <b><span id="barragerCost">${barragerCost}</span></b> <span id="barragerUnit">${barragerUnit}</span></div>
                                    </div>
                                </div>
                            </div>
                            <div class="fn-clear comment-submit chat-room__status">
                                <div class="fn-left chat-room__status-left">
                                    <div class="tip" id="chatContentTip"></div>
                                </div>
                            </div>
                        <#else>
                            <div class="comment-login">
                                <a rel="nofollow"
                                   href="javascript:window.scrollTo(0,0);Util.goLogin();">${loginDiscussLabel}</a>
                            </div>
                        </#if>
                    </div> <!-- .reply -->
                </div> <!-- .chat-room__input -->
                        </div> <!-- .chat-room__main -->

                        <#-- 右侧：话题 + 在线用户 -->
                        <div class="chat-room__side">
                            <#if isLoggedIn>
                                <div class="chat-room__topic">
                                    <div class="chat-room__topic-header">
                                        <span class="chat-room__topic-label">当前话题</span>
                                    </div>
                                    <div class="chat-room__topic-body">
                                        <span class="chat-room__topic-text"># <span id="discuss-title">加载中...</span> #</span>
                                        <div class="chat-room__topic-actions">
                                            <a onclick="ChatRoom.setDiscuss()" class="ft-a-title tooltipped tooltipped-se" aria-label="编辑话题" style="text-decoration: none;">
                                                <svg><use xlink:href="#edit-discuss"></use></svg>
                                            </a>
                                            <a onclick="ChatRoom.useDiscuss()" class="ft-a-title tooltipped tooltipped-se" aria-label="引用话题" style="text-decoration: none;">
                                                <svg><use xlink:href="#pound"></use></svg>
                                            </a>
                                        </div>
                                    </div>
                                </div>

                                <div class="chat-room__online">
                                    <div class="chat-room__online-header">
                                        <span>${onlineVisitorCountLabel}</span>
                                        <span class="chat-room__online-count" id="onlineCnt"></span>
                                        <a onclick="ChatRoom.toggleOnlineAvatar()" class="chat-room__online-toggle" style="cursor:pointer;">
                                            <svg id="toggleAvatarBtn"><use xlink:href="#showMore"></use></svg>
                                        </a>
                                    </div>
                                    <div id="chatRoomOnlineCnt" class="chats__users chat-room__online-list" style="display: none">
                                    </div>
                                </div>
                            </#if>
                        </div> <!-- .chat-room__side -->
                    </div> <!-- .chat-room__layout -->
                </div>
            </div>
        </div>
    </div>
</div>
<div id="goToTop" style="position:fixed;bottom:20px;right:10%;display:none;"><a href="#"><svg style="width:30px;height:30px;color:#626262;"><use xlink:href="#toTopIcon"></use></svg></a></div>
<div id="musicBox">
    <div class="music-box">
        <div class="music-controller">
            <div class="music-prev" onclick="ChatRoom.playSound.prev()">
                <img src="${staticServePath}/images/music/circle_skip_previous.png" alt="">
            </div>
            <#--            <div class="music-play" onclick="ChatRoom.playSound.togglePlay()">-->
            <#--                <img class="music-play-icon" src="${staticServePath}/images/music/circle_play.png" alt="">-->
            <#--            </div>-->
            <div class="music-next" onclick="ChatRoom.playSound.next()">
                <img src="${staticServePath}/images/music/circle_skip_next.png" alt="">
            </div>
        </div>
        <#--        <div class="music-img">-->
        <#--            <img src="${staticServePath}/images/music/cat.gif" class="music-img-item" alt="" />-->
        <#--        </div>-->
        <div class="music-detail">
            <#--            <iframe frameborder="no" border="0" marginwidth="0" marginheight="0" width="100%" height="52" src="//music.163.com/outchain/player?type=2&id=2635209343&auto=0&height=32"></iframe>-->

            <div class="music-title">摸鱼播放器v1.0</div>
            <div class="music-time"><span class="music-current">00:00</span>-<span class="music-duration">00:00</span></div>
        </div>
        <div class="music-controller">
            <#--            <div class="music-voice" style="padding: 2px;box-sizing: border-box">-->
            <#--                <img class="music-voice-icon" src="${staticServePath}/images/music/volume_3.png" alt="">-->
            <#--                <div class="music-voice-box">-->
            <#--                    <input type="range" value="100" max="100" min="0" onchange="ChatRoom.playSound.changeVoice(this)">-->
            <#--                </div>-->
            <#--            </div>-->
            <div class="music-mode" style="padding: 5px;box-sizing: border-box" onclick="ChatRoom.playSound.toggleMode()">
                <img class="music-mode-icon" src="${staticServePath}/images/music/repeat.png" alt="">
            </div>
            <div class="music-list" style="padding: 5px;box-sizing: border-box" onclick="ChatRoom.playSound.toggleList()">
                <img src="${staticServePath}/images/music/list.png" alt="">
            </div>
        </div>
        <div class="music-close-btn" onclick="ChatRoom.playSound.toggleShow()">
            <img class="music-close-icon" src="${staticServePath}/images/music/arrow_up.png" alt="" />
        </div>
    </div>
    <div class="music-core">
        <audio id="music-core-item" src=""></audio>
    </div>
</div>
<div class="music-list-box"></div>
<div id="robotBtn" class="robot-btn" style="">
    <img src="https://file.fishpi.cn/2024/05/chatbot-7857bb0c.gif" class="ice-game-icon" style="border-radius: 50%;" alt="">
</div>
<div id="robotBox" style="display: none;" class="">
    <div class="robot-tool-bar">
        <img src="https://www.iconninja.com/files/400/1008/1019/align-left-icon.png" class="robot-logo" alt="">
        用户消息捕获
        <div class="robot-toolbar-btn">
            <svg id="robotClose" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="32" height="32">
                <title>关闭</title>
                <path d="M576 512l277.333333 277.333333-64 64-277.333333-277.333333L234.666667 853.333333 170.666667 789.333333l277.333333-277.333333L170.666667 234.666667 234.666667 170.666667l277.333333 277.333333L789.333333 170.666667 853.333333 234.666667 576 512z" fill="#ffffff"></path>' +
            </svg>
        </div>
    </div>
    <div class="robot-chat-box" style="position: relative; height: 30px">
        <input id="catch-word" class="robot-catch-input" type="checkbox">捕获
        <div id="changeCatchKeys" class="robot-keys-btn">修改口令</div>
        <div id="changeCatchUsers" class="robot-change-btn">修改用户</div>
        <div id="clearRobotMsg" class="robot-clear-btn">清屏</div>
    </div>
    <div id="robotMsgList"></div>
</div>
<script src="${staticServePath}/js/symbol-defs${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/lib/compress/libs.min.js?${staticResourceVersion}"></script>
<script src="https://file.fishpi.cn/vditor/3.11.1/dist/index.min.js"></script>
<script src="${staticServePath}/js/common${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/count${miniPostfix}.js?${staticResourceVersion}"></script>
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
        csrfToken: '${csrfToken}',
        </#if>
        staticResourceVersion: '${staticResourceVersion}',
    }

    <#if isLoggedIn>
    Label.userKeyboardShortcutsStatus = '${currentUser.userKeyboardShortcutsStatus}'
    </#if>

    Util.init(${isLoggedIn?c})

    <#if isLoggedIn>
    // Init [User] channel
    Util.initUserChannel("${wsScheme}://${serverHost}:${serverPort}${contextPath}/user-channel")
    </#if>

    <#if mouseEffects>
    Util.mouseClickEffects()
    </#if>
</script>
<#if algoliaEnabled>
    <script src="${staticServePath}/js/lib/algolia/algolia.min.js"></script>
    <script>
        Util.initSearch('${algoliaAppId}', '${algoliaSearchKey}', '${algoliaIndex}')
    </script>
</#if>
<script src="${staticServePath}/js/lib/tooltips/tooltips.min.js?${staticResourceVersion}"></script>
<script>
    var _hmt = _hmt || [];
    (function() {
        var hm = document.createElement("script");
        hm.src = "https://hm.baidu.com/hm.js?bab35868f6940b3c4bfc020eac6fe61f";
        var s = document.getElementsByTagName("script")[0];
        s.parentNode.insertBefore(hm, s);
    })();
</script>
<script>
    function iframeCheck () {
        try {
            return window.self !== window.top;
        } catch (e) {
            return true;
        }
    }
    $(function () {
        if (iframeCheck()) {
            location.href = "/privacy"
        }
    });
</script>
<script>
    Label.uploadLabel = "${uploadLabel}";
    Label.vipUsers = ${vipUsers};
    Label.membership = ${membership};
</script>
<script src="${staticServePath}/js/lib/echarts.min.js"></script>
<script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
<script src="${staticServePath}/js/channel-2${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/chat-room-2${miniPostfix}.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/lib/viewer.min.js?${staticResourceVersion}"></script>
<script src="${staticServePath}/js/lib/barrager/jquery.barrager.min.js"></script>
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
    Label.currentUserId = '<#if currentUser??>${currentUser.oId}</#if>';
    Label.level3Permitted = ${level3Permitted?string("true", "false")};
    Label.chatRoomPictureStatus = "<#if 0 == chatRoomPictureStatus> blur</#if>";
    Label.latestMessage = "";
    Label.plusN = 0;
    Label.hasMore = true;
    Label.node;
    // 首屏是否已经自动滚动到底部
    Label.initialScrolled = false;
    ChatRoom.init();
    // Init [ChatRoom] channel
    $.ajax({
        url: Label.servePath + '/chat-room/node/get',
        type: 'GET',
        cache: false,
        success: function (result) {
            $('#nodeButton').html(`<svg style='vertical-align: -2px;'><use xlink:href="#server"></use></svg> ` + result.msg);
            Label.node = result;
            ChatRoomChannel.init(result.data);
        }
    });
    var page = 0;
    var pointsArray = [];
    var linesArray = [];
    if ('${contextMode}' === 'no') {
        ChatRoom.more();
        // 兜底：再过 500ms 再尝试滚一次（如果之前没滚成功）
        setTimeout(function () {
            if (!Label.initialScrolled && typeof ChatRoom.scrollToBottom === 'function') {
                ChatRoom.scrollToBottom(true);
                Label.initialScrolled = true;
            }
        }, 500);
    } else {
        page = 1;
        let contextOId = '${contextOId}';
        $.ajax({
            url: Label.servePath + '/chat-room/getMessage?size=25&mode=0&oId=' + contextOId,
            type: 'GET',
            cache: false,
            async: false,
            success: function (result) {
                if (result.data.length !== 0) {
                    for (let i in result.data) {
                        let data = result.data[i];
                        if ($("#chatroom" + data.oId).length === 0) {
                            ChatRoom.renderMsg(data, 'more');
                        }
                        ChatRoom.resetMoreBtnListen();
                    }
                    Util.listenUserCard();
                    ChatRoom.imageViewer();
                    let html = "<div class='redPacketNotice' style='color: rgb(50 50 50);margin-bottom: 12px;text-align: center;display: none;'>您当前处于指定消息预览模式，将显示指定消息的前后25条消息，如需查看最新消息请 <a onclick='location.href = \"/cr\"' style='cursor:pointer;'>点击这里</a></div>";
                    $('#chats').prepend(html);
                    $(".redPacketNotice").slideDown(500);
                    location.hash = '#chatroom' + contextOId;
                } else {
                    alert("没有更多聊天消息了！");
                    Label.hasMore = false;
                }
            }
        });
    }
    Label.onlineAvatarData = "";
</script>
<script>
    // 判断消息区是否接近底部（用于决定是否自动跟随新消息滚动）
    ChatRoom.isAtBottom = function (threshold) {
        const $c = $('#comments');
        const scrollTop = $c.scrollTop();
        const scrollHeight = $c[0].scrollHeight;
        const clientHeight = $c[0].clientHeight;
        const gap = scrollHeight - clientHeight - scrollTop;
        return gap <= (threshold || 50); // 默认阈值 50px
    };

    // 滚动到消息区底部（首屏使用瞬间滚动，避免闪烁）
    ChatRoom.scrollToBottom = function (instant) {
        const $c = $('#comments');
        if (!$c.length) return;
        const target = $c[0].scrollHeight;
        if (instant) {
            $c.scrollTop(target);
        } else {
            $c.stop().animate({scrollTop: target}, 300);
        }
    };
</script>
<script>
    // 在消息区滚动到顶部时加载更多历史消息
    $(function () {
        const $c = $('#comments');
        let isLoadingMore = false;

        $c.on('scroll', function () {
            if (isLoadingMore) return;
            if ($c.scrollTop() <= 0 && Label.hasMore) {
                isLoadingMore = true;
                const oldHeight = $c[0].scrollHeight;
                const oldScrollTop = $c.scrollTop();

                ChatRoom.more();

                // 等更多历史加载并渲染完成后，再根据高度差调整 scrollTop，避免“跳动到最上面”
                setTimeout(function () {
                    const newHeight = $c[0].scrollHeight;
                    const delta = newHeight - oldHeight;
                    $c.scrollTop(oldScrollTop + delta);
                    isLoadingMore = false;
                }, 50); // 50ms 够让一次 AJAX+DOM 渲染完成，必要时可以略微调大
            }
        });
    });
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
<style>
    .vditor-reset p, .vditor-reset pre {
        margin: 0!important;
    }
    #emojiList {
        bottom: unset!important;
    }
</style>
</body>
</html>
