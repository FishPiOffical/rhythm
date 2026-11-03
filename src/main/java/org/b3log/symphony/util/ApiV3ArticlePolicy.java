package org.b3log.symphony.util;

import org.b3log.symphony.model.Article;
import org.json.JSONObject;

/** Current article policy values; unknown historical values must not become an implicit grant. */
public final class ApiV3ArticlePolicy {
    public static final String RAW_COMMENTABLE = "v3ContentCommentable";
    private ApiV3ArticlePolicy() { }

    /** Alias bypasses Latke's registered boolean conversion without changing shared repository mapping. */
    public static String commentableColumn(final String prefix) {
        if (!prefix.matches("[A-Za-z0-9_]*")) throw new IllegalArgumentException("internal alias");
        return (prefix.isEmpty() ? "" : prefix + ".") + Article.ARTICLE_COMMENTABLE + " AS " + RAW_COMMENTABLE;
    }

    public static String lockSql(final String table) {
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("internal table");
        return "SELECT oId," + commentableColumn("") + " FROM `" + table + "` WHERE oId=? FOR UPDATE";
    }

    public static Boolean commentable(final JSONObject article) {
        final boolean raw = article.has(RAW_COMMENTABLE);
        final Object value = article.opt(raw ? RAW_COMMENTABLE : Article.ARTICLE_COMMENTABLE);
        if (raw && value instanceof String text) {
            if ("0".equals(text)) return false;
            if ("1".equals(text)) return true;
            return null;
        }
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number && number.doubleValue() == number.longValue()) {
            if (number.longValue() == 0) return false;
            if (number.longValue() == 1) return true;
        }
        return null;
    }

    public static Integer statement(final JSONObject article) {
        final Object value = article.opt(Article.ARTICLE_STATEMENT);
        if (value instanceof Number number && number.doubleValue() == number.longValue()
                && number.longValue() >= 0 && number.longValue() <= 3) return number.intValue();
        return null;
    }

    public static JSONObject metadata(final JSONObject article) {
        final Boolean commentable = commentable(article);
        final Integer statement = statement(article);
        return new JSONObject().put("commentable", commentable == null ? JSONObject.NULL : commentable)
                .put("statement", statement == null ? JSONObject.NULL : statement);
    }

    public static void requireCommenting(final JSONObject article) {
        if (!Boolean.TRUE.equals(commentable(article))) {
            throw new ApiV3Exception(403, "comments_closed", "文章当前不可发布或编辑评论");
        }
    }
}
