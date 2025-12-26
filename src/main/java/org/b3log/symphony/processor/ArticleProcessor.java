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

import jodd.util.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.http.renderer.AbstractResponseRenderer;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.*;
import org.b3log.latke.util.Strings;
import org.b3log.symphony.cache.DomainCache;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.channel.ArticleChannel;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.PermissionMidware;
import org.b3log.symphony.processor.middleware.validate.ArticlePostValidationMidware;
import org.b3log.symphony.processor.middleware.validate.UserRegisterValidationMidware;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Article processor.
 * <ul>
 * <li>Shows an article (/article/{articleId}), GET</li>
 * <li>Shows article pre adding form page (/pre-post), GET</li>
 * <li>Shows article adding form page (/post), GET</li>
 * <li>Adds an article (/post) <em>locally</em>, POST</li>
 * <li>Shows an article updating form page (/update) <em>locally</em>, GET</li>
 * <li>Updates an article (/article/{id}) <em>locally</em>, PUT</li>
 * <li>Markdowns text (/markdown), POST</li>
 * <li>Rewards an article (/article/reward), POST</li>
 * <li>Gets an article preview content (/article/{articleId}/preview), GET</li>
 * <li>Sticks an article (/article/stick), POST</li>
 * <li>Gets an article's revisions (/article/{id}/revisions), GET</li>
 * <li>Gets article image (/article/{articleId}/image), GET</li>
 * <li>Checks article title (/article/check-title), POST</li>
 * <li>Removes an article (/article/{id}/remove), POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @author <a href="https://qiankunpingtai.cn">qiankunpingtai</a>
 * @version 2.0.1.0, May 11, 2020
 * @since 0.2.0
 */
