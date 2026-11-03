package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;

/** The same viewer-explicit gate is used for HTTP and each forum event recipient. */
@Service
public class ApiV3ReactionTargetService {
    @Inject private ArticleRepository articles;
    @Inject private ChatRoomRepository chat;
    @Inject private ReactionRepository reactions;
    @Inject private ApiV3ContentQueryService content;
    public JSONObject read(final JSONObject viewer, final String type, final String id) throws RepositoryException {
        final JSONObject row = switch (type) {
            case Reaction.TARGET_TYPE_ARTICLE -> content.viewableArticle(id, viewer);
            case Reaction.TARGET_TYPE_COMMENT -> content.currentComment(id);
            case Reaction.TARGET_TYPE_CHAT -> chat.get(id);
            default -> throw ApiV3Requests.invalid();
        };
        return validate(viewer, type, row, false);
    }
    /** Target row is already locked by the shared mutation service. */
    public JSONObject validate(final JSONObject viewer, final String type, final JSONObject row, final boolean locked) throws RepositoryException {
        if (null == row) throw ApiV3Requests.notFound();
        if (Reaction.TARGET_TYPE_CHAT.equals(type)) {
            if (null == viewer) throw ApiV3Requests.notFound();
            return row;
        }
        final JSONObject article;
        if (Reaction.TARGET_TYPE_COMMENT.equals(type)) {
            if (Comment.COMMENT_STATUS_C_VALID != row.optInt(Comment.COMMENT_STATUS) || blocked(content.currentAuthor(row.optString(Comment.COMMENT_AUTHOR_ID)))) throw ApiV3Requests.notFound();
            final String articleId = row.optString(Comment.COMMENT_ON_ARTICLE_ID);
            if (locked) reactions.lockEntity(articles.getName(), articleId);
            article = content.viewableArticle(articleId, viewer);
        } else article = content.viewableArticle(row.optString(Keys.OBJECT_ID), viewer);
        if (null == article) throw ApiV3Requests.notFound();
        if (Reaction.TARGET_TYPE_COMMENT.equals(type) && !ApiV3ContentUtil.canViewCommentContent(row, article, viewer)) throw ApiV3Requests.notFound();
        return article;
    }
    private static boolean blocked(final JSONObject user) {
        return null == user || UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS) || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS);
    }
}
