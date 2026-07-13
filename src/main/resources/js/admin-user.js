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
