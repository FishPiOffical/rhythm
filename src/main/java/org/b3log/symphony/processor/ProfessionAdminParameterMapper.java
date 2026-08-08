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

import org.b3log.latke.http.RequestContext;
import org.b3log.symphony.service.ProfessionCatalogSummaryQuery;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 职业管理接口的输入校验与转换。 */
final class ProfessionAdminParameterMapper {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE = 1_000_000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 64;

    private ProfessionAdminParameterMapper() {
    }

    static ProfessionCatalogSummaryQuery catalogQuery(final RequestContext context) {
        return new ProfessionCatalogSummaryQuery(optionalText(context, "keyword", MAX_KEYWORD_LENGTH),
                catalogStatus(context.param("status")), positiveInt(context, "page", DEFAULT_PAGE, MAX_PAGE),
                positiveInt(context, "pageSize", DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE), catalogSort(context.param("sort")));
    }

    static String id(final RequestContext context, final String key) {
        return id(context.requestJSON(), key);
    }

    static String id(final String value, final String key) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException(key + "不合法");
        }
        return value;
    }

    static String id(final JSONObject request, final String key) {
        return id(text(request, key, 19), key);
    }

    static List<String> idList(final JSONObject request, final String key, final int maximum) {
        final JSONArray values = request.optJSONArray(key);
        if (null == values || values.isEmpty() || values.length() > maximum) {
            throw new IllegalArgumentException(key + "不合法");
        }
        final List<String> result = new ArrayList<>();
        for (final Object value : values) {
            result.add(id(String.valueOf(value), key));
        }
        return List.copyOf(result);
    }

    static String text(final RequestContext context, final String key, final int maximum) {
        return text(context.requestJSON(), key, maximum);
    }

    static String text(final JSONObject request, final String key, final int maximum) {
        final String value = request.optString(key);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(key + "不合法");
        }
        return value;
    }

    private static String optionalText(final RequestContext context, final String key, final int maximum) {
        final String value = context.param(key);
        if (null == value) {
            return "";
        }
        final String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(key + "不合法");
        }
        return normalized;
    }

    private static String catalogStatus(final String status) {
        if (null == status || status.isBlank()) {
            return "";
        }
        return switch (status) {
            case "DRAFT", "PUBLISHED", "RETIRED" -> status;
            default -> throw new IllegalArgumentException("职业状态不合法");
        };
    }

    private static ProfessionCatalogSummaryQuery.Sort catalogSort(final String sort) {
        if (null == sort || sort.isBlank() || "recent".equals(sort)) {
            return ProfessionCatalogSummaryQuery.Sort.RECENT;
        }
        return switch (sort) {
            case "manual" -> ProfessionCatalogSummaryQuery.Sort.MANUAL;
            case "name" -> ProfessionCatalogSummaryQuery.Sort.NAME;
            default -> throw new IllegalArgumentException("职业排序不合法");
        };
    }

    private static int positiveInt(final RequestContext context, final String key, final int defaultValue, final int maximum) {
        final String value = context.param(key);
        if (null == value || value.isBlank()) {
            return defaultValue;
        }
        try {
            final int number = Integer.parseInt(value);
            if (number < 1 || number > maximum) {
                throw new IllegalArgumentException(key + "不合法");
            }
            return number;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(key + "不合法");
        }
    }
}
