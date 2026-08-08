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
<html><head><@head title="职业管理 - ${symphonyLabel}"></@head><link rel="stylesheet" href="${staticServePath}/css/profession-admin.css?${staticResourceVersion}&amp;profession-admin-v20260803c"></head>
<body><#include "../header.ftl">
<main class="profession-admin" id="professionAdmin">
    <header class="profession-admin__hero">
        <div><span class="profession-admin__eyebrow">职业库</span><h1>职业管理</h1><p>创建、调整与维护职业</p></div>
        <div class="profession-admin__hero-actions"><span class="profession-admin__status" data-profession-admin-status role="status" aria-live="polite">读取中</span><a class="profession-admin__button profession-admin__button--icon" href="${servePath}/admin/profession/help"><svg aria-hidden="true"><use xlink:href="#book"></use></svg><span>帮助</span></a><button type="button" class="profession-admin__button profession-admin__button--icon" data-profession-template-open><svg aria-hidden="true"><use xlink:href="#articles"></use></svg><span>模板</span></button><button type="button" class="profession-admin__button profession-admin__button--icon" data-profession-simulation-open><svg aria-hidden="true"><use xlink:href="#history"></use></svg><span>模拟</span></button><button type="button" class="profession-admin__button profession-admin__button--icon" data-profession-transfer-open><svg aria-hidden="true"><use xlink:href="#upload"></use></svg><span>导入导出</span></button><button type="button" class="profession-admin__button profession-admin__button--primary profession-admin__button--icon" data-admin-mode="compose"><svg aria-hidden="true"><use xlink:href="#addpost"></use></svg><span>新建职业</span></button></div>
    </header>
    <dialog class="profession-admin__dialog profession-admin__editor-dialog" id="professionEditorDialog" aria-labelledby="professionEditorTitle">
        <header class="profession-admin__dialog-head"><div><span class="profession-admin__eyebrow">职业编辑</span><h2 id="professionEditorTitle" data-profession-workspace-title>新建职业</h2></div><button type="button" class="profession-admin__dialog-close" data-profession-editor-close aria-label="关闭">×</button></header>
        <nav class="profession-admin__wizard" aria-label="职业编辑步骤">
            <button type="button" class="is-active" data-admin-view="definition" aria-selected="true"><span>①</span><strong>职业</strong><small>基本资料</small></button>
            <button type="button" data-admin-view="scheme" aria-selected="false"><span>②</span><strong>等级</strong><small>进度与奖励</small></button>
            <button type="button" data-admin-view="automation" aria-selected="false"><span>③</span><strong>自动化</strong><small>触发与通知</small></button>
        </nav>
        <section class="profession-admin__composer">
        <form class="profession-admin__form" id="professionDefinitionForm" data-admin-panel="definition">
            <header class="profession-admin__form-head"><span data-definition-step>步骤 ①</span><h2 data-definition-title>职业资料</h2><p data-definition-description>先完成必填内容，再设置展示外观。</p></header>
            <div class="profession-admin__definition-layout"><div class="profession-admin__field-grid">
                <label><span>职业名称 <b aria-hidden="true">*</b></span><input name="displayName" placeholder="例如：著述家" required></label>
                <label><span>职业标识 <b aria-hidden="true">*</b></span><input name="professionCode" placeholder="例如：longform_author" autocomplete="off" required></label>
                <label><span>展示简称 <b aria-hidden="true">*</b></span><input name="shortName" placeholder="例如：著述" required></label>
                <label class="profession-admin__field--wide">职业说明<textarea name="description" placeholder="例如：连载长篇与章节作品"></textarea></label>
            </div><aside class="profession-admin__live-preview" data-profession-live-preview aria-live="polite"><span data-profession-live-preview-icon>职</span><div><strong data-profession-live-preview-name>职业名称</strong><small data-profession-live-preview-level>等级展示预览</small></div><i data-profession-live-preview-progress></i></aside></div>
            <section class="profession-admin__theme-editor"><header><div><h3>职业主题</h3><p>主题色会用于所有默认展示位置。</p></div><button type="button" class="profession-admin__button profession-admin__button--icon" data-profession-random-theme><svg aria-hidden="true"><use xlink:href="#refresh"></use></svg><span>随机配色</span></button></header><div class="profession-admin__appearance">
                <label>主色<input name="primaryColor" type="color" value="#334155"></label><label>背景<input name="backgroundColor" type="color" value="#f1f5f9"></label><label>文字<input name="textColor" type="color" value="#334155"></label>
                <label class="profession-admin__field--wide">职业图标<input name="imageUrl" type="url" placeholder="粘贴图片地址，或使用上传按钮" data-profession-asset-preview="icon"><span class="profession-admin__asset"><span class="profession-admin__asset-preview" data-profession-asset-preview-target="icon"></span><button type="button" class="profession-admin__button profession-admin__button--icon" data-profession-asset-upload="imageUrl"><svg aria-hidden="true"><use xlink:href="#upload"></use></svg><span>上传图标</span></button></span></label>
                <label class="profession-admin__field--wide">职业封面<input name="textureUrl" type="url" placeholder="粘贴图片地址，或使用上传按钮" data-profession-asset-preview="cover"><span class="profession-admin__asset"><span class="profession-admin__asset-preview profession-admin__asset-preview--cover" data-profession-asset-preview-target="cover"></span><button type="button" class="profession-admin__button profession-admin__button--icon" data-profession-asset-upload="textureUrl"><svg aria-hidden="true"><use xlink:href="#upload"></use></svg><span>上传封面</span></button></span></label>
            </div></section>
            <footer class="profession-admin__form-actions"><button type="button" class="profession-admin__button" data-profession-editor-close>关闭</button><button class="green profession-admin__button profession-admin__button--primary" data-definition-save>保存草稿</button></footer>
        </form>
        <form class="profession-admin__form" id="professionSchemeForm" data-admin-panel="scheme" hidden>
            <header class="profession-admin__form-head"><span>步骤 ②</span><h2>等级方案</h2><p>每个等级可折叠查看，依次设置经验与奖励。</p></header>
            <div class="profession-admin__owner" data-profession-owner="scheme"><span>当前职业</span><strong data-profession-owner-name>请先保存职业资料</strong><input name="professionId" type="hidden"></div>
            <details class="profession-admin__details"><summary>生效与迁移</summary><div class="profession-admin__field-grid">
                <label>生效方式<select name="migrationPolicy"><option value="MIGRATE_ALL">立即切换并迁移</option><option value="NEW_USERS_ONLY">仅新用户使用</option><option value="KEEP_EXISTING" selected>保留现有用户方案</option><option value="SCHEDULED_SWITCH">定时切换并迁移</option></select></label>
                <label data-scheduled-switch hidden>切换时间<input name="scheduledSwitchAt" type="datetime-local"></label>
                <label>迁移奖励<select name="rewardMigrationPolicy"><option value="NO_GRANT">不补发</option><option value="GRANT_NEW_LEVELS">补发新增等级奖励</option><option value="GRANT_SELECTED">补发指定奖励</option></select></label>
                <label data-selected-rewards hidden>奖励编号<input name="selectedRewardCodes" placeholder="多个编号用逗号分隔"></label>
            </div></details>
            <div class="profession-admin__level-list" data-level-list></div>
            <footer class="profession-admin__form-actions"><button type="button" class="profession-admin__button profession-admin__button--primary profession-admin__button--icon" data-add-level><svg aria-hidden="true"><use xlink:href="#addfile"></use></svg><span>新增等级</span></button><button class="green profession-admin__button profession-admin__button--primary">保存方案</button></footer>
        </form>
        <form class="profession-admin__form" id="professionAutomationForm" data-admin-panel="automation" hidden>
            <header class="profession-admin__form-head"><span>步骤 ③</span><h2>自动化</h2><p>每条规则可使用不同触发时机、条件和执行内容。</p></header>
            <div class="profession-admin__owner" data-profession-owner="automation"><span>当前职业</span><strong data-profession-owner-name>请先保存职业资料</strong><input name="professionId" type="hidden"></div>
            <div class="profession-automation-list" data-automation-list></div>
            <footer class="profession-admin__form-actions"><button type="button" class="profession-admin__button profession-admin__button--primary profession-admin__button--icon" data-add-automation><svg aria-hidden="true"><use xlink:href="#addfile"></use></svg><span>新增规则</span></button><button type="button" class="profession-admin__button profession-admin__button--icon" data-profession-structure-open><svg aria-hidden="true"><use xlink:href="#articles"></use></svg><span>流程图</span></button><button class="green profession-admin__button profession-admin__button--primary">保存规则</button></footer>
        </form>
        </section>
    </dialog>
    <dialog class="profession-admin__dialog profession-admin__template-dialog" id="professionTemplateDialog" aria-labelledby="professionTemplateTitle"><header class="profession-admin__dialog-head"><div><span class="profession-admin__eyebrow">职业模板</span><h2 id="professionTemplateTitle">从常用职业开始</h2></div><button type="button" class="profession-admin__dialog-close" data-profession-dialog-close aria-label="关闭">×</button></header><div class="profession-admin__template-list"><button type="button" data-profession-template="author"><strong>著述家</strong><span>连载长篇与章节作品</span></button><button type="button" data-profession-template="life"><strong>生活家</strong><span>记录知识、兴趣和日常</span></button><button type="button" data-profession-template="social"><strong>交流家</strong><span>连接讨论、聊天和短文</span></button><button type="button" data-profession-template="activity"><strong>参与家</strong><span>围绕活动持续投入</span></button></div></dialog>
    <section class="profession-admin__catalog"><header><div><span class="profession-admin__eyebrow">职业库</span><h2>已创建职业</h2><p data-profession-catalog-count>读取中</p></div><div class="profession-admin__catalog-tools"><label class="profession-admin__search"><span class="fn-none">搜索职业</span><input type="search" placeholder="搜索名称或标识" autocomplete="off" data-profession-catalog-search></label><label><span class="fn-none">职业状态</span><select data-profession-catalog-status><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已启用</option><option value="RETIRED">已停用</option></select></label><label><span class="fn-none">排序方式</span><select data-profession-catalog-sort><option value="recent">最近修改</option><option value="manual">展示顺序</option><option value="name">职业名称</option></select></label><button type="button" class="profession-admin__button" data-profession-order-open>调整顺序</button></div></header><div class="profession-admin__list" data-profession-admin-list></div><p class="profession-admin__empty" data-profession-catalog-empty hidden>没有匹配的职业</p><footer class="profession-admin__pagination"><button type="button" class="profession-admin__button" data-profession-catalog-previous>上一页</button><span data-profession-catalog-page>第 1 页</span><button type="button" class="profession-admin__button" data-profession-catalog-next>下一页</button></footer></section>
    <dialog class="profession-admin__dialog" id="professionHistoryDialog" aria-labelledby="professionHistoryTitle"><header><div><span class="profession-admin__eyebrow">职业资料</span><h2 id="professionHistoryTitle">历史记录</h2></div><button type="button" class="profession-admin__button" data-profession-dialog-close>关闭</button></header><div class="profession-admin__history" data-profession-history></div></dialog>
    <dialog class="profession-admin__dialog" id="professionTransferDialog" aria-labelledby="professionTransferTitle"><header><div><span class="profession-admin__eyebrow">职业配置</span><h2 id="professionTransferTitle">导入导出</h2></div><button type="button" class="profession-admin__button" data-profession-dialog-close>关闭</button></header><section class="profession-admin__transfer"><h3>导出配置</h3><div><button type="button" class="profession-admin__button" data-profession-export-all>全部导出</button><label>职业<select data-profession-export-select></select></label><button type="button" class="profession-admin__button" data-profession-export-one>单个导出</button></div><h3>导入配置</h3><p>检查通过后才能导入，不会覆盖同代号职业。</p><label>配置文件<input type="file" accept="application/json,.json" data-profession-import-file></label><details class="profession-admin__transfer-paste"><summary>粘贴配置</summary><label>配置内容<textarea rows="6" maxlength="16000000" placeholder="粘贴导出的职业配置" data-profession-import-text></textarea></label></details><div class="profession-admin__transfer-actions"><button type="button" class="profession-admin__button" data-profession-import-precheck>检查配置</button><button type="button" class="profession-admin__button profession-admin__button--primary" data-profession-import disabled>开始导入</button></div><output data-profession-transfer-result aria-live="polite"></output><div data-profession-transfer-preview></div></section></dialog>
    <dialog class="profession-admin__dialog" id="professionOrderDialog" aria-labelledby="professionOrderTitle"><header class="profession-admin__dialog-head"><div><span class="profession-admin__eyebrow">展示顺序</span><h2 id="professionOrderTitle">调整职业顺序</h2></div><button type="button" class="profession-admin__dialog-close" data-profession-dialog-close aria-label="关闭">×</button></header><p class="profession-admin__dialog-copy">上下移动后保存，用户端将按此顺序展示。</p><ol class="profession-admin__order-list" data-profession-order-list></ol><footer class="profession-admin__form-actions"><button type="button" class="profession-admin__button" data-profession-dialog-close>取消</button><button type="button" class="profession-admin__button profession-admin__button--primary" data-profession-order-save>保存顺序</button></footer></dialog>
    <dialog class="profession-admin__dialog profession-admin__simulation-dialog" id="professionSimulationDialog" aria-labelledby="professionSimulationTitle"><header class="profession-admin__dialog-head"><div><span class="profession-admin__eyebrow">预览</span><h2 id="professionSimulationTitle">经验模拟</h2></div><button type="button" class="profession-admin__dialog-close" data-profession-dialog-close aria-label="关闭">×</button></header><section class="profession-admin__simulation"><div class="profession-admin__simulation-fields"><label>职业<select data-profession-simulation-profession></select></label><label>当前经验<input type="number" value="0" min="0" step="1" data-profession-simulation-current></label></div><div class="profession-admin__simulation-summary" aria-live="polite"><strong data-profession-simulation-experience>0 EXP</strong><span data-profession-simulation-level>选择职业</span></div><section class="profession-admin__simulation-entry"><label>经验变化<select data-profession-simulation-direction><option value="1">增加经验</option><option value="-1">减少经验</option></select></label><label>经验值<input type="number" min="0" step="1" value="1" data-profession-simulation-delta></label><label>触发器<input type="text" maxlength="64" placeholder="阅读结算" data-profession-simulation-trigger></label><label>时间<input type="datetime-local" data-profession-simulation-time></label><button type="button" class="profession-admin__button profession-admin__button--primary" data-profession-simulation-add>加入时间线</button></section><ol class="profession-admin__simulation-timeline" data-profession-simulation-timeline></ol></section></dialog>
    <dialog class="profession-admin__dialog profession-admin__structure-dialog" id="professionStructureDialog" aria-labelledby="professionStructureTitle"><header class="profession-admin__dialog-head"><div><span class="profession-admin__eyebrow">职业关系</span><h2 id="professionStructureTitle">职业流程图</h2></div><button type="button" class="profession-admin__dialog-close" data-profession-dialog-close aria-label="关闭">×</button></header><p class="profession-admin__dialog-copy">等级、奖励、规则与执行内容</p><div class="profession-structure-map" data-profession-structure-map></div></dialog>
</main>
<#include "../footer.ftl">
<script src="${staticServePath}/js/profession-automation-condition${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803b"></script>
<script src="${staticServePath}/js/profession-automation-calculator${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-automation-editor${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803a"></script>
<script src="${staticServePath}/js/profession-medal-picker${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-level-editor${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803d"></script>
<script src="${staticServePath}/js/profession-admin-assets${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-admin-preview${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-automation-collection${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803b"></script>
<script src="${staticServePath}/js/profession-admin${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803e"></script>
<script src="${staticServePath}/js/profession-automation-tester${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803b"></script>
<script src="${staticServePath}/js/profession-admin-catalog${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-admin-history${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-admin-transfer${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803e"></script>
<script src="${staticServePath}/js/profession-admin-order${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-admin-workflow${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803d"></script>
<script src="${staticServePath}/js/profession-admin-templates${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260802n"></script>
<script src="${staticServePath}/js/profession-admin-simulation${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803a"></script>
<script src="${staticServePath}/js/profession-structure-map${miniPostfix}.js?${staticResourceVersion}&amp;profession-admin-v20260803a"></script>
</body></html>
