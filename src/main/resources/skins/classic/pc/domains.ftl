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
        <@head title="${domainLabel} - ${symphonyLabel}">
        <meta name="description" content="${symDescriptionLabel}"/>
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
        <link rel="canonical" href="${servePath}/domains">
    </head>
    <body>
        <#include "header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content fn-clear">
                    <div class="fn-flex module-header ft-domain-header"  >
                        领域
                        <span class="fn-right ft-fade fn-flex-1 fn-flex ft-domain-head">
                            <a  class="ft-gray" href="${servePath}/domians">${domainCnt} 领域</a>
                            <span style="width: 20px"></span>
                            <a  class="ft-gray" href="${servePath}/tags">${tagCnt} 标签</a>
                        </span>
                    </div>


                    <#list allDomains as domain>
                    <div class="module" style="padding: 15px;margin-bottom: 1px;">
                        <div class="fn__flex">
                            <a class="fn__flex-inline" href="${servePath}/domain/${domain.domainURI}">
                                <img src="${domain.domainIconPath}" style="width: 55px;height: 56px;margin-right: 15px" />
                            </a>
                            <div class="fn-flex-1">
                                <div class="title" style="font-size: 16px; font-weight: 400;line-height: 20px; ">
                                    <a  style="color: #000;" href="${servePath}/domain/${domain.domainURI}">${domain.domainTitle}</a>
                                </div>
                                <div class="ft__fade ft__smaller">
                                    <p>${domain.domainDescription}</p>
                                </div>

                            <ul class="tag-desc fn-clear" style="margin: 10px 0 0 0;">
                                <#list domain.domainTags as tag>
                                    <li style="margin-bottom: 10px;">
                                        <a rel="nofollow" href="${servePath}/tag/${tag.tagURI}">
                                            <#if tag.tagIconPath!="">
                                                <img src="${tag.tagIconPath}" alt="${tag.tagTitle}" /></#if>
                                            ${tag.tagTitle}
                                        </a>
                                        <div<#if tag.tagDescription == ''> style="width:auto"</#if>>
                                            <div>${tag.tagDescription}</div>
                                            <span class="fn-right">
                                            <span class="ft-gray">${referenceLabel}</span>
                                            ${tag.tagReferenceCount} &nbsp;
                                            <span class="ft-gray">${cmtLabel}</span>
                                             ${tag.tagCommentCount}&nbsp;
                                            </span>

                                        </div>
                                    </li>
                                </#list>
                            </ul>
                            </div>
                        </div>
                    </div>
                    </#list>
                </div>

                <div class="side">
                    <#include "side.ftl">
                </div>
            </div>
        </div>
        <#include "footer.ftl">
    </body>
</html>
