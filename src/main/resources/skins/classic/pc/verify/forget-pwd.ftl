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
        <@head title="${forgetPwdLabel} - ${symphonyLabel}">
        <meta name="description" content="${forgetPwdLabel} ${symphonyLabel}"/>
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
        <link rel="canonical" href="${servePath}/forget-pwd">
        <script src="https://static.geetest.com/v4/gt4.js"></script>
        <script src="https://apps.bdimg.com/libs/jquery/1.9.1/jquery.js"></script>
    </head>
    <body>
        <#include "../header.ftl">
        <div class="main">
            <div class="wrapper verify">
                <div class="verify-wrap">
                    <div class="form">
                        <img src="https://file.fishpi.cn/logo_raw.png" style="width: 100px;height: 100px;margin: 0 auto;display: block;"/>

                        <div class="input-wrap">
                            <svg><use xlink:href="#phone"></use></svg>
                            <input id="fpwdPhone" type="text" placeholder="手机号码" autocomplete="off" autofocus="autofocus" />
                        </div>

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
                                        Verify.forgetPwd(result);
                                        setTimeout(function () {
                                            gt.reset();
                                        }, 3000);
                                    })
                            });

                        </script>
                        <div id="fpwdTip" class="tip"></div>
                        <button class="green" style="display: none" id="forgetBtn" onclick="Verify.forgetPwd()">${forgetPwdLabel}</button>
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
            Label.invalidEmailLabel = "${invalidEmailLabel}";
            Label.captchaErrorLabel = "${captchaErrorLabel}";
        </script>
    </body>
</html>
