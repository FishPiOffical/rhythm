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
package org.b3log.symphony.service;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Skin query service.
 *
 * <p>Skins are organized under {@code /skins/<theme>/<device>}, and each selectable
 * skin directory must contain a {@code skin.properties} file.</p>
 *
 * @author Codex
 * @version 0.1.0.0, Mar 31, 2026
 */
@Service
public class SkinQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(SkinQueryService.class);

    /**
     * Desktop skin device type.
     */
    public static final String DEVICE_PC = "pc";

    /**
     * Mobile skin device type.
     */
    public static final String DEVICE_MOBILE = "mobile";

    /**
     * Cached skin list.
     */
    private volatile List<JSONObject> cachedSkins;

    /**
     * Gets skins for the specified device.
     *
     * @param device the specified device
     * @return skin list
     */
    public List<JSONObject> getSkins(final String device) {
        final List<JSONObject> ret = new ArrayList<>();
        for (final JSONObject skin : getAllSkins()) {
            if (StringUtils.equals(device, skin.optString("device"))) {
                ret.add(new JSONObject(skin.toString()));
            }
        }
        ret.sort(Comparator
                .comparingInt((JSONObject skin) -> skin.optInt("order", Integer.MAX_VALUE))
                .thenComparing(skin -> skin.optString("name")));
        return ret;
    }

    /**
     * Returns whether the specified skin exists and matches the device.
     *
     * @param skinDirName the specified skin directory name
     * @param device      the specified device
     * @return {@code true} if valid, otherwise returns {@code false}
     */
    public boolean isValidSkin(final String skinDirName, final String device) {
        final String canonicalSkin = canonicalizeSkin(skinDirName, device);
        if (StringUtils.isBlank(canonicalSkin)) {
            return false;
        }

        for (final JSONObject skin : getAllSkins()) {
            if (StringUtils.equals(canonicalSkin, skin.optString("dirName"))
                    && StringUtils.equals(device, skin.optString("device"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Normalizes a skin value for the specified device.
     *
     * @param skinDirName the specified skin directory name
     * @param device      the specified device
     * @return normalized skin directory name
     */
    public String normalizeSkin(final String skinDirName, final String device) {
        final String canonicalSkin = canonicalizeSkin(skinDirName, device);
        if (isValidSkin(canonicalSkin, device)) {
            return canonicalSkin;
        }

        return getDefaultSkin(device);
    }

    /**
     * Gets default skin for the specified device.
     *
     * @param device the specified device
     * @return default skin directory name
     */
    public String getDefaultSkin(final String device) {
        return StringUtils.equals(device, DEVICE_MOBILE) ? Symphonys.MOBILE_SKIN_DIR_NAME : Symphonys.SKIN_DIR_NAME;
    }

    /**
     * Canonicalizes the specified skin value for the given device.
     *
     * <p>This keeps old stored values compatible after the directory layout changed
     * from {@code classic/mobile} + {@code classic} to {@code classic/mobile} + {@code classic/pc}.</p>
     *
     * @param skinDirName the specified skin directory name
     * @param device      the specified device
     * @return canonical skin directory name
     */
    public String canonicalizeSkin(final String skinDirName, final String device) {
        if (StringUtils.isBlank(skinDirName)) {
            return skinDirName;
        }

        if (StringUtils.equals(device, DEVICE_PC)) {
            if (StringUtils.equals(skinDirName, "classic") || StringUtils.equals(skinDirName, "classic-pc")) {
                return "classic/pc";
            }
        }

        if (StringUtils.equals(device, DEVICE_MOBILE)) {
            if (StringUtils.equals(skinDirName, "mobile") || StringUtils.equals(skinDirName, "classic-mobile")) {
                return "classic/mobile";
            }
        }

        return skinDirName;
    }

    /**
     * Gets all skins.
     *
     * @return all skins
     */
    private List<JSONObject> getAllSkins() {
        List<JSONObject> skins = cachedSkins;
        if (null != skins) {
            return skins;
        }

        synchronized (this) {
            skins = cachedSkins;
            if (null != skins) {
                return skins;
            }

            skins = loadSkins();
            cachedSkins = skins;
            return skins;
        }
    }

    /**
     * Loads skins from classpath.
     *
     * @return loaded skins
     */
    private List<JSONObject> loadSkins() {
        final Set<String> dirNames = new LinkedHashSet<>();
        final List<JSONObject> ret = new ArrayList<>();

        try {
            final Enumeration<URL> resources = SkinQueryService.class.getClassLoader().getResources("skins");
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    final Path root = Path.of(url.toURI());
                    try (final java.util.stream.Stream<Path> stream = Files.walk(root)) {
                        stream.filter(Files::isRegularFile)
                                .filter(path -> "skin.properties".equals(path.getFileName().toString()))
                                .forEach(path -> {
                                    final Path relativeDir = root.relativize(path.getParent());
                                    final String dirName = relativeDir.toString().replace('\\', '/');
                                    if (StringUtils.isNotBlank(dirName)) {
                                        dirNames.add(dirName);
                                    }
                                });
                    }
                    continue;
                }

                if ("jar".equals(url.getProtocol())) {
                    final JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jarFile = connection.getJarFile()) {
                        final Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            final JarEntry entry = entries.nextElement();
                            final String name = entry.getName();
                            if (!StringUtils.startsWith(name, "skins/") || !StringUtils.endsWith(name, "/skin.properties")) {
                                continue;
                            }

                            final String dirName = StringUtils.removeEnd(
                                    StringUtils.substringAfter(name, "skins/"), "/skin.properties");
                            if (StringUtils.isNotBlank(dirName)) {
                                dirNames.add(dirName);
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Loads skins failed", e);
        }

        for (final String dirName : dirNames) {
            final JSONObject skin = readSkin(dirName);
            if (null != skin) {
                ret.add(skin);
            }
        }

        ret.sort(Comparator
                .comparing((JSONObject skin) -> skin.optString("device"))
                .thenComparingInt(skin -> skin.optInt("order", Integer.MAX_VALUE))
                .thenComparing(skin -> skin.optString("dirName")));
        return ret;
    }

    /**
     * Reads skin metadata from the specified directory.
     *
     * @param dirName the specified directory name
     * @return skin metadata, returns {@code null} if invalid
     */
    private JSONObject readSkin(final String dirName) {
        try (InputStream inputStream = SkinQueryService.class.getClassLoader()
                .getResourceAsStream("skins/" + dirName + "/skin.properties");
             Reader reader = null == inputStream ? null : new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            if (null == inputStream) {
                return null;
            }

            final Properties properties = new Properties();
            properties.load(reader);

            final String device = inferDevice(dirName, properties.getProperty("device"));
            if (StringUtils.isBlank(device)) {
                return null;
            }

            final String themeId = inferThemeId(dirName, properties.getProperty("theme"));
            final String skinName = StringUtils.defaultIfBlank(properties.getProperty("name"), dirName);
            final JSONObject ret = new JSONObject();
            ret.put("dirName", dirName);
            ret.put("device", device);
            ret.put("themeId", themeId);
            ret.put("name", skinName);
            ret.put("memo", properties.getProperty("memo", ""));
            ret.put("previewUrl", properties.getProperty("previewUrl", ""));
            ret.put("version", properties.getProperty("version", ""));
            ret.put("order", parseInt(properties.getProperty("order"), Integer.MAX_VALUE));
            return ret;
        } catch (final Exception e) {
            LOGGER.log(Level.WARN, "Reads skin [" + dirName + "] failed", e);
            return null;
        }
    }

    /**
     * Infers device from the specified directory name or property.
     *
     * @param dirName         the specified directory name
     * @param propertyDevice  the specified device property
     * @return device
     */
    private String inferDevice(final String dirName, final String propertyDevice) {
        if (StringUtils.equals(propertyDevice, DEVICE_PC) || StringUtils.equals(propertyDevice, DEVICE_MOBILE)) {
            return propertyDevice;
        }

        final String directoryName = StringUtils.substringAfterLast(dirName, "/");
        if (StringUtils.equals(directoryName, DEVICE_PC)) {
            return DEVICE_PC;
        }
        if (StringUtils.equals(directoryName, DEVICE_MOBILE)) {
            return DEVICE_MOBILE;
        }
        return null;
    }

    /**
     * Infers theme id from the specified directory name or property.
     *
     * @param dirName        the specified directory name
     * @param propertyTheme  the specified theme property
     * @return theme id
     */
    private String inferThemeId(final String dirName, final String propertyTheme) {
        if (StringUtils.isNotBlank(propertyTheme)) {
            return propertyTheme;
        }

        final String parentDir = StringUtils.substringBeforeLast(dirName, "/");
        if (StringUtils.isNotBlank(parentDir)) {
            return parentDir;
        }
        return dirName;
    }

    /**
     * Parses integer.
     *
     * @param value        the specified value
     * @param defaultValue the specified default value
     * @return parsed integer
     */
    private int parseInt(final String value, final int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (final Exception ignored) {
            return defaultValue;
        }
    }
}
