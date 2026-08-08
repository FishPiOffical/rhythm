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
<html><head><@head title="职业管理帮助 - ${symphonyLabel}"></@head><link rel="stylesheet" href="${staticServePath}/css/profession-help.css?${staticResourceVersion}&amp;profession-help-v20260804b"></head>
<body><#include "../header.ftl">
<main class="profession-help" id="professionHelp">
    <header class="profession-help__hero">
        <div><span class="profession-help__eyebrow">职业管理</span><h1>使用帮助</h1><p>创建、启用与日常维护</p></div>
        <a class="profession-help__back" href="${servePath}/admin/profession">返回职业库</a>
    </header>
    <nav class="profession-help__nav" aria-label="帮助目录">
        <a href="#create"><span>1</span>创建职业</a><a href="#levels"><span>2</span>设置等级</a><a href="#rules"><span>3</span>设置经验</a><a href="#review"><span>4</span>检查启用</a><a href="#manage"><span>5</span>日常管理</a>
    </nav>
    <section class="profession-help__intro" aria-labelledby="professionHelpStart">
        <div><span class="profession-help__number">开始</span><h2 id="professionHelpStart">先做一个可验证的职业</h2><p>职业资料、等级、经验规则相互独立。完成一项就保存，再继续下一项。</p></div>
        <ol><li><strong>职业资料</strong><span>名称、代号、外观</span></li><li><strong>等级方案</strong><span>进度、展示、奖励</span></li><li><strong>经验规则</strong><span>行为、条件、通知</span></li></ol>
    </section>
    <section class="profession-help__section" id="create" aria-labelledby="professionHelpCreate">
        <header><span class="profession-help__number">1</span><div><h2 id="professionHelpCreate">创建职业</h2><p>在职业库点击“新建职业”。</p></div></header>
        <ol class="profession-help__steps"><li><strong>填写名称、代号和简称</strong><span>代号用于系统识别，创建后不能修改。</span></li><li><strong>补充职业说明和默认外观</strong><span>图标与封面可上传，也可填写图片地址。</span></li><li><strong>保存职业</strong><span>保存后会回到职业库，继续配置等级和经验规则。</span></li></ol>
        <aside class="profession-help__note"><strong>发布前</strong><span>职业名称、简称和展示外观会出现在用户主页与名片。</span></aside>
    </section>
    <section class="profession-help__section" id="levels" aria-labelledby="professionHelpLevels">
        <header><span class="profession-help__number">2</span><div><h2 id="professionHelpLevels">设置等级</h2><p>等级决定经验进度、展示和阶段奖励。</p></div></header>
        <ol class="profession-help__steps"><li><strong>打开职业编辑</strong><span>从职业库选择需要维护的职业。等级方案固定归属当前职业。</span></li><li><strong>逐级填写经验门槛</strong><span>经验门槛按从低到高排列，名称和说明直接显示给用户。</span></li><li><strong>设置等级展示与奖励</strong><span>每个等级可以分别设置主页、名片等位置的样式，并添加积分或勋章奖励。</span></li></ol>
        <aside class="profession-help__note"><strong>方案切换</strong><span>调整已启用方案前，先确认迁移方式和奖励补发范围。</span></aside>
    </section>
    <section class="profession-help__section" id="rules" aria-labelledby="professionHelpRules">
        <header><span class="profession-help__number">3</span><div><h2 id="professionHelpRules">设置经验</h2><p>经验规则把站内行为转成职业经验。</p></div></header>
        <ol class="profession-help__steps"><li><strong>选择发生时机</strong><span>发帖、评论、聊天室发言等行为可触发规则。</span></li><li><strong>补充生效条件与经验计算</strong><span>条件用于筛选行为，计算方式决定每次增加或减少多少经验。</span></li><li><strong>选择执行内容</strong><span>可增加经验、记录贡献，或在升级时发送站内通知。</span></li></ol>
        <aside class="profession-help__note"><strong>测试规则</strong><span>填写测试数据后先查看计算结果，再保存和启用规则。</span></aside>
    </section>
    <section class="profession-help__section" id="review" aria-labelledby="professionHelpReview">
        <header><span class="profession-help__number">4</span><div><h2 id="professionHelpReview">检查并启用</h2><p>先核对效果，再让用户获得经验。</p></div></header>
        <ol class="profession-help__steps"><li><strong>打开经验模拟</strong><span>选择职业，输入当前经验和变化值，查看等级变化、奖励和时间线。</span></li><li><strong>检查展示样式</strong><span>确认每个等级在主页和名片中的名称、图标、颜色与奖励信息。</span></li><li><strong>从职业库启用</strong><span>职业、等级方案和经验规则都启用后，新的记录才会按完整配置结算。</span></li></ol>
    </section>
    <section class="profession-help__section" id="manage" aria-labelledby="professionHelpManage">
        <header><span class="profession-help__number">5</span><div><h2 id="professionHelpManage">日常管理</h2><p>职业库集中查看每个职业的当前状态。</p></div></header>
        <div class="profession-help__cards"><article><h3>编辑与历史</h3><p>编辑会保留历史记录。需要恢复旧配置时，在历史记录中选择对应版本。</p></article><article><h3>复制与停用</h3><p>复制适合基于现有职业建立新方案。停用前先确认用户进度和奖励安排。</p></article><article><h3>导入导出</h3><p>测试环境确认后可导出单个职业或全部职业，再导入到其他环境。</p></article></div>
    </section>
</main>
<#include "../footer.ftl"></body></html>
