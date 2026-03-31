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
    <div id="systemTip" class="tip"></div><br/>
    <div id="skinTip" class="tip"></div>

    <div class="module">
        <div class="module-header">
            主题外观
        </div>
        <div class="module-panel form fn-clear">
            <style>
                .skin-picker-title {
                    display: block;
                    margin: 0 0 10px;
                    width: 100%;
                    flex: 0 0 100%;
                    float: none;
                    clear: both;
                    padding: 0;
                    font-size: 13px;
                    font-weight: 600;
                    color: #7f5a2a;
                    letter-spacing: .02em;
                }

                .skin-picker-section {
                    display: block;
                    width: 100%;
                    clear: both;
                    margin-bottom: 20px;
                }

                .skin-picker-grid {
                    display: grid;
                    width: 100%;
                    grid-template-columns: repeat(auto-fit, minmax(210px, 240px));
                    justify-content: flex-start;
                    align-items: start;
                    gap: 14px;
                }

                .skin-card {
                    width: 100%;
                    border: 1px solid #f2ddc2;
                    border-radius: 16px;
                    background-color: #fff;
                    overflow: hidden;
                    box-shadow: 0 6px 16px rgba(43, 47, 54, 0.05);
                    transition: border-color .2s ease, box-shadow .2s ease, background-color .2s ease;
                }

                .skin-card.is-selected {
                    border-color: #e9b15d;
                    box-shadow: 0 8px 18px rgba(201, 122, 0, 0.10);
                    background-color: #fffdf9;
                }

                .skin-card__head {
                    padding: 12px 12px 10px;
                    text-align: left;
                }

                .skin-card__name {
                    margin: 0;
                    font-size: 15px;
                    font-weight: 600;
                    color: #2b2f36;
                }

                .skin-card__memo {
                    margin-top: 7px;
                    min-height: 38px;
                    font-size: 12px;
                    color: #8a9099;
                    line-height: 1.6;
                }

                .skin-card__preview {
                    display: block;
                    width: 100%;
                    aspect-ratio: 16 / 9;
                    object-fit: fill;
                    background-color: #f6f8fa;
                    border-top: 1px solid #eef2f7;
                    border-bottom: 1px solid #eef2f7;
                }

                .skin-card__preview--empty {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #8a9099;
                    font-size: 13px;
                }

                .skin-card__preview--contain {
                    object-fit: contain;
                    background-color: #fff;
                }

                .skin-card__preview--portrait {
                    width: auto;
                    max-width: 100%;
                    height: 158px;
                    margin: 0 auto;
                    object-fit: contain;
                    border: 0;
                }

                .skin-card__preview-frame {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    height: 158px;
                    background-color: #fff;
                    border-top: 1px solid #eef2f7;
                    border-bottom: 1px solid #eef2f7;
                }

                .skin-card__foot {
                    display: flex;
                    align-items: center;
                    justify-content: flex-start;
                    gap: 10px;
                    padding: 10px;
                }

                .skin-card__action {
                    min-width: 68px;
                    border: 0;
                    border-radius: 10px;
                    padding: 6px 10px;
                    font-size: 12px;
                    color: #fff;
                    background-color: #d0d6e0;
                    cursor: pointer;
                }

                .skin-card.is-selected .skin-card__action {
                    background-color: #e7a13a;
                }

                .skin-picker-help {
                    color: #8a9099;
                    line-height: 1.6;
                }
            </style>

            <input id="userSkin" type="hidden" value="${currentDesktopSkin}">
            <input id="userMobileSkin" type="hidden" value="${currentMobileSkin}">

            <div class="skin-picker-section">
                <label class="skin-picker-title">桌面端主题</label>
                <div class="skin-picker-grid" id="desktopSkinGrid">
                    <#list desktopSkins as skin>
                        <div class="skin-card <#if skin.dirName == currentDesktopSkin>is-selected</#if>" data-select="#userSkin" data-value="${skin.dirName}">
                            <div class="skin-card__head">
                                <div class="skin-card__name">${skin.name}</div>
                                <div class="skin-card__memo">${skin.memo!''}</div>
                            </div>
                            <#if skin.previewUrl?? && skin.previewUrl?has_content>
                                <img class="skin-card__preview" src="${skin.previewUrl}" alt="${skin.name}">
                            <#else>
                                <div class="skin-card__preview skin-card__preview--empty">暂无预览图</div>
                            </#if>
                            <div class="skin-card__foot">
                                <button type="button" class="skin-card__action"><#if skin.dirName == currentDesktopSkin>当前使用<#else>启用</#if></button>
                            </div>
                        </div>
                    </#list>
                </div>
            </div>

            <div class="skin-picker-section">
                <label class="skin-picker-title">移动端主题</label>
                <div class="skin-picker-grid" id="mobileSkinGrid">
                    <#list mobileSkins as skin>
                        <div class="skin-card <#if skin.dirName == currentMobileSkin>is-selected</#if>" data-select="#userMobileSkin" data-value="${skin.dirName}">
                            <div class="skin-card__head">
                                <div class="skin-card__name">${skin.name}</div>
                                <div class="skin-card__memo">${skin.memo!''}</div>
                            </div>
                            <#if skin.previewUrl?? && skin.previewUrl?has_content>
                                <div class="skin-card__preview-frame">
                                    <img class="skin-card__preview skin-card__preview--portrait" src="${skin.previewUrl}" alt="${skin.name}">
                                </div>
                            <#else>
                                <div class="skin-card__preview skin-card__preview--empty">暂无预览图</div>
                            </#if>
                            <div class="skin-card__foot">
                                <button type="button" class="skin-card__action"><#if skin.dirName == currentMobileSkin>当前使用<#else>启用</#if></button>
                            </div>
                        </div>
                    </#list>
                </div>
            </div>

            <label class="skin-picker-help">保存后会刷新页面；当前设备会立即切换到你刚选择的主题。</label>
            <button class="fn-right" onclick="Settings.update('skin', '${csrfToken}')">${saveLabel}</button>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            自定义社区标题
        </div>
        <div class="module-panel form fn-clear">
            <label>设置自定义的网页标题，让你免受老板查岗的困扰。</label>
            <input id="newSystemTitle" type="text" value="<#if hasSystemTitle>${systemTitle}<#else>${symphonyLabel}</#if>"/><br/><br/>
            <button class="fn-right" onclick="Settings.update('system', '${csrfToken}')">${saveLabel}</button>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            自定义社区图标
        </div>
        <div class="module-panel form fn-clear">
            <label>你可以自定义网页标题的图标。</label>
            <div class="avatar-big" id="iconURL" data-imageurl="${iconURL}" style="background-image:url('${iconURL}')"></div>
            <div class="fn__clear" id="iconUploadButtons" style="margin-top: 15px;">
                <form id="iconUpload" method="POST" enctype="multipart/form-data">
                    <label class="btn green label__upload" style="height: 37px;margin: 0;">
                        ${uploadLabel}<input type="file" name="file">
                    </label>
                </form>
                <button class="fn-right" style="height: 37px;" onclick="$('#iconURL').data('imageurl', ''); Settings.update('system', '${csrfToken}');location.reload();">恢复默认</button>
            </div>
        </div>
    </div>

    <div class="module">
        <div class="module-header">
            在线时间显示单位
        </div>
        <div class="module-panel form fn-clear">
            <label>设置显示在线时间时使用的单位。</label>
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
        <div class="module-header" style="margin-bottom: 15px;">
            个人卡片背景
        </div>
        <div class="module-panel form fn-clear">
            <input id="userCardSettings" type="text" value="">
            <div class="fn__clear" id="cardBgUploadButtons" style="margin-top: 15px;">
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
<script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
<script>
    Settings.initUploadAvatar({
        id: 'cardBgUpload',
        userId: '${currentUser.oId}',
        maxSize: '${imgMaxSize?c}'
    }, function (data) {
        let imgUrl = data.result.key;
        let userCardInput = $("#userCardSettings");

        // 确保图片加载完成后再设置背景
        let img = new Image();
        img.onload = function () {
            userCardInput.val(imgUrl);
            userCardInput.attr("bgUrl", imgUrl);
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
    $("#userCardSettings").val(currentCardBg);
    $("#userCardSettings").attr("bgUrl", currentCardBg);

    function bindSkinCards(containerSelector) {
        const container = document.querySelector(containerSelector);
        if (!container) {
            return;
        }

        container.addEventListener('click', function (event) {
            const card = event.target.closest('.skin-card');
            if (!card) {
                return;
            }

            const selectSelector = card.dataset.select;
            const input = document.querySelector(selectSelector);
            if (!input) {
                return;
            }

            input.value = card.dataset.value || '';

            container.querySelectorAll('.skin-card').forEach(function (item) {
                const selected = item === card;
                item.classList.toggle('is-selected', selected);
                const action = item.querySelector('.skin-card__action');
                if (action) {
                    action.textContent = selected ? '当前使用' : '启用';
                }
            });
        });
    }

    bindSkinCards('#desktopSkinGrid');
    bindSkinCards('#mobileSkinGrid');
</script>
