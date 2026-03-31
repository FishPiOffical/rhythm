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
<@admin "ip">
    <div class="content">
        <div class="module">
            <div class="module-header">
                <h2>IP 封/解禁</h2>
            </div>

            <div class="module-panel form fn-clear form--admin">
                <form action="${servePath}/admin/ip" method="POST" id="ipListForm">
                    <label>IP列表 (每行一个)</label>
                    <textarea rows="20" name="ipList"></textarea>
                    <br/><br/>
                    <input id="ipType" name="type" type="text" value="unban" style="display: none" />
                    <button type="button" class="red" onclick="Util.ipAction(1)">封禁</button>
                    <button type="button" class="green fn-right" onclick="Util.ipAction(2)">解封</button>
                </form>
            </div>
        </div>
    </div>
</@admin>
