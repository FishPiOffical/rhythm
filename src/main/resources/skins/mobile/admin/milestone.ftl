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
<@admin "milestones">
<div class="wrapper">
    <div class="module">
        <div class="module-header">
            <h2>${unmodifiableLabel}</h2>
        </div>
        <div class="module-panel form fn-clear">
            <label for="oId">Id</label>
            <input type="text" id="oId" name="oId" value="${milestone.oId}" readonly="readonly" />
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>${modifiableLabel}</h2>
        </div>
        <div class="module-panel form fn-clear">
            <form action="${servePath}/admin/milestone/${milestone.oId}" method="POST">
                <label for="milestoneTitle">大事记</label>
                <input type="text" id="milestoneTitle" name="milestoneTitle" value="${milestone.milestoneTitle}"/>

                <label for="milestoneDate">开始日期</label>
                <input type="date" id="milestoneDate" name="milestoneDate" value="${milestone.milestoneDate?number_to_datetime?string('yyyy-MM-dd')}">

                <label for="milestoneEndDate">结束日期（可选）</label>
                <input type="date" id="milestoneEndDate" name="milestoneEndDate" value="${(milestone.milestoneEndDate?number_to_datetime?string('yyyy-MM-dd'))!''}">

                <label for="milestoneContent">内容</label>
                <textarea id="milestoneContent" name="milestoneContent" rows="5">${milestone.milestoneContent!''}</textarea>

                <label for="milestoneLink">相关链接</label>
                <input type="text" id="milestoneLink" name="milestoneLink" value="${milestone.milestoneLink!''}">

                <label for="milestoneMediaCaption">媒体标题</label>
                <input type="text" id="milestoneMediaCaption" name="milestoneMediaCaption" value="${milestone.milestoneMediaCaption!''}">

                <label for="milestoneMediaUrl">媒体链接</label>
                <input type="text" id="milestoneMediaUrl" name="milestoneMediaUrl" value="${milestone.milestoneMediaUrl!''}">

                <label for="milestoneMediaType">媒体类型</label>
                <select id="milestoneMediaType" name="milestoneMediaType">
                    <option value="image"
                            <#if milestone.milestoneMediaType == "image">selected</#if>>
                        图片
                    </option>
                    <option value="video"
                            <#if milestone.milestoneMediaType == "video">selected</#if>>
                        视频
                    </option>
                </select>

                <label for="milestoneAuthorId">作者Id</label>
                <input type="text" id="milestoneAuthorId" name="milestoneAuthorId" value="${milestone.milestoneAuthorId!''}">

                <label for="milestoneStatus">状态</label>
                <select id="milestoneStatus" name="milestoneStatus">
                    <option value="1"
                            <#if milestone.milestoneStatus == 1>selected</#if>>
                        待审核
                    </option>
                    <option value="2"
                            <#if milestone.milestoneStatus == 2>selected</#if>>
                        正常
                    </option>
                    <option value="3"
                            <#if milestone.milestoneStatus == 3>selected</#if>>
                        已拒绝
                    </option>
                </select>

                <br/><br/>
                <button type="submit" class="green fn-right">${submitLabel}</button>
            </form>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2 class="ft-red">${removeDataLabel}</h2>
        </div>
        <div class="module-panel form fn-clear">
            <form action="${servePath}/admin/remove-milestone" method="POST" onsubmit="return window.confirm('${confirmRemoveLabel}')">
                <label for="milestoneId">Id</label>
                <input type="text" id="milestoneId" name="milestoneId" value="${milestone.oId}" readonly="readonly"/>

                <br/><br/>
                <button type="submit" class="red fn-right">${submitLabel}</button>
            </form>
        </div>
    </div>
</div>
</@admin>
