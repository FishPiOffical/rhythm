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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.ArticleDraft;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.repository.ArticleDraftRepository;
import org.json.JSONObject;

import java.util.List;

/**
 * Article draft management service.
 */
@Service
public class ArticleDraftMgmtService {

    private static final int FETCH_SIZE = 50;

    @Inject
    private ArticleDraftRepository articleDraftRepository;

    public JSONObject saveDraft(final String userId, final JSONObject request) throws ServiceException {
        final JSONObject values = buildDraft(userId, request);
        final Transaction transaction = articleDraftRepository.beginTransaction();
        try {
            final String draftId = StringUtils.trimToEmpty(request.optString(ArticleDraft.ARTICLE_DRAFT_ID));
            final JSONObject draft = loadWritableDraft(userId, draftId, values);
            final String id = draft.optString(Keys.OBJECT_ID);
            if (StringUtils.isBlank(id)) {
                final String newId = articleDraftRepository.add(draft);
                draft.put(Keys.OBJECT_ID, newId);
            } else {
                articleDraftRepository.update(id, draft);
            }
            transaction.commit();
            return toListItem(draft);
        } catch (final ServiceException e) {
            rollback(transaction);
            throw e;
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw new ServiceException(e);
        }
    }

    public List<JSONObject> listDrafts(final String userId) throws ServiceException {
        try {
            final List<JSONObject> drafts = articleDraftRepository.getUserDrafts(userId, FETCH_SIZE);
            for (final JSONObject draft : drafts) {
                trimForList(draft);
            }
            return drafts;
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    public JSONObject getDraft(final String userId, final String draftId) throws ServiceException {
        try {
            final JSONObject draft = articleDraftRepository.getOwnedDraft(userId, draftId);
            if (null == draft) {
                throw new ServiceException("草稿不存在");
            }
            return draft;
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }
    }

    public void removeDraft(final String userId, final String draftId) throws ServiceException {
        final Transaction transaction = articleDraftRepository.beginTransaction();
        try {
            final JSONObject draft = articleDraftRepository.getOwnedDraft(userId, draftId);
            if (null == draft) {
                throw new ServiceException("草稿不存在");
            }
            articleDraftRepository.remove(draft.optString(Keys.OBJECT_ID));
            transaction.commit();
        } catch (final ServiceException e) {
            rollback(transaction);
            throw e;
        } catch (final RepositoryException e) {
            rollback(transaction);
            throw new ServiceException(e);
        }
    }

    private JSONObject loadWritableDraft(final String userId, final String draftId,
                                         final JSONObject values) throws RepositoryException, ServiceException {
        if (StringUtils.isBlank(draftId)) {
            return values;
        }
        final JSONObject draft = articleDraftRepository.getOwnedDraft(userId, draftId);
        if (null == draft) {
            throw new ServiceException("草稿不存在");
        }
        values.put(Keys.OBJECT_ID, draft.optString(Keys.OBJECT_ID));
        values.put(ArticleDraft.CREATED_TIME, draft.optLong(ArticleDraft.CREATED_TIME));
        return values;
    }

    private JSONObject buildDraft(final String userId, final JSONObject request) throws ServiceException {
        final long now = System.currentTimeMillis();
        final JSONObject draft = new JSONObject();
        draft.put(ArticleDraft.USER_ID, userId);
        draft.put(ArticleDraft.TITLE, limitTrimmed(request.optString(Article.ARTICLE_TITLE), ArticleDraft.MAX_TITLE_LENGTH, "标题"));
        draft.put(ArticleDraft.CONTENT, limitRaw(request.optString(Article.ARTICLE_CONTENT), ArticleDraft.MAX_CONTENT_LENGTH, "正文"));
        draft.put(ArticleDraft.THOUGHT_CONTENT, limitRaw(request.optString(ArticleDraft.THOUGHT_CONTENT),
                ArticleDraft.MAX_THOUGHT_CONTENT_LENGTH, "思绪记录"));
        draft.put(ArticleDraft.TAGS, limitTrimmed(request.optString(Article.ARTICLE_TAGS), ArticleDraft.MAX_TAGS_LENGTH, "标签"));
        draft.put(ArticleDraft.ARTICLE_TYPE, normalizeArticleType(request.optInt(Article.ARTICLE_TYPE)));
        draft.put(ArticleDraft.COLUMN_ID, limitTrimmed(request.optString(LongArticleColumn.COLUMN_ID),
                ArticleDraft.MAX_COLUMN_ID_LENGTH, "专栏"));
        draft.put(ArticleDraft.COLUMN_TITLE, limitTrimmed(request.optString(LongArticleColumn.COLUMN_TITLE),
                LongArticleColumn.MAX_COLUMN_TITLE_LENGTH, "专栏名称"));
        draft.put(ArticleDraft.CHAPTER_NO, limitTrimmed(request.optString(LongArticleColumn.CHAPTER_NO),
                ArticleDraft.MAX_CHAPTER_NO_LENGTH, "章节号"));
        draft.put(ArticleDraft.REWARD_CONTENT, limitRaw(request.optString(Article.ARTICLE_REWARD_CONTENT),
                ArticleDraft.MAX_REWARD_CONTENT_LENGTH, "打赏区内容"));
        draft.put(ArticleDraft.REWARD_POINT, Math.max(0, request.optInt(Article.ARTICLE_REWARD_POINT)));
        draft.put(ArticleDraft.QNA_OFFER_POINT, Math.max(0, request.optInt(Article.ARTICLE_QNA_OFFER_POINT)));
        draft.put(ArticleDraft.COMMENTABLE, request.optBoolean(Article.ARTICLE_COMMENTABLE, true));
        draft.put(ArticleDraft.ANONYMOUS, request.optBoolean(Article.ARTICLE_ANONYMOUS, false));
        draft.put(ArticleDraft.NOTIFY_FOLLOWERS, request.optBoolean(Article.ARTICLE_T_NOTIFY_FOLLOWERS, false));
        draft.put(ArticleDraft.SHOW_IN_LIST, request.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES));
        draft.put(ArticleDraft.STATEMENT, request.optInt(Article.ARTICLE_STATEMENT, 0));
        draft.put(ArticleDraft.SUMMARY, buildSummary(draft.optString(ArticleDraft.CONTENT)));
        draft.put(ArticleDraft.CREATED_TIME, now);
        draft.put(ArticleDraft.UPDATED_TIME, now);
        return draft;
    }

    private int normalizeArticleType(final int articleType) {
        if (Article.isInvalidArticleType(articleType)) {
            return Article.ARTICLE_TYPE_C_NORMAL;
        }
        return articleType;
    }

    private String limitTrimmed(final String value, final int maxLength, final String field) throws ServiceException {
        final String ret = StringUtils.trimToEmpty(value);
        return limitRaw(ret, maxLength, field);
    }

    private String limitRaw(final String value, final int maxLength, final String field) throws ServiceException {
        final String ret = null == value ? "" : value;
        if (ret.length() > maxLength) {
            throw new ServiceException(field + "长度不能超过 " + maxLength + " 个字符");
        }
        return ret;
    }

    private String buildSummary(final String content) {
        final String normalized = StringUtils.trimToEmpty(content).replaceAll("\\s+", " ");
        if (normalized.length() <= ArticleDraft.MAX_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, ArticleDraft.MAX_SUMMARY_LENGTH);
    }

    private JSONObject toListItem(final JSONObject draft) {
        final JSONObject ret = new JSONObject(draft.toString());
        trimForList(ret);
        return ret;
    }

    private void trimForList(final JSONObject draft) {
        draft.remove(ArticleDraft.CONTENT);
        draft.remove(ArticleDraft.THOUGHT_CONTENT);
        draft.remove(ArticleDraft.REWARD_CONTENT);
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
