/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.b3log.symphony.processor.middleware.validate;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.http.RequestContext;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.CommentQueryService;
import org.b3log.symphony.service.LongArticleParagraphService;
import org.json.JSONObject;

/** Validates and enriches a long article paragraph comment request. */
@Singleton
public class LongArticleParagraphCommentValidationMidware {

    @Inject
    private LongArticleParagraphService paragraphService;

    @Inject
    private ArticleQueryService articleQueryService;

    @Inject
    private CommentQueryService commentQueryService;

    public void handle(final RequestContext context) {
        JSONObject request = (JSONObject) context.attr(Keys.REQUEST);
        if (null == request) {
            request = context.requestJSON();
            context.getRequest().setAttribute(Keys.REQUEST, request);
        }
        final String articleId = request.optString(Article.ARTICLE_T_ID);
        final JSONObject article = articleQueryService.getArticleById(articleId);
        if (null == article || Article.ARTICLE_TYPE_C_LONG != article.optInt(Article.ARTICLE_TYPE)) {
            reject(context, "段评仅支持长篇文章");
            return;
        }
        articleQueryService.processArticleContent(article);

        final String paragraphId = request.optString("paragraphId");
        final String originalId = request.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
        try {
            JSONObject source = null;
            if (StringUtils.isNotBlank(originalId)) {
                source = commentQueryService.getComment(originalId);
                if (null == source || !articleId.equals(source.optString(Comment.COMMENT_ON_ARTICLE_ID))
                        || Comment.COMMENT_TYPE_C_PARAGRAPH != source.optInt(Comment.COMMENT_TYPE)
                        || Comment.COMMENT_STATUS_C_VALID != source.optInt(Comment.COMMENT_STATUS)
                        || !paragraphId.equals(source.optString(Comment.COMMENT_PARAGRAPH_ID))) {
                    reject(context, "段评回复目标无效");
                    return;
                }
                request.put(Comment.COMMENT_PARAGRAPH_ID, source.optString(Comment.COMMENT_PARAGRAPH_ID));
                request.put(Comment.COMMENT_PARAGRAPH_KIND, source.optString(Comment.COMMENT_PARAGRAPH_KIND));
                request.put(Comment.COMMENT_PARAGRAPH_INDEX, source.optInt(Comment.COMMENT_PARAGRAPH_INDEX, -1));
                request.put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, source.optString(Comment.COMMENT_PARAGRAPH_SNAPSHOT));
                request.put(Comment.COMMENT_PARAGRAPH_STATUS,
                        source.optInt(Comment.COMMENT_PARAGRAPH_STATUS));
            } else {
                final LongArticleParagraphService.Paragraph paragraph = paragraphService.findRenderedParagraph(
                        articleId, article.optString(Article.ARTICLE_CONTENT), paragraphId);
                if (null == paragraph) {
                    reject(context, "段落不存在或已更新");
                    return;
                }
                request.put(Comment.COMMENT_PARAGRAPH_KIND, paragraph.getKind());
                request.put(Comment.COMMENT_PARAGRAPH_ID, paragraph.getId());
                request.put(Comment.COMMENT_PARAGRAPH_INDEX, paragraph.getIndex());
                request.put(Comment.COMMENT_PARAGRAPH_SNAPSHOT, paragraph.getSnapshot());
            }
            request.put(Comment.COMMENT_TYPE, Comment.COMMENT_TYPE_C_PARAGRAPH);
            if (!request.has(Comment.COMMENT_PARAGRAPH_STATUS)) {
                request.put(Comment.COMMENT_PARAGRAPH_STATUS, Comment.COMMENT_PARAGRAPH_STATUS_C_ACTIVE);
            }
            context.handle();
        } catch (final Exception e) {
            reject(context, "段落校验失败");
        }
    }

    private void reject(final RequestContext context, final String message) {
        context.renderJSON(new JSONObject().put(Keys.CODE, -1).put(Keys.MSG, message));
        context.abort();
    }
}
