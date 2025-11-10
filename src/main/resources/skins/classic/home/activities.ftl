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
        <@head title="${activityLabel} - ${symphonyLabel}">
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "../header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content activity">
                    <div class="module">
                        <h2 class="sub-head">${activityLabel}</h2>
                        <div class="list">
                            <ul>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('https://file.fishpi.cn/evolve/evolved.ico')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/games/evolve/">《进化-Evolve》</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                在本游戏中，您首先由原生质选择进化为某种智慧生物，然后带领他们走向繁荣昌盛，最后迈向星空，征服宇宙。<br>
                                                支持第三方存档云同步（需手动在游戏设定中开启），参与 <a href="${servePath}/top/evolve">进化排行榜</a>
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('https://file.fishpi.cn/adarkroom/img/adr.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/games/adarkroom/?lang=zh_cn">《A Dark Room》</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                极简主义的游戏杰作，有上世纪 ascii 游戏的遗风。<br>
                                                支持存档云同步，参与 <a href="${servePath}/top/adr">${symphonyLabel} ADR 游戏总分排行榜</a>
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('https://file.fishpi.cn/2023/08/minecraft-71137c15.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a target="_blank" href="https://fishpi.cn/article/1689661528756">Minecraft 私服</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                著名 Minecraft 方块放置与探险游戏，社区成员 <a href="https://fishpi.cn/member/Yui" target="_blank">Yui</a> 搭建的鱼油专属私服。可打怪，可挖矿，可田园，一起来耍吧~<i>♂♂</i>
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('https://file.fishpi.cn/2023/05/icon-4d115aa0.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a target="_blank" href="https://maze.hancel.org/">《Maze》</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                游戏规则十分简单，游戏开始会随机生成一个迷宫，但每次只显示你所在当下位置周围 3 × 3 的迷宫，你只需要凭直觉找到出口即可。<br><del title="已停服">通过游戏右上角 Login 按钮使用摸鱼派账号登录后，每次通关将会<b>奖励关卡数 × 10的积分</b></del>。
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('https://wju.adventext.fun/images/logo.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a target="_blank" href="https://wju.adventext.fun/">WJU 谜题</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                游戏开始会随机生成 WJU 三个字母组成的字符串，你需要通过 5 种字符串的操作，来生成最终的目标字符串。<br>使用摸鱼派账号登录后，每次通关将会随机<b>奖励 128 ~ 1024 的积分</b>。连续通关奖励可以叠加！
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/mofish.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="https://p.hancel.org/" target="_blank">摸鱼大闯关</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                一个非常烧脑的 CTF 类闯关游（酷）戏（刑）。这个游戏的目标就只有一个：进入下一关。在大部分关卡中，你需要利用网页上的提示来找到密码并进入下一关。<br>
                                                参与 <a href="${servePath}/top/mofish">摸鱼大闯关排行榜</a>
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/mofish.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="https://puzzle.iwpz.net/" target="_blank">摸鱼小闯关</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                               一个<a href="https://p.hancel.org/" target="_blank">摸鱼大闯关</a>的"山寨版"，但关卡内容为原创。
                                                框架来自 <a href="https://fishpi.cn/member/imlinhanchao" target="_blank">跳跳</a><br>
                                                参与 <a href="${servePath}/top/smallmofish">摸鱼小闯关排行榜</a>
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/games/handle/favicon.svg')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/games/handle/" target="_blank">汉兜</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                汉兜是一款猜四字词语的有趣游戏，你有十次的机会猜一个四字词语。<br>
                                                <b>每天首次游戏获积分奖励</b>，快来猜猜吧！
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/restart.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/games/lifeRestart/view/">人生重开模拟器</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                如果你不满意现在的人生，那就来重开吧～<br>
                                                支持存档云同步，参与 <a href="${servePath}/top/lifeRestart">人生重开模拟器成就排行榜</a>
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar" style=" font-size: 2rem; margin-bottom: 0.5rem;display: flex;
                                            justify-content: center;align-items: center;">😘</div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/games/emojiPair">Emoji 真假小黄脸</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                调皮的小黄脸玩起了真假美猴王的游戏，在众黄脸中找出他们吧！<br>
                                                <b>每天首次游戏获积分奖励</b>，封顶30积分，参与 <a href="${servePath}/top/emoji">Emoji 真假小黄脸排行榜</a>
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/activities/cat.jpg')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/activity/catch-the-cat">围住小猫</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                你能围住这只调皮的小猫么？
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/activities/2048.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/activity/2048">2048</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                加强版2048，来吧~
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/activities/daxigua.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/activity/daxigua">合成大西瓜</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                大~大~大~大西瓜~
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/activities/char.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/activity/character">${characterLabel}</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">${activityCharacterTitleLabel}</span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/activities/snak.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/activity/eating-snake">${eatingSnakeLabel}</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                                ${activityEatingSnakeTitleLabel}
                                            </span>
                                        </div>
                                    </div>
                                </li>
                                <li>
                                    <div class='fn-flex'>
                                        <div class="avatar"
                                             style="background-image:url('${staticServePath}/images/activities/gobang.png')"></div>
                                        <div class="fn-flex-1">
                                            <h2>
                                                <a href="${servePath}/activity/gobang">${gobangLabel}</a>
                                            </h2>
                                            <span class="ft-fade vditor-reset">
                                            ${activityGobangTitleLabel}
                                            </span>
                                        </div>
                                    </div>
                                </li>
                            </ul>
                        </div>
                    </div>
                </div>
                <div class="side">
                    <#include "../side.ftl">
                </div>
            </div>
        </div>
        <#include "../footer.ftl">
    </body>
</html>
