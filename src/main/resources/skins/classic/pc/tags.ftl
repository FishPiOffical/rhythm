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
<#include "macro-head.ftl">
<!DOCTYPE html>
<html>
    <head>
        <@head title="${tagLabel} - ${symphonyLabel}">
        <meta name="description" content="${symphonyLabel} ${trendTagsLabel},${symphonyLabel} ${coldTagsLabel}"/>
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
        <link rel="canonical" href="${servePath}/tags">
    </head>
    <body>
        <#include "header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content">
                    <div class="module">
                        <div class="module-header">  
                            <h2>
                                ${trendTagsLabel}
                            </h2>
                        </div>
                        <div class="module-panel list">
                            <ul>
                                <#list trendTags as tag>
                                <li class="fn-flex">
                                    <#if tag.tagIconPath!="">
                                    <div class="avatar" style="background-image:url('${tag.tagIconPath}')" alt="${tag.tagTitle}"></div>
                                    </#if>
                                    <div class="fn-flex-1">
                                        <div class="fn-clear">
                                            <h3 ><a rel="tag" class="ft-a-title" href="${servePath}/tag/${tag.tagURI}">${tag.tagTitle}</a></h3>
                                            <div style="color:rgba(0,0,0,0.54);font-size: 12px; float: left">
                                                <span >${referenceLabel}</span>
                                                <span class="article-level<#if tag.tagReferenceCount lt 100>0
                                                                           <#elseif tag.tagReferenceCount gte 100 && tag.tagReferenceCount lt 500>1
                                                                           <#elseif tag.tagReferenceCount gte 500 && tag.tagReferenceCount lt 1000>2
                                                                           <#elseif tag.tagReferenceCount gte 1000 && tag.tagReferenceCount lt 2000>3
                                                                           <#else>4</#if>">
                                                    ${tag.tagReferenceCount?c} &nbsp;&nbsp;
                                                </span>
                                                <span>${cmtLabel}</span>
                                                <span class="article-level<#if tag.tagCommentCount lt 100>0
                                                                           <#elseif tag.tagCommentCount gte 100 && tag.tagCommentCount lt 500>1
                                                                           <#elseif tag.tagCommentCount gte 500 && tag.tagCommentCount lt 1000>2
                                                                           <#elseif tag.tagCommentCount gte 1000 && tag.tagCommentCount lt 2000>3
                                                                           <#else>4</#if>">
                                                     ${tag.tagCommentCount?c}&nbsp;&nbsp;
                                                </span>
                                                <span>${followLabel}</span>
                                                <span id= "tag-follow-count${tag.oId}" class="article-level<#if tag.tagFollowerCount lt 100>0
                                                                           <#elseif tag.tagFollowerCount gte 100 && tag.tagFollowerCount lt 500>1
                                                                           <#elseif tag.tagFollowerCount gte 500 && tag.tagFollowerCount lt 1000>2
                                                                           <#elseif tag.tagFollowerCount gte 1000 && tag.tagFollowerCount lt 2000>3
                                                                           <#else>4</#if>">
                                                     ${tag.tagFollowerCount?c}
                                                </span>
                                            </div>
                                            <div class="fn-right">
                                                        <button id= "btn-follow${tag.oId}" class="btn mid" onclick="follow('${tag.oId}', ${tag.isFollowing?c})">
                                                            <#if isLoggedIn && tag.isFollowing>
                                                                ${unfollowLabel}
                                                            <#else>
                                                                ${followLabel}
                                                            </#if>
                                                        </button>
                                            </div>
                                        </div>
                                        <div class="vditor-reset">${tag.tagDescription}</div>
                                    </div>
                                </li>
                                </#list>
                            </ul>
                        </div>
                    </div>
                </div>
                <div class="side">
                    <#include 'common/person-info.ftl'/>
                    <div class="module">
                        <div class="module-header">  
                            <h2>
                                ${coldTagsLabel}
                            </h2>
                        </div>
                        <div class="module-panel">
                            <ul class="module-list">
                                <#list coldTags as tag>
                                <li>
                                    <#if tag.tagIconPath!="">
                                    <div class="avatar-small" style="background-image: url('${tag.tagIconPath}')" alt="${tag.tagTitle}"></div>
                                    </#if>
                                    <a class="ft-a-title" rel="tag" href="${servePath}/tag/${tag.tagURI}">${tag.tagTitle}</a>
                                    <div class="vditor-reset">${tag.tagDescription}</div>
                                </li>
                                </#list>
                            </ul>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <#include "footer.ftl">
    </body>
</html>
<script>
    function follow(id,isFollowing) {
        if (!Label.isLoggedIn) {
            Util.needLogin()
        }

        var requestJSONObject = {
            followingId: id,
        }

        let path

        if (isFollowing) {
            path = '/unfollow/tag';
        } else {
            path = '/follow/tag';
        }

        $.ajax({
            url: Label.servePath + path,
            type: 'POST',
            cache: false,
            data: JSON.stringify(requestJSONObject),
            success: function (result, textStatus) {
                if (0 === result.code) {
                    var btn = $('#btn-follow'+ id).attr('onclick', 'follow(\'' + id + '\', ' +!isFollowing + ')')
                    var personCnt = document.getElementById("ftc").innerHTML
                    var tagCnt = document.getElementById("tag-follow-count"+id).innerHTML
                    if (isFollowing) {
                        btn.html(Label.followLabel)
                        //个人信息 followingTagCnt - 1
                        $('#ftc').html(--personCnt)
                        $("#tag-follow-count"+id).html(--tagCnt)
                    } else {
                        $('#btn-follow'+ id).html(Label.unfollowLabel)
                        //个人信息 followingTagCnt + 1
                        $('#ftc').html(++personCnt)
                        $("#tag-follow-count"+id).html(++tagCnt)
                    }

                }
            },
            complete: function () {
            },
        });
    }
</script>
