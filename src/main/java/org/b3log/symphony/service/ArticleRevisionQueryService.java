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

import org.apache.commons.lang.time.DateFormatUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Revision;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.RevisionRepository;
import org.b3log.symphony.util.Escapes;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章历史版本查询服务。
 */
@Service
public class ArticleRevisionQueryService {

    private static final String CURRENT_REVISION_ID = "current";
    private static final String REVISION_ID = "revisionId";
    private static final String REVISION_TIME = "revisionTime";
    private static final String REVISION_TIME_STR = "revisionTimeStr";
    private static final String REVISION_INDEX = "revisionIndex";
    private static final String REVISION_CURRENT = "current";
    private static final String REVISION_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Inject
    private RevisionRepository revisionRepository;

    @Inject
    private ArticleRepository articleRepository;

    /**
     * 获取文章历史版本元数据，不返回正文。
     */
    public List<JSONObject> getArticleRevisionMetas(final String articleId)
            throws RepositoryException, ServiceException {
        final JSONObject article = requireArticle(articleId);
        final List<JSONObject> revisions = getStoredRevisionMetas(articleId);
        final List<JSONObject> ret = new ArrayList<>();
        for (int i = 0; i < revisions.size(); i++) {
            ret.add(toStoredRevisionMeta(revisions.get(i), i + 1));
        }

        if (!revisions.isEmpty() && currentArticleChanged(article, revisions)) {
            ret.add(toCurrentRevisionMeta(article, ret.size() + 1));
        }
        return ret;
    }

    /**
     * 获取单个文章历史版本正文。
     */
    public JSONObject getArticleRevision(final String articleId, final String revisionId)
            throws RepositoryException, ServiceException {
        final JSONObject article = requireArticle(articleId);
        if (CURRENT_REVISION_ID.equals(revisionId)) {
            return toCurrentRevision(article);
        }

        final JSONObject revision = revisionRepository.get(revisionId);
        if (!matchesArticleRevision(revision, articleId)) {
            throw new ServiceException("历史版本不存在");
        }
        return toStoredRevision(revision);
    }

    private List<JSONObject> getStoredRevisionMetas(final String articleId) throws RepositoryException {
        return revisionRepository.getList(articleRevisionQuery(articleId).select(
                Keys.OBJECT_ID,
                Revision.REVISION_DATA_ID,
                Revision.REVISION_DATA_TYPE,
                Revision.REVISION_AUTHOR_ID));
    }

    private Query articleRevisionQuery(final String articleId) {
        return new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(Revision.REVISION_DATA_ID, FilterOperator.EQUAL, articleId),
                new PropertyFilter(Revision.REVISION_DATA_TYPE, FilterOperator.EQUAL, Revision.DATA_TYPE_C_ARTICLE)
        )).addSort(Keys.OBJECT_ID, SortDirection.ASCENDING);
    }

    private JSONObject requireArticle(final String articleId) throws RepositoryException, ServiceException {
        final JSONObject article = articleRepository.get(articleId);
        if (null == article) {
            throw new ServiceException("文章不存在");
        }
        return article;
    }

    private boolean currentArticleChanged(final JSONObject article, final List<JSONObject> revisions)
            throws RepositoryException {
        final JSONObject latestMeta = revisions.get(revisions.size() - 1);
        final JSONObject latestRevision = revisionRepository.get(latestMeta.optString(Keys.OBJECT_ID));
        final JSONObject latestData = new JSONObject(latestRevision.optString(Revision.REVISION_DATA));
        return contentChanged(latestData, article);
    }

    private boolean contentChanged(final JSONObject oldData, final JSONObject article) {
        final String oldTitle = oldData.optString(Article.ARTICLE_TITLE);
        final String newTitle = article.optString(Article.ARTICLE_TITLE);
        final String oldContent = oldData.optString(Article.ARTICLE_CONTENT);
        final String newContent = article.optString(Article.ARTICLE_CONTENT);
        return !squash(oldTitle).equals(squash(newTitle)) || !squash(oldContent).equals(squash(newContent));
    }

    private String squash(final String text) {
        return text.replaceAll("\\s+", "");
    }

    private JSONObject toStoredRevisionMeta(final JSONObject revision, final int index) {
        final String revisionId = revision.optString(Keys.OBJECT_ID);
        final long revisionTime = Long.parseLong(revisionId);
        return revisionMeta(
                revisionId,
                revisionTime,
                revision.optString(Revision.REVISION_AUTHOR_ID),
                index,
                false);
    }

    private JSONObject toCurrentRevisionMeta(final JSONObject article, final int index) {
        final long updateTime = article.optLong(Article.ARTICLE_UPDATE_TIME);
        return revisionMeta(
                CURRENT_REVISION_ID,
                updateTime,
                article.optString(Article.ARTICLE_AUTHOR_ID),
                index,
                true);
    }

    private JSONObject revisionMeta(final String id, final long time, final String authorId,
                                    final int index, final boolean current) {
        final JSONObject ret = new JSONObject();
        ret.put(REVISION_ID, id);
        ret.put(REVISION_TIME, time);
        ret.put(REVISION_TIME_STR, DateFormatUtils.format(time, REVISION_TIME_FORMAT));
        ret.put(Revision.REVISION_AUTHOR_ID, authorId);
        ret.put(REVISION_INDEX, index);
        ret.put(REVISION_CURRENT, current);
        return ret;
    }

    private boolean matchesArticleRevision(final JSONObject revision, final String articleId) {
        return null != revision
                && articleId.equals(revision.optString(Revision.REVISION_DATA_ID))
                && Revision.DATA_TYPE_C_ARTICLE == revision.optInt(Revision.REVISION_DATA_TYPE);
    }

    private JSONObject toCurrentRevision(final JSONObject article) {
        final JSONObject ret = new JSONObject();
        ret.put(REVISION_ID, CURRENT_REVISION_ID);
        ret.put(REVISION_TIME, article.optLong(Article.ARTICLE_UPDATE_TIME));
        ret.put(REVISION_TIME_STR, DateFormatUtils.format(article.optLong(Article.ARTICLE_UPDATE_TIME), REVISION_TIME_FORMAT));
        ret.put(Revision.REVISION_DATA, normalizeArticleData(article));
        return ret;
    }

    private JSONObject toStoredRevision(final JSONObject revision) {
        final JSONObject ret = new JSONObject();
        final String revisionId = revision.optString(Keys.OBJECT_ID);
        final long revisionTime = Long.parseLong(revisionId);
        ret.put(REVISION_ID, revisionId);
        ret.put(REVISION_TIME, revisionTime);
        ret.put(REVISION_TIME_STR, DateFormatUtils.format(revisionTime, REVISION_TIME_FORMAT));
        ret.put(Revision.REVISION_DATA, normalizeArticleData(new JSONObject(revision.optString(Revision.REVISION_DATA))));
        return ret;
    }

    private JSONObject normalizeArticleData(final JSONObject data) {
        final JSONObject ret = new JSONObject();
        ret.put(Article.ARTICLE_TITLE, Escapes.escapeHTML(data.optString(Article.ARTICLE_TITLE)));
        ret.put(Article.ARTICLE_CONTENT, cleanContent(data.optString(Article.ARTICLE_CONTENT)));
        return ret;
    }

    private String cleanContent(final String content) {
        String ret = content.replace("\n", "_esc_br_");
        ret = Markdowns.clean(ret, "");
        return ret.replace("_esc_br_", "\n");
    }
}
