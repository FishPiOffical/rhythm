/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * @fileoverview 聊天室
 *
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 1.3.0.2, Apr 30, 2020
 */

/**
 * @description Add comment function.
 * @static
 */
var el;
var ctx;
var isDrawing = false;
var x = 0;
var y = 0;
var isClick = true;
var thisClient = 'Web/PC网页端';
// 弹幕颜色选择器
var BarragerColorPicker = null;
var DarwColorPicker = null;
var redPacketMap = new Map();
var catchUserParam = window.localStorage['robot_list'] ? window.localStorage['robot_list'] : '';
var catchUsers = catchUserParam.length > 0 ? catchUserParam.split(',') : [];
var catchWordFlag;
if (window.localStorage['catch-word-flag']) {
    catchWordFlag = window.localStorage['catch-word-flag'] == true || window.localStorage['catch-word-flag'] == 'true' ? true : false;
} else {
    window.localStorage['catch-word-flag'] = true;
    catchWordFlag = true;
}
$('#catch-word').prop('checked', catchWordFlag);
var ChatRoom = {
    init: function () {
        if (window.localStorage['robot_list'] == undefined) {
            ChatRoom.changeCatchUser('xiaoIce,b,sevenSummer');
        }
        // 聊天窗口高度设置
        /* if ($.ua.device.type !== 'mobile') {
          $('.list').
            height($('.side').height() -
              $('.chat-room .module:first').outerHeight() - 20)
        } else {
          $('.list').height($(window).height() - 173)
        } */

        if (ChatRoom.isMobile()) {
            thisClient = 'Mobile/移动网页端';
        }

        // 没有登录就不需要编辑器初始化了
        if ($('#chatContent').length === 0) {
            return false
        }
        ChatRoom.editor = Util.newVditor({
            id: 'chatContent',
            cache: true,
            preview: {
                mode: 'editor',
            },
            resize: {
                enable: true,
                position: 'bottom',
            },
            toolbar: [
                'emoji',
                'headings',
                'bold',
                'italic',
                '|',
                'link',
                'upload',
                '|',
                'undo',
                'redo',
                '|',
                'edit-mode',
                'fullscreen',
                {
                    name: 'more',
                    toolbar: [
                        'table',
                        'list',
                        'ordered-list',
                        'check',
                        'outdent',
                        'indent',
                        'quote',
                        'code',
                        'insert-before',
                        'insert-after',
                        // 'fullscreen',
                        // 'both',
                        // 'preview',
                        // 'outline',
                        // 'content-theme',
                        // 'code-theme',
                        // 'devtools',
                        'info',
                        'help',
                    ],
                }],
            height: 150,
            placeholder: '说点什么吧！',
            ctrlEnter: function () {
                ChatRoom.send()
            },
        })

        // img preview
        // $('.chat-room').on('click', '.vditor-reset img', function () {
        //   if ($(this).hasClass('emoji')) {
        //     return;
        //   }
        //   window.open($(this).attr('src'));
        // });

        // 加载备注
        let userRemarkList = localStorage.getItem('user_remark');
        if (userRemarkList) {
            ChatRoom.remarkList = JSON.parse(userRemarkList);
        }
        // 表情包初始化
        // 加载表情
        ChatRoom.listenUploadEmojis();
        ChatRoom.loadEmojis();
        // 监听按钮

        (() => {
            let time_out = new Date().getTime(), timeoutId = 0
            const closeEmoji = function () {
                if (timeoutId !== 0) {
                    clearTimeout(timeoutId)
                    timeoutId = 0
                }
                time_out = new Date().getTime()
                timeoutId = setTimeout(() => {
                    new Date().getTime() - time_out <= 700 && $("#emojiList").removeClass("showList")
                }, navigator.userAgent.match(/(phone|pad|pod|ios|Android|Mobile|BlackBerry|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian)/i) !== null ? 0 : 600)
            }
            $("#emojiBtn").hover(function (e) {
                $('#emojiList').css('top', '290px')
                if (timeoutId !== 0) {
                    clearTimeout(timeoutId)
                    timeoutId = 0
                }
                time_out = new Date().getTime()
                setTimeout(() => 0 !== $("#emojiBtn:hover").length && $("#emojiList").addClass("showList"), 300)
            }, closeEmoji)
            $("#emojiList").hover(function () {
                if (timeoutId !== 0) {
                    clearTimeout(timeoutId)
                    timeoutId = 0
                }
                time_out = new Date().getTime()
            }, closeEmoji)
        })()

        // 红包初始化
        $("#redPacketBtn").on('click', function () {
            Util.alert("" +
                "<div class=\"form fn__flex-column\">\n" +
                "<label>\n" +
                "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">红包类型</div>\n" +
                "  <div class=\"fn-hr5 fn__5\"></div>\n" +
                "  <select id=\"redPacketType\">\n" +
                "  <option value=\"random\" selected>拼手气红包</option>" +
                "  <option value=\"average\">普通红包</option>" +
                "  <option value=\"specify\">专属红包</option>" +
                "  <option value=\"heartbeat\">心跳红包</option>" +
                "  <option value=\"rockPaperScissors\">猜拳红包</option>" +
                "  </select>\n" +
                "</label>\n" +
                "<label id=\"gesture\" style=\"display:none;\">\n" +
                "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">出拳</div>\n" +
                "  <div class=\"fn-hr5 fn__5\"></div>\n" +
                "  <select id=\"gestureType\">\n" +
                "  <option value=\"0\" selected>石头</option>" +
                "  <option value=\"1\">剪刀</option>" +
                "  <option value=\"2\">布</option>" +
                "  </select>\n" +
                "</label>\n" +
                "<label id = \"who\" style=\"display:none;\">\n" +
                "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">发给谁</div>\n" +
                "  <div class=\"fn-hr5 fn__5\"></div>\n" +
                "  <div id=\"recivers\" class=\"chats__users\">\n" +
                "                            </div> \n" +
                "  <input id=\"userInput\" type='text' autocomplete='off'> \n" +
                "  <div class='selected-username-box'><div id=\"chatUsernameSelectedPanel\" class=\"selected-username-panel\"\n" +
                "                             style=\"height:170px;\"></div><div class='arrow_up'></div></div> \n" +
                "</label>\n" +
                "<label id='redPacketMoneyLabel'>\n" +
                "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">积分</div>\n" +
                "  <div class=\"fn-hr5 fn__5\"></div>\n" +
                "  <input type=\"number\" min=\"32\" max=\"20000\" required=\"\" value=\"32\" id=\"redPacketMoney\" onkeypress=\"return(/[\\d]/.test(String.fromCharCode(event.keyCode)))\">\n" +
                "</label>\n" +
                "<label id='redPacketCountLabel'>\n" +
                "  <div class=\"ft__smaller ft__fade\" style=\"float: left\" id=\"countx\">个数</div>\n" +
                "  <div class=\"fn-hr5 fn__5\"></div>\n" +
                "  <input type=\"number\" min=\"1\" max=\"1000\" required=\"\" value=\"2\" id=\"redPacketCount\" onkeypress=\"return(/[\\d]/.test(String.fromCharCode(event.keyCode)))\">\n" +
                "</label>\n" +
                "<label>\n" +
                "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">留言</div>\n" +
                "  <div class=\"fn-hr5 fn__5\"></div>\n" +
                "  <input type=\"text\" id=\"redPacketMsg\" placeholder=\"摸鱼者，事竟成！\" maxlength=\"20\">\n" +
                "</label>\n" +
                "<div class=\"fn-hr5\"></div>\n" +
                "<div class=\"fn__flex\" style=\"margin-top: 15px\">\n" +
                "  <div id='totalAmount' class=\"fn__flex-1 fn__flex-center\" style=\"text-align: left;\">总计：<span id=\"redPacketAmount\">32</span> 积分</div>\n" +
                "  <button class=\"btn btn--confirm\" id=\"redPacketConfirm\">发送</button>\n" +
                "</div>\n" +
                "</div>" +
                "", "发红包");

            var UserSelectedList = [];
            var SelectedPanelTimeout = 0;
            $("#userInput").on('focus input', function () {
                var inputName = $("#userInput").val().toUpperCase();

                clearTimeout(SelectedPanelTimeout);
                $(".selected-username-box").hide();
                $("#chatUsernameSelectedPanel").html("");
                var SelectedHtml = "";
                onelineUsers.forEach((userInfo, userName) => {
                    var testName = userName.toUpperCase();
                    if (testName.includes(inputName)) {
                        SelectedHtml += `<div class="candidateName">${userName}</div>`
                    }
                })
                $("#chatUsernameSelectedPanel").html(SelectedHtml);

                $(".selected-username-box").show();

                $(".candidateName").on("click", function () {
                    let clickUserName = $(this).html();
                    console.log(UserSelectedList.includes(clickUserName))
                    if (!UserSelectedList.includes(clickUserName)) {
                        UserSelectedList.push(clickUserName);
                        var userInfos = [];
                        for (index in UserSelectedList) {
                            userInfos.push(onelineUsers.get(UserSelectedList[index]))
                        }
                        $("#recivers").html("");
                        $("#redPacketCount").val(userInfos.length);
                        for (var userInfo in userInfos) {
                            $("#recivers").append("<a target=\"_blank\" data-name=\"" + userInfos[userInfo].userName + "\"\n" +
                                "href=\"" + userInfos[userInfo].homePage + "\">\n" +
                                "<img style='margin-bottom: 10px' class=\"avatar avatar-small\" aria-label=\"" + userInfos[userInfo].userName + "\"\n" +
                                "src=\"" + userInfos[userInfo].userAvatarURL + "\">\n" +
                                "</a>");
                        }
                    }
                })
            })

            $("#userInput").on("blur", function () {
                SelectedPanelTimeout = setTimeout(() => {
                    $(".selected-username-box").hide();
                }, 500)
            })

            var onelineUsers = new Map();
            $("#redPacketType").on('change', function () {
                let type = $("#redPacketType").val();
                if (type === 'specify') {
                    $('#who').removeAttr("style");
                    $("#redPacketCount").val("1");
                    $('#redPacketCount').attr("readOnly", "true");
                    $.ajax({
                        url: Label.servePath + '/chat-room/online-users',
                        type: 'GET',
                        cache: false,
                        success: function (result) {
                            // console.log(result)
                            if (0 == result.code) {
                                $("#userOption").html("");
                                for (var userIndex in result.data.users) {
                                    var user = result.data.users[userIndex]
                                    onelineUsers.set(user.userName, user)
                                }
                            } else {
                                // console.log("获取聊天室在线成员信息失败")
                            }
                        },
                        error: function (result) {
                            // console.log("获取聊天室在线成员信息失败")
                        }
                    })
                } else {
                    $('#who').css('display', 'none')
                    $('#gesture').css('display', 'none')
                    $('#redPacketCount').removeAttr("readOnly");
                    $('#redPacketMoneyLabel').removeAttr("style");
                    $('#totalAmount').css('display', 'inline')
                    $("#countx").text("个数");
                    $("#redPacketCount").val("1");
                }
                if (type === 'heartbeat') {
                    $("#countx").text("个数（最少5个）");
                    $("#redPacketCount").val("5");
                }
                if (type === 'rockPaperScissors') {
                    $('#gesture').removeAttr("style");
                    $("#redPacketCount").val("1");
                    $('#redPacketCount').attr("readOnly", "true");
                    $("#redPacketAmount").text($("#redPacketMoney").val() + " (含猜拳红包税 10%，实际红包 " + Math.floor($("#redPacketMoney").val() * 0.9) + " 积分) ");
                }
                if (type === 'dice') {
                    $('#redPacketMoneyLabel').css('display', 'none')
                    $('#totalAmount').css('display', 'none')
                    $("#countx").text("开盘人数");
                    $("#redPacketCount").val("3");
                }
            });

            $("#redPacketMoney").unbind();
            $("#redPacketCount").unbind();

            $("#redPacketMoney").on('change', function () {
                if ($("#redPacketMoney").val() === "") {
                    $("#redPacketMoney").val("32");
                }
                if ($("#redPacketMoney").val() < 32) {
                    $("#redPacketMoney").val("32");
                }
                $("#redPacketAmount").text($("#redPacketMoney").val());
                let type = $("#redPacketType").val();
                if (type === 'rockPaperScissors') {
                    $("#redPacketAmount").text($("#redPacketMoney").val() + " (含猜拳红包税 10%，实际红包 " + Math.floor($("#redPacketMoney").val() * 0.9) + " 积分) ");
                }
            });

            $('#redPacketMoney,#redPacketCount').bind('input propertychange', function () {
                let type = $("#redPacketType").val();
                if (type === 'average') {
                    $("#redPacketAmount").text($("#redPacketMoney").val() * $("#redPacketCount").val());
                    $("#redPacketMsg").val("平分红包，人人有份！");
                } else if (type === 'random') {
                    $("#redPacketAmount").text($("#redPacketMoney").val());
                    $("#redPacketMsg").val("摸鱼者，事竟成！");
                } else if (type === 'specify') {
                    $("#redPacketAmount").text($("#redPacketMoney").val() * $("#redPacketCount").val());
                    $("#redPacketMsg").val("试试看，这是给你的红包吗？");
                } else if (type === 'heartbeat') {
                    $("#redPacketAmount").text($("#redPacketMoney").val());
                    $("#redPacketMsg").val("玩的就是心跳！");
                } else if (type === 'rockPaperScissors') {
                    $("#redPacketAmount").text($("#redPacketMoney").val() + " (含猜拳红包税 10%，实际红包 " + Math.floor($("#redPacketMoney").val() * 0.9) + " 积分) ");
                }
            });

            $("#redPacketType").on('change', function () {
                let type = $("#redPacketType").val();
                if (type === 'average') {
                    $("#redPacketAmount").text($("#redPacketMoney").val() * $("#redPacketCount").val());
                    $("#redPacketMsg").val("平分红包，人人有份！");
                } else if (type === 'random') {
                    $("#redPacketAmount").text($("#redPacketMoney").val());
                    $("#redPacketMsg").val("摸鱼者，事竟成！");
                } else if (type === 'specify') {
                    $("#redPacketAmount").text($("#redPacketMoney").val() * $("#redPacketCount").val());
                    $("#redPacketMsg").val("试试看，这是给你的红包吗？");
                } else if (type === 'heartbeat') {
                    $("#redPacketAmount").text($("#redPacketMoney").val());
                    $("#redPacketMsg").val("玩的就是心跳！");
                } else if (type === 'rockPaperScissors') {
                    $("#redPacketMsg").val("石头剪刀布！");
                } else if (type === 'dice') {
                    $("#redPacketMsg").val("买定离手！");
                }
            });

            $("#redPacketCount").on('change', function () {
                let type = $("#redPacketType").val();
                if (type === 'dice') {
                    if ($("#redPacketCount").val() > 15) {
                        $("#redPacketCount").val("15");
                    }
                    if ($("#redPacketCount").val() < 3) {
                        $("#redPacketCount").val("3");
                    }
                }

                if (type === 'heartbeat') {
                    if ($("#redPacketCount").val() < 5) {
                        $("#redPacketCount").val("5");
                    }
                }

                if (Number($("#redPacketCount").val()) > Number($("#redPacketMoney").val())) {
                    $("#redPacketCount").val($("#redPacketMoney").val());
                } else {
                    if ($("#redPacketCount").val() > 100) {
                        $("#redPacketCount").val("100");
                    }
                    if ($("#redPacketCount").val() <= 0) {
                        $("#redPacketCount").val("1");
                    }
                }
            });

            $("#redPacketConfirm").on('click', function () {
                let type = $("#redPacketType").val();
                let money = $("#redPacketMoney").val();
                let count = $("#redPacketCount").val();
                let msg = $("#redPacketMsg").val();
                let recivers = UserSelectedList;
                let gesture = $("#gestureType").val();
                if (type === '' || type === null || type === undefined) {
                    type = "random";
                }
                if (recivers === undefined) {
                    recivers = []
                }
                if (recivers.length == 0 && type === 'specify') {
                    $('#chatContentTip').addClass('error').html('<ul><li>请选择红包发送对象</li></ul>')
                }
                if (msg === '') {
                    msg = '摸鱼者，事竟成！';
                }
                let content;
                if (type !== "rockPaperScissors") {
                    content = {
                        type: type,
                        money: money,
                        count: count,
                        msg: msg,
                        recivers: recivers
                    }
                } else {
                    content = {
                        type: type,
                        money: money,
                        count: count,
                        msg: msg,
                        recivers: recivers,
                        gesture: gesture
                    }
                }
                let requestJSONObject = {
                    content: "[redpacket]" + JSON.stringify(content) + "[/redpacket]",
                    client: thisClient
                }
                $.ajax({
                    url: Label.servePath + '/chat-room/send',
                    type: 'POST',
                    cache: false,
                    data: JSON.stringify(requestJSONObject),
                    success: function (result) {
                        if (0 !== result.code) {
                            $('#chatContentTip').addClass('error').html('<ul><li>' + result.msg + '</li></ul>')
                        }
                    },
                    error: function (result) {
                        $('#chatContentTip').addClass('error').html('<ul><li>' + result.statusText + '</li></ul>')
                    }
                })
                Util.closeAlert();
            })
        });

        // 加载挂件
        ChatRoom.loadAvatarPendant();
        // 加载用户捕获
        ChatRoom.initCatchUser();
        // 加载播放器
        //ChatRoom.playSound.init();
        // 加载画图
        ChatRoom.charInit('paintCanvas');
        // 监听弹幕
        $("#barragerBtn").on('click', function () {
            if ($("#barragerContent").css("display") === 'none') {
                $("#barragerContent").slideDown(1000);
                $("#paintContent").slideUp(1000);
            } else {
                $("#barragerContent").slideUp(1000);
            }
        });
        $("#barragerInput").keydown(function (event) {
            if (event.keyCode == 13) {
                ChatRoom.sendBarrager();
            }
        });
        // 监听画图按钮
        $("#paintBtn").on('click', function () {
            if ($("#paintContent").css("display") === 'none') {
                $("#paintContent").slideDown(1000);
                $("#barragerContent").slideUp(1000);
            } else {
                $("#paintContent").slideUp(1000);
            }
        });
        /*BarragerColorPicker = new XNColorPicker({
            color: "#ffffff",
            selector: "#selectBarragerColor",
            showhistorycolor: false,
            colorTypeOption: 'single',
            autoConfirm: true,
            onError: function (e) {
            },
            onCancel: function (color) {
            },
            onChange: function (color) {
            },
            onConfirm: function (color) {
            }
        })*/
        // 监听弹幕颜色
        // $('#selectBarragerColor').cxColor({
        //     color: '#ffffff'
        // });
        // 监听修改颜色
        // $('#selectColor').cxColor();
        /*DarwColorPicker = new XNColorPicker({
            color: "#000000",
            selector: "#selectColor",
            showhistorycolor: false,
            colorTypeOption: 'single',
            autoConfirm: true,
            onError: function (e) {
            },
            onCancel: function (color) {
            },
            onChange: function (color) {
                // console.log("change",color.color.rgba)
                ChatRoom.changeColor(color.color.rgba);
            },
            onConfirm: function (color) {
                // console.log("change",color.color.rgba)
                ChatRoom.changeColor(color.color.rgba);
            }
        })*/
        // $("#selectColor").bind("change", function () {
        //     ChatRoom.changeColor(this.value);
        // });
        $("#selectWidth").bind("change", function () {
            let width = $("#selectWidth").val();
            ChatRoom.changeWidth(width);
        });

        // 流畅模式
        let smoothMode = localStorage.getItem("smoothMode") || 'false';
        if (smoothMode === 'true') {
            ChatRoom.enableSmoothMode();
        } else {
            setInterval(ChatRoom.reloadMessages, 15 * 60 * 1000);
        }
    },
    // 启用流畅模式
    enableSmoothMode: function () {
        console.log("Smooth mode enabling...");
        $('#smoothMode').html('开启');
        setInterval(ChatRoom.reloadMessages, 3 * 1000);
    },
    toggleSmoothMode: function () {
        let status;
        if ($('#smoothMode').html() === '开启') {
            status = 'false';
        } else {
            status = 'true';
        }

        localStorage.setItem("smoothMode", status);
        if (status === 'true') {
            Util.notice("success", 5000, "流畅模式已开启，占用内存更小，体验更流畅。");
            ChatRoom.enableSmoothMode();
        } else {
            location.reload();
        }
    },
    sendBarrager: function () {
        // let color = $("#selectBarragerColor")[0].value;
        let color = 'rgba(255,255,255,1)';
        let content = $('#barragerInput').val();
        let json = {
            color: color,
            content: content
        };
        let requestJSONObject = {
            content: "[barrager]" + JSON.stringify(json) + "[/barrager]",
            client: thisClient
        }
        $.ajax({
            url: Label.servePath + '/chat-room/send',
            type: 'POST',
            cache: false,
            data: JSON.stringify(requestJSONObject),
            success: function (result) {
                if (0 !== result.code) {
                    $('#chatContentTip').addClass('error').html('<ul><li>' + result.msg + '</li></ul>')
                } else {
                    $('#barragerInput').val('');
                }
            },
            error: function (result) {
                $('#chatContentTip').addClass('error').html('<ul><li>' + result.statusText + '</li></ul>')
            }
        });
    },
    times: 0,
    reloadMessages: function () {
        ChatRoom.times++;
        if (document.documentElement.scrollTop <= 2000) {
            ChatRoom.flashScreenQuiet();
        } else if (ChatRoom.times > 20) {
            ChatRoom.flashScreenQuiet();
            ChatRoom.times = 0;
        }

        if (ChatRoom.times > 20) {
            ChatRoom.times = 0;
        }
    },
    flashScreen: function () {
        NProgress.start();
        $('#chats').css("display", "none");
        page = 1;
        let chatLength = $("#chats>div");
        if (chatLength.length > 25) {
            for (let i = chatLength.length - 1; i > 24; i--) {
                chatLength[i].remove();
            }
        }
        setTimeout(function () {
            $('#chats').css("display", "block");
            NProgress.done();
        }, 150);
    },
    flashScreenQuiet: function () {
        page = 1;
        let chatLength = $("#chats>div");
        if (chatLength.length > 25) {
            for (let i = chatLength.length - 1; i > 24; i--) {
                chatLength[i].remove();
            }
        }
    },
    /**
     * 打开思过崖
     */
    showSiGuoYar: function () {
        Util.alert(`
<style>
.dialog-panel {
border-radius: 20px 20px 20px 20px;
border: 0;
box-shadow: none;
}
.dialog-header-bg {
display: none;
}
.dialog-main {
height: 456px;
overflow: auto;
padding: 10px 10px 20px !important;
color: #e1e1e1;
background: url(https://file.fishpi.cn/2023/04/面壁-5e5b04c3.jpg) no-repeat;
background-size: 100% 100%;
background-attachment: fixed;
font-family: STKaiti;
}
.list>ul {
margin-top: 15px;
}
.list>ul>li {
padding: 6px 8px;
border-bottom: none;
}
</style>
<div class="fn-hr5"></div>
<div class="ft__center">
    <div>
        <h2>思過崖</h2>
        <div class="fn-hr5"></div>
        <span>摸魚派倡導自由、友善的交流環境。<br>這裏收留了因不遵守摸魚法則而受到處罰的魚油。</span>
    </div>
    <div class="list">
    <ul id="si-guo-list">
    </ul>
    </div>
</div>`);
        $.ajax({
            url: Label.servePath + '/chat-room/si-guo-list',
            type: 'GET',
            cache: false,
            async: false,
            success: function (result) {
                let list = result.data;
                if (list.length == 0) {
                    $("#si-guo-list").prepend('<li style="color: #3caf36; font-weight: bold;">目前沒有受到處罰的魚油，請繼續保持！</li>');
                }
                for (let i = 0; i < list.length; i++) {
                    let j = list[i];
                    let date = new Date(j.time);
                    let userAvatarURL = j.userAvatarURL;
                    let userName = j.userName;
                    let userNickname = j.userNickname;
                    let useName = userName;
                    if (userNickname != '') {
                        useName = userNickname;
                    }
                    $("#si-guo-list").prepend(`
    <li class="fn__flex menu__item">
        <img class="avatar avatar--mid" style="width: 24px; height: 24px; margin-right: 10px; background-image: none; background-color: transparent;" src="` + userAvatarURL + `">
        <div class="fn__flex-1" style="text-align: left !important;">
            <h2 class="list__user">
                <a target="_blank" href="` + Label.servePath + `/member/` + userName + `" style="color: #c0c0c0; text-decoration: none;">` + useName + `</a>
            </h2>
        </div>
        <div class="fn__flex-center" style="color: #ff1919; font-weight: bold">
        將於 ` + date.getFullYear() + `年` + (date.getMonth() + 1 < 10 ? '0' + (date.getMonth() + 1) : date.getMonth() + 1) + `月` + date.getDate() + `日 ` + date.getHours() + `時` + date.getMinutes() + `分 釋放
        </div>
  
    </li>
                `);
                }
            }
        })
    },
    /**
     * 提交写好字的图片.
     *
     * @param {string} id canvas id.
     */
    submitCharacter: function (id) {
        if (linesArray.length !== 0) {
            var canvas = document.getElementById(id);
            let dataURL = canvas.toDataURL();
            let blob = dataURLToBlob(dataURL);
            var formData = new FormData();
            formData.append("file[]", blob);
            $.ajax({
                url: Label.servePath + '/upload',
                type: 'POST',
                cache: false,
                data: formData,
                processData: false,
                contentType: false,
                success: function (data) {
                    let url = data.data.succMap.blob;
                    ChatRoom.editor.setValue(ChatRoom.editor.getValue() + '![涂鸦](' + url + ')');
                    ChatRoom.editor.focus();
                    ChatRoom.clearCharacter("paintCanvas");
                    $("#paintContent").slideUp(500);
                },
                error: function (err) {
                }
            });

            function dataURLToBlob(dataurl) {
                var arr = dataurl.split(',');
                var mime = arr[0].match(/:(.*?);/)[1];
                var bstr = atob(arr[1]);
                var n = bstr.length;
                var u8arr = new Uint8Array(n);
                while (n--) {
                    u8arr[n] = bstr.charCodeAt(n);
                }
                return new Blob([u8arr], {type: mime});
            }

            linesArray = [];
            $(window).scrollTop(0);
        } else {
            alert("画布为空，无法提交！")
        }
    },
    /**
     * clear canvas
     *
     * @param {string} id canvas id.
     */
    clearCharacter: function (id) {
        var canvas = document.getElementById(id),
            ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
        linesArray = [];
    },
    revokeChatacter: function (id) {
        // 存储点集的数组
        if (linesArray.length > 0) {
            ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
            // 删掉上一次操作
            linesArray.pop();

            // 遍历历史记录重新绘制
            linesArray.forEach(arr => {
                ChatRoom.changeColor(arr.color);
                ChatRoom.changeWidth(arr.width);
                ctx.beginPath();
                ctx.moveTo(arr.point[0].x, arr.point[0].y);
                for (let i = 1; i < arr.point.length; i++) {
                    ctx.lineTo(arr.point[i].x, arr.point[i].y);
                }
                ctx.stroke();
            })
        }
    },
    /**
     * paint brush
     * @param {string} id canvas id.
     * @returns {undefined}
     */
    changeColor: function (color) {
        ctx.fillStyle = ctx.strokeStyle = ctx.shadowColor = color;
    },
    changeWidth: function (width) {
        ctx.lineWidth = width;
    },
    charInit: function (id) {
        el = document.getElementById(id);
        ctx = el.getContext('2d');
        ctx.fillStyle = ctx.strokeStyle = ctx.shadowColor = '#000';
        ctx.lineWidth = 3;
        ctx.lineJoin = 'miter';
        ctx.lineCap = 'round';
        ctx.shadowBlur = 2;

        el.onmousedown = function (e) {
            pointsArray = [];
            isDrawing = true;
            isClick = true;
            ctx.beginPath();
            x = e.clientX - e.target.offsetLeft + $(window).scrollLeft();
            y = e.clientY - e.target.offsetTop + $(window).scrollTop();
            pointsArray.push({x: x, y: y});
            ctx.moveTo(x, y);
        };

        el.onmousemove = function (e) {
            if (!isDrawing) {
                return;
            }
            isClick = false;

            x = e.clientX - e.target.offsetLeft + $(window).scrollLeft();
            y = e.clientY - e.target.offsetTop + $(window).scrollTop();
            pointsArray.push({x: x, y: y});
            ctx.lineTo(x, y);
            ctx.stroke();
        };

        el.onmouseup = function () {
            linesArray.push({
                point: pointsArray,
                color: ctx.fillStyle,
                width: ctx.lineWidth
            });
            if (isClick) {
                ctx.lineTo(x, y);
                ctx.stroke();
            }
            isDrawing = false;
        };

        el.addEventListener("touchstart", function (e) {
            isClick = true;
            pointsArray = [];
            ctx.beginPath();
            x = e.changedTouches[0].pageX - e.target.offsetLeft;
            y = e.changedTouches[0].pageY - e.target.offsetTop;
            pointsArray.push({x: x, y: y});
            ctx.moveTo(x, y);

        }, false);

        el.addEventListener("touchmove", function (e) {
            isClick = false;
            e.preventDefault();
            x = e.changedTouches[0].pageX - e.target.offsetLeft;
            y = e.changedTouches[0].pageY - e.target.offsetTop;
            pointsArray.push({x: x, y: y});
            ctx.lineTo(x, y);
            ctx.stroke();
        }, false);

        el.addEventListener("touchend", function (e) {
            if (isClick) {
                ctx.lineTo(x, y);
                ctx.stroke();
            }
            linesArray.push({
                point: pointsArray,
                color: ctx.fillStyle,
                width: ctx.lineWidth
            });
        }, false);
    },
    /**
     * 设置话题
     */
    setDiscuss: function () {
        Util.alert("" +
            "<div class=\"form fn__flex-column\">\n" +
            "<label>\n" +
            "  <div class=\"ft__smaller\" style=\"float: left\">修改话题需要16积分，将自动从账户中扣除；<br>最大长度64字符，不合法字符将被自动过滤。</div>\n" +
            "  <div class=\"fn-hr5 fn__5\"></div>\n" +
            "  <input type=\"text\" id=\"discuss-new-title\">\n" +
            "</label>\n" +
            "<div class=\"fn-hr5\"></div>\n" +
            "<div class=\"fn__flex\" style=\"margin-top: 15px; justify-content: flex-end;\">\n" +
            "  <button class=\"btn btn--confirm\" onclick='ChatRoom.updateDiscussData($(\"#discuss-new-title\").val());Util.closeAlert();'>设置 <span class='count'><svg style='vertical-align: -2px;'><use xlink:href=\"#iconPoints\"></use></svg> 16</span></button>\n" +
            "</div>\n" +
            "</div>" +
            "", "设置话题");
    },
    /**
     * 设置话题
     */
    updateDiscussData: function (discuss) {
        let requestJSONObject = {
            content: "[setdiscuss]" + discuss + "[/setdiscuss]",
            client: thisClient
        }
        $.ajax({
            url: Label.servePath + '/chat-room/send',
            type: 'POST',
            cache: false,
            data: JSON.stringify(requestJSONObject),
            success: function (result) {
                if (0 !== result.code) {
                    Util.notice("danger", 3000, result.msg);
                } else {
                    Util.notice("success", 3000, "话题修改成功，所有人可见。<br>16积分已扣除。");
                }
            },
            error: function (result) {
                Util.notice("danger", 3000, result.statusText);
            }
        })
    },
    /**
     * 引用话题
     */
    useDiscuss: function () {
        let history = ChatRoom.editor.getValue();
        ChatRoom.editor.setValue("*`# " + $("#discuss-title").html() + " #`*  ");
        ChatRoom.editor.insertValue(history, 0);
        ChatRoom.editor.focus();
    },
    /**
     * 切换显示/隐藏在线人数头像
     */
    toggleOnlineAvatar: function () {
        if ($("#chatRoomOnlineCnt").css("display") === 'none') {
            $("#chatRoomOnlineCnt").html(Label.onlineAvatarData);
            setTimeout(function () {
                $("#toggleAvatarBtn").html('<use xlink:href="#showLess"></use>');
                $("#chatRoomOnlineCnt").slideDown(200);
                setTimeout(function () {
                    Util.listenUserCard();
                }, 200);
            }, 100);
        } else {
            $("#toggleAvatarBtn").html('<use xlink:href="#showMore"></use>');
            $("#chatRoomOnlineCnt").slideUp(200);
        }
    },
    /**
     * 删除表情包
     * @param url
     */
    confirmed: false,
    delEmoji: function (url) {
        if (ChatRoom.confirmed === true || confirm("确定要删除该表情包吗？")) {
            ChatRoom.confirmed = true;
            let emojis = ChatRoom.getEmojis();
            for (let i = 0; i < emojis.length; i++) {
                if (emojis[i] === url) {
                    emojis.splice(i, 1);
                }
            }
            emojis.reverse();
            $.ajax({
                url: Label.servePath + "/api/cloud/sync",
                method: "POST",
                data: JSON.stringify({
                    gameId: "emojis",
                    data: emojis
                }),
                headers: {'csrfToken': Label.csrfToken},
                async: false,
                success: function (result) {
                    if (result.code === 0) {
                        Util.notice("success", 1500, "表情包删除成功。");
                        ChatRoom.loadEmojis();
                        setTimeout(function () {
                            $("#emojiBtn").click();
                        }, 50)
                    } else {
                        Util.notice("warning", 1500, "表情包删除失败：" + result.msg);
                    }
                }
            });
        }
    },
    /**
     * 加载表情
     */
    loadEmojis: function () {
        let emojis = ChatRoom.getEmojis(), html = "";
        for (let i = 0; i < emojis.length; i++) {
            html += `<button onclick="ChatRoom.editor.setValue(ChatRoom.editor.getValue() + '![图片表情](${emojis[i]})')">
    <div class="divX"><svg onclick='ChatRoom.delEmoji("${emojis[i]}");event.cancelBubble =true;' style="width: 15px; height: 15px;"><use xlink:href="#delIcon"></use></svg></div>
    <img style='max-height: 50px' class="vditor-emojis__icon" src="${emojis[i]}">
</button>`;
        }
        $("#emojis").html(html);
    },
    /**
     * 上传表情
     */
    listenUploadEmojis: function () {
        $('#uploadEmoji').fileupload({
            acceptFileTypes: /(\.|\/)(gif|jpe?g|png)$/i,
            maxFileSize: 5242880,
            multipart: true,
            pasteZone: null,
            dropZone: null,
            url: Label.servePath + '/upload',
            paramName: 'file[]',
            add: function (e, data) {
                ext = data.files[0].type.split('/')[1]

                if (window.File && window.FileReader && window.FileList &&
                    window.Blob) {
                    var reader = new FileReader()
                    reader.readAsArrayBuffer(data.files[0])
                    reader.onload = function (evt) {
                        var fileBuf = new Uint8Array(evt.target.result.slice(0, 11))
                        var isImg = isImage(fileBuf)

                        if (!isImg) {
                            Util.alert('只允许上传图片!')

                            return
                        }

                        if (evt.target.result.byteLength > 1024 * 1024 * 5) {
                            Util.alert('图片过大 (最大限制 5M)')

                            return
                        }

                        data.submit()
                    }
                } else {
                    data.submit()
                }
            },
            formData: function (form) {
                var data = form.serializeArray()
                return data
            },
            submit: function (e, data) {
            },
            done: function (e, data) {
                var result = {
                    result: {
                        key: data.result.data.succMap[Object.keys(data.result.data.succMap)[0]]
                    }
                }
                ChatRoom.addEmoji(result.result.key);
            },
            fail: function (e, data) {
                Util.alert('Upload error: ' + data.errorThrown)
            },
        })
    },
    // 从URL导入表情包
    fromURL: function () {
        Util.alert("" +
            "<div class=\"form fn__flex-column\">\n" +
            "<label>\n" +
            "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">请输入图片的URL</div>\n" +
            "  <div class=\"fn-hr5 fn__5\"></div>\n" +
            "  <input type=\"text\" id=\"fromURL\">\n" +
            "</label>\n" +
            "<div class=\"fn-hr5\"></div>\n" +
            "<div class=\"fn__flex\" style=\"margin-top: 15px; justify-content: flex-end;\">\n" +
            "  <button class=\"btn btn--confirm\" onclick='ChatRoom.addEmoji($(\"#fromURL\").val());Util.closeAlert();'>导入</button>\n" +
            "</div>\n" +
            "</div>" +
            "", "从URL导入表情包");
        $("#fromURL").focus();
        $("#fromURL").unbind();
        $("#fromURL").bind('keypress', function (event) {
            if (event.keyCode == "13") {
                ChatRoom.addEmoji($("#fromURL").val());
                Util.closeAlert();
            }
        });
    },
    addEmoji: function () {
        for (let i = 0; i < arguments.length; i++) {
            let url = arguments[i];
            let emojis = ChatRoom.getEmojis();
            emojis.reverse();
            for (let i = 0; i < emojis.length; i++) {
                if (emojis[i] === url) {
                    emojis.splice(i, 1);
                }
            }
            emojis.push(url);
            $.ajax({
                url: Label.servePath + "/api/cloud/sync",
                method: "POST",
                data: JSON.stringify({
                    gameId: "emojis",
                    data: emojis
                }),
                headers: {'csrfToken': Label.csrfToken},
                async: false,
                success: function (result) {
                    if (result.code !== 0) {
                        Util.notice("warning", 1500, "表情包上传失败：" + result.msg);
                    }
                }
            });
        }
        Util.notice("success", 1500, "表情包上传成功。");
        $("details[open]").removeAttr("open");
        ChatRoom.loadEmojis();
    },
    /**
     * 获取表情包
     */
    getEmojis: function () {
        let ret;
        $.ajax({
            url: Label.servePath + "/api/cloud/get",
            method: "POST",
            data: JSON.stringify({
                gameId: "emojis",
            }),
            headers: {'csrfToken': Label.csrfToken},
            async: false,
            success: function (result) {
                if (result.code === 0 && result.data !== "") {
                    ret = Util.parseArray(result.data);
                } else {
                    ret = [];
                }
            },
        });
        ret.reverse();
        return ret;
    },
    /**
     * 发送聊天内容
     * @returns {undefined}
     */
    isSend: false,
    send: function () {
        if (ChatRoom.isSend) {
            return;
        }
        ChatRoom.isSend = true;
        var content = ChatRoom.editor.getValue()
        var requestJSONObject = {
            content: content,
            client: thisClient
        }
        ChatRoom.editor.setValue('')
        $.ajax({
            url: Label.servePath + '/chat-room/send',
            type: 'POST',
            cache: false,
            data: JSON.stringify(requestJSONObject),
            beforeSend: function () {
                $('.form button.red').attr('disabled', 'disabled').css('opacity', '0.3')
            },
            success: function (result) {
                if (0 === result.code) {
                    $('#chatContentTip').removeClass('error succ').html('')
                } else {
                    $('#chatContentTip').addClass('error').html('<ul><li>' + result.msg + '</li></ul>')
                    ChatRoom.editor.setValue(content)
                }
            },
            error: function (result) {
                $('#chatContentTip').addClass('error').html('<ul><li>' + result.statusText + '</li></ul>')
                ChatRoom.editor.setValue(content)
            },
            complete: function (jqXHR, textStatus) {
                ChatRoom.isSend = false;
                $('.form button.red').removeAttr('disabled').css('opacity', '1')
            },
        })
    },
    /**
     * 获取更多内容
     * @returns {undefined}
     */
    more: function () {
        NProgress.start();
        setTimeout(function () {
            page++;
            let chatMessageLatestOId;
            if (page !== 1) {
                let chatMessages = $(".chats__item");
                let chatMessageLatest = chatMessages[chatMessages.length - 1];
                chatMessageLatestOId = $(chatMessageLatest).attr('id').replace('chatroom', '');
            }
            if (Label.hasMore) {
                if (page === 1) {
                    $.ajax({
                        url: Label.servePath + '/chat-room/more?page=' + page,
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
                                ChatRoom.imageViewer()
                            } else {
                                alert("没有更多聊天消息了！");
                                Label.hasMore = false;
                            }
                        }
                    });
                } else {
                    $.ajax({
                        url: Label.servePath + '/chat-room/getMessage?size=25&mode=1&oId=' + chatMessageLatestOId,
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
                                ChatRoom.imageViewer()
                            } else {
                                alert("没有更多聊天消息了！");
                                Label.hasMore = false;
                            }
                        }
                    });
                }
            }
            NProgress.done();
        }, 0);
    },
    /**
     * 监听点击更多按钮关闭事件
     */
    resetMoreBtnListen: function () {
        $("body").unbind();
        $('body').click(function (event) {
            if ($(event.target).closest('a').attr('id') !== 'aPersonListPanel' &&
                $(event.target).closest('.module').attr('id') !== 'personListPanel') {
                $('#personListPanel').hide()
            }
        })
        $("body").click(function () {
            $("details[open]").removeAttr("open");
        });
    },
    /**
     * 开始批量撤回聊天室消息
     */
    groupRevokeProcess: false,
    startGroupRevoke: function () {
        $("#groupRevoke").attr("onclick", "ChatRoom.stopGroupRevoke()");
        $("#groupRevoke").html("关闭批量撤回");
        Util.notice("warning", 6000, "批量撤回已启动，已在消息中添加便捷撤回按钮。<br>使用完成后请记得关闭此功能。");
        ChatRoom.groupRevokeProcess = true;
        let groupRevokeInterval = setInterval(function () {
            if (!ChatRoom.groupRevokeProcess) {
                $('#chats').empty();
                page = 0;
                ChatRoom.more();
                clearInterval(groupRevokeInterval);
            }
            $(".chats__item").each(function () {
                if ($(this).find(".button").length === 0) {
                    $(this).find(".date-bar").css("float", "left");
                    $(this).find(".date-bar").html("<button class='button' onclick='ChatRoom.adminRevoke(\"" + $(this).attr("id").replace("chatroom", "") + "\")'>撤回</button>");
                }
            });
        }, 500);
    },
    /**
     * 停止批量撤回聊天室消息
     */
    stopGroupRevoke: function () {
        $("#groupRevoke").attr("onclick", "ChatRoom.startGroupRevoke()");
        $("#groupRevoke").html("批量撤回");
        Util.notice("success", 1500, "批量撤回已关闭。");
        ChatRoom.groupRevokeProcess = false;
    },
    /**
     * 管理员撤回聊天室消息，无提示
     * @param oId
     */
    adminRevoke: function (oId) {
        $.ajax({
            url: Label.servePath + '/chat-room/revoke/' + oId,
            type: 'DELETE',
            cache: false,
            success: function (result) {
                if (0 === result.code) {
                } else {
                    Util.notice("danger", 1500, result.msg);
                }
            }
        });
    },
    /**
     * 屏蔽聊天室 某人 消息
     * @param userName
     */
    shileds: ',',
    shiled: function (uName) {
        if (confirm("友好的交流是沟通的基础, 确定要屏蔽 Ta 吗？\n本次屏蔽仅针对当前页面有效, 刷新后需重新屏蔽！")) {
            ChatRoom.shileds += uName + ",";
        }
    },
    /**
     * 撤回聊天室消息
     * @param oId
     */
    revoke: function (oId) {
        if (confirm("确定要撤回吗？")) {
            $.ajax({
                url: Label.servePath + '/chat-room/revoke/' + oId,
                type: 'DELETE',
                cache: false,
                success: function (result) {
                    if (0 === result.code) {
                        Util.notice("success", 1500, result.msg);
                    } else {
                        Util.notice("danger", 1500, result.msg);
                    }
                }
            });
        }
    },
    /**
     * 复读机
     */
    repeat: function (id) {
        let md = '';
        $.ajax({
            url: Label.servePath + '/cr/raw/' + id,
            method: 'get',
            async: false,
            success: function (result) {
                md = result.replace(/(<!--).*/g, "");
            }
        });
        ChatRoom.editor.setValue(md);
        ChatRoom.send();
        $(window).scrollTop(0);
    },
    /**
     * 屏蔽用户发言，收纳到小机器人里
     * @param userName
     */
    block: function (userName) {
        var robotList = window.localStorage['robot_list'] ? window.localStorage['robot_list'] : '';
        if (robotList && robotList.length > 0) {
            robotList = robotList + "," + userName;
        } else {
            robotList = userName;
        }
        window.localStorage['robot_list'] = robotList;
        catchUsers = robotList.split(",");
        Util.notice("success", 10000, "屏蔽 " + userName + " 成功，您可以在左侧小机器人图标中找到 TA 发送的消息，也可以在 “修改捕获用户” 中解除对 TA 的屏蔽。");
    },
    /**
     * 一键举报
     * @param id
     */
    report: function (id) {
        var res = confirm("确定举报吗？");
        if (res) {
            $.ajax({
                url: Label.servePath + '/report',
                type: 'POST',
                cache: false,
                data: JSON.stringify({
                    reportDataId: id,
                    reportDataType: 3,
                    reportType: 49,
                    reportMemo: '',
                }),
                complete: function (result) {
                    if (result.responseJSON.code === 0) {
                        Util.alert('一键举报成功，感谢你的帮助！<br>管理员将进行审核，如情况属实系统会为举报人发放积分奖励，并对违规者进行相应处罚。');
                    } else {
                        Util.alert(result.responseJSON.msg);
                    }
                },
            })
        }
    },
    /**
     * 艾特某个人
     */
    at: function (userName, id, justAt) {
        ChatRoom.editor.focus();
        if (justAt === true) {
            ChatRoom.editor.insertValue("@" + userName + "  \n", !1);
        } else {
            let md = '';
            $.ajax({
                url: Label.servePath + '/cr/raw/' + id,
                method: 'get',
                async: false,
                success: function (result) {
                    md = result.replace(/(<!--).*/g, "");
                    md = md.replace(/\n/g, "\n> ");
                }
            });
            ChatRoom.editor.insertValue(`\n##### 引用 @${userName} [↩](${Label.servePath}/cr#chatroom${id} "跳转至原消息")  \n> ${md}</span>\n`, !1);
        }
        $(window).scrollTop(0);
    },
    /**
     * 给用户添加备注
     */
    remarkList: {},
    remark: function (userId, userName) {
        let userRemark = prompt(`要给 ${userName} 备注什么呢?`);
        if (userRemark === null) return;
        if (userRemark === '') {
            delete ChatRoom.remarkList[userId];
        } else {
            ChatRoom.remarkList[userId] = userRemark;
        }
        localStorage.setItem('user_remark', JSON.stringify(ChatRoom.remarkList));
    },
    /**
     * 处理消息
     * 处理图片压缩 处理特殊颜色文字
     * */
    filterContent: function (content, isAdmin) {
        let dom = document.createElement("div");
        dom.innerHTML = content;
        let imgList = dom.querySelectorAll('img');
        imgList.forEach(ele => {
            ele.setAttribute('originalsrc', ele.src);
            if (ele.src.startsWith('https://file.fishpi.cn')) {
                ele.src = ele.src.split('?')[0] + '?imageView2/0/w/150/h/150/interlace/0/q/90'
            }
        })
        if (isAdmin) {
            let textList = dom.querySelectorAll('p,h1,h2,h3,h4,h5,h6,h7');
            let reg = /\[color=([^\]]+)\](.*?)\[\/color\]/g;
            textList.forEach(ele => {
                ele.innerHTML = ele.innerText.replaceAll(reg, '<span style="color:$1">$2</span>')
            })
        }
        return dom.innerHTML;
    },
    /**
     * 渲染抢到红包的人的列表
     *
     * @param who
     */
    renderRedPacket: function (usersJSON, count, got, recivers, diceRet, packMaster) {
        let hasGot = false;
        let highest = -1;
        let winner
        let diceParticles
        if (diceRet !== undefined) {
            winner = diceRet.winnerResult;
            diceParticles = diceRet.diceParticles
        }
        if (count === got) {
            for (let i = 0; i < usersJSON.length; i++) {
                let current = usersJSON[i];
                if (current.userMoney > highest) {
                    highest = current.userMoney;
                }
            }
        }
        for (let i = 0; i < usersJSON.length; i++) {
            let current = usersJSON[i];
            let currentUserMoney = current.userMoney;
            let dice = current.dice
            let currentUserName = current.userName;
            if (currentUserName === Label.currentUser) {
                if (currentUserMoney !== undefined) {
                    if (currentUserMoney > 0) {
                        $("#redPacketIGot").text("抢到了 " + currentUserMoney + " 积分");
                    } else if (currentUserMoney == 0) {
                        $("#redPacketIGot").text("恭喜你，抢了个寂寞");
                    } else {
                        $("#redPacketIGot").text("什么运气，你竟然被反向抢红包了");
                    }
                } else {
                    if (dice !== undefined) {
                        $("#redPacketIGot").text("押注 " + dice.chips + " 积分");
                    }
                }
                hasGot = true;
            }
            let bet = '';
            if (dice !== undefined) {
                bet = dice.bet;
            }
            let userNameInfo = currentUserName
            switch (bet) {
                case 'big':
                    userNameInfo = currentUserName + " (" + "押大" + ")";
                    break
                case 'small':
                    userNameInfo = currentUserName + " (" + "押小" + ")";
                    break
                case 'leopard':
                    userNameInfo = currentUserName + " (" + "豹子" + ")";
                    break
            }
            let currentUserTime = current.time;
            let currentUserAvatar = "";
            if (current.avatar !== undefined) currentUserAvatar = `<img class="avatar avatar--mid" style="margin-right: 10px; background-image: none; background-color: transparent;" src="${current.avatar}">`;
            let html = `<li class="fn__flex menu__item">${currentUserAvatar}
<div class="fn__flex-1" style="text-align: left !important;">
<h2 class="list__user"><a href="${Label.servePath}/member/${currentUserName}">${userNameInfo}</a>
</h2>`;
            if (currentUserMoney > 0 && currentUserMoney === highest) {
                highest = -1;
                html += "<span class='green small btn'>来自老王的认可</span><br>\n";
            } else if (currentUserMoney === 0) {
                html += "<span class='red small btn'>0溢事件</span><br>\n";
            } else if (currentUserMoney < 0) {
                html += "<span class='yellow small btn'>抢红包有风险</span><br>\n";
            }
            if (winner !== undefined) {
                if (winner === dice.bet) {
                    html += "<span class='green small btn'>运气好而已</span><br>\n";
                } else {
                    html += "<span class='red small btn'>别玩了</span><br>\n";
                }
            }

            let money;
            if (currentUserMoney === undefined && dice !== undefined) {
                money = dice.chips
            } else {
                money = currentUserMoney
            }
            html += `<span class="ft__fade ft__smaller">${currentUserTime}</span>
    </div>
    <div class="fn__flex-center">${money} 积分</div>
</li>
`;
            $("#redPacketList").append(html);
        }
        if (!hasGot) {
            $("#redPacketIGot").text(Label.currentUser === packMaster ? "金主来了" : "你错过了这个红包");
        }

        if (undefined !== recivers) {
            if (recivers.length > 0) {
                index = recivers.indexOf(Label.currentUser);
                // console.log(index)
                if (index === -1) {
                    $("#msg").text("这个红包属于 " + recivers)
                    $("#redPacketIGot").text(Label.currentUser === packMaster ? "金主来了" : "这个红包不属于你");
                }
            }
        }

        if (undefined !== winner) {
            let content = " "
            let sum = 0
            for (key in diceParticles) {
                sum += diceParticles[key]
            }
            switch (winner) {
                case "big":
                    content += sum + "点大"
                    break
                case "small":
                    content += sum + "点小"
                    break
                case "leopard":
                    content += "豹子通杀"
                    break
            }
            $("#redPacketIGot").text(diceParticles + content);
        }
    },

    bet: function (oId) {
        Util.alert("<div class=\"form fn__flex-column\">\n" +
            "<label id=\"betRadio\">\n" +
            "  <input type=\"radio\" name=\"bet\" value=\"big\" checked>大\n" +
            "  <input type=\"radio\" name=\"bet\" value=\"small\">小\n" +
            "  <input type=\"radio\" name=\"bet\" value=\"leopard\">豹子\n" +
            "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">筹码</div>\n" +
            "  <div class=\"fn-hr5 fn__5\"></div>\n" +
            "  <input type=\"number\" min=\"32\" max=\"100\" required=\"\" value=\"32\" id=\"chipsLabel\" name='chips' onkeypress=\"return(/[\\d]/.test(String.fromCharCode(event.keyCode)))\">\n" +
            "</label>\n" +
            "<div class=\"fn__flex\" style=\"margin-top: 15px\">\n" +
            "  <button class=\"btn btn--confirm\" onclick=\"ChatRoom.unpackRedPacket(" + oId + ")\;Util.clearAlert()\">押注</button>\n" +
            "</div>\n" +
            "</div>", '买定离手');

        $("#chipsLabel").on('change', function () {
            if ($("#chipsLabel").val() > 100) {
                $("#chipsLabel").val("100");
            }
            if ($("#chipsLabel").val() <= 32) {
                $("#chipsLabel").val("32");
            }
        });
    },


    selectGesture: function (oId) {
        Util.alert(`<div class="form fn__flex-column select-center">
<div>
   <img width="50" src="${Label.staticServePath}/images/redpacket/gesture/rock.png" onclick="ChatRoom.unpackRedPacket(${oId},'0');Util.clearAlert()"/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
   <img width="50" src="${Label.staticServePath}/images/redpacket/gesture/scissors.png" onclick="ChatRoom.unpackRedPacket(${oId},'1');Util.clearAlert()"/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
   <img width="50" src="${Label.staticServePath}/images/redpacket/gesture/paper.png" onclick="ChatRoom.unpackRedPacket(${oId},'2');Util.clearAlert()"/>
</div>
</div>`, '出拳');
    },
    /**
     * 拆开红包
     */
    unpackRedPacket: function (oId, gesture) {
        if (undefined === gesture || null === gesture) {
            gesture = "0"
        }
        redPacketMap.set(oId, $($('#chatroom' + oId).find('.hongbao__item').find('div')[1]).html());
        $($('#chatroom' + oId).find('.hongbao__item').find('div')[1]).html('红包在路上啦~<br><b>请稍等...</b>');
        $('#chatroom' + oId).css("pointer-events", "none");
        $.ajax({
            url: Label.servePath + "/chat-room/red-packet/open",
            async: true,
            method: "POST",
            data: JSON.stringify({
                oId: oId,
                gesture: gesture,
                dice: {
                    bet: $("#betRadio>input[name=bet]:checked").val(),
                    chips: $("#betRadio>input[name=chips]").val()
                }
            }),
            success: function (result) {
                setTimeout(function () {
                    $($('#chatroom' + oId).find('.hongbao__item').find('div')[1]).html(redPacketMap.get(oId));
                    $('#chatroom' + oId).css("pointer-events", "");
                }, 300);
                if (result.code !== -1) {
                    let iGot = "抢红包人数较多，加载中...";
                    let gesture = "";
                    if (result.info.gesture !== undefined) {
                        gesture = (Label.currentUser === result.info.userName ? "你" : result.info.userName) + "出拳:  " + ["石头", "剪刀", "布"][result.info.gesture]
                    }
                    Util.alert(`<style>.dialog-header-bg {border-radius: 4px 4px 0 0; background-color: rgb(210, 63, 49, .8); color: rgb(255, 255, 255);}.dialog-main {height: 456px;overflow: auto;}</style><div class="fn-hr5"></div>
<div class="ft__center">
    <div class="fn__flex-inline">
        <img class="avatar avatar--small" src="${result.info.userAvatarURL48}" style="background-image: none; background-color: transparent; width: 20px; height: 20px; margin-right: 0;">
        <div class="fn__space5"></div>
        <a href="${Label.servePath}/member/${result.info.userName}">${result.info.userName}</a>'s 红包
    </div>
    <div class="fn-hr5"></div>
${gesture ? `<div class="ft__smaller ft__fade">${gesture}</div>` : ""}    <div id = "msg" class="ft__smaller ft__fade">
${result.info.msg}
    </div>
    <div class="hongbao__count" id='redPacketIGot'>${iGot}</div>
    <div class="ft__smaller ft__fade">总计 ${result.info.got}/${result.info.count}</div>
</div>
<div class="list"><ul id="redPacketList">
</ul></div>`, "红包");
                    ChatRoom.renderRedPacket(result.who, result.info.count, result.info.got, result.recivers, result.diceRet, result.info.userName)
                    if (result.info.count === result.info.got) {
                        $("#chatroom" + oId).find(".hongbao__item").css("opacity", ".36").attr('onclick', "ChatRoom.unpackRedPacket(" + oId + ")");
                        if (!$("#chatroom" + oId).find(".hongbao__item").hasClass('opened')) {
                            $("#chatroom" + oId).find(".hongbao__item").addClass('opened')
                        }
                        if (result.dice === true) {
                            $("#chatroom" + oId).find(".redPacketDesc").html("已开盘");
                        } else {
                            $("#chatroom" + oId).find(".redPacketDesc").html("已经被抢光啦");
                        }
                    }
                } else {
                    Util.alert(result.msg);
                }
            },
            error: function (result) {
                Util.alert(result.msg);
            }
        });
    },
    /**
     * 消息+1
     */
    plusOne: function () {
        ChatRoom.editor.setValue(Label.latestMessage);
        ChatRoom.send();
    },
    /**
     * 渲染聊天室消息
     */
    renderMsg: function (data, more) {
        if (ChatRoom.shileds.indexOf(data.userName) > 0) {
            // 被屏蔽了,
            return;
        }
        // 没屏蔽的情况，判断是否需要捕获
        let userName = data.userName;
        let newContent = data.content;
        let newMd = data.md ? data.md : '';
        let robotAvatar = data.userAvatarURL;
        // 看看是否有备注
        let remark = ChatRoom.remarkList[data.userOId];
        if ((!more) && catchUsers.includes(userName) && newContent.indexOf("\"msgType\":\"redPacket\"") == -1 && newContent.indexOf("\"msgType\":\"music\"") == -1 && newContent.indexOf("\"msgType\":\"weather\"") == -1) {
            let robotDom = '<div class="robot-msg-item"><div class="avatar" style="background-image: url(' + robotAvatar + ')"></div><div class="robot-msg-content"><div class="robot-username"><p>' + userName + '</p></div> ' + newContent + ' <div class="fn__right" style="margin-top: 5px; font-size: 10px;">' + data.time + '</div></div></div>';
            ChatRoom.addRobotMsg(robotDom);
        } else if ((!more) && $('#catch-word').prop('checked') && newContent.indexOf("\"msgType\":\"redPacket\"") == -1 && (newMd.startsWith("鸽 ") || newMd.startsWith("小冰 ") || newMd.startsWith("凌 ") || newMd.startsWith("ida "))) {
            let robotDom = '<div class="robot-msg-item"><div class="avatar" style="background-image: url(' + robotAvatar + ')"></div><div class="robot-msg-content"><div class="robot-username"><p>' + userName + '</p></div> ' + newContent + ' <div class="fn__right" style="margin-top: 5px; font-size: 10px;">' + data.time + '</div></div></div>';
            ChatRoom.addRobotMsg(robotDom);
        } else {
            let isRedPacket = false;
            let isWeather = false;
            let isMusic = false;
            let isPlusOne = Label.latestMessage === data.md;
            try {
                let msgJSON = $.parseJSON(data.content.replace("<p>", "").replace("</p>", ""));
                if (msgJSON.msgType === "redPacket") {
                    isRedPacket = true;
                    let type = "未知类型红包";
                    let onclick = 'ChatRoom.unpackRedPacket(\'' + data.oId + '\')';
                    switch (msgJSON.type) {
                        case "random":
                            type = "拼手气红包";
                            break;
                        case "average":
                            type = "普通红包";
                            break;
                        case "specify":
                            type = "专属红包";
                            break;
                        case "heartbeat":
                            type = "心跳红包 (慎抢)";
                            break;
                        case "rockPaperScissors":
                            type = "石头剪刀布红包";
                            if (msgJSON.senderId != Label.currentUserId) {
                                onclick = '';
                            }
                            break;
                        case "dice":
                            type = "摇骰子";
                            if (msgJSON.senderId !== Label.currentUserId) {
                                let dup = false
                                for (idx in msgJSON.who) {
                                    if (msgJSON.who[idx].userId === Label.currentUserId) {
                                        dup = true
                                        break
                                    }
                                }
                                if (!dup) {
                                    onclick = 'ChatRoom.bet(\'' + data.oId + '\')';
                                }
                            }
                            break
                    }
                    if (Number(msgJSON.count) === Number(msgJSON.got)) {
                        let content = "已经被抢光啦"
                        if (msgJSON.type === 'dice') {
                            content = "已开盘"
                        }
                        data.content = '' +
                            '<div style="opacity: .36;" class="hongbao__item fn__flex-inline" onclick="ChatRoom.unpackRedPacket(\'' + data.oId + '\')">\n' +
                            '    <svg class="ft__red hongbao__icon">\n' +
                            '        <use xlink:href="#redPacketIcon"></use>\n' +
                            '    </svg>\n' +
                            '    <div>\n' +
                            '        <div>' + msgJSON.msg + '<br><b>' + type + '</b></div>\n' +
                            '        <div><svg style="vertical-align: -2px; width: 13px; height: 13px"><use xlink:href="#coin"></use></svg> ' + msgJSON.money + '</div>\n' +
                            '        <div class="ft__smaller ft__fade redPacketDesc">\n' +
                            content + '\n' +
                            '        </div>\n' +
                            '    </div>\n' +
                            '</div>';
                    } else {
                        if (msgJSON.type === 'rockPaperScissors' && msgJSON.senderId != Label.currentUserId) {
                            data.content = '' +
                                '<div class="hongbao__item fn__flex-inline" >\n' +
                                '    <div class="hongbao__finger_guessing">\n' +
                                '        <div class="hongbao__finger_guessing_icon" onclick="event.stopPropagation();Util.clearAlert();ChatRoom.unpackRedPacket(' + data.oId + ',\'0\');"></div>\n' +
                                '        <div class="hongbao__finger_guessing_icon" onclick="event.stopPropagation();Util.clearAlert();ChatRoom.unpackRedPacket(' + data.oId + ',\'1\');"></div>\n' +
                                '        <div class="hongbao__finger_guessing_icon" onclick="event.stopPropagation();Util.clearAlert();ChatRoom.unpackRedPacket(' + data.oId + ',\'2\');"></div>\n' +
                                '    </div>\n' +
                                '    <svg class="ft__red hongbao__icon">\n' +
                                '        <use xlink:href="#redPacketIcon"></use>\n' +
                                '    </svg>\n' +
                                '    <div>\n' +
                                '        <div>' + msgJSON.msg + '<br><b>' + type + '</b></div>\n' +
                                '        <div><svg style="vertical-align: -2px; width: 13px; height: 13px"><use xlink:href="#coin"></use></svg> ' + msgJSON.money + '</div>\n' +
                                '        <div class="ft__smaller ft__fade redPacketDesc">\n' +
                                '        </div>\n' +
                                '    </div>\n' +
                                '</div>';
                        } else {
                            data.content = '' +
                                '<div class="hongbao__item fn__flex-inline" onclick="' + onclick + '">\n' +
                                '    <svg class="ft__red hongbao__icon">\n' +
                                '        <use xlink:href="#redPacketIcon"></use>\n' +
                                '    </svg>\n' +
                                '    <div>\n' +
                                '        <div>' + msgJSON.msg + '<br><b>' + type + '</b></div>\n' +
                                '        <div><svg style="vertical-align: -2px; width: 13px; height: 13px"><use xlink:href="#coin"></use></svg> ' + msgJSON.money + '</div>\n' +
                                '        <div class="ft__smaller ft__fade redPacketDesc">\n' +
                                '        </div>\n' +
                                '    </div>\n' +
                                '</div>';
                        }
                    }
                } else if (msgJSON.msgType === "weather") {
                    isWeather = true;
                    data.content = '<div id="weather_' + data.oId + '" style="width: 300px;height:280px;" data-date="' + msgJSON.date + '" data-code="' + msgJSON.weatherCode + '" data-max="' + msgJSON.max + '" data-min="' + msgJSON.min + '" data-t="' + msgJSON.t + '" data-st="' + msgJSON.st + '"></div>';
                } else if (msgJSON.msgType === 'music') {
                    isMusic = true;
                    data.content = '<div class="music-player">' +
                        '<img class="music-player-img" src="' + (msgJSON.coverURL === "" ? Label.servePath + "/images/music/cat.gif" : msgJSON.coverURL) + '" />' +
                        '<div class="music-player-box"><div class="music-player-title">' + msgJSON.title + '</div>' +
                        '<div class="music-player-controller"  data-source="' + msgJSON.source + '" data-cover="' + msgJSON.coverURL +
                        '" data-title="' + msgJSON.title + '" data-from="' + msgJSON.from + '">' +
                        '<span onclick="ChatRoom.playSound.add(this)">加入列表</span>' +
                        ' | ' +
                        '<span onclick="ChatRoom.playSound.play(this)">立即播放</span>' +
                        '</div></div>' +
                        '</div>'
                }
            } catch (err) {
            }
            let meTag1 = "";
            let meTag2 = "";
            if (data.userNickname !== undefined && data.userNickname !== "") {
                data.userNickname = data.userNickname + " (" + data.userName + ")"
            } else {
                data.userNickname = data.userName;
            }
            if (Label.currentUser === data.userName) {
                meTag1 = " chats__item--me";
                meTag2 = "<a onclick=\"ChatRoom.revoke(" + data.oId + ")\" class=\"item\">撤回</a>\n";
            }
            // isAdmin
            if (Label.level3Permitted) {
                meTag2 = "<a onclick=\"ChatRoom.revoke(" + data.oId + ")\" class=\"item\"><svg><use xlink:href=\"#administration\"></use></svg> 撤回</a>\n";
            }
            try {
                // 判断是否可以收藏为表情包
                let emojiContent = data.content.replace("<p>", "").replace("</p>", "");
                let emojiDom = Util.parseDom(emojiContent);
                let canCollect = false;
                let srcs = "";
                let count = 0;
                for (let i = 0; i < emojiDom.length; i++) {
                    let cur = emojiDom.item(i);
                    if (cur.src !== undefined) {
                        canCollect = true;
                        if (count !== 0) {
                            srcs += ",";
                        }
                        srcs += "\'" + cur.src + "\'";
                        count++;
                    }
                }
                if (canCollect) {
                    meTag2 += "<a onclick=\"ChatRoom.addEmoji(" + srcs + ")\" class=\"item\">一键收藏表情</a>";
                }
            } catch (err) {
            }
            let isAdmin = [""].includes(data.userOId.toString());
            let newHTML = '<div class="fn-none">';
            newHTML += '<div id="chatroom' + data.oId + '" class="fn__flex chats__item' + meTag1 + '">\n' +
                '    <a href="/member/' + data.userName + '" style="height: 38px">\n' +
                '        <div class="avatar tooltipped__user" aria-label="' + data.userName + '" style="background-image: url(\'' + data.userAvatarURL + '\');"></div>\n' +
                '    </a>\n' +
                '    <div class="chats__content">\n' +
                '        <div class="chats__arrow"></div>\n';

            // let display = Label.currentUser === data.userName && !isPlusOne ? 'display: none;' : ''
            let display = '';
            newHTML += '<div id="userName" class="ft__fade ft__smaller" style="' + display + 'padding-bottom: 3px;border-bottom: 1px solid #eee">\n' +
                '    <span class="' + (isAdmin ? "ft-admin-user" : "ft-gray") + '">' + (remark != null ? (remark + '-') : '') + data.userNickname + '</span>&nbsp;\n';
            if (data.sysMetal !== undefined && data.sysMetal !== "") {
                let list = JSON.parse(data.sysMetal).list;
                if (list !== undefined) {
                    for (let i = 0; i < list.length; i++) {
                        let m = list[i];
                        newHTML += "<img title='" + m.name + " - " + m.description + "' src='" + Util.genMiniMetal(m.attr) + "'/>";
                    }
                }
            }
            newHTML += '</div>';

            newHTML += '        <div class="vditor-reset ft__smaller ' + Label.chatRoomPictureStatus + '" style="margin-top: 3px">\n' +
                '            ' + ChatRoom.filterContent(data.content ,isAdmin) + '\n' +
                '        </div>\n' +
                '        <div class="ft__smaller ft__fade fn__right date-bar">\n' +
                '            ' + data.time + '\n' +
                '                <span class="fn__space5"></span>\n';
            // 客户端标识
            if (data.client !== undefined && data.client !== '') {
                let client = data.client.split('/')[0];
                let version = data.client.split('/')[1];
                switch (client) {
                    case 'Web':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-fish"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Bird':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-bird"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Dart':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-dart"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'ElvesOnline':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-moon"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Mobile':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-mobile"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Windows':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-windows"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Linux':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-linux"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'IceNet':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-icenet"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Extension':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-extension"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Edge':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-edge"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Other':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-other"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'PC':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-pc2"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'iOS':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-apple"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'macOS':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-apple"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Android':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-apk"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Chrome':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-chrome"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'VSCode':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-vscode"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'IDEA':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-idea"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Python':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-python"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Golang':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-golang"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Harmony':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-harmony"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'Rust':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-rust"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'CLI':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-cli"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                    case 'uTools':
                        newHTML += '<span class="tooltipped tooltipped-n" aria-label="' + client + ' ' + version + '">' +
                            '<svg style="vertical-align: -3px;"><use xlink:href="#ic-utools"></use></svg>' +
                            '</span>';
                        newHTML += '<span class="fn__space5"></span>\n';
                        break;
                }
            }
            // === 客户端标识
            if (!isRedPacket && !isWeather && !isMusic) {
                newHTML += '                <details class="details action__item fn__flex-center">\n' +
                    '                    <summary>\n' +
                    '                        ···\n' +
                    '                    </summary>\n' +
                    '                    <details-menu class="fn__layer">\n' +
                    '                        <a onclick=\"ChatRoom.at(\'' + data.userName + '\', \'' + data.oId + '\', true)\" class="item">@' + data.userName + '</a>\n' +
                    '                        <a onclick=\"ChatRoom.at(\'' + data.userName + '\', \'' + data.oId + '\', false)\" class="item">引用</a>\n' +
                    '                        <a onclick=\"ChatRoom.repeat(\'' + data.oId + '\')\" class="item">复读机</a>\n' +
                    '                        <a onclick=\"ChatRoom.remark(\'' + data.userOId + '\', \'' + data.userName + '\')\" class="item">备注</a>\n' +
                    '                        <a onclick=\"ChatRoom.block(\'' + data.userName + '\')\" class="item">屏蔽该用户发言</a>\n' +
                    '                        <a onclick=\"ChatRoom.report(\'' + data.oId + '\')\" class="item"><svg><use xlink:href="#icon-report"></use></svg> 一键举报</a>\n' +
                    meTag2 +
                    '                    </details-menu>\n' +
                    '                </details>\n';
            } else {
                newHTML += '                <details class="details action__item fn__flex-center">\n' +
                    '                    <summary>\n' +
                    '                        ···\n' +
                    '                    </summary>\n' +
                    '                    <details-menu class="fn__layer">\n' +
                    '                        <a onclick=\"ChatRoom.at(\'' + data.userName + '\', \'' + data.oId + '\', true)\" class="item">@' + data.userName + '</a>\n' +
                    '                        <a onclick=\"ChatRoom.report(\'' + data.oId + '\')\" class="item"><svg><use xlink:href="#icon-report"></use></svg> 一键举报</a>\n' +
                    '                    </details-menu>\n' +
                    '                </details>\n';
            }
            newHTML += '        </div>\n' +
                '    </div>\n' +
                '</div></div>';
            if (more) {
                $('#chats').append(newHTML);
                let $fn = $('#chats>div.fn-none');
                $fn.show();
                $fn.removeClass("fn-none");
            }
            // 堆叠复读机消息
            else if (isPlusOne) {
                let plusN = ++Label.plusN;
                if (plusN === 1) {
                    let stackedHtml = "<div id='stacked' class='fn__flex' style='position:relative;display:none;'>" +
                        "<span id='plusOne' onclick='ChatRoom.plusOne()' style='display:block;margin-left: 20px'><svg style='width: 30px; height: 20px; cursor: pointer;'><use xlink:href='#plusOneIcon'></use></svg></span>" +
                        "</div>"
                    $('#chats').prepend(stackedHtml);
                    let latest = $('#chats>div.latest');
                    $('#stacked').prepend(latest);
                    latest.find('#userName').show();
                    latest.removeClass('latest');
                }
                let $stacked = $('#stacked');
                if (plusN !== 1) {
                    $stacked.fadeOut(100);
                }
                setTimeout(function () {
                    $stacked.append(newHTML);
                    $stacked.height($stacked.height() + 27 + 'px')

                    let $fn = $('#stacked>div.fn-none');
                    $fn.show();
                    $fn.css('left', plusN * 9 + 'px');
                    $fn.css('top', plusN * 27 + 'px');
                    $fn.css('position', 'absolute');
                    $fn.find('.chats__content').css('background-color', plusN % 2 === 0 ? 'rgb(240 245 254)' : 'rgb(245 245 245)');
                    $fn.removeClass("fn-none");

                    $stacked.fadeIn(200);
                }, 100);
            } else {
                $('#plusOne').remove();
                if (data.md) {
                    Label.latestMessage = data.md;
                    Label.plusN = 0;
                }
                let $chats = $('#chats');
                $chats.find('.latest').removeClass('latest');
                $chats.prepend(newHTML);
                let $fn = $('#chats>div.fn-none');
                $fn.slideDown(200);
                $fn.addClass("latest");
                $fn.removeClass("fn-none");
            }
            if (isWeather) {
                ChatRoom.initNewWeather(data.oId);
            }
        }
    },
    /**
     * 天气卡片渲染
     */
    initNewWeather: function (oId) {
        let chartDom = document.getElementById('weather_' + oId);
        let myChart = echarts.init(chartDom, null, {
            renderer: 'svg'
        });
        let option;
        let CodeMap = {
            CLEAR_DAY: "晴",
            CLEAR_NIGHT: "晴",
            PARTLY_CLOUDY_DAY: "多云 ",
            PARTLY_CLOUDY_NIGHT: "多云",
            CLOUDY: "阴",
            LIGHT_HAZE: "轻度雾霾",
            MODERATE_HAZE: "中度雾霾",
            HEAVY_HAZE: "重度雾霾",
            LIGHT_RAIN: "小雨",
            MODERATE_RAIN: "中雨",
            HEAVY_RAIN: "大雨",
            STORM_RAIN: "暴雨",
            FOG: "雾",
            LIGHT_SNOW: "小雪",
            MODERATE_SNOW: "中雪",
            HEAVY_SNOW: "大雪",
            STORM_SNOW: "暴雪",
            DUST: "浮尘",
            SAND: "沙尘",
            WIND: "大风",
        }
        let searchObj = {}

        searchObj.date = chartDom.dataset.date.split(",");
        searchObj.max = chartDom.dataset.max.split(",");
        searchObj.min = chartDom.dataset.min.split(",");
        searchObj.weatherCode = chartDom.dataset.code.split(",");
        searchObj.weatherName = [];
        searchObj.t = chartDom.dataset.t;
        searchObj.st = chartDom.dataset.st;
        for (var i = 0; i < searchObj.weatherCode.length; i++) {
            searchObj.weatherName.push(CodeMap[searchObj.weatherCode[i]])
        }
        option = {
            title: {
                text: searchObj.t,
                subtext: searchObj.st,
                left: "center",
                top: "top",
                textStyle: {
                    fontSize: 24
                },
                subtextStyle: {
                    fontSize: 14
                }
            },
            grid: {
                show: true,
                backgroundColor: 'transparent',
                opacity: 0.3,
                borderWidth: '0',
                top: '200',
                bottom: '50'
            },
            tooltip: {
                trigger: 'axis'
            },
            legend: {
                show: false
            },
            xAxis: [
                // 日期
                {
                    type: 'category',
                    boundaryGap: false,
                    position: 'top',
                    offset: 100,
                    zlevel: 100,
                    axisLine: {
                        show: false
                    },
                    axisTick: {
                        show: false
                    },
                    axisLabel: {
                        interval: 0,
                        formatter: ['{a|{value}}'].join('\n'),
                        rich: {
                            a: {
                                fontSize: 14
                            }
                        }
                    },
                    nameTextStyle: {},
                    data: searchObj.date
                },
                // 天气图标
                {
                    type: 'category',
                    boundaryGap: false,
                    position: 'top',
                    offset: 20,
                    zlevel: 100,
                    axisLine: {
                        show: false
                    },
                    axisTick: {
                        show: false
                    },
                    axisLabel: {
                        interval: 0,
                        formatter: function (value, index) {
                            return '{' + index + '| }\n{b|' + value + '}';
                        },
                        rich: {
                            0: {
                                backgroundColor: {
                                    image: Label.servePath + `/images/weather/svg/${searchObj.weatherCode[0]}.svg`
                                },
                                height: 40,
                                width: 40
                            },
                            1: {
                                backgroundColor: {
                                    image: Label.servePath + `/images/weather/svg/${searchObj.weatherCode[1]}.svg`
                                },
                                height: 40,
                                width: 40
                            },
                            2: {
                                backgroundColor: {
                                    image: Label.servePath + `/images/weather/svg/${searchObj.weatherCode[2]}.svg`
                                },
                                height: 40,
                                width: 40
                            },
                            3: {
                                backgroundColor: {
                                    image: Label.servePath + `/images/weather/svg/${searchObj.weatherCode[3]}.svg`
                                },
                                height: 40,
                                width: 40
                            },
                            4: {
                                backgroundColor: {
                                    image: Label.servePath + `/images/weather/svg/${searchObj.weatherCode[4]}.svg`
                                },
                                height: 40,
                                width: 40
                            },
                            b: {
                                fontSize: 12,
                                lineHeight: 30,
                                height: 20
                            }
                        }
                    },
                    nameTextStyle: {
                        fontWeight: 'bold',
                        fontSize: 19
                    },
                    data: searchObj.weatherName
                }
            ],
            yAxis: {
                type: 'value',
                show: false,
                axisLabel: {
                    formatter: '{value} °C',
                    color: 'white'
                }
            },
            series: [{
                name: '最高气温',
                type: 'line',
                data: searchObj.max,
                symbol: 'emptyCircle',
                symbolSize: 10,
                showSymbol: true,
                smooth: true,
                itemStyle: {
                    normal: {
                        color: '#C95843'
                    }
                },
                label: {
                    show: true,
                    position: 'top',
                    formatter: '{c} °C'
                },
                lineStyle: {
                    width: 1
                },
                areaStyle: {
                    opacity: 1,
                    color: 'transparent'
                }
            }, {
                name: '最低气温',
                type: 'line',
                data: searchObj.min,
                symbol: 'emptyCircle',
                symbolSize: 10,
                showSymbol: true,
                smooth: true,
                itemStyle: {
                    normal: {
                        color: 'blue'
                    }
                },
                label: {
                    show: true,
                    position: 'bottom',
                    formatter: '{c} °C'
                },
                lineStyle: {
                    width: 1
                },
                areaStyle: {
                    opacity: 1,
                    color: 'transparent'
                }
            }]
        };
        option && myChart.setOption(option);
    },
    /**
     * 音乐卡片
     * [list] 播放列表
     * [mode] 播放方式 0 列表循环 1 随机播放
     * [playing] 当前是否在播放
     * [isShow] 是否显示播放器
     * [index] 当前播放的下标
     * */
    playSound: {
        list: [],
        mode: 0,
        playing: false,
        isShow: false,
        index: 0,
        ele: null,
        timer: null,
        isShowList: false,
        isSHowVoice: false,
        init() {
            let radioEle = document.querySelector('#music-core-item');
            let playIcon = document.querySelector('.music-play-icon');
            let playBox = document.querySelector('.music-box');
            let currentEle = document.querySelector('.music-current');
            let durationEle = document.querySelector('.music-duration');
            let titleEle = document.querySelector('.music-title');
            let coverEle = document.querySelector('.music-img-item');
            this.ele = radioEle;
            radioEle.addEventListener('ended', () => {
                // console.log('播放完成');
                this.playing = false;
                clearInterval(this.timer);
                playIcon.src = Label.servePath + '/images/music/circle_play.png';
                playBox.classList.remove('playing');
                this.autoNext();
            });
            radioEle.addEventListener('play', () => {
                // console.log('播放');
                playIcon.src = Label.servePath + '/images/music/circle_pause.png';
                playBox.classList.add('playing');
                this.timer = setInterval(() => {
                    currentEle.innerHTML = this.secondsToTime(this.ele.currentTime);
                });
            });
            radioEle.addEventListener('pause', () => {
                // console.log('暂停');
                clearInterval(this.timer);
                playIcon.src = Label.servePath + '/images/music/circle_play.png';
                playBox.classList.remove('playing');
            });
            radioEle.addEventListener('canplay', () => {
                // console.log('加载完成');
                currentEle.innerHTML = this.secondsToTime(this.ele.currentTime);
                durationEle.innerHTML = this.secondsToTime(this.ele.duration);
                titleEle.innerHTML = this.list[this.index].title;
                let cover = this.list[this.index].cover;
                coverEle.src = cover === '' ? Label.servePath + '/images/music/cat.gif' : cover;
            });
        },
        secondsToTime(time) {
            time = parseInt(time);
            let mm = 0, ss = 0;
            if (time > 59) {
                mm = Math.floor(time / 60);
                ss = time % 60;
                return (mm > 9 ? mm : '0' + mm) + ":" + (ss > 9 ? ss : '0' + ss);
            } else {
                ss = time;
                return "00:" + (ss > 9 ? ss : '0' + ss);
            }
        },
        toggleMode() {
            let modeIcon = document.querySelector('.music-mode-icon');
            if (this.mode === 0) {
                this.mode = 1;
                modeIcon.src = Label.servePath + '/images/music/shuffle.png';
                modeIcon.setAttribute('alt', '顺序播放');
            } else {
                this.mode = 0;
                modeIcon.src = Label.servePath + '/images/music/repeat.png';
                modeIcon.setAttribute('alt', '随机播放');
            }
        },
        add(e, showToast = true) {
            let music = e.parentElement.dataset;
            if (music.source.startsWith('http://music.163.com/song') || music.source.startsWith('https://music.163.com/song')) {
                let sourceEle = music.source.split('=');
                music.source = sourceEle[sourceEle.length - 1];
            }
            let idx = this.list.findIndex(e => e.source === music.source);
            if (idx !== -1) {
                this.index = idx;
                return;
            }
            this.list.push(music);
            this.renderList();
            showToast && Util.notice("success", 2000, "已加入播放列表。");
        },
        remove(idx) {
            this.list.splice(idx, 1);
            this.renderList();
            Util.notice("success", 2000, "已移出播放列表。");
        },
        renderList() {
            let listEle = document.querySelector('.music-list-box');
            let list = "";
            for (let i = 0; i < this.list.length; i++) {
                let item = this.list[i];
                list += '<div class="music-list-item">' +
                    '        <img src="' + item.cover + '" alt="">' +
                    '        <div class="music-list-title">' + item.title + '</div>' +
                    '        <div class="music-list-controller">' +
                    '            <span style="color: #198cff" data-i="' + i + '" onclick="ChatRoom.playSound.playIndex(' + i + ')">播放</span>' +
                    '            <span style="color: red" data-i="' + i + '" onclick="ChatRoom.playSound.remove(' + i + ')">移除</span>' +
                    '        </div>' +
                    '    </div>'
            }
            listEle.innerHTML = list;
        },
        next() {
            if (this.list.length === 0) return;
            this.index += 1;
            if (this.index >= this.list.length) {
                this.index = 0;
            }
            this.playIndex(this.index);
        },
        prev() {
            if (this.list.length === 0) return;
            this.index -= 1;
            if (this.index < 0) {
                this.index = this.list.length - 1;
            }
            this.playIndex(this.index);
        },
        play(e) {
            let music = e.parentElement.dataset;
            this.add(e, false);
            if (music.source.startsWith('http://music.163.com/song') || music.source.startsWith('https://music.163.com/song')) {
                let sourceEle = music.source.split('=');
                music.source = sourceEle[sourceEle.length - 1];
            }
            //this.ele.src = music.source;
            let iframeBox = document.querySelector('.music-detail');
            iframeBox.innerHTML = '<iframe frameborder="no" border="0" marginwidth="0" marginheight="0" width="100%" height=86 src="//music.163.com/outchain/player?type=2&id=' + music.source + '&auto=1&height=66"></iframe>';
            this.playing = false;
            this.togglePlay();
            !this.isShow && this.show();
        },
        playIndex(idx) {
            //this.ele.src = this.list[idx].source;
            let iframeBox = document.querySelector('.music-detail');
            iframeBox.innerHTML = '<iframe frameborder="no" border="0" marginwidth="0" marginheight="0" width="100%" height=86 src="//music.163.com/outchain/player?type=2&id=' + this.list[idx].source + '&auto=1&height=66"></iframe>';
            this.playing = false;
            this.index = idx;
            this.togglePlay();
        },
        autoNext() {
            if (this.mode === 0) {
                this.next();
            } else {
                this.playIndex(Math.floor(Math.random() * this.list.length));
            }
        },
        togglePlay() {
            this.playing = !this.playing;
            // if(this.playing){
            //     this.ele.play();
            // }else{
            //     this.ele.pause();
            // }
        },
        hide() {
            this.isShow = false;
            let playEle = document.querySelector('#musicBox');
            let closeEle = document.querySelector('.music-close-icon');
            closeEle.src = Label.servePath + '/images/music/arrow_up.png';
            playEle.classList.remove('show');
            this.isShowList && this.toggleList();
        },
        show() {
            this.isShow = true;
            let playEle = document.querySelector('#musicBox');
            let closeEle = document.querySelector('.music-close-icon');
            closeEle.src = Label.servePath + '/images/music/arrow_down.png';
            playEle.classList.add('show');
        },
        toggleShow() {
            this.isShow ? this.hide() : this.show();
        },
        toggleList() {
            let listEle = document.querySelector('.music-list-box');
            this.isShowList = !this.isShowList;
            if (this.isShowList) {
                listEle.classList.add('show');
            } else {
                listEle.classList.remove('show');
            }
        },
        changeVoice(voice) {
            // console.log(voice.value);
            let volume = voice.value;
            let volumeEle = document.querySelector('.music-voice-icon');
            if (volume > 80) {
                volumeEle.src = Label.servePath + '/images/music/volume_3.png';
            } else if (volume > 30) {
                volumeEle.src = Label.servePath + '/images/music/volume_2.png';
            } else if (volume > 0) {
                volumeEle.src = Label.servePath + '/images/music/volume_1.png';
            } else {
                volumeEle.src = Label.servePath + '/images/music/volume_off.png';
            }
            this.ele.volume = volume / 100;
        }
    },
    /**
     * 看图插件dom
     */
    imgViewer: null,
    /**
     * 看图插件等待更新状态
     */
    imgWaitting: false,
    /**
     * 全屏看图插件渲染
     */
    imageViewer: function () {
        // console.log("新消息")
        //没有新图片就不重载
        if (this.imgViewer && $("div.vditor-reset.ft__smaller img:not(.ft__smaller,.emoji,.ext-emoji,*[src*='shield'],*[src*='/gen?'])").length === this.imgViewer.length)
            return
        // console.log("包含图片")
        this.imgViewer = this.imgViewer || new Viewer(document.querySelector('#chats'), {
            inline: false,
            className: "PWLimgViwer",
            filter: (img) => !img.parentElement.classList.contains("ft__smaller") && !img.parentElement.classList.contains("ext-emoji") && !img.classList.contains("emoji") && img.src.indexOf("shield") < 0 && img.src.indexOf("/gen?") < 0,
            title() {
                let ele = this.images[$(".PWLimgViwer .viewer-active").attr("data-index")];
                while (ele = ele.parentElement,
                    !ele.querySelector(".avatar")) ;
                return "From @" + ele.querySelector(".avatar").getAttribute("aria-label")
            }
        });
        const delayshow = function () {
            setTimeout(() => {
                    if (!ChatRoom.imgViewer.isShown) {
                        ChatRoom.imgWaitting = false;
                        // console.log("重载")
                        ChatRoom.imgViewer.update()
                    } else {
                        // console.log("等待")
                        delayshow()
                    }
                }
                , 1000)
            return true
        }
        // console.log("前", this.imgWaitting)
        this.imgWaitting = this.imgWaitting || delayshow()
        // console.log("后", this.imgWaitting)
    },

    /**
     * 渲染捕获消息
     * @param t
     */
    addRobotMsg: function (t, more) {
        // if (more) {
        //     $("#robotMsgList").append(t);
        // } else {
        //     $("#robotMsgList").prepend(t);
        // }
        $("#robotMsgList").prepend(t);
        // 当dom元素数量达到一定程度时，只保留最近的n条数据
        const n = 50;
        if ($(".robot-msg-item") && $(".robot-msg-item").length > n) {
            $('.robot-msg-item:gt(n-1)').remove();
        }
    },

    /**
     * 捕获用户被修改时
     * @param robotList
     */
    changeCatchUser: function (robotList) {
        if (robotList && robotList.length > 0) {
            let changeCatch = '<div class="robot-msg-item">' +
                '<div class="robot-msg-content">当前捕获用户:' + robotList + '</div>' +
                '</div></div>';
            $("#robotMsgList").prepend(changeCatch);
        } else {
            let changeCatch = '<div class="robot-msg-item">' +
                '<div class="robot-msg-content">当前不存在需要捕获的用户</div>' +
                '</div></div>';
            $("#robotMsgList").prepend(changeCatch);
        }
        window.localStorage['robot_list'] = robotList;
        catchUsers = robotList.split(",");
    },

    /**
     * 初始化用户捕获功能
     */
    initCatchUser: function () {
        // 判断页面是否满足用户开启捕获功能
        // var sideDom = document.getElementsByClassName('side')[0];
        // var chatDom = document.getElementsByClassName('chat-room')[0];
        // var needWidth = sideDom.offsetWidth + chatDom.offsetWidth + 300; // 最小宽度为300px
        // if (needWidth > window.innerWidth) {
        //     // 删除用户捕获相关的组件和按钮
        //     //$("#robotBtn").remove();
        //     //$("#robotBox").remove();
        // } else {
        //     // 自动调整css样式
        //     $("#robotMsgList").attr("style", "height:" + (window.innerHeight - 85 - 58) + "px");
        //     // $("#robotBox").attr("style", "width:" + ((window.innerWidth - chatDom.offsetWidth - sideDom.offsetWidth) / 2) + "px");
        //     // $(".robot-active").eq(0).attr("style", "height:" + (chatDomHeight - 25) + "px");
        //     // $("#robotBox").attr("style", "height:" + (chatDomHeight - 25) + "px");
        // }
        // 点击事件
        $("#robotBtn").click(function () {
            $("#robotBox").show(200),
                $("#robotBtn").hide(200),
                setTimeout(() => {
                        // 自动调整css样式，每次打开小窗，都要调整小窗高度，宽度目前固定300px;小窗用户概率性出现遮挡聊天室的情况
                        $("#robotBox").addClass("robot-active");
                        $("#robotBox").attr("style", "height:" + (window.innerHeight - 25 - 58) + "px");
                        $("#robotMsgList").attr("style", "height:" + (window.innerHeight - 85 - 58) + "px");
                    }
                    , 220)
        })
        $("#robotClose").click(function () {
            var e = $("#robotBox");
            setTimeout(() => {
                    $(".robot-chat-input").val(""),
                        $("#robotBox").hide(200),
                        $("#robotBtn").show(200)
                }
                , e.hasClass("robot-active") ? 420 : 1),
                e.removeClass("robot-active")
            e.css("height", "");
        })
        $("#robotMinimize").click(function () {
            $("#robotBox").toggleClass("robot-active")
        })
        $("#clearRobotMsg").click(function () {
            $(".robot-msg-item").remove();
        })
        $("#catch-word").click(function () {
            window.localStorage['catch-word-flag'] = $('#catch-word').prop('checked');
        })
        $("#changeCatchUsers").click(function () {
            Util.alert("" +
                "<div class=\"form fn__flex-column\">\n" +
                "<label>\n" +
                "  <div class=\"ft__smaller\" style=\"float: left\">将捕获的用户id填写到下方输入框；<br>多个用户id的情况用英文逗号隔开。</div>\n" +
                "  <div class=\"fn-hr5 fn__5\"></div>\n" +
                "  <input type=\"text\" id=\"robot-catch-user\">\n" +
                "</label>\n" +
                "<div class=\"fn-hr5\"></div>\n" +
                "<div class=\"fn__flex\" style=\"margin-top: 15px; justify-content: flex-end;\">\n" +
                "  <button class=\"btn btn--confirm\" onclick='ChatRoom.changeCatchUser($(\"#robot-catch-user\").val());Util.closeAlert();'>确认</button>\n" +
                "</div>\n" +
                "</div>" +
                "", "编辑捕获用户列表");
            $("#robot-catch-user").val(window.localStorage['robot_list'] ? window.localStorage['robot_list'] : '');
        })

        // 读取浏览器缓存，获取捕获的用户    和是否捕获关键字
        var robotList = window.localStorage['robot_list'] ? window.localStorage['robot_list'] : '';
        ChatRoom.changeCatchUser(robotList);
        let status = false;
        if (window.localStorage['catch-word-flag'] === 'true') {
            status = true;
        }
        $('#catch-word').prop('checked', status);
    },


    /**
     * 按时间加载头像挂件
     * */
    loadAvatarPendant: function () {
        let year = new Date().getFullYear();
        let month = (new Date().getMonth() + 1).toString();
        month = month[1] ? month : "0" + month;
        let day = new Date().getDate();
        let formatDate = `${year}-${month}-${day}`;
        let SpringFestivalDateList = {
            2022: ["2022-01-31", "2022-02-06"],
            2023: ["2023-01-21", "2023-01-27"],
            2024: ["2024-02-09", "2024-02-15"],
            2025: ["2025-01-28", "2025-02-03"],
            2026: ["2026-02-16", "2026-02-22"],
            2027: ["2027-02-06", "2027-02-12"],
        }
        let MidAutumnFestivalDateList = {
            2022: ["2022-09-10", "2022-09-12"],
            2023: ["2023-09-29", "2023-10-01"],
            2024: ["2024-09-17", "2024-09-19"],
            2025: ["2025-10-06", "2025-10-09"],
            2026: ["2026-09-25", "2026-09-27"],
            2027: ["2027-09-15", "2027-09-17"],
        }
        //  国庆头像挂件
        let chatRoom = document.querySelector('body')
        if (month == 10 && day <= 7) {
            chatRoom.classList.add('NationalDay')
            return;
        }
        //  圣诞节头像挂件
        if ((month == 12 && day >= 24) && (month == 12 && day <= 25)) {
            chatRoom.classList.add('Christmas')
            return;
        }
        let toDayTimes = new Date(formatDate).getTime();
        //  中秋头像挂件
        if (new Date(MidAutumnFestivalDateList[year][0]).getTime() <= toDayTimes && new Date(MidAutumnFestivalDateList[year][1]).getTime() >= toDayTimes) {
            chatRoom.classList.add('MidAutumnFestival')
            return;
        }
        //  春节头像挂件
        if (new Date(SpringFestivalDateList[year][0]).getTime() <= toDayTimes && new Date(SpringFestivalDateList[year][1]).getTime() >= toDayTimes) {
            chatRoom.classList.add('SpringFestival')
        }
    },

    isMobile: function () {
        var userAgentInfo = navigator.userAgent;
        var mobileAgents = ["Android", "iPhone", "SymbianOS", "Windows Phone", "iPad", "iPod"];
        var mobile_flag = false;
        //根据userAgent判断是否是手机
        for (var v = 0; v < mobileAgents.length; v++) {
            if (userAgentInfo.indexOf(mobileAgents[v]) > 0) {
                mobile_flag = true;
                break;
            }
        }
        var screen_width = window.screen.width;
        var screen_height = window.screen.height;
        //根据屏幕分辨率判断是否是手机
        if (screen_width > 325 && screen_height < 750) {
            mobile_flag = true;
        }
        return mobile_flag;
    },

    switchNode: function () {
        let text = "        <h3 style=\"margin: 0 0 10px; font-size: 18px; color: #333;\">请选择区服</h3>" +
            "        <div style=\"margin-bottom: 15px;\">";
        Label.node.avaliable.forEach(function (node) {
            text += "<button onclick='ChatRoom.connectNewNode(\"" + node.node + "?apiKey=" + Label.node.apiKey + "\", \"" + node.name + "\")' style=\"background-color: #fff; color: #007bff; border: 1px solid #007bff; padding: 10px 20px; border-radius: 4px; cursor: pointer; margin: 5px; font-size: 14px; font-weight: bold;\">" + node.name + " " + node.online + "人</button>";
        })
        text += "        </div>" +
            "        <p style=\"margin: 0; font-size: 14px; color: #666; line-height: 1.5;\">" +
            "            您切换的区服仅为本次生效，系统会自动将您分配至最流畅的区服。" +
            "        </p>" +
            "        <p style=\"margin: 10px 0 0; font-size: 14px; color: #666; line-height: 1.5;\">" +
            "            所有区服之间消息同步，完全互通。" +
            "        </p>"
        Util.alert(text);
    },

    connectNewNode: function (node, name) {
        Util.clearAlert();
        $('#nodeButton').html(`<svg style='vertical-align: -2px;'><use xlink:href="#server"></use></svg> ` + name);
        ChatRoomChannel.ws.close();
        ChatRoomChannel.init(node);
        setTimeout(function () {
            clearInterval(ChatRoomChannel.manual);
        }, 1000);
    }
}

