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
<div class="content">
    <div class="module">
        <div class="module-header">
            <h2>${modifiableLabel}</h2>
        </div>

        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/misc" method="POST">
                <#list options as item>
                    <#if (permissions["miscAllowAddArticle"].permissionGrant && item.oId == 'miscAllowAddArticle')
                    || (permissions["miscAllowAddComment"].permissionGrant && item.oId == 'miscAllowAddComment')
                    || (permissions["miscAllowAnonymousView"].permissionGrant && item.oId == 'miscAllowAnonymousView')
                    || (permissions["miscLanguage"].permissionGrant && item.oId == 'miscLanguage')
                    || (permissions["miscRegisterMethod"].permissionGrant && item.oId == 'miscAllowRegister')
                    >
                    <div class="fn__flex">
                        <label>
                            <div>${item.label}</div>
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
                            <option value="0"<#if "0" == item.optionValue>
                                    selected</#if>>${selectByBrowserLabel}</option>
                            <option value="zh_CN"<#if "zh_CN" == item.optionValue> selected</#if>>zh_CN</option>
                            <option value="en_US"<#if "en_US" == item.optionValue> selected</#if>>en_US</option>
                            </#if>
                            </select>
                        </label>
                    </div>
                    </#if>
                    <#if "miscArticleVisitCountMode" == item.oId>
                    <div class="fn__flex">
                        <label>
                            <div>${item.label}</div>
                            <select name="${item.oId}">
                                <option value="0"<#if "0" == item.optionValue> selected</#if>>${noDeduplicateLabel}</option>
                                <option value="1"<#if "1" == item.optionValue> selected</#if>>${deduplicateLabel}</option>
                            </select>
                        </label>
                    </div>
                    </#if>
                </#list>

                <br/>
                <button type="submit" class="green fn-right">${submitLabel}</button>
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
                <div class="fn__flex">
                    <label>
                        <div>公告内容</div>
                        <textarea rows="20" name="warnBroadcastText"></textarea>
                    </label>
                </div>
                <br/>
                <button type="button" onclick="Util.insertWarnBroadcastModel(1)">模版：维护5分钟</button>
                <button type="button" onclick="Util.insertWarnBroadcastModel(2)">模版：维护20秒</button>
                <button type="submit" class="green fn-right">${submitLabel}</button>
            </form>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>社区安全开关（临时，重启恢复默认开启）</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <div class="fn__flex">
                <label style="width:100%;">
                    <div>社区 CC 防火墙（BeforeRequestHandler → Firewall）</div><br>
                    <div class="fn__flex">
                        <label style="margin-right:12px;">
                            <input type="checkbox" id="firewallEnabled" <#if firewallEnabled?? && firewallEnabled>checked</#if>/> 启用
                        </label>
                        <input type="number" id="firewallThreshold" min="1" value="${firewallThreshold!400}" style="width:120px;" placeholder="阈值/分钟">
                        <button type="button" class="green" style="margin-left:8px;" onclick="SecuritySwitches.updateFirewall()">保存</button>
                    </div><br><br>
                    <div class="ft-small">阈值=每分钟请求数，超过则 ipset 封禁；默认 400。</div>
                </label>
            </div>
            <div class="fn__flex" style="margin-top:12px;">
                <label style="width:100%;">
                    <div>社区验证盾（AnonymousViewCheckMidware 验证码）</div><br>
                    <div class="fn__flex">
                        <label style="margin-right:12px;">
                            <input type="checkbox" id="verificationEnabled" <#if verificationShieldEnabled?? && verificationShieldEnabled>checked</#if>/> 启用
                        </label>
                        <button type="button" class="green" onclick="SecuritySwitches.updateVerification()">保存</button>
                    </div><br><br>
                    <div class="ft-small">关闭后匿名访问不再触发验证码；重启会恢复默认开启。</div><br>
                    <div class="fn__flex" style="margin-top:8px;">
                        <label style="margin-right:12px;">
                            <input type="checkbox" id="firstVisitCaptchaEnabled" <#if firstVisitCaptchaEnabled?? && firstVisitCaptchaEnabled>checked</#if>/> 首次访问（2 小时内）必需验证码
                        </label>
                        <button type="button" class="green" onclick="SecuritySwitches.updateFirstVisit()">保存</button>
                    </div>
                </label>
            </div>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>发放工资</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <div class="fn__flex">
                <label>
                    <div>员工工资列表</div>
                    <table id="salaryTable" class="table" style="width: 100%;">
                        <thead>
                            <tr>
                                <th>用户名</th>
                                <th>工资数额</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody id="salaryTableBody">
                        </tbody>
                    </table>
                    <button type="button" onclick="SalaryUtil.addEmployeeRow()" class="green">+ 添加员工</button>
                </label>
            </div>
            <br/>
            <button type="button" onclick="SalaryUtil.paySalary()" class="green fn-right">发放工资</button>
        </div>
    </div>
    </#if>
