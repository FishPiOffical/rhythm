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
 * @fileoverview Message channel via WebSocket.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @version 1.14.0.3, Mar 17, 2019
 */

/**
 * @description Article channel.
 * @static
 */
var ArticleChannel = {
    /**
     * WebSocket instance.
     *
     * @type WebSocket
     */
    ws: undefined,
    /**
     * @description Initializes message channel
     */
    init: function (channelServer) {
        ArticleChannel.ws = new ReconnectingWebSocket(channelServer)

        ArticleChannel.ws.onopen = function () {
            console.log("Connected to article channel websocket.")
        }

        setInterval(function () {
            //ArticleChannel.ws.send('p');
        }, 1000 * 30)

        ArticleChannel.ws.onmessage = function (evt) {
            var data = JSON.parse(evt.data)

            if (Label.articleOId !== data.articleId) { // It's not the current article
                return
            }

            switch (data.type) {
                case 'comment':
                    var cmtCount = parseInt(
                        $('.comments-header .article-cmt-cnt').text()) + 1
                    // 总帖数更新
                    $('.comments-header .article-cmt-cnt').text(cmtCount + ' ' + Label.cmtLabel)

                    // 新增第一条评论时到底部的锚点
                    if ($('#comments .list > ul > li').length === 0) {
                        $('.comment-header > .fn-none').show()
                        // 显示预览模式 & 回到底部
                        $('.comments-header > .fn-none').show()
                        // 显示评论
                        $('#articleCommentsPanel').parent().show()
                    }

                    if (0 === Label.userCommentViewMode) { // tranditional view mode
                        $('#comments > .list > ul').append(data.cmtTpl)
                    } else {
                        $('#comments > .list > ul').prepend(data.cmtTpl)
                    }

                    // ua
                    $('#' + data.commentId + ' .cmt-via').text('via ' + Util.getDeviceByUa(data.commentUA))

                    // 回帖高亮，他人回帖不定位，只有自己回帖才定位
                    if (Label.currentUserName === data.commentAuthorName) {
                        Comment._bgFade($('#' + data.commentId))
                    }

                    // 更新回复的回帖
                    if (data.commentOriginalCommentId !== '') {
                        var $originalComment = $('#' + data.commentOriginalCommentId),
                            $replyBtn = $originalComment.find(
                                '.comment-action > .ft-fade > .fn-pointer')
                        if ($replyBtn.length === 1) {
                            $replyBtn.html(' ' + (parseInt($.trim($replyBtn.text())) + 1)
                                + ' ' + Label.replyLabel + ' <span class="'
                                + $replyBtn.find('span').attr('class') + '"></span>')

                            if ($replyBtn.find('svg').attr('class') === 'icon-chevron-up') {
                                $replyBtn.find('svg').removeClass('icon-chevron-up').addClass('icon-chevron-down').find('use').attr('xlink:href', '#chevron-down')
                                $replyBtn.click()
                            }
                        } else {
                            $originalComment.find('.comment-action > .ft-fade').prepend('<span class="fn-pointer ft-smaller fn-left" onclick="Comment.showReply(\''
                                + data.commentOriginalCommentId +
                                '\', this, \'comment-replies\')" style="opacity: 1;"> 1 '
                                + Label.replyLabel +
                                ' <svg class="icon-chevron-down"><use xlink:href="#chevron-down"></use></svg>')
                        }
                    }
                    Util.parseHljs()
                    Util.parseMarkdown()
                    break
                case 'articleHeat':
                    var $heatBar = $('#heatBar'),
                        $heat = $('.heat')

                    if (data.operation === '+') {
                        $heatBar.append('<i class="point"></i>')
                        setTimeout(function () {
                            $heat.width($('.heat').width() + 1 * 3)
                            $heatBar.find('.point').remove()
                        }, 2000)
                    } else {
                        $heat.width($('.heat').width() - 1 * 3)
                        $heatBar.append('<i class="point-remove"></i>')
                        setTimeout(function () {
                            $heatBar.find('.point-remove').remove()
                        }, 2000)
                    }

                    break
                default:
                    console.error('Wrong data [type=' + data.type + ']')
            }

        }

        ArticleChannel.ws.onclose = function () {
            console.log("Disconnected to article channel websocket.")
        }

        ArticleChannel.ws.onerror = function (err) {
            console.log(err)
        }
    },
}

/**
 * @description Article list channel.
 * @static
 */
