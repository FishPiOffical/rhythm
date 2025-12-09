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
<@admin "pic">
    <div class="content">
        <div class="module">
            <div class="module-header">
                <h2>图片审核</h2>
            </div>

            <@pagination url="${servePath}/admin/pic"/>
            <div class="module-panel form fn-clear form--admin">
                <div>
                    <h3>审前必读</h3>
                    <p>1. 您的审核将被记录，审核完毕的图片刷新页面后会消失，只能审核最近45天的图片</p>
                    <p>2. 违规图片：如涉及政治、色情、违法等违规图片，请及时删除，删除后您将获得128积分奖励，同时用户将被处以500积分的处罚，并通知用户</p>
                    <p>3. 正常图片：以后会被翻出来看，有用并且合法的图片，标记后您将获得2积分奖励</p>
                </div>
                <br>
                <div class="file__items fn__clear">
                    <#list files as file>
                        <div class="item" id="${file.oId}">
                            <#if file.type == "mp4">
                                <#if file.public == true>
                                    <video class="item__img" autoplay muted loop playsinline>
                                        <source src="${file.path}" type="video/mp4">
                                        您的浏览器不支持 video 标签。
                                    </video>
                                <#else>
                                    <div class="item__img" style="margin: 15px 0 0 15px;">
                                        <svg class="icon-thumbs-up"><use xlink:href="#thumbs-up"></use></svg> 已审核
                                    </div>
                                </#if>
                            <#else>
                                <#if file.public == true>
                                    <div class="item__img" style="background-image: url(${file.path});"></div>
                                <#else>
                                    <div class="item__img" style="margin: 15px 0 0 15px;">
                                        <svg class="icon-thumbs-up"><use xlink:href="#thumbs-up"></use></svg> 已审核
                                    </div>
                                </#if>
                            </#if>
                            <div class="item__info">
                                上传时间：${file.time}
                                <br>
                                类型：${file.type}
                                <br>
                                上传者：<a href="${servePath}/member/${file.userName}" target="_blank">${file.userName}</a>
                                <#if file.public == true>
                                    <span style="position: absolute; left: 5px; bottom: 5px">
                                        <button class="btn red" onclick="mark('${file.oId}', 'illegal')">标记为违规图片</button><br>
                                        <button class="btn green" style="margin-top: 8px" onclick="mark('${file.oId}', 'normal')">标记为正常图片</button>
                                    </span>
                                    <span style="position: absolute; right: 5px; bottom: 5px">
                                        <button class="btn green" onclick="window.open('${file.path}')">查看原图</button>
                                    </span>
                                </#if>
                            </div>
                        </div>
                    </#list>
                </div>
            </div>
            <@pagination url="${servePath}/admin/pic"/>
        </div>
    </div>
    <script>
        function mark(oId, type) {
            if (type == "temp") {
                if (confirm("请确定这张图片不是表情包、没有引用在帖子或评论里\n如果它是聊天室里随意发送的一张截图，不会再有人看了，或者干脆就是无效内容，请删除它\n请再次确认！多次无效审核将会影响你的工资！")) {
                    $.ajax({
                        url: "${servePath}/admin/pic",
                        type: "POST",
                        async: true,
                        data: {
                            oId: oId,
                            type: type
                        },
                        success: function (result) {
                            Util.notice("success", 1500, result.msg);
                            $("#" + oId).find("span")[0].innerHTML = '';
                            $("#" + oId).find("span")[1].innerHTML = '';
                            $("#" + oId + " .item__img").remove();
                            $("#" + oId).prepend('<div class="item__img" style="margin: 15px 0 0 15px;">' +
                                '<svg class="icon-thumbs-up"><use xlink:href="#thumbs-up"></use></svg> 已审核' +
                                '</div>');
                        }
                    });
                }
            } else {
                $.ajax({
                    url: "${servePath}/admin/pic",
                    type: "POST",
                    async: true,
                    data: {
                        oId: oId,
                        type: type
                    },
                    success: function (result) {
                        Util.notice("success", 1500, result.msg);
                        $("#" + oId).find("span")[0].innerHTML = '';
                        $("#" + oId).find("span")[1].innerHTML = '';
                        $("#" + oId + " .item__img").remove();
                        $("#" + oId).prepend('<div class="item__img" style="margin: 15px 0 0 15px;">' +
                            '<svg class="icon-thumbs-up"><use xlink:href="#thumbs-up"></use></svg> 已审核' +
                            '</div>');
                    }
                });
            }
        }
    </script>
</@admin>
