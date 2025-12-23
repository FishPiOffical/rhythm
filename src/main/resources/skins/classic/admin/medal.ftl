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
<#include "../macro-pagination.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="勋章管理 - ${symphonyLabel}">
    </@head>
    <link rel="stylesheet" href="${staticServePath}/css/base.css?${staticResourceVersion}"/>
    <link rel="stylesheet" href="${staticServePath}/css/medal.css?${staticResourceVersion}"/>
</head>
<body>
<#include "../header.ftl">
<div class="main" style="padding: 25px 15px 20px 15px">
        <div class="medal-admin" id="medalAdminRoot">
            <div class="medal-admin__header">
                <h1 class="medal-admin__title">勋章管理</h1>
                <div class="medal-admin__actions">
                    <input type="text" id="medalSearchInput" class="form-control form-control--inline"
                           placeholder="按ID/名称/描述搜索"/>
                    <button type="button" class="btn btn-secondary" id="btnSearchMedal">搜索</button>
                    <button type="button" class="btn btn-secondary" id="btnResetSearchMedal">重置</button>
                    <button type="button" class="btn btn-primary" id="btnCreateMedal">新建勋章</button>
                </div>
            </div>

            <div class="medal-admin__table-wrapper">
                <table class="medal-table">
                    <thead>
                    <tr>
                        <th>ID</th>
                        <th>名称</th>
                        <th>类型</th>
                        <th>描述</th>
                        <th>属性</th>
                        <th>预览</th>
                        <th style="width: 160px;">操作</th>
                    </tr>
                    </thead>
                    <tbody id="medalTableBody">
                    <tr>
                        <td colspan="7" class="medal-table__empty">加载中...</td>
                    </tr>
                    </tbody>
                </table>
            </div>

            <div class="medal-admin__pagination">
                <button type="button" class="btn btn-secondary" id="medalPrevPage">上一页</button>
                <span class="medal-admin__page-info">
                    第 <span id="medalPage">1</span> 页
                </span>
                <button type="button" class="btn btn-secondary" id="medalNextPage">下一页</button>
            </div>

            <!-- Medal edit/create modal -->
            <div class="medal-modal" id="medalEditModal">
                <div class="medal-modal__backdrop"></div>
                <div class="medal-modal__content">
                    <div class="medal-modal__header">
                        <h2 id="medalEditModalTitle">新建勋章</h2>
                        <button type="button" class="medal-modal__close" data-medal-modal-close>&times;</button>
                    </div>
                    <div class="medal-modal__body">
                        <form id="medalEditForm">
                            <input type="hidden" id="medalId" name="medalId"/>

                            <div class="form-group">
                                <label for="medalName">名称 *</label>
                                <input type="text" id="medalName" name="name" class="form-control" required/>
                            </div>

                            <div class="form-group">
                                <label for="medalType">类型</label>
                                <select id="medalType" name="type" class="form-control">
                                    <option value="普通">普通</option>
                                    <option value="精良">精良</option>
                                    <option value="稀有">稀有</option>
                                    <option value="史诗">史诗</option>
                                    <option value="传说">传说</option>
                                    <option value="神话">神话</option>
                                    <option value="限定">限定</option>
                                </select>
                            </div>

                            <div class="form-group">
                                <label for="medalDescription">描述</label>
                                <textarea id="medalDescription" name="description" class="form-control"
                                          placeholder="支持 {var1} {var2} 变量占位"></textarea>
                            </div>

                            <div class="form-group">
                                <label for="medalAttr">属性</label>
                                <textarea id="medalAttr" name="attr" class="form-control"
                                          placeholder="JSON 或配置串，自定义含义"></textarea>
                            </div>
                        </form>
                    </div>
                    <div class="medal-modal__footer">
                        <button type="button" class="btn btn-secondary" data-medal-modal-close>取消</button>
                        <button type="button" class="btn btn-primary" id="btnSaveMedal">保存</button>
                    </div>
                </div>
            </div>

            <!-- Medal owners modal -->
            <div class="medal-modal" id="medalOwnersModal">
                <div class="medal-modal__backdrop"></div>
                <div class="medal-modal__content medal-modal__content--large">
                    <div class="medal-modal__header">
                        <h2>勋章拥有者</h2>
                        <button type="button" class="medal-modal__close" data-medal-modal-close>&times;</button>
                    </div>
                    <div class="medal-modal__body">
                        <div class="medal-owners__toolbar">
                            <span>勋章 ID：<span id="ownersMedalIdLabel"></span></span>
                            <div class="medal-owners__actions">
                                <input type="text" id="grantUserId" class="form-control form-control--inline"
                                       placeholder="用户名"/>
                                <input type="text" id="grantExpireTime" class="form-control form-control--inline"
                                       placeholder="过期时间戳(毫秒,0=永久)"/>
                                <input type="text" id="grantData" class="form-control form-control--inline"
                                       placeholder="数据(可选)"/>
                                <button type="button" class="btn btn-primary" id="btnGrantMedalToUser">
                                    发放勋章
                                </button>
                            </div>
                        </div>
                        <div class="medal-admin__table-wrapper">
                            <table class="medal-table">
                                <thead>
                                <tr>
                                    <th>用户 ID</th>
                                    <th>用户名</th>
                                    <th>过期时间</th>
                                    <th>展示</th>
                                    <th>数据</th>
                                    <th>操作</th>
                                </tr>
                                </thead>
                                <tbody id="medalOwnersTableBody">
                                <tr>
                                    <td colspan="6" class="medal-table__empty">暂无数据</td>
                                </tr>
                                </tbody>
                            </table>
                        </div>
                        <div class="medal-admin__pagination">
                            <button type="button" class="btn btn-secondary" id="ownersPrevPage">上一页</button>
                            <span class="medal-admin__page-info">
                                第 <span id="ownersPage">1</span> 页，共 <span id="ownersTotal">0</span> 条
                            </span>
                            <button type="button" class="btn btn-secondary" id="ownersNextPage">下一页</button>
                        </div>
                    </div>
                </div>
            </div>

            <div class="medal-admin__hint">
                <p>说明：</p>
                <ul>
                    <li>勋章描述支持变量：例如 <code>在 {var1} 活动中获得</code>，发放时 data 使用 <code>活动名称</code>。</li>
                    <li>删除勋章会同时删除用户拥有记录，请谨慎操作。</li>
                </ul>
            </div>
        </div>
    <div id="goToTop" style="position:fixed;bottom:20px;right:10%;display:none;"><a href="#"><svg style="width:30px;height:30px;color:#626262;"><use xlink:href="#toTopIcon"></use></svg></a></div>
