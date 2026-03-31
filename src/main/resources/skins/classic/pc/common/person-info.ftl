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
<#if tipsLabel??>
<div class="module">
    <div class="module-header"><h2>${smallTipLabel}</h2></div>
    <div class="module-panel">
        <ul class="module-list small-tips">
            <li>
                <span class="ft-gray">${tipsLabel}</a></span>
            </li>
        </ul>
    </div>
</div>
</#if>

<#if isLoggedIn>
<div class="module person-info" data-percent="${liveness}">
    <div class="module-panel">
        <ul class="status fn-flex">
            <li class="fn-pointer" onclick="window.location.href = '${servePath}/member/${currentUser.userName}/following/tags'">
                <strong id="ftc">${currentUser.followingTagCnt?c}</strong>
                <span class="ft-gray">${followingTagsLabel}</span>
            </li>
            <li class="fn-pointer" onclick="window.location.href = '${servePath}/member/${currentUser.userName}/following/users'">
                <strong>${currentUser.followingUserCnt?c}</strong>
                <span class="ft-gray">${followingUsersLabel}</span>
            </li>
            <li class="fn-pointer" onclick="window.location.href = '${servePath}/member/${currentUser.userName}/following/articles'">
                <strong>${currentUser.followingArticleCnt?c}</strong>
                <span class="ft-gray">${followingArticlesLabel}</span>
            </li>
            <li class="fn-pointer">
                <div id="activityProcessor" class="fn-right" style="margin-top: 6px">
                    <div class="percent-container">
                        <div class="percent-wave">
                            <div class="percent-wave-before"></div>
                            <div class="percent-wave-after"></div>
                            <div class="percent">0%</div>
                        </div>
                    </div>
                </div>
            </li>
        </ul>

        <div class="fn-clear">
            <div class="fn-right">
                <a href="${servePath}/settings/point" style="text-decoration: none" class="tooltipped tooltipped-w ft-fade"
                   aria-label="快捷转账" target="_blank">
                    <svg style="vertical-align: -2px">
                        <use xlink:href="#coin"></use>
                    </svg> ${currentUser.userPoint?c}
                </a>
            </div>
        </div>
    </div>
    <script>
        function getActivityStatus() {
            let percent = ${liveness};
            if (percent == 100) {
                Util._initActivity(percent, "#ff6515");
            } else if (percent > 80) {
                Util._initActivity(percent, "#ff930c");
            } else if (percent > 50) {
                Util._initActivity(percent, "#fdd802");
            } else if (percent > 15) {
                Util._initActivity(percent, "#587aff");
            } else {
                Util._initActivity(percent, "#5bde0f");
            }
        }

        <#--setTimeout(function () {-->
        <#--    var ccref = document.createElement('script')-->
        <#--    ccref.setAttribute("type", "text/javascript")-->
        <#--    ccref.setAttribute("src", '${staticServePath}/js/lib/circleChart.min.js')-->
        <#--    document.getElementsByTagName("head")[0].appendChild(ccref)-->
        <#--    console.log("Circle Chart loaded.")-->
        <#--}, 1000);-->
        setTimeout(function () {
            getActivityStatus();
        }, 2000);
    </script>
</div>
</#if>
