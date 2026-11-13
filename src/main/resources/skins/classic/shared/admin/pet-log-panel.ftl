<#assign risk = petLogTab == "risk">
<#assign logsPath = servePath + "/admin/pet-logs">
<header class="pet-log-heading">
    <h1>宠物日志</h1>
    <nav class="pet-log-tabs" aria-label="日志类别">
        <a href="${logsPath}?tab=activity"<#if !risk> aria-current="page"</#if>>活动日志</a>
        <a href="${logsPath}?tab=risk"<#if risk> aria-current="page"</#if>>风控日志</a>
    </nav>
</header>

<form class="pet-log-filters" method="get" action="${logsPath}">
    <input type="hidden" name="tab" value="${petLogTab?html}">
    <div class="pet-log-fields">
        <label class="pet-log-account">用户
            <input type="text" name="user" maxlength="64" value="${petLogUser?html}" placeholder="用户名或 UID">
        </label>
        <label>开始日期
            <input type="date" name="from" value="${petLogFrom?html}" required>
        </label>
        <label>结束日期
            <input type="date" name="to" value="${petLogTo?html}" required>
        </label>
        <label class="pet-log-type">操作类型
            <select name="operation">
                <option value="">全部操作</option>
                <#list petLogOperations?keys?sort as key>
                    <option value="${key?html}"<#if petLogOperation == key> selected</#if>>${petLogOperations[key]?html}</option>
                </#list>
            </select>
        </label>
    </div>
    <div class="pet-log-filter-footer">
        <p>北京时间，最多 31 天。纯数字用户名请加 @。</p>
        <div class="pet-log-actions">
            <a class="pet-log-button" href="${logsPath}?tab=${petLogTab?html}">重置</a>
            <button class="pet-log-button pet-log-primary" type="submit">筛选</button>
        </div>
    </div>
</form>

<#if petLogError??>
    <div class="pet-log-message" role="alert">${petLogError?html}</div>
<#else>
    <section class="pet-log-results" aria-label="查询结果">
        <#if petLogRows?has_content>
            <table aria-label="<#if risk>风控日志<#else>活动日志</#if>">
                <thead><tr>
                    <th scope="col">操作</th>
                    <th scope="col">用户</th>
                    <th scope="col">时间</th>
                    <#if !risk><th scope="col">状态</th></#if>
                </tr></thead>
                <tbody>
                    <#list petLogRows as row>
                        <tr>
                            <td>
                                <strong>${row.operation?html}</strong>
                                <#if row.detail?has_content><span class="pet-log-detail">${row.detail?html}</span></#if>
                            </td>
                            <td>
                                <span class="pet-log-user"><span class="pet-log-unit">UID</span> ${row.userId?html}</span>
                                <#if row.petId?has_content><span class="pet-log-detail">宠物 ${row.petId?html}</span></#if>
                            </td>
                            <td class="pet-log-time">${row.time?html}</td>
                            <#if !risk>
                                <td class="pet-log-state">
                                    <#if row.status?has_content>
                                        <span class="pet-log-status">${row.status?keep_before(" · ")?html}</span>
                                        <#if row.status?contains(" · ")>
                                            <span class="pet-log-detail">${row.status?keep_after(" · ")?html}</span>
                                        </#if>
                                    <#else>
                                        <span class="pet-log-detail pet-log-no-state">—</span>
                                    </#if>
                                </td>
                            </#if>
                        </tr>
                    </#list>
                </tbody>
            </table>
        <#else>
            <p class="pet-log-message">当前筛选条件下没有日志。</p>
        </#if>
        <#if petLogPage gt 1 || petLogHasNext>
            <#assign filterQuery = "tab=" + petLogTab?url("UTF-8") + "&user=" + petLogUser?url("UTF-8")
                + "&from=" + petLogFrom?url("UTF-8") + "&to=" + petLogTo?url("UTF-8")
                + "&operation=" + petLogOperation?url("UTF-8")>
            <nav class="pet-log-pagination" aria-label="宠物日志分页">
                <span>第 ${petLogPage?c} 页</span>
                <#if petLogPage gt 1>
                    <a class="pet-log-button" href="${logsPath}?${filterQuery?html}&amp;p=${(petLogPage - 1)?c}">上一页</a>
                </#if>
                <#if petLogHasNext>
                    <a class="pet-log-button" href="${logsPath}?${filterQuery?html}&amp;p=${(petLogPage + 1)?c}">下一页</a>
                </#if>
            </nav>
        </#if>
    </section>
</#if>
