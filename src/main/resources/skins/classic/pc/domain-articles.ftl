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
<!DOCTYPE html>
<html>
    <head>
        <@head title="${domain.domainTitle} - ${domainLabel} - ${symphonyLabel}">
        <meta name="keywords" content="${domain.domainSeoKeywords}" />
        <meta name="description" content="${domain.domainSeoDesc}"/>
        </@head>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "header.ftl">
        <div class="main">
            <div class="wrapper">
             <div class="fn-flex fn__flex-1" id="domain-pjax-container">

                 <#if pjax><!---- pjax {#domain-pjax-container} start ----></#if>
                <div class="side__domains">
                    <ul>
                        <#list domains as sideDomain>
                            <li>
                                <a class="domains__first <#if sideDomain.domainURI == domain.domainURI >domains__first--current</#if> "  href="${servePath}/domain/${sideDomain.domainURI}">
                                    <img class="domain__icon" src="${sideDomain.domainIconPath}" />
                                    ${sideDomain.domainTitle}
                                </a>
                            </li>
                        </#list>
                    </ul>
                </div>
                <div class="content" >
                    <div class="module" style="box-shadow: unset">
                    ${domain.domainCSS}
                    <div class="tabs-sub fn-clear domain-sub-tags">
                        <#list domains as navDomain>
                        <#if navDomain.domainURI == domain.domainURI>
                            <#if navDomain.domainTags?size gt 0>
                                <#list navDomain.domainTags as tag>
                                <a class="tabs-sub__item tabs-sub__item--link <#if  tag.tagTitle == domainCurrentTag>tabs-sub__item--current</#if>" rel="nofollow" href="${servePath}/domain/${domain.domainURI}?tag=${tag.tagTitle}">${tag.tagTitle}</a>
                                </#list>
                            <a  class="tabs-sub__item tabs-sub__item--link ft-fade see-all-domains" href="${servePath}/domains"  style="display: flex;flex-direction: row;align-items: center">
                                <svg style="margin-right: 5px"><use xlink:href="#domain"></use></svg>查看所有领域
                            </a>
                            <a  class="domain-rss tabs-sub__item tabs-sub__item--link" href="${servePath}/rss/domain/${domain.domainURI}.xml">
                                <svg><use xlink:href="#iconRss"></use></svg>
                            </a>
                            <#else>
                                <div class="no-list fn-flex-1">${chickenEggLabel}</div>
                            </#if>
                        </#if>
                        </#list>
                    </div>
                        <#if latestArticles?size gt 0>
                            <@list listData=latestArticles/>
                            <@pagination url="${servePath}/domain/${domain.domainURI}" pjaxTitle="${domain.domainTitle} - ${domainLabel} - ${symphonyLabel}"/>
                        <#else>
                            <div class="no-list"> ${systemEmptyLabel}</div>
                        </#if>
                    </div>
                </div>
                 <#if pjax><!---- pjax {#domain-pjax-container} end ----></#if>
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
                container: '#domain-pjax-container',
                show: '',
                cache: false,
                storage: true,
                titleSuffix: '',
                filter: function(href){
                    return 0 > href.indexOf('${servePath}/domain/');
                },
                callback: function(status){
                    switch(status.type){
                        case 'success':
                        case 'cache':
                            $('.nav-tabs a').removeClass('current');
                            $('.nav-tabs a').each(function () {
                                if ($(this).attr('href') === location.href) {
                                    $(this).addClass('current');
                                }
                            });
                            Util.parseMarkdown();
                            Util.parseHljs();
                            Util.listenUserCard();
                        case 'error':
                            break;
                        case 'hash':
                            break;
                    }
                }
            });
            NProgress.configure({ showSpinner: false });
            $('#domain-pjax-container').bind('pjax.start', function(){
                NProgress.start();
            });
            $('#domain-pjax-container').bind('pjax.end', function(){
                NProgress.done();
            });
        </script>
    </body>
</html>
