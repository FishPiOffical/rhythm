package org.b3log.symphony.util;

import org.b3log.symphony.model.Comment;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Position uses the same ID ordering and selection as ordinary/paragraph oldest pages. */
public final class ApiV3CommentLocationQueries {
    public static final int PAGE_SIZE = 20;
    private ApiV3CommentLocationQueries() { }
    public static ApiV3ParagraphQueries.Selection ordinary(final String table, final String article) {
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("internal table");
        return new ApiV3ParagraphQueries.Selection(" FROM `" + table + "` WHERE commentOnArticleId=? AND commentType=? AND commentStatus=?",
                List.of(article, Comment.COMMENT_TYPE_C_ARTICLE, Comment.COMMENT_STATUS_C_VALID));
    }
    public static ApiV3ParagraphQueries.Selection rank(final ApiV3ParagraphQueries.Selection selection, final String id) {
        final var args = new ArrayList<Object>();args.add(id);args.addAll(selection.args());args.add(id);
        return new ApiV3ParagraphQueries.Selection("SELECT COUNT(1) AS position,MAX(CASE WHEN oId=? THEN 1 ELSE 0 END) AS targetPresent"
                + selection.sql() + " AND oId<=?", args);
    }
    public static int page(final JSONObject rank) {
        final long position=rank.optLong("position",-1);
        if (position<1 || rank.optInt("targetPresent")!=1) throw new ApiV3Exception(404,"not_found","评论当前无法定位");
        final long page=(position-1)/PAGE_SIZE+1;
        if(page>Integer.MAX_VALUE/50)throw new ApiV3Exception(503,"temporarily_unavailable","评论位置暂不可用");
        return (int)page;
    }
}
