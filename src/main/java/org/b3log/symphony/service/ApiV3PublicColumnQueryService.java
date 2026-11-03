package org.b3log.symphony.service;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LongArticleChapterRepository;
import org.b3log.symphony.repository.LongArticleColumnRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.UserMentionCandidates;
import org.b3log.symphony.util.Escapes;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import java.util.ArrayList;
import java.util.List;

/** Public columns are a projection of currently discoverable, signed long articles, never stored raw counts. */
@Service
public class ApiV3PublicColumnQueryService {
    @Inject private LongArticleColumnRepository columns;
    @Inject private LongArticleChapterRepository chapters;
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    private record Selection(String sql, List<Object> args) { }

    private Selection from(final JSONObject viewer, final String id) {
        final String sql = " FROM `" + columns.getName() + "` c JOIN `" + chapters.getName()
                + "` r ON r.columnId=c.oId JOIN `" + articles.getName() + "` a ON a.oId=r.articleId"
                + " JOIN `" + users.getName() + "` u ON u.oId=c.columnAuthorId AND u.oId=a.articleAuthorId"
                + " WHERE c.columnStatus=? AND u.userStatus IN (?,?) AND a.articleStatus<>?"
                + " AND a.articleType=? AND a.articleAnonymous=? AND a.articleShowInList<>?"
                + " AND a.articleTags<>? AND a.articleTags NOT LIKE ?";
        final List<Object> args = new ArrayList<>(List.of(LongArticleColumn.COLUMN_STATUS_C_VALID,
                UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID_LOGIN, Article.ARTICLE_STATUS_C_INVALID,
                Article.ARTICLE_TYPE_C_LONG, Article.ARTICLE_ANONYMOUS_C_PUBLIC, Article.ARTICLE_SHOW_IN_LIST_C_NOT,
                Tag.TAG_TITLE_C_SANDBOX, "B3log%"));
        String result = sql;
        if (viewer == null) { result += " AND a.articleAnonymousView<>?"; args.add(Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW); }
        if (id != null) { result += " AND c.oId=?"; args.add(id); }
        return new Selection(result, args);
    }
    private static String aggregate() {
        return "SELECT c.oId,c.columnTitle,c.columnCoverURL,COUNT(*) AS visibleCount,MAX(a.articleUpdateTime) AS visibleUpdatedAt";
    }
    private static String group() { return " GROUP BY c.oId,c.columnTitle,c.columnCoverURL"; }
    private List<JSONObject> query(final String sql, final List<Object> args) throws RepositoryException {
        return columns.select(sql, args.toArray());
    }
    private static void paging(final int p, final int size) { if (p < 1 || size < 1 || size > 50) throw ApiV3Requests.invalid(); }
    private static void target(final String id) { if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3Requests.invalid(); }
    public JSONObject list(final JSONObject viewer, final int p, final int size, final boolean hot) {
        paging(p, size);
        try {
            final Selection selection = from(viewer, null);
            final long total = query("SELECT COUNT(DISTINCT c.oId) AS total" + selection.sql, selection.args).getFirst().getLong("total");
            final List<Object> args = new ArrayList<>(selection.args); args.add(size); args.add((long) (p - 1) * size);
            final List<JSONObject> rows = query(aggregate() + selection.sql + group()
                    + " ORDER BY " + (hot ? "visibleCount DESC," : "") + "visibleUpdatedAt DESC,c.oId DESC LIMIT ? OFFSET ?", args);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) list.put(card(row, viewer));
            return ApiV3Requests.pagination(list, p, size, total);
        } catch (final RepositoryException error) { throw unavailable(); }
    }
    public JSONObject detail(final JSONObject viewer, final String id) {
        target(id);
        try {
            final Selection selection = from(viewer, id);
            final List<JSONObject> rows = query(aggregate() + selection.sql + group(), selection.args);
            if (rows.size() != 1) throw ApiV3Requests.notFound();
            return new JSONObject().put("column", card(rows.getFirst(), viewer)).put("chapters", chapterList(selection, false, false, 0, 0));
        } catch (final RepositoryException error) { throw unavailable(); }
    }
    public JSONObject chapters(final JSONObject viewer, final String id, final int p, final int size) {
        target(id); paging(p, size);
        try {
            final Selection selection = from(viewer, id);
            final long total = query("SELECT COUNT(*) AS total" + selection.sql, selection.args).getFirst().getLong("total");
            if (total == 0) throw ApiV3Requests.notFound();
            return ApiV3Requests.pagination(chapterList(selection, true, false, p, size), p, size, total);
        } catch (final RepositoryException error) { throw unavailable(); }
    }
    /** Bounded article navigation; the directory remains a separate paginated resource. */
    public JSONObject articleContext(final JSONObject viewer, final String articleId) {
        if (articleId == null || !articleId.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        try {
            final List<JSONObject> targets = query("SELECT a.oId,a.articleAuthorId,a.articleStatus,a.articleType,a.articleAnonymousView,"
                    + "CASE WHEN a.articleType=? THEN a.articleContent ELSE '' END AS articleContent FROM `" + articles.getName()
                    + "` a JOIN `" + users.getName() + "` u ON u.oId=a.articleAuthorId WHERE a.oId=? AND u.userStatus IN (?,?)",
                    List.of(Article.ARTICLE_TYPE_C_DISCUSSION, articleId, UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID_LOGIN));
            if (targets.size() != 1) throw ApiV3Requests.notFound();
            final JSONObject target = targets.getFirst();
            if (!ApiV3ContentUtil.canViewArticle(target, viewer, UserMentionCandidates.parse(target.optString("articleContent")))) {
                throw ApiV3Requests.notFound();
            }
            final JSONObject result = new JSONObject().put("articleId", articleId).put("column", JSONObject.NULL)
                    .put("chapterNo", JSONObject.NULL).put("previous", JSONObject.NULL).put("next", JSONObject.NULL);
            final Selection visible = from(viewer, null);
            final List<Object> binding = new ArrayList<>(visible.args); binding.add(articleId);
            final List<JSONObject> bindings = query("SELECT c.oId,r.chapterNo,r.chapterCreateTime" + visible.sql + " AND a.oId=?", binding);
            if (bindings.isEmpty()) return result;
            if (bindings.size() != 1) throw unavailable();
            final JSONObject current = bindings.getFirst();
            final Selection selection = from(viewer, current.getString("oId"));
            final List<JSONObject> summaries = query(aggregate() + selection.sql + group(), selection.args);
            if (summaries.size() != 1) return result;
            result.put("column", summary(summaries.getFirst())).put("chapterNo", current.getInt("chapterNo"));
            for (final boolean next : List.of(false, true)) {
                final String op = next ? ">" : "<", direction = next ? " ASC" : " DESC";
                final List<Object> args = new ArrayList<>(selection.args);
                args.addAll(List.of(current.getInt("chapterNo"), current.getInt("chapterNo"), current.getLong("chapterCreateTime"),
                        current.getInt("chapterNo"), current.getLong("chapterCreateTime"), articleId));
                final List<JSONObject> rows = query("SELECT a.oId,a.articleTitle,a.articlePermalink,a.articleCreateTime,r.chapterNo"
                        + selection.sql + " AND (r.chapterNo" + op + "? OR (r.chapterNo=? AND r.chapterCreateTime" + op
                        + "?) OR (r.chapterNo=? AND r.chapterCreateTime=? AND a.oId" + op + "?))"
                        + " ORDER BY r.chapterNo" + direction + ",r.chapterCreateTime" + direction + ",a.oId" + direction + " LIMIT 1", args);
                if (!rows.isEmpty()) result.put(next ? "next" : "previous", chapter(rows.getFirst()));
            }
            return result;
        } catch (final RepositoryException error) { throw unavailable(); }
    }
    private JSONObject summary(final JSONObject row) {
        final String id = row.getString("oId");
        return new JSONObject().put("id", id).put("title", Escapes.escapeHTML(row.optString("columnTitle")))
                .put("coverURL", StringUtils.defaultIfBlank(row.optString("columnCoverURL"), Latkes.getStaticServePath() + "/images/holiday/book-bg.jpg"))
                .put("articleCount", row.getLong("visibleCount")).put("updatedAt", row.getLong("visibleUpdatedAt"));
    }
    private JSONObject chapter(final JSONObject row) {
        return new JSONObject().put("id", row.getString("oId")).put("title", Escapes.escapeHTML(row.optString("articleTitle")))
                .put("chapterNo", row.getInt("chapterNo")).put("permalink", row.optString("articlePermalink"))
                .put("createdAt", row.getLong("articleCreateTime"));
    }
    private JSONObject card(final JSONObject row, final JSONObject viewer) throws RepositoryException {
        final String id = row.getString("oId");
        return new JSONObject().put("id", id).put("columnId", id)
                .put("title", Escapes.escapeHTML(row.optString("columnTitle")))
                .put("coverURL", StringUtils.defaultIfBlank(row.optString("columnCoverURL"), Latkes.getStaticServePath() + "/images/holiday/book-bg.jpg"))
                .put("articleCount", row.getLong("visibleCount")).put("updatedAt", row.getLong("visibleUpdatedAt"))
                .put("chapters", chapterList(from(viewer, id), true, true, 1, 2));
    }
    private JSONArray chapterList(final Selection selection, final boolean paged, final boolean card, final int p, final int size) throws RepositoryException {
        final List<Object> args = new ArrayList<>(selection.args);
        final String direction = card ? " DESC" : " ASC";
        final String sql = "SELECT a.oId,a.articleTitle,a.articlePermalink,a.articleCreateTime"
                + (card ? ",a.articleContent" : "") + ",r.chapterNo" + selection.sql
                + " ORDER BY r.chapterNo" + direction + ",r.chapterCreateTime" + direction + ",a.oId" + direction;
        if (paged) { args.add(size); args.add((long) (p - 1) * size); }
        final JSONArray list = new JSONArray();
        for (final JSONObject chapter : query(sql + (paged ? " LIMIT ? OFFSET ?" : ""), args)) {
            final JSONObject dto = new JSONObject().put("id", chapter.getString("oId"))
                    .put("title", Escapes.escapeHTML(chapter.optString("articleTitle"))).put("chapterNo", chapter.getInt("chapterNo"))
                    .put("permalink", chapter.optString("articlePermalink")).put("createdAt", chapter.getLong("articleCreateTime"));
            if (card) {
                String preview = StringUtils.normalizeSpace(Jsoup.clean(Markdowns.toHTML(chapter.optString("articleContent")), Safelist.none()));
                if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
                dto.put("preview", preview);
            }
            list.put(dto);
        }
        return list;
    }
    private ApiV3Exception unavailable() {
        LogManager.getLogger(getClass()).warn("Public column reads unavailable");
        return new ApiV3Exception(503, "temporarily_unavailable", "专栏暂不可用");
    }
}
