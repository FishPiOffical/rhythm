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
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Stopwatchs;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.repository.ArticleRepository;
import org.json.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short link query service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 1.2.3.3, Nov 1, 2019
 * @since 1.3.0
 */
@Service
public class ShortLinkQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ShortLinkQueryService.class);

    /**
     * Article pattern - full.
     */
    private static final String ARTICLE_SOURCE_BASE = Latkes.getServePath();
    private static final Pattern ARTICLE_PATTERN_FULL = Pattern.compile(ARTICLE_SOURCE_BASE + "/article/\\d{13,15}[?\\w&-_=#%:]*(\\b|$)");

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    /**
     * Processes article short link (article id).
     *
     * @param content the specified content
     * @return processed content
     */
    public String linkArticle(final String content) {
        return linkArticle(content, null);
    }

    /** A non-null lookup supplies only titles the caller is authorized to expose; null leaves the URL intact. */
    public String linkArticle(final String content, final java.util.function.Function<String, String> titleLookup) {
        return linkArticle(content, titleLookup == null ? null : (id, url) -> titleLookup.apply(id), false);
    }

    /** Paired renderers retain the original destination while choosing a public label. */
    public String linkArticleWithUrlTitles(final String content,
            final java.util.function.BiFunction<String, String, String> titleLookup) {
        return linkArticle(content, titleLookup, true);
    }

    private String linkArticle(final String content,
            final java.util.function.BiFunction<String, String, String> titleLookup, final boolean preserveUrl) {
        Stopwatchs.start("Link article");

        StringBuffer contentBuilder = new StringBuffer();
        try {
            Matcher matcher = ARTICLE_PATTERN_FULL.matcher(content);
            final String[] codeBlocks = StringUtils.substringsBetween(content, "```", "```");
            String codes = "";
            if (null != codeBlocks) {
                codes = String.join("", codeBlocks);
            }
            try {
                while (matcher.find()) {
                    final String url = StringUtils.trim(matcher.group());
                    if (preserveUrl && !url.startsWith(ARTICLE_SOURCE_BASE + "/article/")) continue;
                    if (0 < matcher.start()) {
                        final char c = content.charAt(matcher.start() - 1); // look back one char
                        if ('(' == c || ']' == c || '\'' == c || '"' == c || '`' == c) {
                            continue;
                        }
                    }

                    if (StringUtils.containsIgnoreCase(codes, url)) {
                        continue;
                    }
                    String linkId;
                    String queryStr = null;
                    String anchor = null;
                    if (StringUtils.contains(url, "?")) {
                        linkId = StringUtils.substringBetween(url, "/article/", "?");
                        queryStr = StringUtils.substringAfter(url, "?");
                    } else {
                        linkId = StringUtils.substringAfter(url, "/article/");
                    }
                    if (StringUtils.contains(url, "#")) {
                        linkId = StringUtils.substringBefore(linkId, "#");
                        anchor = StringUtils.substringAfter(url, "#");
                    }

                    final String linkTitle;
                    if (null == titleLookup) {
                        final Query query = new Query().select(Article.ARTICLE_TITLE).setFilter(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.EQUAL, linkId));
                        final List<JSONObject> results = articleRepository.getList(query);
                        if (results.isEmpty()) continue;
                        linkTitle = results.getFirst().optString(Article.ARTICLE_TITLE);
                    } else {
                        linkTitle = titleLookup.apply(linkId, url);
                        if (null == linkTitle) continue;
                    }
                    String destination = url;
                    if (!preserveUrl) {
                        destination = Latkes.getServePath() + "/article/" + linkId;
                        if (StringUtils.isNotBlank(queryStr)) destination += "?" + queryStr;
                        if (StringUtils.isNotBlank(anchor)) destination += "#" + anchor;
                    }
                    final String link = " [" + linkTitle + "](" + destination + ") ";

                    matcher.appendReplacement(contentBuilder, Matcher.quoteReplacement(link));
                }

                matcher.appendTail(contentBuilder);
            } catch (final RepositoryException e) {
                LOGGER.log(Level.ERROR, "Generates article link failed", e);
            }

            return contentBuilder.toString();
        } finally {
            Stopwatchs.end();
        }
    }
}
