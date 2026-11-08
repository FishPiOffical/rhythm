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
    <div class="module security-panel">
        <div class="module-header"><h2>社区安全</h2></div>
        <div class="module-panel form fn-clear form--admin">
            <p class="security-mode">运行模式：<strong>${securityRuntimeMode}</strong><span>${securitySwitchEditable?then('可调整，重启后恢复默认值','生产模式只读')}</span></p>
            <div class="security-controls">
                <label><input type="checkbox" id="firewallEnabled" <#if firewallEnabled?? && firewallEnabled>checked</#if> <#if !securitySwitchEditable>disabled</#if>/> CC 防护</label>
                <label>每分钟请求数 <input type="number" id="firewallThreshold" min="1" value="${firewallThreshold!400}" <#if !securitySwitchEditable>disabled</#if>/></label>
                <label><input type="checkbox" id="verificationEnabled" <#if verificationShieldEnabled?? && verificationShieldEnabled>checked</#if> <#if !securitySwitchEditable>disabled</#if>/> 验证码盾</label>
                <label><input type="checkbox" id="firstVisitCaptchaEnabled" <#if firstVisitCaptchaEnabled?? && firstVisitCaptchaEnabled>checked</#if> <#if !securitySwitchEditable>disabled</#if>/> 首访验证</label>
                <button type="button" class="green" id="securitySaveButton" onclick="SecuritySwitches.save()" <#if !securitySwitchEditable>disabled</#if>>保存</button>
            </div>
            <div class="security-monitor">
                <div class="security-monitor-heading"><h3>访问监控</h3><span>本次启动 · 最多100条</span></div>
                <div class="security-stat-strip"><span>记录 <strong id="securityTotal">${securityStats.total}</strong></span><span>近10分钟 <strong id="securityLast10">${securityStats.last10Minutes}</strong></span><span id="securityRefreshMessage">刚刚更新</span></div>
                <div class="security-actions"><button type="button" onclick="SecuritySwitches.refresh()">刷新</button><button type="button" onclick="SecuritySwitches.check()">执行自检</button><label><input type="checkbox" id="securityAutoRefresh" checked/> 自动刷新（10秒）</label></div>
                <div class="security-events-wrap"><table class="table security-events"><thead><tr><th>时间</th><th>IP</th><th>用户状态</th><th>请求</th><th>处理器</th><th>流程</th><th>结果</th><th>原因</th></tr></thead><tbody id="securityEvents"><#list securityEvents as event><tr><td>${event.time}</td><td>${event.ip}</td><td>${event.userStatus}</td><td>${event.method} ${event.uri}</td><td>${event.handler}</td><td>${event.flow}</td><td>${event.result}</td><td>${event.reason}</td></tr></#list></tbody></table></div>
            </div>
            <div class="security-self-check" id="securitySelfCheck"><strong>自检：${securitySelfCheck.status}</strong><#list securitySelfCheck.checks as check><div>${check.passed?then('通过','需核对')}：${check.name}（${check.detail}）</div></#list><div class="ft-small">${securitySelfCheck.note}</div></div>
        </div>
    </div>

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

const SecuritySwitches = {
    servePath: '${servePath}',
    save() {
        const threshold = document.getElementById('firewallThreshold').value.trim();
        if (!/^\d+$/.test(threshold) || Number(threshold) < 1) { alert('每分钟请求数必须是正整数'); return; }
        const body = new URLSearchParams({firewallEnabled: document.getElementById('firewallEnabled').checked, threshold, verificationEnabled: document.getElementById('verificationEnabled').checked, firstVisitCaptchaEnabled: document.getElementById('firstVisitCaptchaEnabled').checked});
        const button = document.getElementById('securitySaveButton'); button.disabled = true;
        fetch(this.servePath + '/admin/security/save', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded','csrfToken':'${csrfToken}'}, body})
          .then(res=>res.json()).then(result=>{alert(result.msg || '已保存'); if(result.data)this.apply(result.data);})
          .catch(err=>alert('保存失败：'+err.message)).finally(()=>{button.disabled=${securitySwitchEditable?then('false','true')};});
    },
    apply(data) { if(data.firewallEnabled!==undefined)document.getElementById('firewallEnabled').checked=!!data.firewallEnabled; if(data.threshold!==undefined)document.getElementById('firewallThreshold').value=data.threshold; if(data.verificationEnabled!==undefined)document.getElementById('verificationEnabled').checked=!!data.verificationEnabled; if(data.firstVisitCaptchaEnabled!==undefined)document.getElementById('firstVisitCaptchaEnabled').checked=!!data.firstVisitCaptchaEnabled; },
    renderEvents(events) { const tbody=document.getElementById('securityEvents'); tbody.textContent=''; (events||[]).forEach(e=>{ const tr=document.createElement('tr'); ['time','ip','userStatus','request','handler','flow','result','reason'].forEach(key=>{ const td=document.createElement('td'); td.textContent=key==='request'?((e.method||'-')+' '+(e.uri||'-')):(e[key]||'-'); tr.appendChild(td); }); tbody.appendChild(tr); }); if(!tbody.children.length){ const tr=document.createElement('tr'); const td=document.createElement('td'); td.colSpan=8; td.textContent='暂无记录'; tr.appendChild(td); tbody.appendChild(tr); } },
    refresh(silent) { fetch(this.servePath+'/admin/security/monitor').then(res=>res.json()).then(result=>{const d=result.data||{};document.getElementById('securityTotal').textContent=d.total||0;document.getElementById('securityLast10').textContent=d.last10Minutes||0;this.renderEvents(d.events);document.getElementById('securityRefreshMessage').textContent=(silent?'自动更新：':'')+new Date().toLocaleTimeString();}).catch(err=>{if(!silent)document.getElementById('securityRefreshMessage').textContent='刷新失败：'+err.message;}); },
    startAutoRefresh() { this.timer=setInterval(()=>{ const toggle=document.getElementById('securityAutoRefresh'); if(toggle.checked && !document.hidden)this.refresh(true); },10000); },
    check() { fetch(this.servePath+'/admin/security/self-check').then(res=>res.json()).then(result=>{const d=result.data||{};const box=document.getElementById('securitySelfCheck');box.textContent='自检：'+(d.status||'未知')+'；'+(d.note||'');(d.checks||[]).forEach(c=>{const line=document.createElement('div');line.textContent=(c.passed?'通过':'需核对')+'：'+c.name+'（'+c.detail+'）';box.appendChild(line);});}).catch(err=>alert('自检失败：'+err.message)); }
};
SecuritySwitches.startAutoRefresh();
</script>
</@admin>
