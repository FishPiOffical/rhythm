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
<#include "macro-settings.ftl">
<@home "profession">
<div class="module">
    <div class="module-header"><h2>职业</h2></div>
    <div class="module-panel form profession-settings" id="professionSettings">
        <header class="profession-settings__hero">
            <div>
                <span class="profession-settings__eyebrow">职业档案</span>
                <h2>选择展示身份</h2>
                <p>主页与用户名片显示主职业</p>
            </div>
            <span class="profession-settings__status" data-profession-status role="status" aria-live="polite">读取中</span>
        </header>
        <section class="profession-settings__section">
            <div class="profession-settings__section-head"><div><h3>主职业</h3><p>其余职业仍持续累计</p></div></div>
            <div class="profession-settings__choices" data-profession-choices></div>
            <div class="profession-settings__actions">
                <button type="button" class="profession-button" data-profession-reopen-onboarding>重新选择</button>
                <span data-profession-skip-wrap><button type="button" class="profession-button" data-profession-skip>暂不设置</button></span>
            </div>
        </section>
        <section class="profession-settings__section profession-settings__section--detail">
            <div class="profession-settings__section-head"><div><h3>职业进度</h3><p>等级、贡献与近 90 天记录</p></div></div>
            <div class="profession-settings__detail" data-profession-detail>请选择职业</div>
        </section>
        <section class="profession-settings__section">
            <div class="profession-settings__section-head"><div><h3>展示范围</h3><p>控制他人可查看的职业资料</p></div></div>
            <div class="profession-settings__presets" role="radiogroup" aria-label="展示范围">
                <label class="profession-settings__preset"><input type="radio" name="professionPreset" value="ALL_PUBLIC" checked><span><strong>公开全部</strong><small>展示职业与贡献</small></span></label>
                <label class="profession-settings__preset"><input type="radio" name="professionPreset" value="LEVEL_ONLY"><span><strong>仅展示等级</strong><small>隐藏经验与记录</small></span></label>
                <label class="profession-settings__preset"><input type="radio" name="professionPreset" value="PRIMARY_ONLY"><span><strong>仅展示主职业</strong><small>隐藏其他职业</small></span></label>
                <label class="profession-settings__preset"><input type="radio" name="professionPreset" value="SELF_ONLY"><span><strong>仅自己可见</strong><small>仅登录本人查看</small></span></label>
                <label class="profession-settings__preset"><input type="radio" name="professionPreset" value="FULLY_HIDDEN"><span><strong>完全隐藏</strong><small>不显示职业资料</small></span></label>
                <label class="profession-settings__preset"><input type="radio" name="professionPreset" value="CUSTOM"><span><strong>自定义</strong><small>分别设置展示内容</small></span></label>
            </div>
            <div class="profession-settings__custom" data-profession-custom-visibility hidden></div>
        </section>
    </div>
</div>
</@home>
