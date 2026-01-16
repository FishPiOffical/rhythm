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
<div class="content">
    <div class="module">
        <div class="module-header">
            <h2>${unmodifiableLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin fn__flex">
            <label>
                <div>Id</div>
                <input onfocus="this.select()" type="text" id="oId" value="${milestone.oId}" readonly="readonly"/>
            </label>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            <h2>${modifiableLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/milestone/${milestone.oId}" method="POST">
                <div class="fn__flex">
                    <label>
                        <div>大事记</div>
                        <input type="text" id="milestoneTitle" name="milestoneTitle" value="${milestone.milestoneTitle}"/>
                    </label>

                </div>
                <div class="fn__flex">
                    <label >
                        <div>开始日期</div>
                        <input type="date"  id="milestoneDate" name="milestoneDate" value="${milestone.milestoneDate?number_to_datetime?string('yyyy-MM-dd')}">
                    </label>
                    <label class="mid">
                        <div>结束日期（可选）</div>
                        <input type="date"  id="milestoneEndDate" name="milestoneEndDate" value="${(milestone.milestoneEndDate?number_to_datetime?string('yyyy-MM-dd'))!''}">
                    </label>
                    <label></label>
                </div>

                <div class="fn__flex">
                    <label >
                        <div>内容</div>
                        <textarea class="form-control" id="milestoneContent" name="milestoneContent">${milestone.milestoneContent!''}</textarea>
                    </label>

                </div>

                <div class="fn__flex">
                    <label >
                        <div>相关链接</div>
                        <input type="text" class="form-control" id="milestoneLink" name="milestoneLink" value="${milestone.milestoneLink!''}">
                    </label>
                </div>

                <div class="fn__flex">
                    <label >
                        <div>媒体标题</div>
                        <input type="text" class="form-control" id="milestoneMediaCaption" name="milestoneMediaCaption" value="${milestone.milestoneMediaCaption!''}">
                    </label>

                </div>

                <div class="fn__flex">
                    <label class="form-label">
                        <div>媒体链接</div>
                        <input type="text" class="form-control" id="milestoneMediaUrl" name="milestoneMediaUrl" value="${milestone.milestoneMediaUrl!''}">
                    </label>
                </div>


                <div class="fn__flex">
                    <label >
                        <div>媒体类型</div>
                        <select class="form-control" id="milestoneMediaType" name="milestoneMediaType">
                            <option value="image"
                                    <#if milestone.milestoneMediaType == "image">selected</#if>>
                                图片
                            </option>
                            <option value="video"
                                    <#if milestone.milestoneMediaType == "video">selected</#if>>
                                视频
                            </option>
                        </select>
                    </label>
                    <label class="mid">
                        <div>作者Id</div>
                        <input type="text" class="form-control" id="milestoneAuthorId" name="milestoneAuthorId" value="${milestone.milestoneAuthorId!''}" >
                    </label>
                    <label >
                        <div>状态</div>
                        <select class="form-control" id="milestoneStatus" name="milestoneStatus">
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
                    </label>
                </div>


                <button type="submit" class="green fn-right">${submitLabel}</button>
            </form>
        </div>
    </div>
    <div class="module">
        <div class="module-header">
            <h2 class="ft-red">${removeDataLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/remove-milestone" method="POST" class="fn__flex"
                  onsubmit="return window.confirm('${confirmRemoveLabel}')">
                <label>
                    <div>Id</div>
                    <input type="text" id="milestoneId" name="milestoneId" value="${milestone.oId}" readonly class="input--admin-readonly"/>
                </label>
                <div>
                    &nbsp; &nbsp;
                    <button type="submit" class="red fn-right btn--admin">${submitLabel}</button>
                </div>
            </form>
        </div>
    </div>

</div>
</@admin>
