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
    <@head title="合成大西瓜 - ${activityLabel} - ${symphonyLabel}">
        <meta charset="UTF-8">
        <style>
            * {
                padding: 0;
                margin: 0
            }

            body {
                background-color: #eeeeee;
                width: unset !important;
                height: unset !important;
            }

            #catch-the-cat {
                width: 100%;
                margin-top: 32px;
                text-align: center;
            }
        </style>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
    <link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/daxigua/style-mobile.css" />
    <script src="https://file.fishpi.cn/daxigua/layer/jquery.min.js"></script>
    <script src="https://file.fishpi.cn/daxigua/layer/layer.min.js"></script>
    <link href="https://file.fishpi.cn/daxigua/layer/layer.css" rel="stylesheet">
    <script src="https://file.fishpi.cn/daxigua/src/settings.js" charset="utf-8"></script>
</head>
<body>
<#include "../header.ftl">
<div class="main">
    <div class="wrapper">
        <div class="content">
            <div class="module">
                <h2 class="sub-head">
                    <div class="avatar-small"
                         style="background-image:url('${staticServePath}/images/activities/daxigua.png')"></div>
                    合成大西瓜
                    <span class="ft-13 ft-gray"></span>
                </h2>
                <br>

                <div style="display: flex;flex-direction: column;align-items: center">
                    <div style="width: 350px;height: 600px;align-self: center">
                        <div id="canvasDiv" >
                            <canvas id="GameCanvas" oncontextmenu="event.preventDefault()" tabindex="0"></canvas>


                        </div>
                        <div id="loadingText" style="width:100%;display: none;text-align:center;position:absolute;top:45%;z-index:2;font-size:20px;color:#f99f0a">
                            loading......0%
                        </div>

                        <div id="splash">
                            <!-- <div class="progress-bar stripes"> -->
                            <!-- <span style="width: 0%"></span> -->
                            <!-- </div> -->
                        </div>

                        <div id="loadingImg" style="top:45%;width: 100%;position:absolute; left: 36%">
                            <img src="https://file.fishpi.cn/daxigua/res/loading.gif" width="7%" height="7%" />
                        </div>
                    </div>
                </div>

            </div>
        </div>
        <div class="side">
            <#include "../side.ftl">
        </div>
    </div>
