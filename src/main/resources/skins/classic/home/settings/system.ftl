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
<@home "system">
    <div id="systemTip" class="tip"></div>
    <div class="module">
        <div class="module-header">
            自定义社区标题
        </div>
        <div class="module-panel form fn-clear">
            <input id="newSystemTitle" type="text" value="<#if hasSystemTitle>${systemTitle}<#else>${symphonyLabel}</#if>"/><br/><br/>

            <button class="fn-right" onclick="Settings.update('system', '${csrfToken}')">${saveLabel}</button>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            自定义社区图标
        </div>
        <div class="module-panel form fn-clear">
            <div class="avatar-big" id="iconURL" data-imageurl="${iconURL}" style="height: 128px; width: 128px; background-image:url('${iconURL}')"></div>
            <div class="fn__clear" id="iconUploadButtons" style="margin-top: 15px;">
                <form id="iconUpload" method="POST" enctype="multipart/form-data">
                    <label class="btn green label__upload" style="height: 37px;margin: 0;">
                        ${uploadLabel}<input type="file" name="file">
                    </label>
                </form>
                <button class="fn-right" style="height: 37px;" onclick="$('#iconURL').data('imageurl', ''); Settings.update('system', '${csrfToken}');location.reload();">恢复默认</button>
            </div>
            <label style="padding: 3px 0">
                如果自定义网站图标后不生效，请使用 CTRL+F5 快捷键强行刷新页面；为确保浏览体验，建议使用128KB以下图片。
            </label>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            在线时间显示单位
        </div>
        <div class="module-panel form fn-clear">
            <select id="onlineTimeUnit" onchange="Settings.update('system', '${csrfToken}')">
                <option value="m" <#if 'm' == onlineTimeUnit>selected</#if>>分钟</option>
                <option value="h" <#if 'h' == onlineTimeUnit>selected</#if>>小时</option>
                <option value="d" <#if 'd' == onlineTimeUnit>selected</#if>>天</option>
            </select>
        </div>
    </div>

    <div class="module">
        <div class="module-header">社区广告</div>
        <div class="module-panel form fn-clear">
            <label>
                摸鱼派社区❤️用爱发电；如果你喜欢这里的氛围，可以通过开启社区广告来支持我们。
            </label>
            <div class="fn-clear settings-secret">
                <div>
                    <label>
                        <input id="showSideAd" type="checkbox" <#if showSideAd>checked="checked"</#if>>
                        显示侧栏广告
                    </label>
                </div>
                <div>
                    <label>
                        <input id="showTopAd" type="checkbox" <#if showTopAd>checked="checked"</#if>>
                        显示顶部广告
                    </label>
                </div>
            </div>
            <button class="fn-right" onclick="Settings.update('system', '${csrfToken}')">${saveLabel}</button>
        </div>
    </div>

    <div class="module">
        <div class="module-header" style="margin-bottom: 50px;">
            个人卡片背景
        </div>
        <div class="module-panel form fn-clear">
            <div style="position: absolute;z-index: 130;">
                <div class="user-card" id="userCardSettings">
                    <div>
                        <a href="${servePath}/member/${currentUser.userName}">
                            <div class="avatar-mid-card" style="background-image: url(${currentUser.userAvatarURL});"></div>
                        </a>
                        <div class="user-card__meta">
                            <div class="fn__ellipsis">
                                <a class="user-card__name" href="${servePath}/member/${currentUser.userName}"><b>${currentUser.userNickname}</b></a>
                                <a class="ft-gray ft-smaller" href="${servePath}/member/${currentUser.userName}"><b>${currentUser.userName}</b></a>
                            </div>
                            <div class="user-card__info vditor-reset">
                                ${currentUser.userIntro}
                            </div>
                            <div class="user-card__icons fn__flex">
                                <div class="fn__flex-1">
                                    <a href="https://fishpi.cn/article/1630575841478">
                                        <img style="height: 20px;margin: 0px;" src="https://file.fishpi.cn/vipRole.png">
                                    </a>
                                    <a href="${servePath}/member/${currentUser.userName}/points" class="tooltipped-new tooltipped__n" aria-label="${currentUser.userPoint?c} 积分">
                                        <svg>
                                            <use xlink:href="#iconPoints"></use>
                                        </svg>
                                    </a>
                                </div>
                                <div class="fn__shrink">
                                    <a class="green small btn" href="${servePath}/chat?toUser=${currentUser.userName}" rel="nofollow">
                                        私信
                                    </a>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="fn__clear" id="cardBgUploadButtons" style="margin-top: 150px;">
                <form id="cardBgUpload" method="POST" enctype="multipart/form-data">
                    <label class="btn green label__upload" style="height: 37px;margin: 0;">
                        ${uploadLabel}<input type="file" name="file">
                    </label>
                </form>
                <button class="fn-right" style="height: 37px;" onclick="$('#userCardSettings').attr('bgUrl', '');Settings.update('system', '${csrfToken}');location.reload();">恢复默认</button>
            </div>
        </div>
    </div>
</@home>
<script src="${staticServePath}/js/lib/jquery/file-upload-9.10.1/jquery.fileupload.min.js"></script>
<script>
    Settings.initUploadAvatar({
        id: 'cardBgUpload',
        userId: '${currentUser.oId}',
        maxSize: '${imgMaxSize?c}'
    }, function (data) {
        let imgUrl = data.result.key;
        let userCard = $("#userCardSettings");

        // 确保图片加载完成后再设置背景
        let img = new Image();
        img.onload = function () {
            $("#cardBgUploadButtons").css("margin-top", "270px");
            userCard.addClass("user-card--bg");
            userCard.css("background-image", "url(" + imgUrl + ")");
            userCard.find("> div").attr("style", "background-image: linear-gradient(90deg, rgba(214, 227, 235, 0.36), rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.76));");
            userCard.find("> div > a > div").css({
                width: "105px",
                height: "105px",
                top: "80px"
            });
            userCard.attr("bgUrl", imgUrl);
            Settings.update('system', '${csrfToken}');
        };
        img.onerror = function () {
            alert("图片加载失败，请检查图片格式或重新上传。");
        };
        img.src = imgUrl; // 触发图片加载
    });

    Settings.initUploadAvatar({
        id: 'iconUpload',
        userId: '${currentUser.oId}',
        maxSize: '${imgMaxSize?c}'
    }, function (data) {
        let imgUrl = data.result.key;
        $('#iconURL').data('imageurl', imgUrl);
        $('#iconURL').css('background-image', 'url(\'' + imgUrl + '\')');
        Settings.update('system', '${csrfToken}');
        location.reload();
    });

    let currentCardBg = "${cardBg}";
    if (currentCardBg !== "") {
        $("#cardBgUploadButtons").css("margin-top", "270px");
        $("#userCardSettings").addClass("user-card--bg");
        $("#userCardSettings").css("background-image", "url(" + currentCardBg + ")");
        $("#userCardSettings > div").attr("style", "background-image: linear-gradient(90deg, rgba(214, 227, 235, 0.36), rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.76));");
        $("#userCardSettings > div > a > div").css("width", "105px");
        $("#userCardSettings > div > a > div").css("height", "105px");
        $("#userCardSettings > div > a > div").css("top", "80px");
        $("#userCardSettings").attr("bgUrl", currentCardBg);
    }
</script>
