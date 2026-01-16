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
    <div class="content admin">
        <div class="module list">
            <div class="module-header">
                <div class="module-header-actions">
                    <div class="module-header-actions" >
                        <a href="${servePath}/admin/milestones"
                           class="<#if status == 0>ft-gray</#if>">
                            全部
                        </a>
                        /
                        <a href="${servePath}/admin/milestones?status=1"
                           class="<#if status == 1>ft-gray</#if>">
                            待审核
                        </a>
                        /
                        <a href="${servePath}/admin/milestones?status=2"
                           class="<#if status == 2>ft-gray</#if>">
                            正常
                        </a>
                        /
                        <a href="${servePath}/admin/milestones?status=3"
                           class="<#if status == 3>ft-gray</#if>">
                            已拒绝
                        </a>
                    </div>

                </div>
            </div>
            <ul>
                <#list milestones as milestone>
                    <li>
                        <div class="fn-flex">
                            <#if milestone.milestoneMediaType == 'image'>
                                <div class="avatar tooltipped tooltipped-s"
                                     aria-label="${milestone.milestoneTitle}"
                                     style="background-image:url('${milestone.milestoneMediaUrl}')">
                                </div>

                            </#if>

                            <div class="fn-flex" style="flex-direction: column">
                                <h2>
                                    <a href="${milestone.milestoneLink}" target="_blank">
                                        ${milestone.milestoneTitle}
                                    </a>
                                    <span class="ft-smaller">
                                    <#-- 状态 -->
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
                                    ${milestone.milestoneContent}
                                     •
                                     ${milestone.milestoneDate}
                                </span>
                            </div>
                            <div class="fn-flex-1" style="margin-left: 10px">
                                <#if milestone.milestoneStatus == 1>
                                    <button class="green" onclick="approveMilestone('${milestone.oId}')">通过</button>
                                    <button  onclick="rejectMilestone('${milestone.oId}')">拒绝</button>
                                </#if>
                            </div>

                            <a href="${servePath}/admin/milestone/${milestone.oId}"
                               class="fn-right tooltipped tooltipped-w ft-a-title"
                               aria-label="${editLabel}">
                                <svg><use xlink:href="#edit"></use></svg>
                            </a>
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