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
<@admin "vipManage">
<div class="content" id="vipAdminRoot">
    <div class="module">
        <div class="module-header">
            <h2>VIP 管理</h2>
        </div>
        <div class="module-panel form fn-clear form--admin vip-admin-panel">
            <div class="vip-admin-row">
                <label>用户名筛选</label>
                <input type="text" id="vipSearchUserName" placeholder="按用户名筛选"/>
                <label>等级</label>
                <select id="vipSearchLvCode">
                    <option value="">全部</option>
                </select>
                <label>状态</label>
                <select id="vipSearchState">
                    <option value="">全部</option>
                    <option value="1">生效</option>
                    <option value="0">失效</option>
                </select>
                <button type="button" class="green" id="vipSearchBtn">查询</button>
                <button type="button" id="vipResetBtn">重置</button>
            </div>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>VIP 列表</h2>
        </div>
        <div class="module-panel form fn-clear form--admin vip-admin-panel">
            <div class="table-container">
                <table class="table" id="vipMembershipTable">
                    <thead>
                    <tr>
                        <th>用户名</th>
                        <th>用户ID</th>
                        <th>等级</th>
                        <th>状态</th>
                        <th>到期时间</th>
                        <th>更新时间</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="vipMembershipTableBody">
                    <tr>
                        <td colspan="7" class="ft-gray">加载中...</td>
                    </tr>
                    </tbody>
                </table>
            </div>
            <div class="vip-admin-pagination">
                <button type="button" id="vipPrevPage">上一页</button>
                <span id="vipPageInfo">第 1 页</span>
                <button type="button" id="vipNextPage">下一页</button>
            </div>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>新增免费 VIP（不扣积分）</h2>
        </div>
        <div class="module-panel form fn-clear form--admin vip-admin-panel">
            <div class="vip-admin-row">
                <label>用户（用户名或ID）</label>
                <input type="text" id="vipAddUser" placeholder="用户名或用户ID"/>
                <label>等级</label>
                <select id="vipAddLevel"></select>
            </div>
            <div class="vip-admin-row vip-config-row">
                <label>配置项</label>
                <div class="vip-config-builder" id="vipAddConfigBuilder"></div>
            </div>
            <div class="vip-admin-row">
                <button type="button" class="green" id="vipAddBtn">新增 VIP</button>
            </div>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>维护 VIP 信息</h2>
        </div>
        <div class="module-panel form fn-clear form--admin vip-admin-panel">
            <div class="vip-admin-row">
                <label>用户（用户名或ID）</label>
                <input type="text" id="vipUpdateUser" placeholder="用户名或用户ID"/>
                <label>等级（可选）</label>
                <select id="vipUpdateLvCode">
                    <option value="">不修改</option>
                </select>
            </div>
            <div class="vip-admin-row">
                <label>状态（可选）</label>
                <select id="vipUpdateState">
                    <option value="">不修改</option>
                    <option value="1">生效</option>
                    <option value="0">失效</option>
                </select>
                <label>到期时间（毫秒）</label>
                <input type="text" id="vipUpdateExpiresAt" placeholder="例如 1767225600000"/>
                <button type="button" id="vipClearExpiresBtn">清空到期时间</button>
            </div>
            <div class="vip-admin-row">
                <label>
                    <input type="checkbox" id="vipUpdateConfigEnabled"/>
                    同时更新配置
                </label>
                <span class="vip-config-hint">勾选后按下方配置项覆盖原配置</span>
            </div>
            <div class="vip-admin-row vip-config-row" id="vipUpdateConfigRow" style="display:none;">
                <label>配置项</label>
                <div class="vip-config-builder" id="vipUpdateConfigBuilder"></div>
            </div>
            <div class="vip-admin-row">
                <label>延长天数</label>
                <input type="number" id="vipExtendDays" min="1" value="30" placeholder="天数"/>
                <button type="button" class="green" id="vipExtendBtn">一键延长到期</button>
                <button type="button" class="green" id="vipUpdateBtn">更新 VIP</button>
            </div>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>VIP 按天退款</h2>
        </div>
        <div class="module-panel form fn-clear form--admin vip-admin-panel">
            <div class="vip-admin-row">
                <label>用户（用户名或ID）</label>
                <input type="text" id="vipRefundUser" placeholder="用户名或用户ID"/>
                <button type="button" class="red" id="vipRefundBtn">执行退款</button>
            </div>
            <div class="vip-admin-row ft-gray">
                说明：基于当前会员剩余天数按开通价格折算退款，并立即失效该会员。
            </div>
        </div>
    </div>
</div>
<link rel="stylesheet" href="${staticServePath}/css/vip-admin.css?${staticResourceVersion}"/>
<script src="${staticServePath}/js/vip-admin${miniPostfix}.js?${staticResourceVersion}"></script>
</@admin>