</div>

<script>
const SalaryUtil = {
    STORAGE_KEY: 'admin_salary_list',
    servePath: '${servePath}',

    init: function() {
        this.loadFromStorage();
    },

    addEmployeeRow: function(data) {
        const tbody = document.getElementById('salaryTableBody');
        const row = document.createElement('tr');
        const userName = data ? data.userName : '';
        const amount = data ? data.amount : '';
        row.innerHTML =
            '<td><input type="text" class="salary-username" value="' + userName + '" placeholder="请输入用户名" oninput="SalaryUtil.saveToStorage()"></td>' +
            '<td><input type="number" class="salary-amount" value="' + amount + '" placeholder="请输入工资数额" min="1" oninput="SalaryUtil.saveToStorage()"></td>' +
            '<td><button type="button" onclick="SalaryUtil.removeEmployeeRow(this)">删除</button></td>';
        tbody.appendChild(row);
        this.saveToStorage();
    },

    removeEmployeeRow: function(btn) {
        const row = btn.closest('tr');
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
                    document.getElementById('salaryTableBody').innerHTML = '';
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
        const rows = document.querySelectorAll('#salaryTableBody tr');
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

const SecuritySwitches = {
    servePath: '${servePath}',
    updateFirewall() {
        const enabled = document.getElementById('firewallEnabled').checked;
        const threshold = document.getElementById('firewallThreshold').value;
        fetch(this.servePath + '/admin/security/firewall', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: 'enabled=' + enabled + '&threshold=' + encodeURIComponent(threshold)
        }).then(res => res.json()).then(res => {
            alert(res.msg || '已更新');
            if (res.data && res.data.threshold) {
                document.getElementById('firewallThreshold').value = res.data.threshold;
                document.getElementById('firewallEnabled').checked = !!res.data.enabled;
            }
        }).catch(err => alert('更新失败：' + err.message));
    },
    updateVerification() {
        const enabled = document.getElementById('verificationEnabled').checked;
        fetch(this.servePath + '/admin/security/verification', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: 'enabled=' + enabled
        }).then(res => res.json()).then(res => {
            alert(res.msg || '已更新');
            if (res.data) {
                document.getElementById('verificationEnabled').checked = !!res.data.enabled;
            }
        }).catch(err => alert('更新失败：' + err.message));
    },
    updateFirstVisit() {
        const enabled = document.getElementById('firstVisitCaptchaEnabled').checked;
        fetch(this.servePath + '/admin/security/verification-first', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: 'enabled=' + enabled
        }).then(res => res.json()).then(res => {
            alert(res.msg || '已更新');
            if (res.data) {
                document.getElementById('firstVisitCaptchaEnabled').checked = !!res.data.enabled;
            }
        }).catch(err => alert('更新失败：' + err.message));
    }
};
</script>
</@admin>
