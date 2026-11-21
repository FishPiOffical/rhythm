/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;

/**
 * 来源分类仅影响统计，不模拟身份验证或访问放行。
 */
@Test(timeOut = 60_000)
public class ClientVisitSourcesTest {

    private static final String ANDROID = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 "
            + "Chrome/130.0.0.0 Mobile Safari/537.36 ";
    private static final String IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1 ";

    @DataProvider(name = "browsers")
    public Object[][] browsers() {
        return new Object[][]{
                {"MQQBrowser/15.0", ClientVisitSources.Source.QQ_BROWSER},
                {"QQBrowser/13.0", ClientVisitSources.Source.QQ_BROWSER},
                {"Quark/7.0", ClientVisitSources.Source.QUARK},
                {"QuarkPC/3.0", ClientVisitSources.Source.QUARK},
                {"UCBrowser/17.0", ClientVisitSources.Source.UC},
                {"UCWEB/2.0", ClientVisitSources.Source.UC},
                {"UCMobile/13.0", ClientVisitSources.Source.UC},
                {"HuaweiBrowser/15.0", ClientVisitSources.Source.HUAWEI},
                {"HonorBrowser/8.0", ClientVisitSources.Source.HONOR},
                {"MiuiBrowser/18.0", ClientVisitSources.Source.MIUI},
                {"VivoBrowser/23.0", ClientVisitSources.Source.VIVO},
                {"HeyTapBrowser/45.0", ClientVisitSources.Source.HEYTAP},
                {"YaBrowser/25.0", ClientVisitSources.Source.YANDEX},
                {"Vivaldi/7.0", ClientVisitSources.Source.VIVALDI},
                {"MQQBrowser/6.2 MicroMessenger/8.0", ClientVisitSources.Source.WECHAT},
                {"MQQBrowser/6.2 QQ/9.0.0", ClientVisitSources.Source.QQ_APP},
                {"baiduboxapp/14.0", ClientVisitSources.Source.BAIDU_APP},
                {"Edg/130.0", ClientVisitSources.Source.EDGE},
                {"Edge/18.0", ClientVisitSources.Source.EDGE},
                {"EdgA/130.0", ClientVisitSources.Source.EDGE},
                {"Firefox/130.0", ClientVisitSources.Source.FIREFOX},
                {"OPR/90.0", ClientVisitSources.Source.OPERA},
                {"Opera/9.80", ClientVisitSources.Source.OPERA},
                {"Opera Mini/36.0", ClientVisitSources.Source.OPERA},
                {"Opera Mobi/12.0", ClientVisitSources.Source.OPERA},
                {"OPiOS/8.0", ClientVisitSources.Source.OPERA},
                {"OPT/3.0", ClientVisitSources.Source.OPERA},
                {"SamsungBrowser/27.0", ClientVisitSources.Source.SAMSUNG},
                {"", ClientVisitSources.Source.CHROME}
        };
    }

    @Test(dataProvider = "browsers")
    public void specificSourceWinsBeforeChromium(final String token, final ClientVisitSources.Source source) {
        Assert.assertEquals(ClientVisitSources.detect(ANDROID + token),
                List.of(source, ClientVisitSources.Source.ANDROID));
        Assert.assertEquals(ClientVisitSources.detect((ANDROID + token).toUpperCase(java.util.Locale.ROOT)),
                List.of(source, ClientVisitSources.Source.ANDROID));
    }

    public void iosVariantsAndHostsOverrideSafari() {
        Assert.assertEquals(ClientVisitSources.detect(IOS),
                List.of(ClientVisitSources.Source.SAFARI, ClientVisitSources.Source.IPHONE));
        for (final var sample : new Object[][]{
                {"CriOS/130.0", ClientVisitSources.Source.CHROME},
                {"EdgiOS/130.0", ClientVisitSources.Source.EDGE},
                {"FxiOS/130.0", ClientVisitSources.Source.FIREFOX},
                {"QQ/9.0 MQQBrowser/6.2", ClientVisitSources.Source.QQ_APP},
                {"MicroMessenger/8.0 QQ/9.0", ClientVisitSources.Source.WECHAT},
                {"baiduboxapp/14.0", ClientVisitSources.Source.BAIDU_APP}}) {
            Assert.assertEquals(ClientVisitSources.detect(IOS + sample[0]),
                    List.of(sample[1], ClientVisitSources.Source.IPHONE));
        }
    }

    public void qqRequiresItsOwnProductToken() {
        for (final String suffix : List.of("MQQBrowser/6.2", "QQBrowser/13.0")) {
            Assert.assertEquals(ClientVisitSources.detect(ANDROID + suffix).getFirst(),
                    ClientVisitSources.Source.QQ_BROWSER);
        }
        for (final String suffix : List.of("NotQQ/9.0", "QQ/not-a-version", "https://qq/9.0")) {
            Assert.assertEquals(ClientVisitSources.detect(ANDROID + suffix).getFirst(),
                    ClientVisitSources.Source.CHROME);
        }
        Assert.assertEquals(ClientVisitSources.detect("QQ/9.0").getFirst(), ClientVisitSources.Source.QQ_APP);
    }

