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
<#macro top type>
<#include "../macro-head.ftl">
<!DOCTYPE html>
<html>
    <head>
        <#if type == "balance">
        <@head title="${wealthRankLabel} - ${symphonyLabel}">
        <link rel="canonical" href="${servePath}/top/balance">
        </@head>
        </#if>
        <#if type == "consumption">
        <@head title="${consumptionRankLabel} - ${symphonyLabel}">
        <link rel="canonical" href="${servePath}/top/consumption">
        </@head>
        </#if>
        <#if type == "checkin">
        <@head title="${checkinTopLabel} - ${symphonyLabel}">
        <link rel="canonical" href="${servePath}/top/checkin">
        </@head>
        </#if>
<#--        <#if type == "link">-->
<#--            <@head title="${linkRankLabel} - ${symphonyLabel}">-->
<#--        <link rel="canonical" href="${servePath}/top/link">-->
<#--            </@head>-->
<#--        </#if>-->
        <#if type == "online">
            <@head title="在线时间排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/online">
            </@head>
        </#if>
        <#if type == "evolve">
            <@head title="进化排行榜 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/evolve">
            </@head>
        </#if>
        <#if type == "adr">
            <@head title="ADR 游戏总分排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/adr">
            </@head>
        </#if>
        <#if type == "mofish">
            <@head title="摸鱼大闯关游戏排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/mofish">
            </@head>
        </#if>
        <#if type == "smallmofish">
            <@head title="摸鱼小闯关游戏排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/smallmofish">
            </@head>
        </#if>
        <#if type == "lifeRestart">
            <@head title="人生重开模拟器成就排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/lifeRestart">
            </@head>
        </#if>
        <#if type == "emoji">
            <@head title="Emoji 真假小黄脸 游戏总分排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/emoji">
            </@head>
        </#if>
        <#if type == "xiaoice">
            <@head title="小冰游戏排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/xiaoice">
            </@head>
        </#if>
        <#if type == "invite">
            <@head title="邀请成员排行 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/invite">
            </@head>
        </#if>
        <#if type == "donate">
            <@head title="鱼排续命师 - ${symphonyLabel}">
                <link rel="canonical" href="${servePath}/top/donate">
            </@head>
        </#if>
        <link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
        <link rel="stylesheet" href="${staticServePath}/css/responsive.css?${staticResourceVersion}" />
    </head>
    <body>
        <#include "../header.ftl">
        <div class="main">
            <div class="wrapper">
                <div class="content">
                    <div class="module">
                    <#nested>
                        <div class="top-ranking">
                            <#include "../common/ranking.ftl">
                        </div>
                    </div>
                </div>
                <div class="side">
                    <#include "../side.ftl">
                </div>
            </div>
        </div>
        <#include "../footer.ftl">
        <script src="${staticServePath}/js/settings${miniPostfix}.js?${staticResourceVersion}"></script>
    </body>
</html>
</#macro>