</div>
<#include "../footer.ftl">
<script src="${staticServePath}/js/medal${miniPostfix}.js?${staticResourceVersion}"></script>
</body>
</html>
<script>
    var Label = {
        commentEditorPlaceholderLabel: '${commentEditorPlaceholderLabel}',
        langLabel: '${langLabel}',
        luteAvailable: ${luteAvailable?c},
        reportSuccLabel: '${reportSuccLabel}',
        breezemoonLabel: '${breezemoonLabel}',
        confirmRemoveLabel: "${confirmRemoveLabel}",
        reloginLabel: "${reloginLabel}",
        invalidPasswordLabel: "${invalidPasswordLabel}",
        loginNameErrorLabel: "${loginNameErrorLabel}",
        followLabel: "${followLabel}",
        unfollowLabel: "${unfollowLabel}",
        symphonyLabel: "${symphonyLabel}",
        visionLabel: "${visionLabel}",
        cmtLabel: "${cmtLabel}",
        collectLabel: "${collectLabel}",
        uncollectLabel: "${uncollectLabel}",
        desktopNotificationTemplateLabel: "${desktopNotificationTemplateLabel}",
        servePath: "${servePath}",
        staticServePath: "${staticServePath}",
        isLoggedIn: ${isLoggedIn?c},
        funNeedLoginLabel: '${funNeedLoginLabel}',
        notificationCommentedLabel: '${notificationCommentedLabel}',
        notificationReplyLabel: '${notificationReplyLabel}',
        notificationAtLabel: '${notificationAtLabel}',
        notificationFollowingLabel: '${notificationFollowingLabel}',
        pointLabel: '${pointLabel}',
        sameCityLabel: '${sameCityLabel}',
        systemLabel: '${systemLabel}',
        newFollowerLabel: '${newFollowerLabel}',
        makeAsReadLabel: '${makeAsReadLabel}',
        imgMaxSize: ${imgMaxSize?c},
        fileMaxSize: ${fileMaxSize?c},
        <#if isLoggedIn>
        currentUserName: '${currentUser.userName}',
        </#if>
        <#if csrfToken??>
        csrfToken: '${csrfToken}'
        </#if>
    }
    var apiKey = '${apiKey}';
</script>
