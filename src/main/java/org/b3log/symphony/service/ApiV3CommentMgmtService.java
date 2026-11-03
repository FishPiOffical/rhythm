package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.util.ApiV3CommentTarget;
import org.b3log.symphony.util.ApiV3ArticlePolicy;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3CommentInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.CommentEditing;
import org.b3log.symphony.util.CommentValidation;
import org.b3log.symphony.util.CommentRemoval;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.json.JSONObject;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ApiV3CommentMgmtService {
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    @Inject private ArticleMgmtService articleWrites;
    @Inject private CommentMgmtService comments;
    @Inject private CommentValidationService validation;
    @Inject private ApiV3ContentQueryService content;
    @Inject private CommentRepository repository;
    @Inject private OptionRepository options;
    @Inject private UserRepository users;
    @Inject private RoleQueryService roles;
    @Inject private ArticleRepository articles;
    @Inject private LongArticleParagraphService paragraphs;
    @Inject private CommentRemovalMgmtService removal;
    @Inject private CommentThankMgmtService thanks;
    @Inject private CommentAcceptMgmtService acceptance;

    public JSONObject accept(final JSONObject viewer, final String id, final String key) throws Exception {
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final var expected = thanks.target(id);
        final var destinations = null == expected ? java.util.List.of(Pointtransfer.ID_C_SYS)
                : java.util.List.of(Pointtransfer.ID_C_SYS, expected.author());
        final var accepted = new AtomicReference<CommentAcceptMgmtService.Result>();
        final var outcome = articleWrites.withPublicationLocks(actor, destinations,
                () -> idempotency.executeWithOutcome(actor, "comments.accept:" + id, key, new JSONObject(), () -> {
            if (null == expected) throw ApiV3Requests.notFound();
            try {
                final var result = acceptance.acceptInCurrentTransaction(actor, expected, (sender, article, comment) -> {
                    final var author = users.getForUpdate(expected.author());
                    if (null == content.viewableArticle(article.optString(Keys.OBJECT_ID), sender)
                            || comment.optInt(Comment.COMMENT_STATUS) == Comment.COMMENT_STATUS_C_INVALID
                            || null == author || author.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID
                            || !ApiV3ContentUtil.canViewCommentContent(comment, article, sender)) throw ApiV3Requests.notFound();
                    CommentAcceptMgmtService.requireV3Question(article);
                });
                if (!id.equals(result.acceptedId())) throw new ApiV3Exception(409, "already_accepted", "该文章已采纳其他评论");
                accepted.set(result);
                return new JSONObject().put("id", id).put("accepted", true);
            } catch (final CommentAcceptMgmtService.Rejected e) {
                if ("not_found".equals(e.code)) throw ApiV3Requests.notFound();
                if ("forbidden".equals(e.code)) throw ApiV3Requests.forbidden();
                if ("target_changed".equals(e.code)) throw new ApiV3Exception(409, e.code, "评论已变化，请重试");
                throw new ApiV3Exception(422, e.code, "采纳条件未满足，请检查评论作者与积分余额");
            }
        }), execution -> { if (execution.applied()) acceptance.afterAccepted(accepted.get()); });
        return outcome.result();
    }

    public JSONObject thank(final JSONObject viewer, final String id, final String key) throws Exception {
        thankPermission(viewer);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final var expected = thanks.target(id);
        final var destinations = null == expected ? java.util.List.of(Pointtransfer.ID_C_SYS)
                : java.util.List.of(Pointtransfer.ID_C_SYS, expected.author());
        final var thanked = new AtomicReference<CommentThankMgmtService.Result>();
        final var outcome = articleWrites.withPublicationLocks(actor, destinations,
                () -> idempotency.executeWithOutcome(actor, "comments.thank:" + id, key, new JSONObject(), () -> {
            if (null == expected) throw ApiV3Requests.notFound();
            try {
                final var result = thanks.thankInCurrentTransaction(actor, expected, (sender, article, comment) -> {
                    thankPermission(sender);
                    if (null == content.viewableArticle(article.optString(Keys.OBJECT_ID), sender)
                            || !ApiV3ContentUtil.canViewCommentContent(comment, article, sender)) throw ApiV3Requests.notFound();
                });
                if (null == result) throw ApiV3Requests.notFound();
                thanked.set(result);
                return new JSONObject().put("id", id).put("thanked", true);
            } catch (final CommentThankMgmtService.Rejected e) {
                if ("target_changed".equals(e.code)) throw new ApiV3Exception(409, e.code, "评论已变化，请重试");
                throw new ApiV3Exception(422, e.code, "感谢条件未满足，请检查评论作者与积分余额");
            }
        }), execution -> { if (execution.applied()) thanks.afterThanked(thanked.get()); });
        return outcome.result();
    }

    private void thankPermission(final JSONObject actor) throws Exception {
        if (!Permission.hasPermission(Set.of(Permission.PERMISSION_ID_C_COMMON_THANK_COMMENT), roles.getPermissions(actor.optString(User.USER_ROLE)))) {
            throw ApiV3Requests.forbidden();
        }
    }

    /** Shared owner deletion joins its result and all business writes to one idempotency transaction. */
    public JSONObject remove(final JSONObject viewer, final String id, final String key) throws Exception {
        removalPermission(viewer);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final var removed = new AtomicReference<CommentRemoval.Result>();
        final var outcome = articleWrites.withPublicationLock(actor, () -> idempotency.executeWithOutcome(actor,
                "comments.remove:" + id, key, new JSONObject(), () -> {
            try {
                final var result = removal.removeInCurrentTransaction(actor, id, (author, article, comment) -> {
                    removalPermission(author);
                    if (null == content.viewableArticle(article.optString(Keys.OBJECT_ID), author)) throw ApiV3Requests.notFound();
                });
                removed.set(result);
                return new JSONObject().put("id", id).put("removed", true);
            } catch (final CommentRemoval.Rejected e) {
                if ("not_found".equals(e.code)) throw ApiV3Requests.notFound();
                if ("forbidden".equals(e.code)) throw ApiV3Requests.forbidden();
                throw new ApiV3Exception(422, e.code, "删除条件未满足，请检查评论互动和积分余额");
            }
        }), execution -> { if (execution.applied()) removal.afterRemoved(removed.get()); });
        return outcome.result();
    }

    private void removalPermission(final JSONObject actor) throws Exception {
        if (!Permission.hasPermission(Set.of(Permission.PERMISSION_ID_C_COMMON_REMOVE_COMMENT), roles.getPermissions(actor.optString(User.USER_ROLE)))) {
            throw ApiV3Requests.forbidden();
        }
    }

    public JSONObject create(final JSONObject viewer, final String articleId, final JSONObject body, final String key,
                             final String ip, final String ua) throws Exception {
        final JSONObject normalized = ApiV3CommentInputs.create(body);
        publicationPermission(viewer, normalized);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final String expectedAuthor = articles.getCommentPublicationAuthor(articleId);
        final var published = new AtomicReference<CommentMgmtService.Publication>();
        final var destinations = expectedAuthor.isEmpty() ? java.util.List.of(Pointtransfer.ID_C_SYS)
                : java.util.List.of(Pointtransfer.ID_C_SYS, expectedAuthor);
        final var outcome = articleWrites.withPublicationLocks(actor, destinations,
                () -> idempotency.executeWithOutcome(actor, "comments.create:" + articleId, key, normalized, () -> {
            if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
            if (expectedAuthor.isEmpty()) throw ApiV3Requests.notFound();
            for (final String participant : java.util.List.of(actor, expectedAuthor).stream().distinct().sorted().toList()) {
                if (null == users.getForUpdate(participant)) throw ApiV3Requests.notFound();
            }
            final var author = users.getForUpdate(actor);
            if (author.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.forbidden();
            publicationPermission(author, normalized);
            if (author.optString("userPhone").isBlank()) throw new ApiV3Exception(403, "phone_required", "请先绑定手机号码");
            final var lockedArticle = articles.getV3CommentPolicyForUpdate(articleId);
            if (null == lockedArticle) throw ApiV3Requests.notFound();
            final var article = content.viewableArticle(articleId, author);
            if (null == article) throw ApiV3Requests.notFound();
            if (!expectedAuthor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))) {
                throw new ApiV3Exception(409, "target_changed", "文章已变化，请重试");
            }
            if (article.optInt(Article.ARTICLE_STATUS) != Article.ARTICLE_STATUS_C_VALID) {
                throw new ApiV3Exception(403, "comments_closed", "文章当前不可评论");
            }
            ApiV3ArticlePolicy.requireCommenting(lockedArticle);
            final var request = new JSONObject(normalized.toString());
            final String parentId = request.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID);
            final var parent = parentId.isEmpty() ? null : repository.get(parentId);
            LongArticleParagraphService.CompatibleContent paragraphView = null;
            final String suppliedParagraph = request.optString(Comment.COMMENT_PARAGRAPH_ID);
            if (!suppliedParagraph.isEmpty() && (parentId.isEmpty() || null != parent
                    && parent.optInt(Comment.COMMENT_TYPE) == Comment.COMMENT_TYPE_C_PARAGRAPH
                    && !suppliedParagraph.equals(parent.optString(Comment.COMMENT_PARAGRAPH_ID)))) {
                paragraphView = paragraphs.compatibleMarkdown(articleId, article.optString(Article.ARTICLE_CONTENT), content.paragraphTitleSnapshot());
                final String canonical = paragraphView.canonicalId(suppliedParagraph);
                if (null == parent && null != canonical) request.put(Comment.COMMENT_PARAGRAPH_ID, canonical);
                else if (null != parent && null != canonical && canonical.equals(paragraphView.canonicalId(parent.optString(Comment.COMMENT_PARAGRAPH_ID)))) {
                    request.put(Comment.COMMENT_PARAGRAPH_ID, parent.optString(Comment.COMMENT_PARAGRAPH_ID));
                }
            }
            final var resolvedParagraphs = paragraphView;
            ApiV3CommentTarget.prepare(request, article, parent, author, paragraphId -> {
                final var paragraph = null == resolvedParagraphs ? null : resolvedParagraphs.find(paragraphId);
                return null == paragraph ? null : new ApiV3CommentTarget.Paragraph(paragraph.getId(), paragraph.getKind(), paragraph.getIndex(), paragraph.getSnapshot());
            });
            try {
                validation.prepare(request, author, true);
                validation.moderate(request, ignored -> LogManager.getLogger(ApiV3CommentMgmtService.class)
                        .info("v3 comment publish rejected by moderation userId={}", actor));
                request.put(Comment.COMMENT_AUTHOR_ID, actor).put(Comment.COMMENT_ON_ARTICLE_ID, articleId)
                        .put(Comment.COMMENT_IP, ip).put(Comment.COMMENT_UA, ua)
                        .put(UserExt.USER_COMMENT_VIEW_MODE, author.optInt(UserExt.USER_COMMENT_VIEW_MODE));
                final var result = comments.addCommentInCurrentTransaction(request, expectedAuthor, true);
                published.set(result);
                return new JSONObject().put("id", result.comment().optString(Keys.OBJECT_ID))
                        .put("permalink", result.comment().optString(Comment.COMMENT_SHARP_URL));
            } catch (final CommentValidation.Rejected e) {
                throw new ApiV3Exception(e.status, e.code, "content_rejected".equals(e.code) ? "内容审核未通过，请修改后重试" : e.getMessage());
            } catch (final ServiceException e) {
                for (Throwable cause = e; null != cause; cause = cause.getCause()) if (cause instanceof RepositoryException) throw e;
                throw new ApiV3Exception(422, "publication_rejected", "发布条件未满足，请检查余额、发言间隔和账号状态");
            }
        }), execution -> { if (execution.applied()) comments.afterCommentPublished(published.get()); });
        return outcome.result();
    }

    private void publicationPermission(final JSONObject actor, final JSONObject request) throws Exception {
        final var required = new java.util.HashSet<String>();
        required.add(Permission.PERMISSION_ID_C_COMMON_ADD_COMMENT);
        if (request.optInt(Comment.COMMENT_ANONYMOUS) == Comment.COMMENT_ANONYMOUS_C_ANONYMOUS) {
            required.add(Permission.PERMISSION_ID_C_COMMON_ADD_COMMENT_ANONYMOUS);
        }
        if (!Permission.hasPermission(required, roles.getPermissions(actor.optString(User.USER_ROLE)))) throw ApiV3Requests.forbidden();
    }

    public JSONObject update(final JSONObject viewer, final String id, final JSONObject body, final String key,
                             final String ip, final String ua) throws Exception {
        permission(viewer);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final JSONObject normalized = ApiV3CommentInputs.edit(body);
        final var edited = new AtomicReference<CommentEditing.Result>();
        final var outcome = articleWrites.withPublicationLock(actor, () -> idempotency.executeWithOutcome(actor, "comments.update:" + id, key, normalized, () -> {
            if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
            final var author = users.getForUpdate(actor);
            if (null == author) throw ApiV3Requests.forbidden();
            permission(author);
            final var target = repository.getOwnedEditTarget(id, actor);
            if (null == target) throw ApiV3Requests.notFound();
            final var lockedArticle = articles.getV3CommentPolicyForUpdate(target.optString(Comment.COMMENT_ON_ARTICLE_ID));
            if (null == lockedArticle) throw ApiV3Requests.notFound();
            final var article = content.viewableArticle(target.optString(Comment.COMMENT_ON_ARTICLE_ID), author);
            if (null == article) throw ApiV3Requests.notFound();
            if (article.optInt(Article.ARTICLE_STATUS) != Article.ARTICLE_STATUS_C_VALID) throw new ApiV3Exception(403, "article_locked", "文章已锁定");
            ApiV3ArticlePolicy.requireCommenting(lockedArticle);
            final var current = repository.getOwnedForUpdate(id, actor);
            if (null == current) throw ApiV3Requests.notFound();
            if (current.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID) throw new ApiV3Exception(403, "comment_locked", "评论不可编辑");
            final var request = new JSONObject(normalized.toString());
            try {
                validation.prepare(request, author, false);
                validation.moderate(request, ignored -> LogManager.getLogger(ApiV3CommentMgmtService.class)
                        .info("v3 comment edit rejected by moderation userId={}", actor));
                request.put(Comment.COMMENT_AUTHOR_ID, actor).put(Comment.COMMENT_IP, ip).put(Comment.COMMENT_UA, ua);
                final var result = comments.updateCommentInCurrentTransaction(id, request);
                edited.set(result);
                return new JSONObject().put("id", id).put("permalink", result.comment().optString(Comment.COMMENT_SHARP_URL));
            } catch (final CommentValidation.Rejected e) {
                throw new ApiV3Exception(e.status, e.code, "content_rejected".equals(e.code) ? "内容审核未通过，请修改后重试" : e.getMessage());
            } catch (final ServiceException e) {
                for (Throwable cause = e; null != cause; cause = cause.getCause()) if (cause instanceof RepositoryException) throw e;
                throw new ApiV3Exception(422, "edit_rejected", "编辑条件未满足，请检查余额和账号状态");
            }
        }), execution -> { if (execution.applied()) comments.afterCommentEdited(edited.get()); });
        return outcome.result();
    }
    private void permission(final JSONObject actor) throws Exception {
        if (!Permission.hasPermission(Set.of(Permission.PERMISSION_ID_C_COMMON_UPDATE_COMMENT), roles.getPermissions(actor.optString(User.USER_ROLE)))) {
            throw ApiV3Requests.forbidden();
        }
    }
}
