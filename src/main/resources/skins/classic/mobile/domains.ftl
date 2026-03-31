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
<#include "common/index-nav.ftl">
<!DOCTYPE html>
<html>
    <head>
        <@head title="${domainLabel} - ${symphonyLabel}">
        <meta name="description" content="${symDescriptionLabel}"/>
        </@head>
    </head>
    <body>
    <div class="mobile-head">
        <#include "header.ftl">
<#--        <@indexNav "domains"/>-->
        <div class="index-top__nav">
            <a href="${servePath}/domains">${domainCnt} Domains</a>
            <a href="${servePath}/tags">${tagCnt} Tags</a>
        </div>

    </div>
    <div style="height: 74px;width: 1px;" ></div>
        <div class="main" style="padding: 0;margin: 0px;">
                <div class="content fn-clear">
                    <#list allDomains as domain>
                    <div class="module" style="margin-bottom: 0px;">
                        <div class="module-header fn-flex">
                            <a class="ft-gray fn-flex fn__flex-1" rel="nofollow" href="${servePath}/domain/${domain.domainURI}" style="align-items: center">
                                <img src="${domain.domainIconPath}"  style="width: 14px; height: 14px;">
                                &nbsp;${domain.domainTitle}
                            </a>
                            <a class="ft-gray fn-right" rel="nofollow" href="${servePath}/domain/${domain.domainURI}">${domain.domainTags?size} Tags</a>
                        </div>
                        <div class="module-panel  fn-clear">
                            <div class="ft__smaller ft__fade" style="padding: 10px 10px 0 10px">
                                <p>${domain.domainDescription}</p>
                            </div>
                            <ul class=" tags fn-clear" style="margin: 0px; padding-top: 0px" >
                                <#list domain.domainTags as tag>
                                <li>
                                    <a class="tag" rel="nofollow" href="${servePath}/tag/${tag.tagURI}">${tag.tagTitle}</a>
                                </li>
                                </#list>
                            </ul>
                        </div>
                        <div style="width: 100%;height: 2px;background-color: #f7f7f7"></div>
                    </div>
                    </#list>
                </div>
                <div class="side">
                    <#if showSideAd>
                    <#if ADLabel!="">
                    <div class="module">
                        <div class="module-header">
                            <h2>
                                ${sponsorLabel}
                                <a href="${servePath}/settings/system" class="fn-right ft-13 ft-gray" target="_blank">${wantPutOnLabel}</a>
                            </h2>
                        </div>
                        <div class="module-panel ad fn-clear">
                            ${ADLabel}
                        </div>
                    </div>
                    </#if>
                    </#if>
                </div>

        </div>
        <#include "footer.ftl">
        <@listScript/>
    </body>
</html>
