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
        if (idCert === '' || idId === '') {
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
            '    <div class="avatar-big" id="id-id"\n' +
            '         onclick="$(\'#id-id-upload input\').click()"\n' +
            '         style="background-image:url(https://file.fishpi.cn/id/%E6%89%8B%E6%8C%81%E8%BA%AB%E4%BB%BD%E8%AF%81%E8%87%AA%E6%8B%8D%E7%85%A7.png)"></div>\n' +
            '</div>\n' +
            '<form id="id-id-upload" style="display: none" method="POST" enctype="multipart/form-data">\n' +
            '        <input type="file" name="file">\n' +
            '</form>' +
            '';
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
          Util.alert(result.msg);
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
          Util.alert(result.msg);
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
    if (bag.patchCheckinCard !== undefined && bag.patchCheckinCard > 0) {
      html += '<button style="margin:0 5px 5px 0" onclick="Settings.usePatchCheckinCard(\'' + Label.csrfToken + '\', ' + bag.patchStart + ')">补签卡 x' + bag.patchCheckinCard + '</button>';
    }
    if (bag.metalTicket !== undefined && bag.metalTicket > 0) {
      html += '<button style="margin:0 5px 5px 0" onclick="alert(\'您已取得摸鱼派一周年纪念勋章领取权限，请静待系统公告\')">摸鱼派一周年纪念勋章领取券 x' + bag.metalTicket + '</button>';
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
   * 初始化勋章
   */
  initMetal: function (metal) {
    let html = '';
    if (metal.list !== undefined) {
      for (let i = 0; i < metal.list.length; i++) {
        let m = metal.list[i];
        let btn = '';
        if (m.enabled === true) {
          btn = '<button class="btn red" onclick="Settings.toggleMetal(\'' + m.name + '\', false)">卸下</button>';
        } else {
          btn = '<button class="btn green" onclick="Settings.toggleMetal(\'' + m.name + '\', true)">佩戴</button>';
        }
        html += '<div class="fn__flex" style="justify-content: space-between; margin-bottom: 10px">' +
            '<div>' +
            ' <label style="margin: 0 0 0 0">' +
            '   <div><img src="' + Util.genMetal(m.name, m.attr) + '"/><br><span style="font-size: 5px">' + m.name + ' (' + m.description + ')</span></div>' +
            ' </label>' +
            ' </div>' +
            ' <div>' + btn + "</div>" +
            "</div>";
      }
    }
    if (html === '') {
      html = '鱼战士，你还没有任何勋章！';
    }
    document.getElementById("metal").innerHTML = html;
  },
  toggleMetal: function (name, enabled) {
    $.ajax({
      url: Label.servePath + "/admin/user/toggle-metal",
      method: "post",
      data: "name=" + name + "&enabled=" + enabled,
      async: false,
      success: function () {
        location.reload();
      }
    });
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
   */
  getPhoneCaptcha: function (csrfToken) {
    $('#phoneGetBtn').attr('disabled', 'disabled').css('opacity', '0.3')
    $.ajax({
      url: Label.servePath + '/settings/phone/vc',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      data: JSON.stringify({
        userPhone: $('#phoneInput').val(),
        captcha: $('#phoneVerify').val(),
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
        }],
    })) {
      return {
        userNickname: $('#userNickname').val().replace(/(^\s*)|(\s*$)/g, ''),
        userTags: $('#userTags').val().replace(/(^\s*)|(\s*$)/g, ''),
        userURL: $('#userURL').val().replace(/(^\s*)|(\s*$)/g, ''),
        userIntro: $('#userIntro').val().replace(/(^\s*)|(\s*$)/g, ''),
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
          window.location.reload()
        }
      },
    })
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

    $.ajax({
      url: Label.servePath + "/user/" + Label.userName + "/metal",
      async: true,
      method: "get",
      success: function (result) {
        let list = result.data.list;
        if (list !== undefined) {
          for (let i = 0; i < list.length; i++) {
            let m = list[i];
            $("#metal").append("<img title='" + m.description + "' src='" + Util.genMetal(m.name, m.attr) + "'/>");
          }
        }
      }
    });

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
}
