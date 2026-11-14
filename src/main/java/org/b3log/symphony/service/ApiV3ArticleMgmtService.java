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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LongArticleChapterRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ArticleCreation;
import org.b3log.symphony.util.ApiV3ArticleInputs;
import org.b3log.symphony.util.ApiV3ArticleEditInputs;
import org.b3log.symphony.util.ApiV3ArticleEditing;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ArticleSubmissionPolicy;
import org.b3log.symphony.util.ArticleValidation;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ApiV3ArticleMgmtService {
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;
    @Inject
    private ArticleMgmtService articles;
    @Inject
    private ArticleSubmissionMgmtService submissions;
    @Inject
    private ArticleValidationService validation;
    @Inject
    private ArticleDraftMgmtService drafts;
    @Inject
    private OptionRepository options;
    @Inject
    private UserRepository users;
    @Inject
    private RoleQueryService roles;
    @Inject
    private ArticleRepository repository;
    @Inject
    private LongArticleChapterRepository chapters;

    public JSONObject create(final JSONObject viewer, final JSONObject body, final String key, final String ip, final String ua) throws Exception {
        permission(viewer);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final JSONObject normalized = ApiV3ArticleInputs.normalize(body);
        final var publication = new AtomicReference<ArticleMgmtService.Publication>();
        final var execution = articles.withPublicationLock(actor, () -> idempotency.executeWithOutcome(actor, "articles.create", key, normalized, () -> {
            try {
                return ApiV3ArticleCreation.apply(new ApiV3ArticleCreation.Store() {
                    private JSONObject author;

                    public boolean inTransaction() {
                        return users.hasTransactionBegun();
                    }

                    public void lockAndAuthorize() throws Exception {
                        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT))
                            throw new IllegalStateException("article statistic option missing");
                        author = users.getForUpdate(actor);
                        if (null == author) throw ApiV3Requests.forbidden();
                        permission(author);
                    }

                    public void validate(final JSONObject request) {
                        validation.validate(request, author, true, ignored -> LogManager.getLogger(ApiV3ArticleMgmtService.class)
                                .info("v3 article rejected by moderation userId={}", actor));
                        ApiV3ArticleInputs.validateGoodPost(request);
                        final String reward = request.optString(Article.ARTICLE_REWARD_CONTENT);
                        if (!reward.isBlank() && CensorFactory.getTextCensor().censor(reward).isBlocked()) {
                            throw new ApiV3Exception(422, "content_rejected", "内容审核未通过，请修改后重试");
                        }
                    }

                    public ApiV3ArticleCreation.Created publish(final JSONObject request) throws Exception {
                        request.put(Article.ARTICLE_AUTHOR_ID, actor).put(Article.ARTICLE_IP, ip).put(Article.ARTICLE_UA, ua);
                        final var result = submissions.submitInCurrentTransaction(request);
                        publication.set(result);
                        return new ApiV3ArticleCreation.Created(result.article().optString(Keys.OBJECT_ID), result.article().optString(Article.ARTICLE_PERMALINK));
                    }

                    public void removeDraft(final String id) throws Exception {
                        try {
                            drafts.removeDraftInCurrentTransaction(actor, id);
                        } catch (final ServiceException e) {
                            throw new ApiV3Exception(404, "not_found", "草稿不存在或不可访问");
                        }
                    }
                }, normalized);
            } catch (final ArticleValidation.Rejected e) {
                throw new ApiV3Exception(e.status, e.code, "content_rejected".equals(e.code) ? "内容审核未通过，请修改后重试" : e.getMessage());
            } catch (final ArticleSubmissionPolicy.Rejected e) {
                final int status = "phone_required".equals(e.code) ? 403 : "curfew_interval".equals(e.code) ? 429 : 422;
                throw new ApiV3Exception(status, e.code, e.getMessage().replace("<br>", "\n"));
            } catch (final ServiceException e) {
                for (Throwable cause = e; null != cause; cause = cause.getCause())
                    if (cause instanceof RepositoryException) throw e;
                // 旧链路的重复标题错误可能暴露另一位作者，包括匿名作者。
                throw new ApiV3Exception(422, "publication_rejected", "发布条件未满足，请检查标题、余额和账号状态");
            }
        }), outcome -> {
            // 幂等回放只返回已提交的回执，不再次执行发布后的操作。
            if (outcome.applied()) articles.afterArticlePublished(publication.get());
        });
        return execution.result();
    }

    private void permission(final JSONObject actor) throws Exception {
        if (!Permission.hasPermission(Set.of(Permission.PERMISSION_ID_C_COMMON_ADD_ARTICLE), roles.getPermissions(actor.optString(User.USER_ROLE)))) {
            throw ApiV3Requests.forbidden();
        }
    }

    public JSONObject update(final JSONObject viewer, final String id, final JSONObject body, final String key, final String ip, final String ua) throws Exception {
        editPermission(viewer);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final JSONObject patch = ApiV3ArticleEditInputs.normalize(body);
        final var edited = new AtomicReference<ArticleMgmtService.Edit>();
        final var execution = articles.withPublicationLock(actor, () -> idempotency.executeWithOutcome(actor, "articles.update:" + id, key, patch, () -> {
            try {
                return ApiV3ArticleEditing.apply(new ApiV3ArticleEditing.Store() {
                    private JSONObject author;

                    public boolean inTransaction() {
                        return users.hasTransactionBegun();
                    }

                    public ApiV3ArticleEditing.State lockAndLoad() throws Exception {
                        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT))
                            throw new IllegalStateException("article statistic option missing");
                        author = users.getForUpdate(actor);
                        if (null == author) throw ApiV3Requests.forbidden();
                        editPermission(author);
                        final var row = repository.getOwnedForUpdate(id, actor);
                        return null == row ? null : new ApiV3ArticleEditing.State(row, chapters.getByArticleForUpdate(id));
                    }

                    public void validate(final JSONObject request) {
                        validation.validate(request, author, false, ignored -> LogManager.getLogger(ApiV3ArticleMgmtService.class)
                                .info("v3 article edit rejected by moderation userId={}", actor));
                        final String reward = request.optString(Article.ARTICLE_REWARD_CONTENT);
                        if (!reward.isBlank() && CensorFactory.getTextCensor().censor(reward).isBlocked())
                            throw new ApiV3Exception(422, "content_rejected", "内容审核未通过，请修改后重试");
                        String tags = request.optString(Article.ARTICLE_TAGS);
                        if (!Role.ROLE_ID_C_ADMIN.equals(author.optString(User.USER_ROLE)))
                            tags = articles.filterReservedTags(tags);
                        if (tags.isBlank() && request.optInt(Article.ARTICLE_TYPE) == Article.ARTICLE_TYPE_C_DISCUSSION)
                            tags = "机要";
                        if (tags.isBlank() && request.optInt(Article.ARTICLE_TYPE) == Article.ARTICLE_TYPE_C_THOUGHT)
                            tags = "思绪";
                        request.put(Article.ARTICLE_TAGS, tags);
                    }

                    public ApiV3ArticleEditing.Saved save(final JSONObject request) throws Exception {
                        request.put(Keys.OBJECT_ID, id).put(Article.ARTICLE_AUTHOR_ID, actor).put(Article.ARTICLE_IP, ip).put(Article.ARTICLE_UA, ua);
                        final var result = articles.updateArticleInCurrentTransaction(request, true);
                        edited.set(result);
                        return new ApiV3ArticleEditing.Saved(id, result.article().optString(Article.ARTICLE_PERMALINK));
                    }
                }, actor, patch);
            } catch (final ArticleValidation.Rejected e) {
                throw new ApiV3Exception(e.status, e.code, "content_rejected".equals(e.code) ? "内容审核未通过，请修改后重试" : e.getMessage());
            } catch (final ServiceException e) {
                for (Throwable cause = e; null != cause; cause = cause.getCause())
                    if (cause instanceof RepositoryException) throw e;
                throw new ApiV3Exception(422, "edit_rejected", "编辑条件未满足，请检查标题、悬赏和余额");
            }
        }), outcome -> {
            if (outcome.applied()) articles.afterArticleEdited(edited.get());
        });
        return execution.result();
    }

    public JSONObject remove(final JSONObject viewer, final String id, final String key) throws Exception {
        removePermission(viewer);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final var removed = new AtomicReference<ArticleMgmtService.Removal>();
        final var execution = articles.withPublicationLock(actor, () -> idempotency.executeWithOutcome(actor, "articles.remove:" + id, key, new JSONObject(), () -> {
            if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT))
                throw new IllegalStateException("article statistic option missing");
            final var author = users.getForUpdate(actor);
            if (null == author) throw ApiV3Requests.forbidden();
            removePermission(author);
            final var row = repository.getOwnedForUpdate(id, actor);
            if (null == row) throw new ApiV3Exception(404, "not_found", "文章不存在或不可访问");
            if (Article.ARTICLE_STATUS_C_VALID != row.optInt(Article.ARTICLE_STATUS))
                throw new ApiV3Exception(403, "article_locked", "文章已锁定");
            try {
                final var result = articles.removeArticleInCurrentTransaction(id, false);
                if (null == result) throw new IllegalStateException("locked article disappeared");
                removed.set(result);
                return new JSONObject().put("id", id);
            } catch (final ServiceException e) {
                for (Throwable cause = e; null != cause; cause = cause.getCause())
                    if (cause instanceof RepositoryException) throw e;
                throw new ApiV3Exception(422, "removal_rejected", e.getMessage());
            }
        }), outcome -> {
            if (outcome.applied()) articles.afterArticleRemoved(removed.get());
        });
        return execution.result();
    }

    private void removePermission(final JSONObject actor) throws Exception {
        if (!Permission.hasPermission(Set.of(Permission.PERMISSION_ID_C_COMMON_REMOVE_ARTICLE), roles.getPermissions(actor.optString(User.USER_ROLE))))
            throw ApiV3Requests.forbidden();
    }

    private void editPermission(final JSONObject actor) throws Exception {
        if (!Permission.hasPermission(Set.of(Permission.PERMISSION_ID_C_COMMON_UPDATE_ARTICLE), roles.getPermissions(actor.optString(User.USER_ROLE))))
            throw ApiV3Requests.forbidden();
    }
}
