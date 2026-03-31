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

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Templates utilities.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.2, Jun 20, 2020
 * @since 1.3.0
 */
public final class Templates {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(Templates.class);

    /**
     * FreeMarker template configurations.
     */
    private static final Configuration TEMPLATE_CFG;

    /**
     * Freemarker version.
     */
    public static final Version FREEMARKER_VER = Configuration.VERSION_2_3_30;

    static {
        TEMPLATE_CFG = new Configuration(FREEMARKER_VER);
        TEMPLATE_CFG.setDefaultEncoding("UTF-8");
        try {
            String path = Templates.class.getResource("/").getPath();
            if (StringUtils.contains(path, "/target/classes/") || StringUtils.contains(path, "/target/test-classes/")) {
                // 开发时使用源码目录
                path = StringUtils.replace(path, "/target/classes/", "/src/main/resources/");
                path = StringUtils.replace(path, "/target/test-classes/", "/src/main/resources/");
            }
            path += "skins";
            TEMPLATE_CFG.setTemplateLoader(new FallbackSkinTemplateLoader(new FileTemplateLoader(new File(path))));
            LOGGER.log(Level.INFO, "Loaded template from directory [" + path + "]");
        } catch (final Exception e) {
            TEMPLATE_CFG.setTemplateLoader(new FallbackSkinTemplateLoader(
                    new ClassTemplateLoader(Templates.class, "/skins")));
            LOGGER.log(Level.INFO, "Loaded template from classpath");
        }
        TEMPLATE_CFG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        TEMPLATE_CFG.setLogTemplateExceptions(false);
    }

    /**
     * Gets a template specified by the given name.
     *
     * @param name the given name
     * @return template
     * @throws Exception exception
     */
    public static Template getTemplate(final String name) throws Exception {
        return TEMPLATE_CFG.getTemplate(name);
    }

    /**
     * Private constructor.
     */
    private Templates() {
    }

    /**
     * Skin template loader with same-device fallback.
     *
     * <p>When a custom skin misses some templates, it falls back to the default
     * skin of the same device, such as {@code foo/pc/header.ftl -> classic/pc/header.ftl}.</p>
     */
    private static final class FallbackSkinTemplateLoader implements TemplateLoader {

        /**
         * Delegate template loader.
         */
        private final TemplateLoader delegate;

        /**
         * Constructs a fallback skin template loader.
         *
         * @param delegate the delegate loader
         */
        private FallbackSkinTemplateLoader(final TemplateLoader delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object findTemplateSource(final String name) throws java.io.IOException {
            final Object source = delegate.findTemplateSource(name);
            if (null != source) {
                return source;
            }

            for (final String fallbackName : buildFallbackNames(name)) {
                final Object fallbackSource = delegate.findTemplateSource(fallbackName);
                if (null != fallbackSource) {
                    return fallbackSource;
                }
            }

            return null;
        }

        @Override
        public long getLastModified(final Object templateSource) {
            return delegate.getLastModified(templateSource);
        }

        @Override
        public Reader getReader(final Object templateSource, final String encoding) throws java.io.IOException {
            return delegate.getReader(templateSource, encoding);
        }

        @Override
        public void closeTemplateSource(final Object templateSource) throws java.io.IOException {
            delegate.closeTemplateSource(templateSource);
        }

        /**
         * Builds fallback template names for the specified template.
         *
         * @param name the specified template name
         * @return fallback template names
         */
        private List<String> buildFallbackNames(final String name) {
            final List<String> ret = new ArrayList<>();
            final String normalizedName = StringUtils.removeStart(name, "/");
            final String[] segments = StringUtils.split(normalizedName, '/');
            if (null == segments || segments.length < 3) {
                return ret;
            }

            for (int i = 0; i < segments.length - 1; i++) {
                final String device = segments[i];
                if (!StringUtils.equals(device, "pc") && !StringUtils.equals(device, "mobile")) {
                    continue;
                }

                final String skinDir = joinSegments(segments, 0, i + 1);
                final String defaultSkinDir = StringUtils.equals(device, "mobile")
                        ? Symphonys.MOBILE_SKIN_DIR_NAME : Symphonys.SKIN_DIR_NAME;
                if (StringUtils.equals(skinDir, defaultSkinDir)) {
                    return ret;
                }

                final String relativePath = joinSegments(segments, i + 1, segments.length);
                ret.add(defaultSkinDir + "/" + relativePath);
                return ret;
            }

            return ret;
        }

        /**
         * Joins the specified path segments.
         *
         * @param segments the specified segments
         * @param start    the start index, inclusive
         * @param end      the end index, exclusive
         * @return joined path
         */
        private String joinSegments(final String[] segments, final int start, final int end) {
            final StringBuilder builder = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (builder.length() > 0) {
                    builder.append('/');
                }
                builder.append(segments[i]);
            }
            return builder.toString();
        }
    }
}
