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
<html>
    <head>
        <@head title="榜单 - ${symphonyLabel}">
            <link rel="canonical" href="${servePath}/top/index">
        </@head>
    </head>
    <body>
        <#include "../header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content activity">
                    <h2 class="sub-head"><span>🏅</span> ${totalRankLabel}</h2>
                    <div class="list">
                        <ul>
                            <li class="fn__flex">
                                <svg class="avatar"><use xlink:href="#linkIcon"></use></svg>
                                <div class="fn-flex-1">
                                    <h2>
                                        <a class="title"
                                           href="${servePath}/top/link">链接排行</a>
                                    </h2>
                                    <div class="ft-fade ft-smaller">链接排行榜</div>
                                </div>
                            </li>
                            <li class="fn__flex">
                                <svg class="avatar"><use xlink:href="#balanceIcon"></use></svg>
                                <div class="fn-flex-1">
                                    <h2>
                                        <a class="title"
                                           href="${servePath}/top/balance">财富排行</a>
                                    </h2>
                                    <div class="ft-fade ft-smaller">有钱人的世界我不懂</div>
                                </div>
                            </li>
                            <li class="fn__flex">
                                <svg class="avatar"><use xlink:href="#consumptionIcon"></use></svg>
                                <div class="fn-flex-1">
                                    <h2>
                                        <a class="title"
                                           href="${servePath}/top/consumption">消费排行</a>
                                    </h2>
                                    <div class="ft-fade ft-smaller">今晚的消费，由陈公子买单</div>
                                </div>
                            </li>
                            <li class="fn__flex">
                                <svg class="avatar"><use xlink:href="#checkinIcon"></use></svg>
                                <div class="fn-flex-1">
                                    <h2>
                                        <a class="title"
                                           href="${servePath}/top/checkin">签到排行</a>
                                    </h2>
                                    <div class="ft-fade ft-smaller">公司打卡是生存，摸鱼才是生活</div>
                                </div>
                            </li>
                            <li class="fn__flex">
                                <svg class="avatar"><use xlink:href="#onlineIcon"></use></svg>
                                <div class="fn-flex-1">
                                    <h2>
                                        <a class="title"
                                           href="${servePath}/top/online">在线时间排行</a>
                                    </h2>
                                    <div class="ft-fade ft-smaller">摸鱼总统山</div>
                                </div>
                            </li>
                            <li class="fn__flex">
                                <img class="avatar" src="${staticServePath}/games/adarkroom/img/adr.png" />
                                <div class="fn-flex-1">
                                    <h2>
                                        <a class="title"
                                           href="${servePath}/top/adr">A Dark Room 游戏总分排行</a>
                                    </h2>
                                    <div class="ft-fade ft-smaller">通过了 ADR 游戏的玩家总分排名</div>
                                </div>
                            </li>
                        </ul>
                        <br/>
                    </div>
                </div>
                <div class="side">
                    <#include "../side.ftl">
                </div>
            </div>
        </div>
        <#include "../footer.ftl">
        <script src="${staticServePath}/js/settings${miniPostfix}.js?${staticResourceVersion}"></script>
    </body>
</html>