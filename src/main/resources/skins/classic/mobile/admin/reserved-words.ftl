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
<@admin "reservedWords">
<div class="admin">
    <div class="list">
        <#if permissions["rwAddReservedWord"].permissionGrant>
        <div class="form wrapper">
            <button type="button" class="btn red" onclick="window.location = '${servePath}/admin/add-reserved-word'">${allReservedWordLabel}</button>
        </div>
        </#if>
        <script>
            function removeReservedWord(word) {
                $.ajax({
                    url: Label.servePath + '/admin/remove-reserved-word',
                    type: 'POST',
                    data: {
                        id: word
                    },
                    success: function (data) {
                        try {
                            $("#" + word)
                        } catch (e) {
                            location.reload();
                        }
                        let div = $("#" + word);
                        if (div.length > 0) {
                            div.remove();
                        } else {
                            location.reload();
                        }
                    },
                    error: function (err) {
                    }
                })
            }
        </script>
        <ul>
            <#list words as item>
            <li id="${item.oId}">
                <div class="fn-clear">
                    ${item.optionValue}
                    <a href="javascript:void(0);" onclick="removeReservedWord('${item.oId}')" class="fn-right ft-a-title">${removeLabel}</a>
                </div>
            </li>
            </#list>
        </ul>
        <@pagination url="${servePath}/admin/reserved-words"/>
    </div>
</div>
</@admin>
