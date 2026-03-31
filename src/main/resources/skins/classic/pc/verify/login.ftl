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
<#include "../macro-head.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="${loginLabel} - ${symphonyLabel}">
        <meta name="description" content="${registerLabel} ${symphonyLabel}"/>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}"/>
    <#--        <link rel="stylesheet" href="${staticServePath}/css/theme/dark-index.css?${staticResourceVersion}" />-->
    <link rel="canonical" href="${servePath}/register">
</head>
<body>
<#include "../header.ftl">
<div class="main">
    <div class="wrapper verify">
        <div class="verify-wrap">
            <div class="form" id="account_login">
                <img src="https://file.fishpi.cn/logo_raw.png" style="width: 100px;height: 100px;margin: 0 auto;display: block;"/>
                <div class="input-wrap">
                    <svg>
                        <use xlink:href="#userrole"></use>
                    </svg>
                    <input id="nameOrEmail" type="text" autofocus="autofocus" placeholder="${nameOrEmailLabel}"
                           autocomplete="off"/>
                </div>
                <div class="input-wrap">
                    <svg>
                        <use xlink:href="#locked"></use>
                    </svg>
                    <input type="password" id="loginPassword" placeholder="${passwordLabel}"/>
                </div>
                <div class="input-wrap">
                    <svg>
                        <use xlink:href="#mfa"></use>
                    </svg>
                    <input type="text" id="mfaCode" placeholder="两步验证码 (未开启请留空)"/>
                </div>
                <div class="fn-none input-wrap">
                    <img id="captchaImg" class="captcha-img fn-pointer"/>
                    <input type="text" id="captchaLogin" class="captcha-input" placeholder="${captchaLabel}"/>
                </div>

                <div class="fn-clear">
                    <div class="fn-hr5"></div>
                    <input type="checkbox" id="rememberLogin" checked/> ${rememberLoginStatusLabel}
                    <a href="${servePath}/forget-pwd" class="fn-right">${forgetPwdLabel}</a>
                    <div class="fn-hr5"></div>
                </div>

                <div id="loginTip" class="tip"></div>
                <button class="green" onclick="Verify.login('${goto}')">${loginLabel}</button>
                <button onclick="Util.goRegister()">${registerLabel}</button>
                <button onclick="toggleLoginType(0)">扫码登录</button>
            </div>
            <div id="qrcode_login" class="form hide-me">
                <img src="https://file.fishpi.cn/logo_raw.png" style="width: 100px;height: 100px;margin: 0 auto;display: block;"/>
                <div class="login-qrcode-box" id="login-qrcode-div">
                    <img src="https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=web:" id="qrcode_img" alt="">
                </div>
                <div class="login-qrcode-box">
                    <p>请使用官方APP扫码登录</p>
                </div>
                <button onclick="toggleLoginType(1)">账号登录</button>
            </div>

        </div>
        <div class="intro vditor-reset">
            ${introLabel}
        </div>
    </div>
</div>
<#include "../footer.ftl">
<script src="${staticServePath}/js/verify${miniPostfix}.js?${staticResourceVersion}"></script>
<script>
    Verify.init();
    Label.invalidUserNameLabel = "${invalidUserNameLabel}";
    Label.invalidEmailLabel = "${invalidEmailLabel}";
    Label.confirmPwdErrorLabel = "${confirmPwdErrorLabel}";
    Label.captchaErrorLabel = "${captchaErrorLabel}";
    Label.loginChannelURL = "${wsScheme}://${serverHost}:${serverPort}${contextPath}/login-channel";
    function toggleLoginType(type) {
        let accObj = document.querySelector("#account_login");
        let qrObj = document.querySelector("#qrcode_login");
        accObj.classList.toggle('hide-me');
        qrObj.classList.toggle('hide-me');
        if(type === 0){
            Verify.initQrCodeLogin();
        }else{
            Verify.closeQrCodeLogin();
        }
    }
</script>
</body>
</html>
