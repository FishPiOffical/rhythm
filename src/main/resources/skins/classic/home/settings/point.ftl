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
<@home "point">

<div class="module">
    <div class="module-header">
        <h2>${pointTransferTipLabel}</h2>
        <span class="fn-right">
            <a class="ft-green" href="${servePath}/charge/point">${rechargePointLabel}</a>
        </span>
    </div>
    <div class="module-panel form fn-clear">
        <div style="font-size: 12px;color:grey;margin-bottom: 1rem;">
            注意️:转账收取1%手续费(100积分以内固定收取1积分,100积分以上收取1%,VIP4免税)
        </div>
        <input id="pointTransferUserName" type="text" placeholder="${userNameLabel}"/><br/> <br/>
        <input id="pointTransferAmount" type="number" placeholder="${amountLabel}"/> <br/><br/>
        <input id="pointTransferMemo" type="text" placeholder="${memoLabel}"/> <br/><br/>
        <div id="pointTransferTip" class="tip"></div> <br/>
        <button class="red fn-right" onclick="Settings.pointTransfer('${csrfToken}')">${confirmTransferLabel}</button>
    </div>
    <script>
        $('#pointTransferUserName').val('${pointTransferToUser}');
    </script>
</div>
</@home>