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
<#include "macro-pagination.ftl">
<#include "common/title-icon.ftl">
<!DOCTYPE html>
<html>
    <head>
        <@head title="${breezemoonLabel} - ${symphonyLabel}">
        <meta name="description" content="${symDescriptionLabel}"/>
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content fn-clear" id="breezemoons-pjax-container">
                    <#if pjax><!---- pjax {#breezemoons-pjax-container} start ----></#if>
                    <#if 0 < breezemoons?size>
                        <div class="module">
                            <div class="article-list list">
                                <ul class="stick active-bz-list-big">
                                    <#list breezemoons as bm>
                                    <li>
                                        <div class="fn-flex">
                                            <div class="fn-flex-1">
                                                <div class="fn-flex">
                                                    <a rel="nofollow" href="${servePath}/member/${bm.breezemoonAuthorName}"><div class="avatar" aria-label="${bm.breezemoonAuthorName}" style="background-image:url('${bm.breezemoonAuthorThumbnailURL48}')"></div></a>
                                                    <div class="fn-ellipsis ft-fade ft-smaller list-info">
                                                        <a rel="nofollow" class="author" href="${servePath}/member/${bm.breezemoonAuthorName}">
                                                            ${bm.breezemoonAuthorName}
                                                        </a>
                                                        <br>
                                                        ${bm.timeAgo}
                                                        <#if "" != bm.breezemoonCity>
                                                            <a href="${servePath}/city/${bm.breezemoonCity}" target="_blank" class="ft-gray" rel="nofollow">${bm.breezemoonCity}</a>
                                                        </#if>
                                                        <br>
                                                    </div>
                                                </div>
                                                <a style="color: #3b3b3b" class="abstract" href="${servePath}/member/${bm.breezemoonAuthorName}/breezemoons/${bm.oId}">
                                                    ${bm.breezemoonContent}
                                                </a>
                                            </div>
                                        </div>
                                    </li>
                                    </#list>
                                </ul>
                            </div>
                        </div>
                    </#if>
                    <div class="module">
                        <@pagination url="${servePath}/breezemoons${current}" pjaxTitle="${breezemoonLabel} - ${symphonyLabel}"/>
                    </div>
                    <#if pjax><!---- pjax {#breezemoons-pjax-container} end ----></#if>
                </div>

                <div class="side">
                    <#include "side.ftl">
                </div>
            </div>
        </div>
        <#include "common/domains.ftl">
        <#include "footer.ftl">
        <@listScript/>
        <script>
            $.pjax({
                selector: 'a',
                container: '#breezemoons-pjax-container',
                show: '',
                cache: false,
                storage: true,
                titleSuffix: '',
                filter: function(href){
                    return 0 > href.indexOf('${servePath}/breezemoons');
                },
                callback: function () {
                    Util.parseMarkdown();
                    Util.parseHljs();
                    Util.listenUserCard();
                }
            });
            NProgress.configure({ showSpinner: false });
            $('#breezemoons-pjax-container').bind('pjax.start', function(){
                NProgress.start();
            });
            $('#breezemoons-pjax-container').bind('pjax.end', function(){
                NProgress.done();
            });
        </script>
    </body>
</html>
