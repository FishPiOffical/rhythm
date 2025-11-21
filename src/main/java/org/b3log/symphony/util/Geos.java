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

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.util.Strings;
import org.b3log.symphony.model.Common;
import org.json.JSONObject;

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

/**
 * Geography utilities.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.3.0.1, Sep 1, 2018
 * @since 1.3.0
 */
public final class Geos {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(Geos.class);

    /**
     * Private constructor.
     */
    private Geos() {
    }

    /**
     * The cache of ip locates relationship.
     */
    private static final Map<String, JSONObject> ipLocatesCache = new HashMap<>();

    /**
     * Gets country, province and city of the specified IP.
     *
     * @param ip the specified IP
     * @return address info, for example      <pre>
     * {
     *     "country": "",
     *     "province": "",
     *     "city": ""
     * }
     * </pre>, returns {@code null} if not found
     */
    public static JSONObject getAddressByBaidu(String ip) {
        ip = IPUtils.getFirstIP(ip);

        if (ipLocatesCache.containsKey(ip)) {
            return ipLocatesCache.get(ip);
        }

        final String ak = Symphonys.BAIDU_LBS_AK;
        if (StringUtils.isBlank(ak) || !Strings.isIPv4(ip)) {
            return null;
        }

        HttpURLConnection conn = null;
        try {
            final URL url = new URL("http://api.map.baidu.com/location/ip?ip=" + ip + "&ak=" + ak);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            final JSONObject data = new JSONObject(IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8));
            if (0 != data.optInt("status")) {
                return null;
            }

            final String content = data.optString("address");
            final String country = content.split("\\|")[0];
            if (!"CN".equals(country) && !"HK".equals(country) && !"TW".equals(country)) {
                LOGGER.log(Level.WARN, "Found other country via Baidu [" + country + ", " + ip + "]");

                return null;
            }

            final String province = content.split("\\|")[1];
            String city = content.split("\\|")[2];
            if ("None".equals(province) || "None".equals(city)) {
                return null;
            }

            city = StringUtils.replace(city, "市", "");

            final JSONObject ret = new JSONObject();
            ret.put(Common.COUNTRY, "中国");
            ret.put(Common.PROVINCE, province);
            ret.put(Common.CITY, city);
            ipLocatesCache.put(ip, ret);

            LOGGER.log(Level.INFO, "Geolocated [ip=" + ip + ", " + ret + "]");

            return ret;
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Can't get location from Baidu [ip=" + ip + "]");

            return null;
        } finally {
            if (null != conn) {
                try {
                    conn.disconnect();
                } catch (final Exception e) {
                    LOGGER.log(Level.ERROR, "Close HTTP connection failed", e);
                }
            }
        }
    }

    public static JSONObject getAddressByGeoIP(String ip) {
        ip = IPUtils.getFirstIP(ip);
        var isLocalHost = Networks.isInnerAddress(ip);
        if (isLocalHost) {
            return null;
        }
        if (ipLocatesCache.containsKey(ip)) {
            return ipLocatesCache.get(ip);
        }
        try {
            JSONObject ret = getIpByApi(ip);
            /**try {
                GeoIPLocator geoLocator = GeoIPLocator.getInstance(Symphonys.get("geoip.config.mmdb"));
                ret = geoLocator.getLocation(ip);
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Can't get location from GeoIP [ip=" + ip + "]", e);
            }
            if (ret == null || ret.optString("city").isEmpty()) {
                JSONObject ret2 = getIpByApi(ip);
                if (ret2 != null && !ret2.optString("city").isEmpty()) {
                    ret = ret2;
                }
            }**/
            if (ret != null) {
                ipLocatesCache.put(ip, ret);
            }
            LOGGER.log(Level.INFO, "Geolocated [ip=" + ip + ", " + ret + "]");
            return ret;
        } catch (Exception e) {
            // 日志
            LOGGER.log(Level.ERROR, "Can't get location from GeoIP [ip=" + ip + "]", e);
            return null;
        }
    }

    public static JSONObject getIpByApi(String ip) {
        String url = Symphonys.get("geoip.fallback.api.url").replaceAll("IPADDR", ip);
        final HttpRequest req = HttpRequest.get(url).header(Common.USER_AGENT, Symphonys.USER_AGENT_BOT);
        final HttpResponse res = req.connectionTimeout(3000).timeout(5000).send();
        res.charset("UTF-8");
        res.close();
        if (200 != res.statusCode()) {
            return null;
        }
        JSONObject src = new JSONObject(res.bodyText()).optJSONObject("data");
        JSONObject result = new JSONObject();
        String country = src.optString("country");
        String province = src.optString("city");
        String city = src.optString("area");
        String back_province = src.optString("prov");

        if (country.isEmpty()) {
            result.put("country", "中国");
            return result;
        }

        if (province.isEmpty()) {
            result.put("country", country);
            return result;
        }

        if (city.isEmpty()) {
            if (!back_province.isEmpty()) {
                result.put("country", country);
                result.put("province", back_province);
                result.put("city", province);
            } else {
                result.put("country", country);
                result.put("province", province);
            }
            return result;
        }

        result.put("country", country);
        result.put("province", back_province);
        result.put("city", province);

        LOGGER.log(Level.INFO, "Geolocated by API [ip=" + ip + ", " + result + "]");

        return result;
    }

    /**
     * @deprecated 已弃用，接口无效
     *
     * Gets province, city of the specified IP by Taobao API.
     *
     * @param ip the specified IP
     * @return address info, for example      <pre>
     * {
     *     "province": "",
     *     "city": ""
     * }
     * </pre>, returns {@code null} if not found
     */
    private static JSONObject getAddressTaobao(final String ip) {
        HttpURLConnection conn = null;
        try {
            final URL url = new URL("http://ip.taobao.com/service/getIpInfo.php?ip=" + ip);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            final JSONObject data = new JSONObject(IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8));
            if (0 != data.optInt("code")) {
                return null;
            }

            final String country = data.optString("country");
            final String province = data.optString("region");
            String city = data.optString("city");
            city = StringUtils.replace(city, "市", "");

            final JSONObject ret = new JSONObject();
            ret.put(Common.COUNTRY, country);
            ret.put(Common.PROVINCE, province);
            ret.put(Common.CITY, city);

            return ret;
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Can't get location from Taobao [ip=" + ip + "]", e);

            return null;
        } finally {
            if (null != conn) {
                try {
                    conn.disconnect();
                } catch (final Exception e) {
                    LOGGER.log(Level.ERROR, "Close HTTP connection failed", e);
                }
            }
        }
    }
}
