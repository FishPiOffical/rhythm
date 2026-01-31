/*
 * Rhythm - Emoji Groups Module
 * 表情包分组模块
 * 提供表情包分组管理功能
 */

var EmojiGroups = {
    // 名称校验：仅中英文、数字、空格、常用标点（无引号/尖括号/斜杠），最长 20
    NAME_PATTERN: /^[\p{L}\p{N}\s_\-。，！？!?:：；;（）()\[\]{}·]+$/u,
    NAME_MAX_LEN: 20,
    /**
     * 初始化表情包分组
     * @param {string} targetObjectName - 目标对象名称，如 'Chat'、'Comment'、'ChatRoom'
     * @param {string} prefix - 命名空间前缀，默认为 'New'
     */
    init: function (targetObjectName, prefix) {
        prefix = prefix || 'New';

        var targetObject = window[targetObjectName];
        if (!targetObject) {
            console.error('目标对象不存在:', targetObjectName);
            return;
        }

        // 定义属性
        targetObject['emojiGroups' + prefix] = [];
        targetObject['emojiGroupsData' + prefix] = {};
        targetObject['currentEmojiGroupId' + prefix] = null;

        // 定义方法
        targetObject['loadEmojiGroups' + prefix] = EmojiGroups._createLoadMethod(targetObject, prefix);
        targetObject['renderEmojiGroups' + prefix] = EmojiGroups._createRenderMethod(targetObject, prefix);
        targetObject['selectEmojiGroup' + prefix] = EmojiGroups._createSelectMethod(targetObject, prefix);
        targetObject['loadGroupEmojis' + prefix] = EmojiGroups._createLoadEmojisMethod(targetObject, prefix, targetObjectName);
        targetObject['renderGroupEmojis' + prefix] = EmojiGroups._createRenderEmojisMethod(targetObject, prefix, targetObjectName);
        targetObject['uploadEmojiToGroup' + prefix] = EmojiGroups._createUploadEmojiMethod(targetObject, prefix);

        console.log('表情包分组模块已挂载到对象:', targetObjectName);
    },

    /**
     * 创建加载分组方法
     */
    _createLoadMethod: function (targetObject, prefix) {
        return function () {
            $.ajax({
                url: Label.servePath + '/api/emoji/groups',
                type: 'GET',
                cache: false,
                success: function (result) {
                    if (result.code == 0) {
                        var groups = result.data || [];
                        targetObject['emojiGroups' + prefix] = groups;
                        targetObject['renderEmojiGroups' + prefix](groups);
                        // 默认加载第一个分组（type === 1，全部分组）
                        for (let i = 0; i < groups.length; i++) {
                            if (groups[i].type === 1) {
                                targetObject['currentEmojiGroupId' + prefix] = groups[i].oId;
                                // 延迟一下，确保DOM已经渲染完成
                                setTimeout(function() {
                                    targetObject['selectEmojiGroup' + prefix](groups[i].oId);
                                }, 50);
                                break;
                            }
                        }
                    } else {
                        console.error('加载分组失败:', result.msg);
                    }
                },
                error: function () {
                    console.error('加载分组失败，请检查网络');
                }
            });
        };
    },

    /**
     * 创建渲染分组列表方法
     */
    _createRenderMethod: function (targetObject, prefix) {
        return function (groups) {
            let $container = $('#emojiGroupBox' + prefix);
            if ($container.length === 0) {
                return;
            }
            $container.empty();

            for (let i = 0; i < groups.length; i++) {
                let group = groups[i];
                let $groupDiv = $('<div>', {
                    class: 'emoji_group_' + prefix,
                    id: 'emojiGroup' + prefix + '_' + group.oId,
                    'data-id': group.oId,
                    'data-name': group.name,
                    'data-type': group.type,
                    css: {
                        'padding': '8px 12px',
                        'cursor': 'pointer',
                        'border-bottom': '1px solid #eee',
                        'font-size': '14px',
                        'overflow': 'hidden',
                        'line-clamp': '1',
                        'white-space': 'nowrap',
                        'text-overflow': 'ellipsis'
                    }
                });

                // 分组名称
                let $nameSpan = $('<span>', {
                    class: 'group_name_' + prefix,
                    text: group.name,
                    css: {
                        'display': 'inline-block',
                        'width': '100%'
                    }
                });

                // 点击分组名称选择分组
                $nameSpan.on('click', function (e) {
                    e.stopPropagation();
                    var groupId = $(this).closest('.emoji_group_' + prefix).data('id');
                    targetObject['selectEmojiGroup' + prefix](groupId);
                });

                $groupDiv.append($nameSpan);
                $container.append($groupDiv);
            }
        };
    },

    /**
     * 创建选择分组方法
     */
    _createSelectMethod: function (targetObject, prefix) {
        return function (groupId) {
            targetObject['currentEmojiGroupId' + prefix] = groupId;

            // 移除旧分组的高亮（恢复默认样式）
            $('.emoji_group_' + prefix).css({
                'background-color': '',
                'color': '',
                'font-weight': ''
            });
            
            // 设置选中分组的内联样式
            $('#emojiGroup' + prefix + '_' + groupId).css({
                'background-color': '#e6f7ff',
                'color': '#1890ff',
                'font-weight': 'bold'
            });

            // 加载该分组的表情
            targetObject['loadGroupEmojis' + prefix](groupId);
        };
    },

    /**
     * 创建加载分组表情方法
     */
    _createLoadEmojisMethod: function (targetObject, prefix, targetObjectName) {
        return function (groupId) {
            // 检查内存中是否已有该分组的数据
            if (targetObject['emojiGroupsData' + prefix][groupId]) {
                targetObject['renderGroupEmojis' + prefix](targetObject['emojiGroupsData' + prefix][groupId]);
                return;
            }

            $.ajax({
                url: Label.servePath + '/api/emoji/group/emojis?groupId=' + groupId,
                type: 'GET',
                cache: false,
                success: function (result) {
                    if (result.code == 0) {
                        var emojis = result.data || [];
                        // 保存到内存
                        targetObject['emojiGroupsData' + prefix][groupId] = emojis;
                        targetObject['renderGroupEmojis' + prefix](emojis);
                    } else {
                        console.error('加载表情失败:', result.msg);
                    }
                },
                error: function () {
                    console.error('加载表情失败，请检查网络');
                }
            });
        };
    },

    /**
     * 创建渲染表情列表方法
     */
    _createRenderEmojisMethod: function (targetObject, prefix, targetObjectName) {
        return function (emojis) {
            var html = "";
            var editor = targetObject.editor; // 可能为空，列表仍可渲染

            var manageUrl = Label.servePath + '/settings/function#emojiGroupBox';
            html += '<div class="emoji_manage_bar" style="display:flex;align-items:center;justify-content:space-between;padding:6px 8px;border-bottom:1px solid #eee;">'
                + '<span style="font-size:13px;color:#666;">表情包</span>'
                + '<a style="font-size:12px;color:#1890ff;" target="_blank" href="' + manageUrl + '">管理/迁移</a>'
                + '</div>';

            if (emojis.length === 0) {
                html += '<div style="text-align: center; padding: 20px; color: #999; line-height: 1.6;">' +
                    '该分组暂无表情<br>' +
                    '<a style="color:#1890ff;" target="_blank" href="' + manageUrl + '">前往表情包管理迁移旧表情</a>' +
                    '</div>';
            } else {
                emojis.sort(function (b, a) { return a.sort - b.sort; });
                for (let i = 0; i < emojis.length; i++) {
                    var url = String(emojis[i].url || '').replace(/"/g, '&quot;');
                    var emojiId = emojis[i].emojiId || '';
                    html += '<button class="emoji-insert-btn" data-url="' + url + '" data-emoji-id="' + emojiId + '" style="position:relative;">';
                    html += '<span class="emoji-del-btn" style="position:absolute;top:0;right:0;background:rgba(0,0,0,0.45);color:#fff;border-radius:50%;width:16px;height:16px;line-height:16px;font-size:12px;display:flex;align-items:center;justify-content:center;">×</span>';
                    html += '<img style="max-height: 50px" class="vditor-emojis__icon" src="' + emojis[i].url + '">';
                    html += '</button>';
                }
            }
            var $box = $('#emojis' + prefix);
            $box.html(html);
            $box.off('click.emojiInsert').on('click.emojiInsert', 'button.emoji-insert-btn', function (e) {
                e.preventDefault();
                var url = $(this).data('url');
                // 删除按钮单独处理
                if ($(e.target).hasClass('emoji-del-btn')) {
                    return;
                }
                EmojiGroups.insertEmojiToEditor(targetObjectName, url);
            });

            // 删除表情
            $box.off('click.emojiDelete').on('click.emojiDelete', '.emoji-del-btn', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var $btn = $(this).closest('button');
                var emojiId = $btn.data('emoji-id');
                var groupId = targetObject['currentEmojiGroupId' + prefix];
                if (!emojiId || !groupId) {
                    Util && Util.alert ? Util.alert('删除失败：缺少参数') : console.error('删除失败：缺少参数');
                    return;
                }
                if (!confirm('确定删除该表情吗？')) {
                    return;
                }
                EmojiGroups._deleteEmojiFromGroup(groupId, emojiId, function () {
                    // 清理缓存并刷新当前分组
                    delete targetObject['emojiGroupsData' + prefix][groupId];
                    targetObject['loadGroupEmojis' + prefix](groupId);
                });
            });
        };
    },

    /**
     * 创建上传表情到分组方法
     */
    _createUploadEmojiMethod: function (targetObject, prefix) {
        return function (emojiUrl) {


            $.ajax({
                url: Label.servePath + '/api/emoji/upload',
                type: 'POST',
                contentType: 'application/json;charset=UTF-8',
                data: JSON.stringify({ url: emojiUrl }),
                cache: false,
                success: function (result) {
                    if (result.code == 0) {
                        var gid = targetObject['currentEmojiGroupId' + prefix];
                        EmojiGroups._refreshGroupsCache(targetObject, prefix, [gid, EmojiGroups._findAllGroupId(targetObject, prefix)]);
                    } else {
                        console.error('上传表情失败:', result.msg);
                        alert('上传表情失败: ' + result.msg);
                    }
                },
                error: function () {
                    console.error('上传表情失败，请检查网络');
                    alert('上传表情失败，请检查网络');
                }
            });
        };
    },

    /**
     * 删除分组中的表情。
     * 使用无 CSRF 的 /api 路由，方便客户端场景。
     */
    _deleteEmojiFromGroup: function (groupId, emojiId, onDone) {
        $.ajax({
            url: Label.servePath + '/api/emoji/group/remove-emoji',
            type: 'POST',
            contentType: 'application/json;charset=UTF-8',
            data: JSON.stringify({ groupId: groupId, emojiId: emojiId }),
            success: function (res) {
                if (res.code === 0) {
                    Util && Util.notice ? Util.notice('success', 1200, '表情已删除') : console.log('表情已删除');
                    onDone && onDone();
                } else {
                    Util && Util.alert ? Util.alert(res.msg || '删除失败') : console.error(res.msg);
                }
            },
            error: function () {
                Util && Util.alert ? Util.alert('删除失败，请检查网络') : console.error('删除失败，请检查网络');
            }
        });
    },

    /**
     * 将表情插入编辑器并立即收起弹层。
     * @param {string} targetObjectName 宿主对象名称，例如 Comment/Chat/ChatRoom
     * @param {string} emojiUrl 表情图片地址
     */
    insertEmojiToEditor: function (targetObjectName, emojiUrl) {
        var target = window[targetObjectName];
        if (!target || !target.editor) {
            EmojiGroups._toast('编辑器尚未就绪，请稍后再试');
            return;
        }
        try {
            if (target.editor.insertValue) {
                // insertValue 能保持焦点与光标位置
                target.editor.insertValue('![图片表情](' + emojiUrl + ')', true);
            } else if (target.editor.setValue) {
                var cur = target.editor.getValue ? target.editor.getValue() : '';
                target.editor.setValue(cur + '![图片表情](' + emojiUrl + ')');
            }
        } catch (e) {
            console.error('插入表情失败', e);
        }
        if (target.editor.focus) {
            target.editor.focus();
        }
        EmojiGroups.closePanel();
    },

    /**
     * 弹出选择分组的对话框，将 URL 收藏到指定分组（默认 sort=0 由后端自增）。
     * @param {string|Array<string>} emojiUrls 单个或多个 URL
     * @param {string} defaultName 可选别名
     */
    openCollectDialog: function (emojiUrls, defaultName) {
        var urls = Array.isArray(emojiUrls) ? emojiUrls : [emojiUrls];
        if (!urls || urls.length === 0) {
            return;
        }
        // 去重 & 清洗
        urls = urls.map(function (u) { return String(u || '').trim(); }).filter(Boolean);
        urls = Array.from(new Set(urls));
        if (!urls.length) {
            return;
        }

        // 优先使用已加载的分组缓存
        var groups =
            (window.Chat && window.Chat.emojiGroupsNew) ||
            (window.ChatRoom && window.ChatRoom.emojiGroupsNew) ||
            (window.Comment && window.Comment.emojiGroupsNew) ||
            (window.Settings && window.Settings.emojiGroupsNew) ||
            (window.Settings && window.Settings.emojiGroups) ||
            [];

        // 回退：尝试从接口同步一次
        if (!groups || !groups.length) {
            try {
                $.ajax({
                    url: Label.servePath + '/api/emoji/groups',
                    type: 'GET',
                    async: false,
                    success: function (res) {
                        if (res.code === 0) {
                            groups = res.data || [];
                        }
                    }
                });
            } catch (e) {}
        }
        if (!groups || !groups.length) {
            Util && Util.alert ? Util.alert('未获取到分组，请稍后重试') : alert('未获取到分组');
            return;
        }

        var options = '';
        for (var i = 0; i < groups.length; i++) {
            options += '<option value="' + groups[i].oId + '">' + groups[i].name + '</option>';
        }
        EmojiGroups._pendingCollectUrls = urls;
        var html = '<div id="emojiCollectOverlay" style="position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;opacity:0;transition:opacity 180ms ease-out;background:rgba(0,0,0,0.25);">'
            + '<div id="emojiCollectDialog" style="width:92%;max-width:420px;background:#fff;border-radius:12px;box-shadow:0 18px 46px rgba(0,0,0,0.18);padding:18px 16px;box-sizing:border-box;font-size:14px;line-height:1.6;opacity:0;transform:scale(0.9) translateY(12px);transition:opacity 200ms ease-out, transform 240ms ease-out;">'
            + '<div style="font-weight:600;font-size:15px;margin-bottom:12px;">收藏表情到分组</div>'
            + '<div style="margin-bottom:12px;">'
            + '  <div style="font-size:13px;color:#666;margin-bottom:6px;">选择分组：</div>'
            + '  <select id="collectGroupSelect" style="width:100%;padding:8px;border:1px solid #d9d9d9;border-radius:6px;font-size:14px;">' + options + '</select>'
            + '</div>'
            + '<div style="margin-bottom:14px;">'
            + '  <div style="font-size:13px;color:#666;margin-bottom:6px;">表情名称（可选，用于展示）</div>'
            + '  <input id="collectEmojiName" type="text" value="' + (defaultName || '') + '" style="width:100%;padding:8px;border:1px solid #d9d9d9;border-radius:6px;font-size:14px;box-sizing:border-box;" />'
            + '</div>'
            + '<div style="text-align:right;display:flex;gap:10px;justify-content:flex-end;">'
            + '  <button id="collectCancelBtn" style="min-width:80px;padding:8px 10px;border:1px solid #d9d9d9;background:#fff;border-radius:6px;cursor:pointer;">取消</button>'
            + '  <button id="collectOkBtn" style="min-width:80px;padding:8px 10px;border:1px solid #1890ff;background:#1890ff;color:#fff;border-radius:6px;cursor:pointer;">确定</button>'
            + '</div>'
            + '</div></div>';

        $('#emojiCollectOverlay').remove();
        $('body').append(html);
        $('#collectOkBtn').off('click').on('click', EmojiGroups._confirmCollectEmojiInline);
        $('#collectCancelBtn').off('click').on('click', EmojiGroups.closeCollectDialog);
        $('#emojiCollectOverlay').off('click').on('click', function (e) {
            if (e.target && e.target.id === 'emojiCollectOverlay') {
                EmojiGroups.closeCollectDialog();
            }
        });
        // 进入动画
        setTimeout(function () {
            $('#emojiCollectOverlay').css('opacity', '1');
            $('#emojiCollectDialog').css({ opacity: 1, transform: 'scale(1) translateY(0)' });
        }, 10);
    },

    _confirmCollectEmojiInline: function () {
        var groupId = $('#collectGroupSelect').val();
        var name = $('#collectEmojiName').val().trim();
        var urls = EmojiGroups._pendingCollectUrls || [];
        if (!groupId) {
            Util && Util.alert ? Util.alert('请选择分组') : alert('请选择分组');
            return;
        }
        var doAdd = function (idx) {
            if (idx >= urls.length) {
                EmojiGroups.closeCollectDialog();
                Util && Util.notice ? Util.notice('success', 1200, '收藏成功') : console.log('收藏成功');
                return;
            }
            EmojiGroups._addUrlEmojiToGroup(groupId, urls[idx], name, function () {
                doAdd(idx + 1);
            }, function () {
                // 失败时关闭弹窗并中止后续
                Util && Util.closeAlert ? Util.closeAlert() : null;
            });
        };
        doAdd(0);
    },

    _addUrlEmojiToGroup: function (groupId, url, name, onDone, onFail) {
        $.ajax({
            url: Label.servePath + '/api/emoji/group/add-url-emoji',
            type: 'POST',
            contentType: 'application/json;charset=UTF-8',
            data: JSON.stringify({ groupId: groupId, url: url, sort: 0, name: name || '' }),
            success: function (res) {
                if (res.code === 0) {
                    onDone && onDone();
                    // 刷新所有宿主的当前分组与全部分组
                    EmojiGroups._refreshGroupsCacheForAll([groupId]);
                } else {
                    EmojiGroups._toast(res.msg || '收藏失败');
                    EmojiGroups.closeCollectDialog();
                    onFail && onFail();
                }
            },
            error: function () {
                EmojiGroups._toast('收藏失败，请检查网络');
                EmojiGroups.closeCollectDialog();
                onFail && onFail();
            }
        });
    },

    closeCollectDialog: function () {
        var $overlay = $('#emojiCollectOverlay');
        var $dialog = $('#emojiCollectDialog');
        if (!$overlay.length) return;
        // 退出动画
        $overlay.css('opacity', '0');
        $dialog.css({ opacity: 0, transform: 'scale(0.9) translateY(12px)' });
        setTimeout(function () { $overlay.remove(); }, 220);
    },

    _toast: function (msg) {
        if (typeof Util !== 'undefined' && Util.notice) {
            Util.notice('warning', 2000, msg);
        } else if (typeof Util !== 'undefined' && Util.alert) {
            Util.alert(msg);
        } else {
            alert(msg);
        }
    },

    /**
     * 清理并刷新指定分组（含全部分组），保持最新展示。
     */
    _refreshGroupsCache: function (targetObject, prefix, groupIds) {
        if (!targetObject || !targetObject['emojiGroupsData' + prefix]) return;
        var ids = (groupIds || []).filter(Boolean);
        if (!ids.length) return;
        for (var i = 0; i < ids.length; i++) {
            delete targetObject['emojiGroupsData' + prefix][ids[i]];
        }
        targetObject['emojiGroupsDirty' + prefix] = true;
        var cur = targetObject['currentEmojiGroupId' + prefix];
        if (cur && ids.indexOf(cur) !== -1 && targetObject['loadGroupEmojis' + prefix]) {
            targetObject['loadGroupEmojis' + prefix](cur);
            targetObject['emojiGroupsDirty' + prefix] = false;
        }
    },

    /**
     * 跨宿主刷新：Chat/ChatRoom/Comment/Settings。
     */
    _refreshGroupsCacheForAll: function (groupIds) {
        var ids = (groupIds || []).filter(Boolean);
        if (!ids.length) return;
        var targets = [window.Chat, window.ChatRoom, window.Comment, window.Settings];
        var prefix = 'New';
        for (var t = 0; t < targets.length; t++) {
            var target = targets[t];
            if (!target || !target['emojiGroupsData' + prefix]) continue;
            var merged = ids.slice();
            var allId = EmojiGroups._findAllGroupId(target, prefix);
            if (allId) merged.push(allId);
            for (var i = 0; i < merged.length; i++) {
                delete target['emojiGroupsData' + prefix][merged[i]];
            }
            target['emojiGroupsDirty' + prefix] = true;
        }
    },

    _findAllGroupId: function (targetObject, prefix) {
        var groups = targetObject ? targetObject['emojiGroups' + prefix] : null;
        if (!groups || !groups.length) return null;
        for (var i = 0; i < groups.length; i++) {
            if (groups[i].type === 1) {
                return groups[i].oId;
            }
        }
        return null;
    },

    /**
     * 关闭表情列表弹层。
     */
    closePanel: function () {
        var $list = $('#emojiList');
        if ($list.length) {
            $list.removeClass('showList');
        }
        // 关闭可能展开的菜单，避免 hover 保持展开
        $('details[open]').removeAttr('open');
    },

    /**
     * 在打开面板时确保当前分组数据是最新的。
     */
    ensureCurrentFresh: function (targetObject, prefix) {
        prefix = prefix || 'New';
        if (!targetObject || !targetObject['emojiGroupsData' + prefix]) return;
        if (!targetObject['emojiGroupsDirty' + prefix]) return;
        var cur = targetObject['currentEmojiGroupId' + prefix];
        if (!cur || !targetObject['loadGroupEmojis' + prefix]) return;
        delete targetObject['emojiGroupsData' + prefix][cur];
        targetObject['emojiGroupsDirty' + prefix] = false;
        targetObject['loadGroupEmojis' + prefix](cur);
    }
};
