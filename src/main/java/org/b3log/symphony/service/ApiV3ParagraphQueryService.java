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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.util.ApiV3ParagraphDto;
import org.b3log.symphony.util.ApiV3CommentLocationQueries;
import org.b3log.symphony.util.ApiV3ParagraphQueries;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;

@Service
public class ApiV3ParagraphQueryService {
    @Inject private ApiV3ContentQueryService content;
    @Inject private LongArticleParagraphService paragraphs;
    @Inject private CommentRepository comments;

    public JSONObject paragraphs(final String articleId, final JSONObject viewer, final int p, final int size,
                                 final boolean includeContent) throws Exception {
        final JSONObject article = longArticle(articleId, viewer);
        final var rendered = compatible(article);
        final var all = rendered.paragraphs();
        final long start = (long) (p - 1) * size;
        final var list = new JSONArray();
        if (start < all.size()) {
            final var page = all.subList((int) start, (int) Math.min(start + size, all.size()));
            final var query = ApiV3ParagraphQueries.aliasCounts(comments.getName(), articleId,
                    page.stream().flatMap(paragraph -> rendered.idsFor(paragraph.getId()).stream()).distinct().toList());
            final var counts = new HashMap<String, Long>();
            for (final var row : comments.select(query.sql(), query.args().toArray())) {
                counts.merge(rendered.canonicalId(row.optString(Comment.COMMENT_PARAGRAPH_ID)), row.optLong("commentCount"), Long::sum);
            }
            for (final var paragraph : page) list.put(ApiV3ParagraphDto.paragraph(paragraph.getId(), paragraph.getKind(),
                    paragraph.getIndex(), paragraph.getSnapshot(), false).put("commentCount", counts.getOrDefault(paragraph.getId(), 0L)));
        }
        final JSONObject result = ApiV3Requests.pagination(list, p, size, all.size());
        if (includeContent) result.put("contentHtml", rendered.html());
        return result;
    }

    public JSONObject comments(final String articleId, final JSONObject viewer, final String paragraphId, final String state,
                               final int p, final int size, final String sort) throws Exception {
        final JSONObject article = longArticle(articleId, viewer);
        final var rendered = "orphaned".equals(state) ? null : compatible(article);
        final var ids = paragraphId.isEmpty() ? java.util.List.<String>of()
                : null == rendered || rendered.idsFor(paragraphId).isEmpty() ? java.util.List.of(paragraphId) : rendered.idsFor(paragraphId);
        final var selection = ApiV3ParagraphQueries.selection(comments.getName(), articleId, ids, state);
        final long total = comments.select("SELECT COUNT(1) AS total" + selection.sql(), selection.args().toArray()).getFirst().optLong("total");
        final var query = ApiV3ParagraphQueries.page(selection, p, size, sort);
        final var list = new JSONArray();
        for (final var row : comments.select(query.sql(), query.args().toArray())) {
            list.put(ApiV3ParagraphDto.comment(content.commentDto(row, article, viewer), normalize(row, rendered)));
        }
        return ApiV3Requests.pagination(list, p, size, total);
    }

    public JSONObject comment(final String id, final JSONObject viewer) throws Exception {
        final JSONObject row = content.currentComment(id);
        if (null == row || row.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID) throw ApiV3Requests.notFound();
        final JSONObject article = content.viewableArticle(row.optString(Comment.COMMENT_ON_ARTICLE_ID), viewer);
        if (null == article) throw ApiV3Requests.notFound();
        final var rendered = row.optInt(Comment.COMMENT_TYPE) == Comment.COMMENT_TYPE_C_PARAGRAPH
                && row.optInt(Comment.COMMENT_PARAGRAPH_STATUS) == Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE
                && article.optInt(Article.ARTICLE_TYPE) == Article.ARTICLE_TYPE_C_LONG ? compatible(article) : null;
        return ApiV3ParagraphDto.comment(content.commentDto(row, article, viewer), normalize(row, rendered));
    }

