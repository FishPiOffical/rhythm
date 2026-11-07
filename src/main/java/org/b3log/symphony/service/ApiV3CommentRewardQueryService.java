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
import org.b3log.symphony.model.Reward;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.RewardRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

/** Read-only state and current indicative fees. Mutations recheck all qualifications under their existing locks. */
@Service
public class ApiV3CommentRewardQueryService {
    @Inject private CommentRepository comments;
    @Inject private RewardRepository rewards;
    @Inject private UserRepository users;
    @Inject private ApiV3ContentQueryService visibility;

    public JSONObject read(final JSONObject viewer, final String id, final boolean acceptance) throws Exception {
        if (viewer == null || viewer.optString(Keys.OBJECT_ID).isBlank()) throw new ApiV3Exception(401,"unauthorized","需要登录");
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw new ApiV3Exception(422,"invalid_input","评论编号不合法");
        final String actor=viewer.getString(Keys.OBJECT_ID);
        final var rows=comments.select("SELECT c.oId,c.commentOnArticleId,c.commentAuthorId,c.commentStatus,c.commentVisible,"
                + "c.commentAnonymous,c.commentThankCnt,c.commentQnAOffered,"
                + "EXISTS(SELECT 1 FROM `"+rewards.getName()+"` r WHERE r.senderId=? AND r.dataId=c.oId AND r.`type`=?) AS recorded,"
                + "EXISTS(SELECT 1 FROM `"+comments.getName()+"` ac WHERE ac.commentOnArticleId=c.commentOnArticleId AND ac.commentQnAOffered=?) AS anyAccepted "
                + "FROM `"+comments.getName()+"` c JOIN `"+users.getName()+"` au ON au.oId=c.commentAuthorId AND au.userStatus=? "
                + "JOIN `"+users.getName()+"` actor ON actor.oId=? AND actor.userStatus=? WHERE c.oId=? AND c.commentStatus=? LIMIT 1",
                actor,Reward.TYPE_C_COMMENT,Comment.COMMENT_QNA_OFFERED_C_YES,UserExt.USER_STATUS_C_VALID,actor,UserExt.USER_STATUS_C_VALID,id,Comment.COMMENT_STATUS_C_VALID);
        if(rows.isEmpty())throw missing();
        final var comment=rows.getFirst();
        final var article=visibility.viewableArticle(comment.getString(Comment.COMMENT_ON_ARTICLE_ID),viewer);
        if(article==null || !ApiV3ContentUtil.canViewCommentContent(comment,article,viewer))throw missing();
        final boolean own=actor.equals(comment.getString(Comment.COMMENT_AUTHOR_ID));
        final boolean anonymous=comment.getInt(Comment.COMMENT_ANONYMOUS)==Comment.COMMENT_ANONYMOUS_C_ANONYMOUS;
        if(acceptance){
            final int offer=article.getInt(Article.ARTICLE_QNA_OFFER_POINT);
            if(offer<0)throw new IllegalStateException("negative stored comment offer");
            return new JSONObject().put("id",id).put("accepted",comment.getInt(Comment.COMMENT_QNA_OFFERED)==Comment.COMMENT_QNA_OFFERED_C_YES)
                    .put("canAccept",article.getInt(Article.ARTICLE_TYPE)==Article.ARTICLE_TYPE_C_QNA && offer >= 20 && !own && actor.equals(article.getString(Article.ARTICLE_AUTHOR_ID)) && comment.getInt("anyAccepted")==0)
                    .put("offerPoint",anonymous?0:offer);
        }
        final int count=comment.getInt(Comment.COMMENT_THANK_CNT);
        if(count<0 || Symphonys.POINT_THANK_COMMENT<0)throw new IllegalStateException("invalid comment thanks values");
        final boolean recorded=comment.getInt("recorded")!=0;
        return new JSONObject().put("id",id).put("thanked",recorded).put("canThank",!own && !recorded)
                .put("thankCount",count).put("thankPoint",anonymous?0:Symphonys.POINT_THANK_COMMENT);
    }
    private static ApiV3Exception missing(){return new ApiV3Exception(404,"not_found","资源不存在或不可查看");}
}
