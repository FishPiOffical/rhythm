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
        <@head title="${registerLabel} - ${symphonyLabel}">
        <meta name="description" content="${registerLabel} ${symphonyLabel}"/>
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "../header.ftl">
        <div class="main">
            <div class="wrapper verify">
                <div class="verify-wrap">
                    <div class="form">
                        <img src="https://file.fishpi.cn/logo_raw.png" style="width: 100px;height: 100px;margin: 0 auto;display: block;"/>
                        <div class="input-wrap">
                            <svg><use xlink:href="#userrole"></use></svg>
                            <input type="text" id="registerUserName2" value="${user.userName}" readonly="readonly" placeholder="${userNameLabel}" autocomplete="off" />
                        </div>
                        <div class="input-wrap">
                            <svg><use xlink:href="#phone"></use></svg>
                            <input type="text" id="registerUserPhone2" value="${user.userPhone}" readonly="readonly" placeholder="${emailLabel}" autocomplete="off" />
                        </div>
                        <div class="input-wrap">
                            <svg><use xlink:href="#locked"></use></svg>
                            <input type="password" autofocus="autofocus" id="registerUserPassword2" placeholder="${passwordLabel}" />
                        </div>
                         <div class="input-wrap">
                             <svg><use xlink:href="#locked"></use></svg>
                             <input type="password" id="registerConfirmPassword2" placeholder="${userPasswordLabel2}" />
                        </div>
                        <div class="input-wrap">
                            <svg><use xlink:href="#mbti"></use></svg>
                            <input type="text" id="mbtiReg" placeholder="(选填) MBTI" />
                            <label>MBTI填写示例：<b>ENTP ENFP-A ENTP-T ISTJ ISFJ-A ISTJ-T</b><br>
                                <a href="https://www.16personalities.com/ch" target="_blank">什么是MBTI？</a></label>
                        </div>
                        <div class="fn-clear">
                            <label>${roleLabel}</label>
                            <label>&nbsp;&nbsp;&nbsp;&nbsp;<input name="userAppRole" type="radio" value="0" checked="checked" />&nbsp;&nbsp;${programmerLabel}</label>
                            <label style="float:right">&nbsp;&nbsp;<input name="userAppRole" type="radio" value="1" />&nbsp;&nbsp;${designerLabel}</label>

                        </div>
                        <div id="registerTip2" class="tip"></div>
                        <button class="green" onclick="Verify.register2()">${registerLabel}</button>
                        <input id="userId2" type="hidden" value="${user.oId}">

                    </div>
                </div>
                <div class="intro fn-flex-1 vditor-reset">
                    ${introLabel}
                </div>
            </div>
        </div>
        <#include "../footer.ftl">
        <script src="${staticServePath}/js/verify${miniPostfix}.js?${staticResourceVersion}"></script>
        <script>
            Verify.init();
            Label.confirmPwdErrorLabel = "${confirmPwdErrorLabel}";
        </script>
    </body>
</html>
