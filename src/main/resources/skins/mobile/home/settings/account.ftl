<#--

    Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
    Modified version from Symphony, Thanks Symphony :)
    Copyright (C) 2012-present, b3log.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

-->
<#include "macro-settings.ftl">
<@home "account">
<div class="module">
    <div class="module-header" style="margin-bottom: 10px">
        <h2>你的背包</h2>
    </div>
    <div class="module-panel form fn-clear" id="bag">
    </div>
</div>

<div class="module">
    <div class="module-header" style="margin-bottom: 10px">
        <h2>你的勋章</h2>
    </div>
    <div class="module-panel form fn-clear" id="metal">
    </div>
</div>

<div class="module">
    <div class="module-header">
        <h2>两步验证</h2>
    </div>
    <div class="module-panel form fn-clear">
        <div id="mfaCode">
        </div>
    </div>
</div>

<div class="module">
    <div class="module-header">
        <h2>${userNameLabel} ${updateNameTipLabel}</h2>
    </div>
    <div class="module-panel form fn-clear">
        <label>${currentUsernameLabel}</label>
        <input value="${currentUser.userName}" type="text" readonly />

        <label>${newUsernameLabel}</label>
        <input id="newUsername" type="text" /><br/><br/>

        <div id="usernameTip" class="tip"></div><br/>
        <button class="fn-right" onclick="Settings.update('username', '${csrfToken}')">${saveLabel}</button>
    </div>
</div>

<div class="module">
    <div class="module-header">
        <h2>${passwordLabel}</h2>
    </div>
    <div class="module-panel form fn-clear">
        <label>${oldPasswordLabel}</label>
        <input id="pwdOld" type="password" />

        <label>${newPasswordLabel}</label>
        <input id="pwdNew" type="password" />

        <label>${confirmPasswordLabel}</label>
        <input id="pwdRepeat" type="password" /> <br/><br/>
        <div id="passwordTip" class="tip"></div><br/>
        <button class="fn-right" onclick="Settings.update('password', '${csrfToken}')">${saveLabel}</button>
    </div>
</div>

<div class="module">
    <div class="module-header" id="bind-phone">
        <h2>绑定手机</h2>
    </div>
    <div class="module-panel form fn-clear">
        <label>手机号</label>
        <input id="phoneInput" type="text" value="${currentUser.userPhone}" />
        <button style="margin-top: 10px" onclick="$('#change-phone-form').removeClass('fn-none');$('#change-phone-form img').click();$(this).remove()">修改绑定手机</button>
        <span id="change-phone-form" class="fn-none">
            <br>
                        <div id="captcha"></div>
                        <br>
                        <script>
                            var captchaId = "6d886bcaec3f86fcfd6f61bff5af2cb4"
                            var product = "float"
                            if (product !== 'bind') {
                                $('#btn').remove();
                            }

                            initGeetest4({
                                captchaId: captchaId,
                                product: product,
                            }, function (gt) {
                                window.gt = gt
                                gt
                                    .appendTo("#captcha")
                                    .onSuccess(function (e) {
                                        var result = gt.getValidate();
                                        Settings.getPhoneCaptcha('${csrfToken}', result)
                                        $("#captcha").remove();
                                    })
                            });

                        </script>
        </span>

        <div class="fn-none" id="phoneCodePanel">
            <label>请输入短信验证码</label>
            <input id="phoneCode" type="text" />
        </div>
        <div id="phoneTip" class="tip"></div><br/>
        <button id="phoneSubmitBtn" class="fn-right fn-none"
                onclick="Settings.updatePhone('${csrfToken}')">${submitLabel}</button>
    </div>

    <div class="module-header" id="bind-email">
        <h2>${emailLabel}</h2>
    </div>
    <div class="module-panel form fn-clear">
        <label>${emailLabel}</label>
        <input id="emailInput" type="text" value="<#if !currentUser.userEmail?ends_with("sym.b3log.org")>${currentUser.userEmail}</#if>" />

        <div class="home-account__captch" id="email_captch">
            <label>${imageCaptchaLabel}</label>
            <img class="fn-pointer" height="20px" src="${servePath}/captcha?v=02" onclick="this.src = '${servePath}/captcha?' + (new Date()).getTime()" />
            <input id="emailVerify" type="text" />
        </div>
        <div class="fn-none" id="emailCodePanel">
            <label>${emailCaptchaLabel}</label>
            <input id="emailCode" type="text" />
        </div>
        <div id="emailTip" class="tip"></div><br/>
        <button id="emailSubmitBtn" class="fn-right fn-none"
                onclick="Settings.updateEmail('${csrfToken}')">${submitLabel}</button>
        <button id="emailGetBtn"
                class="fn-right"
                onclick="Settings.getEmailCaptcha('${csrfToken}')">${getEmailCaptchaLabel}</button>
    </div>
</div>
<script>
  $(function () {
      if (typeof Settings !== 'undefined' && document.getElementById('metal')) {
          Settings.initMetal();
      }
  });
</script>
</@home>
