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
                <div class="security-monitor-heading"><h3 id="securityEventsTitle">最近访问</h3><span>本次启动 · 最多100条</span></div>
                <div class="security-stat-strip"><span>记录 <strong class="security-stat-value" id="securityTotal">${securityStats.total}</strong></span><span>近10分钟 <strong class="security-stat-value" id="securityLast10">${securityStats.last10Minutes}</strong></span><span id="securityRefreshMessage" role="status" aria-live="polite">刚刚更新</span></div>
                <div class="security-actions"><button type="button" onclick="SecuritySwitches.refresh()">刷新</button><button type="button" onclick="SecuritySwitches.showLatest()">最新记录</button><button type="button" onclick="SecuritySwitches.check()">执行自检</button><label><input type="checkbox" id="securityAutoRefresh" checked/> 自动刷新（1秒）</label></div>
                <div class="security-events-wrap" id="securityEventsViewport" role="region" aria-labelledby="securityEventsTitle" tabindex="0"><table class="table security-events"><thead><tr><th>时间</th><th>IP</th><th>用户状态</th><th>请求</th><th>处理器</th><th>流程</th><th>结果</th><th>原因</th></tr></thead><tbody id="securityEvents"><#list securityEvents as event><tr><td>${event.time}</td><td>${event.ip}</td><td>${event.userStatus}</td><td>${event.method} ${event.uri}</td><td>${event.handler}</td><td>${event.flow}</td><td>${event.result}</td><td>${event.reason}</td></tr></#list></tbody></table></div>
            </div>
            <#if currentUser.userName == "adlered">
            <div class="security-monitor security-crawlers">
                <div class="security-monitor-heading">
                    <h3 id="securityCrawlerTitle">爬虫访问</h3><span>本次启动 · 重启清空</span>
                </div>
                <p class="security-observation-note">仅统计已识别的搜索爬虫。校验未通过不代表一定伪造。</p>
                <div class="security-events-wrap" role="region" aria-labelledby="securityCrawlerTitle" tabindex="0">
                    <table class="table security-events security-crawler-summary">
                        <thead><tr><th>爬虫</th><th>身份校验</th><th>请求次数</th><th>最近访问</th><th>未存明细</th><th>明细</th></tr></thead>
                        <tbody id="securityCrawlerGroups">
                            <#list crawlerStats.groups as item>
                            <tr>
                                <td>${item.name?html}</td>
                                <td>${item.verified?then('已验证','未通过')}</td>
                                <td>${item.count?c}</td>
                                <td>${item.lastSeen?html}</td>
                                <td>${item.omitted?c}</td>
                                <td><button type="button" data-crawler="${item.key?html}">查看</button></td>
                            </tr>
                            <#else><tr><td colspan="6">暂无爬虫访问</td></tr>
                            </#list>
                        </tbody>
                    </table>
                </div>
                <details id="securityCrawlerDetails">
                    <summary id="securityCrawlerSelection">访问明细</summary>
                    <h4 id="securityCrawlerSourcesTitle">来源与校验</h4>
                    <p id="securityCrawlerSourcesNote" class="security-observation-note">每个 IP 显示最近校验。</p>
                    <div class="security-events-wrap" id="securityCrawlerSourcesViewport" role="region"
                         aria-labelledby="securityCrawlerSourcesTitle" tabindex="0">
                        <table class="table security-events security-crawler-sources">
                            <thead><tr><th>来源 IP</th><th>请求次数</th><th>最近访问</th><th>校验结果</th><th>反向域名</th><th>正向解析</th><th>校验时间</th><th>最近请求</th></tr></thead>
                            <tbody id="securityCrawlerSources"><tr><td colspan="8">请选择爬虫</td></tr></tbody>
                        </table>
                    </div>
                    <div class="security-actions">
                        <button type="button" onclick="SecuritySwitches.latestCrawlerPaths()">最新明细</button>
                        <span>每组最多1000条路径；不含查询参数。</span>
                    </div>
                    <div class="security-events-wrap" id="securityCrawlerPathsViewport" role="region"
                         aria-labelledby="securityCrawlerSelection" tabindex="0">
                        <table class="table security-events security-crawler-paths">
                            <thead><tr><th>路径</th><th>请求次数</th><th>最近访问</th></tr></thead>
                            <tbody id="securityCrawlerPaths"><tr><td colspan="3">请选择爬虫</td></tr></tbody>
                        </table>
                    </div>
                </details>
            </div>
            <div class="security-monitor security-observations">
                <div class="security-monitor-heading"><h3 id="securityObservationTitle">链路观测</h3><span>本次启动 · 最近2小时 · 最多200条</span></div>
                <p class="security-observation-note">按来源 IP 关联；页面渲染不代表浏览器已显示，集合核对不代表网络拦截。</p>
                <div class="security-events-wrap" id="securityObservationViewport" role="region" aria-labelledby="securityObservationTitle" tabindex="0">
                    <table class="table security-events"><thead><tr><th>时间</th><th>IP</th><th>环节</th><th>状态</th><th>说明</th><th>异常记录</th></tr></thead><tbody id="securityObservations"><#list securityStats.observations as item><tr><td>${item.time?html}</td><td>${item.ip?html}</td><td>${item.kind?html}</td><td>${item.state?html}</td><td>${item.detail?html}</td><td>${item.failure?has_content?then(item.failure, '—')?html}</td></tr><#else><tr><td colspan="6">暂无观测</td></tr></#list></tbody></table>
                </div>
                <p class="security-observation-note" id="securityObservationRetention">已过期或淘汰 ${securityStats.observationDiscarded} 条；重启清空。未有后续访问时保持待确认。</p>
            </div>
            </#if>
            <div class="security-self-check" id="securitySelfCheck"><strong>结构自检：${securitySelfCheck.status}</strong><#list securitySelfCheck.checks as check><div>${check.passed?then('通过','需核对')}：${check.name}（${check.detail}）</div></#list><div class="ft-small">${securitySelfCheck.note}</div></div>
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
    renderEvents(events) {
        const tbody = document.getElementById('securityEvents');
        const rows = document.createDocumentFragment();
        events.forEach(event => {
            const tr = document.createElement('tr');
            ['time', 'ip', 'userStatus', 'request', 'handler', 'flow', 'result', 'reason'].forEach(key => {
                const td = document.createElement('td');
                td.textContent = key === 'request' ? ((event.method || '-') + ' ' + (event.uri || '-')) : (event[key] || '-');
                tr.appendChild(td);
            });
            rows.appendChild(tr);
        });
        if (!events.length) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 8;
            td.textContent = '暂无记录';
            tr.appendChild(td);
            rows.appendChild(tr);
        }
        tbody.replaceChildren(rows);
    },
    renderObservations(data) {
        const viewport = document.getElementById('securityObservationViewport');
        if (!viewport) return;
        // 独立观察表同样保留阅读现场，回到左上角后下轮刷新展示新结果。
        if (viewport.scrollTop > 0 || viewport.scrollLeft > 0) return;
        const rows = document.createDocumentFragment();
        (data.observations || []).forEach(item => {
            const tr = document.createElement('tr');
            ['time', 'ip', 'kind', 'state', 'detail', 'failure'].forEach(key => {
                const td = document.createElement('td');
                td.textContent = item[key] || '—';
                tr.appendChild(td);
            });
            rows.appendChild(tr);
        });
        if (!rows.childNodes.length) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 6;
            td.textContent = '暂无观测';
            tr.appendChild(td);
            rows.appendChild(tr);
        }
        document.getElementById('securityObservations').replaceChildren(rows);
        document.getElementById('securityObservationRetention').textContent =
            '已过期或淘汰 ' + (data.observationDiscarded || 0) + ' 条；重启清空。未有后续访问时保持待确认。';
    },
    crawlerGroup: '',
    renderCrawlers(data) {
        const body = document.getElementById('securityCrawlerGroups');
        if (!body || !data) return;
        const activeKey = document.activeElement && document.activeElement.dataset.crawler;
        const rows = document.createDocumentFragment();
        data.groups.forEach(item => {
            const tr = document.createElement('tr');
            [item.name, item.verified ? '已验证' : '未通过', item.count, item.lastSeen, item.omitted].forEach(value => {
                const td = document.createElement('td');
                td.textContent = value;
                tr.appendChild(td);
            });
            const td = document.createElement('td');
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = '查看';
            button.dataset.crawler = item.key;
            button.setAttribute('aria-label', item.name + (item.verified ? '已验证' : '未通过') + '访问明细');
            button.addEventListener('click', () => this.selectCrawler(item));
            td.appendChild(button);
            tr.appendChild(td);
            rows.appendChild(tr);
        });
        if (!data.groups.length) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 6;
            td.textContent = '暂无爬虫访问';
            tr.appendChild(td);
            rows.appendChild(tr);
        }
        body.replaceChildren(rows);
        if (activeKey) {
            const button = Array.from(body.querySelectorAll('button')).find(item => item.dataset.crawler === activeKey);
            if (button) button.focus({preventScroll: true});
        }
        // 分组切换时忽略旧请求；来源与路径分别保持阅读位置。
        if (data.selectedGroup !== this.crawlerGroup) return;
        const sourceViewport = document.getElementById('securityCrawlerSourcesViewport');
        if (sourceViewport.scrollTop === 0 && sourceViewport.scrollLeft === 0) {
            const sources = document.createDocumentFragment();
            data.sources.forEach(item => {
                const tr = document.createElement('tr');
                [item.ip, item.count, item.lastSeen, item.reason, item.ptr || '—', item.forward || '—',
                    item.checkedAt, item.cached ? '使用缓存' : '实时校验'].forEach(value => {
                    const td = document.createElement('td');
                    td.textContent = value;
                    tr.appendChild(td);
                });
                sources.appendChild(tr);
            });
            if (!data.sources.length) {
                const tr = document.createElement('tr');
                const td = document.createElement('td');
                td.colSpan = 8;
                td.textContent = this.crawlerGroup ? '暂无来源明细' : '请选择爬虫';
                tr.appendChild(td);
                sources.appendChild(tr);
            }
            document.getElementById('securityCrawlerSources').replaceChildren(sources);
            document.getElementById('securityCrawlerSourcesNote').textContent =
                '每个 IP 显示最近校验；未存来源 ' + data.sourceOmitted + ' 次。';
        }
        const viewport = document.getElementById('securityCrawlerPathsViewport');
        if (viewport.scrollTop > 0 || viewport.scrollLeft > 0) return;
        const paths = document.createDocumentFragment();
        data.paths.forEach(item => {
            const tr = document.createElement('tr');
            [item.path, item.count, item.lastSeen].forEach(value => {
                const td = document.createElement('td');
                td.textContent = value;
                tr.appendChild(td);
            });
            paths.appendChild(tr);
        });
        if (!data.paths.length) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 3;
            td.textContent = this.crawlerGroup ? '暂无路径明细' : '请选择爬虫';
            tr.appendChild(td);
            paths.appendChild(tr);
        }
        document.getElementById('securityCrawlerPaths').replaceChildren(paths);
    },
    selectCrawler(item) {
        this.crawlerGroup = item.key;
        document.getElementById('securityCrawlerDetails').open = true;
        document.getElementById('securityCrawlerSelection').textContent = item.name + ' · ' + (item.verified ? '已验证' : '未通过');
        document.getElementById('securityCrawlerPaths').textContent = '';
        document.getElementById('securityCrawlerSources').textContent = '';
        return this.latestCrawlerPaths();
    },
    latestCrawlerPaths() {
        ['securityCrawlerPathsViewport', 'securityCrawlerSourcesViewport'].forEach(id => {
            const viewport = document.getElementById(id);
            viewport.scrollTop = 0;
            viewport.scrollLeft = 0;
        });
        return this.refresh();
    },
    setRefreshMessage(text, failed = false) {
        const message = document.getElementById('securityRefreshMessage');
        message.textContent = text;
        message.dataset.state = failed ? 'error' : 'ready';
    },
    refresh(silent = false) {
        if (this.refreshing) return;
        this.refreshing = true;
        const requestedCrawler = this.crawlerGroup;
        return fetch(this.servePath + '/admin/security/monitor' + (this.crawlerGroup ? '?crawler=' + encodeURIComponent(this.crawlerGroup) : ''))
            .then(response => {
                if (!response.ok) throw new Error('HTTP ' + response.status);
                return response.json();
            })
            .then(result => {
                const data = result.data;
                if (result.code !== 0 || !data || !Array.isArray(data.events)) {
                    throw new Error(result.msg || '监控数据无效');
                }
                this.renderObservations(data);
                this.renderCrawlers(data.crawlers);
                document.getElementById('securityTotal').textContent = data.total;
                document.getElementById('securityLast10').textContent = data.last10Minutes;
                const viewport = document.getElementById('securityEventsViewport');
                // 查看历史或横向列时不重绘列表，避免自动刷新打断阅读。
                if (silent && (viewport.scrollTop > 0 || viewport.scrollLeft > 0)) {
                    this.pendingEvents = data.events;
                    this.setRefreshMessage('阅读中，列表暂缓更新');
                    return;
                }
                this.renderEvents(data.events);
                this.pendingEvents = null;
                this.setRefreshMessage('已更新 ' + new Date().toLocaleTimeString());
            })
            .catch(error => this.setRefreshMessage('刷新失败：' + error.message, true))
            .finally(() => {
                this.refreshing = false;
                if (requestedCrawler !== this.crawlerGroup) return this.refresh();
            });
    },
    showLatest() {
        const viewport = document.getElementById('securityEventsViewport');
        viewport.scrollTop = 0;
        viewport.scrollLeft = 0;
        if (this.pendingEvents) {
            this.renderEvents(this.pendingEvents);
            this.pendingEvents = null;
        }
        this.refresh();
    },
    startAutoRefresh() {
        const toggle = document.getElementById('securityAutoRefresh');
        if (!toggle) return;
        const crawlerBody = document.getElementById('securityCrawlerGroups');
        if (crawlerBody) crawlerBody.querySelectorAll('button[data-crawler]').forEach(button => {
            button.addEventListener('click', () => this.selectCrawler({
                key: button.dataset.crawler, name: button.closest('tr').cells[0].textContent,
                verified: button.closest('tr').cells[1].textContent === '已验证'
            }));
        });
        const viewport = document.getElementById('securityEventsViewport');
        viewport.addEventListener('scroll', () => {
            if (viewport.scrollTop === 0 && viewport.scrollLeft === 0 && this.pendingEvents) {
                this.renderEvents(this.pendingEvents);
                this.pendingEvents = null;
                if (document.getElementById('securityRefreshMessage').dataset.state !== 'error') {
                    this.setRefreshMessage('已显示最近记录');
                }
            }
        }, {passive: true});
        clearInterval(this.timer);
        this.timer = setInterval(() => {
            if (toggle.checked && !document.hidden) this.refresh(true);
        }, 1000);
    },
    check() { fetch(this.servePath+'/admin/security/self-check').then(res=>res.json()).then(result=>{const d=result.data||{};const box=document.getElementById('securitySelfCheck');box.textContent='结构自检：'+(d.status||'未知')+'；'+(d.note||'');(d.checks||[]).forEach(c=>{const line=document.createElement('div');line.textContent=(c.passed?'通过':'需核对')+'：'+c.name+'（'+c.detail+'）';box.appendChild(line);});}).catch(err=>alert('自检失败：'+err.message)); }
};
SecuritySwitches.startAutoRefresh();
</script>
</@admin>
