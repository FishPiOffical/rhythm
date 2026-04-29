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
 * @fileoverview register.
 *
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @author <a href="http://88250.b3log.org">Liang Ding</a> */

/**
 * @description Verify
 * @static
 */
var Verify = {
    /**
     * @description 登录
     */
    login: function (goto) {
        goto = decodeURIComponent(goto)
        if (Validate.goValidate({
            target: $('#loginTip'),
            data: [{
                "target": $("#nameOrEmail"),
                "type": "string",
                "max": 256,
                "msg": Label.loginNameErrorLabel
            }]
        })) {
            var requestJSONObject = {
                nameOrEmail: $("#nameOrEmail").val().replace(/(^\s*)|(\s*$)/g, ""),
                userPassword: calcMD5($("#loginPassword").val()),
                mfaCode: $("#mfaCode").val(),
                rememberLogin: $("#rememberLogin").prop("checked"),
                captcha: $('#captchaLogin').val().replace(/(^\s*)|(\s*$)/g, "")
            };

            $.ajax({
                url: Label.servePath + "/login",
                type: "POST",
                cache: false,
                data: JSON.stringify(requestJSONObject),
                success: function (result, textStatus) {
                    if (0 === result.code) {
                        window.location.href = goto;
                    } else {
                        $("#loginTip").addClass('error').html('<ul><li>' + result.msg + '</li></ul>');

                        if (result.needCaptcha && "" !== result.needCaptcha) {
                            $('#captchaImg').parent().show();
                            $("#captchaImg").attr("src", Label.servePath + "/captcha/login?needCaptcha="
                                + result.needCaptcha + "&t=" + Math.random())
                                .click(function () {
                                    $(this).attr('src', Label.servePath + "/captcha/login?needCaptcha="
                                        + result.needCaptcha + "&t=" + Math.random())
                                });
                        }
                    }
                }
            });
        }
    },
    /**
     * @description Register Step 1
     */
    register: function (result) {
        if (Validate.goValidate({
            target: $("#registerTip"),
            data: [{
                "target": $("#registerUserName"),
                "msg": Label.invalidUserNameLabel,
                "type": 'string',
                'max': 20
            }, {
                "target": $("#registerUserPhone"),
                "msg": "手机号码不合法",
                "type": "phone"
            }]
        })) {
            var requestJSONObject = {
                userName: $("#registerUserName").val().replace(/(^\s*)|(\s*$)/g, ""),
                userPhone: $("#registerUserPhone").val().replace(/(^\s*)|(\s*$)/g, ""),
                invitecode: $("#registerInviteCode").val().replace(/(^\s*)|(\s*$)/g, ""),
                captcha: result
            };

            let args = "";
            if (Util.getParameterByName("r") !== '') {
                args = "?r=" + Util.getParameterByName("r");
            }

            $.ajax({
                url: Label.servePath + "/register" + args,
                type: "POST",
                cache: false,
                data: JSON.stringify(requestJSONObject),
                success: function (result, textStatus) {
                    if (0 === result.code) {
                        $("#registerTip").addClass('succ').removeClass('error').html('<ul><li>' + result.msg + '</li></ul>');
                        $("#regForm, #gotoLoginBtn").hide();
                        $("#registerTip").before('' +
                            '<div class="input-wrap">\n' +
                            '    <svg><use xlink:href="#email"></use></svg>\n' +
                            '    <input id="registerVerifyCode" type="text" placeholder="短信验证码" autocomplete="off" />\n' +
                            '</div>');
                        $("#captcha").remove();
                        $("#registerBtn").show();
                        $("#registerBtn").text('验证');
                        if (Util.getParameterByName("r") !== '') {
                            $("#registerBtn").attr('onclick', 'location.href = Label.servePath + "/register?r=" + Util.getParameterByName("r") + "&code=" + $("#registerVerifyCode").val()');
                        } else {
                            $("#registerBtn").attr('onclick', 'location.href = Label.servePath + "/register?code=" + $("#registerVerifyCode").val()');
                        }
                    } else {
                        $("#registerTip").addClass('error').removeClass('succ').html('<ul><li>' + result.msg + '</li></ul>');
                        $("#registerCaptchaImg").attr("src", Label.servePath + "/captcha?code=" + Math.random());
                        $("#registerCaptcha").val("");
                    }
                }
            });
        }
    },
    /**
     * @description Register Step 2
     */
    register2: function () {
        if (Validate.goValidate({
            target: $("#registerTip2"),
            data: [{
                "target": $("#registerUserPassword2"),
                "msg": Label.invalidPasswordLabel,
                "type": 'password',
                'max': 20
            }, {
                "target": $("#registerConfirmPassword2"),
                "original": $("#registerUserPassword2"),
                "msg": Label.confirmPwdErrorLabel,
                "type": "confirmPassword"
            }]
        })) {
            var requestJSONObject = {
                userAppRole: $("input[name=userAppRole]:checked").val(),
                userPassword: calcMD5($("#registerUserPassword2").val()),
                referral: $("#referral2").val(),
                userId: $("#userId2").val(),
                mbti: $("#mbtiReg").val()
            };

            let args = "";
            if (Util.getParameterByName("r") !== '') {
                args = "?r=" + Util.getParameterByName("r");
            }

            $.ajax({
                url: Label.servePath + "/register2" + args,
                type: "POST",
                cache: false,
                data: JSON.stringify(requestJSONObject),
                success: function (result, textStatus) {
                    if (0 === result.code) {
                        window.location.href = Label.servePath;
                    } else {
                        $("#registerTip2").addClass('error').removeClass('succ').html('<ul><li>' + result.msg + '</li></ul>');
                    }
                }
            });
        }
    },
    /**
     * @description Forget password
     */
    forgetPwd: function (result) {
        if (Validate.goValidate({
            target: $("#fpwdTip"),
            data: [{
                "target": $("#fpwdPhone"),
                "msg": "手机号码不合法",
                "type": "phone"
            }]
        })) {
            var requestJSONObject = {
                userPhone: $("#fpwdPhone").val(),
                captcha: result
            };

            $.ajax({
                url: Label.servePath + "/forget-pwd",
                type: "POST",
                cache: false,
                data: JSON.stringify(requestJSONObject),
                success: function (result, textStatus) {
                    if (0 === result.code) {
                        $("#fpwdTip").addClass('succ').removeClass('error').html('<ul><li>' + result.msg + '</li></ul>');
                        $('#fpwdPhone').parent().hide();
                        //$("#fpwdPhone, #fpwdCaptcha, #fpwdSecurityCode").hide();
                        $("#fpwdTip").before('' +
                            '<div class="input-wrap">\n' +
                            '    <svg><use xlink:href="#email"></use></svg>\n' +
                            '    <input id="resetPwdVerifyCode" type="text" placeholder="短信验证码" autocomplete="off" />\n' +
                            '</div>');
                        $("#captcha").remove();
                        $("#forgetBtn").show();
                        $("#forgetBtn").text('验证');
                        $("#forgetBtn").attr('onclick', 'location.href = Label.servePath + "/reset-pwd?code=" + $("#resetPwdVerifyCode").val()');
                    } else {
                        $("#fpwdTip").removeClass("tip-succ");
                        $("#fpwdTip").addClass('error').removeClass('succ').html('<ul><li>' + result.msg + '</li></ul>');
                        $("#fpwdCaptcha").attr("src", Label.servePath + "/captcha?code=" + Math.random());
                        $("#fpwdSecurityCode").val("");
                    }
                }
            });
        }
    },
    /**
     * @description Reset password
     */
    resetPwd: function () {
        if (Validate.goValidate({
            target: $("#rpwdTip"),
            data: [{
                "target": $("#rpwdUserPassword"),
                "msg": Label.invalidPasswordLabel,
                "type": 'password',
                'max': 20
            }, {
                "target": $("#rpwdConfirmPassword"),
                "original": $("#rpwdUserPassword"),
                "msg": Label.confirmPwdErrorLabel,
                "type": "confirmPassword"
            }]
        })) {
            var requestJSONObject = {
                userPassword: calcMD5($("#rpwdUserPassword").val()),
                userId: $("#rpwdUserId").val(),
                code: $("#code").val()
            };

            $.ajax({
                url: Label.servePath + "/reset-pwd",
                type: "POST",
                cache: false,
                data: JSON.stringify(requestJSONObject),
                success: function (result, textStatus) {
                    if (0 === result.code) {
                        window.location.href = Label.servePath;
                    } else {
                        $("#rpwdTip").addClass('error').removeClass('succ').html('<ul><li>' + result.msg + '</li></ul>');
                    }
                }
            });
        }
    },
    /**
     * 登录注册等页面回车事件绑定
     */
    init: function () {
        // 注册回车事件
        $("#registerCaptcha, #registerInviteCode").keyup(function (event) {
            if (event.keyCode === 13) {
                Verify.register();
            }
        });

        // 忘记密码回车事件
        $("#fpwdSecurityCode").keyup(function (event) {
            if (event.keyCode === 13) {
                Verify.forgetPwd();
            }
        });

        // 登录密码输入框回车事件
        $("#loginPassword, #captchaLogin, #mfaCode").keyup(function (event) {
            if (event.keyCode === 13) {
                $('#loginTip').next().click();
            }
        });

        // 重置密码输入框回车事件
        $("#rpwdConfirmPassword").keyup(function (event) {
            if (event.keyCode === 13) {
                Verify.resetPwd();
            }
        });
    },
    /**
     * 新手向导初始化
     * @param {int} currentStep 新手向导步骤，0 为向导完成
     * @param {int} tagSize 标签数
     */
    initGuide: function (currentStep, tagSize) {
        if (currentStep === 0) {
            window.location.href = Label.servePath;
            return false;
        }

        var step2Sort = 'random';

        var step = function () {
            if (currentStep !== 6) {
                $('.intro dt').removeClass('current');
                $('.guide-tab > div').hide();
            }

            if (currentStep < 6 && currentStep > 0) {
                $.ajax({
                    url: Label.servePath + "/guide/next",
                    type: "POST",
                    cache: false,
                    data: JSON.stringify({
                        userGuideStep: currentStep
                    }),
                    success: function (result, textStatus) {
                        if (0 !== result.code) {
                            Util.alert(result.msg);
                        }
                    }
                });
            }


            switch (currentStep) {
                case 1:
                    $('.guide-tab > div:eq(0)').show();

                    $('.step-btn .red').hide();

                    $('.intro dt:eq(0)').addClass('current');
                    break;
                case 2:
                    $('.guide-tab > div:eq(1)').show();

                    $('.step-btn .red').show();

                    $('.intro dt:eq(1)').addClass('current');

                    // sort
                    $('.step-btn .green, .step-btn .red').prop('disabled', true);
                    $('.tag-desc').isotope({
                        sortBy: step2Sort
                    });
                    step2Sort = (step2Sort === 'random' ? 'original-order' : 'random');
                    $('.tag-desc').on('arrangeComplete', function () {
                        $('.step-btn .green, .step-btn .red').prop('disabled', false);
                    });
                    if ($('.tag-desc li').length < 2) {
                        $('.step-btn .green, .step-btn .red').prop('disabled', false);
                    }
                    break;
                case 3:
                    $('.guide-tab > div:eq(2)').show();
                    $('.intro dt:eq(2)').addClass('current');
                    $('.step-btn .red').show();
                    break;
                case 4:
                    $('.guide-tab > div:eq(3)').show();
                    $('.intro dt:eq(3)').addClass('current');

                    $('.step-btn .red').show();
                    $('.step-btn .green').text(Label.nextStepLabel);

                    $('.intro > div').hide();
                    $('.intro > dl').show();
                    break;
                case 5:
                    $('.guide-tab > div:eq(4)').show();

                    $('.step-btn .red').show();
                    $('.step-btn .green').text(Label.finshLabel);

                    $('.intro > div').show();
                    $('.intro > dl').hide();
                    break;
                case 6:
                    // finished
                    window.location.href = Label.servePath;
                    break;
                default:
                    break;

            }
        };

        $('.step-btn .green').click(function () {
            if (currentStep > 5) {
                return false;
            }
            currentStep++;
            step();
        });

        $('.step-btn .red').click(function () {
            currentStep--;
            step();
        });

        $('.tag-desc li').click(function () {
            var $it = $(this);
            if ($it.hasClass('current')) {
                Util.unfollow(window, $it.data('id'), 'tag');
                $it.removeClass('current');
            } else {
                Util.follow(window, $it.data('id'), 'tag');
                $it.addClass('current');
            }
        });

        step(currentStep);

        $('.tag-desc').isotope({
            transitionDuration: '1.5s',
            filter: 'li',
            layoutMode: 'fitRows'
        });

        // random select one tag

        var random = parseInt(Math.random() * tagSize);
        $('.tag-desc li:eq(' + random + ')').addClass('current');
        Util.follow(window, $('.tag-desc li:eq(' + random + ')').data('id'), 'tag');

    },
    /**
     * 初始化二维码登录
     *
     * */
    loginSocket: null,
    initQrCodeLogin: function () {

        this.loginSocket = new ReconnectingWebSocket(Label.loginChannelURL);
        this.loginSocket.onopen = (event) => {
            console.log('连接打开');
            this.loginSocket.send(JSON.stringify({
                type: 1,
            }))
        };

        // 发心跳包
        setInterval(function () {
            //this.loginSocket.send('p');
        }, 1000 * 5)

        this.loginSocket.onmessage = function (event) {
            console.log('收到消息:', event.data);
            let msg = event.data;
            try {
                let imgObj = document.querySelector("#qrcode_img");
                if (msg.indexOf("targetId") === 0) {
                    imgObj.src = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=web:" + msg.split(':')[1];
                } else if (msg.indexOf("apiKey") === 0) {
                    window.location.href = Label.servePath + "/loginWebInApiKey?apiKey=" + msg.split(':')[1];
                }else if(msg.indexOf("scan") === 0){
                    let boxObj = document.querySelector("#login-qrcode-div");
                    boxObj.classList.add("scan-ing")
                }
            } catch (e) {

            }
        }

        this.loginSocket.onclose =  (event)=> {
            console.log('WebSocket 连接已关闭');
        };

        this.loginSocket.onerror = (error) =>{
            console.log('WebSocket 出现错误:', error);
            this.loginSocket.close();
        };
    },
    closeQrCodeLogin: function () {
        this.loginSocket.close();
    }
};