</div>
<#include "../footer.ftl">
<script type="text/javascript">
    var preloader;
    var adCompleteFlag = false;
    var resCompleteFlag = false;

    var adEndComplete = false;
    var resEndComplete = false;



    if(true){
        window.difficulty = [0, 5];
        multiplescore = 1;
        function changeDifficulty(ele, diff) {
            $('[name=difficulty]').css('background-color', '');
            $(ele).css('background-color', '#1E9FFF');
            window.difficulty = diff;
        }

        function multipleScore(ele, diff) {
            $('[name=fanbei]').css('background-color', '');
            $(ele).css('background-color', '#1E9FFF');
            multiplescore = diff;
        }

    }

    layer.open({
        type: 1
        , title: false //不显示标题栏
        , closeBtn: false
        , area: '300px;'
        , shade: 0.8
        , id: 'layer2' //设定一个id，防止重复弹出
        , resize: false
        , btn: ['开始游戏']
        , btnAlign: 'c'
        , shadeClose: true //开启遮罩关闭
        , moveType: 1 //拖拽模式，0或者1
        , content:
            '<div style="padding: 10px;text-align:center; line-height: 5px; background-color: #393D49; color: #fff;"><h4 style="line-height: normal">模式选择</h4>'
            + '<button name="difficulty" onclick="changeDifficulty(this,[0,10])">随缘Play</button><br>'
            + '<button name="difficulty" onclick="changeDifficulty(this,[0,0])">圣雄肝帝</button><br>'
            + '<button name="difficulty" style="background-color:#1E9FFF" onclick="changeDifficulty(this,[0,5])">原汁原味</button><br>'
            + '<button name="difficulty" onclick="changeDifficulty(this,[5,5])">作弊模式</button><br>'
            + '<button name="difficulty" onclick="changeDifficulty(this,[2,7])">去除小瓜</button><br>'
            + '<button name="difficulty" onclick="changeDifficulty(this,[9,9])">暴力吃瓜</button><br>'
            + '</div>'
            + '<div style="padding: 10px;text-align:center; line-height: 5px; background-color: #393D49; color: #fff;"><h4 style="line-height: normal">分数选择</h4>'
            + '<button name="fanbei" onclick="multipleScore(this,2)">两倍暴击</button><br>'
            + '<button name="fanbei" onclick="multipleScore(this,5)">五倍暴击</button><br>'
            + '<button name="fanbei" onclick="multipleScore(this,10)">十倍暴击</button><br>'
            + '<button name="fanbei" onclick="multipleScore(this,100)">百倍暴击</button><br>'
            + '<button name="fanbei" onclick="multipleScore(this,1000)">千倍暴击</button><br>'
            + '<button name="fanbei" onclick="multipleScore(this,10000)">万倍暴击</button><br>'
            + '<button name="fanbei" onclick="multipleScore(this,100000)">十万伏特</button><br>'
            + '</div>',


        success: function (layero) {
        }
    });

    var loadintT = document.getElementById("loadingText");
    var loadintGif = document.getElementById("loadingImg")
    setTimeout(function() {
        loadintGif.remove();
        loadintT.style.display = ""
        updateLabView(0.1);
    }, 1 * 1000)

    window.timer = null;
    window.tempSeconds = 1;
    window.loadData = {};
    loadData.completedCount = 0;
    loadData.totalCount = 0;

    onload();

    function onload() {
        var winHeight = document.documentElement.clientHeight;
        document.getElementById("canvasDiv").style.height = 600 + "px";
    };
    window.onload = function() {
        document.getElementById("block-Box").style.display = "none";
    }

    function updateLabView(t) {
        if (timer != null) {
            clearInterval(timer);
        }
        timer = setInterval(function() {
            tempSeconds++;
            actualTotal();
            var loadintT = document.getElementById("loadingText")
            if (!loadintT) {
                return;
            }
            loadintT.innerHTML = 'loading......' + parseInt(tempSeconds) + '%';

            switch (tempSeconds) {
                case 20:
                    updateLabView(0.2);
                    break;
                case 40:
                    updateLabView(0.3);
                    break;
                case 60:
                    updateLabView(0.4);
                    break;
                case 96:
                    updateLabView(5);
                    break;
                case 97:
                    updateLabView(10);
                    break;
                case 98:
                    updateLabView(50);
                    break;
                case 99:
                    updateLabView(100);
                    break;
                default:
                    if (tempSeconds >= 80 && tempSeconds < 96) {
                        updateLabView(t + 0.1);
                    }
                    break;
            }
            if (tempSeconds > 100) {
                clearInterval(timer);
                tempSeconds = 100
                loadintT.innerHTML = 'loading......' + parseInt(tempSeconds) + '%';
            }
        }, t * 1000);
    }

    function actualTotal() {
        var percent = parseInt(100 * loadData.completedCount / loadData.totalCount);
        if (percent > tempSeconds && loadData.totalCount > 1) {
            tempSeconds = percent;
        }
    }


    /*setTimeout("ShowBannerAD()","2000");*/
</script>
<script src="https://file.fishpi.cn/daxigua/main.js" charset="utf-8"></script>
<script type="text/javascript">
    (function() {
        // open web debugger console
        if (typeof VConsole !== 'undefined') {
            window.vConsole = new VConsole();
        }

        var splash = document.getElementById('splash');
        splash.style.display = 'block';


        var cocos2d = document.createElement('script');
        cocos2d.async = true;
        cocos2d.src = window._CCSettings.debug ? 'https://file.fishpi.cn/daxigua/cocos2d-js.js' : 'https://file.fishpi.cn/daxigua/cocos2d-js-min.js';

        var engineLoaded = function() {
            document.body.removeChild(cocos2d);
            cocos2d.removeEventListener('load', engineLoaded, false);
            window.boot();
        };
        cocos2d.addEventListener('load', engineLoaded, false);
        document.body.appendChild(cocos2d);
    })();
</script>
</body>
</html>
