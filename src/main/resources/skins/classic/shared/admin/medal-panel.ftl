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
<#assign medalResourceVersion = staticResourceVersion + "-medal-panel">
<#macro medalPager top>
    <nav class="medal-catalog__pager<#if top> medal-catalog__pager--top</#if>"
         aria-label="<#if top>顶部翻页<#else>底部翻页</#if>" data-medal-pager>
        <span class="medal-catalog__range"<#if top> aria-live="polite"</#if>></span>
        <form class="medal-catalog__paging">
            <button type="button" class="btn btn-secondary" data-previous disabled>上一页</button>
            <input type="text" class="form-control medal-catalog__page" inputmode="numeric" enterkeyhint="go"
                   aria-label="跳到第几页" value="1" disabled>
            <button type="submit" class="btn btn-secondary" disabled>跳转</button>
            <button type="button" class="btn btn-secondary" data-next disabled>下一页</button>
        </form>
    </nav>
</#macro>
<#macro medalPanel mobile=false>
    <div class="medal-admin<#if mobile> medal-admin--mobile</#if>" id="medalAdminRoot">
        <div class="medal-admin__header">
            <h1 class="medal-admin__title">勋章管理</h1>
            <div class="medal-admin__actions">
                <#if !mobile><button type="button" class="btn btn-secondary" id="btnReorderAllMedals">重排捐赠勋章</button></#if>
                <button type="button" class="btn btn-primary" id="btnCreateMedal">新建勋章</button>
            </div>
        </div>
        <section class="medal-catalog" aria-label="勋章列表">
            <form id="medalSearchForm" class="medal-catalog__search">
                <input type="search" id="medalSearchInput" class="form-control" aria-label="按 ID、名称或描述搜索勋章"
                       placeholder="按 ID、名称或描述搜索">
                <button type="submit" class="btn btn-secondary">搜索</button>
                <button type="reset" class="btn btn-secondary">重置</button>
            </form>
            <@medalPager top=true />
            <div class="medal-catalog__columns" aria-hidden="true"><span>勋章</span><span>类型</span><span>操作</span></div>
            <div class="medal-catalog__listing">
                <ul class="medal-catalog__rows" id="medalRows"></ul>
                <div class="medal-catalog__status" id="medalListStatus" role="status">加载中…</div>
            </div>
            <@medalPager top=false />
        </section>

        <dialog id="medalDetail" class="medal-drawer medal-detail" aria-labelledby="medalDetailTitle"></dialog>

        <dialog id="medalEditModal" class="medal-drawer" aria-labelledby="medalEditModalTitle">
            <header class="medal-drawer__header">
                <h2 id="medalEditModalTitle">新建勋章</h2>
                <button type="button" class="medal-drawer__close" data-close aria-label="关闭">×</button>
            </header>
            <div class="medal-drawer__body">
                <form id="medalEditForm" class="medal-drawer__fields">
                    <input type="hidden" id="medalId" name="medalId">
                    <label>名称 <input id="medalName" name="name" class="form-control" required autofocus></label>
                    <label>类型
                        <select id="medalType" name="type" class="form-control">
                            <option value=""></option>
                            <#list ["普通", "精良", "稀有", "史诗", "传说", "神话", "限定"] as type>
                                <option value="${type}">${type}</option>
                            </#list>
                        </select>
                    </label>
                    <label>描述 <textarea id="medalDescription" name="description" class="form-control" rows="3"></textarea></label>
                    <details class="medal-drawer__help">
                        <summary aria-label="勋章描述变量说明">?</summary>
                        <p>例如：<code>在 {var1} 活动中获得</code>。发放时在数据中填写活动名称。</p>
                    </details>
                    <label>属性 <textarea id="medalAttr" name="attr" class="form-control" rows="5"></textarea></label>
                    <p id="medalEditError" class="medal-drawer__error" role="alert" hidden></p>
                </form>
            </div>
            <footer class="medal-drawer__footer">
                <button type="button" class="btn btn-secondary" data-close>取消</button>
                <button type="submit" class="btn btn-primary" id="btnSaveMedal" form="medalEditForm">保存</button>
            </footer>
        </dialog>

        <dialog id="medalOwnersModal" class="medal-drawer" aria-labelledby="medalOwnersTitle">
            <header class="medal-drawer__header">
                <h2 id="medalOwnersTitle">持有人</h2>
                <button type="button" class="medal-drawer__close" data-close aria-label="关闭">×</button>
            </header>
            <div class="medal-drawer__body">
                <p id="ownersMedalLabel" class="medal-drawer__meta"></p>
                <p id="medalOwnersStatus" class="medal-drawer__meta" role="status" hidden></p>
                <ul id="medalOwnersList" class="medal-owners"></ul>
            </div>
            <footer class="medal-drawer__footer medal-owners__paging" aria-label="持有人翻页">
                <span id="ownersPageInfo" aria-live="polite"></span>
                <button type="button" class="btn btn-secondary" id="ownersPrevPage">上一页</button>
                <button type="button" class="btn btn-secondary" id="ownersNextPage">下一页</button>
            </footer>
        </dialog>

        <dialog id="medalGrantModal" class="medal-drawer" aria-labelledby="medalGrantTitle">
            <header class="medal-drawer__header">
                <h2 id="medalGrantTitle">发放勋章</h2>
                <button type="button" class="medal-drawer__close" data-close aria-label="关闭">×</button>
            </header>
            <div class="medal-drawer__body">
                <p id="grantMedalLabel" class="medal-drawer__meta"></p>
                <form id="medalGrantForm" class="medal-drawer__fields">
                    <label>用户名 <input id="grantUserId" class="form-control" autocomplete="off" required autofocus></label>
                    <label>有效期
                        <select id="grantExpiryMode" class="form-control">
                            <option value="forever">永久</option>
                            <option value="date">指定日期</option>
                            <option value="membership">同步会员到期</option>
                        </select>
                    </label>
                    <label id="grantDateField" hidden>到期日期 <input id="grantDate" class="form-control" type="date"></label>
                    <label>发放数据（选填） <textarea id="grantData" class="form-control" rows="3"></textarea></label>
                </form>
                <p id="medalGrantSummary" hidden></p>
                <p id="medalGrantError" class="medal-drawer__error" role="alert" hidden></p>
            </div>
            <footer class="medal-drawer__footer">
                <button type="button" class="btn btn-secondary" id="medalGrantBack">取消</button>
                <button type="submit" class="btn btn-primary" id="medalGrantReview" form="medalGrantForm">确认发放</button>
                <button type="button" class="btn btn-primary" id="btnGrantMedalToUser" hidden>发放</button>
            </footer>
        </dialog>
    </div>
</#macro>
