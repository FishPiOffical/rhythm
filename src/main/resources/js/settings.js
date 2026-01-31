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
 * @fileoverview settings.
 *
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @version 1.27.0.1, Mar 17, 2019
 */

/**
 * @description Settings function
 * @static
 */
var Settings = {
  submitIdentity: function () {
    let info = {};
    info.type = $("#id-type").children('option:selected').val();
    let pause = false;
    switch (info.type) {
      case 'LGBT 群体认证':
        if (idCert === '') {
          alert('上传资料不完整，请重新上传');
          pause = true;
        }
        break;
      case '00 后认证':
        if (idCert === '' || idId === '') {
          alert('上传资料不完整，请重新上传');
          pause = true;
        }
        break;
      default:
        if (idCert === '') {
          alert('上传资料不完整，请重新上传');
          pause = true;
        }
        break;
    }
    if (!pause) {
      info.idCert = idCert;
      info.idId = idId;
      $.ajax({
        url: Label.servePath + '/user/identify',
        type: 'POST',
        data: JSON.stringify(info),
        success: function (result) {
          alert(result.msg);
          location.reload();
        },
      });
    }
  },
  initIdentity: function () {
    $("#id-type").change(function () {
      let selected = $(this).children('option:selected').val();
      Settings.changeIdentityType(selected);
    });
    Settings.changeIdentityType('企业入驻认证');
  },
  changeIdentityType: function (selected) {
    idCert = '';
    idId = '';
    let html = '';
    switch (selected) {
      case '企业入驻认证':
        html += '' +
            '<div class="fn-clear">申请企业认证，我们会通过私信联系您补交企业信息、官网、Logo等内容，以用来定制专属勋章，请耐心等待。</div><br>\n' +
            '<div class="fn-clear" style="margin: 0 30px 20px 0; display: inline-block">\n' +
            '    <div class="avatar-big" id="id-cert"\n' +
            '         onclick="$(\'#id-cert-upload input\').click()"' +
            '         style="background-image:url(https://file.fishpi.cn/id/%E8%90%A5%E4%B8%9A%E6%89%A7%E7%85%A7%E5%89%AF%E6%9C%AC%E5%A4%8D%E5%8D%B0%E4%BB%B6.png)"></div>\n' +
            '</div>\n' +
            '<form id="id-cert-upload" style="display: none" method="POST" enctype="multipart/form-data">\n' +
            '        <input type="file" name="file">\n' +
            '</form>' +
            '';
        break;
      case '小姐姐认证':
        html += '' +
            '<div class="fn-clear" style="margin: 0 30px 20px 0; display: inline-block">\n' +
            '    <div class="avatar-big" id="id-cert"\n' +
            '         onclick="$(\'#id-cert-upload input\').click()"\n' +
            '         style="background-image:url(https://file.fishpi.cn/id/%E6%89%8B%E5%86%99%E7%A4%BE%E5%8C%BAID%E8%87%AA%E6%8B%8D%E7%85%A7.png)"></div>\n' +
            '</div>\n' +
            '<form id="id-cert-upload" style="display: none" method="POST" enctype="multipart/form-data">\n' +
            '        <input type="file" name="file">\n' +
            '</form>' +
            '';
        break;
      case 'LGBT 群体认证':
        html += '' +
            '<div class="fn-clear" style="margin: 0 30px 20px 0; display: inline-block">\n' +
            '    <div class="avatar-big" id="id-cert"\n' +
            '         onclick="$(\'#id-cert-upload input\').click()"\n' +
            '         style="background-image:url(https://file.fishpi.cn/id/%E8%BA%AB%E4%BB%BD%E8%AF%81%E6%98%8E.png)"></div>\n' +
            '</div>\n' +
            '<form id="id-cert-upload" style="display: none" method="POST" enctype="multipart/form-data">\n' +
            '        <input type="file" name="file">\n' +
            '</form>' +
            '';
        break;
      case '00 后认证':
        html += '' +
            '<div class="fn-clear" style="margin: 0 30px 20px 0; display: inline-block">\n' +
            '    <div class="avatar-big" id="id-cert"\n' +
            '         onclick="$(\'#id-cert-upload input\').click()"\n' +
            '         style="background-image:url(https://file.fishpi.cn/id/%E6%89%8B%E6%8C%81%E8%BA%AB%E4%BB%BD%E8%AF%81%E8%87%AA%E6%8B%8D%E7%85%A7.png)"></div>\n' +
            '</div>\n' +
            '<form id="id-cert-upload" style="display: none" method="POST" enctype="multipart/form-data">\n' +
            '        <input type="file" name="file">\n' +
            '</form>' +
            '';
        html += '' +
            '<div class="fn-clear" style="margin: 0 30px 20px 0; display: inline-block">\n' +
            '    <div class="avatar-big" id="id-id"\n' +
            '         onclick="$(\'#id-id-upload input\').click()"\n' +
            '         style="background-image:url(https://file.fishpi.cn/id/%E6%89%8B%E5%86%99%E7%A4%BE%E5%8C%BAID%E8%87%AA%E6%8B%8D%E7%85%A7.png)"></div>\n' +
            '</div>\n' +
            '<form id="id-id-upload" style="display: none" method="POST" enctype="multipart/form-data">\n' +
            '        <input type="file" name="file">\n' +
            '</form>' +
            '';
        break;
    }
    $('#id-content').html(html);
    Settings.initUploadAvatar({
      id: 'id-cert-upload',
      userId: '${currentUser.oId}',
      maxSize: '${imgMaxSize?c}'
    }, function (data) {
      var uploadKey = data.result.key;
      $('#id-cert').css("background-image", 'url(' + uploadKey + ')').data('imageurl', uploadKey);
      idCert = uploadKey;
    });
    Settings.initUploadAvatar({
      id: 'id-id-upload',
      userId: '${currentUser.oId}',
      maxSize: '${imgMaxSize?c}'
    }, function (data) {
      var uploadKey = data.result.key;
      $('#id-id').css("background-image", 'url(' + uploadKey + ')').data('imageurl', uploadKey);
      idId = uploadKey;
    });
  },
  /**
   * 解绑两步验证
   */
  removeMFA: function () {
    $.ajax({
      url: Label.servePath + '/mfa/remove',
      type: 'GET',
      cache: false,
      success: function (result) {
        if (0 === result.code) {
          alert(result.msg);
          location.reload();
        } else {
          Util.notice('warning', 2000, result.msg);
        }
      }
    });
  },
  /**
   * 绑定两步验证
   */
  verifyMFA: function () {
    let code = $("#mfaVerifyCode").val();
    $.ajax({
      url: Label.servePath + '/mfa/verify?code=' + code,
      type: 'GET',
      cache: false,
      success: function (result) {
        if (0 === result.code) {
          alert(result.msg);
          location.reload();
        } else {
          Util.notice('warning', 2000, result.msg);
          $("#mfaVerifyCode").val("");
        }
      }
    });
  },
  /**
   * 初始化两步验证信息
   */
  initMFA: function () {
    $.ajax({
      url: Label.servePath + '/mfa/enabled',
      type: 'GET',
      cache: false,
      success: function (result) {
        if (0 === result.code) {
          // 已有MFA
          $("#mfaCode").append("<label><svg><use xlink:href=\"#safe\"></use></svg> 验证器已启用，保护中</label><br><br><br>");
          $("#mfaCode").append("<p>" +
              "    您已绑定两步验证器，账户安全等级高。<br>如需更换绑定设备，请解绑后重新绑定。" +
              "</p>");
          $("#mfaCode").append("<br><button class=\"fn-right\" onclick=\"Settings.removeMFA()\">解绑</button>");
        } else {
          // 没有MFA
          $("#mfaCode").append("<label><svg><use xlink:href=\"#unsafe\"></use></svg> 未在保护中</label><br><br><br>");
          $("#mfaCode").append("<p>" +
              "    两步验证可以极大增强您的账户安全性，<a href=\"https://fishpi.cn/article/1650648000379\" target=\"_blank\">使用指南</a><br>" +
              "    为防止意外丢失，建议您备份二维码下方的手动输入代码。<br>" +
              "    请使用两步验证器扫描二维码绑定 (推荐使用 Authenticator)" +
              "</p>");
          $.ajax({
            url: Label.servePath + '/mfa',
            type: 'GET',
            cache: false,
            success: function (result) {
              if (0 === result.code) {
                $("#mfaCode").append("<br>");
                $("#mfaCode").append("<img src='" + result.qrCodeLink + "'/>");
                $("#mfaCode").append("<br>");
                $("#mfaCode").append("<p>或手动输入代码：" + result.secret + "</p>");
                $("#mfaCode").append("<br>");
                $("#mfaCode").append("<p>绑定成功后，请输入一次性密码用于验证，并点击绑定按钮：</p>");
                $("#mfaCode").append("<input id=\"mfaVerifyCode\" type=\"text\" />")
                $("#mfaCode").append("<br><br>");
                $("#mfaCode").append("<button class=\"fn-right\" onclick=\"Settings.verifyMFA()\">绑定</button>");
              } else {
                $("#mfaCode").append("获取2FA信息失败，请联系管理员");
              }
            }
          });

        }
      },
    });
  },
  /**
   * 加载扫码登录APP功能
   */
  initApiCode: function(){
    $.ajax({
      url: Label.servePath + '/getApiKeyInWeb',
      type: 'GET',
      cache: false,
      success: function (result) {
        if (result.apiKey !== "") {
          $("#apiCode").append("<label><svg><use xlink:href=\"#safe\"></use></svg> 请使用官方APP扫码以登录APP </label><br><br><br>");
          $("#apiCode").append("<p>为了保护账号安全,请勿将本二维码以任何方式分享给他人,请勿在任何地点分享此二维码内容</p>");
          $("#apiCode").append("<br>");
          $("#apiCode").append("<img src='https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=login:" + result.apiKey + "'/>");
        }
      },
    });
  },
  /**
   * 初始化背包
   */
  remainCheckin: 0,
  initBag: function (sysBag) {
    let html = '';
    let bag = sysBag;
    if (bag.checkin1day !== undefined && bag.checkin1day > 0) {
      html += '<button style="margin:0 5px 5px 0" onclick="Settings.use1dayCheckinCard(\'' + Label.csrfToken + '\')">单日免签卡 x' + bag.checkin1day + '</button>';
    }
    if (bag.checkin2days !== undefined && bag.checkin2days > 0) {
      html += '<button style="margin:0 5px 5px 0" onclick="Settings.use2dayCheckinCard(\'' + Label.csrfToken + '\')">两天免签卡 x' + bag.checkin2days + '</button>';
    }
    if (bag.nameCard !== undefined && bag.nameCard > 0) {
      html += '<button style="margin:0 5px 5px 0" onclick="Settings.useNameCard(\'' + Label.csrfToken + '\')">改名卡 x' + bag.nameCard + '</button>';
    }
    if (bag.metalTicket !== undefined && bag.metalTicket > 0) {
      html += '<button style="margin:0 5px 5px 0" onclick="alert(\'您已取得摸鱼派一周年纪念勋章领取权限，请静待系统公告\')">摸鱼派一周年纪念勋章领取券 x' + bag.metalTicket + '</button>';
    }
    if (bag.patchCheckinCard !== undefined && bag.patchCheckinCard > 0) {
      html += '<button style="margin:0 5px 5px 0" onclick="Settings.usePatchCheckinCard(\'' + Label.csrfToken + '\', ' + bag.patchStart + ')">补签卡 x' + bag.patchCheckinCard + '</button>';
    }

    // 下面内容不要变更顺序
    if (bag.sysCheckinRemain !== undefined && bag.sysCheckinRemain > 0) {
      html += '<br><div style="float: left;font-size: 12px;color: rgba(0,0,0,0.38);">免签卡生效中，剩余' + bag.sysCheckinRemain + '天</div>';
      Settings.remainCheckin = bag.sysCheckinRemain;
    }
    if (html === '') {
      html = '你的背包和钱包一样，是空的。';
    }
    document.getElementById("bag").innerHTML = html;
  },
  /**
   * 初始化勋章（使用 MedalProcessor 用户侧接口）
   */
  initMetal: function () {
    Settings.loadMyMedals();
  },
  /**
   * 拉取当前用户的所有勋章
   */
  loadMyMedals: function () {
    $.ajax({
      url: Label.servePath + '/api/medal/my/list',
      type: 'POST',
      cache: false,
      data: JSON.stringify({}),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (result && result.code === 0) {
          var list = result.data || [];
          Settings.renderMyMedals(list);
        } else {
          document.getElementById('metal').innerHTML = '加载勋章失败，请稍后再试。';
        }
      },
      error: function () {
        document.getElementById('metal').innerHTML = '加载勋章失败，请检查网络。';
      }
    });
  },
  /**
   * 将 expire_time（毫秒）格式化为日期字符串
   */
  _formatExpireTime: function (ts) {
    if (!ts || ts <= 0) {
      return '永久';
    }
    try {
      var d = new Date(ts);
      if (isNaN(d.getTime())) {
        return String(ts);
      }
      var y = d.getFullYear();
      var m = ('0' + (d.getMonth() + 1)).slice(-2);
      var day = ('0' + d.getDate()).slice(-2);
      return y + '-' + m + '-' + day;
    } catch (e) {
      return String(ts);
    }
  },

  /**
   * 渲染当前用户勋章列表
   */
  renderMyMedals: function (list) {
    var html = '';
    if (list && list.length > 0) {
      // 按 display_order 升序排序，未设置的排后
      list.sort(function (a, b) {
        var oa = typeof a.display_order === 'number' ? a.display_order : 0;
        var ob = typeof b.display_order === 'number' ? b.display_order : 0;
        return oa - ob;
      });
      for (var i = 0; i < list.length; i++) {
        var m = list[i];
        var medalId = m.medal_id || m.medalId || '';
        var name = m.medal_name || m.name || '';
        var desc = m.medal_description || m.description || '';
        var type = m.medal_type || m.type || '普通'; // 勋章类型
        var display = (typeof m.display === 'boolean') ? m.display : true;
        var order = (typeof m.display_order === 'number') ? m.display_order : 0;
        var expireTime = m.expire_time || m.expireTime || 0;
        var expireText = Settings._formatExpireTime(expireTime);

        // 按类型选择样式：普通、精良、稀有、史诗、传说、神话、限定
        var typeStyle = Util.renderMedalType(type);
        var btn;
        if (display) {
          // 已佩戴 -> 卸下，用红色按钮
          btn = '<button class="btn red" onclick="Settings.toggleMyMedalDisplay(\'' + medalId + '\', false)">卸下</button>';
        } else {
          // 未佩戴 -> 佩戴，用绿色按钮
          btn = '<button class="btn green" onclick="Settings.toggleMyMedalDisplay(\'' + medalId + '\', true)">佩戴</button>';
        }
        // 排序按钮只用箭头表示：↑ 上移 / ↓ 下移
        var orderBtns = '' +
            '<button class="btn btn-secondary" style="margin-left:4px" onclick="Settings.changeMyMedalOrder(\'' + medalId + '\', \'up\')">↑</button>' +
            '<button class="btn btn-secondary" style="margin-left:4px" onclick="Settings.changeMyMedalOrder(\'' + medalId + '\', \'down\')">↓</button>';

        html += '' +
          '<div class="fn__flex" style="justify-content: space-between; align-items: center; margin-bottom: 10px">' +
            '<div>' +
              '<label style="margin: 0">' +
                '<div>' +
                  '<img src="' + Util.genMetal(medalId) + '" style="border-radius:4px;object-fit:cover;"/>' +
                  '<br>' +
                  '<span style="font-size: 12px;">' +
                    name +
                    ' <span style="font-size:11px;margin-left:4px;' + typeStyle + '">[' + type + ']</span>' +
                    (desc ? ' (' + desc + ')' : '') +
                  '</span>' +
                  '<br>' +
                  '<span style="font-size: 11px; color: rgba(0,0,0,0.54);">过期时间：' + expireText + '</span>' +
                '</div>' +
              '</label>' +
            '</div>' +
            // 右边一行放所有操作按钮：佩戴/卸下 + 上移 + 下移
            '<div style="text-align:right; display:flex; flex-direction:column; align-items:flex-end;">' +
              '<div style="display:flex; align-items:center; justify-content:flex-end;">' +
                 btn +
                 orderBtns +
              '</div>' +
            '</div>' +
          '</div>';
      }
    }
    if (html === '') {
      html = '鱼战士，你还没有任何勋章！';
    }
    document.getElementById('metal').innerHTML = html;
  },

  /**
   * 拉取指定用户展示中的勋章（用于主页侧边栏）
   */
  loadUserMedals: function (userId, userName) {
    if (!document.getElementById('metal')) {
      return;
    }
    var payload = {};
    if (userId) {
      payload.userId = userId;
    } else if (userName) {
      payload.userName = userName;
    } else {
      return;
    }
    $.ajax({
      url: Label.servePath + '/api/medal/user/list',
      type: 'POST',
      cache: false,
      data: JSON.stringify(payload),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (result && result.code === 0) {
          var list = result.data || [];
          Settings.renderUserMedals(list);
        } else {
          // 主页侧边栏失败就简单忽略即可
        }
      },
      error: function () {
        // 忽略错误，避免打扰用户
      }
    });
  },

  /**
   * 在主页侧边栏渲染指定用户的勋章（简单图标列表）
   */
  renderUserMedals: function (list) {
    var el = document.getElementById('metal');
    if (!el) {
      return;
    }
    if (!list || list.length === 0) {
      el.innerHTML = '';
      return;
    }
    // 按 display_order 排序
    list.sort(function (a, b) {
      var oa = typeof a.display_order === 'number' ? a.display_order : 0;
      var ob = typeof b.display_order === 'number' ? b.display_order : 0;
      return oa - ob;
    });
    var html = '';
    for (var i = 0; i < list.length; i++) {
      var m = list[i];
      html += Util.genMedalTooltips("<img src='" + Util.genMetal(m.medal_id) + "'/>", m.medal_type, m.medal_name + ' - ' + m.medal_description);
    }
    el.innerHTML = html;
  },

  /**
   * 佩戴/卸下勋章 -> /api/medal/my/display
   */
  toggleMyMedalDisplay: function (medalId, display) {
    $.ajax({
      url: Label.servePath + '/api/medal/my/display',
      type: 'POST',
      cache: false,
      data: JSON.stringify({
        medalId: medalId,
        display: display
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (result && result.code === 0) {
          Settings.loadMyMedals();
        } else {
          Util.notice('warning', 2000, '操作失败，请稍后再试');
        }
      },
      error: function () {
        Util.notice('warning', 2000, '网络错误，请稍后再试');
      }
    });
  },
  /**
   * 修改勋章排序 -> /api/medal/my/reorder
   * @param medalId 勋章ID
   * @param direction "up" 或 "down"
   */
  changeMyMedalOrder: function (medalId, direction) {
    if (direction !== 'up' && direction !== 'down') {
      return;
    }
    $.ajax({
      url: Label.servePath + '/api/medal/my/reorder',
      type: 'POST',
      cache: false,
      data: JSON.stringify({
        medalId: medalId,
        direction: direction
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (result && result.code === 0) {
          Settings.loadMyMedals();
        } else {
          Util.alert('排序失败，请稍后再试');
        }
      },
      error: function () {
        Util.alert('网络错误，请稍后再试');
      }
    });
  },
  /**
   * 使用改名卡
   * @param csrfToken
   */
  useNameCard: function (csrfToken) {
    var username = prompt("请输入要修改的用户名", "");
    if (username === null || username === "") {
      alert("您没有输入用户名，取消使用改名卡。");
    } else {
      $.ajax({
        url: Label.servePath + '/bag/nameCard',
        type: 'POST',
        async: false,
        headers: {'csrfToken': csrfToken},
        data: JSON.stringify({
          userName: username
        }),
        success: function (result) {
          alert(result.msg);
          location.reload();
        }
      })
    }
  },
  /**
   * 使用补签卡
   * @param csrfToken
   */
  usePatchCheckinCard: function (csrfToken, record) {
    if (record == undefined) {
      alert("您没有可以补签的记录！");
    } else {
      if (confirm('补签卡仅适用于断签一天的情况！\n在使用补签卡后，你的签到记录将提前至日期：' + record + '\n确定继续吗？') === true) {
        $.ajax({
          url: Label.servePath + '/bag/patchCheckin',
          type: 'GET',
          async: false,
          headers: {'csrfToken': csrfToken},
          success: function (result) {
            alert(result.msg);
            location.reload();
          }
        })
      }
    }
  },
  /**
   * 使用单日免签卡
   * @param csrfToken
   */
  use1dayCheckinCard: function (csrfToken) {
    let confirmText = '使用单日免签卡后，明天的签到将由系统自动帮你完成，不需要登录摸鱼派。确定使用吗？';
    if (Settings.remainCheckin > 0) {
      confirmText = '您已经有生效中的免签卡，如继续使用时间将继续叠加，确认继续吗？';
    }
    if (confirm(confirmText) === true) {
      $.ajax({
        url: Label.servePath + '/bag/1dayCheckin',
        type: 'GET',
        async: false,
        headers: {'csrfToken': csrfToken},
        success: function (result) {
          alert(result.msg);
          location.reload();
        }
      })
    }
  },
  /**
   * 使用两天免签卡
   * @param csrfToken
   */
  use2dayCheckinCard: function (csrfToken) {
    let confirmText = '使用两天免签卡后，明天和后天的签到将由系统自动帮你完成，不需要登录摸鱼派。确定使用吗？';
    if (Settings.remainCheckin > 0) {
      confirmText = '您已经有生效中的免签卡，如继续使用时间将继续叠加，确认继续吗？';
    }
    if (confirm(confirmText) === true) {
      $.ajax({
        url: Label.servePath + '/bag/2dayCheckin',
        type: 'GET',
        async: false,
        headers: {'csrfToken': csrfToken},
        success: function (result) {
          alert(result.msg);
          location.reload();
        }
      })
    }
  },
  /**
   * 举报
   * @param it
   */
  report: function (it) {
    var $btn = $(it)
    $btn.attr('disabled', 'disabled').css('opacity', '0.3')
    $.ajax({
      url: Label.servePath + '/report',
      type: 'POST',
      cache: false,
      data: JSON.stringify({
        reportDataId: $('#reportDialog').data('id'),
        reportDataType: 2,
        reportType: $('input[name=report]:checked').val(),
        reportMemo: $('#reportTextarea').val(),
      }),
      complete: function (result) {
        $btn.removeAttr('disabled').css('opacity', '1')
        if (result.responseJSON.code === 0) {
          Util.alert(Label.reportSuccLabel)
          $('#reportTextarea').val('')
          $('#reportDialog').dialog('close')
        } else {
          Util.alert(result.responseJSON.msg)
        }
      },
    })
  },
  /**
   * 获取手机验证码
   * @param csrfToken
   * @param result
   */
  getPhoneCaptcha: function (csrfToken, result) {
    $('#phoneGetBtn').attr('disabled', 'disabled').css('opacity', '0.3')
    $.ajax({
      url: Label.servePath + '/settings/phone/vc',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      data: JSON.stringify({
        userPhone: $('#phoneInput').val(),
        captcha: result
      }),
      success: function (result) {
        if (0 === result.code) {
          $('#phoneInput').prop('disabled', true)
          $('#phone_captch').hide()
          $('#phoneCodePanel').show()
          $('#phoneCode').show().focus()
          $('#phoneSubmitBtn').show()
          $('#phoneGetBtn').hide()
        }
        Util.alert(result.msg)
        $('#phoneGetBtn').removeAttr('disabled').css('opacity', '1')
      },
      error: function (result) {
        Util.alert(result.statusText)
      }
    })
  },
  /**
   * 更新手机
   */
  updatePhone: function (csrfToken) {
    $('#phoneSubmitBtn').attr('disabled', 'disabled').css('opacity', '0.3')
    $.ajax({
      url: Label.servePath + '/settings/phone',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      data: JSON.stringify({
        userPhone: $('#phoneInput').val(),
        captcha: $('#phoneCode').val(),
      }),
      success: function (result) {
        if (0 === result.code) {
          $('#phone_captch').show()
          $('#phoneVerify').val('')
          $('#phoneCodePanel').hide()
          $('#phoneCode').val('')
          $('#phoneSubmitBtn').hide()
          $('#phoneGetBtn').show()
          $('#phoneInput').prop('disabled', false)
          $('#phone_captch img').click()
          Util.alert(Label.updateSuccLabel)
        } else {
          if (result.code === 1) {
            $('#phone_captch').show()
            $('#phoneVerify').val('')
            $('#phoneCodePanel').hide()
            $('#phoneSubmitBtn').hide()
            $('#phoneGetBtn').show()
            $('#phoneInput').prop('disabled', false)
            $('#phone_captch img').click()
          }
          Util.alert(result.msg)
        }
        $('#phoneSubmitBtn').removeAttr('disabled').css('opacity', '1')
      },
    })
  },
  /**
   * 获取邮箱验证码
   * @param csrfToken
   */
  getEmailCaptcha: function (csrfToken) {
    $('#emailGetBtn').attr('disabled', 'disabled').css('opacity', '0.3')
    $.ajax({
      url: Label.servePath + '/settings/email/vc',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      data: JSON.stringify({
        userEmail: $('#emailInput').val(),
        captcha: $('#emailVerify').val(),
      }),
      success: function (result) {
        if (0 === result.code) {
          $('#emailInput').prop('disabled', true)
          $('#email_captch').hide()
          $('#emailCodePanel').show()
          $('#emailCode').show().focus()
          $('#emailSubmitBtn').show()
          $('#emailGetBtn').hide()
        }
        Util.alert(result.msg)
        $('#emailGetBtn').removeAttr('disabled').css('opacity', '1')
      },
    })
  },
  /**
   * 更新邮箱
   */
  updateEmail: function (csrfToken) {
    $('#emailSubmitBtn').attr('disabled', 'disabled').css('opacity', '0.3')
    $.ajax({
      url: Label.servePath + '/settings/email',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      data: JSON.stringify({
        userEmail: $('#emailInput').val(),
        captcha: $('#emailCode').val(),
      }),
      success: function (result) {
        if (0 === result.code) {
          $('#email_captch').show()
          $('#emailVerify').val('')
          $('#emailCodePanel').hide()
          $('#emailCode').val('')
          $('#emailSubmitBtn').hide()
          $('#emailGetBtn').show()
          $('#emailInput').prop('disabled', false)
          $('#email_captch img').click()
          Util.alert(Label.updateSuccLabel)
        } else {
          if (result.code === 1) {
            $('#email_captch').show()
            $('#emailVerify').val('')
            $('#emailCodePanel').hide()
            $('#emailSubmitBtn').hide()
            $('#emailGetBtn').show()
            $('#emailInput').prop('disabled', false)
            $('#email_captch img').click()
          }
          Util.alert(result.msg)
        }
        $('#emailSubmitBtn').removeAttr('disabled').css('opacity', '1')
      },
    })
  },
  /**
   * 个人主页滚动固定
   */
  homeScroll: function () {
    $('.nav-tabs').html($('.home-menu').html())
    $('.nav').css({
      'position': 'fixed',
      'box-shadow': '0 1px 2px rgba(0,0,0,.2)',
    })
    $('.main').css('paddingTop', '68px')
  },
  /**
   * 通知页面侧边栏滚动固定
   */
  notiScroll: function () {
    var $side = $('#side'),
      width = $side.width(),
      maxScroll = $('.small-tips').closest('.module').length === 1 ? 109 +
        $('.small-tips').closest('.module').height() : 89
    $('.side.fn-none').height($side.height())
    $(window).scroll(function () {
      if ($(window).scrollTop() > maxScroll) {
        $side.css({
          position: 'fixed',
          width: width + 'px',
          top: 0,
          right: $('.wrapper').css('margin-right'),
        })

        $('.side.fn-none').show()
        $('.small-tips').closest('.module').hide()
      } else {
        $side.removeAttr('style')

        $('.side.fn-none').hide()
        $('.small-tips').closest('.module').show()
      }
    })
  },
  /**
   * 初始化个人设置中的头像图片上传.
   *
   * @returns {Boolean}
   */
  initUploadAvatar: function (params, succCB) {
    var ext = ''
    $('#' + params.id).fileupload({
      acceptFileTypes: /(\.|\/)(gif|jpe?g|png)$/i,
      maxFileSize: parseInt(params.maxSize),
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

            if (evt.target.result.byteLength > 1024 * 1024 * 20) {
              Util.alert('图片过大 (最大限制 20M)')

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
        succCB(result)
      },
      fail: function (e, data) {
        Util.alert('Upload error: ' + data.errorThrown)
      },
    }).on('fileuploadprocessalways', function (e, data) {
      var currentFile = data.files[data.index]
      if (data.files.error && currentFile.error) {
        Util.alert(currentFile.error)
      }
    })
  },
  /**
   * 数据导出.
   */
  exportPosts: function () {
    $.ajax({
      url: Label.servePath + '/export/posts',
      type: 'POST',
      cache: false,
      success: function (result, textStatus) {
        if (0 !== result.code) {
          Util.alert('TBD: V, tip display it....')

          return
        }

        window.open(result.url)
      },
    })
  },
  /**
   * @description 修改地理位置状态
   * @param {type} csrfToken CSRF token
   */
  changeGeoStatus: function (csrfToken) {
    var requestJSONObject = {
      'userGeoStatus': $('#geoStatus').val(),
    }

    $.ajax({
      url: Label.servePath + '/settings/geo/status',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      cache: false,
      data: JSON.stringify(requestJSONObject),
      success: function (result, textStatus) {
        console.log(result)
      },
    })
  },
  /**
   * @description 积分转账
   * @argument {String} csrfToken CSRF token
   */
  pointTransfer: function (csrfToken) {
    if (Validate.goValidate({
      target: $('#pointTransferTip'),
      data: [
        {
          'target': $('#pointTransferUserName'),
          'type': 'string',
          'max': 256,
          'msg': Label.invalidUserNameLabel,
        }, {
          'target': $('#pointTransferAmount'),
          'type': 'string',
          'max': 50,
          'msg': Label.amountNotEmpty,
        }],
    })) {
      var requestJSONObject = {
        'userName': $('#pointTransferUserName').val(),
        'amount': $('#pointTransferAmount').val(),
        'memo': $('#pointTransferMemo').val(),
      }

      $.ajax({
        url: Label.servePath + '/point/transfer',
        type: 'POST',
        headers: {'csrfToken': csrfToken},
        cache: false,
        data: JSON.stringify(requestJSONObject),
        beforeSend: function () {
          $('#pointTransferTip').
            removeClass('succ').
            removeClass('error').
            html('')
        },
        error: function (jqXHR, textStatus, errorThrown) {
          Util.alert(errorThrown)
        },
        success: function (result, textStatus) {
          if (0 === result.code) {
            $('#pointTransferTip').
              addClass('succ').
              removeClass('error').
              html('<ul><li>' + Label.transferSuccLabel + '</li></ul>')
            $('#pointTransferUserName').val('')
            $('#pointTransferAmount').val('')
            $('#pointTransferMemo').val('')
          } else {
            $('#pointTransferTip').
              addClass('error').
              removeClass('succ').
              html('<ul><li>' + result.msg + '</li></ul>')
          }

          $('#pointTransferTip').show()

          setTimeout(function () {
            $('#pointTransferTip').hide()
          }, 2000)
        },
      })
    }
  },
  /**
   * @description 积分兑换邀请码
   * @argument {String} csrfToken CSRF token
   */
  pointBuyInvitecode: function (csrfToken) {
    var requestJSONObject = {}

    $.ajax({
      url: Label.servePath + '/point/buy-invitecode',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      cache: false,
      data: JSON.stringify(requestJSONObject),
      beforeSend: function () {
        $('#pointBuyInvitecodeTip').
          removeClass('succ').
          removeClass('error').
          html('')
      },
      error: function (jqXHR, textStatus, errorThrown) {
        Util.alert(errorThrown)
      },
      success: function (result, textStatus) {
        if (0 === result.code) {
          $('.list ul').
            prepend('<li class="vditor-reset"><code>' +
              result.msg.split(' ')[0] + '</code>' + result.msg.substr(16) +
              '</li>')
        } else {
          $('#pointBuyInvitecodeTip').
            addClass('error').
            removeClass('succ').
            html('<ul><li>' + result.msg + '</li></ul>')
        }
        $('#pointBuyInvitecodeTip').show()
      },
    })
  },
  /**
   * @description 查询邀请码状态
   * @param {String} csrfToken CSRF token
   * @returns {undefined}
   */
  queryInvitecode: function (csrfToken) {
    var requestJSONObject = {
      invitecode: $('#invitecode').val(),
    }

    $.ajax({
      url: Label.servePath + '/invitecode/state',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      cache: false,
      data: JSON.stringify(requestJSONObject),
      beforeSend: function () {
        $('#invitecodeStateTip').
          removeClass('succ').
          removeClass('error').
          html('')
      },
      error: function (jqXHR, textStatus, errorThrown) {
        Util.alert(errorThrown)
      },
      success: function (result, textStatus) {
        switch (result.code) {
          case -1:
          case 0:
          case 2:
            $('#invitecodeStateTip').
              addClass('error').
              removeClass('succ').
              html('<ul><li>' + result.msg + '</li></ul>')

            break
          case 1:
            $('#invitecodeStateTip').
              addClass('succ').
              removeClass('error').
              html('<ul><li>' + result.msg + '</li></ul>')

            break
          default:
            $('#invitecodeStateTip').
              addClass('error').
              removeClass('succ').
              html('<ul><li>' + result.msg + '</li></ul>')
        }
        S
        $('#invitecodeStateTip').show()
      },
    })
  },
  /**
   * 向用户确认是否真的注销账号
   */
  requestDeactive: function (csrfToken) {
    if (confirm("请注意！这不是保存按钮！！！\n点击确定后，您的摸鱼派账号将会被永久停用，无法登录，账户信息将被部分抹除，您绑定的手机号需要一个月后才能在摸鱼派重新注册账号，确定继续吗？")) {
      if (confirm("亲爱的鱼油，再次向您确认！\n您的账号数据非常宝贵，如果对社区的发展有任何意见或建议，欢迎联系摸鱼派管理组。\n本次确认后，您的账户将被永久停用。")) {
        Settings.update('deactivate', csrfToken);
      }
    }
  },
  /**
   * @description 更新 settings 页面数据.
   * @argument {String} csrfToken CSRF token
   */
  update: function (type, csrfToken) {
    var requestJSONObject = {}

    switch (type) {
      case 'profiles':
        requestJSONObject = this._validateProfiles()

        break
      case 'password':
        requestJSONObject = this._validatePassword()

        break
      case 'privacy':
        requestJSONObject = {
          userArticleStatus: $('#userArticleStatus').prop('checked'),
          userCommentStatus: $('#userCommentStatus').prop('checked'),
          userFollowingUserStatus: $('#userFollowingUserStatus').
            prop('checked'),
          userFollowingTagStatus: $('#userFollowingTagStatus').prop('checked'),
          userFollowingArticleStatus: $('#userFollowingArticleStatus').
            prop('checked'),
          userWatchingArticleStatus: $('#userWatchingArticleStatus').
            prop('checked'),
          userFollowerStatus: $('#userFollowerStatus').prop('checked'),
          userBreezemoonStatus: $('#userBreezemoonStatus').prop('checked'),
          userPointStatus: $('#userPointStatus').prop('checked'),
          userOnlineStatus: $('#userOnlineStatus').prop('checked'),
          userJoinPointRank: $('#joinPointRank').prop('checked'),
          userJoinUsedPointRank: $('#joinUsedPointRank').prop('checked'),
          userUAStatus: $('#userUAStatus').prop('checked'),
        }

        break
      case 'function':
        requestJSONObject = {
          userListPageSize: $('#userListPageSize').val(),
          userIndexRedirectURL: $('#userIndexRedirectURL').val(),
          userCommentViewMode: $('#userCommentViewMode').val(),
          userAvatarViewMode: $('#userAvatarViewMode').val(),
          userListViewMode: $('#userListViewMode').val(),
          userNotifyStatus: $('#userNotifyStatus').prop('checked'),
          userSubMailStatus: $('#userSubMailStatus').prop('checked'),
          userKeyboardShortcutsStatus: $('#userKeyboardShortcutsStatus').
            prop('checked'),
          userReplyWatchArticleStatus: $('#userReplyWatchArticleStatus').
            prop('checked'),
          userForwardPageStatus: $('#userForwardPageStatus').prop('checked'),
          chatRoomPictureStatus: $('#chatRoomPictureStatus').prop('checked')
        }

        break
      case 'emotionList':
        requestJSONObject = this._validateEmotionList()

        break
      case 'i18n':
        requestJSONObject = {
          userLanguage: $('#userLanguage').val(),
          userTimezone: $('#userTimezone').val(),
        }

        break
      case 'username':
        requestJSONObject = {
          userName: $('#newUsername').val(),
        }

        break
      case 'system':
        let cardBg = "";
        if ($("#userCardSettings").attr("bgUrl") !== undefined) {
          cardBg = $("#userCardSettings").attr("bgUrl");
        }
        let iconURL = `https://fishpi.cn/images/favicon.png?` + Label.staticResourceVersion;
        if ($("#iconURL").data("imageurl") !== undefined && $("#iconURL").data("imageurl") !== '') {
          iconURL = $("#iconURL").data("imageurl");
        }
        requestJSONObject = {
          systemTitle: $('#newSystemTitle').val(),
          cardBg: cardBg,
          onlineTimeUnit: $('#onlineTimeUnit').val(),
          showSideAd: $("#showSideAd").prop("checked"),
          showTopAd: $("#showTopAd").prop("checked"),
          iconURL: iconURL
        }
        break
      case 'deactivate':
        break
      default:
        console.log('update settings has no type')
    }

    if (!requestJSONObject) {
      return false
    }

    $.ajax({
      url: Label.servePath + '/settings/' + type,
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      cache: false,
      data: JSON.stringify(requestJSONObject),
      beforeSend: function () {
        $('#' + type.replace(/\//g, '') + 'Tip').
          removeClass('succ').
          removeClass('error').
          html('')
      },
      error: function (jqXHR, textStatus, errorThrown) {
        Util.alert(errorThrown)
      },
      success: function (result, textStatus) {
        if (0 === result.code) {
          $('#' + type.replace(/\//g, '') + 'Tip').
            addClass('succ').
            removeClass('error').
            html('<ul><li>' + Label.updateSuccLabel + '</li></ul>').
            show()
          if (type === 'profiles') {
            $('#userNicknameDom').text(requestJSONObject.userNickname)
            $('#userTagsDom').text(requestJSONObject.userTags)
            $('#userURLDom').
              text(requestJSONObject.userURL).
              attr('href', requestJSONObject.userURL)
            $('#userIntroDom').text(requestJSONObject.userIntro)

            return
          }
        } else {
          $('#' + type.replace(/\//g, '') + 'Tip').
            addClass('error').
            removeClass('succ').
            html('<ul><li>' + result.msg + '</li></ul>')
        }

        $('#' + type.replace(/\//g, '') + 'Tip').show()

        setTimeout(function () {
          $('#' + type.replace(/\//g, '') + 'Tip').hide()

          if (type === 'i18n') {
            window.location.reload()
          }

          if (type === 'deactivate') {
            window.location.href = Label.servePath
          }
        }, 5000)
      },
    })
  },
  /**
   * @description 需要在上传完成后调用该函数来更新用户头像数据.
   * @argument {String} csrfToken CSRF token
   */
  updateAvatar: function (csrfToken) {
    var requestJSONObject = {
      userAvatarURL: $('#avatarURL').data('imageurl'),
    }

    $.ajax({
      url: Label.servePath + '/settings/avatar',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      cache: false,
      data: JSON.stringify(requestJSONObject),
      beforeSend: function () {
      },
      error: function (jqXHR, textStatus, errorThrown) {
        Util.alert(errorThrown)
      },
      success: function (result, textStatus) {
        if (0 === result.code) {
          $('#avatarURLDom, .user-nav .avatar-small').
            attr('style', 'background-image:url(' +
              requestJSONObject.userAvatarURL + ')')
        }
      },
    })
  },
  /**
   * @description settings 页面 profiles 数据校验
   * @returns {boolean/obj} 当校验不通过时返回 false，否则返回校验数据值。
   */
  _validateProfiles: function () {
    if (Validate.goValidate({
      target: $('#profilesTip'),
      data: [
        {
          'target': $('#userNickname'),
          'type': 'string',
          'min': 0,
          'max': 20,
          'msg': Label.invalidUserNicknameLabel,
        }, {
          'target': $('#userTags'),
          'type': 'string',
          'min': 0,
          'max': 255,
          'msg': Label.tagsErrorLabel,
        }, {
          'target': $('#userURL'),
          'type': 'string',
          'min': 0,
          'max': 255,
          'msg': Label.invalidUserURLLabel,
        }, {
          'target': $('#userIntro'),
          'type': 'string',
          'min': 0,
          'max': 255,
          'msg': Label.invalidUserIntroLabel,
        }, {
          'target': $('#userMbti'),
          'type': 'string',
          'min': 0,
          'max': 255,
          'msg': '错误的MBTI长度',
        }],
    })) {
      return {
        userNickname: $('#userNickname').val().replace(/(^\s*)|(\s*$)/g, ''),
        userTags: $('#userTags').val().replace(/(^\s*)|(\s*$)/g, ''),
        userURL: $('#userURL').val().replace(/(^\s*)|(\s*$)/g, ''),
        userIntro: $('#userIntro').val().replace(/(^\s*)|(\s*$)/g, ''),
        mbti: $('#userMbti').val().replace(/(^\s*)|(\s*$)/g, ''),
      }
    } else {
      return false
    }
  },
  /**
   * @description settings 页面密码校验
   * @returns {boolean/obj} 当校验不通过时返回 false，否则返回校验数据值。
   */
  _validatePassword: function () {
    var pwdVal = $('#pwdOld').val(),
      newPwdVal = $('#pwdNew').val()
    if (Validate.goValidate({
      target: $('#passwordTip'),
      data: [
        {
          'target': $('#pwdNew'),
          'type': 'password',
          'msg': Label.invalidPasswordLabel,
        }, {
          'target': $('#pwdRepeat'),
          'type': 'password',
          'oranginal': $('#pwdNew'),
          'msg': Label.confirmPwdErrorLabel,
        }],
    })) {
      if (newPwdVal !== $('#pwdRepeat').val()) {
        return false
      }
      var data = {}
      data.userPassword = calcMD5(pwdVal)
      data.userNewPassword = calcMD5(newPwdVal)
      return data
    }
    return false
  },
  /**
   * @description settings 页面表情校验（不知道有啥可校验的，暂不做校验）
   * @returns {boolean/obj} 当校验不通过时返回 false，否则返回校验数据值。
   */
  _validateEmotionList: function () {
    return {
      emotions: $('#emotionList').val(),
    }
  },
  /**
   * @description 标记所有消息通知为已读状态.
   */
  makeAllNotificationsRead: function () {
    $.ajax({
      url: Label.servePath + '/notifications/all-read',
      type: 'GET',
      cache: false,
      success: function (result, textStatus) {
        if (0 === result.code) {
            Settings.refreshNotificationsAllRead()
        }
      },
    })
  },
    refreshNotificationsAllRead: function () {
        $.ajax({
            url: window.location.href,
            type: 'GET',
            success: function(html) {
                // 用 jQuery 解析返回的 HTML 字符串
                var $tempDom = $('<div>').html(html);
                var $newMain = $tempDom.find('.main').first();
                if ($newMain.length === 0) {
                    console.error('未找到class为main的div');
                    return;
                }
                var $oldMain = $('.main').first();
                if ($oldMain.length === 0) {
                    console.error('当前页面未找到class为main的div');
                    return;
                }
                // 替换
                $oldMain.replaceWith($newMain);
            },
            error: function(xhr, status, error) {
                console.error('请求或处理出错:', error);
            }
        });
    },
  /**
   * @description 删除消息.
   */
  removeNotifications: function (type) {
    $.ajax({
      url: Label.servePath + '/notifications/remove/' + type,
      type: 'GET',
      cache: false,
      success: function (result) {
        if (0 === result.code) {
          location.reload()
        }
      },
    })
    return false
  },
  /**
   * @description 设置常用表情点击事件绑定.
   */
  initFunction: function () {
    $('#emojiGrid img').click(function () {
      var emoji = $(this).attr('alt')
      if ($('#emotionList').val().split(',').indexOf(emoji) !== -1) {
        return
      }
      if ($('#emotionList').val() !== '') {
        $('#emotionList').val($('#emotionList').val() + ',' + emoji)
      } else {
        $('#emotionList').val(emoji)
      }
    })
  },
  /**
   * 个人主页初始化
   */
  initHome: function () {
    if (Label.type === 'commentsAnonymous' || 'comments' === Label.type) {
      Util.parseHljs()
      Util.parseMarkdown()
    }

    $('#reportDialog').dialog({
      'width': $(window).width() > 500 ? 500 : $(window).width() - 50,
      'height': 365,
      'modal': true,
      'hideFooter': true,
    })

    // 个人主页展示勋章：如果是当前登录用户自己的主页，可考虑使用新接口展示更多信息。
    // 对于设置页 account.ftl，直接在页面脚本中调用 Settings.initMetal() 即可。

    if ($.ua.device.type !== 'mobile') {
      Settings.homeScroll()
    } else {
      return
    }

    $.pjax({
      selector: 'a',
      container: '#home-pjax-container',
      show: '',
      cache: false,
      storage: true,
      titleSuffix: '',
      filter: function (href) {
        return 0 > href.indexOf(Label.servePath + '/member/' + Label.userName)
      },
      callback: function (status) {
        switch (status.type) {
          case 'success':
          case 'cache':
            $('.home-menu a').removeClass('current')
            Util.listenUserCard()
            switch (location.pathname) {
              case '/member/' + Label.userName:
              case '/member/' + Label.userName + '/comments':
                Util.parseHljs()
                Util.parseMarkdown()
              case '/member/' + Label.userName + '/articles/anonymous':
              case '/member/' + Label.userName + '/comments/anonymous':
                Util.parseHljs()
                Util.parseMarkdown()
                $('.home-menu a:eq(0)').addClass('current')
                break
              case '/member/' + Label.userName + '/watching/articles':
              case '/member/' + Label.userName + '/following/users':
              case '/member/' + Label.userName + '/following/tags':
              case '/member/' + Label.userName + '/following/articles':
              case '/member/' + Label.userName + '/followers':
                $('.home-menu a:eq(1)').addClass('current')
                break
              case '/member/' + Label.userName + '/breezemoons':
                $('.home-menu a:eq(1)').addClass('current')
                Breezemoon.init()
                break
              case '/member/' + Label.userName + '/points':
                $('.home-menu a:eq(2)').addClass('current')
                break
            }
          case 'error':
            break
          case 'hash':
            break
        }
        $('.nav-tabs').html($('.home-menu').html())
        Util.parseMarkdown()
        Util.parseHljs()
      },
    })
    NProgress.configure({showSpinner: false})
    $('#home-pjax-container').bind('pjax.start', function () {
      NProgress.start()
    })
    $('#home-pjax-container').bind('pjax.end', function () {
      NProgress.done()
    })
  },
  /**
   * 初始化表情包分组
   */
  currentEmojiGroupId:'', // 当前选择的分组id
    emojiGroups:[],
  initEmojiGroups: function () {
      Settings.currentEmojiGroupId="";
      Settings.loadEmojiGroups();
      var $groupEmojiList = $('#groupEmojiList');
      $groupEmojiList.on('click', '.btn_add', function (e) {
          e.stopPropagation()
          const emojiId = $(this).closest('.emoji_item').data('emoji-id')
          const itemName = $(this).closest('.emoji_item').data('name')
          console.log('add emoji', emojiId)
          Settings.showGroupSelectDialog(emojiId,itemName)
      })

      $groupEmojiList.on('click', '.btn_delete', function (e) {
          e.stopPropagation()
          const emojiId = $(this).closest('.emoji_item').data('emoji-id')
          console.log('delete emoji', emojiId)
          Settings.removeEmojiFromGroup(emojiId)
      })

      $groupEmojiList.on('click', '.btn_rename', function (e) {
          e.stopPropagation()
          const itemId = $(this).closest('.emoji_item').data('id')
          const itemName = $(this).closest('.emoji_item').data('name')
          const itemSort = $(this).closest('.emoji_item').data('sort') || 0
          Settings.editEmojiItem(itemId, itemName, itemSort)
      })

  },
  /**
   * 加载用户的所有分组
   */
  loadEmojiGroups: function () {
    $.ajax({
      url: Label.servePath + '/api/emoji/groups',
      type: 'GET',
      cache: false,
      success: function (result) {
          if(result.code == 0){
              var groups = result.data || [];
              Settings.emojiGroups = groups;
              for (let i = 0; i < groups.length; i++) {
                  if (groups[i].type === 1){
                      Settings.currentEmojiGroupId = groups[i].oId;
                      break
                  }
              }
              Settings.renderEmojiGroups(groups);
              Settings.selectEmojiGroup(Settings.currentEmojiGroupId, 1);
          }else {
          Util.notice('warning', 2000, result.msg || '加载分组失败');
          }

      },
      error: function () {
        Util.notice('warning', 2000, '加载分组失败，请检查网络');
      }
    });
  },
  /**
   * 渲染分组列表
   */
  renderEmojiGroups: function (groups) {
     let $container = $('#emojiGroupBox')
      $container.empty()

      for(let i=0;i<groups.length;i++){
          let group = groups[i];
          let isAll = group.type === 1;
          const $groupDiv = $('<div>', {
              class: 'emoji_group',
              id: 'emojiGroup_' + group.oId,
              'data-id': group.oId,
              'data-name': group.name,
              'data-type': group.type
          })

          // 分组名称
          const $nameSpan = $('<span>', {
              class: 'group_name',
              text: group.name,
              css: {
                  'cursor': isAll ? 'default' : 'pointer'
              }
          })

          // 删除按钮
          const $btnDelete = $('<span>', {
              class: 'group_btn btn_delete_group',
              text: '🗑',
              css: {
                  'margin-left': '5px',
                  'cursor': 'pointer'
              }
          })

          // 默认分组不显示编辑和删除按钮
          if (isAll) {
              $groupDiv.append($nameSpan)
          } else {
              $groupDiv.append($nameSpan, $btnDelete)
          }

          // 单击分组名称选择分组
          $nameSpan.on('click', function (e) {
              e.stopPropagation()
              Settings.selectEmojiGroup(group.oId);
          })

          // 双击分组名称编辑分组（非默认分组）- 桌面端
          if (!isAll) {
              $nameSpan.on('dblclick', function (e) {
                  e.stopPropagation()
                  Settings.editEmojiGroup(group.oId, group.name, group.sort)
              })

              // 长按分组名称编辑分组（非默认分组）- 移动端
              let longPressTimer;
              $nameSpan.on('touchstart', function (e) {
                  longPressTimer = setTimeout(function () {
                      e.preventDefault()
                      Settings.editEmojiGroup(group.oId, group.name, group.sort)
                  }, 500)
              })

              $nameSpan.on('touchend', function () {
                  clearTimeout(longPressTimer)
              })

              $nameSpan.on('touchmove', function () {
                  clearTimeout(longPressTimer)
              })
          }

          // 点击删除按钮
          $btnDelete.on('click', function (e) {
              e.stopPropagation()
              Settings.deleteEmojiGroupById(group.oId, group.name)
          })

          $container.append($groupDiv)
      }
  },
  /**
   * 选择分组
   */
  selectEmojiGroup: function (groupId) {

      let oldGroupId = Settings.currentEmojiGroupId;
      Settings.currentEmojiGroupId = groupId;
      if (oldGroupId !== groupId) {
          $('#emojiGroup_' + oldGroupId).removeClass('emoji_group_select');
      }
      $('#emojiGroup_' + groupId).addClass('emoji_group_select');

      // 加载该分组的表情
      Settings.loadGroupEmojis(groupId);
  },
  /**
   * 加载分组下的表情
   */
  loadGroupEmojis: function (groupId) {
    $.ajax({
      url: Label.servePath + '/api/emoji/group/emojis?groupId=' + groupId,
      type: 'GET',
      cache: false,
      success: function (result) {
          if(result.code == 0){
              var emojis = result.data || [];
              Settings.renderGroupEmojis(emojis);
          }else {
              Util.notice('warning', 2000, result.msg || '加载表情失败');
          }
      },
      error: function () {
        Util.notice('warning', 2000, '加载表情失败，请检查网络');
      }
    });
  },
  /**
   * 渲染表情列表
   */
  renderGroupEmojis: function (emojis) {
    var $groupEmojiList = $('#groupEmojiList');
      $groupEmojiList.empty();

    if (emojis.length === 0) {
        $groupEmojiList.html('<div>暂无表情</div>');
        return;
    }
    emojis.sort((b, a) => a.sort - b.sort)
      emojis.forEach(item => {
          // 外层容器
          const $emojiItem = $('<div>', {
              class: 'emoji_item',
              'data-id': item.oId,
              'data-name': item.name || '',
              'data-emoji-id': item.emojiId,
              'data-sort': item.sort || 0,
          })

          // 图片包裹
          const $imgWrap = $('<div>', {
              class: 'emoji_img_wrap'
          })

          const $img = $('<img>', {
              src: item.url,
              alt: ''
          })

          // 悬浮浮层
          const $overlay = $('<div>', {
              class: 'emoji_overlay'
          })

          // 操作按钮（示例）
          const $btnAdd = $('<span>', {
              class: 'emoji_btn btn_add',
              text: '✚'
          })

          const $btnDelete = $('<span>', {
              class: 'emoji_btn btn_delete',
              text: '🗑'
          })

          const $btnRename = $('<span>', {
              class: 'emoji_btn btn_rename',
              text: '✎'
          })

          // 表情名称和排序
          const $nameWrap = $('<div>', {
              class: 'emoji_name_wrap'
          })

          const $nameDiv = $('<div>', {
              class: 'emoji_name',
              text: item.name || ''
          })

          const $sortDiv = $('<div>', {
              class: 'emoji_sort',
              text: '#' + (item.sort || 0)
          })

          $nameWrap.append($nameDiv, $sortDiv)

          // 组装
          $overlay.append($btnAdd, $btnRename, $btnDelete)
          $imgWrap.append($img)
          $emojiItem.append($imgWrap, $nameWrap, $overlay)
          $groupEmojiList.append($emojiItem)
      })
  },
  /**
   * 编辑分组（名称和排序）
   * @param {string} groupId 分组ID
   * @param {string} currentName 当前分组名称
   * @param {number} currentSort 当前排序值
   */
  editEmojiGroup: function (groupId, currentName, currentSort) {
    if (!groupId) {
      Util.notice('warning', 2000, '分组ID不能为空');
      return;
    }

    // 生成弹窗HTML
    var html = '<div class="form fn-clear" style="padding: 20px;">';
    html += '<div style="margin-bottom: 15px;">';
    html += '<label style="display: block; margin-bottom: 8px;">分组名称：</label>';
    html += '<input id="editGroupName" type="text" value="' + (currentName || '') + '" style="width: 100%; padding: 8px; box-sizing: border-box;"/>';
    html += '</div>';
    html += '<div style="margin-bottom: 15px;">';
    html += '<label style="display: block; margin-bottom: 8px;">排序值：</label>';
    html += '<input id="editGroupSort" type="number" value="' + (currentSort || 0) + '" style="width: 100%; padding: 8px; box-sizing: border-box;"/>';
    html += '</div>';
    html += '<br><br>';
    html += '<button onclick="Settings.confirmEditGroup(\'' + groupId + '\')" class="fn-right green">确定</button>';
    html += '<button onclick="$(\'#editGroupDialog\').dialog(\'close\')" class="fn-right" style="margin-right: 10px;">取消</button>';
    html += '</div>';

    // 检查弹窗是否存在，如果不存在则创建
    if ($('#editGroupDialog').length === 0) {
      $('body').append('<div id="editGroupDialog"></div>');
    }

    $('#editGroupDialog').html(html);

    // 初始化弹窗
    $('#editGroupDialog').dialog({
      'width': $(window).width() > 400 ? 400 : $(window).width() - 50,
      'height': 350,
      'modal': true,
      'hideFooter': true,
      'title': '编辑分组'
    });

    $('#editGroupDialog').dialog('open');
  },

  /**
   * 确认编辑分组
   * @param {string} groupId 分组ID
   */
  confirmEditGroup: function (groupId) {
    var newName = $('#editGroupName').val().trim();
    var newSort = parseInt($('#editGroupSort').val());

    if (!newName) {
      Util.notice('warning', 2000, '分组名称不能为空');
      return;
    }

    if (isNaN(newSort)) {
      Util.notice('warning', 2000, '排序值必须为数字');
      return;
    }

    $.ajax({
      url: Label.servePath + '/api/emoji/group/update',
      type: 'POST',
      data: JSON.stringify({
        groupId: groupId,
        name: newName,
        sort: newSort
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (0 === result.code) {
          Util.notice('success', 1200, '更新分组成功');
          $('#editGroupDialog').dialog('close');
          Settings.loadEmojiGroups();
        } else {
          Util.notice('warning', 2000, result.msg || '更新分组失败');
        }
      },
      error: function () {
        Util.notice('warning', 2000, '更新分组失败，请检查网络');
      }
    });
  },
  /**
   * 删除分组
   * @param {string} groupId 分组ID
   * @param {string} groupName 分组名称
   */
  deleteEmojiGroupById: function (groupId, groupName) {
    if (!groupId) {
      Util.notice('warning', 2000, '分组ID不能为空');
      return;
    }

    if (!confirm('确定要删除分组"' + groupName + '"吗？分组内的表情会同步被移除。')) {
      return;
    }

    $.ajax({
      url: Label.servePath + '/api/emoji/group/delete',
      type: 'POST',
      data: JSON.stringify({
        groupId: groupId
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (0 === result.code) {
          Util.notice('success', 1200, '删除分组成功');
          Settings.loadEmojiGroups();
          // 如果删除的是当前选中的分组，则切换到全部分组
          if (Settings.currentEmojiGroupId === groupId) {
             let allGroupId = ""
             Settings.emojiGroups.forEach(item =>{
                if(item.type === 1){
                    allGroupId = item.oId;
                }
              })
              Settings.selectEmojiGroup(allGroupId);

          }
        } else {
          Util.notice('warning', 2000, result.msg || '删除分组失败');
        }
      },
      error: function () {
        Util.notice('warning', 2000, '删除分组失败，请检查网络');
      }
    });
  },
  /**
   * 创建新分组
   */
  createEmojiGroup: function () {
    var groupName = prompt('请输入分组名称：', '');
    if (!groupName || groupName.trim() === '') {
      return;
    }
    
    $.ajax({
      url: Label.servePath + '/api/emoji/group/create',
      type: 'POST',
      data: JSON.stringify({
        name: groupName.trim(),
        sort: 0
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
          if (0 !== result.code) {
              Util.notice('warning', 2000, result.msg || '创建分组失败');
              return;
          }
          Util.notice('success', 1200, '创建分组成功');
          Settings.loadEmojiGroups();
      },
      error: function () {
        Util.notice('warning', 2000, '创建分组失败，请检查网络');
      }
    });
  },
  /**
   * 显示通过URL添加表情的弹窗
   */
  addEmojiByUrl: function () {
    // 生成弹窗HTML
    var html = '<div class="form fn-clear" style="padding:0 20px;">';

    html += '</select>';
    html += '<label>表情URL：</label><br>';
    html += '<input id="emojiUrl" type="text" placeholder="请输入表情图片URL" style="width: 100%; padding: 8px; margin-bottom: 10px;"/>';
    html += '<label>表情别名（可选）：</label><br>';
    html += '<input id="emojiAlias" type="text" placeholder="可选，输入表情别名" style="width: 100%; padding: 8px; margin-bottom: 10px;"/>';
    html += '<br><br>';
    html += '<button onclick="Settings.confirmAddUrlEmoji()" class="fn-right green">确定</button>';
    html += '<button onclick="$(\'#addUrlEmojiDialog\').dialog(\'close\')" class="fn-right" style="margin-right: 10px;">取消</button>';
    html += '</div>';
    
    // 检查弹窗是否存在，如果不存在则创建
    if ($('#addUrlEmojiDialog').length === 0) {
      $('body').append('<div id="addUrlEmojiDialog"></div>');
    }
    
    $('#addUrlEmojiDialog').html(html);
    
    // 初始化弹窗
    $('#addUrlEmojiDialog').dialog({
      'width': $(window).width() > 400 ? 400 : $(window).width() - 50,
        'height':300,
      'modal': true,
      'hideFooter': true,
      'title': '通过URL添加表情'
    });
    
    $('#addUrlEmojiDialog').dialog('open');
  },
  /**
   * 确认通过URL添加表情到分组
   */
  confirmAddUrlEmoji: function () {
    let groupId = Settings.currentEmojiGroupId;
    let url = $('#emojiUrl').val().trim();
    let name = $('#emojiAlias').val().trim();
    

    if (!url) {
      Util.notice('warning', 2000, '请输入表情图片URL');
      return;
    }
    
    $.ajax({
      url: Label.servePath + '/api/emoji/group/add-url-emoji',
      type: 'POST',
      data: JSON.stringify({
        groupId: groupId,
        url: url,
        sort: 0,
        name: name
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (result.code === 0) {
          Util.notice('success', 1200, '添加表情成功');
          $('#addUrlEmojiDialog').dialog('close');
          // 如果当前正在查看该分组，则刷新表情列表
            Settings.loadGroupEmojis(groupId);
        } else {
          Util.notice('warning', 2000, result.msg || '添加表情失败');
        }
      },
      error: function () {
        Util.notice('warning', 2000, '添加表情失败，请检查网络');
      }
    });
  },
  /**
   * 编辑表情项（名称和排序）
   */
  editEmojiItem: function (itemId, currentName, currentSort) {
    if (!itemId) {
      Util.notice('warning', 2000, '表情ID不能为空');
      return;
    }

    // 生成弹窗HTML
    var html = '<div class="form fn-clear" style="padding: 20px;">';
    html += '<div style="margin-bottom: 15px;">';
    html += '<label style="display: block; margin-bottom: 8px;">表情名称：</label>';
    html += '<input id="editEmojiName" type="text" value="' + (currentName || '') + '" style="width: 100%; padding: 8px; box-sizing: border-box;"/>';
    html += '</div>';
    html += '<div style="margin-bottom: 15px;">';
    html += '<label style="display: block; margin-bottom: 8px;">排序值：</label>';
    html += '<input id="editEmojiSort" type="number" value="' + (currentSort || 0) + '" style="width: 100%; padding: 8px; box-sizing: border-box;"/>';
    html += '</div>';
    html += '<br><br>';
    html += '<button onclick="Settings.confirmEditEmoji(\'' + itemId + '\')" class="fn-right green">确定</button>';
    html += '<button onclick="$(\'#editEmojiDialog\').dialog(\'close\')" class="fn-right" style="margin-right: 10px;">取消</button>';
    html += '</div>';

    // 检查弹窗是否存在，如果不存在则创建
    if ($('#editEmojiDialog').length === 0) {
      $('body').append('<div id="editEmojiDialog"></div>');
    }

    $('#editEmojiDialog').html(html);

    // 初始化弹窗
    $('#editEmojiDialog').dialog({
      'width': $(window).width() > 400 ? 400 : $(window).width() - 50,
      'height': 350,
      'modal': true,
      'hideFooter': true,
      'title': '编辑表情'
    });

    $('#editEmojiDialog').dialog('open');
  },

  /**
   * 确认编辑表情项
   * @param {string} itemId 表情项ID
   */
  confirmEditEmoji: function (itemId) {
    var newName = $('#editEmojiName').val().trim();
    var newSort = parseInt($('#editEmojiSort').val());

    if (!newName) {
      Util.notice('warning', 2000, '表情名称不能为空');
      return;
    }

    if (isNaN(newSort)) {
      Util.notice('warning', 2000, '排序值必须为数字');
      return;
    }

    $.ajax({
      url: Label.servePath + '/api/emoji/emoji/update',
      type: 'POST',
      data: JSON.stringify({
        oId: itemId,
        groupId: Settings.currentEmojiGroupId,
        name: newName,
        sort: newSort
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (0 === result.code) {
          Util.notice('success', 1200, '更新表情成功');
          $('#editEmojiDialog').dialog('close');
          Settings.loadGroupEmojis(Settings.currentEmojiGroupId);
        } else {
          Util.notice('warning', 2000, result.msg || '更新表情失败');
        }
      },
      error: function () {
        Util.notice('warning', 2000, '更新表情失败，请检查网络');
      }
    });
  },
  /**
   * 从分组移除表情
   */
  removeEmojiFromGroup: function (emojiId) {
    if (!emojiId) {
      return;
    }
    
    if (!confirm('确定要移除这个表情吗？')) {
      return;
    }
    
    $.ajax({
      url: Label.servePath + '/api/emoji/group/remove-emoji',
      type: 'POST',
      data: JSON.stringify({
        groupId: Settings.currentEmojiGroupId,
        emojiId: emojiId
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (0 === result.code) {
          Util.notice('success', 1200, '移除表情成功');
          Settings.loadGroupEmojis(Settings.currentEmojiGroupId);
        } else {
          Util.notice('warning', 2000, result.msg || '移除表情失败');
        }
      },
      error: function () {
        Util.notice('warning', 2000, '移除表情失败，请检查网络');
      }
    });
  },

  /**
   * 显示分组选择弹窗
   * @param {string} emojiId 表情id
   * @param {array} groups 分组列表
   */
  showGroupSelectDialog: function (emojiId,name) {
    let groups = Settings.emojiGroups;
    
    // 生成分组选择HTML
    var html = '<div class="form fn-clear" style="padding:0 20px;">';
    html += '<label>请选择要添加到的分组：</label><br><br>';
    html += '<select id="groupSelect" style="width: 100%; padding: 8px; margin-bottom: 10px;">';
    
    for (var i = 0; i < groups.length; i++) {
      var group = groups[i];
      var selected = '';
      if(group.type==1){
          continue;
      }
      html += '<option value="' + group.oId + '" ' + selected + '>' + group.name + '</option>';
    }
    
    html += '</select>';
    html += '<label>表情别名（可选）：</label><br>';
    html += '<input id="emojiName" type="text" placeholder="可选，输入表情别名" value="' + (name || '') + '" style="width: 100%; padding: 8px; margin-bottom: 10px;"/>';
    html += '<br><br>';
    html += '<button onclick="Settings.confirmAddEmojiToGroup(\'' + emojiId + '\')" class="fn-right green">确定</button>';
    html += '<button onclick="$(\'#emojiGroupSelectDialog\').dialog(\'close\')" class="fn-right" style="margin-right: 10px;">取消</button>';
    html += '</div>';
    
    // 检查弹窗是否存在，如果不存在则创建
    if ($('#emojiGroupSelectDialog').length === 0) {
      $('body').append('<div id="emojiGroupSelectDialog"></div>');
    }
    
    $('#emojiGroupSelectDialog').html(html);
    
    // 初始化弹窗
    $('#emojiGroupSelectDialog').dialog({
      'width': $(window).width() > 400 ? 400 : $(window).width() - 50,
        'height':350,
      'modal': true,
      'hideFooter': true,
      'title': '选择分组'
    });
    
    $('#emojiGroupSelectDialog').dialog('open');
  },
  /**
   * 确认添加表情到分组
   * @param {string} emojiId 表情id
   */
  confirmAddEmojiToGroup: function (emojiId) {
    var groupId = $('#groupSelect').val();
    var name = $('#emojiName').val().trim();

    if (!groupId) {
      Util.notice('warning', 2000, '请选择一个分组');
      return;
    }

    $.ajax({
      url: Label.servePath + '/api/emoji/group/add-emoji',
      type: 'POST',
      data: JSON.stringify({
        groupId: groupId,
        emojiId: emojiId,
        sort: 0,
        name: name
      }),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (result.code === 0) {
          Util.notice('success', 1200, '添加表情到分组成功');
          $('#emojiGroupSelectDialog').dialog('close');
          // 如果当前正在查看该分组，则刷新表情列表
          // if (Settings.currentEmojiGroupId === groupId) {
          //   Settings.loadGroupEmojis(groupId);
          // }
        } else {
          Util.notice('warning', 2000, result.msg || '添加表情失败');
        }
      },
      error: function () {
        Util.notice('warning', 2000, '添加表情失败，请检查网络');
      }
    });
  },
  /**
   * 迁移历史表情包
   */
  migrateEmojis: function () {
    if (!confirm('确定要迁移历史表情包吗？')) {
      return;
    }
    const $btn = $('button:contains("迁移历史表情")').first();
    const $loading = $('#emoji-migrate-loading').length ? $('#emoji-migrate-loading')
      : $('<span id="emoji-migrate-loading" style="margin-left:8px;color:#1890ff;display:inline-flex;align-items:center;font-size:12px;">⏳ 迁移中...</span>').insertAfter($btn);
    $btn.prop('disabled', true).css('opacity', 0.6);
    $loading.show();
    if (typeof NProgress !== 'undefined') { NProgress.start(); }
    Util.notice('info', 2000, '正在迁移，请稍候...');

    $.ajax({
      url: Label.servePath + '/api/emoji/emoji/migrate',
      type: 'POST',
      data: JSON.stringify({}),
      contentType: 'application/json;charset=UTF-8',
      success: function (result) {
        if (0 === result.code) {
          Util.notice('success', 1200, '迁移历史表情包成功');
          Settings.loadEmojiGroups();
        } else {
          Util.notice('warning', 2000, result.msg || '迁移历史表情包失败');
        }
        if (typeof NProgress !== 'undefined') { NProgress.done(); }
        $btn.prop('disabled', false).css('opacity', 1);
        $loading.hide();
      },
      error: function () {
        Util.notice('warning', 2000, '迁移历史表情包失败，请检查网络');
        if (typeof NProgress !== 'undefined') { NProgress.done(); }
        $btn.prop('disabled', false).css('opacity', 1);
        $loading.hide();
      }
    });
  },
}

