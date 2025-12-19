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
    <link rel="stylesheet" href="${staticServePath}/css/mobile-base.css?${staticResourceVersion}"/>
    <link rel="stylesheet" href="${staticServePath}/css/medal.css?${staticResourceVersion}"/>
</head>
<body>
<#include "../header.ftl">
<div class="main" style="padding: 25px 15px 20px 15px">
        <div class="medal-admin medal-admin--mobile" id="medalAdminRoot">
            <div class="medal-admin__header">
                <h1 class="medal-admin__title">勋章管理</h1>
                <div class="medal-admin__actions">
                    <input type="text" id="medalSearchInput" class="form-control form-control--inline"
                           placeholder="搜索勋章"/>
                    <button type="button" class="btn btn-secondary" id="btnSearchMedal">搜索</button>
                    <button type="button" class="btn btn-secondary" id="btnResetSearchMedal">重置</button>
                    <button type="button" class="btn btn-primary" id="btnCreateMedal">新建</button>
                </div>
            </div>

            <div class="medal-admin__list" id="medalMobileList">
                <!-- 由 medal.js 动态填充 -->
                <div class="medal-admin__list-empty">加载中...</div>
            </div>

            <div class="medal-admin__pagination">
                <button type="button" class="btn btn-secondary" id="medalPrevPage">上一页</button>
                <span class="medal-admin__page-info">
                    第 <span id="medalPage">1</span> 页
                </span>
                <button type="button" class="btn btn-secondary" id="medalNextPage">下一页</button>
            </div>

            <!-- 复用与桌面版相同的两个弹层结构 -->
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
                                <input type="text" id="medalType" name="type" class="form-control"/>
                            </div>

                            <div class="form-group">
                                <label for="medalDescription">描述</label>
                                <textarea id="medalDescription" name="description" class="form-control"></textarea>
                            </div>

                            <div class="form-group">
                                <label for="medalAttr">属性</label>
                                <textarea id="medalAttr" name="attr" class="form-control"></textarea>
                            </div>
                        </form>
                    </div>
                    <div class="medal-modal__footer">
                        <button type="button" class="btn btn-secondary" data-medal-modal-close>取消</button>
                        <button type="button" class="btn btn-primary" id="btnSaveMedal">保存</button>
                    </div>
                </div>
            </div>

            <div class="medal-modal" id="medalOwnersModal">
                <div class="medal-modal__backdrop"></div>
                <div class="medal-modal__content medal-modal__content--large">
                    <div class="medal-modal__header">
                        <h2>拥有者</h2>
                        <button type="button" class="medal-modal__close" data-medal-modal-close>&times;</button>
                    </div>
                    <div class="medal-modal__body">
                        <div class="medal-owners__toolbar">
                            <span>ID：<span id="ownersMedalIdLabel"></span></span>
                            <div class="medal-owners__actions">
                                <input type="text" id="grantUserId" class="form-control form-control--inline"
                                       placeholder="用户ID"/>
                                <input type="text" id="grantExpireTime" class="form-control form-control--inline"
                                       placeholder="过期时间戳(毫秒)"/>
                                <input type="text" id="grantData" class="form-control form-control--inline"
                                       placeholder="数据"/>
                                <button type="button" class="btn btn-primary" id="btnGrantMedalToUser">
                                    发放
                                </button>
                            </div>
                        </div>
                        <div class="medal-admin__table-wrapper">
                            <table class="medal-table">
                                <thead>
                                <tr>
                                    <th>用户</th>
                                    <th>过期</th>
                                    <th>展示</th>
                                    <th>操作</th>
                                </tr>
                                </thead>
                                <tbody id="medalOwnersTableBody">
                                <tr>
                                    <td colspan="4" class="medal-table__empty">暂无数据</td>
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