var ArticleListChannel = {
    /**
     * WebSocket instance.
     *
     * @type WebSocket
     */
    ws: undefined,
    /**
     * @description Initializes message channel
     */
    init: function (channelServer) {
        ArticleListChannel.ws = new ReconnectingWebSocket(channelServer)

        ArticleListChannel.ws.onopen = function () {
            console.log("Connected to article list channel websocket.")
        }

        setInterval(function () {
            //ArticleListChannel.ws.send('p');
        }, 1000 * 30)

        ArticleListChannel.ws.onmessage = function (evt) {
            var data = JSON.parse(evt.data)
            $('.article-list h2 > a[rel=bookmark]').each(function () {
                var id = $(this).data('id').toString()

                if (data.articleId === id) {
                    var $li = $(this).closest('li'),
                        $heat = $li.find('.heat')

                    if (data.operation === '+') {
                        $li.append('<i class="point"></i>')
                        setTimeout(function () {
                            $heat.width($heat.width() + 1 * 3)
                            $li.find('.point').remove()
                        }, 2000)
                    } else {
                        $heat.width($heat.width() - 1 * 3)
                        $li.append('<i class="point-remove"></i>')
                        setTimeout(function () {
                            $li.find('.point-remove').remove()
                        }, 2000)
                    }
                }
            })
        }

        ArticleListChannel.ws.onclose = function () {
            console.log("Disconnected to article list channel websocket.")
        }

        ArticleListChannel.ws.onerror = function (err) {
            console.log('ERROR', err)
        }
    },
}

var IdleTalkChannel = {
    /**
     * WebSocket instance.
     *
     * @type WebSocket
     */
    ws: undefined,
    /**
     * @description Initializes message channel
     */
    init: function (channelServer) {
        IdleTalkChannel.ws = new ReconnectingWebSocket(channelServer)

        IdleTalkChannel.ws.onopen = function () {
            console.log("Connected to idle talk channel websocket.")
        }

        setInterval(function () {
            //IdleTalkChannel.ws.send('p');
        }, 1000 * 30)

        IdleTalkChannel.ws.onmessage = function (evt) {
            var data = JSON.parse(evt.data);

            let cmd = data.command;
            switch (data.youAre) {
                case 'sender':
                    let html2 = '' +
                        '<li id="' + data.mapId + '">\n' +
                        '    <div class=\'fn-flex\'>\n' +
                        '<a href="/member/' + cmd.toUserName + '">' +
                        '        <div class="avatar tooltipped tooltipped-ne"\n' +
                        '             aria-label="' + cmd.toUserName + '"\n' +
                        '             style="background-image:url(\'' + cmd.toUserAvatar + '\')"></div>\n' +
                        '</a>' +
                        '        <div class="fn-flex-1">\n' +
                        '            <h2>\n' +
                        '                <a href="/member/' + cmd.toUserName + '">发送给「' + cmd.toUserName + '」的私信</a>\n' +
                        '                <button class="btn fn-right" style="margin-left: 10px" onclick="IdleTalk.revoke(\'' + data.mapId + '\')">撤回</button>\n' +
                        '            </h2>\n' +
                        '            <span class="ft-fade vditor-reset">\n' +
                        '                ' + timeTrans(parseFloat(data.mapId)) + ' · 主题：' + cmd.theme + '\n' +
                        '            </span>\n' +
                        '        </div>\n' +
                        '    </div>\n' +
                        '</li>';
                    $("#sent").prepend(html2);
                    if ($("#sent").find(".nope")[0] !== undefined) {
                        $("#sent").find(".nope")[0].remove();
                    }
                    break;
                case 'receiver':
                    let html = '' +
                        '<li id="' + data.mapId + '">\n' +
                        '    <div class=\'fn-flex\'>\n' +
                        '<a href="/member/' + cmd.fromUserName + '">' +
                        '        <div class="avatar tooltipped tooltipped-ne"\n' +
                        '             aria-label="' + cmd.fromUserName + '"\n' +
                        '             style="background-image:url(\'' + cmd.fromUserAvatar + '\')"></div>\n' +
                        '</a>' +
                        '        <div class="fn-flex-1">\n' +
                        '            <h2>\n' +
                        '                <a href="/member/' + cmd.fromUserName + '">来自「' + cmd.fromUserName + '」的私信</a>\n' +
                        '                <button class="red fn-right" onclick="IdleTalk.seek(\'' + data.mapId + '\', \'' + cmd.fromUserName + '\', \'' + cmd.theme + '\')">查看并销毁</button>\n' +
                        '            </h2>\n' +
                        '            <span class="ft-fade vditor-reset">\n' +
                        '                ' + timeTrans(parseFloat(data.mapId)) + ' · 主题：' + cmd.theme + '\n' +
                        '            </span>\n' +
                        '        </div>\n' +
                        '    </div>\n' +
                        '</li>';
                    $("#received").prepend(html);
                    if ($("#received").find(".nope")[0] !== undefined) {
                        $("#received").find(".nope")[0].remove();
                    }
                    break;
                case 'destroyIdleChatMessage':
                    $("#" + cmd).remove();
                    if ($("#received").find("li")[0] === undefined && $("#received").find(".nope")[0] === undefined) {
                        $("#received").append('<div class="nope"><svg><use xlink:href="#nope"></use></svg> 没有收到任何来信</div>');
                    }
                    if ($("#sent").find("li")[0] === undefined && $("#sent").find(".nope")[0] === undefined) {
                        $("#sent").append('<div class="nope"><svg><use xlink:href="#nope"></use></svg> 没有未读的发信</div>');
                    }
                    break;
            }
        }

        IdleTalkChannel.ws.onclose = function () {
            console.log("Disconnected to idle talk channel websocket.")
        }

        IdleTalkChannel.ws.onerror = function (err) {
            console.log('ERROR', err)
        }
    },
}

