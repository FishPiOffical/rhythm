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

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.Keys;
import org.b3log.latke.model.User;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.YuhuService;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.PermissionMidware;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Singleton
public class YuhuProcessor {
    @Inject
    private YuhuService yuhuService;
    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final PermissionMidware permissionMidware = beanManager.getReference(PermissionMidware.class);
        final YuhuProcessor p = beanManager.getReference(YuhuProcessor.class);

        Dispatcher.post("/yuhu/book", p::addBook, loginCheck::handle);
        Dispatcher.get("/yuhu/books", p::listBooks);
        Dispatcher.post("/yuhu/book/{bookId}/volume", p::addVolume, loginCheck::handle);
        Dispatcher.get("/yuhu/book/{bookId}/volumes", p::listVolumes);
        Dispatcher.post("/yuhu/book/{bookId}/chapter", p::addChapterDraft, loginCheck::handle);
        Dispatcher.put("/yuhu/chapter/{chapterId}/publish", p::publishChapter, loginCheck::handle,
                permissionMidware::check);
        Dispatcher.put("/yuhu/chapter/{chapterId}/approve", p::approveChapter, loginCheck::handle,
                permissionMidware::check);
        Dispatcher.put("/yuhu/chapter/{chapterId}/reject", p::rejectChapter, loginCheck::handle,
                permissionMidware::check);
        Dispatcher.put("/yuhu/chapter/{chapterId}/freeze", p::freezeChapter, loginCheck::handle,
                permissionMidware::check);
        Dispatcher.put("/yuhu/chapter/{chapterId}/ban", p::banChapter, loginCheck::handle, permissionMidware::check);
        Dispatcher.put("/yuhu/chapter/{chapterId}/draft", p::updateChapterDraft, loginCheck::handle);

        Dispatcher.get("/yuhu/prefs", p::getPrefs, loginCheck::handle);
        Dispatcher.post("/yuhu/prefs", p::setPrefs, loginCheck::handle);

        Dispatcher.get("/yuhu/progress/{bookId}", p::getProgress, loginCheck::handle);
        Dispatcher.post("/yuhu/progress/{bookId}", p::setProgress, loginCheck::handle);

        Dispatcher.get("/yuhu/bookmarks", p::listBookmarks, loginCheck::handle);
        Dispatcher.post("/yuhu/bookmarks", p::addBookmark, loginCheck::handle);
        Dispatcher.delete("/yuhu/bookmarks/{id}", p::deleteBookmark, loginCheck::handle);

        Dispatcher.post("/yuhu/comment", p::addComment, loginCheck::handle);
        Dispatcher.get("/yuhu/comments", p::listComments);
        Dispatcher.delete("/yuhu/comment/{id}", p::deleteComment, loginCheck::handle, permissionMidware::check);
        Dispatcher.get("/yuhu/admin/comments", p::listCommentsAdmin, loginCheck::handle);
        Dispatcher.put("/yuhu/comment/{id}", p::updateComment, loginCheck::handle);

        Dispatcher.post("/yuhu/tag", p::addTag, loginCheck::handle, permissionMidware::check);
        Dispatcher.get("/yuhu/tags", p::listTags);
        Dispatcher.post("/yuhu/book/{bookId}/tags", p::bindTags, loginCheck::handle, permissionMidware::check);

        Dispatcher.post("/yuhu/subscribe/{bookId}", p::subscribe, loginCheck::handle);
        Dispatcher.delete("/yuhu/subscribe/{bookId}", p::unsubscribe, loginCheck::handle);
        Dispatcher.get("/yuhu/subscriptions", p::listSubscriptions, loginCheck::handle);

        Dispatcher.post("/yuhu/vote", p::vote, loginCheck::handle);
        Dispatcher.get("/yuhu/vote/stats", p::voteStats);
        Dispatcher.get("/yuhu/subscription/stats", p::subscriptionStats);

        Dispatcher.get("/yuhu/search", p::search);

        Dispatcher.post("/yuhu/profile/display", p::toggleProfileDisplay, loginCheck::handle);

        Dispatcher.get("/yuhu/author/{profileId}", p::getAuthorProfile);
        Dispatcher.get("/yuhu/author/byBook/{bookId}", p::getAuthorByBook);
        Dispatcher.get("/yuhu/author/{profileId}/stats", p::getAuthorStats);
        Dispatcher.get("/yuhu/author/{profileId}/me", p::getAuthorProfileMe, loginCheck::handle);
        Dispatcher.get("/yuhu/author/{profileId}/books", p::listAuthorBooks);

