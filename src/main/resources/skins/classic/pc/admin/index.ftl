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
<#include "macro-admin.ftl">
<@admin "index">
<div class="content admin-index">
    <div class="module fn-content vditor-reset">
        <span style="padding: 0 0 7px 10px;display: block;font-weight: 550;">关于 Rhythm</span>
        <ul>
            ${startup}
        </ul>
        <span style="padding: 0 0 7px 10px;display: block;font-weight: 550;">数据</span>
        <ul>
            <li>${onlineVisitorCountLabel} ${onlineVisitorCnt?c}</li>
            <li>${onlineMemberCountLabel} ${onlineMemberCnt?c}</li>
            <li>${maxOnlineVisitorCountLabel} ${statistic.statisticMaxOnlineVisitorCount?c}</li>
            <li>${memberLabel} ${statistic.statisticMemberCount?c}</li>
            <li>${articleLabel} ${statistic.statisticArticleCount?c}</li>
            <li>${cmtLabel} ${statistic.statisticCmtCount?c}</li>
            <li>${domainLabel} ${statistic.statisticDomainCount?c}</li>
            <li>${tagLabel} ${statistic.statisticTagCount?c}</li>
        </ul>
        <span style="padding: 0 0 7px 10px;display: block;font-weight: 550;">实时：频道会话数</span>
        <ul>
            <li>文章列表频道 <span id="c1"></span></li>
            <li>文章频道 <span id="c2"></span></li>
            <li>私信频道 <span id="c3"></span></li>
            <li>聊天室频道（故障转移区） <span id="c4"></span></li>
            <li>五子棋频道 <span id="c5"></span></li>
            <li>管理日志频道 <span id="c6"></span></li>
            <li>商店频道（弃用） <span id="c7"></span></li>
            <li>用户频道 <span id="c8"></span></li>
        </ul>
        <span style="padding: 0 0 7px 10px;display: block;font-weight: 550;">实时：近十分钟请求数量/带宽</span>
        <ul>
            <li>文章列表频道 <span id="c1c"></span> 次 平均 <span id="c1b"></span> KB/s</li>
            <li>文章频道 <span id="c2c"></span> 次 平均 <span id="c2b"></span> KB/s</li>
            <li>私信频道 <span id="c3c"></span> 次 平均 <span id="c3b"></span> KB/s</li>
            <li>聊天室频道 <span id="c4c"></span> 次 平均 <span id="c4b"></span> KB/s</li>
            <li>五子棋频道 <span id="c5c"></span> 次 平均 <span id="c5b"></span> KB/s</li>
            <li>管理日志频道 <span id="c6c"></span> 次 平均 <span id="c6b"></span> KB/s</li>
            <li>商店频道 <span id="c7c"></span> 次 平均 <span id="c7b"></span> KB/s</li>
            <li>用户频道 <span id="c8c"></span> 次 平均 <span id="c8b"></span> KB/s</li>
        </ul>
    </div>
    <script>
        function updateStats() {
            fetch('/admin/stats')
                .then(response => response.json())
                .then(data => {
                    // 更新会话数
                    document.getElementById('c1').textContent = data.c1;
                    document.getElementById('c2').textContent = data.c2;
                    document.getElementById('c3').textContent = data.c3;
                    document.getElementById('c4').textContent = data.c4;
                    document.getElementById('c5').textContent = data.c5;
                    document.getElementById('c6').textContent = data.c6;
                    document.getElementById('c7').textContent = data.c7;
                    document.getElementById('c8').textContent = data.c8;

                    // 更新近十分钟请求数量和带宽
                    document.getElementById('c1c').textContent = data.c1c;
                    document.getElementById('c1b').textContent = data.c1b;
                    document.getElementById('c2c').textContent = data.c2c;
                    document.getElementById('c2b').textContent = data.c2b;
                    document.getElementById('c3c').textContent = data.c3c;
                    document.getElementById('c3b').textContent = data.c3b;
                    document.getElementById('c4c').textContent = data.c4c;
                    document.getElementById('c4b').textContent = data.c4b;
                    document.getElementById('c5c').textContent = data.c5c;
                    document.getElementById('c5b').textContent = data.c5b;
                    document.getElementById('c6c').textContent = data.c6c;
                    document.getElementById('c6b').textContent = data.c6b;
                    document.getElementById('c7c').textContent = data.c7c;
                    document.getElementById('c7b').textContent = data.c7b;
                    document.getElementById('c8c').textContent = data.c8c;
                    document.getElementById('c8b').textContent = data.c8b;
                })
                .catch(error => {
                    console.error('Error fetching data:', error);
                });
        }

        // 确保DOM完全加载后再执行
        document.addEventListener('DOMContentLoaded', function() {
            // 初始加载数据
            updateStats();

            // 每隔10秒更新一次数据
            setInterval(updateStats, 10000);
        });
    </script>
</div>
</@admin>
