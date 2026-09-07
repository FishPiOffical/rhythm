package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.FishGameVote;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.processor.middleware.FishGameWriteSecurityMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.PermissionMidware;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.FishGameMgmtService;
import org.b3log.symphony.service.FishGameQueryService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** 鱼游投稿、审核与互动处理器。 */
@Singleton
public class FishGameProcessor {
    @Inject
    private FishGameMgmtService fishGameMgmtService;
    @Inject
    private FishGameQueryService fishGameQueryService;
    @Inject
    private DataModelService dataModelService;
    @Inject
    private UserQueryService userQueryService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final PermissionMidware permissionMidware = beanManager.getReference(PermissionMidware.class);
        final AnonymousViewCheckMidware anonymousViewCheck = beanManager.getReference(AnonymousViewCheckMidware.class);
        final FishGameWriteSecurityMidware writeSecurity = beanManager.getReference(FishGameWriteSecurityMidware.class);
        final FishGameProcessor processor = beanManager.getReference(FishGameProcessor.class);
        Dispatcher.get("/activities/game/{id}", processor::showGame, anonymousViewCheck::handle);
        Dispatcher.post("/api/fish-games", processor::submit, loginCheck::handle, writeSecurity::checkWriteRequest,
                writeSecurity::checkSubmissionLimit);
        Dispatcher.post("/api/fish-games/{id}/edit", processor::requestEdit, loginCheck::handle, writeSecurity::checkWriteRequest,
                writeSecurity::checkSubmissionLimit);
        Dispatcher.post("/api/fish-games/{id}/vote", processor::vote, loginCheck::handle, writeSecurity::checkWriteRequest,
                writeSecurity::checkVoteLimit);
        Dispatcher.get("/api/fish-games/{id}/comments", processor::comments, anonymousViewCheck::handle);
        Dispatcher.post("/api/fish-games/{id}/comments", processor::addComment, loginCheck::handle, writeSecurity::checkWriteRequest,
                writeSecurity::checkCommentLimit);
        Dispatcher.get("/admin/fish-games", processor::showAdmin, loginCheck::handle, permissionMidware::check);
        Dispatcher.get("/api/admin/fish-games", processor::adminList, loginCheck::handle, permissionMidware::check);
        Dispatcher.post("/api/admin/fish-games", processor::adminAdd, loginCheck::handle, writeSecurity::checkWriteRequest, permissionMidware::check);
        Dispatcher.get("/api/admin/fish-games/export", processor::adminExport, loginCheck::handle, permissionMidware::check);
        Dispatcher.post("/api/admin/fish-games/import", processor::adminImport, loginCheck::handle, writeSecurity::checkWriteRequest, permissionMidware::check);
        Dispatcher.post("/api/admin/fish-games/{id}/review", processor::adminReview, loginCheck::handle, writeSecurity::checkWriteRequest, permissionMidware::check);
        Dispatcher.post("/api/admin/fish-games/{id}/edit", processor::adminEdit, loginCheck::handle, writeSecurity::checkWriteRequest, permissionMidware::check);
    }

    public void showGame(final RequestContext context) {
        final JSONObject game = fishGameQueryService.get(context.pathVar("id"));
        if (game == null || game.optInt(FishGame.STATUS) != FishGame.STATUS_APPROVED) {
            context.sendError(404);
            return;
        }
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/fish-game.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModel.put("fishGame", game);
        dataModel.put("fishGameComments", withAuthors(fishGameQueryService.getComments(game.optString(Keys.OBJECT_ID))));
        dataModel.put("fishGameCanEdit", isAuthor(game, context));
        dataModel.put("fishGameEditPending", game.optInt(FishGame.EDIT_PENDING) == 1);
        dataModel.put("fishGameUserVote", fishGameQueryService.getUserVote(currentUserId(context), game.optString(Keys.OBJECT_ID)));
    }

    public void submit(final RequestContext context) {
        try {
            final JSONObject game = fishGameMgmtService.submit(currentUserId(context), context.requestJSON());
            renderSuccess(context, game);
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void requestEdit(final RequestContext context) {
        try {
            fishGameMgmtService.requestEdit(currentUserId(context), context.pathVar("id"), context.requestJSON());
            renderSuccess(context, new JSONObject());
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void vote(final RequestContext context) {
        try {
            final JSONObject result = fishGameMgmtService.vote(currentUserId(context), context.pathVar("id"),
                    context.requestJSON().optString(FishGameVote.VALUE));
            renderSuccess(context, result);
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void comments(final RequestContext context) {
        final String gameId = context.pathVar("id");
        final JSONObject game = fishGameQueryService.get(gameId);
        if (game == null || game.optInt(FishGame.STATUS) != FishGame.STATUS_APPROVED) {
            renderError(context, "鱼游不存在");
            return;
        }
        final JSONObject result = new JSONObject().put("comments", new JSONArray(withAuthors(fishGameQueryService.getComments(gameId))));
        renderSuccess(context, result);
    }

    public void addComment(final RequestContext context) {
        try {
            final JSONObject request = context.requestJSON();
            final JSONObject comment = fishGameMgmtService.addComment(currentUserId(context), context.pathVar("id"),
                    request.optString("content"));
            renderSuccess(context, publicComment(comment, currentUser(context)));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void showAdmin(final RequestContext context) {
        if (!isAdmin(currentUser(context))) {
            context.sendError(403);
            return;
        }
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "admin/fish-games.ftl");
        renderer.getDataModel().put("fishGames", fishGameQueryService.getAll());
        dataModelService.fillHeaderAndFooter(context, renderer.getDataModel());
    }

    public void adminList(final RequestContext context) {
        if (!isAdmin(currentUser(context))) {
            renderError(context, "无权限");
            return;
        }
        renderSuccess(context, new JSONObject().put("games", new JSONArray(fishGameQueryService.getAll())));
    }

    public void adminAdd(final RequestContext context) {
        if (!isAdmin(currentUser(context))) {
            renderError(context, "无权限");
            return;
        }
        try {
            renderSuccess(context, fishGameMgmtService.addManual(currentUserId(context), context.requestJSON()));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void adminReview(final RequestContext context) {
        if (!isAdmin(currentUser(context))) {
            renderError(context, "无权限");
            return;
        }
        try {
            final JSONObject request = context.requestJSON();
            renderSuccess(context, fishGameMgmtService.review(context.pathVar("id"),
                    request.optInt(FishGame.STATUS), request));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void adminEdit(final RequestContext context) {
        if (!isAdmin(currentUser(context))) {
            renderError(context, "无权限");
            return;
        }
        try {
            renderSuccess(context, fishGameMgmtService.updateByAdmin(context.pathVar("id"), context.requestJSON()));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    public void adminExport(final RequestContext context) {
        if (!isAdmin(currentUser(context))) {
            renderError(context, "无权限");
            return;
        }
        final JSONArray games = new JSONArray();
        for (final JSONObject game : fishGameQueryService.getAll()) {
            games.put(new JSONObject()
                    .put(FishGame.NAME, game.optString(FishGame.NAME))
                    .put(FishGame.DESCRIPTION, game.optString(FishGame.DESCRIPTION))
                    .put(FishGame.URL, game.optString(FishGame.URL))
                    .put(FishGame.ICON_URL, game.optString(FishGame.ICON_URL))
                    .put(FishGame.STATUS, game.optInt(FishGame.STATUS)));
        }
        context.renderJSON(new JSONObject().put("version", 1).put("games", games));
    }

    public void adminImport(final RequestContext context) {
        if (!isAdmin(currentUser(context))) {
            renderError(context, "无权限");
            return;
        }
        try {
            final JSONObject request = context.requestJSON();
            final JSONArray games = request.optJSONArray("games");
            if (games == null) {
                renderError(context, "导入文件必须包含 games 数组");
                return;
            }
            renderSuccess(context, fishGameMgmtService.importGames(currentUserId(context), games));
        } catch (final ServiceException e) {
            renderError(context, e.getMessage());
        }
    }

    private List<JSONObject> withAuthors(final List<JSONObject> comments) {
        final List<JSONObject> result = new java.util.ArrayList<>();
        for (final JSONObject comment : comments) {
            final JSONObject author = userQueryService.getUser(comment.optString("fishGameCommentAuthorId"));
            result.add(publicComment(comment, author));
        }
        return result;
    }

    private JSONObject publicComment(final JSONObject comment, final JSONObject author) {
        return new JSONObject()
                .put(Keys.OBJECT_ID, comment.optString(Keys.OBJECT_ID))
                .put("fishGameCommentGameId", comment.optString("fishGameCommentGameId"))
                .put("fishGameCommentAuthorId", comment.optString("fishGameCommentAuthorId"))
                .put("fishGameCommentContent", comment.optString("fishGameCommentContent"))
                .put("fishGameCommentCreatedTime", comment.optLong("fishGameCommentCreatedTime"))
                .put("authorName", author == null ? "匿名用户" : author.optString(User.USER_NAME));
    }

    private JSONObject currentUser(final RequestContext context) {
        final Object user = context.attr(User.USER);
        if (user instanceof JSONObject) {
            return (JSONObject) user;
        }
        return Sessions.getUser();
    }

    private String currentUserId(final RequestContext context) {
        final JSONObject user = currentUser(context);
        return user == null ? "" : user.optString(Keys.OBJECT_ID);
    }

    private boolean isAuthor(final JSONObject game, final RequestContext context) {
        return StringUtils.equals(game.optString(FishGame.AUTHOR_ID), currentUserId(context));
    }

    private boolean isAdmin(final JSONObject user) {
        return user != null && Role.ROLE_ID_C_ADMIN.equals(user.optString(User.USER_ROLE));
    }

    private void renderSuccess(final RequestContext context, final JSONObject data) {
        context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.SUCC).put(Keys.MSG, "").put(Keys.DATA, data));
    }

    private void renderError(final RequestContext context, final String message) {
        context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.ERR).put(Keys.MSG, message));
    }
}
