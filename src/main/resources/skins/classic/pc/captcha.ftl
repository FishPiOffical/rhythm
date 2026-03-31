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
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>访客验证</title>
    <style>
        body {
            font-family: "微软雅黑", Arial, sans-serif;
            background: #f7f7f7;
            margin: 0;
            padding: 0;
        }

        .container {
            max-width: 400px;
            margin: 80px auto;
            background: #fff;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
            padding: 32px 24px;
            text-align: center;
        }

        .tip {
            font-size: 18px;
            margin-bottom: 24px;
            color: #333;
        }

        .captcha-box {
            margin-bottom: 18px;
            display: flex;
            justify-content: center;
            align-items: center;
        }

        .note {
            font-size: 14px;
            color: #888;
        }
    </style>
    <script src="https://static.geetest.com/v4/gt4.js"></script>
    <script src="https://file.fishpi.cn/tac/jquery.min.js"></script>
</head>
<body>
<div class="container">
    <div class="tip">
        访客您好，在继续浏览摸鱼派社区前请验证
    </div>
    <div class="captcha-box" id="captcha-box">
        <div id="captcha"></div>
    </div>
    <div class="note">
        登录后将不会再弹出验证<br><br>
        如果您使用火狐浏览器无法显示验证码，请尝试<br><a target="_blank" href="https://support.mozilla.org/zh-CN/kb/Firefox%20%E6%A1%8C%E9%9D%A2%E7%89%88%E7%9A%84%E5%A2%9E%E5%BC%BA%E8%B7%9F%E8%B8%AA%E4%BF%9D%E6%8A%A4#w_wang-zhan-you-wen-ti-ying-gai-zen-yao-zuo">关闭 Firefox 严格增强隐私保护</a>
    </div>
</div>

<script>
    var captchaId = "6d886bcaec3f86fcfd6f61bff5af2cb4"
    var product = "popup"
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
                var requestJSONObject = {
                    captcha: result
                };

                $.ajax({
                    url: "/validateCaptcha",
                    type: "POST",
                    cache: false,
                    data: JSON.stringify(requestJSONObject),
                    success: function (data) {
                        if (data.code === 0) {
                            window.location.href = "/"
                        } else {
                            gt.reset();
                        }
                    }
                });
                setTimeout(function () {
                    gt.reset();
                }, 3000);
            })
    });
</script>
</body>
</html>
