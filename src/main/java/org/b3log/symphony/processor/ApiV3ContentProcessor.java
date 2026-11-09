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
package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3ContentQueryService;
import org.json.JSONObject;

import java.util.Set;

/** 文章和评论的只读资源。 */
@Singleton
public class ApiV3ContentProcessor {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3ContentProcessor.class);
    @Inject
    private ApiV3ContentQueryService contentQueryService;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3ContentProcessor processor = beans.getReference(ApiV3ContentProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/articles", processor::articles, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/articles/{articleId}/comments", processor::comments, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/articles/{articleId}", processor::article, auth::optionalContent, anonymous::handle);
    }

    public void articles(final RequestContext context) {
        prepare(context);
        try {
            final int p = number(context, "p", 1, Integer.MAX_VALUE / 50);
            final int size = number(context, "size", 20, 50);
            final String sort = choice(context, "sort", "latest", Set.of("latest", "active", "hot", "comments", "month-comments"));
            final String type = choice(context, "type", "recent", Set.of("recent", "long", "qna", "perfect"));
            ApiV3Processor.success(context, contentQueryService.articles(viewer(context), p, size, sort, type));
        } catch (final IllegalArgumentException e) {
            ApiV3Processor.error(context, 422, "invalid_request", "分页或筛选参数不合法");
        } catch (final Exception e) {
            unavailable(context, e);
        }
    }

    public void article(final RequestContext context) {
        prepare(context);
        try {
            final String id = articleId(context);
            final JSONObject article = contentQueryService.viewableArticle(id, viewer(context));
            if (null == article) {
                ApiV3Processor.error(context, 404, "not_found", "文章不存在或不可访问");
                return;
            }
            ApiV3Processor.success(context, contentQueryService.detail(article, viewer(context)));
        } catch (final IllegalArgumentException e) {
            ApiV3Processor.error(context, 422, "invalid_request", "文章 ID 格式不合法");
        } catch (final Exception e) {
            unavailable(context, e);
        }
    }

    public void comments(final RequestContext context) {
        prepare(context);
        try {
            final String id = articleId(context);
            final int p = number(context, "p", 1, Integer.MAX_VALUE / 50);
            final int size = number(context, "size", 20, 50);
            final String sort = choice(context, "sort", "oldest", Set.of("oldest", "latest", "hot"));
            final JSONObject article = contentQueryService.viewableArticle(id, viewer(context));
            if (null == article) {
                ApiV3Processor.error(context, 404, "not_found", "文章不存在或不可访问");
                return;
            }
            ApiV3Processor.success(context, contentQueryService.comments(article, viewer(context), p, size, sort));
        } catch (final IllegalArgumentException e) {
            ApiV3Processor.error(context, 422, "invalid_request", "文章 ID、分页或排序参数不合法");
        } catch (final Exception e) {
            unavailable(context, e);
        }
    }

    private static void prepare(final RequestContext context) {
        // 公开资源中的打赏和评论内容也可能因当前读者而异。
        context.getResponse().setHeader("Cache-Control", "no-store");
        context.getResponse().setHeader("Vary", "Authorization");
    }

    private static JSONObject viewer(final RequestContext context) {
        final JSONObject identity = (JSONObject) context.attr(ApiV3Processor.IDENTITY);
        return null == identity ? null : identity.optJSONObject("user");
    }

    private static String articleId(final RequestContext context) {
        final String id = StringUtils.trimToEmpty(context.pathVar("articleId"));
        if (!id.matches("[0-9]{1,32}")) throw new IllegalArgumentException("articleId");
        return id;
    }

    private static int number(final RequestContext context, final String key, final int fallback, final int max) {
        final String raw = context.param(key);
        if (null == raw) return fallback;
        final String value = raw.trim();
        if (!value.matches("[0-9]{1,10}")) throw new IllegalArgumentException(key);
        final int number = Integer.parseInt(value);
        if (number < 1 || number > max) throw new IllegalArgumentException(key);
        return number;
    }

    private static String choice(final RequestContext context, final String key, final String fallback, final Set<String> allowed) {
        final String raw = context.param(key);
        if (null == raw) return fallback;
        final String value = raw.trim();
        if (!allowed.contains(value)) throw new IllegalArgumentException(key);
        return value;
    }

    private static void unavailable(final RequestContext context, final Exception exception) {
        LOGGER.error("API v3 content query failed", exception);
        ApiV3Processor.error(context, 500, "internal_error", "内容服务暂不可用");
    }
}
