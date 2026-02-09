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
(function () {
    if (typeof window === 'undefined') {
        return;
    }

    function initVipAdmin() {
        if (window.__vipAdminInited) {
            return true;
        }

        if (!document.getElementById('vipAdminRoot') || typeof window.$ === 'undefined') {
            return false;
        }

        window.__vipAdminInited = true;

        var state = {
            page: 1,
            pageSize: 20,
            total: 0,
            hasMore: false,
            levels: [],
            levelById: {},
            levelByCode: {},
            currentRowLvCode: '',
            currentRowConfig: {}
        };

        var vip4ColorEffects = [
            'rainbow', 'neon', 'fire', 'ocean', 'forest', 'sunset', 'metal', 'galaxy'
        ];

        var configKeyLabels = {
            bold: '昵称加粗',
            underline: '昵称下划线',
            color: '昵称颜色/特效',
            autoCheckin: '自动签到',
            checkinCard: '免签卡数量',
            metal: 'DIY 动态勋章',
            jointVip: '联合会员'
        };

        function notice(type, message) {
            var msg = message || '操作完成';
            if (window.Util && typeof window.Util.notice === 'function') {
                window.Util.notice(type === 'success' ? 'success' : 'error', 2500, msg);
            } else {
                window.alert(msg);
            }
        }

        function escapeHTML(input) {
            var text = String(input == null ? '' : input);
            return text
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        }

        function formatTime(ts) {
            var value = Number(ts || 0);
            if (!value || value <= 0) {
                return '永久';
            }
            var date = new Date(value);
            if (isNaN(date.getTime())) {
                return String(ts);
            }
            var y = date.getFullYear();
            var m = ('0' + (date.getMonth() + 1)).slice(-2);
            var d = ('0' + date.getDate()).slice(-2);
            var hh = ('0' + date.getHours()).slice(-2);
            var mm = ('0' + date.getMinutes()).slice(-2);
            return y + '-' + m + '-' + d + ' ' + hh + ':' + mm;
        }

        function requestJSON(url, payload, done) {
            $.ajax({
                url: url,
                type: 'POST',
                contentType: 'application/json;charset=UTF-8',
                dataType: 'json',
                data: JSON.stringify(payload || {}),
                success: function (response) {
                    done(null, response || {});
                },
                error: function (xhr) {
                    var msg = '请求失败';
                    try {
                        if (xhr && xhr.responseJSON && xhr.responseJSON.msg) {
                            msg = xhr.responseJSON.msg;
                        }
                    } catch (e) {
                    }
                    done(new Error(msg));
                }
            });
        }

        function getSuccessData(response) {
            if (!response || response.code !== 0) {
                throw new Error((response && response.msg) || '接口执行失败');
            }
            return response.data || {};
        }

        function parseJSONSafe(raw, fallback) {
            if (raw == null || raw === '') {
                return fallback;
            }
            if (typeof raw === 'object') {
                return raw;
            }
            try {
                return JSON.parse(raw);
            } catch (e) {
                return fallback;
            }
        }

        function cloneObject(obj) {
            try {
                return JSON.parse(JSON.stringify(obj || {}));
            } catch (e) {
                return {};
            }
        }

        function parseBenefitsTemplate(level) {
            if (!level) {
                return {};
            }
            var benefits = parseJSONSafe(level.benefits, {});
            return benefits && typeof benefits === 'object' ? benefits : {};
        }

        function getLevelById(levelOId) {
            return state.levelById[levelOId] || null;
        }

        function getLevelByCode(lvCode) {
            return state.levelByCode[lvCode] || null;
        }

        function getConfigItemLabel(key) {
            if (configKeyLabels[key]) {
                return configKeyLabels[key] + '（' + key + '）';
            }
            return key;
        }

        function isVip4Level(lvCode) {
            return /^VIP4/.test(String(lvCode || ''));
        }

        function mergeConfigWithTemplate(template, current) {
            var merged = {};
            var cfg = current && typeof current === 'object' ? current : {};
            var keys = Object.keys(template || {});
            for (var i = 0; i < keys.length; i++) {
                var key = keys[i];
                if (cfg.hasOwnProperty(key) && cfg[key] !== undefined && cfg[key] !== null) {
                    merged[key] = cfg[key];
                } else {
                    merged[key] = template[key];
                }
            }
            return merged;
        }

        function renderConfigBuilder(containerSelector, template, currentConfig, lvCode) {
            var $container = $(containerSelector);
            var keys = Object.keys(template || {});
            if (!keys.length) {
                $container.html('<div class="vip-config-builder__empty">该等级没有可配置项</div>');
                return;
            }

            var config = mergeConfigWithTemplate(template, currentConfig);
            var html = '';
            for (var i = 0; i < keys.length; i++) {
                var key = keys[i];
                var templateVal = template[key];
                var value = config[key];
                var itemType = 'text';

                if (typeof templateVal === 'boolean') {
                    itemType = 'bool';
                } else if (typeof templateVal === 'number') {
                    itemType = 'number';
                } else if (Array.isArray(templateVal)) {
                    itemType = 'array';
                } else if (templateVal && typeof templateVal === 'object') {
                    itemType = 'object';
                } else if (typeof templateVal === 'string') {
                    if (key === 'color' && isVip4Level(lvCode)) {
                        itemType = 'vip4-color';
                    } else if (key === 'color') {
                        itemType = 'color';
                    }
                }

                html += '<div class="vip-config-item" data-key="' + escapeHTML(key) + '" data-type="' + escapeHTML(itemType) + '">';
                html += '<label>' + escapeHTML(getConfigItemLabel(key)) + '</label>';

                if (itemType === 'bool') {
                    html += '<input type="checkbox" class="vip-config-value" ' + (value ? 'checked' : '') + '/>';
                } else if (itemType === 'number') {
                    var step = Number.isInteger(Number(templateVal)) ? '1' : 'any';
                    html += '<input type="number" class="vip-config-value" step="' + step + '" value="' + escapeHTML(value) + '"/>';
                } else if (itemType === 'color') {
                    var color = /^#[0-9a-fA-F]{6}$/.test(String(value || '')) ? String(value) : '#ff0000';
                    html += '<input type="color" class="vip-config-value" value="' + escapeHTML(color) + '"/>';
                } else if (itemType === 'vip4-color') {
                    var colorValue = String(value || '');
                    var isPreset = vip4ColorEffects.indexOf(colorValue) >= 0;
                    html += '<select class="vip-config-value vip4-color-select">';
                    for (var c = 0; c < vip4ColorEffects.length; c++) {
                        var effect = vip4ColorEffects[c];
                        html += '<option value="' + escapeHTML(effect) + '"' + (isPreset && effect === colorValue ? ' selected' : '') + '>' + escapeHTML(effect) + '</option>';
                    }
                    html += '<option value="__custom__"' + (!isPreset ? ' selected' : '') + '>自定义颜色</option>';
                    html += '</select>';
                    html += '<input type="text" class="vip-config-custom" placeholder="#RRGGBB 或类名" value="' + escapeHTML(isPreset ? '' : colorValue) + '"' + (isPreset ? ' style="display:none;"' : '') + '/>';
                } else if (itemType === 'array' || itemType === 'object') {
                    var jsonVal = '';
                    try {
                        jsonVal = JSON.stringify(value, null, 2);
                    } catch (e) {
                        jsonVal = '';
                    }
                    html += '<textarea class="vip-config-value" rows="3" placeholder="JSON">' + escapeHTML(jsonVal) + '</textarea>';
                } else {
                    html += '<input type="text" class="vip-config-value" value="' + escapeHTML(value) + '"/>';
                }

                html += '</div>';
            }

            $container.html(html);
        }

        function readConfigBuilder(containerSelector) {
            var result = {};
            var hasItem = false;
            var hasError = false;
            var errorMsg = '';

            $(containerSelector).find('.vip-config-item').each(function () {
                hasItem = true;
                var $item = $(this);
                var key = $item.attr('data-key');
                var type = $item.attr('data-type');

                try {
                    if (type === 'bool') {
                        result[key] = $item.find('.vip-config-value').prop('checked');
                    } else if (type === 'number') {
                        var numRaw = $.trim($item.find('.vip-config-value').val() || '');
                        if (numRaw === '') {
                            throw new Error(key + ' 不能为空');
                        }
                        var numVal = Number(numRaw);
                        if (isNaN(numVal)) {
                            throw new Error(key + ' 不是有效数字');
                        }
                        result[key] = numVal;
                    } else if (type === 'color') {
                        var color = $.trim($item.find('.vip-config-value').val() || '');
                        if (!/^#[0-9a-fA-F]{6}$/.test(color)) {
                            throw new Error(key + ' 颜色格式不正确');
                        }
                        result[key] = color;
                    } else if (type === 'vip4-color') {
                        var selected = $item.find('.vip4-color-select').val();
                        if (selected === '__custom__') {
                            var custom = $.trim($item.find('.vip-config-custom').val() || '');
                            if (custom === '') {
                                throw new Error(key + ' 不能为空');
                            }
                            result[key] = custom;
                        } else {
                            result[key] = selected;
                        }
                    } else if (type === 'array' || type === 'object') {
                        var jsonText = $.trim($item.find('.vip-config-value').val() || '');
                        if (jsonText === '') {
                            throw new Error(key + ' 不能为空');
                        }
                        result[key] = JSON.parse(jsonText);
                    } else {
                        var textVal = $.trim($item.find('.vip-config-value').val() || '');
                        if (textVal === '') {
                            throw new Error(key + ' 不能为空');
                        }
                        result[key] = textVal;
                    }
                } catch (e) {
                    hasError = true;
                    errorMsg = e.message || (key + ' 配置错误');
                    return false;
                }
            });

            return {
                hasItem: hasItem,
                hasError: hasError,
                errorMsg: errorMsg,
                data: result
            };
        }

        function refreshAddConfigBuilder() {
            var levelOId = $.trim($('#vipAddLevel').val() || '');
            var level = getLevelById(levelOId);
            var template = parseBenefitsTemplate(level);
            var lvCode = level ? level.lvCode : '';
            renderConfigBuilder('#vipAddConfigBuilder', template, template, lvCode);
        }

        function currentUpdateLvCode() {
            var selected = $.trim($('#vipUpdateLvCode').val() || '');
            return selected || state.currentRowLvCode || '';
        }

        function refreshUpdateConfigBuilder(configObj) {
            if (!$('#vipUpdateConfigEnabled').prop('checked')) {
                $('#vipUpdateConfigRow').hide();
                return;
            }

            var lvCode = currentUpdateLvCode();
            if (!lvCode) {
                $('#vipUpdateConfigRow').show();
                $('#vipUpdateConfigBuilder').html('<div class="vip-config-builder__empty">请先选择等级或点“填入编辑”加载用户当前等级</div>');
                return;
            }

            var level = getLevelByCode(lvCode);
            var template = parseBenefitsTemplate(level);
            if (!configObj) {
                configObj = state.currentRowConfig;
            }
            renderConfigBuilder('#vipUpdateConfigBuilder', template, configObj, lvCode);
            $('#vipUpdateConfigRow').show();
        }

        function collectFilters() {
            var payload = {
                page: state.page,
                pageSize: state.pageSize
            };

            var userName = $.trim($('#vipSearchUserName').val() || '');
            if (userName) {
                payload.userName = userName;
            }

            var lvCode = $.trim($('#vipSearchLvCode').val() || '');
            if (lvCode) {
                payload.lvCode = lvCode;
            }

            var st = $.trim($('#vipSearchState').val() || '');
            if (st !== '') {
                payload.state = Number(st);
            }

            return payload;
        }

        function fillSelectOptions() {
            var lvCodeSelect = $('#vipSearchLvCode');
            var addLevelSelect = $('#vipAddLevel');
            var updateLvCodeSelect = $('#vipUpdateLvCode');

            lvCodeSelect.find('option:not(:first)').remove();
            addLevelSelect.empty();
            updateLvCodeSelect.find('option:not(:first)').remove();

            state.levelById = {};
            state.levelByCode = {};

            var codeExists = {};
            for (var i = 0; i < state.levels.length; i++) {
                var level = state.levels[i] || {};
                var oId = level.oId || '';
                var lvCode = level.lvCode || '';
                var lvName = level.lvName || '';
                var price = Number(level.price || 0);
                var duration = Number(level.durationValue || 0);

                if (oId) {
                    state.levelById[oId] = level;
                    var addText = lvName + ' [' + lvCode + '] ' + duration + '天 / ' + price + '积分';
                    addLevelSelect.append('<option value="' + escapeHTML(oId) + '">' + escapeHTML(addText) + '</option>');
                }

                if (lvCode && !state.levelByCode[lvCode]) {
                    state.levelByCode[lvCode] = level;
                }

                if (lvCode && !codeExists[lvCode]) {
                    codeExists[lvCode] = true;
                    lvCodeSelect.append('<option value="' + escapeHTML(lvCode) + '">' + escapeHTML(lvCode) + '</option>');
                    updateLvCodeSelect.append('<option value="' + escapeHTML(lvCode) + '">' + escapeHTML(lvCode) + '</option>');
                }
            }

            refreshAddConfigBuilder();
            refreshUpdateConfigBuilder();
        }

        function loadLevels(callback) {
            $.ajax({
                url: '/api/membership/levels',
                type: 'GET',
                dataType: 'json',
                success: function (response) {
                    try {
                        var data = getSuccessData(response);
                        state.levels = $.isArray(data) ? data : [];
                        fillSelectOptions();
                        if (callback) {
                            callback();
                        }
                    } catch (e) {
                        notice('error', e.message);
                    }
                },
                error: function () {
                    notice('error', '读取会员等级失败');
                }
            });
        }

        function renderRows(items) {
            var $tbody = $('#vipMembershipTableBody');
            if (!items || !items.length) {
                $tbody.html('<tr><td colspan="7" class="ft-gray">暂无数据</td></tr>');
                return;
            }

            var html = '';
            for (var i = 0; i < items.length; i++) {
                var row = items[i] || {};
                var runtimeState = row.runtimeState || '';
                var stateText = row.state === 1 ? '生效' : '失效';
                if (runtimeState === 'expired') {
                    stateText = '已过期';
                }
                if (runtimeState === 'active') {
                    stateText = '生效';
                }

                var userName = row.userName || '';
                var userId = row.userId || '';
                var lvCode = row.lvCode || '';
                var expiresAt = Number(row.expiresAt || 0);
                var configJson = row.configJson || '';

                html += '<tr>' +
                    '<td>' + escapeHTML(userName) + '</td>' +
                    '<td>' + escapeHTML(userId) + '</td>' +
                    '<td>' + escapeHTML(lvCode) + '</td>' +
                    '<td>' + escapeHTML(stateText) + '</td>' +
                    '<td>' + escapeHTML(formatTime(expiresAt)) + '</td>' +
                    '<td>' + escapeHTML(formatTime(row.updatedAt)) + '</td>' +
                    '<td>' +
                    '<button type="button" class="vip-row-fill" data-user-id="' + escapeHTML(userId) + '" data-user-name="' + escapeHTML(userName) + '" data-lv-code="' + escapeHTML(lvCode) + '" data-state="' + escapeHTML(row.state) + '" data-expires-at="' + escapeHTML(expiresAt) + '" data-config-json="' + escapeHTML(configJson) + '">填入编辑</button> ' +
                    '<button type="button" class="green vip-row-extend" data-user-id="' + escapeHTML(userId) + '" data-user-name="' + escapeHTML(userName) + '">延长</button> ' +
                    '<button type="button" class="red vip-row-refund" data-user-id="' + escapeHTML(userId) + '" data-user-name="' + escapeHTML(userName) + '">退款</button>' +
                    '</td>' +
                    '</tr>';
            }

            $tbody.html(html);
        }

        function renderPagination() {
            var totalPages = Math.max(1, Math.ceil((state.total || 0) / state.pageSize));
            $('#vipPageInfo').text('第 ' + state.page + ' / ' + totalPages + ' 页，共 ' + (state.total || 0) + ' 条');
            $('#vipPrevPage').prop('disabled', state.page <= 1);
            $('#vipNextPage').prop('disabled', !state.hasMore);
        }

        function loadMemberships() {
            requestJSON('/api/admin/vip/list', collectFilters(), function (err, response) {
                if (err) {
                    notice('error', err.message);
                    return;
                }
                try {
                    var data = getSuccessData(response);
                    var items = $.isArray(data.items) ? data.items : [];
                    state.total = Number(data.total || 0);
                    state.hasMore = state.page * state.pageSize < state.total;
                    renderRows(items);
                    renderPagination();
                } catch (e) {
                    notice('error', e.message);
                }
            });
        }

        function doAddMembership() {
            var user = $.trim($('#vipAddUser').val() || '');
            var levelOId = $.trim($('#vipAddLevel').val() || '');

            if (!user) {
                notice('error', '请先填写用户');
                return;
            }
            if (!levelOId) {
                notice('error', '请先选择等级');
                return;
            }

            var addConfig = readConfigBuilder('#vipAddConfigBuilder');
            if (addConfig.hasError) {
                notice('error', addConfig.errorMsg);
                return;
            }

            var configJson = addConfig.hasItem ? JSON.stringify(addConfig.data) : '';
            requestJSON('/api/admin/vip/add', {
                userIdOrName: user,
                levelOId: levelOId,
                configJson: configJson
            }, function (err, response) {
                if (err) {
                    notice('error', err.message);
                    return;
                }
                try {
                    getSuccessData(response);
                    notice('success', '新增 VIP 成功');
                    loadMemberships();
                } catch (e) {
                    notice('error', e.message);
                }
            });
        }

        function doUpdateMembership() {
            var user = $.trim($('#vipUpdateUser').val() || '');
            if (!user) {
                notice('error', '请先填写用户');
                return;
            }

            var payload = {
                userIdOrName: user
            };

            var lvCode = $.trim($('#vipUpdateLvCode').val() || '');
            var stateVal = $.trim($('#vipUpdateState').val() || '');
            var expiresAt = $.trim($('#vipUpdateExpiresAt').val() || '');

            if (lvCode) {
                payload.lvCode = lvCode;
            }
            if (stateVal !== '') {
                payload.state = Number(stateVal);
            }
            if (expiresAt !== '') {
                payload.expiresAt = expiresAt;
            }

            if ($('#vipUpdateConfigEnabled').prop('checked')) {
                var useLvCode = currentUpdateLvCode();
                if (!useLvCode) {
                    notice('error', '请先选择等级，才能更新配置');
                    return;
                }

                var updateConfig = readConfigBuilder('#vipUpdateConfigBuilder');
                if (updateConfig.hasError) {
                    notice('error', updateConfig.errorMsg);
                    return;
                }
                payload.configJson = updateConfig.hasItem ? JSON.stringify(updateConfig.data) : '';
            }

            requestJSON('/api/admin/vip/update', payload, function (err, response) {
                if (err) {
                    notice('error', err.message);
                    return;
                }
                try {
                    getSuccessData(response);
                    notice('success', '更新 VIP 成功');
                    loadMemberships();
                } catch (e) {
                    notice('error', e.message);
                }
            });
        }

        function executeExtend(userIdOrName) {
            var user = $.trim(userIdOrName || $('#vipUpdateUser').val() || '');
            if (!user) {
                notice('error', '请先填写用户');
                return;
            }

            var days = parseInt($.trim($('#vipExtendDays').val() || ''), 10);
            if (!days || days <= 0) {
                notice('error', '请填写大于 0 的延长天数');
                return;
            }

            requestJSON('/api/admin/vip/extend', {
                userIdOrName: user,
                days: days
            }, function (err, response) {
                if (err) {
                    notice('error', err.message);
                    return;
                }

                try {
                    var data = getSuccessData(response);
                    var newExpiresAt = Number(data.newExpiresAt || 0);
                    if (newExpiresAt > 0) {
                        $('#vipUpdateExpiresAt').val(String(newExpiresAt));
                    }
                    notice('success', '已延长 ' + days + ' 天，新到期：' + formatTime(newExpiresAt));
                    loadMemberships();
                } catch (e) {
                    notice('error', e.message);
                }
            });
        }

        function executeRefund(userIdOrName) {
            var user = $.trim(userIdOrName || '');
            if (!user) {
                notice('error', '请先填写用户');
                return;
            }

            if (!window.confirm('确认执行 VIP 按天退款？退款后会员会立即失效。')) {
                return;
            }

            requestJSON('/api/admin/vip/refund', {
                userIdOrName: user
            }, function (err, response) {
                if (err) {
                    notice('error', err.message);
                    return;
                }

                try {
                    var data = getSuccessData(response);
                    var refundPoints = Number(data.refundPoints || 0);
                    var remainingDays = Number(data.remainingDays || 0);
                    notice('success', '退款成功：返还 ' + refundPoints + ' 积分（剩余 ' + remainingDays + ' 天）');
                    loadMemberships();
                } catch (e) {
                    notice('error', e.message);
                }
            });
        }

        function bindEvents() {
            $('#vipSearchBtn').on('click', function () {
                state.page = 1;
                loadMemberships();
            });

            $('#vipResetBtn').on('click', function () {
                $('#vipSearchUserName').val('');
                $('#vipSearchLvCode').val('');
                $('#vipSearchState').val('');
                state.page = 1;
                loadMemberships();
            });

            $('#vipPrevPage').on('click', function () {
                if (state.page <= 1) {
                    return;
                }
                state.page -= 1;
                loadMemberships();
            });

            $('#vipNextPage').on('click', function () {
                if (!state.hasMore) {
                    return;
                }
                state.page += 1;
                loadMemberships();
            });

            $('#vipAddLevel').on('change', function () {
                refreshAddConfigBuilder();
            });

            $('#vipUpdateLvCode').on('change', function () {
                refreshUpdateConfigBuilder();
            });

            $('#vipUpdateConfigEnabled').on('change', function () {
                refreshUpdateConfigBuilder();
            });

            $('#vipUpdateConfigBuilder').on('change', '.vip4-color-select', function () {
                var $select = $(this);
                var $custom = $select.closest('.vip-config-item').find('.vip-config-custom');
                if ($select.val() === '__custom__') {
                    $custom.show();
                } else {
                    $custom.hide();
                }
            });

            $('#vipAddConfigBuilder').on('change', '.vip4-color-select', function () {
                var $select = $(this);
                var $custom = $select.closest('.vip-config-item').find('.vip-config-custom');
                if ($select.val() === '__custom__') {
                    $custom.show();
                } else {
                    $custom.hide();
                }
            });

            $('#vipAddBtn').on('click', doAddMembership);
            $('#vipUpdateBtn').on('click', doUpdateMembership);
            $('#vipExtendBtn').on('click', function () {
                executeExtend($('#vipUpdateUser').val());
            });
            $('#vipRefundBtn').on('click', function () {
                executeRefund($('#vipRefundUser').val());
            });

            $('#vipClearExpiresBtn').on('click', function () {
                $('#vipUpdateExpiresAt').val('');
            });

            $('#vipMembershipTableBody').on('click', '.vip-row-fill', function () {
                var $btn = $(this);
                var userName = $btn.attr('data-user-name') || '';
                var userId = $btn.attr('data-user-id') || '';
                var lvCode = $btn.attr('data-lv-code') || '';
                var stateVal = $btn.attr('data-state') || '';
                var expiresAt = $btn.attr('data-expires-at') || '';
                var configJson = $btn.attr('data-config-json') || '';

                $('#vipUpdateUser').val(userName || userId);
                $('#vipRefundUser').val(userName || userId);
                $('#vipUpdateLvCode').val(lvCode);
                $('#vipUpdateState').val(stateVal);
                $('#vipUpdateExpiresAt').val(expiresAt);

                state.currentRowLvCode = lvCode;
                state.currentRowConfig = parseJSONSafe(configJson, {});

                $('#vipUpdateConfigEnabled').prop('checked', true);
                refreshUpdateConfigBuilder(state.currentRowConfig);
                notice('success', '已填入编辑区域');
            });

            $('#vipMembershipTableBody').on('click', '.vip-row-extend', function () {
                var $btn = $(this);
                var user = $btn.attr('data-user-name') || $btn.attr('data-user-id');
                $('#vipUpdateUser').val(user);
                executeExtend(user);
            });

            $('#vipMembershipTableBody').on('click', '.vip-row-refund', function () {
                var $btn = $(this);
                executeRefund($btn.attr('data-user-name') || $btn.attr('data-user-id'));
            });
        }

        bindEvents();
        loadLevels(function () {
            loadMemberships();
        });

        return true;
    }

    if (!initVipAdmin()) {
        var retry = 0;
        var timer = window.setInterval(function () {
            retry += 1;
            if (initVipAdmin() || retry >= 200) {
                window.clearInterval(timer);
            }
        }, 50);
    }
})();
