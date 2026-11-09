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

import org.json.JSONObject;

/**
 * 历史版本响应仅返回元数据和清理后的标题、正文，省略编辑者身份。
 */
public final class ApiV3RevisionDto {
    private ApiV3RevisionDto() {
    }

    public static JSONObject metadata(final String id, final Long time, final int index) {
        return new JSONObject().put("id", id)
                .put("time", time == null ? JSONObject.NULL : time)
                .put("index", index)
                .put("current", "current".equals(id));
    }

    public static JSONObject detail(final JSONObject metadata, final JSONObject data, final boolean article) {
        final JSONObject dto = new JSONObject(metadata.toString());
        if (article)
            dto.put("title", Escapes.escapeHTML(text(data, "articleTitle")));
        final String content = text(data, article ? "articleContent" : "commentContent");
        // 清理正文时用占位标记保留换行，清理后再恢复。
        dto.put("content", Markdowns.clean(content.replace("\n", "_esc_br_"), "").replace("_esc_br_", "\n"));
        if (dto.toString().length() > 2097152)
            throw unavailable();
        return dto;
    }

    public static String text(final JSONObject data, final String field) {
        if (!(data.opt(field) instanceof String text))
            throw unavailable();
        return text;
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "历史版本暂不可用");
    }
}
