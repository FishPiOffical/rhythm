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
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.json.JSONObject;

/**
 * 在持锁期间检查评论目标，段落元数据始终取自服务端保存的内容。
 */
public final class ApiV3CommentTarget {
    private ApiV3CommentTarget() {
    }

    public record Paragraph(String id, String kind, int index, String snapshot) {
    }

    @FunctionalInterface
    public interface ParagraphLookup {
        Paragraph find(String id);
    }

    public static void prepare(final JSONObject request, final JSONObject article, final JSONObject parent,
                               final JSONObject viewer, final ParagraphLookup paragraphs) {
        final String parentId = request.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
        final String paragraphId = request.optString(Comment.COMMENT_PARAGRAPH_ID);
        if (!parentId.isEmpty()) {
            if (null == parent || !parentId.equals(parent.optString(Keys.OBJECT_ID))
                    || !article.optString(Keys.OBJECT_ID).equals(parent.optString(Comment.COMMENT_ON_ARTICLE_ID))
                    || parent.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID
                    || !ApiV3ContentUtil.canViewCommentContent(parent, article, viewer)) throw missing();
            if (parent.optInt(Comment.COMMENT_TYPE) == Comment.COMMENT_TYPE_C_PARAGRAPH) {
                if (article.optInt(Article.ARTICLE_TYPE) != Article.ARTICLE_TYPE_C_LONG
                        || (!paragraphId.isEmpty() && !paragraphId.equals(parent.optString(Comment.COMMENT_PARAGRAPH_ID))))
                    throw missing();
                // 发布流程继承完整的段落字段，其中包括孤立状态。
                return;
            }
            if (!paragraphId.isEmpty()) throw ApiV3WriteInputs.invalid();
            return;
        }
        if (paragraphId.isEmpty()) return;
        if (article.optInt(Article.ARTICLE_TYPE) != Article.ARTICLE_TYPE_C_LONG) throw missing();
        final Paragraph paragraph = paragraphs.find(paragraphId);
        if (null == paragraph || !paragraphId.equals(paragraph.id())) throw missing();
        request.put(Comment.COMMENT_TYPE, Comment.COMMENT_TYPE_C_PARAGRAPH)
                .put(Comment.COMMENT_PARAGRAPH_ID, paragraph.id()).put(Comment.COMMENT_PARAGRAPH_KIND, paragraph.kind())
                .put(Comment.COMMENT_PARAGRAPH_INDEX, paragraph.index()).put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, paragraph.snapshot())
                .put(Comment.COMMENT_PARAGRAPH_STATUS, Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE);
    }

    private static ApiV3Exception missing() {
        return new ApiV3Exception(404, "not_found", "资源不存在或不可访问");
    }
}
