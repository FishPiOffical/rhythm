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
package org.b3log.symphony.repository;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.cache.ArticleCache;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.util.JSONs;
import org.b3log.symphony.util.ArticleSubmissionPolicy;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.ApiV3ArticleEditorDto;
import org.b3log.symphony.util.ApiV3ArticlePolicy;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Article repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://qiankunpingtai.cn">qiankunpingtai</a>
 * @version 1.1.1.9, Jun 6, 2019
 * @since 0.2.0
 */
@Repository
public class ArticleRepository extends AbstractRepository {

    /**
     * Article cache.
     */
    @Inject
    private ArticleCache articleCache;

    @Inject private ApiV3ArticleRevisionRepository realtimeRevisions;

    /**
     * Public constructor.
     */
    public ArticleRepository() {
        super(Article.ARTICLE);
    }

    @Override
    public String add(final JSONObject article) throws RepositoryException {
        requireWriteTransaction();
        final String id = insertArticle(toWritableArticle(article));
        realtimeRevisions.touchInCurrentTransaction(id);
        return id;
    }
    protected String insertArticle(final JSONObject row) throws RepositoryException { return super.add(row); }
    protected void updateArticle(final String id, final JSONObject row, final String... fields) throws RepositoryException { super.update(id,row,fields); }
    protected void removeArticle(final String id) throws RepositoryException { super.remove(id); }
    private void requireWriteTransaction() { if (!hasTransactionBegun()) throw new IllegalStateException("article transaction required"); }