/**
 * 转化时间戳
 *
 * @param date
 * @returns {string}
 */
function timeTrans(date) {
    var date = new Date(date);
    var Y = date.getFullYear() + '年';
    var M = (date.getMonth() + 1 < 10 ? '0' + (date.getMonth() + 1) : date.getMonth() + 1) + '月';
    var D = (date.getDate() < 10 ? '0' + (date.getDate()) : date.getDate()) + '日 ';
    var h = (date.getHours() < 10 ? '0' + date.getHours() : date.getHours()) + ':';
    var m = (date.getMinutes() < 10 ? '0' + date.getMinutes() : date.getMinutes()) + ':';
    var s = (date.getSeconds() < 10 ? '0' + date.getSeconds() : date.getSeconds());
    return Y + M + D + h + m + s;
}

/**
 * @description Chatroom channel.
 * @static
 */
var ChatRoomChannel = {
    /**
     * WebSocket instance.
     *
     * @type WebSocket
     */
    ws: undefined,
    manual: undefined,
    /**
     * @description Initializes message channel
     */
    init: function (channelServer) {
        ChatRoomChannel.ws = new ReconnectingWebSocket(channelServer)

        ChatRoomChannel.ws.onopen = function () {
            console.log("Connected to chatroom channel websocket.")
        }

        ChatRoomChannel.ws.onmessage = function (evt) {
            var data = JSON.parse(evt.data)

            switch (data.type) {
                case 'barrager':
                    let barragerContent = data.barragerContent;
                    let barragerColor = data.barragerColor;
                    let barragerUserName = data.userName;
                    let barragerUserAvatarURL = data.userAvatarURL;
                    let barragerUserNickname = data.userNickname;
                    let barrager = '';
                    if (barragerUserNickname != '' && barragerUserNickname != undefined) {
                        barrager = barragerUserNickname + ': ' + barragerContent;
                    } else {
                        barrager = barragerUserName + ': ' + barragerContent;
                    }
                    let item = {
                        img: barragerUserAvatarURL,
                        info: barrager,
                        href: Label.servePath + '/member/' + barragerUserName,
                        close: false,
                        speed: Math.round(Math.random()*10+10),
                        color: barragerColor
                    }
                    $('body').barrager(item);
                    break;
                case 'discussChanged':
                    let whoChanged = data.whoChanged;
                    let newDiscuss = data.newDiscuss;
                    // 通知
                    let discussHtml = "<div class='newDiscussNotice' style='color: rgb(50 50 50);margin-bottom: 12px;text-align: center;display: none;'>" +
                        "<svg><use xlink:href='#pound'></use></svg>&nbsp;" + '<a href="' + Label.servePath + '/member/' + whoChanged + '" target="_blank">' + whoChanged + "</a> 编辑了话题：<a href='javascript:void(0)' style='text-decoration: none'>" +
                        newDiscuss +
                        "</a></div>";
                    $('#chats').prepend(discussHtml);
                    $(".newDiscussNotice").slideDown(500);
                    $("#discuss-title").text(newDiscuss);
                    break;
                case 'redPacketStatus':
                    let whoGive = data.whoGive;
                    let whoGot = data.whoGot;
                    let got = data.got;
                    let count = data.count;
                    let oId = data.oId;
                    let dice = data.dice
                    let spell
                    if (dice === null || dice === undefined) {
                        spell = '<a href="' + Label.servePath + '/member/' + whoGot + '" target="_blank">' + whoGot + '</a> 抢到了 <a href="' + Label.servePath + '/member/' + whoGive + '" target="_blank">' + whoGive + '</a> 的 <a style="cursor: pointer" onclick="ChatRoom.unpackRedPacket(\'' + oId + '\')">红包</a>';
                    } else {
                        let bet
                        switch (dice.bet) {
                            case 'big':
                                bet = "大"
                                break
                            case 'small':
                                bet = "小"
                                break
                            case 'leopard':
                                bet = "豹子"
                                break
                        }
                        let chips = dice.chips;
                        spell = '<a href="' + Label.servePath + '/member/' + whoGot + '" target="_blank">' + whoGot + '</a> 在 <a href="' + Label.servePath + '/member/' + whoGive + '" target="_blank">' + whoGive + '</a> 的 <a style="cursor: pointer" onclick="ChatRoom.bet(\'' + oId + '\')">盘口</a> 下注' + chips + '积分买' + bet;
                    }
                    // 红包抢光了，修改状态
                    if (got === count) {
                        $("#chatroom" + data.oId).find(".hongbao__item").css("opacity", ".36");
                        if(!$("#chatroom" + oId).find(".hongbao__item").hasClass('opened')){
                            $("#chatroom" + oId).find(".hongbao__item").addClass('opened')
                        }
                        $("#chatroom" + data.oId +" .hongbao__item").removeAttr("onclick").attr("onclick","ChatRoom.unpackRedPacket(" + oId + ");")
                        $("#chatroom" + data.oId).find(".redPacketDesc").html("已经被抢光啦");
                        if (dice === null || dice === undefined) {
                            spell += '，红包已被领完 (' + got + '/' + count + ')';
                        } else {
                            spell += '，已封盘 (' + got + '/' + count + ')';
                        }
                    } else {
                        spell += ' (' + got + '/' + count + ')';
                    }
                    // 通知
                    let html = "<div class='redPacketNotice' style='color: rgb(50 50 50);margin-bottom: 12px;text-align: center;display: none;'>" +
                        "<svg><use xlink:href='#redPacketIcon'></use></svg>&nbsp;" +
                        spell +
                        "</div>";
                    $('#chats').prepend(html);
                    $(".redPacketNotice").slideDown(500);
                    break;
                case 'online':
                    $("#discuss-title").text(data.discussing);
                    $('#onlineCnt').text(data.onlineChatCnt);
                    $('#indexOnlineChatCnt').text(data.onlineChatCnt);
                    Label.onlineAvatarData = "";
                    for (var user in data.users) {
                        let userInfo = data.users[user];
                        Label.onlineAvatarData += "<a target=\"_blank\" data-name=\"" + userInfo.userName + "\"\n" +
                            "href=\"" + userInfo.homePage + "\">\n" +
                            "<img style='margin-bottom: 10px' class=\"avatar avatar-small\" aria-label=\"" + userInfo.userName + "\"\n" +
                            "src=\"" + userInfo.userAvatarURL48 + "\">\n" +
                            "</a>";
                    }
                    Util.listenUserCard();
                    break;
                case 'revoke':
                    $("#chatroom" + data.oId).remove();
                    $("#chatindex" + data.oId).remove();
                    break;
                case 'refresh':
                    ChatRoom.flashScreen();
                    break;
                case 'customMessage':
                    let message = "<div class='customNotice' style='color: rgb(118 118 118);margin-bottom: 12px;text-align: center;display: none;'>" +
                        data.message +
                        "</div>";
                    $('#chats').prepend(message);
                    $(".customNotice").slideDown(500);
                    break;
                case 'refreshBarrager':
                    $('#barragerCost').text(data.cost);
                    $('#barragerUnit').text(data.unit);
                    break;
                case 'msg':
                    // Chatroom
                    if ($("#chatRoomIndex").length === 0 && $("#chatroom" + data.oId).length <= 0) {
                        ChatRoom.renderMsg(data);
                        ChatRoom.resetMoreBtnListen();
                    }

                    // index
                    if ($("#chatRoomIndex").has("#emptyChatRoom").length !== 0) {
                        $("#emptyChatRoom").remove();
                    }
                    let userNickname = data.userNickname;
                    let userName = data.userName;
                    if (userNickname !== undefined && userNickname !== "") {
                        userNickname = userNickname + " (" + userName + ")"
                    } else {
                        userNickname = userName;
                    }
                    let newContent = data.content;
                    if (newContent.indexOf("\"msgType\":\"redPacket\"") !== -1) {
                        newContent = "[收到红包，请在完整版聊天室查看]";
                    }
                    if (newContent.indexOf("\"msgType\":\"weather\"") !== -1) {
                        newContent = "[天气卡片，请在完整版聊天室查看]";
                    }
                    if (newContent.indexOf("\"msgType\":\"music\"") !== -1) {
                        newContent = "[音乐卡片，请在完整版聊天室查看]";
                    }
                    $("#chatRoomIndex").prepend("" +
                        "<li class=\"fn-flex\" id=\"chatindex" + data.oId + "\" style='display: none; border-bottom: 1px solid #eee;'>\n" +
                        "    <a rel=\"nofollow\" href=\"/member/" + data.userName + "\">\n" +
                        "        <div class=\"avatar tooltipped tooltipped-n\"\n" +
                        "             aria-label=\"" + data.userName + "\"\n" +
                        "             style=\"background-image:url('" + data.userAvatarURL48 + "')\"></div>\n" +
                        "    </a>\n" +
                        "    <div class=\"fn-flex-1\">\n" +
                        "        <div class=\"ft-smaller\">\n" +
                        "            <a rel=\"nofollow\" href=\"/member/" + data.userName + "\">\n" +
                        "                <span class=\"ft-gray\">" + userNickname + "</span>\n" +
                        "            </a>\n" +
                        "        </div>\n" +
                        "        <div class=\"vditor-reset comment " + Label.chatRoomPictureStatus + "\">\n" +
                        "            " + ChatRoomChannel.filterContent(newContent) + "\n" +
                        "        </div>\n" +
                        "    </div>\n" +
                        "</li>");
                    if ($("#chatRoomIndex li.fn-flex").length === 11) {
                        $("#chatRoomIndex li.fn-flex:last").fadeOut(199, function () {
                            $("#chatRoomIndex li.fn-flex:last").remove();
                        });
                    }
                    $("#chatRoomIndex li:first").slideDown(200);
                    Util.listenUserCard();
                    typeof ChatRoom==="object"&&ChatRoom.imageViewer()
                    break;
            }
        }

        ChatRoomChannel.ws.onclose = function () {
            console.log("Disconnected to chat room channel websocket.")
        }

        ChatRoomChannel.ws.onerror = function (err) {
            console.log('ERROR', err)
        }
    },
    /**
     * 过滤消息中的图片
     * */
    filterContent: function(content){
        let dom = document.createElement("div");
        dom.innerHTML = content;
        let imgList = dom.querySelectorAll('img');
        imgList.forEach(ele=>{
            const source = ele.src || '';
            const sharpIndex = source.indexOf('#');
            const hash = sharpIndex > -1 ? source.substring(sharpIndex) : '';
            const withoutHash = sharpIndex > -1 ? source.substring(0, sharpIndex) : source;
            const queryIndex = withoutHash.indexOf('?');
            const base = queryIndex > -1 ? withoutHash.substring(0, queryIndex) : withoutHash;
            ele.src = base + '?imageView2/0/w/150/h/150/interlace/0/q/90' + hash;
        })
        return dom.innerHTML;
    },
}

/**
 * @description gobang game channel.
 * @static
 */
var GobangChannel = {
    /**
     * WebSocket instance.
     *
     * @type WebSocket
     */
    ws: undefined,
    /**
     * @description Initializes message channel
     */
    init: function (channelServer) {
        GobangChannel.ws = new ReconnectingWebSocket(channelServer)

        GobangChannel.ws.onopen = function () {
            console.log("Connected to gobang channel websocket.")
            setInterval(function () {
                GobangChannel.ws.send('zephyr test')
            }, 1000 * 30)
        }

        GobangChannel.ws.onmessage = function (evt) {
            var data = JSON.parse(evt.data)

            switch (data.type) {
                case 'gobangPlayer':
                    console.log('data.type:>gobangPlayer')
                    break
                case 'msg':
                    console.log('data.type:>msg')
                    break
            }
        }

        GobangChannel.ws.onclose = function () {
            console.log("Disconnected to gobang channel websocket.")
        }

        GobangChannel.ws.onerror = function (err) {
            console.log('ERROR', err)
        }
    },
}
