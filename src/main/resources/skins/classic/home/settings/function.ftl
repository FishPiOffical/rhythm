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
<@home "function">
<div class="module">
    <div class="module-header">${functionTipLabel}</div>
    <div class="module-panel form fn-clear">
        <label>${userListPageSizeLabel}</label>
        <input id="userListPageSize" type="number" value="${currentUser.userListPageSize}" />
        <label>${cmtViewModeLabel}</label>
        <select id="userCommentViewMode" name="userCommentViewMode">
            <option value="0"<#if 0 == currentUser.userCommentViewMode> selected</#if>>${traditionLabel}</option>
            <option value="1"<#if 1 == currentUser.userCommentViewMode> selected</#if>>${realTimeLabel}</option>
        </select>
        <label>${avatarViewModeLabel}</label>
        <select id="userAvatarViewMode" name="userAvatarViewMode">
            <option value="0"<#if 0 == currentUser.userAvatarViewMode> selected</#if>>${orgImgLabel}</option>
            <option value="1"<#if 1 == currentUser.userAvatarViewMode> selected</#if>>${staticImgLabel}</option>
        </select>
        <label>${listViewModeLabel}</label>
        <select id="userListViewMode" name="userListViewMode">
            <option value="0"<#if 0 == currentUser.userListViewMode> selected</#if>>${onlyTitleLabel}</option>
            <option value="1"<#if 1 == currentUser.userListViewMode> selected</#if>>${titleAndAbstract}</option>
        </select>
        <label>${indexRedirectLabel}</label>
        <input id="userIndexRedirectURL" type="text" value="${currentUser.userIndexRedirectURL}"/>
        <div class="fn-clear settings-secret">
            <div>
                <label>
                    <input id="userNotifyStatus" <#if 0 == currentUser.userNotifyStatus> checked="checked"</#if> type="checkbox" />
                    ${useNotifyLabel}
                </label>
            </div>
            <div>
                <label>
                    <input id="userSubMailStatus" <#if 0 == currentUser.userSubMailStatus> checked="checked"</#if> type="checkbox" />
                    ${subMailLabel}
                </label>
            </div>
        </div>
        <div class="fn-clear settings-secret">
            <div>
                <label>
                    <input id="userKeyboardShortcutsStatus" <#if 0 == currentUser.userKeyboardShortcutsStatus> checked="checked"</#if> type="checkbox" />
                    ${enableKbdLabel}
                </label>
            </div>
            <div>
                <label>
                    <input id="userReplyWatchArticleStatus" <#if 0 == currentUser.userReplyWatchArticleStatus> checked="checked"</#if> type="checkbox" />
                    ${enableReplyWatchLabel}
                </label>
            </div>
        </div>
        <div class="fn-clear settings-secret">
            <div>
                <label>
                    <input id="userForwardPageStatus" <#if 0 == currentUser.userForwardPageStatus> checked="checked"</#if> type="checkbox" />
                    ${useForwardPageLabel}
                </label>
            </div>
            <div>
                <label>
                    <input id="chatRoomPictureStatus" <#if 0 == currentUser.chatRoomPictureStatus> checked="checked"</#if> type="checkbox" />
                    ${chatRoomPictureStatusLabel}
                </label>
            </div>
        </div>
        <div class="fn-clear"></div>
        <div id="functionTip" class="tip"></div>
        <div class="fn-hr5"></div>
        <button class="fn-right" onclick="Settings.update('function', '${csrfToken}')">${saveLabel}</button>
    </div>
</div>

<div class="module">
    <div class="module-header">
        <h2>${setEmotionLabel}</h2>
    </div>
    <div class="module-panel form fn-clear">
        <textarea id="emotionList" rows="3" placeholder="${setEmotionTipLabel}" >${emotions}</textarea>
        <table id="emojiGrid">
            <#list shortLists as shortlist>
            <tr>
                <#list shortlist as emoji>
                    <#if emoji != "endOfEmoji">
                        <#if emoji == "+1">
                            <td><img alt="${emoji}" src="https://file.fishpi.cn/emoji/graphics/%2B1.png"></td>
                        <#else>
                            <td><img alt="${emoji}" src="https://file.fishpi.cn/emoji/graphics/${emoji}.png"></td>
                        </#if>
                    <#else>
                        <td colspan="2"><a href="https://file.fishpi.cn/emoji/index.html">${moreLabel}</a></td>
                    </#if>
                </#list>
            </tr>
            </#list>
        </table>
        <br><br>
        <div class="fn-clear"></div>
        <div id="emotionListTip" class="tip"></div>
        <div class="fn-hr5"></div>
        <button class="fn-right" onclick="Settings.update('emotionList', '${csrfToken}')">${saveLabel}</button>
    </div>
</div>

<div class="module">
    <div class="module-header fn__flex flex" style="align-items: center">
        <h2>表情包分组</h2>
        <div style="flex:1"></div>
        <button onclick="Settings.migrateEmojis()">迁移历史表情</button>
    </div>
    <div class="module-panel form fn-clear">
        <div class="fn__flex flex flex-wrap" style="align-items: center">
            <div class="fn-clear" id="emojiGroupBox">

            </div>
            <button style="margin-left: 10px" onclick="Settings.createEmojiGroup()">添加分组</button>
        </div>
        <div onclick="Settings.addEmojiByUrl()">通过url添加表情</div>
        <div class="fn__flex flex flex-wrap" id="groupEmojiList" style="flex-wrap: wrap;overflow: scroll;max-height: 300px">

        </div>

    </div>
</div>

</@home>
<script>
    Settings.initFunction();
    Settings.initEmojiGroups();
</script>