    @Override
    public void remove(final String id) throws RepositoryException {
        requireWriteTransaction();
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("removal transaction required");
            ArticlePublicationCaches.afterTransaction("article:" + id, () -> articleCache.removeArticle(id));
            removeArticle(id);
            realtimeRevisions.touchInCurrentTransaction(id);
            return;
        }
        removeArticle(id);
        realtimeRevisions.touchInCurrentTransaction(id);
        articleCache.removeArticle(id);
    }

    @Override
    public JSONObject get(final String id) throws RepositoryException {
        if (ArticlePublicationCaches.active()) return getForUpdate(id);
        JSONObject ret = articleCache.getArticle(id);
        if (null != ret) {
            return ret;
        }

        ret = super.get(id);
        if (null == ret) {
            return null;
        }

        articleCache.putArticle(ret);

        return ret;
    }

    @Override
    public void update(final String id, final JSONObject article, final String... propertyNames) throws RepositoryException {
        requireWriteTransaction();
        ensureWritableProperties(propertyNames);
        final JSONObject writableArticle = toWritableArticle(article);
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("article transaction required");
            ArticlePublicationCaches.afterTransaction("article:" + id, () -> {
                articleCache.removeArticle(id);
                try { get(id); } catch (final RepositoryException e) { /* Refill on a later read. */ }
            });
            updateArticle(id, writableArticle, propertyNames);
            realtimeRevisions.touchInCurrentTransaction(id);
            return;
        }
        updateArticle(id, writableArticle, propertyNames);
        realtimeRevisions.touchInCurrentTransaction(id);

        writableArticle.put(Keys.OBJECT_ID, id);
        articleCache.putArticle(writableArticle);
    }

    private JSONObject toWritableArticle(final JSONObject article) {
        if (null == article) {
            return null;
        }

        final JSONObject ret = JSONs.clone(article);
        ret.remove(Article.ARTICLE_HOT_SCORE);
        return ret;
    }

    private void ensureWritableProperties(final String... propertyNames) throws RepositoryException {
        for (final String propertyName : propertyNames) {
            if (Article.ARTICLE_HOT_SCORE.equals(propertyName)) {
                throw new RepositoryException("Generated article field [articleHotScore] is read-only");
            }
        }
    }

    @Override
    public List<JSONObject> getRandomly(final int fetchSize) throws RepositoryException {
        final List<JSONObject> ret = new ArrayList<>();

        final double mid = Math.random();
        Query query = new Query().
                setFilter(CompositeFilterOperator.and(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.GREATER_THAN_OR_EQUAL, mid),
                        new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.LESS_THAN_OR_EQUAL, 1D),
                        new PropertyFilter(Article.ARTICLE_STATUS, FilterOperator.NOT_EQUAL, Article.ARTICLE_STATUS_C_INVALID),
                        new PropertyFilter(Article.ARTICLE_TYPE, FilterOperator.NOT_EQUAL, Article.ARTICLE_TYPE_C_DISCUSSION),
                        new PropertyFilter(Article.ARTICLE_SHOW_IN_LIST, FilterOperator.NOT_EQUAL, Article.ARTICLE_SHOW_IN_LIST_C_NOT))).
                select(Article.ARTICLE_TITLE, Article.ARTICLE_PERMALINK, Article.ARTICLE_AUTHOR_ID, Keys.OBJECT_ID, Article.ARTICLE_VIEW_CNT).
                setPage(1, fetchSize).setPageCount(1);
        final List<JSONObject> list1 = getList(query);
        ret.addAll(list1);

        final int reminingSize = fetchSize - list1.size();
        if (0 != reminingSize) { // Query for remains
            query = new Query().
                    setFilter(CompositeFilterOperator.and(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.GREATER_THAN_OR_EQUAL, 0D),
                            new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.LESS_THAN_OR_EQUAL, mid),
                            new PropertyFilter(Article.ARTICLE_STATUS, FilterOperator.NOT_EQUAL, Article.ARTICLE_STATUS_C_INVALID),
                            new PropertyFilter(Article.ARTICLE_TYPE, FilterOperator.NOT_EQUAL, Article.ARTICLE_TYPE_C_DISCUSSION),
                            new PropertyFilter(Article.ARTICLE_SHOW_IN_LIST, FilterOperator.NOT_EQUAL, Article.ARTICLE_SHOW_IN_LIST_C_NOT))).
                    select(Article.ARTICLE_TITLE, Article.ARTICLE_PERMALINK, Article.ARTICLE_AUTHOR_ID, Keys.OBJECT_ID, Article.ARTICLE_VIEW_CNT).
                    setPage(1, reminingSize).setPageCount(1);
            final List<JSONObject> list2 = getList(query);

            ret.addAll(list2);
        }

        return ret;
    }

    /**
     * Gets an article by the specified article title.
     *
     * @param articleTitle the specified article title
     * @return an article, {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByTitle(final String articleTitle) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(Article.ARTICLE_TITLE,
                FilterOperator.EQUAL, articleTitle)).setPageCount(1);
        return getFirst(query);
    }

    public JSONObject getByTitleForUpdate(final String articleTitle) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("article transaction required");
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE articleTitle=? LIMIT 1 FOR UPDATE", articleTitle);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Read only the trusted payment destination before acquiring the account pair locks. */
    public String getCommentPublicationAuthor(final String id) throws RepositoryException {
        final var rows = select("SELECT articleAuthorId FROM `" + getName() + "` WHERE oId=? LIMIT 1", id);
        return rows.isEmpty() ? "" : rows.getFirst().optString(Article.ARTICLE_AUTHOR_ID);
    }

    /** V3 comment gate locks the current row without coercing damaged historical CHAR booleans. */
    public JSONObject getV3CommentPolicyForUpdate(final String id) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("article transaction required");
        final var rows = select(ApiV3ArticlePolicy.lockSql(getName()), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject getForUpdate(final String id) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("article transaction required");
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE oId=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Projection bypasses the public article cache and binds owner with ID in the same query. */
    public JSONObject getOwnedEditorSource(final String id, final String author, final String chapterTable, final String columnTable) throws RepositoryException {
        final var rows = select(ApiV3ArticleEditorDto.sql(getName(), chapterTable, columnTable), id, author);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject getOwnedForUpdate(final String id, final String author) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("article transaction required");
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE oId=? AND articleAuthorId=? FOR UPDATE", id, author);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** An expiry scan may be stale; never erase a later paid renewal or administrator stick. */
    public boolean expireStickIfCurrentInCurrentTransaction(final String id, final long expected,
                                                            final long now, final long duration) throws RepositoryException {
        requireWriteTransaction();
        if (expected <= 0 || expected == Long.MAX_VALUE || duration <= 0 || now <= duration || expected >= now - duration) return false;
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE oId=? FOR UPDATE", id);
        if (rows.size() != 1 || rows.getFirst().getLong(Article.ARTICLE_STICK) != expected || expected >= now - duration) return false;
        final JSONObject current = rows.getFirst();
        current.put(Article.ARTICLE_STICK, 0L);
        update(id, current, Article.ARTICLE_STICK);
        return true;
    }

    public Long latestPublicArticleIdForUpdate(final String authorId) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("article transaction required");
        final var rows = select(ArticleSubmissionPolicy.latestPublicSql(getName(), true), authorId,
                Article.ARTICLE_ANONYMOUS_C_PUBLIC, Article.ARTICLE_STATUS_C_INVALID);
        return rows.isEmpty() ? null : rows.getFirst().getLong(Keys.OBJECT_ID);
    }
}
