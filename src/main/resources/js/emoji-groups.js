/*
 * Rhythm - Emoji Groups Module
 * 表情包分组模块
 * 提供表情包分组管理功能
 */

var EmojiGroups = {
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
                url: Label.servePath + '/emoji/groups',
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
                                targetObject['selectEmojiGroup' + prefix](groups[i].oId);
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
                url: Label.servePath + '/emoji/group/emojis?groupId=' + groupId,
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
            var editor = targetObject.editor;

            if (!editor) {
                console.error('编辑器对象不存在');
                return;
            }

            if (emojis.length === 0) {
                html = '<div style="text-align: center; padding: 20px; color: #999;">该分组暂无表情</div>';
            } else {
                emojis.sort(function (b, a) { return a.sort - b.sort; });
                for (let i = 0; i < emojis.length; i++) {
                    html += '<button onclick="' + targetObjectName + '.editor.setValue(' + targetObjectName + '.editor.getValue() + \'![图片表情](' + emojis[i].url + ')\')">';
                    html += '<img style="max-height: 50px" class="vditor-emojis__icon" src="' + emojis[i].url + '">';
                    html += '</button>';
                }
            }
            $('#emojis' + prefix).html(html);
        };
    },

    /**
     * 创建上传表情到分组方法
     */
    _createUploadEmojiMethod: function (targetObject, prefix) {
        return function (emojiUrl) {


            $.ajax({
                url: Label.servePath + '/emoji/upload',
                type: 'POST',
                data: {
                    url: emojiUrl,
                },
                headers: {'csrfToken': Label.csrfToken},
                cache: false,
                success: function (result) {
                    if (result.code == 0) {
                        // alert('表情上传成功');
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
    }
};