    /** Metadata-only location; never returns stored comment text or an author's identity. */
    public JSONObject location(final String id, final JSONObject viewer) throws Exception {
        final JSONObject row = content.currentComment(id);
        if (row == null || row.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID) throw ApiV3Requests.notFound();
        final String articleId = row.optString(Comment.COMMENT_ON_ARTICLE_ID);
        final JSONObject article = content.viewableArticle(articleId, viewer);
        if (article == null) throw ApiV3Requests.notFound();
        final int type = row.optInt(Comment.COMMENT_TYPE, -1);
        final ApiV3ParagraphQueries.Selection selection;
        JSONObject paragraph = null;
        if (type == Comment.COMMENT_TYPE_C_ARTICLE) {
            selection = ApiV3CommentLocationQueries.ordinary(comments.getName(), articleId);
        } else if (type == Comment.COMMENT_TYPE_C_PARAGRAPH) {
            if (article.optInt(Article.ARTICLE_TYPE) != Article.ARTICLE_TYPE_C_LONG) throw ApiV3Requests.notFound();
            final int status = row.optInt(Comment.COMMENT_PARAGRAPH_STATUS, -1);
            if (status != Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE && status != Comment.COMMENT_PARAGRAPH_STATUS_C_ORPHANED) throw ApiV3Requests.notFound();
            final String raw = row.optString(Comment.COMMENT_PARAGRAPH_ID);
            if (!raw.matches("[0-9a-f]{64}")) throw ApiV3Requests.notFound();
            final boolean active = status == Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE;
            final var rendered = active ? compatible(article) : null;
            final var aliases = active ? rendered.idsFor(raw) : java.util.List.<String>of();
            final String canonical = aliases.isEmpty() ? raw : rendered.canonicalId(raw);
            final String state = active ? "active" : "orphaned";
            selection = ApiV3ParagraphQueries.selection(comments.getName(), articleId,
                    aliases.isEmpty() ? java.util.List.of(raw) : aliases, state);
            paragraph = new JSONObject().put("id", canonical).put("state", state);
        } else throw ApiV3Requests.notFound();
        final var query = ApiV3CommentLocationQueries.rank(selection, id);
        final var ranks = comments.select(query.sql(), query.args().toArray());
        if (ranks.size() != 1) throw new IllegalStateException("comment position aggregate missing");
        return new JSONObject().put("id", id).put("articleId", articleId).put("type", type)
                .put("page", ApiV3CommentLocationQueries.page(ranks.getFirst())).put("size", ApiV3CommentLocationQueries.PAGE_SIZE)
                .put("paragraph", paragraph == null ? JSONObject.NULL : paragraph);
    }

    private LongArticleParagraphService.CompatibleContent compatible(final JSONObject article) {
        return paragraphs.compatibleMarkdown(article.optString(Keys.OBJECT_ID), article.optString(Article.ARTICLE_CONTENT), content.paragraphTitleSnapshot());
    }

    private static JSONObject normalize(final JSONObject row, final LongArticleParagraphService.CompatibleContent rendered) {
        if (null == rendered || row.optInt(Comment.COMMENT_PARAGRAPH_STATUS) != Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE) return row;
        final var paragraph = rendered.find(row.optString(Comment.COMMENT_PARAGRAPH_ID));
        return null == paragraph ? row : new JSONObject(row.toString()).put(Comment.COMMENT_PARAGRAPH_ID, paragraph.getId())
                .put(Comment.COMMENT_PARAGRAPH_KIND, paragraph.getKind()).put(Comment.COMMENT_PARAGRAPH_INDEX, paragraph.getIndex());
    }

    private JSONObject longArticle(final String id, final JSONObject viewer) throws Exception {
        final JSONObject article = content.viewableArticle(id, viewer);
        if (null == article || article.optInt(Article.ARTICLE_TYPE) != Article.ARTICLE_TYPE_C_LONG) throw ApiV3Requests.notFound();
        return article;
    }
}