        // 页面
        Dispatcher.get("/yuhu", p::bookHome);
        Dispatcher.get("/yuhu/book/{bookId}", p::getBook);
        Dispatcher.get("/yuhu/dashboard", p::showYuhuDashboard, loginCheck::handle);
    }

    public void bookHome(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "yuhu/yuhu.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final JSONObject user = (JSONObject) context.attr(User.USER);
        dataModel.put(User.USER, user);

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void addBook(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String id = yuhuService.addBook(req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put(Keys.OBJECT_ID, id));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listBooks(final RequestContext context) {
        try {
            final String tag = context.param("tag");
            final String q = context.param("q");
            final String sort = context.param("sort");
            int page = 1;
            int size = 20;
            try {
                page = Integer.parseInt(context.param("page"));
            } catch (Exception ignored) {
            }
            try {
                size = Integer.parseInt(context.param("size"));
            } catch (Exception ignored) {
            }
            final JSONObject ret = yuhuService.listBooks(tag, q, sort, page, size);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void getBook(final RequestContext context) {
        try {
            final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "yuhu/detail.ftl");
            final Map<String, Object> dataModel = renderer.getDataModel();
            final JSONObject user = (JSONObject) context.attr(User.USER);
            dataModel.put(User.USER, user);

            // final String bookId = context.pathVar("bookId");
            // final JSONObject ret = yuhuService.getBook(bookId);
            // dataModel.put("book",ret);

            dataModelService.fillHeaderAndFooter(context, dataModel);

        } catch (Exception e) {
            context.sendError(404);
        }
    }

    public void addVolume(final RequestContext context) {
        try {
            final String bookId = context.pathVar("bookId");
            final JSONObject req = context.requestJSON();
            final JSONObject ret = yuhuService.addVolume(bookId, req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listVolumes(final RequestContext context) {
        try {
            final String bookId = context.pathVar("bookId");
            final List<org.json.JSONObject> list = yuhuService.listVolumes(bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(list);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void addChapterDraft(final RequestContext context) {
        try {
            final String bookId = context.pathVar("bookId");
            final JSONObject req = context.requestJSON();
            final JSONObject ret = yuhuService.addChapterDraft(bookId, req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void updateChapterDraft(final RequestContext context) {
        try {
            final String chapterId = context.pathVar("chapterId");
            final JSONObject req = context.requestJSON();
            final JSONObject ret = yuhuService.updateChapterDraft(chapterId, req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("状态非法");
        }
    }

    public void publishChapter(final RequestContext context) {
        try {
            final String chapterId = context.pathVar("chapterId");
            final JSONObject ret = yuhuService.publishChapter(chapterId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("状态非法");
        }
    }

    public void approveChapter(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final String chapterId = context.pathVar("chapterId");
            final JSONObject req = context.requestJSON();
            final String note = req.optString("note");
            final JSONObject ret = yuhuService.approveChapter(chapterId, profile.optString(Keys.OBJECT_ID), note);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("状态非法");
        }
    }

    public void rejectChapter(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final String chapterId = context.pathVar("chapterId");
            final JSONObject req = context.requestJSON();
            final String note = req.optString("note");
            final JSONObject ret = yuhuService.rejectChapter(chapterId, profile.optString(Keys.OBJECT_ID), note);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("状态非法");
        }
    }

    public void freezeChapter(final RequestContext context) {
        try {
            final String chapterId = context.pathVar("chapterId");
            final JSONObject req = context.requestJSON();
            yuhuService.setChapterState(chapterId, "frozen");
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("status", "frozen").put("reason", req.optString("reason")));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void banChapter(final RequestContext context) {
        try {
            final String chapterId = context.pathVar("chapterId");
            final JSONObject req = context.requestJSON();
            yuhuService.setChapterState(chapterId, "banned");
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("status", "banned").put("reason", req.optString("reason")));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void getPrefs(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject ret = yuhuService.getPrefs(linkedUserId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void setPrefs(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject req = context.requestJSON();
            yuhuService.setPrefs(linkedUserId, req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("updated", System.currentTimeMillis()));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void getProgress(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final String bookId = context.pathVar("bookId");
            final JSONObject ret = yuhuService.getProgress(profile.optString(Keys.OBJECT_ID), bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void setProgress(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final String bookId = context.pathVar("bookId");
            final JSONObject req = context.requestJSON();
            yuhuService.setProgress(profile.optString(Keys.OBJECT_ID), bookId, req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("updated", System.currentTimeMillis()));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listBookmarks(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final String bookId = context.param("bookId");
            final List<org.json.JSONObject> list = yuhuService.listBookmarks(profile.optString(Keys.OBJECT_ID), bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new org.json.JSONArray(list));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void addBookmark(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final JSONObject req = context.requestJSON();
            req.put("profileId", profile.optString(Keys.OBJECT_ID));
            final String id = yuhuService.addBookmark(req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put(Keys.OBJECT_ID, id));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void deleteBookmark(final RequestContext context) {
        try {
            final String id = context.pathVar("id");
            yuhuService.deleteBookmark(id);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("deleted", true));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void addComment(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final JSONObject req = context.requestJSON();
            req.put("profileId", profile.optString(Keys.OBJECT_ID));
            final String id = yuhuService.addComment(req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put(Keys.OBJECT_ID, id));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listComments(final RequestContext context) {
        try {
            final String chapterId = context.param("chapterId");
            int page = 1;
            int size = 20;
            try {
                page = Integer.parseInt(context.param("page"));
            } catch (Exception ignored) {
            }
            try {
                size = Integer.parseInt(context.param("size"));
            } catch (Exception ignored) {
            }
            final List<org.json.JSONObject> list = yuhuService.listComments(chapterId, page, size);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new org.json.JSONArray(list));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void deleteComment(final RequestContext context) {
        try {
            final String id = context.pathVar("id");
            yuhuService.deleteComment(id);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("deleted", true));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listCommentsAdmin(final RequestContext context) {
        try {
            final org.json.JSONObject currentUser = (org.json.JSONObject) context.attr(org.b3log.latke.model.User.USER);
            final String bookId = context.param("bookId");
            final String chapterId = context.param("chapterId");
            final String profileId = context.param("profileId");
            final String status = context.param("status");
            final String q = context.param("q");
            final int page = org.apache.commons.lang.StringUtils.isBlank(context.param("page")) ? 1 : Integer.parseInt(context.param("page"));
            final int size = org.apache.commons.lang.StringUtils.isBlank(context.param("size")) ? 20 : Integer.parseInt(context.param("size"));
            boolean allow = false;
            try {
                final java.util.Set<String> requisite = org.b3log.symphony.util.Symphonys.URL_PERMISSION_RULES.get("permission.rule.url./yuhu/admin/comments.GET");
                if (requisite != null) {
                    final String userId = currentUser.optString(org.b3log.latke.Keys.OBJECT_ID);
                    allow = org.b3log.latke.ioc.BeanManager.getInstance().getReference(org.b3log.symphony.service.RoleQueryService.class).userHasPermissions(userId, requisite);
                }
            } catch (Exception ignored) {
            }
            if (!allow) {
                if (org.apache.commons.lang.StringUtils.isBlank(bookId)) {
                    context.renderJSON(StatusCodes.ERR);
                    context.renderMsg("权限不足");
                    return;
                }
                final String linkedUserId = currentUser.optString(org.b3log.latke.Keys.OBJECT_ID);
                final org.json.JSONObject me = yuhuService.ensureProfile(linkedUserId);
                final org.json.JSONObject author = yuhuService.getAuthorByBook(bookId);
                if (!me.optString(org.b3log.latke.Keys.OBJECT_ID).equals(author.optString("profileId"))) {
                    context.renderJSON(StatusCodes.ERR);
                    context.renderMsg("权限不足");
                    return;
                }
            }
            final JSONObject ret = yuhuService.listCommentsAdmin(bookId, chapterId, profileId, status, q, page, size);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void updateComment(final RequestContext context) {
        try {
            final org.json.JSONObject currentUser = (org.json.JSONObject) context.attr(org.b3log.latke.model.User.USER);
            final String id = context.pathVar("id");
            final JSONObject req = context.requestJSON();
            boolean allow = false;
            try {
                final java.util.Set<String> requisite = org.b3log.symphony.util.Symphonys.URL_PERMISSION_RULES.get("permission.rule.url./yuhu/comment/{id}.PUT");
                if (requisite != null) {
                    final String userId = currentUser.optString(org.b3log.latke.Keys.OBJECT_ID);
                    allow = org.b3log.latke.ioc.BeanManager.getInstance().getReference(org.b3log.symphony.service.RoleQueryService.class).userHasPermissions(userId, requisite);
                }
            } catch (Exception ignored) {
            }
            if (!allow) {
                final String linkedUserId = currentUser.optString(org.b3log.latke.Keys.OBJECT_ID);
                final org.json.JSONObject me = yuhuService.ensureProfile(linkedUserId);
                final org.json.JSONObject c = yuhuService.getComment(id);
                if (c == null) {
                    context.renderJSON(StatusCodes.ERR);
                    context.renderMsg("资源不存在");
                    return;
                }
                final String bookId = c.optString(org.b3log.symphony.model.YuhuComment.YUHU_COMMENT_BOOK_ID);
                final org.json.JSONObject author = yuhuService.getAuthorByBook(bookId);
                if (!me.optString(org.b3log.latke.Keys.OBJECT_ID).equals(author.optString("profileId"))) {
                    context.renderJSON(StatusCodes.ERR);
                    context.renderMsg("权限不足");
                    return;
                }
            }
            yuhuService.updateComment(id, req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("updated", true));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void getAuthorProfile(final RequestContext context) {
        try {
            final String profileId = context.pathVar("profileId");
            final JSONObject ret = yuhuService.getAuthorProfile(profileId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void getAuthorByBook(final RequestContext context) {
        try {
            final String bookId = context.pathVar("bookId");
            final JSONObject ret = yuhuService.getAuthorByBook(bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void getAuthorStats(final RequestContext context) {
        try {
            final String profileId = context.pathVar("profileId");
            final JSONObject ret = yuhuService.getAuthorStats(profileId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void getAuthorProfileMe(final RequestContext context) {
        try {
            final String profileId = context.pathVar("profileId");
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            if (!profileId.equals(profile.optString(Keys.OBJECT_ID))) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("权限不足");
                return;
            }
            final JSONObject ret = yuhuService.getAuthorProfilePrivate(profileId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listAuthorBooks(final RequestContext context) {
        try {
            final String profileId = context.pathVar("profileId");
            final int page = org.apache.commons.lang.StringUtils.isBlank(context.param("page")) ? 1 : Integer.parseInt(context.param("page"));
            final int size = org.apache.commons.lang.StringUtils.isBlank(context.param("size")) ? 20 : Integer.parseInt(context.param("size"));
            final java.util.List<org.json.JSONObject> list = yuhuService.listAuthorBooks(profileId, page, size);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new org.json.JSONArray(list));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void addTag(final RequestContext context) {
        try {
            final JSONObject req = context.requestJSON();
            final String id = yuhuService.addTag(req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put(Keys.OBJECT_ID, id));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listTags(final RequestContext context) {
        try {
            final List<org.json.JSONObject> list = yuhuService.listTags();
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new org.json.JSONArray(list));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void bindTags(final RequestContext context) {
        try {
            final String bookId = context.pathVar("bookId");
            final JSONObject req = context.requestJSON();
            final org.json.JSONArray arr = req.optJSONArray("tags");
            final String[] aliases = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++)
                aliases[i] = arr.optString(i);
            yuhuService.bindTagsToBook(bookId, Arrays.asList(aliases));
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("updated", true));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void subscribe(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final String bookId = context.pathVar("bookId");
            yuhuService.subscribe(profile.optString(Keys.OBJECT_ID), bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("subscribed", true));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void unsubscribe(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final String bookId = context.pathVar("bookId");
            yuhuService.unsubscribe(profile.optString(Keys.OBJECT_ID), bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("subscribed", false));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void listSubscriptions(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final List<org.json.JSONObject> list = yuhuService.listSubscriptions(profile.optString(Keys.OBJECT_ID));
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new org.json.JSONArray(list));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void vote(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final JSONObject req = context.requestJSON();
            final JSONObject ret = yuhuService.vote(profile.optString(Keys.OBJECT_ID), req);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void voteStats(final RequestContext context) {
        try {
            final String bookId = context.param("bookId");
            final JSONObject ret = yuhuService.voteStats(bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void subscriptionStats(final RequestContext context) {
        try {
            final String bookId = context.param("bookId");
            final JSONObject ret = yuhuService.subscriptionStats(bookId);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(ret);
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void search(final RequestContext context) {
        context.renderJSON(StatusCodes.SUCC);
        context.renderData(new JSONObject().put("list", new org.json.JSONArray()).put("pagination", new JSONObject()));
    }

    public void toggleProfileDisplay(final RequestContext context) {
        try {
            final JSONObject user = Sessions.getUser();
            final String linkedUserId = user.optString(Keys.OBJECT_ID);
            final JSONObject profile = yuhuService.ensureProfile(linkedUserId);
            final JSONObject req = context.requestJSON();
            final boolean enabled = req.optBoolean("displayOverrideEnabled");
            profile.put("yuhuUserProfileDisplayOverrideEnabled", enabled);
            BeanManager.getInstance().getReference(org.b3log.symphony.repository.YuhuUserProfileRepository.class)
                    .update(profile.optString(Keys.OBJECT_ID), profile);
            context.renderJSON(StatusCodes.SUCC);
            context.renderData(new JSONObject().put("updated", true));
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("请求非法");
        }
    }

    public void showYuhuDashboard(final RequestContext context) {
        try {
            final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "yuhu/dashboard.ftl");
            final Map<String, Object> dataModel = renderer.getDataModel();
            final JSONObject user = (JSONObject) context.attr(User.USER);
            dataModel.put(User.USER, user);

            // final String bookId = context.pathVar("bookId");
            // final JSONObject ret = yuhuService.getBook(bookId);
            // dataModel.put("book",ret);

            dataModelService.fillHeaderAndFooter(context, dataModel);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}
