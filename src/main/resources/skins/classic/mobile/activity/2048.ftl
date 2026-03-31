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
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <title>2048  &#9733; &#9829; </title>
    <meta name="description"
          content="2048 - Join numbers and symbols to get to 2048 tile.">
    <meta name="keywords" content="2048, game, facebook, friends">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta property="og:title" content="2048 - Join numbers and symbols to get to 2048 tile" />
    <meta property="og:type" content="website" />
    <meta property="og:url" content="https://veerasundar.com/2048/" />
    <meta property="og:image" content="https://veerasundar.com/img/projects/2048.png" />
    <link rel="icon" type="image/png" href="${iconURL}" />
    <link rel="apple-touch-icon" href="${staticServePath}/images/faviconH.png">

    <link rel="stylesheet" type="text/css" href="../../games/2048/css/normalize.css">
    <link rel="stylesheet" type="text/css" href="../../games/2048/css/fontello-embedded.css">
    <link rel="stylesheet" type="text/css" href="../../games/2048/css/2048.css">
</head>
<body>
<div class='container'>
    <header>
        <div class='site-info'>
            <h1>2048</h1>
            <h2>
                <span class="icon-heart"></span><span class="icon-star"></span>
            </h2>
        </div>
        <div class='control-panel'>
            <div id="game-score" class='score'>
                <label>score</label>
                <strong class="animated"></strong>
            </div>
            <div id="best-score" class='score best'>
                <label>best</label>
                <strong class="animated"></strong>
            </div>
        </div>
    </header>
    <p class="sub">
        Join the numbers &amp; symbols to get to <strong>2048 tile</strong>.
    </p>
    <div class='app'>
        <div class='grid'>
            <div class='row'>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
            </div>
            <div class='row'>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
            </div>
            <div class='row'>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
            </div>
            <div class='row'>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
                <div class='cell'></div>
            </div>
        </div>
        <div class='pad'></div>
    </div>

    <div class='content'>

        <div class='how-to'>
            <p>
                <strong>怎么玩？</strong>
                我觉得这个不需要介绍，方向键和手指都可以。
                只是增加了一个设定，合并的时候需要至少一个图案相同
                增加了<strong>一丢丢</strong>难度
            </p>
        </div>
        <div class='author'>
            <p>
                <a href="https://fishpi.cn">摸鱼派</a>·鱼游
                Created by <a href="http://veerasundar.com"><strong>Veera Sundar</strong></a>.
            </p>
        </div>
    </div>
</div>
<script src="../../games/2048/js/require.js" data-main="../../games/2048/js/main"></script>
</body>
</html>
