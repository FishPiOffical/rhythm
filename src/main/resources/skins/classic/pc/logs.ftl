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
        <link rel="stylesheet" href="${staticServePath}/css/logs.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content logs-page">
                    <h1>公开日志</h1>
                    <div class="logs-description">公开管理操作记录</div>
                    <div class="logs-filter">
                        <label for="logCategorySelect">分类</label>
                        <select id="logCategorySelect" aria-label="日志分类">
                            <option value="">全部日志</option>
                        </select>
                        <button type="button" id="clearLogFilter" class="logs-filter__clear" hidden>清除</button>
                    </div>
                    <div id="logsContent" class="logs-list" aria-live="polite"></div>
                    <button type="button" id="loadMoreBtn" class="logs-more">加载更多</button>
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
