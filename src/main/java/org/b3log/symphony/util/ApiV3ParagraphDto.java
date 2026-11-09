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

import org.b3log.latke.Keys;
import org.b3log.symphony.model.Comment;
import org.json.JSONObject;

/**
 * 段落元数据使用纯文本，响应省略持久化对象。
 */
public final class ApiV3ParagraphDto {
    private ApiV3ParagraphDto() {
    }

    public static JSONObject paragraph(final String id, final String kind, final int index, final String snapshot,
                                       final boolean orphaned) {
        return new JSONObject().put("id", id).put("kind", kind).put("index", index).put("snapshot", snapshot)
                .put("state", orphaned ? "orphaned" : "active");
    }

    /**
     * safeBase 来自已按当前读者授权的 commentDto 投影。
     */
    public static JSONObject comment(final JSONObject safeBase, final JSONObject row) {
        final boolean paragraph = row.optInt(Comment.COMMENT_TYPE) == Comment.COMMENT_TYPE_C_PARAGRAPH;
        final String id = row.optString(Comment.COMMENT_PARAGRAPH_ID);
        final String anchor = paragraph && id.matches("[0-9a-f]{64}") ? id : "";
        final String permalink = "/article/" + row.optString(Comment.COMMENT_ON_ARTICLE_ID)
                + (anchor.isEmpty() ? "" : "?paragraph=" + anchor) + "#" + row.optString(Keys.OBJECT_ID);
        final JSONObject metadata = paragraph ? paragraph(anchor, row.optString(Comment.COMMENT_PARAGRAPH_KIND),
                row.optInt(Comment.COMMENT_PARAGRAPH_INDEX, -1), "",
                row.optInt(Comment.COMMENT_PARAGRAPH_STATUS) != Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE) : null;
        // 历史快照可能含自动展开的受限文章标题。
        // 当前段落正文从已授权文章的段落列表读取。
        if (null != metadata) metadata.remove("snapshot");
        return new JSONObject(safeBase.toString()).put("type", paragraph ? 1 : 0).put("permalink", permalink)
                .put("paragraph", null == metadata ? JSONObject.NULL : metadata);
    }
}
