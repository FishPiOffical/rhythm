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
<html lang="zh-hans">
    <script type="text/javascript" src="../../../../js/lib/jquery/jquery.min.js"></script>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <meta name="keywords" content="Emoji真假小黄脸" />
    <link rel="stylesheet" type="text/css" href="../../../../games/emojiPair/dict-web.min.css">
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge" />
    <meta name="format-detection" content="telephone=no">
    <meta name="renderer" content="webkit">
    <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
    <meta http-equiv="Cache-Control" content="no-siteapp" />
    <meta name="MobileOptimized" content="width" />
    <meta name="HandheldFriendly" content="true" />
    <meta http-equiv="cleartype" content="on" />
    <script src="${staticServePath}/js/common${miniPostfix}.js?${staticResourceVersion}"></script>
    <script>
        var Label = {
            csrfToken: '${csrfToken}'
        }
    </script>
<body>
<div class="content_right">
    <div class="pages_header banner-animation container">
        <div class="pages_info">
            <div class="pages_name capitalize col-auto line">
                <h1>Emoji 真假小黄脸</h1>
                <br>
                <p class="small">调皮的小黄脸玩起了真假美猴王的游戏，在众黄脸中找出他们吧！</p>
            </div>
            <div class="pages_info_bg banner-emojis">
                <div class="line-row yy06" id="⛰️"></div>
                <div class="line-row yy01" id="🌳🌳🌳🌳🌳🌳🌳🌳"></div>
                <div class="line-row yy02" id="🌲🌲🌲🌲🌲🌲"></div>
                <div class="line-row yy03" id="🍃"></div>
                <div class="line-row yy07" id="🏘️"></div>
                <div class="line-row yy08" id="🏠️"></div>
                <div class="line-row yy09" id="🏢"></div>
                <div class="line-row yy17" id="🎡"></div>
                <div class="line-row yy18" id="🎪"></div>
                <div class="line-row yy16" id="🌳🌳🌳🌳🌳🌳🌳️"></div>
                <div class="line-row yy14" id="🌻🌷🌻🌷🌻"></div>
                <div class="line-row yy15" id="🐕️"></div>
                <div class="line-row overlay"></div>
            </div>
        </div>
    </div>
    <div class="content_body"><div class="container"><div class="row"><div class="col">

        <div class="row page_emoji_list"><div class="col"> <div class="field field-name-body field-type-text-with-summary field-label-hidden"><div class="field-items"><div class="field-item even" property="content:encoded">
            <div class="emoji_card_list pages game_content">
                <div id="container" style="opacity:0; width: 60%; margin: 0 auto">
                    <div id="game" class="fs">
                        <div id="hintBtn">TIPS</div>
                        <div class="foundTxt"></div>
                        <div class="timeTxt">60:00</div>
                        <div class="timePlus">+5</div>
                        <div id="btnArea">
                            <div id="b1" class="btn"></div>
                        </div>
                        <div class="end">
                            <div class="rewardTxt"></div>
                            <div class="endTxt"></div>
                            <div id="replayBtn">
                                <svg xmlns="http://www.w3.org/2000/svg" width="240" height="240" viewBox="0 0 240 240">
                                    <rect width="240" height="240" fill="none" />
                                    <path fill="#888da8" d="M120,50V10L70,60l50,50V70c33.11,0,60,26.9,60,60c0,33.11-26.89,60-60,60c-33.1,0-60-26.89-60-60H40 c0,44.2,35.8,80,80,80s80-35.8,80-80S164.2,50,120,50z" />
                                </svg>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="emoji_card_content" style="text-align: center" >
                    <p class="mb-0 small">tips: 在游戏中成功找出2个相同的表情符号即可产生新的随机排列。<br>
                        每成功找出可增加游戏时间5秒。在有限的时间内找出尽可能多的表情符号吧。</p>
                </div>
                <script type="text/javascript" src="../../../../games/emojiPair/game.js"></script>
                <script type="text/javascript" src="../../../../games/emojiPair/emojiPair.js"></script>
                <link rel="stylesheet" type="text/css" href="../../../../games/emojiPair/emojiPair.css">
            </div>
        </div></div></div></div></div>
        <div class="small" style="text-align: right">
            移植自<a href="https://www.emojiall.com/zh-hans/game-01">emojiall</a>
        </div>
    </div></div></div></div>
</div>
</body>
</html>