    public void appleDevicesAreMutuallyExclusive() {
        Assert.assertEquals(ClientVisitSources.detect(IOS.replace("iPhone", "iPad")),
                List.of(ClientVisitSources.Source.SAFARI, ClientVisitSources.Source.IPAD));
        Assert.assertEquals(ClientVisitSources.detect(IOS.replace("iPhone", "iPod")),
                List.of(ClientVisitSources.Source.SAFARI, ClientVisitSources.Source.IPHONE));
        final String mac = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/605.1.15 Version/18.0 Safari/605.1.15";
        Assert.assertEquals(ClientVisitSources.detect(mac),
                List.of(ClientVisitSources.Source.SAFARI, ClientVisitSources.Source.MACOS));
        Assert.assertFalse(ClientVisitSources.detect(mac).contains(ClientVisitSources.Source.IPAD),
                "桌面 UA 不推测成 iPad");
        Assert.assertEquals(ClientVisitSources.detect("Mac OS X 10.15 Firefox/130.0"),
                List.of(ClientVisitSources.Source.FIREFOX, ClientVisitSources.Source.MACOS));
    }

    public void emptyUnknownAndCrawlerUaDoNotInventClients() {
        for (final String ua : new String[]{null, "", "  ", "unknown", "Baiduspider/2.0",
                "YandexBot/3.0", "Applebot/0.1", "okhttp/4.0"}) {
            Assert.assertTrue(ClientVisitSources.detect(ua).isEmpty(), "UA=" + ua);
        }
        Assert.assertEquals(ClientVisitSources.detect("Chrome/130.0 Safari/537.36"),
                List.of(ClientVisitSources.Source.CHROME));
        Assert.assertEquals(ClientVisitSources.detect("Mozilla/5.0 (Windows NT 10.0) Safari/537.36"),
                List.of(ClientVisitSources.Source.SAFARI));
    }

    public void historicalKeysAndApplebotIconRemainIndependent() {
        Assert.assertEquals(ClientVisitSources.Source.SAMSUNG.key(), "browser:samsung");
        Assert.assertEquals(ClientVisitSources.Source.SAMSUNG.displayName(), "三星浏览器");
        Assert.assertEquals(ClientVisitSources.Source.IPHONE.key(), "device:iphone");
        Assert.assertEquals(ClientVisitSources.Source.IPAD.key(), "device:ipad");
        Assert.assertEquals(ClientVisitSources.Source.MACOS.key(), "device:macos");
        final var keys = new HashSet<String>();
        final var icons = new HashSet<String>();
        for (final var source : ClientVisitSources.Source.values()) {
            Assert.assertTrue(keys.add(source.key()), "重复统计 key");
            Assert.assertTrue(source.key().length() <= 32, "现有表字段上限");
        }
        for (final var device : List.of(ClientVisitSources.Source.IPHONE,
                ClientVisitSources.Source.IPAD, ClientVisitSources.Source.MACOS)) {
            Assert.assertNotEquals(device.icon(), "visitSourceApple");
            Assert.assertTrue(icons.add(device.icon()), "设备图标应各不相同");
        }
    }

    public void wecomWinsOverWechatAndEmbeddedBrowser() {
        Assert.assertEquals(ClientVisitSources.detect(ANDROID
                        + "MQQBrowser/6.2 MicroMessenger/8.0 wxwork/4.1.36"),
                List.of(ClientVisitSources.Source.WECOM, ClientVisitSources.Source.ANDROID));
        Assert.assertEquals(ClientVisitSources.detect(IOS + "MicroMessenger/8.0 WXWORK/4.1.36"),
                List.of(ClientVisitSources.Source.WECOM, ClientVisitSources.Source.IPHONE));
        Assert.assertEquals(ClientVisitSources.detect(ANDROID + "notwxwork/4.0").getFirst(),
                ClientVisitSources.Source.CHROME);
    }

    @DataProvider(name = "systems")
    public Object[][] systems() {
        return new Object[][]{
                {"Mozilla/5.0 (X11; Linux x86_64) Chrome/130.0 Safari/537.36",
                        ClientVisitSources.Source.LINUX},
                {"Mozilla/5.0 (Phone; OpenHarmony 5.0) Chrome/114.0 Safari/537.36 ArkWeb/4.1.6.1 Mobile",
                        ClientVisitSources.Source.OPENHARMONY},
                {"Mozilla/5.0 (Tablet; OpenHarmony 5.0; Linux) Chrome/114.0 Safari/537.36",
                        ClientVisitSources.Source.OPENHARMONY},
                {"Mozilla/5.0 (Linux; Android 12; HarmonyOS 4.0) Chrome/114.0 Safari/537.36",
                        ClientVisitSources.Source.HARMONYOS},
                {"Mozilla/5.0 (Phone; HarmonyOS/5.0) Chrome/114.0 Safari/537.36 ArkWeb/4.1",
                        ClientVisitSources.Source.HARMONYOS},
                {ANDROID + "HuaweiBrowser/15.0 ArkWeb/4.1", ClientVisitSources.Source.ANDROID}
        };
    }

    @Test(dataProvider = "systems")
    public void explicitSystemsExcludeGenericLinuxAndAndroid(final String ua,
                                                             final ClientVisitSources.Source system) {
        final List<ClientVisitSources.Source> sources = ClientVisitSources.detect(ua);
        Assert.assertEquals(sources.size(), 2);
        Assert.assertEquals(sources.getLast(), system);
    }

    public void engineBrandAndFalseSystemTokensDoNotProveOperatingSystem() {
        Assert.assertEquals(ClientVisitSources.detect("Chrome/130.0 ArkWeb/4.1"),
                List.of(ClientVisitSources.Source.CHROME));
        Assert.assertEquals(ClientVisitSources.detect("Chrome/130.0 NotOpenHarmony 5.0 NotLinux"),
                List.of(ClientVisitSources.Source.CHROME));
    }
}
