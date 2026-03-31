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
    <@head title="下载客户端 - ${symphonyLabel}">
    <meta charset="UTF-8">
    <style>
    .download__title{
      font-size:40px;
      font-weight:bold;
      line-height:45px;
    }
    .wrapper{
        display:flex;
        flex-direction:column;
    }
    .download__contain{
        margin:0 auto;
        padding:0 16px;
        display:flex;
        flex-wrap:wrap;
        position:relative;
        z-index:1;
        max-width:1000px;
        place-content:space-evenly;
    }
    .download__contain .item{
        display: flex;
        flex-direction: column;
        width: 240px;
        height: 240px;
        box-shadow: 0 0 0 1px rgba(53,72,91,.1),0 3px 2px rgba(0,0,0,.04),0 7px 5px rgba(0,0,0,.02),0 13px 10px rgba(0,0,0,.02),0 22px 17px rgba(0,0,0,.02);
        transition: box-shadow .4s cubic-bezier(.16,1,.3,1);
        padding: 24px;
        border-radius: 8px;
        background-color: #fafbfc;
        margin-bottom: 32px;
        line-height: 24px
    }
    .download__contain .item img {
        height: 64px;
        width: 64px;
        margin-bottom: 18px
    }
    .download__contain .item:hover {
        box-shadow: 0 0 18px 16px rgb(53 72 91/10%)
    }
    .download__link{
        color:#035bd6;
        transition: color .4s;
        text-decoration:none;
    }

    .item__title{
        font-size:32px;
        font-weight:bold;
        line-height:36px;
    }
    </style>
</@head>
<link rel="stylesheet" href="${staticServePath}/css/index.css?${staticResourceVersion}" />
</head>
<body>
<#include "header.ftl">
<div class="main" style="margin-bottom: 50px">
    <div class="wrapper" style="max-width: 620px;position: relative;z-index: 1;margin-top: 30px">
        <div style="text-align: center" >
            <h2 class="download__title">
                摸鱼派客户端
            </h2>
        </div>
        <div style="opacity: 0.75;color: #586069;font-size: 12px;text-align: center;margin-top: 16px;margin-bottom:30px;">
            <em>享受随时随地摸鱼的快乐</em>
        </div>
    </div>
    <div class="download__contain">
        <div class="item">
            <img src="${staticServePath}/images/clients/windows.png">
            <h3 class="item__title">Windows 客户端</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/imlinhanchao">@imlinhanchao</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://github.com/imlinhanchao/fishpi-desktop/releases" target="_blank">Github Releases</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/macos.png">
            <h3 class="item__title">macOS 客户端</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/imlinhanchao">@imlinhanchao</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://github.com/imlinhanchao/fishpi-desktop/releases" target="_blank">Github Releases</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/ios.png">
            <h3 class="item__title">iOS 客户端</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/Yui">@Yui</a>&nbsp;<a href="${staticServePath}/member/iwpz">@iwpz</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://apps.apple.com/cn/app/%E6%91%B8%E9%B1%BC%E6%B4%BE/id1617385824" target="_blank">App Store</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/android.png">
            <h3 class="item__title">Android客户端(推荐)</h3>
            <div style="flex:1">Author:&nbsp;<a href="${staticServePath}/member/otis1026">@otis1026</a></div>
            <div class="fn__flex">
                <a class="download__link" href="${staticServePath}/article/1745833503658" target="_blank">详细地址</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/android.png">
            <h3 class="item__title">Android 客户端</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/Yui">@Yui</a>&nbsp;<a href="${staticServePath}/member/iwpz">@iwpz</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://github.com/rLingMX/pwl-chat-uniapp/releases" target="_blank">Github Releases</a>
            </div>
             <div class="fn__flex">
                <a class="download__link" href="https://lmx.lanzouj.com/b0dxiaxsb" target="_blank">备用地址（密码pwl）</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/android.png">
            <h3 class="item__title">Android客户端(Flutter)</h3>
            <div style="flex:1">Author:&nbsp;<a href="${staticServePath}/member/iwpz">@iwpz</a></div>
            <div class="fn__flex">
                <a class="download__link" href="${staticServePath}/article/1654133472152" target="_blank">详细地址</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/android.png">
            <h3 class="item__title">Android客户端(原生)</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/csfwff">@csfwff</a></div>
            <div class="fn__flex">
                <a class="download__link" href="${staticServePath}/article/1656144820684" target="_blank">详细地址</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/vscode.png">
            <h3 class="item__title">VSCode 插件</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/imlinhanchao">@imlinhanchao</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://marketplace.visualstudio.com/items?itemName=hancel.pwl-chat" target="_blank">Visual Studio Marketplace</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/utools.png">
            <h3 class="item__title">uTools 插件</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/otis1026">@otis1026</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://fishpi.cn/article/1748939764736" target="_blank">立刻尝鲜</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/chrome.png">
            <h3 class="item__title">Chrome 插件</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/lemon">@Lemon</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://github.com/Lemon-cxh/pwl-chat-extension/releases" target="_blank">Github Releases</a>
            </div>
            <div class="fn__flex">
                <a class="download__link" href="https://microsoftedge.microsoft.com/addons/detail/%E6%91%B8%E9%B1%BC%E6%B4%BE%E8%81%8A%E5%A4%A9%E5%AE%A4/oldbilakhdpiamjbkocdcdnlnakainfm" target="_blank">Microsoft Edge Addons</a>
            </div>
            <div class="fn__flex">
                <a class="download__link" href="https://chrome.google.com/webstore/detail/%E6%91%B8%E9%B1%BC%E6%B4%BE%E8%81%8A%E5%A4%A9%E5%AE%A4/fkaomdjjdbglkbcmfhhlioejkpacbbpe" target="_blank">Chrome 网上应用店</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/idea.jpg">
            <h3 class="item__title">IDEA 插件</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/Danbai">@Danbai</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://github.com/danbai225/pwl-chat/releases" target="_blank">Github Releases</a>
            </div>
            <div class="fn__flex">
                <a class="download__link" href="https://plugins.jetbrains.com/plugin/18091-pwl-chat" target="_blank">Intellij IDEs Plugin | Marketplace</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/python.png">
            <h3 class="item__title">Python 客户端</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/Gakkiyomi">@Gakkiyomi</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://github.com/gakkiyomi/pwl-chat-python" target="_blank">Github</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/Golang.png">
            <h3 class="item__title">Golang 分布式客户端</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/bulabula">@bulabula</a></div>
            <div class="fn__flex">
                <a class="download__link" href="https://github.com/New-arkssac/fishpi-Goclient" target="_blank">Github</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/Golang.png">
            <h3 class="item__title">Golang 终端客户端</h3>
            <div style="flex:1">Author:<a href="${staticServePath}/member/8888">@8888</a></div>
            <div class="fn__flex">
                <a class="download__link" href=" https://github.com/fghwett/fishpi-golang/releases/" target="_blank">Github Releases</a>
            </div>
        </div>
        <div class="item">
            <img src="${staticServePath}/images/clients/code.png">
            <h3 class="item__title">更多平台开发中...</h3>
            <div class="fn__flex">
                敬请期待
            </div>
        </div>
    </div>
</div>
<#include "footer.ftl">
</body>
</html>
