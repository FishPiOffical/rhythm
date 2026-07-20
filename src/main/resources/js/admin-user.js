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
var ADMIN_USER_NOTICE_DURATION = 3000
var ADMIN_USER_REDIRECT_DELAY = 1500

var AdminUser = {
  deactivate: function (userId, userName, csrfToken) {
    if (!confirm('确定永久停用“' + userName + '”？账号资料将被清除。')) {
      return
    }
    if (!confirm('再次确认：停用后无法恢复，原手机号可重新注册。')) {
      return
    }

    $.ajax({
      url: Label.servePath + '/admin/user/' + userId + '/deactivate',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      success: function (result) {
        if (result.code !== 0) {
          Util.notice('warning', ADMIN_USER_NOTICE_DURATION, result.msg)
          return
        }
        Util.notice('success', ADMIN_USER_REDIRECT_DELAY, result.msg)
        setTimeout(function () {
          window.location.href = Label.servePath + '/admin/users'
        }, ADMIN_USER_REDIRECT_DELAY)
      },
      error: function (request) {
        Util.notice('warning', ADMIN_USER_NOTICE_DURATION,
          request.status + ' ' + request.statusText)
      },
    })
  },
}
