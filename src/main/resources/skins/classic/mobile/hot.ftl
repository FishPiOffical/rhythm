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
<#include "macro-list.ftl">
<#include "common/sub-nav.ftl">
<#include "common/index-nav.ftl">
<!DOCTYPE html>
<html>
    <head>
        <@head title="${hotLabel} - ${symphonyLabel}">
        <meta name="description" content="${recentArticleLabel}"/>
        </@head>
    </head>
    <body>
            <div class="mobile-head">
                <#include "header.ftl">
                <@indexNav "hot"/>
            </div>
            <div style="height: 74px;width: 1px;" ></div>
            <div class="main">
            <div class="content fn-clear">
                <@list listData=indexArticles/>
                <a href="${servePath}/recent" class="ft-gray more-article">${moreRecentArticleLabel}</a>
            </div>
            </div>
            <div class="side wrapper">
                <#include "side.ftl">
            </div>
        <#include "footer.ftl">
        <@listScript/>
    </body>
</html>
