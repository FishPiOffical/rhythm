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
<#include "../macro-pagination.ftl">
<@admin "milestones">
<div class="admin">
    <div class="list">
        <div class="wrapper">
            <div class="module">
                <div class="module-header">
                    <h2>筛选</h2>
                </div>
                <div class="module-panel form fn-clear" style="margin-top: 10px">
                    <a href="${servePath}/admin/milestones"
                       <#if status == 0>class="ft-gray"</#if>>
                        全部
                    </a> /
                    <a href="${servePath}/admin/milestones?status=1"
                       <#if status == 1>class="ft-gray"</#if>>
                        待审核
                    </a> /
                    <a href="${servePath}/admin/milestones?status=2"
                       <#if status == 2>class="ft-gray"</#if>>
                        正常
                    </a> /
                    <a href="${servePath}/admin/milestones?status=3"
                       <#if status == 3>class="ft-gray"</#if>>
                        已拒绝
                    </a>
                </div>
            </div>
        </div>
        <ul style="margin:0px 20px ;">
            <#list milestones as milestone>
            <li >
                <div class="fn-flex" >
                    <#if milestone.milestoneMediaType == 'image'>
                        <div class="avatar tooltipped tooltipped-s"
                             aria-label="${milestone.milestoneTitle}"
                             style="background-image:url('${milestone.milestoneMediaUrl}')">
                        </div>
                    </#if>

                    <div class="fn-flex-1">
                        <h2>
                            <a href="${milestone.milestoneLink}" target="_blank">
                                ${milestone.milestoneTitle}
                            </a>
                            <span class="ft-smaller">
                                <#if milestone.milestoneStatus == 2>
                                    <span class="ft-green">${validLabel}</span>
                                <#elseif milestone.milestoneStatus == 1>
                                    <span class="ft-gray">待审核</span>
                                <#else>
                                    <span class="ft-red">已拒绝</span>
                                </#if>
                            </span>
                        </h2>
                        <span class="ft-fade ft-smaller">
                            ${milestone.milestoneContent} •
                            ${milestone.milestoneDate}
                        </span>
                    </div>
                    <div class="fn-right">
                        <#if milestone.milestoneStatus == 1>
                            <button class="green" onclick="approveMilestone('${milestone.oId}')">通过</button>
                            <button onclick="rejectMilestone('${milestone.oId}')">拒绝</button>
                        </#if>
                        <a href="${servePath}/admin/milestone/${milestone.oId}" class="ft-a-title">${editLabel}</a>
                    </div>
                </div>
            </li>
            </#list>
        </ul>
        <@pagination url="${servePath}/admin/milestones"/>
    </div>
</div>

<script>
    function approveMilestone(milestoneId) {
        if (!confirm('确认通过审核？')) {
            return;
        }

        const params = new URLSearchParams();
        params.append('oId', milestoneId);
        params.append('status', 2);

        fetch('${servePath}/admin/milestone/approveOrReject', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: params.toString()
        })
            .then(response => response.json())
            .then(data => {
                if (data.code === 0) {
                    alert('审核通过');
                    window.location.reload();
                } else {
                    alert(data.msg || '操作失败');
                }
            })
            .catch(error => {
                alert('操作失败');
                console.error('Error:', error);
            });
    }

    function rejectMilestone(milestoneId) {
        if (!confirm('确认拒绝审核？')) {
            return;
        }

        const params = new URLSearchParams();
        params.append('oId', milestoneId);
        params.append('status', 3);

        fetch('${servePath}/admin/milestone/approveOrReject', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: params.toString()
        })
            .then(response => response.json())
            .then(data => {
                if (data.code === 0) {
                    alert('已拒绝');
                    window.location.reload();
                } else {
                    alert(data.msg || '操作失败');
                }
            })
            .catch(error => {
                alert('操作失败');
                console.error('Error:', error);
            });
    }
</script>

</@admin>
