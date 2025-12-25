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
<@admin "misc">
<div class="wrapper">
    <div class="module">
        <div class="module-header">
            <h2>${modifiableLabel}</h2>
        </div>

        <div class="module-panel form fn-clear">
            <form action="${servePath}/admin/misc" method="POST">
                <#list options as item>
                    <#if (permissions["miscAllowAddArticle"].permissionGrant && item.oId == 'miscAllowAddArticle')
                         || (permissions["miscAllowAddComment"].permissionGrant && item.oId == 'miscAllowAddComment')
                         || (permissions["miscAllowAnonymousView"].permissionGrant && item.oId == 'miscAllowAnonymousView')
                         || (permissions["miscLanguage"].permissionGrant && item.oId == 'miscLanguage')
                         || (permissions["miscRegisterMethod"].permissionGrant && item.oId == 'miscAllowRegister')
                    >
                        <label>${item.label}</label>
                        <select id="${item.oId}" name="${item.oId}">
                            <#if "miscAllowRegister" == item.oId || "miscAllowAnonymousView" == item.oId ||
                            "miscAllowAddArticle" == item.oId || "miscAllowAddComment" == item.oId>
                            <option value="0"<#if "0" == item.optionValue> selected</#if>>${yesLabel}</option>
                            <option value="1"<#if "1" == item.optionValue> selected</#if>>${noLabel}</option>
                            <#if "miscAllowRegister" == item.oId>
                            <option value="2"<#if "2" == item.optionValue> selected</#if>>${invitecodeLabel}</option>
                            </#if>
                            </#if>
                            <#if "miscLanguage" == item.oId>
                            <option value="0"<#if "0" == item.optionValue> selected</#if>>${selectByBrowserLabel}</option>
                            <option value="zh_CN"<#if "zh_CN" == item.optionValue> selected</#if>>zh_CN</option>
                            <option value="en_US"<#if "en_US" == item.optionValue> selected</#if>>en_US</option>
                            </#if>
                        </select>
                    </#if>
                    <#if "miscArticleVisitCountMode" == item.oId>
                    <label>${item.label}</label>
                    <select name="${item.oId}">
                        <option value="0"<#if "0" == item.optionValue> selected</#if>>${noDeduplicateLabel}</option>
                        <option value="1"<#if "1" == item.optionValue> selected</#if>>${deduplicateLabel}</option>
                    </select>
                    </#if>
                </#list>

                <br/><br/>
                <button type="submit" class="green fn-right" >${submitLabel}</button>
            </form>
        </div>
    </div>

    <#if permissions["miscBroadCast"].permissionGrant>
    <div class="module">
        <div class="module-header">
            <h2>紧急公告发布</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/broadcast/warn" method="POST">
                <label>公告内容</label>
                <textarea rows="20" name="warnBroadcastText"></textarea>
                <br/><br/>
                <button type="button" onclick="Util.insertWarnBroadcastModel(1)">模版：维护5分钟</button>
                <button type="button" onclick="Util.insertWarnBroadcastModel(2)">模版：维护20秒</button>
                <button type="submit" class="green fn-right">${submitLabel}</button>
            </form>
        </div>
    </div>
    </#if>

    <div class="module">
        <div class="module-header">
            <h2>发放工资</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <label>员工工资列表</label><br><br>
            <div id="salaryList"></div>
            <button type="button" onclick="SalaryUtil.addEmployeeRow()" class="green" style="width:100%;margin:10px 0;">+ 添加员工</button>
            <br/><br/>
            <button type="button" onclick="SalaryUtil.paySalary()" class="green fn-right" style="width:100%;">发放工资</button>
        </div>
    </div>
</div>

<script>
const SalaryUtil = {
    STORAGE_KEY: 'admin_salary_list',
    servePath: '${servePath}',

    init: function() {
        this.loadFromStorage();
    },

    addEmployeeRow: function(data) {
        const container = document.getElementById('salaryList');
        const row = document.createElement('div');
        row.className = 'salary-row';
        row.style.cssText = 'display:flex;gap:10px;margin:10px 0;align-items:center;';
        const userName = data ? data.userName : '';
        const amount = data ? data.amount : '';
        row.innerHTML =
            '<input type="text" class="salary-username" value="' + userName + '" placeholder="用户名" style="flex:1;padding:8px;" oninput="SalaryUtil.saveToStorage()">' +
            '<input type="number" class="salary-amount" value="' + amount + '" placeholder="工资" min="1" style="width:100px;padding:8px;" oninput="SalaryUtil.saveToStorage()">' +
            '<button type="button" onclick="SalaryUtil.removeEmployeeRow(this)" style="padding:8px 16px;">删除</button>';
        container.appendChild(row);
        this.saveToStorage();
    },

    removeEmployeeRow: function(btn) {
        const row = btn.closest('.salary-row');
        row.remove();
        this.saveToStorage();
    },

    saveToStorage: function() {
        const data = this.getEmployeeData();
        localStorage.setItem(this.STORAGE_KEY, JSON.stringify(data));
    },

    loadFromStorage: function() {
        const stored = localStorage.getItem(this.STORAGE_KEY);
        if (stored) {
            try {
                const data = JSON.parse(stored);
                if (Array.isArray(data) && data.length > 0) {
                    document.getElementById('salaryList').innerHTML = '';
                    data.forEach(emp => this.addEmployeeRow(emp));
                    return;
                }
            } catch (e) {
                console.error('Failed to load salary list:', e);
            }
        }
        // 默认添加一行
        this.addEmployeeRow();
    },

    getEmployeeData: function() {
        const rows = document.querySelectorAll('.salary-row');
        const data = [];
        rows.forEach(row => {
            const userName = row.querySelector('.salary-username').value.trim();
            const amount = parseInt(row.querySelector('.salary-amount').value);
            if (userName && amount > 0) {
                data.push({ userName: userName, amount: amount });
            }
        });
        return data;
    },

    paySalary: function() {
        const data = this.getEmployeeData();
        if (data.length === 0) {
            alert('请至少添加一名员工');
            return;
        }

        if (!confirm('确认向 ' + data.length + ' 名员工发放工资？')) {
            return;
        }

        fetch(this.servePath + '/admin/pay-salary', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ employees: data })
        })
        .then(res => res.json())
        .then(result => {
            alert(result.msg + '\n\n' + result.logMessage);
        })
        .catch(err => {
            alert('请求失败：' + err.message);
        });
    }
};

// 页面加载时初始化
SalaryUtil.init();
</script>
</@admin>
