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
/* 新旧网页共用服务端会话。只在切回页面时核对，不轮询、不读取 HttpOnly Cookie。 */
(function () {
    'use strict';
    var script = document.currentScript;
    if (!script || script.getAttribute('data-session-managed') !== 'true') return;
    var actor = script.getAttribute('data-current-user') || null;
    var pending = false;
    var lastCheck = 0;
    var navigating = false;
    var timer = null;

    function checkSession() {
        if (document.visibilityState !== 'visible' || pending || navigating) return;
        var remaining = 5000 - (Date.now() - lastCheck);
        if (remaining > 0) {
            // 快速切回也保留一次尾部检查，避免退出事件被节流丢弃。
            if (timer === null) timer = setTimeout(function () { timer = null; checkSession(); }, remaining);
            return;
        }
        if (timer !== null) { clearTimeout(timer); timer = null; }
        pending = true;
        lastCheck = Date.now();
        fetch('/api/v3/auth/session', {
            method: 'POST', credentials: 'same-origin', cache: 'no-store',
            headers: {'Content-Type': 'application/json'}, body: '{}'
        }).then(function (response) {
            if (!response.ok) return null;
            return response.json();
        }).then(function (result) {
            if (!result || result.code !== 0 || !result.data || result.data.linked === false) return;
            var current = result.data.userId;
            if (current !== null && typeof current !== 'string') return;
            if (current !== actor) {
                navigating = true;
                window.location.reload();
            }
        }).catch(function () {
            // 网络错误不当作退出，等用户下次切回页面再检查。
        }).finally(function () { pending = false; });
    }

    window.addEventListener('focus', checkSession);
    document.addEventListener('visibilitychange', checkSession);
}());