@Singleton
public class ArticleProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ArticleProcessor.class);

    /**
     * 文章访客限流：单 IP 每秒仅允许一次。
     */
    private static final ConcurrentHashMap<String, Long> ARTICLE_RATE_LIMIT = new ConcurrentHashMap<>();

    /**
     * Revision query service.
     */
    @Inject
    private RevisionQueryService revisionQueryService;

    /**
     * Short link query service.
     */
    @Inject
    private ShortLinkQueryService shortLinkQueryService;

    /**
     * Article management service.
     */
    @Inject
    private ArticleMgmtService articleMgmtService;

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * Comment query service.
     */
    @Inject
    private CommentQueryService commentQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Follow query service.
     */
    @Inject
    private FollowQueryService followQueryService;

    /**
     * Reward query service.
     */
    @Inject
    private RewardQueryService rewardQueryService;

    /**
     * Vote query service.
     */
    @Inject
    private VoteQueryService voteQueryService;

    /**
     * Liveness management service.
     */
    @Inject
    private LivenessMgmtService livenessMgmtService;

    /**
     * Referral management service.
     */
    @Inject
    private ReferralMgmtService referralMgmtService;

    /**
     * Character query service.
     */
    @Inject
    private CharacterQueryService characterQueryService;

    /**
     * Domain query service.
     */
    @Inject
    private DomainQueryService domainQueryService;

    /**
     * Domain cache.
     */
    @Inject
    private DomainCache domainCache;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Cloud service.
     */
    @Inject
    private CloudService cloudService;

    @Inject
    private TagQueryService tagQueryService;

    @Inject
    private AvatarQueryService avatarQueryService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final PermissionMidware permissionMidware = beanManager.getReference(PermissionMidware.class);
        final AnonymousViewCheckMidware anonymousViewCheckMidware = beanManager.getReference(AnonymousViewCheckMidware.class);
        final ArticlePostValidationMidware articlePostValidationMidware = beanManager.getReference(ArticlePostValidationMidware.class);

        final ArticleProcessor articleProcessor = beanManager.getReference(ArticleProcessor.class);
        Dispatcher.post("/article/{id}/remove", articleProcessor::removeArticle, loginCheck::handle, permissionMidware::check);
        Dispatcher.post("/article/check-title", articleProcessor::checkArticleTitle, loginCheck::handle);
        Dispatcher.get("/article/{articleId}/image", articleProcessor::getArticleImage, loginCheck::handle);
        Dispatcher.get("/article/{id}/revisions", articleProcessor::getArticleRevisions, loginCheck::handle, permissionMidware::check);
        Dispatcher.get("/pre-post", articleProcessor::showPreAddArticle, loginCheck::handle, csrfMidware::fill);
        Dispatcher.get("/post", articleProcessor::showAddArticle, loginCheck::handle, csrfMidware::fill);
        Dispatcher.group().middlewares(anonymousViewCheckMidware::handle, csrfMidware::fill).router().get().uris(new String[]{"/article/{articleId}", "/article/{articleId}/comment/{commentId}"}).handler(articleProcessor::showArticle);
        Dispatcher.post("/article", articleProcessor::addArticle, loginCheck::handle, permissionMidware::check, articlePostValidationMidware::handle);
        Dispatcher.get("/update", articleProcessor::showUpdateArticle, loginCheck::handle, csrfMidware::fill);
        Dispatcher.put("/article/{id}", articleProcessor::updateArticle, loginCheck::handle, permissionMidware::check, articlePostValidationMidware::handle);
        Dispatcher.post("/markdown", articleProcessor::markdown2HTML);
        Dispatcher.get("/article/{articleId}/preview", articleProcessor::getArticlePreviewContent);
        Dispatcher.post("/article/reward", articleProcessor::rewardArticle, loginCheck::handle);
        Dispatcher.post("/article/thank", articleProcessor::thankArticle, loginCheck::handle, permissionMidware::check);
        Dispatcher.post("/article/stick", articleProcessor::stickArticle, loginCheck::handle, permissionMidware::check);
        Dispatcher.get("/article/random/{size}", articleProcessor::randomArticles);
        Dispatcher.group().middlewares(loginCheck::handle).router().get().uris(new String[]{"/api/articles/recent", "/api/articles/recent/hot", "/api/articles/recent/good", "/api/articles/recent/reply"}).handler(articleProcessor::getArticles);
        Dispatcher.group().middlewares(loginCheck::handle).router().get().uris(new String[]{"/api/articles/tag/{tagURI}", "/api/articles/tag/{tagURI}/hot", "/api/articles/tag/{tagURI}/good", "/api/articles/tag/{tagURI}/reply", "/api/articles/tag/{tagURI}/perfect"}).handler(articleProcessor::getTagArticles);
        Dispatcher.get("/api/articles/domain/{domainURI}", articleProcessor::getDomainArticles, loginCheck::handle);
        Dispatcher.get("/api/article/{id}", articleProcessor::showArticleApi, loginCheck::handle);
        Dispatcher.get("/api/article/heat/{articleId}", articleProcessor::getArticleHeat);
        Dispatcher.get("/api/comment/{id}", articleProcessor::showCommentApi, loginCheck::handle);
        Dispatcher.get("/api/article/md/{id}", articleProcessor::showArticleMdApi, loginCheck::handle);
    }

    public void getArticleHeat(final RequestContext context) {
        String articleId = context.pathVar("articleId");
        if (articleId != null) {
            Integer viewingCnt = ArticleChannel.ARTICLE_VIEWS.get(articleId);
            if (null == viewingCnt) {
                viewingCnt = 0;
            }

            context.renderJSON(new JSONObject().put(Article.ARTICLE_T_HEAT, viewingCnt));
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("文章不存在");
        }
    }

    private AbstractResponseRenderer buildJsonRenderer() {
        JsonRenderer renderer = new JsonRenderer();
        renderer.setJSONObject(new JSONObject());
        return renderer;
    }

    public void showCommentApi(final RequestContext context) {
        final AbstractResponseRenderer renderer = buildJsonRenderer();
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = new HashMap<>();
        final String articleId = context.pathVar("id");
        final Request request = context.getRequest();

        final JSONObject article = articleQueryService.getArticleById(articleId);
        if (null == article) {
            context.renderCodeMsg(404, "帖子不存在!");
            return;
        }

        final int cmtViewMode = 0;
        JSONObject currentUser = Sessions.getUser();
        String currentUserId = currentUser.optString(Keys.OBJECT_ID);

        int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.ARTICLE_COMMENTS_CNT;
        final int windowSize = Symphonys.ARTICLE_COMMENTS_WIN_SIZE;
        final int commentCnt = article.getInt(Article.ARTICLE_COMMENT_CNT);
        final int pageCount = (int) Math.ceil((double) commentCnt / (double) pageSize);
        // 回帖分页 SEO https://github.com/b3log/symphony/issues/813
        if (UserExt.USER_COMMENT_VIEW_MODE_C_TRADITIONAL == cmtViewMode) {
            if (0 < pageCount && pageNum > pageCount) {
                pageNum = pageCount;
            }
        } else {
            if (pageNum > pageCount) {
                pageNum = 1;
            }
        }
        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);

        JSONObject pagination = new JSONObject();
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put("pagination", pagination);

        articleQueryService.processArticleContent(article);

        JSONObject result = new JSONObject();

        if (!article.optBoolean(Common.DISCUSSION_VIEWABLE)) {
            result.put(Article.ARTICLE_T_COMMENTS, (Object) Collections.emptyList());
            result.put(Article.ARTICLE_T_NICE_COMMENTS, (Object) Collections.emptyList());
            return;
        }

        List<JSONObject> niceComments = new ArrayList<>();
        if (pageNum == 1) {
            niceComments = commentQueryService.getNiceComments(cmtViewMode, articleId, 3);
            result.put(Article.ARTICLE_T_NICE_COMMENTS, (Object) niceComments);
        }

        // Load comments
        final List<JSONObject> articleComments = commentQueryService.getArticleComments(articleId, pageNum, pageSize, cmtViewMode);
        result.put(Article.ARTICLE_T_COMMENTS, (Object) articleComments);

        final String articleAuthorId = article.optString(Article.ARTICLE_AUTHOR_ID);

        for (final JSONObject comment : niceComments) {
            comment.remove("commentUA");
            comment.remove("commentIP");
            final JSONObject commenter = comment.optJSONObject("commenter");
            commenter.remove("userPassword");
            commenter.remove("userLatestLoginIP");
            commenter.remove("userPhone");
            commenter.remove("userQQ");
            commenter.remove("userCity");
            commenter.remove("userCountry");
            commenter.remove("userEmail");
            commenter.remove("secret2fa");

            String thankTemplate = langPropsService.get("thankConfirmLabel");
            thankTemplate = thankTemplate.replace("{point}", String.valueOf(Symphonys.POINT_THANK_COMMENT))
                    .replace("{user}", comment.optJSONObject(Comment.COMMENT_T_COMMENTER).optString(User.USER_NAME));
            comment.put(Comment.COMMENT_T_THANK_LABEL, thankTemplate);

            final String commentId = comment.optString(Keys.OBJECT_ID);

            comment.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, commentId, Reward.TYPE_C_COMMENT));
            final int commentVote = voteQueryService.isVoted(currentUserId, commentId);
            comment.put(Comment.COMMENT_T_VOTE, commentVote);

            comment.put(Common.REWARED_COUNT, comment.optInt(Comment.COMMENT_THANK_CNT));

            // https://github.com/b3log/symphony/issues/682
            if (Comment.COMMENT_VISIBLE_C_AUTHOR == comment.optInt(Comment.COMMENT_VISIBLE)) {
                final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
                if ((!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                    comment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
                }
            }
        }

        for (final JSONObject comment : articleComments) {
            comment.remove("commentUA");
            comment.remove("commentIP");
            final JSONObject commenter = comment.optJSONObject("commenter");
            commenter.remove("userPassword");
            commenter.remove("userLatestLoginIP");
            commenter.remove("userPhone");
            commenter.remove("userQQ");
            commenter.remove("userCity");
            commenter.remove("userCountry");
            commenter.remove("userEmail");
            commenter.remove("secret2fa");

            final String commentId = comment.optString(Keys.OBJECT_ID);

            comment.put(Common.REWARDED,
                    rewardQueryService.isRewarded(currentUserId, commentId, Reward.TYPE_C_COMMENT));
            final int commentVote = voteQueryService.isVoted(currentUserId, commentId);
            comment.put(Comment.COMMENT_T_VOTE, commentVote);
            comment.put(Common.REWARED_COUNT, comment.optInt(Comment.COMMENT_THANK_CNT));

            // https://github.com/b3log/symphony/issues/682
            if (Comment.COMMENT_VISIBLE_C_AUTHOR == comment.optInt(Comment.COMMENT_VISIBLE)) {
                final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
                if ((!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                    comment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
                }
            }
        }

        context.renderData(result).renderCode(StatusCodes.SUCC).renderMsg("");
    }

    public void showArticleMdApi(final RequestContext context) {
        final String articleId = context.pathVar("id");

        final JSONObject article = articleQueryService.getArticleById(articleId);
        if (null == article) {
            context.renderCodeMsg(404, "帖子不存在!");
            return;
        }

        String md = article.optString(Article.ARTICLE_T_ORIGINAL_CONTENT);

        context.getResponse().setContentType("text/html; charset=UTF-8");
        context.getResponse().sendBytes(md.getBytes());
    }

    /**
     * 简单的每 IP 1 秒一次限流，访客和登录用户共用。
     *
     * @param request the specified request
     * @return true if limited
     */
    private boolean hitArticleRateLimit(final Request request) {
        final String ip = Requests.getRemoteAddr(request);
        final long now = System.currentTimeMillis();
        final Long last = ARTICLE_RATE_LIMIT.get(ip);
        if (null != last && now - last < 1000) {
            return true;
        }

        ARTICLE_RATE_LIMIT.put(ip, now);

        // 防止表过大，简单清理 60 秒前的记录
        if (ARTICLE_RATE_LIMIT.size() > 10000) {
            final long cutoff = now - 60_000;
            ARTICLE_RATE_LIMIT.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        return false;
    }

    /**
     * api for get article details
     *
     * @param context the specified context
     */
    public void showArticleApi(final RequestContext context) {
        final AbstractResponseRenderer renderer = buildJsonRenderer();
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = new HashMap<>();
        final String articleId = context.pathVar("id");
        final Request request = context.getRequest();

        if (hitArticleRateLimit(request)) {
            context.renderCodeMsg(503, "访问过快，请稍候再试");
            return;
        }

        final JSONObject article = articleQueryService.getArticleById(articleId);
        if (null == article) {
            context.renderCodeMsg(404, "帖子不存在!");
            return;
        }

        final String articleAuthorId = article.optString(Article.ARTICLE_AUTHOR_ID);
        JSONObject author;
        if (Article.ARTICLE_ANONYMOUS_C_PUBLIC == article.optInt(Article.ARTICLE_ANONYMOUS)) {
            author = userQueryService.getUser(articleAuthorId);
        } else {
            author = userQueryService.getAnonymousUser();
        }
        Escapes.escapeHTML(author);
        article.put(Article.ARTICLE_T_AUTHOR_NAME, author.optString(User.USER_NAME));
        article.put(Article.ARTICLE_T_AUTHOR_URL, author.optString(User.USER_URL));
        article.put(Article.ARTICLE_T_AUTHOR_INTRO, author.optString(UserExt.USER_INTRO));
        article.put("articleAuthorNickName", author.optString(UserExt.USER_NICKNAME));

        String metal = cloudService.getEnabledMedal(articleAuthorId);
        if (!metal.equals("{}")) {
            List<Object> list = new JSONObject(metal).optJSONArray("list").toList();
            author.put("sysMetal", list);
        } else {
            author.put("sysMetal", new ArrayList<>());
        }



        article.put(Common.IS_MY_ARTICLE, false);
        article.put(Article.ARTICLE_T_AUTHOR, author);
        article.put(Common.REWARDED, false);
        article.put(Common.REWARED_COUNT, rewardQueryService.rewardedCount(articleId, Reward.TYPE_C_ARTICLE));
        article.put(Article.ARTICLE_REVISION_COUNT, revisionQueryService.count(articleId, Revision.DATA_TYPE_C_ARTICLE));

        articleQueryService.processArticleContent(article);

        final int cmtViewMode = 0;
        JSONObject currentUser = Sessions.getUser();
        String currentUserId = currentUser.optString(Keys.OBJECT_ID);

        final boolean isMyArticle = currentUserId.equals(articleAuthorId);
        article.put(Common.IS_MY_ARTICLE, isMyArticle);

        final boolean isFollowing = followQueryService.isFollowing(currentUserId, articleId, Follow.FOLLOWING_TYPE_C_ARTICLE);
        article.put(Common.IS_FOLLOWING, isFollowing);

        final boolean isWatching = followQueryService.isFollowing(currentUserId, articleId, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH);
        article.put(Common.IS_WATCHING, isWatching);

        final int articleVote = voteQueryService.isVoted(currentUserId, articleId);
        article.put(Article.ARTICLE_T_VOTE, articleVote);

        if (isMyArticle) {
            article.put(Common.REWARDED, true);
        } else {
            article.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, articleId, Reward.TYPE_C_ARTICLE));
        }


        //加活跃
        livenessMgmtService.incLiveness(currentUserId, Liveness.LIVENESS_PV);


        if (!Sessions.isBot()) {
            final long created = System.currentTimeMillis();
            final long expired = DateUtils.addMonths(new Date(created), 1).getTime();
            final String ip = Requests.getRemoteAddr(request);
            final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
            final String referer = Headers.getHeader(request, "Referer", "");
            final JSONObject visit = new JSONObject();
            visit.put(Visit.VISIT_IP, ip);
            visit.put(Visit.VISIT_CITY, "");
            visit.put(Visit.VISIT_CREATED, created);
            visit.put(Visit.VISIT_DEVICE_ID, "");
            visit.put(Visit.VISIT_EXPIRED, expired);
            visit.put(Visit.VISIT_REFERER_URL, referer);
            visit.put(Visit.VISIT_UA, ua);
            visit.put(Visit.VISIT_URL, "/article/" + articleId);
            visit.put(Visit.VISIT_USER_ID, "");
            visit.put(Visit.VISIT_USER_ID, currentUser);

            articleMgmtService.incArticleViewCount(visit);
        }

        // Fill article thank
        Stopwatchs.start("Fills article thank");
        try {
            article.put(Common.THANKED, rewardQueryService.isRewarded(currentUserId, articleId, Reward.TYPE_C_THANK_ARTICLE));
            article.put(Common.THANKED_COUNT, article.optInt(Article.ARTICLE_THANK_CNT));
            if (Article.ARTICLE_TYPE_C_QNA == article.optInt(Article.ARTICLE_TYPE)) {
                article.put(Common.OFFERED, rewardQueryService.isRewarded(articleAuthorId, articleId, Reward.TYPE_C_ACCEPT_COMMENT));
                final JSONObject offeredComment = commentQueryService.getOfferedComment(cmtViewMode, articleId);
                article.put(Article.ARTICLE_T_OFFERED_COMMENT, offeredComment);
                if (null != offeredComment) {
                    if (Comment.COMMENT_VISIBLE_C_AUTHOR == offeredComment.optInt(Comment.COMMENT_VISIBLE)) {
                        final String commentAuthorId = offeredComment.optString(Comment.COMMENT_AUTHOR_ID);
                        if ((!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                            offeredComment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
                        }
                    }
                    final String offeredCmtId = offeredComment.optString(Keys.OBJECT_ID);
                    final int rewardCount = offeredComment.optInt(Comment.COMMENT_THANK_CNT);
                    offeredComment.put(Common.REWARED_COUNT, rewardCount);
                    offeredComment.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, offeredCmtId, Reward.TYPE_C_COMMENT));
                }
            }
        } finally {
            Stopwatchs.end();
        }

        int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.ARTICLE_COMMENTS_CNT;
        final int windowSize = Symphonys.ARTICLE_COMMENTS_WIN_SIZE;
        final int commentCnt = article.getInt(Article.ARTICLE_COMMENT_CNT);
        final int pageCount = (int) Math.ceil((double) commentCnt / (double) pageSize);
        // 回帖分页 SEO https://github.com/b3log/symphony/issues/813
        if (UserExt.USER_COMMENT_VIEW_MODE_C_TRADITIONAL == cmtViewMode) {
            if (0 < pageCount && pageNum > pageCount) {
                pageNum = pageCount;
            }
        } else {
            if (pageNum > pageCount) {
                pageNum = 1;
            }
        }
        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);

        JSONObject pagination = new JSONObject();
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put("pagination", pagination);

        if (!article.optBoolean(Common.DISCUSSION_VIEWABLE)) {
            article.put(Article.ARTICLE_T_COMMENTS, (Object) Collections.emptyList());
            article.put(Article.ARTICLE_T_NICE_COMMENTS, (Object) Collections.emptyList());
            return;
        }

        final List<JSONObject> niceComments = commentQueryService.getNiceComments(cmtViewMode, articleId, 3);
        article.put(Article.ARTICLE_T_NICE_COMMENTS, (Object) niceComments);

        double niceCmtScore = Double.MAX_VALUE;
        if (!niceComments.isEmpty()) {
            niceCmtScore = niceComments.get(niceComments.size() - 1).optDouble(Comment.COMMENT_SCORE, 0D);

            for (final JSONObject comment : niceComments) {
                comment.remove("commentUA");
                comment.remove("commentIP");
                final JSONObject commenter = comment.optJSONObject("commenter");
                commenter.remove("userPassword");
                commenter.remove("userLatestLoginIP");
                commenter.remove("userPhone");
                commenter.remove("userQQ");
                commenter.remove("userCity");
                commenter.remove("userCountry");
                commenter.remove("userEmail");
                commenter.remove("secret2fa");

                String thankTemplate = langPropsService.get("thankConfirmLabel");
                thankTemplate = thankTemplate.replace("{point}", String.valueOf(Symphonys.POINT_THANK_COMMENT))
                        .replace("{user}", comment.optJSONObject(Comment.COMMENT_T_COMMENTER).optString(User.USER_NAME));
                comment.put(Comment.COMMENT_T_THANK_LABEL, thankTemplate);

                final String commentId = comment.optString(Keys.OBJECT_ID);

                comment.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, commentId, Reward.TYPE_C_COMMENT));
                final int commentVote = voteQueryService.isVoted(currentUserId, commentId);
                comment.put(Comment.COMMENT_T_VOTE, commentVote);

                comment.put(Common.REWARED_COUNT, comment.optInt(Comment.COMMENT_THANK_CNT));

                // https://github.com/b3log/symphony/issues/682
                if (Comment.COMMENT_VISIBLE_C_AUTHOR == comment.optInt(Comment.COMMENT_VISIBLE)) {
                    final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
                    if ((!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                        comment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
                    }
                }
            }
        }

        // Load comments
        final List<JSONObject> articleComments = commentQueryService.getArticleComments(articleId, pageNum, pageSize, cmtViewMode);
        article.put(Article.ARTICLE_T_COMMENTS, (Object) articleComments);

        // Fill comment thank
        Stopwatchs.start("Fills comment thank");
        try {
            for (final JSONObject comment : articleComments) {
                comment.remove("commentUA");
                comment.remove("commentIP");
                final JSONObject commenter = comment.optJSONObject("commenter");
                commenter.remove("userPassword");
                commenter.remove("userLatestLoginIP");
                commenter.remove("userPhone");
                commenter.remove("userQQ");
                commenter.remove("userCity");
                commenter.remove("userCountry");
                commenter.remove("userEmail");
                commenter.remove("secret2fa");
                comment.put(Comment.COMMENT_T_NICE, comment.optDouble(Comment.COMMENT_SCORE, 0D) >= niceCmtScore);

                final String commentId = comment.optString(Keys.OBJECT_ID);

                comment.put(Common.REWARDED,
                        rewardQueryService.isRewarded(currentUserId, commentId, Reward.TYPE_C_COMMENT));
                final int commentVote = voteQueryService.isVoted(currentUserId, commentId);
                comment.put(Comment.COMMENT_T_VOTE, commentVote);
                comment.put(Common.REWARED_COUNT, comment.optInt(Comment.COMMENT_THANK_CNT));

                // https://github.com/b3log/symphony/issues/682
                if (Comment.COMMENT_VISIBLE_C_AUTHOR == comment.optInt(Comment.COMMENT_VISIBLE)) {
                    final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
                    if ((!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                        comment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
                    }
                }
            }
        } finally {
            Stopwatchs.end();
        }
        dataModel.put(Article.ARTICLE, DesensitizeUtil.articleDesensitize(article));
        context.renderJSON(new JSONObject().put("data", dataModel)).renderCode(StatusCodes.SUCC).renderMsg("");
    }


    /**
     * api for get articles by domain
     *
     * @param context the specified context
     */
    public void getDomainArticles(final RequestContext context) {
        context.setRenderer(buildJsonRenderer());
        final String domainURI = context.pathVar("domainURI");
        final Request request = context.getRequest();

        final Map<String, Object> dataModel = new HashMap<>();
        final int pageNum = Paginator.getPage(request);
        final String size = context.param("size");
        int pageSize = StringUtils.isBlank(size) ? 0 : Integer.parseInt(size);
        pageSize = pageSize <= 0 ? Symphonys.ARTICLE_LIST_CNT : pageSize;

        final JSONObject domain = domainQueryService.getByURI(domainURI);
        if (null == domain) {
            context.renderCodeMsg(404, String.format("domain: %s 不存在!", domainURI));
            return;
        }

        final List<JSONObject> tags = domainQueryService.getTags(domain.optString(Keys.OBJECT_ID));
        domain.put(Domain.DOMAIN_T_TAGS, (Object) tags);
        dataModel.put(Domain.DOMAIN, domain);
        final String domainId = domain.optString(Keys.OBJECT_ID);

        final JSONObject result = articleQueryService.getDomainArticles(domainId,null, pageNum, pageSize);
        final List<JSONObject> allArticles = (List<JSONObject>) result.get(Article.ARTICLES);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        dataModel.put("pagination", pagination);
        dataModel.put(Article.ARTICLES, DesensitizeUtil.articlesDesensitize(allArticles));

        context.renderJSON(new JSONObject().put("data", dataModel)).renderCode(StatusCodes.SUCC).renderMsg("");
    }

    /**
     * api for get articles by tag
     *
     * @param context the specified context
     */
    public void getTagArticles(final RequestContext context) {
        context.setRenderer(buildJsonRenderer());
        final String tagURI = context.pathVar("tagURI");
        final Request request = context.getRequest();

        final Map<String, Object> dataModel = new HashMap<>();
        final int pageNum = Paginator.getPage(request);
        final String size = context.param("size");
        int pageSize = StringUtils.isBlank(size) ? 0 : Integer.parseInt(size);
        pageSize = pageSize <= 0 ? Symphonys.ARTICLE_LIST_CNT : pageSize;


        final JSONObject tag = tagQueryService.getTagByURI(tagURI);
        if (null == tag) {
            context.renderCodeMsg(404, String.format("tag: %s 不存在!", tagURI));
            return;
        }

        tag.put(Common.IS_RESERVED, tagQueryService.isReservedTag(tag.optString(Tag.TAG_TITLE)));
        dataModel.put(Tag.TAG, tag);
        final String tagId = tag.optString(Keys.OBJECT_ID);
        final List<JSONObject> relatedTags = tagQueryService.getRelatedTags(tagId, Symphonys.TAG_RELATED_TAGS_CNT);
        tag.put(Tag.TAG_T_RELATED_TAGS, (Object) relatedTags);

        String sortModeStr = StringUtils.substringAfter(context.requestURI(), "/tag/" + tagURI);
        int sortMode;
        switch (sortModeStr) {
            case "":
                sortMode = 0;
                break;
            case "/hot":
                sortMode = 1;
                break;
            case "/good":
                sortMode = 2;
                break;
            case "/reply":
                sortMode = 3;
                break;
            case "/perfect":
                sortMode = 4;
                break;
            default:
                sortMode = 0;
        }

        final List<JSONObject> articles = articleQueryService.getArticlesByTag(sortMode, tag, pageNum, pageSize);
        dataModel.put(Article.ARTICLES, DesensitizeUtil.articlesDesensitize(articles));

        final int tagRefCnt = tag.getInt(Tag.TAG_REFERENCE_CNT);
        final int pageCount = (int) Math.ceil(tagRefCnt / (double) pageSize);
        final int windowSize = Symphonys.ARTICLE_LIST_WIN_SIZE;
        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        JSONObject paginationRet = new JSONObject();
        paginationRet.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        paginationRet.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModel.put("pagination", paginationRet);

        context.renderJSON(new JSONObject().put("data", dataModel)).renderCode(StatusCodes.SUCC).renderMsg("");
    }


    /**
     * api for get articles
     *
     * @param context the specified context
     */
    public void getArticles(final RequestContext context) {
        final Request request = context.getRequest();
        final Map<String, Object> dataModel = new HashMap<>();
        final int pageNum = Paginator.getPage(request);
        final String size = context.param("size");
        int pageSize = StringUtils.isBlank(size) ? 0 : Integer.parseInt(size);
        pageSize = pageSize <= 0 ? Symphonys.ARTICLE_LIST_CNT : pageSize;

        String sortModeStr = StringUtils.substringAfter(context.requestURI(), "/recent");
        int sortMode;
        switch (sortModeStr) {
            case "":
                sortMode = 0;
                break;
            case "/hot":
                sortMode = 1;
                break;
            case "/good":
                sortMode = 2;
                break;
            case "/reply":
                sortMode = 3;
                break;
            default:
                sortMode = 0;
        }
        final JSONObject result = articleQueryService.getRecentArticles(sortMode, pageNum, pageSize);
        final List<JSONObject> allArticles = (List<JSONObject>) result.get(Article.ARTICLES);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        dataModel.put("pagination", pagination);
        dataModel.put(Article.ARTICLES, DesensitizeUtil.articlesDesensitize(allArticles));

        context.renderJSON(new JSONObject().put("data", dataModel)).renderCode(StatusCodes.SUCC).renderMsg("");
    }




    /**
     * Removes an article.
     *
     * @param context the specified context
     */
    public void removeArticle(final RequestContext context) {
        final String id = context.pathVar("id");
        if (StringUtils.isBlank(id)) {
            context.sendError(404);
            return;
        }

        final JSONObject currentUser = Sessions.getUser();
        final String currentUserId = currentUser.optString(Keys.OBJECT_ID);
        final JSONObject article = articleQueryService.getArticle(id);
        if (null == article) {
            context.sendError(404);
            return;
        }

        final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);
        if (!authorId.equals(currentUserId)) {
            context.sendError(403);
            return;
        }

        context.renderJSON(StatusCodes.ERR);

        if (article.optString(Article.ARTICLE_TITLE).startsWith("摸鱼周报 ")) {
            context.renderMsg("摸鱼周报不能被删除，请认真维护！");
            return;
        }

        final long dayAgo = DateUtils.addDays(new Date(), -1).getTime();
        if (article.optLong(Article.ARTICLE_CREATE_TIME)  > dayAgo) {
            context.renderMsg("文章发布 24 小时后才能删除！");
            return;
        }

        try {
            articleMgmtService.removeArticle(id);

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
            context.renderJSONValue(Article.ARTICLE_T_ID, id);
        } catch (final ServiceException e) {
            final String msg = e.getMessage();
            context.renderMsg(msg);
        }
    }

    /**
     * Checks article title.
     *
     * @param context the specified context
     */
    public void checkArticleTitle(final RequestContext context) {
        final JSONObject currentUser = Sessions.getUser();
        final String currentUserId = currentUser.optString(Keys.OBJECT_ID);
        final JSONObject requestJSONObject = context.requestJSON();
        String title = requestJSONObject.optString(Article.ARTICLE_TITLE);
        title = StringUtils.trim(title);
        String id = requestJSONObject.optString(Article.ARTICLE_T_ID);

        final JSONObject article = articleQueryService.getArticleByTitle(title);
        if (null == article) {
            context.renderJSON(StatusCodes.SUCC);
            return;
        }

        if (StringUtils.isBlank(id)) { // Add
            final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);

            String msg;
            if (authorId.equals(currentUserId)) {
                msg = langPropsService.get("duplicatedArticleTitleSelfLabel");
                msg = msg.replace("{article}", "<a target='_blank' href='/article/" + article.optString(Keys.OBJECT_ID)
                        + "'>" + title + "</a>");
            } else {
                final JSONObject author = userQueryService.getUser(authorId);
                final String userName = author.optString(User.USER_NAME);

                msg = langPropsService.get("duplicatedArticleTitleLabel");
                msg = msg.replace("{user}", "<a target='_blank' href='/member/" + userName + "'>" + userName + "</a>");
                msg = msg.replace("{article}", "<a target='_blank' href='/article/" + article.optString(Keys.OBJECT_ID)
                        + "'>" + title + "</a>");
            }

            final JSONObject ret = new JSONObject();
            ret.put(Keys.CODE, StatusCodes.ERR);
            ret.put(Keys.MSG, msg);

            context.renderJSON(ret);
        } else { // Update
            final JSONObject oldArticle = articleQueryService.getArticle(id);
            if (oldArticle.optString(Article.ARTICLE_TITLE).equals(title)) {
                context.renderJSON(StatusCodes.SUCC);
                return;
            }

            final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);

            String msg;
            if (authorId.equals(currentUserId)) {
                msg = langPropsService.get("duplicatedArticleTitleSelfLabel");
                msg = msg.replace("{article}", "<a target='_blank' href='/article/" + article.optString(Keys.OBJECT_ID)
                        + "'>" + title + "</a>");
            } else {
                final JSONObject author = userQueryService.getUser(authorId);
                final String userName = author.optString(User.USER_NAME);

                msg = langPropsService.get("duplicatedArticleTitleLabel");
                msg = msg.replace("{user}", "<a target='_blank' href='/member/" + userName + "'>" + userName + "</a>");
                msg = msg.replace("{article}", "<a target='_blank' href='/article/" + article.optString(Keys.OBJECT_ID)
                        + "'>" + title + "</a>");
            }

            final JSONObject ret = new JSONObject();
            ret.put(Keys.CODE, StatusCodes.ERR);
            ret.put(Keys.MSG, msg);
            context.renderJSON(ret);
        }
    }

    /**
     * Gets article image.
     *
     * @param context the specified context
     */
    public void getArticleImage(final RequestContext context) {
        final String articleId = context.pathVar("articleId");
        final JSONObject article = articleQueryService.getArticle(articleId);

        final Set<JSONObject> characters = characterQueryService.getWrittenCharacters();
        final String articleContent = article.optString(Article.ARTICLE_CONTENT);

        final List<BufferedImage> images = new ArrayList<>();
        for (int i = 0; i < articleContent.length(); i++) {
            final String ch = articleContent.substring(i, i + 1);
            final JSONObject chRecord = org.b3log.symphony.model.Character.getCharacter(ch, characters);
            if (null == chRecord) {
                images.add(org.b3log.symphony.model.Character.createImage(ch));
                continue;
            }

            final String imgData = chRecord.optString(org.b3log.symphony.model.Character.CHARACTER_IMG);
            final byte[] data = Base64.decode(imgData.getBytes());
            BufferedImage img;
            try {
                img = ImageIO.read(new ByteArrayInputStream(data));
                final BufferedImage newImage = new BufferedImage(50, 50, img.getType());
                final Graphics g = newImage.getGraphics();
                g.setClip(0, 0, 50, 50);
                g.fillRect(0, 0, 50, 50);
                g.drawImage(img, 0, 0, 50, 50, null);
                g.dispose();

                images.add(newImage);
            } catch (final Exception e) {
                // ignored
            }
        }

        final int rowCharacterCount = 30;
        final int rows = (int) Math.ceil((double) images.size() / (double) rowCharacterCount);

        final BufferedImage combined = new BufferedImage(30 * 50, rows * 50, Transparency.TRANSLUCENT);
        int row = 0;
        for (int i = 0; i < images.size(); i++) {
            final BufferedImage image = images.get(i);

            final Graphics g = combined.getGraphics();
            g.drawImage(image, (i % rowCharacterCount) * 50, row * 50, null);

            if (0 == (i + 1) % rowCharacterCount) {
                row++;
            }
        }

        try {
            ImageIO.write(combined, "PNG", new File("./hp.png"));
        } catch (final Exception e) {
            // ignored
        }

        String url = "";
        final JSONObject ret = new JSONObject();
        ret.put(Keys.CODE, StatusCodes.SUCC);
        ret.put(Common.URL, url);
        context.renderJSON(ret);
    }

    /**
     * Gets an article's revisions.
     *
     * @param context the specified context
     */
    public void getArticleRevisions(final RequestContext context) {
        final String id = context.pathVar("id");
        final List<JSONObject> revisions = revisionQueryService.getArticleRevisions(id);
        final JSONObject ret = new JSONObject();
        ret.put(Keys.CODE, StatusCodes.SUCC);
        ret.put(Revision.REVISIONS, (Object) revisions);
        context.renderJSON(ret);
    }

    /**
     * Shows pre-add article.
     *
     * @param context the specified context
     */
    public void showPreAddArticle(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/pre-post.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.BROADCAST_POINT, Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_BROADCAST);
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * Fills the domains with tags.
     *
     * @param dataModel the specified data model
     */
    private void fillDomainsWithTags(final Map<String, Object> dataModel) {
        final List<JSONObject> domains = domainQueryService.getAllDomains();
        dataModel.put(Common.ADD_ARTICLE_DOMAINS, domains);
        for (final JSONObject domain : domains) {
            final List<JSONObject> tags = domainQueryService.getTags(domain.optString(Keys.OBJECT_ID));

            domain.put(Domain.DOMAIN_T_TAGS, (Object) tags);
        }

        final JSONObject user = Sessions.getUser();
        if (null == user) {
            return;
        }

        try {
            final JSONObject followingTagsResult = followQueryService.getFollowingTags(
                    user.optString(Keys.OBJECT_ID), 1, 28);
            final List<JSONObject> followingTags = (List<JSONObject>) followingTagsResult.opt(Keys.RESULTS);
            if (!followingTags.isEmpty()) {
                final JSONObject userWatched = new JSONObject();
                userWatched.put(Keys.OBJECT_ID, String.valueOf(System.currentTimeMillis()));
                userWatched.put(Domain.DOMAIN_TITLE, langPropsService.get("notificationFollowingLabel"));
                userWatched.put(Domain.DOMAIN_T_TAGS, (Object) followingTags);

                domains.add(0, userWatched);
            }
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Get user [name=" + user.optString(User.USER_NAME) + "] following tags failed", e);
        }
    }

    /**
     * Shows add article.
     *
     * @param context the specified context
     */
    public void showAddArticle(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/post.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        String tags = context.param(Tag.TAGS);
        final JSONObject currentUser = Sessions.getUser();

        if (StringUtils.isBlank(tags)) {
            tags = "";

            dataModel.put(Tag.TAGS, tags);
        } else {
            tags = Tag.formatTags(tags);
            final String[] tagTitles = tags.split(",");

            final StringBuilder tagBuilder = new StringBuilder();
            for (final String title : tagTitles) {
                final String tagTitle = title.trim();

                if (StringUtils.isBlank(tagTitle)) {
                    continue;
                }

                if (Tag.containsWhiteListTags(tagTitle)) {
                    tagBuilder.append(tagTitle).append(",");
                    continue;
                }

                if (!Tag.TAG_TITLE_PATTERN.matcher(tagTitle).matches()) {
                    continue;
                }

                if (tagTitle.length() > Tag.MAX_TAG_TITLE_LENGTH) {
                    continue;
                }

                if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))
                        && ArrayUtils.contains(Symphonys.RESERVED_TAGS, tagTitle)) {
                    continue;
                }

                tagBuilder.append(tagTitle).append(",");
            }
            if (tagBuilder.length() > 0) {
                tagBuilder.deleteCharAt(tagBuilder.length() - 1);
            }

            dataModel.put(Tag.TAGS, tagBuilder.toString());
        }

        final String type = context.param(Common.TYPE);
        if (StringUtils.isBlank(type)) {
            dataModel.put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
        } else {
            int articleType = Article.ARTICLE_TYPE_C_NORMAL;

            try {
                articleType = Integer.valueOf(type);
            } catch (final Exception e) {
                LOGGER.log(Level.WARN, "Gets article type error [" + type + "]", e);
            }

            if (Article.isInvalidArticleType(articleType)) {
                articleType = Article.ARTICLE_TYPE_C_NORMAL;
            }

            dataModel.put(Article.ARTICLE_TYPE, articleType);
        }

        String at = context.param(Common.AT);
        at = StringUtils.trim(at);
        if (StringUtils.isNotBlank(at)) {
            if (!UserRegisterValidationMidware.invalidUserName(at)) {
                dataModel.put(Common.AT, at + " ");
            }
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);

        String rewardEditorPlaceholderLabel = langPropsService.get("rewardEditorPlaceholderLabel");
        rewardEditorPlaceholderLabel = rewardEditorPlaceholderLabel.replace("{point}",
                String.valueOf(Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_REWARD));
        dataModel.put("rewardEditorPlaceholderLabel", rewardEditorPlaceholderLabel);
        dataModel.put(Common.BROADCAST_POINT, Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_BROADCAST);

        String articleContentErrorLabel = langPropsService.get("articleContentErrorLabel");
        articleContentErrorLabel = articleContentErrorLabel.replace("{maxArticleContentLength}",
                String.valueOf(ArticlePostValidationMidware.MAX_ARTICLE_CONTENT_LENGTH));
        dataModel.put("articleContentErrorLabel", articleContentErrorLabel);

        fillPostArticleRequisite(dataModel, currentUser);
        fillDomainsWithTags(dataModel);
    }

    private void fillPostArticleRequisite(final Map<String, Object> dataModel, final JSONObject currentUser) {
        boolean requisite = false;
        String requisiteMsg = "";

        dataModel.put(Common.REQUISITE, requisite);
        dataModel.put(Common.REQUISITE_MSG, requisiteMsg);
    }

    /**
     * Shows article with the specified article id.
     *
     * @param context the specified context
     */
    public void showArticle(final RequestContext context) {
        final String articleId = context.pathVar("articleId");
        final Request request = context.getRequest();

        if (hitArticleRateLimit(request)) {
            context.sendError(503);
            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "article.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final JSONObject article = articleQueryService.getArticleById(articleId);
        if (null == article) {
            context.sendError(404);
            return;
        }

        if (article.optInt(Article.ARTICLE_STATUS)==Article.ARTICLE_STATUS_C_INVALID){
            context.sendError(404);
            return;
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);

        final String articleAuthorId = article.optString(Article.ARTICLE_AUTHOR_ID);
        JSONObject author;
        if (Article.ARTICLE_ANONYMOUS_C_PUBLIC == article.optInt(Article.ARTICLE_ANONYMOUS)) {
            author = userQueryService.getUser(articleAuthorId);
        } else {
            author = userQueryService.getAnonymousUser();
        }
        Escapes.escapeHTML(author);
        article.put(Article.ARTICLE_T_AUTHOR_NAME, author.optString(User.USER_NAME));
        article.put(Article.ARTICLE_T_AUTHOR_URL, author.optString(User.USER_URL));
        article.put(Article.ARTICLE_T_AUTHOR_INTRO, author.optString(UserExt.USER_INTRO));
        article.put("articleAuthorNickName", author.optString(UserExt.USER_NICKNAME));

        String metal = cloudService.getEnabledMedal(articleAuthorId);
        if (!metal.equals("{}")&&Article.ARTICLE_ANONYMOUS_C_ANONYMOUS!=article.optInt(Article.ARTICLE_ANONYMOUS)) {
            List<Object> list = new JSONObject(metal).optJSONArray("list").toList();
            article.put("sysMetal", list);
        } else {
            article.put("sysMetal", new ArrayList<>());
        }

        dataModel.put(Article.ARTICLE, article);

        article.put(Common.IS_MY_ARTICLE, false);
        article.put(Article.ARTICLE_T_AUTHOR, author);
        article.put(Common.REWARDED, false);
        article.put(Common.REWARED_COUNT, rewardQueryService.rewardedCount(articleId, Reward.TYPE_C_ARTICLE));
        article.put(Article.ARTICLE_REVISION_COUNT, revisionQueryService.count(articleId, Revision.DATA_TYPE_C_ARTICLE));

        articleQueryService.processArticleContent(article);

        String cmtViewModeStr = context.param("m");
        JSONObject currentUser;
        String currentUserId = null;
        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            currentUser = Sessions.getUser();
            currentUserId = currentUser.optString(Keys.OBJECT_ID);
            final boolean isMyArticle = currentUserId.equals(articleAuthorId);
            article.put(Common.IS_MY_ARTICLE, isMyArticle);

            final boolean isFollowing = followQueryService.isFollowing(currentUserId, articleId, Follow.FOLLOWING_TYPE_C_ARTICLE);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);

            final boolean isWatching = followQueryService.isFollowing(currentUserId, articleId, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH);
            dataModel.put(Common.IS_WATCHING, isWatching);

            final int articleVote = voteQueryService.isVoted(currentUserId, articleId);
            article.put(Article.ARTICLE_T_VOTE, articleVote);

            if (isMyArticle) {
                article.put(Common.REWARDED, true);
            } else {
                article.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, articleId, Reward.TYPE_C_ARTICLE));
            }

            if (StringUtils.isBlank(cmtViewModeStr) || !Strings.isNumeric(cmtViewModeStr)) {
                cmtViewModeStr = currentUser.optString(UserExt.USER_COMMENT_VIEW_MODE);
            }
        } else if (StringUtils.isBlank(cmtViewModeStr) || !Strings.isNumeric(cmtViewModeStr)) {
            cmtViewModeStr = "0";
        }

        final int cmtViewMode = Integer.valueOf(cmtViewModeStr);
        dataModel.put(UserExt.USER_COMMENT_VIEW_MODE, cmtViewMode);

        final JSONObject viewer = Sessions.getUser();
        if (null != viewer) {
            livenessMgmtService.incLiveness(viewer.optString(Keys.OBJECT_ID), Liveness.LIVENESS_PV);
        }

        if (!Sessions.isBot()) {
            final long created = System.currentTimeMillis();
            final long expired = DateUtils.addMonths(new Date(created), 1).getTime();
            final String ip = Requests.getRemoteAddr(request);
            final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
            final String referer = Headers.getHeader(request, "Referer", "");
            final JSONObject visit = new JSONObject();
            visit.put(Visit.VISIT_IP, ip);
            visit.put(Visit.VISIT_CITY, "");
            visit.put(Visit.VISIT_CREATED, created);
            visit.put(Visit.VISIT_DEVICE_ID, "");
            visit.put(Visit.VISIT_EXPIRED, expired);
            visit.put(Visit.VISIT_REFERER_URL, referer);
            visit.put(Visit.VISIT_UA, ua);
            visit.put(Visit.VISIT_URL, "/article/" + articleId);
            visit.put(Visit.VISIT_USER_ID, "");
            if (null != viewer) {
                visit.put(Visit.VISIT_USER_ID, viewer.optString(Keys.OBJECT_ID));
            }

            articleMgmtService.incArticleViewCount(visit);
        }

        dataModelService.fillRelevantArticles(dataModel, article);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);

        // Fill article thank
        Stopwatchs.start("Fills article thank");
        try {
            article.put(Common.THANKED, rewardQueryService.isRewarded(currentUserId, articleId, Reward.TYPE_C_THANK_ARTICLE));
            article.put(Common.THANKED_COUNT, article.optInt(Article.ARTICLE_THANK_CNT));
            if (Article.ARTICLE_TYPE_C_QNA == article.optInt(Article.ARTICLE_TYPE)) {
                article.put(Common.OFFERED, rewardQueryService.isRewarded(articleAuthorId, articleId, Reward.TYPE_C_ACCEPT_COMMENT));
                final JSONObject offeredComment = commentQueryService.getOfferedComment(cmtViewMode, articleId);
                article.put(Article.ARTICLE_T_OFFERED_COMMENT, offeredComment);
                if (null != offeredComment) {
                    if (Comment.COMMENT_VISIBLE_C_AUTHOR == offeredComment.optInt(Comment.COMMENT_VISIBLE)) {
                        final String commentAuthorId = offeredComment.optString(Comment.COMMENT_AUTHOR_ID);
                        if (!isLoggedIn || (!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                            offeredComment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
                        }
                    }
                    final String offeredCmtId = offeredComment.optString(Keys.OBJECT_ID);
                    final int rewardCount = offeredComment.optInt(Comment.COMMENT_THANK_CNT);
                    offeredComment.put(Common.REWARED_COUNT, rewardCount);
                    offeredComment.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, offeredCmtId, Reward.TYPE_C_COMMENT));
                }
            }
        } finally {
            Stopwatchs.end();
        }

        // Fill previous/next article
        final JSONObject previous = articleQueryService.getPreviousPermalink(articleId);
        final JSONObject next = articleQueryService.getNextPermalink(articleId);
        dataModel.put(Article.ARTICLE_T_PREVIOUS, previous);
        dataModel.put(Article.ARTICLE_T_NEXT, next);

        String stickConfirmLabel = langPropsService.get("stickConfirmLabel");
        stickConfirmLabel = stickConfirmLabel.replace("{point}", Symphonys.POINT_STICK_ARTICLE + "");
        stickConfirmLabel = stickConfirmLabel.replace("{time}", (Symphonys.STICK_ARTICLE_TIME / (60 * 1000)) + "");
        dataModel.put("stickConfirmLabel", stickConfirmLabel);
        dataModel.put("pointThankArticle", Symphonys.POINT_THANK_ARTICLE);

        int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.ARTICLE_COMMENTS_CNT;
        final int windowSize = Symphonys.ARTICLE_COMMENTS_WIN_SIZE;
        final int commentCnt = article.getInt(Article.ARTICLE_COMMENT_CNT);
        final int pageCount = (int) Math.ceil((double) commentCnt / (double) pageSize);
        // 回帖分页 SEO https://github.com/b3log/symphony/issues/813
        if (UserExt.USER_COMMENT_VIEW_MODE_C_TRADITIONAL == cmtViewMode) {
            if (0 < pageCount && pageNum > pageCount) {
                pageNum = pageCount;
            }
        } else {
            if (pageNum > pageCount) {
                pageNum = 1;
            }
        }
        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Common.ARTICLE_COMMENTS_PAGE_SIZE, pageSize);

        dataModel.put(Common.DISCUSSION_VIEWABLE, article.optBoolean(Common.DISCUSSION_VIEWABLE));
        if (!article.optBoolean(Common.DISCUSSION_VIEWABLE)) {
            article.put(Article.ARTICLE_T_COMMENTS, (Object) Collections.emptyList());
            article.put(Article.ARTICLE_T_NICE_COMMENTS, (Object) Collections.emptyList());
            return;
        }

        final List<JSONObject> niceComments = commentQueryService.getNiceComments(cmtViewMode, articleId, 3);
        article.put(Article.ARTICLE_T_NICE_COMMENTS, (Object) niceComments);

        double niceCmtScore = Double.MAX_VALUE;
        if (!niceComments.isEmpty()) {
            niceCmtScore = niceComments.get(niceComments.size() - 1).optDouble(Comment.COMMENT_SCORE, 0D);

            for (final JSONObject comment : niceComments) {
                String thankTemplate = langPropsService.get("thankConfirmLabel");
                thankTemplate = thankTemplate.replace("{point}", String.valueOf(Symphonys.POINT_THANK_COMMENT))
                        .replace("{user}", comment.optJSONObject(Comment.COMMENT_T_COMMENTER).optString(User.USER_NAME));
                comment.put(Comment.COMMENT_T_THANK_LABEL, thankTemplate);

                final String commentId = comment.optString(Keys.OBJECT_ID);
                if (isLoggedIn) {
                    comment.put(Common.REWARDED, rewardQueryService.isRewarded(currentUserId, commentId, Reward.TYPE_C_COMMENT));
                    final int commentVote = voteQueryService.isVoted(currentUserId, commentId);
                    comment.put(Comment.COMMENT_T_VOTE, commentVote);
                }

                comment.put(Common.REWARED_COUNT, comment.optInt(Comment.COMMENT_THANK_CNT));

                // https://github.com/b3log/symphony/issues/682
                if (Comment.COMMENT_VISIBLE_C_AUTHOR == comment.optInt(Comment.COMMENT_VISIBLE)) {
                    final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
                    if (!isLoggedIn || (!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                        comment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));
                    }
                }
            }
        }

        // Load comments
        final List<JSONObject> articleComments = commentQueryService.getArticleComments(articleId, pageNum, pageSize, cmtViewMode);
        article.put(Article.ARTICLE_T_COMMENTS, (Object) articleComments);
        article.put("commentors", (Object) commentQueryService.getArticleCommentors(articleId));

        // Fill comment thank
        Stopwatchs.start("Fills comment thank");
        try {
            final String thankTemplate = langPropsService.get("thankConfirmLabel");
            for (final JSONObject comment : articleComments) {
                comment.put(Comment.COMMENT_T_NICE, comment.optDouble(Comment.COMMENT_SCORE, 0D) >= niceCmtScore);

                final String thankStr = thankTemplate.replace("{point}", String.valueOf(Symphonys.POINT_THANK_COMMENT))
                        .replace("{user}", comment.optJSONObject(Comment.COMMENT_T_COMMENTER).optString(User.USER_NAME));
                comment.put(Comment.COMMENT_T_THANK_LABEL, thankStr);

                final String commentId = comment.optString(Keys.OBJECT_ID);
                if (isLoggedIn) {
                    comment.put(Common.REWARDED,
                            rewardQueryService.isRewarded(currentUserId, commentId, Reward.TYPE_C_COMMENT));
                    final int commentVote = voteQueryService.isVoted(currentUserId, commentId);
                    comment.put(Comment.COMMENT_T_VOTE, commentVote);
                }

                comment.put(Common.REWARED_COUNT, comment.optInt(Comment.COMMENT_THANK_CNT));

                // https://github.com/b3log/symphony/issues/682
                if (Comment.COMMENT_VISIBLE_C_AUTHOR == comment.optInt(Comment.COMMENT_VISIBLE)) {
                    final String commentAuthorId = comment.optString(Comment.COMMENT_AUTHOR_ID);
                    if (!isLoggedIn || (!StringUtils.equals(currentUserId, commentAuthorId) && !StringUtils.equals(currentUserId, articleAuthorId))) {
                        comment.put(Comment.COMMENT_CONTENT, langPropsService.get("onlySelfAndArticleAuthorVisibleLabel"));

                    }
                }
            }
        } finally {
            Stopwatchs.end();
        }

        // Referral statistic
        final String referralUserName = context.param("r");
        if (!UserRegisterValidationMidware.invalidUserName(referralUserName)) {
            final JSONObject referralUser = userQueryService.getUserByName(referralUserName);
            if (null == referralUser) {
                return;
            }

            final String viewerIP = Requests.getRemoteAddr(request);

            final JSONObject referral = new JSONObject();
            referral.put(Referral.REFERRAL_CLICK, 1);
            referral.put(Referral.REFERRAL_DATA_ID, articleId);
            referral.put(Referral.REFERRAL_IP, viewerIP);
            referral.put(Referral.REFERRAL_TYPE, Referral.REFERRAL_TYPE_C_ARTICLE);
            referral.put(Referral.REFERRAL_USER, referralUserName);

            referralMgmtService.updateReferral(referral);
        }

        if (StringUtils.isBlank(article.optString(Article.ARTICLE_AUDIO_URL))) {
            articleMgmtService.genArticleAudio(article);
        }
    }

    /**
     * Adds an article locally.
     * <p>
     * The request json object (an article):
     * <pre>
     * {
     *   "articleTitle": "",
     *   "articleTags": "", // Tags spliting by ','
     *   "articleContent": "",
     *   "articleCommentable": boolean,
     *   "articleType": int,
     *   "articleRewardContent": "",
     *   "articleRewardPoint": int,
     *   "articleQnAOfferPoint": int,
     *   "articleAnonymous": boolean,
     *   "articleNotifyFollowers": boolean
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void addArticle(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        final JSONObject requestJSONObject = context.requestJSON();
        final String articleTitle = requestJSONObject.optString(Article.ARTICLE_TITLE);
        String articleTags = requestJSONObject.optString(Article.ARTICLE_TAGS);
        final String articleContent = requestJSONObject.optString(Article.ARTICLE_CONTENT);
        final boolean articleCommentable = requestJSONObject.optBoolean(Article.ARTICLE_COMMENTABLE, true);
        final int articleStatement = requestJSONObject.optInt(Article.ARTICLE_STATEMENT, 0);
        final int articleType = requestJSONObject.optInt(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
        final String articleRewardContent = requestJSONObject.optString(Article.ARTICLE_REWARD_CONTENT);
        final int articleRewardPoint = requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT);
        final int articleQnAOfferPoint = requestJSONObject.optInt(Article.ARTICLE_QNA_OFFER_POINT);
        final String ip = Requests.getRemoteAddr(request);
        final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
        final boolean isAnonymous = requestJSONObject.optBoolean(Article.ARTICLE_ANONYMOUS, false);
        final int articleAnonymous = isAnonymous ? Article.ARTICLE_ANONYMOUS_C_ANONYMOUS : Article.ARTICLE_ANONYMOUS_C_PUBLIC;
        final boolean articleNotifyFollowers = requestJSONObject.optBoolean(Article.ARTICLE_T_NOTIFY_FOLLOWERS);
        final Integer articleShowInList = requestJSONObject.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES);
        final String isGoodArticle = requestJSONObject.optString("isGoodArticle");

        final JSONObject article = new JSONObject();
        article.put(Article.ARTICLE_TITLE, articleTitle);
        article.put(Article.ARTICLE_CONTENT, articleContent);
        article.put(Article.ARTICLE_EDITOR_TYPE, 0);
        article.put(Article.ARTICLE_COMMENTABLE, articleCommentable);
        article.put(Article.ARTICLE_STATEMENT, articleStatement);
        article.put(Article.ARTICLE_TYPE, articleType);
        article.put(Article.ARTICLE_REWARD_CONTENT, articleRewardContent);
        article.put(Article.ARTICLE_REWARD_POINT, articleRewardPoint);
        article.put(Article.ARTICLE_QNA_OFFER_POINT, articleQnAOfferPoint);
        article.put(Article.ARTICLE_IP, "");
        if (StringUtils.isNotBlank(ip)) {
            article.put(Article.ARTICLE_IP, ip);
        }
        article.put(Article.ARTICLE_UA, ua);
        article.put(Article.ARTICLE_ANONYMOUS, articleAnonymous);
        article.put(Article.ARTICLE_T_NOTIFY_FOLLOWERS, articleNotifyFollowers);
        article.put(Article.ARTICLE_SHOW_IN_LIST, articleShowInList);
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userPhone = currentUser.optString("userPhone");
            if (userPhone.isEmpty()) {
                context.renderJSON(StatusCodes.ERR).renderMsg("未绑定手机号码，无法使用此功能。请至设置-账户中绑定手机号码。");
                return;
            }

            article.put(Article.ARTICLE_AUTHOR_ID, currentUser.optString(Keys.OBJECT_ID));

            if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))) {
                articleTags = articleMgmtService.filterReservedTags(articleTags);
            }

            if (Article.ARTICLE_TYPE_C_DISCUSSION == articleType && StringUtils.isBlank(articleTags)) {
                articleTags = "机要";
            }

            if (Article.ARTICLE_TYPE_C_THOUGHT == articleType && StringUtils.isBlank(articleTags)) {
                articleTags = "思绪";
            }

            article.put(Article.ARTICLE_TAGS, articleTags);
            // 用户帖子列表
            final List<JSONObject> userArticles = articleQueryService.getUserArticles(currentUser.optString(Keys.OBJECT_ID),Article.ARTICLE_ANONYMOUS_C_PUBLIC, 1, 1);
            // 没发过帖子
            if (userArticles.isEmpty() && !articleTags.contains("新人报道") && !articleTags.contains("新人报到")) {
                context.renderMsg("迈入社区第一步, 介绍下自己~ 请先发送一个新人报道(标签需要包含'新人报道')帖吧!");
                return;
            }
            // 宵禁状态  TODO 多个地方用了, 阿达你要处理么?
            int start = 1930;
            int end = 800;
            int now = Integer.parseInt(new SimpleDateFormat("HHmm").format(new Date()));
            //是宵禁状态, 设置发帖间隔 一小时一贴?
            if (now < end || now > start) {
                // 帖子列表 不为空
                if (!userArticles.isEmpty()){
                    // 当前时间
                    long nowTime = System.currentTimeMillis();
                    // 最后一篇文章
                    JSONObject lastArticle = userArticles.get(0);
                    // 更新时间 毫秒
                    long articleCreateTime = lastArticle.getLong(Keys.OBJECT_ID);
                    // 小于一小时间隔
                    if (nowTime - articleCreateTime < 60 * 60 * 1000){
                        context.renderMsg("摸鱼派已进入宵禁模式, 充足的睡眠是摸鱼的关键, 良性的思考一定不会促使你高频的发帖, 为了你的身心健康，我们将调整发帖间隔为 1h (一小时)...感谢你的陪伴，我们明天再见，早点休息，(¦3[▓▓] 晚安");
                        return;
                    }
                }
            }
            // TGIF  判断开头和长度
            if(articleTitle.startsWith("摸鱼周报")&&articleTitle.length()==13){
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(new Date());
                String date = DateFormatUtils.format(new Date(), "yyyyMMdd");
                String articleTitleShouldBe = "摸鱼周报 " + date;
                if(!articleTitle.equals(articleTitleShouldBe)){
                    context.renderMsg("您的摸鱼周报日期不是今天哟~检查一下吧");
                    return;
                }
                if (calendar.get(Calendar.DAY_OF_WEEK) == 6 && calendar.get(Calendar.HOUR_OF_DAY) > 8) {
                    JSONObject checkArticle = articleQueryService.getArticleByTitle(articleTitleShouldBe);
                    if (checkArticle == null) {
                        // 检查 TGIF 帖子质量
                        if (articleContent.length() < 128) {
                            context.renderMsg("您的摸鱼周报字数不合格，请认真对待！<br>请注意：根据摸鱼守则，摸鱼周报应该是抒发一周所想的灵感篇章，而不是为了水积分而随意撰写的一两句话，请保证摸鱼周报有个人情感、话题性，发送摸鱼周报带有明显水帖行为的，将取消摸鱼周报的帖子标识（恢复本周摸鱼周报的开放权限），并扣除奖励的积分，并处罚金 1000 积分。");
                            return;
                        }
                        // 发放奖励
                        pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, currentUser.optString(Keys.OBJECT_ID),
                                Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SEND_TGIF,
                                1000, "", System.currentTimeMillis(), "");
                    }
                }else {
                    context.renderMsg("还没到摸鱼周报的时间哟~周报时间是每周五9点以后哟~");
                    return;
                }
            }

            article.put("isGoodArticle", isGoodArticle);
            final String articleId = articleMgmtService.addArticle(article);

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
            context.renderJSONValue(Article.ARTICLE_T_ID, articleId);
        } catch (final ServiceException e) {
            final String msg = e.getMessage();
            LOGGER.log(Level.ERROR, "Adds article [title=" + articleTitle + "] failed: {}", e.getMessage());
            context.renderMsg(msg);
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
        }
    }

    /**
     * Shows update article.
     *
     * @param context the specified context
     */
    public void showUpdateArticle(final RequestContext context) {
        final String articleId = context.param("id");
        if (StringUtils.isBlank(articleId)) {
            context.sendError(404);
            return;
        }

        final JSONObject article = articleQueryService.getArticle(articleId);
        if (null == article) {
            context.sendError(404);
            return;
        }

        final JSONObject currentUser = Sessions.getUser();
        if (null == currentUser
                || !currentUser.optString(Keys.OBJECT_ID).equals(article.optString(Article.ARTICLE_AUTHOR_ID))) {
            context.sendError(403);
            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/post.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        String title = article.optString(Article.ARTICLE_TITLE);
        title = Escapes.escapeHTML(title);
        article.put(Article.ARTICLE_TITLE, title);
        dataModel.put(Article.ARTICLE, article);
        dataModel.put(Article.ARTICLE_TYPE, article.optInt(Article.ARTICLE_TYPE));

        dataModelService.fillHeaderAndFooter(context, dataModel);

        fillDomainsWithTags(dataModel);

        String rewardEditorPlaceholderLabel = langPropsService.get("rewardEditorPlaceholderLabel");
        rewardEditorPlaceholderLabel = rewardEditorPlaceholderLabel.replace("{point}",
                String.valueOf(Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_REWARD));
        dataModel.put("rewardEditorPlaceholderLabel", rewardEditorPlaceholderLabel);
        dataModel.put(Common.BROADCAST_POINT, Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_BROADCAST);

        fillPostArticleRequisite(dataModel, currentUser);
    }

    /**
     * Updates an article locally.
     * <p>
     * The request json object (an article):
     * <pre>
     * {
     *   "articleTitle": "",
     *   "articleTags": "", // Tags spliting by ','
     *   "articleContent": "",
     *   "articleCommentable": boolean,
     *   "articleType": int,
     *   "articleRewardContent": "",
     *   "articleRewardPoint": int,
     *   "articleQnAOfferPoint": int,
     *   "articleNotifyFollowers": boolean
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void updateArticle(final RequestContext context) {
        final String id = context.pathVar("id");
        final Request request = context.getRequest();
        if (StringUtils.isBlank(id)) {
            context.sendError(404);
            return;
        }

        final JSONObject oldArticle = articleQueryService.getArticleById(id);
        if (null == oldArticle) {
            context.sendError(404);
            return;
        }

        context.renderJSON(StatusCodes.ERR);

        if (Article.ARTICLE_STATUS_C_VALID != oldArticle.optInt(Article.ARTICLE_STATUS)) {
            context.renderMsg(langPropsService.get("articleLockedLabel"));
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
            return;
        }

        final JSONObject requestJSONObject = context.requestJSON();
        final String articleTitle = requestJSONObject.optString(Article.ARTICLE_TITLE);
        if (oldArticle.optString(Article.ARTICLE_TITLE).startsWith("摸鱼周报 ")) {
            if (!oldArticle.optString(Article.ARTICLE_TITLE).equals(articleTitle)) {
                context.renderMsg("无法修改摸鱼周报的标题，请认真维护！");
                return;
            }
        }
        String articleTags = requestJSONObject.optString(Article.ARTICLE_TAGS);
        final String articleContent = requestJSONObject.optString(Article.ARTICLE_CONTENT);
        final boolean articleCommentable = requestJSONObject.optBoolean(Article.ARTICLE_COMMENTABLE, true);
        final int articleStatement = requestJSONObject.optInt(Article.ARTICLE_STATEMENT, 0);
        final int articleType = requestJSONObject.optInt(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
        final String articleRewardContent = requestJSONObject.optString(Article.ARTICLE_REWARD_CONTENT);
        final int articleRewardPoint = requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT);
        final int articleQnAOfferPoint = requestJSONObject.optInt(Article.ARTICLE_QNA_OFFER_POINT);
        final String ip = Requests.getRemoteAddr(request);
        final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
        final boolean articleNotifyFollowers = requestJSONObject.optBoolean(Article.ARTICLE_T_NOTIFY_FOLLOWERS);
        final Integer articleShowInList = requestJSONObject.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES);
        final JSONObject article = new JSONObject();
        article.put(Keys.OBJECT_ID, id);
        article.put(Article.ARTICLE_TITLE, articleTitle);
        article.put(Article.ARTICLE_CONTENT, articleContent);
        article.put(Article.ARTICLE_EDITOR_TYPE, 0);
        article.put(Article.ARTICLE_COMMENTABLE, articleCommentable);
        article.put(Article.ARTICLE_STATEMENT, articleStatement);
        article.put(Article.ARTICLE_TYPE, articleType);
        article.put(Article.ARTICLE_REWARD_CONTENT, articleRewardContent);
        article.put(Article.ARTICLE_REWARD_POINT, articleRewardPoint);
        article.put(Article.ARTICLE_QNA_OFFER_POINT, articleQnAOfferPoint);
        article.put(Article.ARTICLE_IP, "");
        if (StringUtils.isNotBlank(ip)) {
            article.put(Article.ARTICLE_IP, ip);
        }
        article.put(Article.ARTICLE_UA, ua);
        article.put(Article.ARTICLE_T_NOTIFY_FOLLOWERS, articleNotifyFollowers);
        article.put(Article.ARTICLE_SHOW_IN_LIST, articleShowInList);
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == currentUser
                || !currentUser.optString(Keys.OBJECT_ID).equals(oldArticle.optString(Article.ARTICLE_AUTHOR_ID))) {
            context.sendError(403);
            return;
        }

        article.put(Article.ARTICLE_AUTHOR_ID, currentUser.optString(Keys.OBJECT_ID));

        if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))) {
            articleTags = articleMgmtService.filterReservedTags(articleTags);
        }

        if (Article.ARTICLE_TYPE_C_DISCUSSION == articleType && StringUtils.isBlank(articleTags)) {
            articleTags = "机要";
        }

        if (Article.ARTICLE_TYPE_C_THOUGHT == articleType && StringUtils.isBlank(articleTags)) {
            articleTags = "思绪";
        }

        article.put(Article.ARTICLE_TAGS, articleTags);

        try {
            articleMgmtService.updateArticle(article);

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
            context.renderJSONValue(Article.ARTICLE_T_ID, id);
        } catch (final ServiceException e) {
            final String msg = e.getMessage();
            LOGGER.log(Level.ERROR, "Adds article [title=" + articleTitle + "] failed: {}", e.getMessage());
            context.renderMsg(msg);
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
        }
    }

    /**
     * Markdowns.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "html": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    public void markdown2HTML(final RequestContext context) {
        final JSONObject result = Results.newSucc();
        context.renderJSON(result);

        final JSONObject requestJSON = context.requestJSON();
        final String markdownText = requestJSON.optString("markdownText");
        if (StringUtils.isBlank(markdownText)) {
            context.renderJSONValue("html", "");
            return;
        }

        String html = shortLinkQueryService.linkArticle(markdownText);
        html = Emotions.toAliases(html);
        html = Emotions.convert(html);
        html = Markdowns.toHTML(html);
        html = Markdowns.clean(html, "");
        html = MediaPlayers.renderAudio(html);
        html = MediaPlayers.renderVideo(html);

        result.put(Common.DATA, html);
    }

    /**
     * Gets article preview content.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "html": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    public void getArticlePreviewContent(final RequestContext context) {
        final String articleId = context.pathVar("articleId");
        final String content = articleQueryService.getArticlePreviewContent(articleId, context);
        if (StringUtils.isBlank(content)) {
            context.renderJSON(StatusCodes.ERR);
            return;
        }

        context.renderJSON(StatusCodes.SUCC).renderJSONValue("html", content);
    }

    /**
     * Article rewards.
     *
     * @param context the specified http request context
     */
    public void rewardArticle(final RequestContext context) {
        final JSONObject currentUser = Sessions.getUser();
        if (null == currentUser) {
            context.sendError(403);
            return;
        }

        final String articleId = context.param(Article.ARTICLE_T_ID);
        if (StringUtils.isBlank(articleId)) {
            context.sendError(400);
            return;
        }

        context.renderJSON(StatusCodes.ERR);

        try {
            articleMgmtService.reward(articleId, currentUser.optString(Keys.OBJECT_ID));
        } catch (final ServiceException e) {
            context.renderMsg(langPropsService.get("transferFailLabel"));
            return;
        }

        final JSONObject article = articleQueryService.getArticle(articleId);
        articleQueryService.processArticleContent(article);

        final String rewardContent = article.optString(Article.ARTICLE_REWARD_CONTENT);
        context.renderJSON(StatusCodes.SUCC).renderJSONValue(Article.ARTICLE_REWARD_CONTENT, rewardContent);
    }

    /**
     * Article thanks.
     *
     * @param context the specified http request context
     */
    public void thankArticle(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }

        if (null == currentUser) {
            context.sendError(403);
            return;
        }

        final String articleId = context.param(Article.ARTICLE_T_ID);
        if (StringUtils.isBlank(articleId)) {
            context.sendError(400);
            return;
        }

        context.renderJSON(StatusCodes.ERR);

        try {
            articleMgmtService.thank(articleId, currentUser.optString(Keys.OBJECT_ID));
        } catch (final ServiceException e) {
            context.renderMsg(langPropsService.get("transferFailLabel"));
            return;
        }

        context.renderJSON(StatusCodes.SUCC);
    }

    /**
     * Sticks an article.
     *
     * @param context the specified HTTP request context
     */
    public void stickArticle(final RequestContext context) {
        final JSONObject currentUser = Sessions.getUser();
        if (null == currentUser) {
            context.sendError(403);
            return;
        }

        final String articleId = context.param(Article.ARTICLE_T_ID);
        if (StringUtils.isBlank(articleId)) {
            context.sendError(400);
            return;
        }

        final JSONObject article = articleQueryService.getArticle(articleId);
        if (null == article) {
            context.sendError(404);
            return;
        }

        if (!currentUser.optString(Keys.OBJECT_ID).equals(article.optString(Article.ARTICLE_AUTHOR_ID))) {
            context.sendError(403);
            return;
        }

        context.renderJSON(StatusCodes.ERR);

        try {
            articleMgmtService.stick(articleId);
        } catch (final ServiceException e) {
            context.renderMsg(e.getMessage());
            return;
        }

        context.renderJSON(StatusCodes.SUCC).renderMsg(langPropsService.get("stickSuccLabel"));
    }

    /**
     * Generate random articles.
     *
     * @param context the specified HTTP request context
     */
    public void randomArticles(final RequestContext context) {
        final int size = Integer.parseInt(context.pathVar("size"));
        if (size > 20) {
            return;
        }
        context.renderJSON(new JSONObject().put("articles", getRandomArticles(size)));
    }

    public static List<JSONObject> getRandomArticles(int size) {
        final BeanManager beanManager = BeanManager.getInstance();
        final ArticleRepository articleRepository = beanManager.getReference(ArticleRepository.class);
        final ArticleQueryService articleQueryService = beanManager.getReference(ArticleQueryService.class);

        Stopwatchs.start("Load random articles");
        try {
            List<JSONObject> articles = articleRepository.getRandomly(size);
            articleQueryService.organizeArticles(articles);
            Collections.shuffle(articles);
            if (articles.size() <= size) {
                return articles;
            }
            return articles.subList(0, size);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Loads random articles failed", e);
        } finally {
            Stopwatchs.end();
        }
        return null;
    }
}
