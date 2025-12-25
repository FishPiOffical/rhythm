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
    <@head title="VIP管理 - ${symphonyLabel}">
        <meta name="robots" content="none"/>
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/home.css?${staticResourceVersion}"/>
    <link rel="stylesheet" href="${staticServePath}/skins/classic/vip/css/admin.css?${staticResourceVersion}"/>
    <style>

    </style>
</head>
<body>
<#include "../header.ftl">
<div class="container">

    <div class="tabs">
        <button class="tab active" data-tab="membership">会员月卡管理</button>
        <button class="tab" data-tab="coupons">优惠券管理</button>
    </div>

    <!-- 会员月卡管理 -->
    <div id="membership" class="tab-content active">
        <div class="card">
            <h2>添加会员月卡</h2>
            <form id="membershipForm">
                <div class="form-row">
                    <div class="form-group">
                        <label>等级名称 *</label>
                        <input type="text" class="form-control" id="lvName" value="" required>
                    </div>
                    <div class="form-group">
                        <label>等级代码 *</label>
                        <input type="text" class="form-control" id="lvCode" value="" required>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>价格 *</label>
                        <input type="number" class="form-control" id="price" min="0" step="1" value="0" required>
                    </div>
                    <div class="form-group">
                        <label>时长类型 *</label>
                        <input type="text" class="form-control" id="durationType" value="" required>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>时长值 *</label>
                        <input type="number" class="form-control" id="durationValue" min="1" value="" required>
                    </div>
                    <div class="form-group">
                        <label>权益 *</label>
                        <input type="text" class="form-control" id="benefits" required>
                    </div>
                </div>
                <button type="submit" class="btn btn-success">添加会员月卡</button>
                <button type="button" class="btn btn-outline" onclick="resetMembershipForm()">重置表单</button>
            </form>
        </div>

        <div class="card">
            <h2>会员月卡列表</h2>
<#--            <div class="search-box">-->
<#--                <input type="text" class="search-input" id="membershipSearch" placeholder="搜索等级名称或代码...">-->
<#--                <button class="btn" onclick="searchMemberships()">搜索</button>-->
<#--                <button class="btn btn-outline" onclick="clearMembershipSearch()">清除</button>-->
<#--            </div>-->
            <div class="table-container">
                <table id="membershipTable">
                    <thead>
                    <tr>
                        <th>名称</th>
                        <th>代码</th>
                        <th>价格</th>
                        <th>类型</th>
                        <th>时长</th>
                        <th>权益</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="membershipList">
                    <!-- 会员数据将通过JavaScript动态加载 -->
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <!-- 优惠券管理 -->
    <div id="coupons" class="tab-content">
        <div class="card">
            <h2>添加优惠券</h2>
            <form id="couponForm">
                <div class="form-row">
                    <div class="form-group">
                        <label>优惠券代码</label>
                        <input type="text" class="form-control" id="couponCode" maxlength="32" minlength="8">
                    </div>
                    <div class="form-group">
                        <label>优惠券类型</label>
                        <input type="text" class="form-control" id="couponType">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>折扣金额 (百分比) *</label>
                        <input type="number" class="form-control" id="discount" min="0" step="0.01" value="100"
                               required>
                    </div>
                    <div class="form-group">
                        <label>使用次数 *</label>
                        <input type="number" class="form-control" id="times" value="-1" required>
                        <small style="color: #666;">-1 表示无限制</small>
                    </div>
                </div>
                <button type="submit" class="btn btn-success">添加优惠券</button>
                <button type="button" class="btn btn-outline" onclick="resetCouponForm()">重置表单</button>
            </form>
        </div>

        <div class="card">
            <h2>优惠券列表</h2>
<#--            <div class="search-box">-->
<#--                <input type="text" class="search-input" id="couponSearch" placeholder="搜索优惠券名称或代码...">-->
<#--                <button class="btn" onclick="searchCoupons()">搜索</button>-->
<#--                <button class="btn btn-outline" onclick="clearCouponSearch()">清除</button>-->
<#--            </div>-->
            <div class="table-container">
                <table id="couponTable">
                    <thead>
                    <tr>
                        <th>优惠券</th>
                        <th>类型</th>
                        <th>折扣</th>
                        <th>次数</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody id="couponList">
                    <!-- 优惠券数据将通过JavaScript动态加载 -->
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>
<#include "../footer.ftl">
<script src="${staticServePath}/skins/classic/vip/js/admin.js?${staticResourceVersion}"></script>
</body>
</html>
