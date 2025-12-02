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
    <@head title="${chargePointLabel} - ${symphonyLabel}">
    </@head>
</head>
<body>
<#include "header.ftl">
<div class="main">
    <div class="wrapper">
        <div class="content">
            <h2 class="sub-head">❤️ 捐助摸鱼派</h2>
            <div style="padding: 15px">
                <p>鱼油你好！摸鱼派是由<a href="https://github.com/Programming-With-Love" target="_blank">用爱发电开源组织</a>衍生的科技社区。我们希望构建一个属于科技爱好者们、以<b>摸鱼</b>为社区精神的综合性社区。</p>
                <p>如果你喜欢摸鱼派，欢迎你支持我们继续运营下去！我们将<b>完全用于摸鱼派的社区运营</b> :)</p><br>
                <div style="text-align: center">
                    <input id="doMoney" style="display: inline; width: 20%" type="text" placeholder="捐助金额">
                    <input id="doNote" style="display: inline; width: 70%" type="text" placeholder="捐助附言，最多32字">
                </div>
                <div style="text-align: right; margin-top: 15px">
                    <button onclick="doWeChat()"><svg style="vertical-align: -2px;color: #44B549"><use xlink:href="#wechat"></use></svg> 使用微信捐助</button>
                    <!--<button onclick="doAlipay()"><svg style="vertical-align: -2px;"><use xlink:href="#alipay"></use></svg> 使用支付宝捐助</button>-->
                </div>
            </div>
            <script>
                function doWeChat() {
                    let doMoney = $("#doMoney").val();
                    let doNote = $("#doNote").val();
                    if (doMoney === "" || doNote === "") {
                        Util.alert("请填写捐助金额和捐助附言 :)");
                    } else if (isNaN(doMoney) || doMoney < 1) {
                        Util.alert("金额不合法！捐助需要大于1❤️");
                    } else {
                        $.ajax({
                            url: "${servePath}/pay/wechat?total_amount=" + doMoney + "&note=" + doNote,
                            type: "GET",
                            async: false,
                            success: function (data) {
                                let url = data.QRcode_url;
                                Util.alert("" +
                                    "<div><img src='" + url + "' height='200' width='200'></div>" +
                                    "<div style='padding-top: 10px'><svg style='vertical-align: -2px;color: #44B549'><use xlink:href='#wechat'></use></svg> 请使用微信扫码支付</div>" +
                                    "<div style='padding-top: 30px'><button class='btn green' onclick='javascript:location.reload()'>我已完成支付</button></div>")
                            }
                        });
                    }
                }

                function doAlipay() {
                    let doMoney = $("#doMoney").val();
                    let doNote = $("#doNote").val();
                    if (doMoney === "" || doNote === "") {
                        Util.alert("请填写捐助金额和捐助附言 :)");
                    } else if (isNaN(doMoney)) {
                        Util.alert("金额不合法！");
                    } else {
                        location.href = "${servePath}/pay/alipay?total_amount=" + doMoney + "&note=" + doNote + "&subject_type=001";
                    }
                }
            </script>
            <#if isSponsor>
                <h2 class="sub-head"><span class="ft-red">✨</span> 您的捐助信息</h2>
                <div style="padding: 15px 50px">
                    <div class="TGIF__item" style="display: flex; justify-content: center">
                        <div style="text-align: center">
                            亲爱的鱼油，感谢你对摸鱼派的支持与喜爱 ❤️
                            <br><br>
                            已累计捐助：<b>${donateTimes} 次</b><br>
                            总捐助数额：<b>${donateCount} ❤️</b><br>
                            为社区运营续航：<b>${donateMakeDays} 天</b>
                            <br><br>
                            <#list donateList as donate>
                                <p style="margin-bottom: 5px" class="tooltipped tooltipped-e" aria-label="${donate.message}" ><span class="count">🧧 ${donate.date} ${donate.time} ${donate.amount} ❤️</span></p>
                            </#list>
                        </div>
                    </div>
                </div>
            </#if>
            <h2 class="sub-head"><span class="ft-red">🤗</span> 捐助称号回馈</h2>
            <div style="padding: 15px">
                <div style="padding-bottom: 15px"></div>
                <div class="TGIF__item" style="display: flex; justify-content: center">
                    <div>
                        <img src="https://fishpi.cn/gen?ver=0.1&scale=0.79&txt=%E6%91%B8%E9%B1%BC%E6%B4%BE%E7%B2%89%E4%B8%9D&url=https://file.fishpi.cn/2021/12/ht1-d8149de4.jpg&backcolor=ffffff&fontcolor=ff3030" />
                        &nbsp;&nbsp;
                        <b style="line-height: 25px">16 ❤️</b>
                        <br>
                        <img src="https://fishpi.cn/gen?ver=0.1&scale=0.79&txt=%E6%91%B8%E9%B1%BC%E6%B4%BE%E5%BF%A0%E7%B2%89&url=https://file.fishpi.cn/2021/12/ht2-bea67b29.jpg&backcolor=87cefa&fontcolor=efffff" />
                        &nbsp;&nbsp;
                        <b style="line-height: 25px">256 ❤️</b>
                        <br>
                        <img src="https://fishpi.cn/gen?ver=0.1&scale=0.79&txt=%E6%91%B8%E9%B1%BC%E6%B4%BE%E9%93%81%E7%B2%89&url=https://file.fishpi.cn/2021/12/ht3-b97ea102.jpg&backcolor=ee3a8c&fontcolor=ffffff" />
                        &nbsp;&nbsp;
                        <b style="line-height: 25px">1024 ❤️</b>
                        <br>
                        <img src="https://fishpi.cn/gen?ver=0.1&scale=0.79&txt=Premium Sponsor&url=https%3A%2F%2Ffile.fishpi.cn%2F2025%2F11%2Flovegif1762236700451-aea18b9a.gif&scale=0.79&backcolor=ff69b4%2Cffff1c%2C00c3ff&fontcolor=ff009e&way=left&anime=3&fontsize=19&barlen=141&font=T1RUTwAJAIAAAwAQQ0ZGIMFtpe4AAATYAAAFBE9TLzJqLmPvAAABAAAAAGBjbWFwAwsCpwAABDQAAACEaGVhZCz8cjkAAACcAAAANmhoZWEDKgIhAAAA1AAAACRobXR4FM8AAAAACdwAAAAwbWF4cAAMUAAAAAD4AAAABm5hbWXFrGfkAAABYAAAAtNwb3N0AAMAAAAABLgAAAAgAAEAAAABAAB0K1g8Xw889QADA%2BgAAAAA5VSXVgAAAADlVJdW%2F7H%2FjgOLAx8AAAADAAIAAAAAAAAAAQAAAyj%2FCQAAAwsAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAwAAFAAAAwAAAADAbwB9AAFAAACigK7AAAAjAKKArsAAAHfADEBAgAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAABYWFhYAEAAIAB1Ayj%2FCQAAAx8AcgAAAAEAAAAAAZQDHwAgACAAAAAAACIBngABAAAAAAAAAAEAUQABAAAAAAABAA8AAAABAAAAAAACAAcALQABAAAAAAADABkA6gABAAAAAAAEABcAQgABAAAAAAAFAAsAyQABAAAAAAAGABYAhwABAAAAAAAHAAEAUQABAAAAAAAIAAEAUQABAAAAAAAJAAEAUQABAAAAAAAKAAEAUQABAAAAAAALAAEAUQABAAAAAAAMAAEAUQABAAAAAAANAAEAUQABAAAAAAAOAAEAUQABAAAAAAAQAA8AAAABAAAAAAARAAcALQADAAEECQAAAAIAdwADAAEECQABAB4ADwADAAEECQACAA4ANAADAAEECQADADIBAwADAAEECQAEAC4AWQADAAEECQAFABYA1AADAAEECQAGACwAnQADAAEECQAHAAIAdwADAAEECQAIAAIAdwADAAEECQAJAAIAdwADAAEECQAKAAIAdwADAAEECQALAAIAdwADAAEECQAMAAIAdwADAAEECQANAAIAdwADAAEECQAOAAIAdwADAAEECQAQAB4ADwADAAEECQARAA4ANE1hbHZpZGVzLXN1YnNldABNAGEAbAB2AGkAZABlAHMALQBzAHUAYgBzAGUAdFJlZ3VsYXIAUgBlAGcAdQBsAGEAck1hbHZpZGVzLXN1YnNldCBSZWd1bGFyAE0AYQBsAHYAaQBkAGUAcwAtAHMAdQBiAHMAZQB0ACAAUgBlAGcAdQBsAGEAck1hbHZpZGVzLXN1YnNldFJlZ3VsYXIATQBhAGwAdgBpAGQAZQBzAC0AcwB1AGIAcwBlAHQAUgBlAGcAdQBsAGEAclZlcnNpb24gMC4xAFYAZQByAHMAaQBvAG4AIAAwAC4AMSA6TWFsdmlkZXMtc3Vic2V0IFJlZ3VsYXIAIAA6AE0AYQBsAHYAaQBkAGUAcwAtAHMAdQBiAHMAZQB0ACAAUgBlAGcAdQBsAGEAcgAAAAABAAMAAQAAAAwABAB4AAAAGgAQAAMACgAgAFAAUwBlAGkAbQBuAG8AcAByAHMAdf%2F%2FAAAAIABQAFMAZQBpAG0AbgBvAHAAcgBzAHX%2F%2F%2F%2Fg%2F7H%2Fr%2F%2Be%2F5v%2FmP%2BY%2F5j%2FmP%2BX%2F5f%2FlgABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEABAEAAQEBF01hbHZpZGVzLXN1YnNldFJlZ3VsYXIAAQEBKPgbAPgcAvgdA%2FgeBIv7Bvm8%2BZ8FHQAAAKMPHQAAALoRix0AAAUEEgAPAQEMIzI5Ojs8PT4%2FQEFCQ0RWZXJzaW9uIDAuMU1hbHZpZGVzLXN1YnNldCBSZWd1bGFyTWFsdmlkZXMtc3Vic2V0UmVndWxhclBTZWltbm9wcnN1AAAAAYsBjAGNAY4BjwGQAZEBkgGTAZQBlQAMAgABAAQAfgDxATkBggH5AloCsgMeA3MDzQQu9%2FgO%2BZ%2F33feOFZKylrOVsgiUs8z3h1GFCH6KgYGGgAhrSln7jnI1CIBmWfthypYIs5Kk9x%2BSrwj3Jrf3gMr3FfdICMXbit1Qwwj7FfcN%2FBiK%2Bzj7OgglJPdC%2BwawzgiZpVW2hKwI93j3QfhxT%2FsY%2B1cILfsg%2B1BS%2BzhUCA75N%2Fdt1hWFgQVd%2B0f4Dfcf5PdMCPcP95P8avsgI7kIcZeLnKClCOL3A%2Fee9y73EG4IT1BUeUNpCFhzk1Tgnwjrofd%2F9yX7CuUIc5xqjGmJCPvOffwe%2FA%2F3az8IznT3Zbn1gQjjg25VQ1QIOEz7Hl1IhAgO997v918V0ZXLuLbHCOj3FfuejEH7Zwh2T6BRxHwI3HX3Mu3KzQidnpGoZnkITW37cvsak%2BYIkcEVm7u%2Ftr2ICHtgTmpifwgO96b35feXFZqff5J6fggoSvs8%2Bxzg94wIlap8mHaMCF6MafsXjGsIi1%2Bga62ACN9w5ODT4Aj7efdMFYBazZGUvwiTtkyEgl4IDvjo%2BR73oBVMSkJNkfYIjKeNp4qmCIjVXJtGWghgbW5kaWUIl%2FcKXLT7Bi4Ii56JtHSHCFCBNPur4JUIxpKS5LXMCK%2FGrpl%2FRAiCU4dFuIoIroqv17fECLnIq56BOwiGZYNhlWIIqvsH90f3JJ%2FaCI%2Bef4x%2BfAgO%2BDj4eveMFZ2gh6hrbghVW2t3d7sIfLSbwGq7CFrQMUNDPAiYxgWUtWiRdWMIdGE1%2B3LgkgifjZyelJ0Il6SVoZWgCLft5%2FGS%2BzwIjGqOaZV1CJlurYKunQi2oq%2BwqLIIDvg399f3rRWf0WWfLoMIeoqGm3iMCIOLg4qDiAg4alr7CKNRCLEy9yio2%2FcMCMiYtZPIkQijjZikbI0IU49kiVKGCPs6tRWCZK9fxYoI%2B0T7H3%2F3PPLCCA73%2F%2Fg%2B944VoJiQq2J0CEVj%2BwFueJAIsp3lwZzMCJvHYJ5VhAhghWFyZm0IibJipHNaCFsoZPtRhTMIiWiZfJ6LCJ2LoJaWwAiXwZe%2FmcAIxXT3Jaz3ANMI%2Bxv1Fb97%2Bw03MmsIicbz3MODCA73tvcQ%2BAAVhI95k4KJCHuHh3iJfwh%2BU2oti1YIjG63iJmuCJmwkdClugijtqaVm2cIkICRgJKCCKJsvpicsAiUn4COe5MIhI%2BJkIaUCGnRgKwlNggO9%2BX3ove0FW2PbItrjQh3jGmLop4IqKO7mraLCKuLooWVqQir9wD7iztSLwh5bZJvp4EIrH%2B9krWKCMOJnXFEeAhUfEeSeW0IfnOkc5%2BICPdab%2FP3VfsTnwgO%2BHz3v%2FemFbn7QtCo9xv3Dwiko3uacnkIV2dVXGS6CHakia%2BJrAiJq36YanQIbHdvdHB1CCo%2BWWnU9ysImaiMpn%2BTCFmqQfsfhzAIiVOndrGPCMWRvMK8uwicm5qanJkIDgFkAAADCwAAAqMAAAFKAAABEgAAAlQAAAGkAAABowAAAWsAAAEiAAABUQAAAegAAA%3D%3D" />
                        &nbsp;&nbsp;
                        <b style="line-height: 25px">4096 ❤️</b>
                    </div>
                </div>
            </div>
            <h2 class="sub-head">🙏 不胜感激</h2>
            <style>
                .fn__space5 {
                    width: 5px;
                    display: inline-block;
                }
                .ft__gray {
                    color: var(--text-gray-color);
                }
                .fn__flex-1 {
                    flex: 1;
                    min-width: 1px;
                }
                .ft__original7 {
                    color: #569e3d;
                }
                .list>ul>li {
                    padding: 15px;
                }
            </style>
            <div class="list">
                <ul>
                    <#list sponsors as sponsor>
                        <li class="fn__flex">
                            <div class="ft-nowrap">
                                ${sponsor.date}<br>
                                <span class="ft-gray">${sponsor.time}</span>
                            </div>
                            <span class="fn__space5"></span>
                            <span class="fn__space5"></span>
                            <div class="ft__gray fn__flex-1">
                                ${sponsor.message}
                            </div>
                            <span class="fn__space5"></span>
                            <span class="fn__space5"></span>
                            <b class="ft__original7" style="width: 90px">${sponsor.amount}</b>
                            <div class="ft__gray" style="width: 70px;text-align: right">
                                <a href="${servePath}/member/${sponsor.userName}" class="tooltipped__user">${sponsor.userName}</a>
                            </div>
                        </li>
                    </#list>
                </ul>
           </div>
        </div>
        <div class="fn-hr10"></div>
        <div class="side">
            <#include "side.ftl">
        </div>
    </div>
</div>
<#include "footer.ftl">
</body>
</html>
