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
<#macro head title>
<meta charset="utf-8" />
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0" />
<meta name="theme-color" content="#4285f4">
<title>${title}</title>
<#nested>
<meta name="copyright" content="B3log" />
<meta http-equiv="Window-target" content="_top" />
<link rel="stylesheet" href="${staticServePath}/css/mobile-base.css?${staticResourceVersion}" />
<link rel="icon" type="image/png" href="${iconURL}" />
<link rel="apple-touch-icon" href="${staticServePath}/images/faviconH.png">
<link rel="stylesheet" href="${staticServePath}/js/lib/tooltips/tooltips.min.css?${staticResourceVersion}">
${siteVisitStatCode}
<script type="text/javascript" charset="UTF-8" src="https://cdn.wwads.cn/js/makemoney.js"></script>
<meta name="baidu-site-verification" content="codeva-wF3TNphcy6" />
</#macro>
