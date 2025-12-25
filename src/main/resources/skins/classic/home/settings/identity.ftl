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
<#include "macro-settings.ftl">
<@home "identity">
    <div class="module">
        <div class="module-header fn-clear">
            <h2>官方身份认证</h2>
        </div>
        <div class="module-panel form fn-clear" style="margin: 0 0 50px 0">
            <div class="fn-clear" style="margin: 0 0 20px 0">
                摸鱼派是一个多元化、包容的社区，我们为在摸鱼派社区中的鱼油们提供官方身份认证服务。<br>
                一旦你通过了官方身份认证，我们将为你发放相对应的勋章（可自行选择显示/隐藏），除此以外，它还能帮助你：<br>
                <i>1. 官方认证标识，权威可信任</i><br>
                <i>2. 结识更多同好人士</i><br>
                <i>3. 允许发布特殊类型的帖子</i><br><br>
                无需担心你的隐私问题，根据<a href="https://fishpi.cn/privacy" target="_blank">摸鱼派社区隐私政策</a>，我们不会对外公布您上传的任何隐私信息。<br>
                除审核员 <a href="https://fishpi.cn/member/adlered" target="_blank">@adlered</a> 外，任何人没有查看认证数据的权限，我们同时承诺在审核后，立即销毁您提交的审核信息。
            </div>
            <div class="fn-clear" style="margin: 0 0 20px 0">
                请选择您的认证类别<br>
                <select id="id-type">
                    <option selected="selected">企业入驻认证</option>
                    <!--<option>小姐姐认证</option>
                    <option>LGBT 群体认证</option>
                    <option>00 后认证</option>-->
                </select>
            </div>
            <div id="id-content"></div>
            <button class="btn fn-right" onclick="Settings.submitIdentity()">提交审核</button>
        </div>
    </div>
</@home>
<script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
<script>
    var idCert = '';
    var idId = '';
    Settings.initIdentity();
</script>
