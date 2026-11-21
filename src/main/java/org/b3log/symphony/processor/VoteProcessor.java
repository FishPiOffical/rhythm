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
package org.b3log.symphony.processor;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.PermissionMidware;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;

/**
 * Vote processor.
 * <ul>
 * <li>Votes up an article (/vote/up/article), POST</li>
 * <li>Votes down an article (/vote/down/article), POST</li>
 * <li>Votes up a comment (/vote/up/comment), POST</li>
 * <li>Votes down a comment (/vote/down/comment), POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 1.3.0
 */
@Singleton
public class VoteProcessor {

    /**
     * Holds votes.
     */
    private static final Set<String> VOTES = Collections.synchronizedSet(new HashSet<>());

    /**
     * Vote management service.
     */
    @Inject
    private VoteMgmtService voteMgmtService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Transactional comment votes.
     */
    @Inject
    private CommentVoteMgmtService commentVotes;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final PermissionMidware permissionMidware = beanManager.getReference(PermissionMidware.class);

        final VoteProcessor voteProcessor = beanManager.getReference(VoteProcessor.class);
        Dispatcher.post("/vote/up/comment", voteProcessor::voteUpComment, loginCheck::handle, permissionMidware::check);
        Dispatcher.post("/vote/down/comment", voteProcessor::voteDownComment, loginCheck::handle, permissionMidware::check);
        Dispatcher.post("/vote/up/article", voteProcessor::voteUpArticle, loginCheck::handle, permissionMidware::check);
        Dispatcher.post("/vote/down/article", voteProcessor::voteDownArticle, loginCheck::handle, permissionMidware::check);
    }

    /**
     * Votes up a comment.
     * <p>
     * The request json object:
     * <pre>
     * {
     *   "dataId": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void voteUpComment(final RequestContext context) {
        voteComment(context, Vote.TYPE_C_UP);
    }

    /**
     * Votes down a comment.
     * <p>
     * The request json object:
     * <pre>
     * {
     *   "dataId": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void voteDownComment(final RequestContext context) {
        voteComment(context, Vote.TYPE_C_DOWN);
    }

    private void voteComment(final RequestContext context, final int choice) {
        context.renderJSON(StatusCodes.ERR);
        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        if (null == currentUser) { context.sendError(403); return; }
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        final String dataId = context.requestJSON().optString(Common.DATA_ID);
        try {
            final var result = commentVotes.toggle(userId, dataId, choice,
                    choice == Vote.TYPE_C_UP && !VOTES.contains(userId + dataId));
            if (result.changed() && result.current() == Vote.TYPE_C_UP) VOTES.add(userId + dataId);
            context.renderJSON(StatusCodes.SUCC).renderJSONValue(Vote.TYPE, result.previous());
        } catch (final CommentVoteMgmtService.Rejected e) {
            context.renderMsg(langPropsService.get("self".equals(e.code) ? "cantVoteSelfLabel" : "systemErrLabel"));
        } catch (final Exception e) {
            LogManager.getLogger(VoteProcessor.class).error("Comment vote failed [id={}]", dataId, e);
            context.renderMsg(langPropsService.get("systemErrLabel"));
        }
    }

    /**
     * Votes up an article.
     * <p>
     * The request json object:
     * <pre>
     * {
     *   "dataId": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void voteUpArticle(final RequestContext context) {
        voteArticle(context, Vote.TYPE_C_UP);
    }

    /**
     * Votes down an article.
     * <p>
     * The request json object:
     * <pre>
     * {
     *   "dataId": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void voteDownArticle(final RequestContext context) {
        voteArticle(context, Vote.TYPE_C_DOWN);
    }

    private void voteArticle(final RequestContext context, final int choice) {
        context.renderJSON(StatusCodes.ERR);
        final JSONObject currentUser = (JSONObject) context.attr(User.USER);
        if (null == currentUser) { context.sendError(403); return; }
        final String actor = currentUser.optString(Keys.OBJECT_ID);
        final String id = context.requestJSON().optString(Common.DATA_ID);
        try {
            final var result = voteMgmtService.toggle(actor, id, choice,
                    choice == Vote.TYPE_C_UP && !VOTES.contains(actor + id));
            if (result.changed() && result.current() == Vote.TYPE_C_UP) VOTES.add(actor + id);
            context.renderJSON(StatusCodes.SUCC).renderJSONValue(Vote.TYPE, result.previous());
        } catch (final VoteMgmtService.Rejected e) {
            context.renderMsg(langPropsService.get("self".equals(e.code) ? "cantVoteSelfLabel" : "systemErrLabel"));
        } catch (final Exception e) {
            LogManager.getLogger(VoteProcessor.class).error("Article vote failed [id={}]", id, e);
            context.renderMsg(langPropsService.get("systemErrLabel"));
        }
    }
}
