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
<#include "macro-head.ftl">
<!DOCTYPE html>
<html>
    <head>
        <@head title="日志公开 - ${symphonyLabel}">
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content">
                    <h1>管理日志公开</h1>
                    <i class="ft-gray">摸鱼派管理组公平、公正、公开，感谢大家的监督。(点击日志类型可以筛选)</i>
                    <br><br>
                    <div id="logsContent">
                    </div>
                    <br>
                    <div style="color: #888f91; cursor: pointer" onclick="Logs.more()" id="loadMoreBtn">加载更多</div>
                    <div style="color: #888f91; cursor: pointer;margin-top: 1rem" onclick="Logs.reset();" id="loadMoreBtn">重置筛选</div>
                </div>
                <div class="side">
                    <#include "side.ftl">
                </div>
            </div>
        </div>
        <#include "footer.ftl">
        <script src="${staticServePath}/js/logs${miniPostfix}.js?${staticResourceVersion}"></script>
        <script>
            //var logsChannelURL = "${wsScheme}://${serverHost}:${serverPort}${contextPath}/logs-channel";
        </script>
    </body>
</html>
