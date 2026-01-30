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
var Chat = {
    page: 1,
    toUser: '',
    init: function (to) {
        let reqRecentList;
        let toUser;
        if (to !== undefined) {
            // 打扫战场
            Chat.page = 1;
            try {
                Chat.ws.close();
            } catch (e) {
            }
            $("#chats").html("");
            $("#chatTo" + Chat.toUser).css("background-color", "");
            Chat.toUser = '';
            Chat.noMore = false;
            toUser = to;
        } else {
            toUser = getQueryVariable("toUser");
        }
        Chat.toUser = toUser;
        // URL删除toUser参数
        // let currenturl = window.location.href;
        // let newUrl = (currenturl.split("?"))[0];
        // history.pushState('', '', newUrl);
        // 加载最近聊天列表
        reqRecentList = $.ajax({
            url: Label.servePath + '/chat/get-list?apiKey=' + apiKey,
            type: 'GET',
            async: true,
            success: function (result) {
                if (0 === result.result) {
                    let data = result.data;
                    if (data.length !== 0) {
                        data.forEach((userData) => {
                            if (userData.receiverUserName === '文件传输助手') {
                                $("#fileTransferMsg").html(userData.preview);
                            } else {
                                if (userData.senderUserName != Label.currentUserName) {
                                    if ($("#chatTo" + userData.senderUserName).length <= 0) {
                                        Chat.addToMessageList(userData.senderUserName, userData.senderAvatar, userData.preview, userData.receiverOnlineFlag);
                                    }
                                } else {
                                    if ($("#chatTo" + userData.receiverUserName).length <= 0) {
                                        Chat.addToMessageList(userData.receiverUserName, userData.receiverAvatar, userData.preview, userData.receiverOnlineFlag);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        });

        $.when(reqRecentList).done(function () {
            if (toUser !== "") {
                // 加载用户
                if (toUser !== 'FileTransfer') {
                    var reqLoadUser = $.ajax({
                        url: Label.servePath + "/user/" + toUser,
                        type: "GET",
                        async: true,
                        success: function (result) {
                            if (result.code === -1) {
                                alert('指定的用户名不存在，请检查后重试！');
                                location.href = Label.servePath + '/chat';
                            }
                            if ($("#chatTo" + result.userName).length <= 0) {
                                Chat.addToMessageList(result.userName, result.userAvatarURL, "&nbsp;");
                            }
                            toUser = result.userName;
                            Chat.toUser = toUser;
                        }
                    });
                }
            }

            $.when(reqLoadUser).done(function () {
                if (toUser === "") {
                    // 未选定用户
                    $("#chatStatus").html('请在列表点选联系人，或直接发起聊天。<br><br>' +
                        '<input class="form" id="chatWithInput" placeholder="输入用户名">&nbsp;<button onclick="Chat.startAChat()">发起聊天</button>');
                    // 监听回车
                    $("#chatWithInput").keypress(function (e) {
                        if (e.which == 13) {
                            Chat.startAChat();
                        }
                    });
                    // 加载未读消息
                    $.ajax({
                        url: Label.servePath + '/chat/has-unread?apiKey=' + apiKey,
                        type: 'GET',
                        async: true,
                        success: function (result) {
                            let count = result.result;
                            let list = result.data;
                            list.forEach((data) => {
                                // 兼容后端返回字段：优先使用 senderUserName，没有则使用 receiverUserName（文件传输助手等）
                                const name = data.senderUserName || data.receiverUserName;
                                if (!name) return;
                                const $item = $("#chatTo" + name);
                                if ($item.length > 0) {
                                    // 设置高亮背景
                                    $item.css("background-color", "#fff4eb");
                                    // 将未读用户移动到列表顶部，方便点击
                                    $("#chatMessageList").prepend($item);
                                }
                            });
                        }
                    });
                } else {
                    // 已选定用户，获取第一页聊天信息
                    // 选中用户
                    $("#chatTo" + toUser).css("background-color", "#f1f1f1");
                    // 状态
                    $("#chatStatus").html('和 ' +
                        '<a href="' + Label.servePath + '/member/' + toUser + '">' + toUser + '</a> ' +
                        '聊天中');
                    // 显示按钮
                    $("#buttons").show();
                    // 显示翻页
                    $(".pagination__chat").show();
                    // 加载编辑器
                    Chat.editor = Util.newVditor({
                        id: 'messageContent',
                        cache: true,
                        preview: {
                            mode: 'editor',
                        },
                        resize: {
                            enable: true,
                            position: 'bottom',
                        },
                        height: 150,
                        placeholder: '说点什么吧，友善第一哦。',
                        toolbar: [
                            'emoji',
                            'link',
                            'upload',
                            'edit-mode',
                            {
                                name: 'more',
                                toolbar: [
                                    'insert-after',
                                    'fullscreen',
                                    'preview',
                                    'info',
                                    'help',
                                ],
                            },
                        ],
                        ctrlEnter: function () {
                            Chat.send();
                        }
                    });
                    // 连接WS
                    Chat.ws = new ReconnectingWebSocket(chatChannelURL + toUser);
                    Chat.ws.onopen = function () {
                        console.log("Connected to chat channel websocket.")
                    }
                    // 发心跳包
                    setInterval(function () {
                        //Chat.ws.send('p');
                    }, 1000 * 5)
                    Chat.ws.onmessage = function (evt) {
                        var data = JSON.parse(evt.data)
                        if (data.type === 'revoke') {
                            $("#chat" + data.data).remove();
                        } else {
                            if (data.code === -1) {
                                $('#chatContentTip').addClass('error').html('<ul><li>' + data.msg + '</li></ul>');
                            } else {
                                $('#chatContentTip').removeClass('error succ').html('')
                                let oId = data.oId;
                                let toId = data.toId;
                                let fromId = data.fromId;
                                let time = data.time;
                                let content = data.content;
                                let preview = data.preview.substring(0, 10);
                                let dot = data.preview.length > 10 ? "..." : "";
                                let user_session = data.user_session;
                                let senderUserName = data.senderUserName;
                                let senderAvatar = data.senderAvatar;
                                let receiverUserName = data.receiverUserName;
                                let receiverAvatar = data.receiverAvatar;
                                // 添加消息
                                if (senderUserName === Label.currentUserName) {
                                    // 我发送的
                                    Chat.addSelfMsg(oId, senderUserName, senderAvatar, content, time, false);
                                    $("#chatTo" + receiverUserName).find("span").html(preview + dot);
                                    // 新消息置顶
                                    let html = $("#chatTo" + receiverUserName).prop('outerHTML');
                                    $("#chatTo" + receiverUserName).remove();
                                    $("#chatMessageList").prepend(html);
                                    // 文件传输助手特殊管理
                                    if (toId === '1000000000086') {
                                        $("#chatToFileTransfer").find("span").html(preview + dot);
                                    }
                                } else {
                                    // 他发给我的
                                    Chat.addTargetMsg(oId, senderUserName, senderAvatar, content, time, false);
                                    $("#chatTo" + senderUserName).find("span").html(preview + dot);
                                    // 新消息置顶
                                    let html = $("#chatTo" + senderUserName).prop('outerHTML');
                                    $("#chatTo" + senderUserName).remove();
                                    $("#chatMessageList").prepend(html);
                                    // 用户已读
                                    $.ajax({
                                        url: Label.servePath + "/chat/mark-as-read?apiKey=" + apiKey + "&fromUser=" + Chat.toUser,
                                        type: "GET",
                                        async: true
                                    });
                                }
                            }
                        }
                    }
                    Chat.ws.onclose = function () {
                        console.log("Disconnected to chat channel websocket.")
                    }
                    Chat.ws.onerror = function (err) {
                        console.log('ERROR', err)
                    }
                    // 加载消息
                    Chat.loadMore();
                    // 监听滑动
                    $(window).scroll(
                        function () {
                            var scrollTop = $(this).scrollTop();
                            var scrollHeight = $(document).height();
                            var windowHeight = $(this).height();
                            if (scrollTop + windowHeight + 500 >= scrollHeight) {
                                Chat.loadMore();
                            }
                        }
                    );
                    // 用户已读
                    $.ajax({
                        url: Label.servePath + "/chat/mark-as-read?apiKey=" + apiKey + "&fromUser=" + Chat.toUser,
                        type: "GET",
                        async: true
                    });
                    // 加载未读消息
                    var loadUnread = $.ajax({
                        url: Label.servePath + '/chat/has-unread?apiKey=' + apiKey,
                        type: 'GET',
                        async: true,
                        success: function (result) {
                            let count = result.result;
                            let list = result.data;
                            list.forEach((data) => {
                                const name = data.senderUserName || data.receiverUserName;
                                if (!name) return;
                                if (name != to) {
                                    const $item = $("#chatTo" + name);
                                    if ($item.length > 0) {
                                        $item.css("background-color", "#fff4eb");
                                        $("#chatMessageList").prepend($item);
                                    }
                                }
                            });
                        }
                    });
                }
            });
        });
    },

    startAChat() {
        let input = $("#chatWithInput").val();
        if (input !== '') {
            Chat.init(input);
        }
    },

    noMore: false,
    moreLock: false,
    loadMore() {
        if (!Chat.moreLock) {
            Chat.moreLock = true;
            if (!Chat.noMore) {
                $.ajax({
                    url: Label.servePath + "/chat/get-message?apiKey=" + apiKey + "&toUser=" + Chat.toUser + "&page=" + Chat.page + "&pageSize=20",
                    type: "GET",
                    async: true,
                    success: function (result) {
                        try {
                            result.data.length;
                        } catch (e) {
                            Chat.noMore = true;
                        }
                        if (!Chat.noMore) {
                            Chat.page++;
                            result.data.forEach((data) => {
                                let oId = data.oId;
                                let toId = data.toId;
                                let fromId = data.fromId;
                                let time = data.time;
                                let content = data.content;
                                let preview = data.preview.substring(0, 10);
                                let dot = data.preview.length > 10 ? "..." : "";
                                let user_session = data.user_session;
                                let senderUserName = data.senderUserName;
                                let senderAvatar = data.senderAvatar;
                                let receiverUserName = data.receiverUserName;
                                let receiverAvatar = data.receiverAvatar;
                                // 添加消息
                                if (senderUserName === Label.currentUserName) {
                                    // 我发送的
                                    Chat.addSelfMsg(oId, senderUserName, senderAvatar, content, time, true);
                                } else {
                                    // 他发给我的
                                    Chat.addTargetMsg(oId, senderUserName, senderAvatar, content, time, true);
                                }
                            });
                        }
                    }
                });
            }
            Chat.moreLock = false;
        }
    },

    send() {
        let content = Chat.editor.getValue();
        if (content.length > 10240) {
            $('#chatContentTip').addClass('error').html('<ul><li>发送失败：超过 10240 字符，请修改后重试。</li></ul>');
        } else {
            Chat.ws.send(content);
            Chat.editor.setValue('');
        }
    },

    addToMessageList(userName, avatarURL, preview, isOnline) {
        let dot = preview.length > 7 ? "..." : "";
        let status = isOnline ? '<div style="color: green">[在线]</div>' : '<div style="color: rgba(149,149,149,0.3)">[离线]</div>';
        $("#chatMessageList").append('' +
            '<div class="module-panel" id="chatTo' + userName + '" style="padding: 10px 15px;cursor: pointer" onclick="Chat.init(\'' + userName + '\')"\n' +
            '    <nav class="home-menu">\n' +
            '        <div class="avatar"\n' +
            '             style="display: inline-block; background-image:url(' + avatarURL + ')">\n' +
            '        </div>\n' +
            '        <div style="display: inline-block; vertical-align: -12px;">\n' +
            '            ' + userName + '<br>\n' +
            '            <span style="color: #868888">' + preview.substring(0, 7) + dot + '</span>\n' +
            '        </div>\n' +
            '        <div style="float: right;display: inline-block; color: #868888">'+ status + '</div>\n'+
            '    </nav>\n' +
            '</div>');
        if ($("#messageListPanel").css("display") === "none") {
            $("#messageListPanel").show();
        }
    },

    addSelfMsg(oId, userName, avatarURL, content, time, reverse) {
        let m = '';
        try {
            // 判断是否可以收藏为表情包
            let emojiContent = content.replace("<p>", "").replace("</p>", "");
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
                m += "<a onclick=\"Chat.addEmoji(" + srcs + ")\" class=\"item\">一键收藏表情</a>";
            }
        } catch (err) {
        }
        m += '<a onclick=\"Chat.at(\'' + userName + '\', \'' + oId + '\')\" class="item">引用</a>\n';

        if (reverse === true) {
            $("#chats").append('' +
                '<div id="chat' + oId + '" class="fn__flex chats__item chats__item--me">\n' +
                '    <a href="' + Label.servePath + '/member/' + userName + '" style="height: 38px">\n' +
                '        <div class="avatar tooltipped__user" aria-label="' + userName + '"\n' +
                '             style="background-image: url(' + avatarURL + ');"></div>\n' +
                '    </a>\n' +
                '    <div class="chats__content">\n' +
                '        <div class="chats__arrow"></div>\n' +
                '        <div style="margin-top: 4px" class="vditor-reset ft__smaller ">\n' +
                '            ' + content + '\n' +
                '        </div>\n' +
                '        <div class="ft__smaller ft__fade fn__right date-bar">\n' +
                '            ' + time + '\n' +
                '        <span class="fn__space5"></span>' +
                '            <details class="details action__item fn__flex-center">\n' +
                '                <summary>\n' +
                '                    ···\n' +
                '                </summary>\n' +
                '                <details-menu class="fn__layer">\n' +
                '                    <a onclick="Chat.revoke(\'' + oId + '\')" class="item">撤回</a>\n' + m +
                '                </details-menu>\n' +
                '            </details>' +
                '        </div>' +
                '    </div>\n' +
                '</div>');
        } else {
            $("#chats").prepend('' +
                '<div id="chat' + oId + '" class="fn__flex chats__item chats__item--me">\n' +
                '    <a href="' + Label.servePath + '/member/' + userName + '" style="height: 38px">\n' +
                '        <div class="avatar tooltipped__user" aria-label="' + userName + '"\n' +
                '             style="background-image: url(' + avatarURL + ');"></div>\n' +
                '    </a>\n' +
                '    <div class="chats__content">\n' +
                '        <div class="chats__arrow"></div>\n' +
                '        <div style="margin-top: 4px" class="vditor-reset ft__smaller ">\n' +
                '            ' + content + '\n' +
                '        </div>\n' +
                '        <div class="ft__smaller ft__fade fn__right date-bar">\n' +
                '            ' + time + '\n' +
                '        <span class="fn__space5"></span>' +
                '            <details class="details action__item fn__flex-center">\n' +
                '                <summary>\n' +
                '                    ···\n' +
                '                </summary>\n' +
                '                <details-menu class="fn__layer">\n' +
                '                    <a onclick="Chat.revoke(\'' + oId + '\')" class="item">撤回</a>\n' + m +
                '                </details-menu>\n' +
                '            </details>' +
                '        </div>' +
                '    </div>\n' +
                '</div>');
        }
        Util.listenUserCard();
    },

    addTargetMsg(oId, userName, avatarURL, content, time, reverse) {
        let menu = true;
        let addMenu = '<span class="fn__space5"></span>' +
            '<details class="details action__item fn__flex-center">\n' +
            '<summary>\n' +
            '···\n' +
            '</summary>\n' +
            '<details-menu class="fn__layer">\n';
        try {
            // 判断是否可以收藏为表情包
            let emojiContent = content.replace("<p>", "").replace("</p>", "");
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
                menu = true;
                addMenu += "<a onclick=\"Chat.addEmoji(" + srcs + ")\" class=\"item\">一键收藏表情</a>";
            }
        } catch (err) {
        }
        addMenu += '<a onclick=\"Chat.at(\'' + userName + '\', \'' + oId + '\')\" class="item">引用</a>\n';
        addMenu +=  '</details-menu>\n</details>';

        let m = '';
        if (menu) {
            m = addMenu;
        }

        if (reverse === true) {
            $("#chats").append('' +
                '<div id="chat' + oId + '" class="fn__flex chats__item">\n' +
                '    <a href="' + Label.servePath + '/member/' + userName + '" style="height: 38px">\n' +
                '        <div class="avatar tooltipped__user" aria-label="' + userName + '"\n' +
                '             style="background-image: url(' + avatarURL + ');"></div>\n' +
                '    </a>\n' +
                '    <div class="chats__content">\n' +
                '        <div class="chats__arrow"></div>\n' +
                '        <div style="margin-top: 4px" class="vditor-reset ft__smaller ">\n' +
                '            ' + content + '\n' +
                '        </div>\n' +
                '        <div class="ft__smaller ft__fade fn__right date-bar">\n' +
                '            ' + time + '\n' + m +
                '        </div>\n' +
                '    </div>\n' +
                '</div>');
        } else {
            $("#chats").prepend('' +
                '<div id="chat' + oId + '" class="fn__flex chats__item">\n' +
                '    <a href="' + Label.servePath + '/member/' + userName + '" style="height: 38px">\n' +
                '        <div class="avatar tooltipped__user" aria-label="' + userName + '"\n' +
                '             style="background-image: url(' + avatarURL + ');"></div>\n' +
                '    </a>\n' +
                '    <div class="chats__content">\n' +
                '        <div class="chats__arrow"></div>\n' +
                '        <div style="margin-top: 4px" class="vditor-reset ft__smaller ">\n' +
                '            ' + content + '\n' +
                '        </div>\n' +
                '        <div class="ft__smaller ft__fade fn__right date-bar">\n' +
                '            ' + time + '\n' + m +
                '        </div>\n' +
                '    </div>\n' +
                '</div>');
        }
        Util.listenUserCard();
    },

    revoke(oId) {
        $.ajax({
            url: Label.servePath + "/chat/revoke?apiKey=" + apiKey + "&oId=" + oId,
            type: "GET",
            async: true
        });
    },

    markAllAsRead() {
        $.ajax({
            url: Label.servePath + "/chat/mark-all-as-read?apiKey=" + apiKey,
            type: "GET",
            async: true,
            success: function (result) {
                result.users.forEach((user) => {
                    $("#chatTo" + user).css("background-color", "");
                });
            }
        });
    },

    /**
     * 艾特某个人
     */
    at: function (userName, id) {
        Chat.editor.focus();
        let md = '';
        $.ajax({
            url: Label.servePath + '/chat/raw/' + id + '?apiKey=' + apiKey,
            method: 'get',
            async: false,
            success: function (result) {
                md = result.replace(/(<!--).*/g, "");
                md = md.replace(/\n/g, "\n> ");
            }
        });
        Chat.editor.insertValue(`\n##### 引用 @${userName} [↩](${Label.servePath}/chat#chat${id} "跳转至原消息")  \n> ${md}</span>\n`, !1);
        const element = document.getElementById('messageContent');
        element.scrollIntoView({ behavior: 'smooth' });
    },
    /**
     * 加载表情
     */
    loadEmojis: function () {
        let emojis = Chat.getEmojis(),html="";
        for (let i = 0; i < emojis.length; i++) {
            html+=`<button onclick="Chat.editor.setValue(Chat.editor.getValue() + '![图片表情](${emojis[i]})')">
    <div class="divX"><svg onclick='Chat.delEmoji("${emojis[i]}");event.cancelBubble =true;' style="width: 15px; height: 15px;"><use xlink:href="#delIcon"></use></svg></div>
    <img style='max-height: 50px' class="vditor-emojis__icon" src="${emojis[i]}">
</button>`;
        }
        $("#emojis").html(html);
    },
    /**
     * 删除表情包
     * @param url
     */
    confirmed: false,
    delEmoji: function (url) {
        if (Chat.confirmed === true || confirm("确定要删除该表情包吗？")) {
            Chat.confirmed = true;
            let emojis = Chat.getEmojis();
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
                        Chat.loadEmojis();
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
                Chat.addEmoji(result.result.key);
            },
            fail: function (e, data) {
                Util.alert('Upload error: ' + data.errorThrown)
            },
        })
    },
    // 从URL导入表情包
    fromURL: function () {
        Util.alert("" +
            "<div class=\"form fn__flex-column\" style=\"border: none;width: 100%;box-shadow: none;background-color: #ffffff;padding: 0;\">\n" +
            "<label>\n" +
            "  <div class=\"ft__smaller ft__fade\" style=\"float: left\">请输入图片的URL</div>\n" +
            "  <div class=\"fn-hr5 fn__5\"></div>\n" +
            "  <input type=\"text\" id=\"fromURL\">\n" +
            "</label>\n" +
            "<div class=\"fn-hr5\"></div>\n" +
            "<div class=\"fn__flex\" style=\"margin-top: 15px; justify-content: flex-end;\">\n" +
            "  <button class=\"btn btn--confirm\" onclick='Chat.addEmoji($(\"#fromURL\").val());Util.closeAlert();'>导入</button>\n" +
            "</div>\n" +
            "</div>" +
            "", "从URL导入表情包");
        $("#fromURL").focus();
        $("#fromURL").unbind();
        $("#fromURL").bind('keypress',function(event){
            if (event.keyCode == "13") {
                Chat.addEmoji($("#fromURL").val());
                Util.closeAlert();
            }
        });
    },
    addEmoji: function () {
        for (let i = 0; i < arguments.length; i++) {
            let url = arguments[i];
            let emojis = Chat.getEmojis();
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
        Chat.loadEmojis();
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


}

function getQueryVariable(variable) {
    var query = window.location.search.substring(1);
    var vars = query.split("&");
    for (var i = 0; i < vars.length; i++) {
        var pair = vars[i].split("=");
        if (pair[0] == variable) {
            return pair[1];
        }
    }
    return "";
}

$(document).ready(function () {
    Chat.init();
    // 监听按钮
    $("#sendChatBtn").on('click', function () {
        Chat.send();
    });
    // img preview
    $('#chats').on('click', '.vditor-reset img', function (event) {
        if ($(this).hasClass('emoji') ||
            $(this).closest('.editor-panel').length === 1 ||
            $(this).closest('.ad').length === 1) {
            return
        }
        var $it = $(this);
        $('body').append('<div class="img-preview" onclick="$(this).remove()"><img src="' +
            ($it.attr('src').split('?imageView2')[0]) +
            '" onload="Article.previewImgAfterLoading()"></div>')

        $('.img-preview').css({
            'background-color': '#fff',
            'position': 'fixed',
        })
    })
    // 返回顶部 置顶按钮监听
    $(window).scroll(function () {
        if ($(this).scrollTop() > 1) {
            $("#goToTop").fadeIn();
        } else {
            $("#goToTop").fadeOut();
        }
    });
    $("#goToTop a").click(function () {
        $("html,body").animate({scrollTop: 0}, 800);
        return false;
    });
    $("body").click(function () {
        $("details[open]").removeAttr("open");
    });
    // 表情包初始化
    // 加载表情
    Chat.listenUploadEmojis();
    Chat.loadEmojis();
    // 加载新版表情包分组
    EmojiGroups.init('Chat', 'New');
    Chat.loadEmojiGroupsNew();
    // 监听表情包按钮

    (()=>{
        let time_out=new Date().getTime(),timeoutId=0
        const closeEmoji=function () {
            if(timeoutId!==0){
                clearTimeout(timeoutId)
                timeoutId=0
            }
            time_out=new Date().getTime()
            timeoutId=setTimeout(()=>{
                new Date().getTime()-time_out<=700&&$("#emojiList").removeClass("showList")
            },navigator.userAgent.match(/(phone|pad|pod|ios|Android|Mobile|BlackBerry|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian)/i)!==null?0:600)
        }
        $("#emojiBtn").hover(function (){
            if(timeoutId!==0){
                clearTimeout(timeoutId)
                timeoutId=0
            }
            $('#emojiList').css('top','350px')
            time_out=new Date().getTime()
            setTimeout(()=>0!==$("#emojiBtn:hover").length&&$("#emojiList").addClass("showList"),300)
        },closeEmoji)
        $("#emojiList").hover(function () {
            if(timeoutId!==0){
                clearTimeout(timeoutId)
                timeoutId=0
            }
            time_out=new Date().getTime()
        },closeEmoji)
    })()
});
