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
    <@head title="${milestoneLabel} - ${symphonyLabel}"></@head>
    <link rel="stylesheet" href="https://cdn.knightlab.com/libs/timeline3/latest/css/timeline.css">
    <style>
        #timeline-embed {
            height: 700px;
            margin: 20px 0;
            font-size: 12px;
            line-height: normal;
        }

        #timeline-embed .tl-timeaxis * {
            line-height: normal !important;
        }

        #timeline-embed .tl-timemarker-content-container {
            height: auto !important;
        }

        .tl-slidenav-previous:hover, .tl-slidenav-next:hover{
            box-shadow: none !important;
        }


    </style>
</head>
<body>
    <#include "header.ftl">
    <div class="main">
        <div class="wrapper">
            <div class="content">
                <div class="module">
                    <h2 style="margin-top: 10px">${milestoneLabel}</h2>
                    <div class="module-panel">
                        <div id="timeline-embed"></div>
                    </div>
                    <span class="fn-flex-1"></span>
                    <a class="btn green" href="${servePath}/milestones/submit">提交大事记</a>
                </div>
            </div>
        </div>
    </div>
    <#include "footer.ftl">
    <script src="https://cdn.knightlab.com/libs/timeline3/latest/js/timeline.js"></script>
    <script>
        <#if timelineEvents?size gt 0>
        const timelineData = {
            "title": {
                "text": {
                    "headline": "${symphonyLabel} 大事记",
                    "text": "记录社区发展的重要时刻"
                },
                "medial":{
                    "url":"https://file.fishpi.cn/logo_raw.png"
                }
            },
            "events": [
                <#list timelineEvents as e>
                    <#assign timelineText>
                         ${e.content}
                         <#if e.link??>
                            <br/>
                            <a href="${e.link.url}" target="_blank">${e.link.text}</a>
                         </#if>
                    </#assign>
                {
                    "start_date": {
                        "year": ${e.date?substring(0,4)},
                        "month": ${e.date?substring(5,7)?number},
                        "day": ${e.date?substring(8,10)?number}
                    },
                    <#if e.end_date??>
                    "end_date": {
                        "year": ${e.end_date?substring(0,4)},
                        "month": ${e.end_date?substring(5,7)?number},
                        "day": ${e.end_date?substring(8,10)?number}
                    },
                    </#if>
                    "text": {
                        "headline": "${e.title?js_string}",
                        "text": "${timelineText?js_string}"
                    },
                    <#if e.media??>
                    "media": {
                        "caption":"${e.media.caption?js_string}",
                        "thumbnail":"${e.media.url}",
                        "url": "${e.media.url}",
                        "type":"${e.media.type}"
                    }
                    </#if>
                }<#if e_has_next>,</#if>
                </#list>
                ]
        };

        new TL.Timeline('timeline-embed', timelineData, {
            language: 'zh-cn',
            type: 'timeline',
            width:"100%"
        });
        <#else>
        document.getElementById('timeline-embed').innerHTML = '<div style="text-align: center; padding: 100px 0; color: #999;">暂无大事记数据</div>';
        </#if>
    </script>
</body>
</html>
