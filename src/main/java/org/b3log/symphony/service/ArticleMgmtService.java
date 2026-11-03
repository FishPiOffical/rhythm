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

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.event.Event;
import org.b3log.latke.event.EventManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.latke.util.URLs;
import org.b3log.symphony.event.EventTypes;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

/**
 * Article management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @author <a href="https://qiankunpingtai.cn">qiankunpingtai</a>
 * @version 2.18.5.11, Jun 6, 2019
 * @since 0.2.0
 */
@Service
public class ArticleMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ArticleMgmtService.class);

    /**
     * Tag max count.
     */
    private static final int TAG_MAX_CNT = 4;

    /**
     * Comment repository.
     */
    @Inject
    private CommentRepository commentRepository;

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    /**
     * Tag repository.
     */
    @Inject
    private TagRepository tagRepository;

    /**
     * Tag-Article repository.
     */
    @Inject
    private TagArticleRepository tagArticleRepository;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * User-Tag repository.
     */
    @Inject
    private UserTagRepository userTagRepository;

    /**
     * Option repository.
     */
    @Inject
    private OptionRepository optionRepository;

    /**
     * Notification repository.
     */
    @Inject
    private NotificationRepository notificationRepository;

    /**
     * Revision repository.
     */
    @Inject
    private RevisionRepository revisionRepository;

    /**
     * Reward repository.
     */
    @Inject
    private RewardRepository rewardRepository;

    /**
     * Vote repository.
     */
    @Inject
    private VoteRepository voteRepository;

    @Inject
    private ReactionRepository reactionRepository;

    /**
     * Tag management service.
     */
    @Inject
    private TagMgmtService tagMgmtService;

    /**
     * Event manager.
     */
    @Inject
    private EventManager eventManager;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    @Inject
    private ArticleRewardMgmtService articleRewards;

    /**
     * Long article read service.
     */
    @Inject
    private LongArticleReadService longArticleReadService;

    @Inject
    private ArticleSearchVisitStatMgmtService articleSearchVisitStatMgmtService;

    /**
     * Long article column management service.
     */
    @Inject
    private LongArticleColumnMgmtService longArticleColumnMgmtService;

    @Inject
    private LongArticleParagraphService longArticleParagraphService;

    @Inject
    private ApiV3ContentQueryService paragraphContentQueryService;

    @Inject
    private ColumnCoverMgmtService columnCoverMgmtService;

    /**
     * Follow query service.
     */
    @Inject
    private FollowQueryService followQueryService;

    /**
     * Tag query service.
     */
    @Inject
    private TagQueryService tagQueryService;
    /**
     * Notification management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

    /**
     * Liveness management service.
     */
    @Inject
    private LivenessMgmtService livenessMgmtService;

    /**
     * Search management service.
     */
    @Inject
    private SearchMgmtService searchMgmtService;

    /**
     * Audio management service.
     */
    @Inject
    private AudioMgmtService audioMgmtService;

    /**
     * Visit management service.
     */
    @Inject
    private VisitMgmtService visitMgmtService;

    @Inject
    private ProfessionSourceEventCaptureService professionSourceEventCaptureService;

    /**
     * Determines whether the specified tag title exists in the specified tags.
     *
     * @param tagTitle the specified tag title
     * @param tags     the specified tags
     * @return {@code true} if it exists, {@code false} otherwise
     * @throws JSONException json exception
     */
    private static boolean tagExists(final String tagTitle, final List<JSONObject> tags) throws JSONException {
        for (final JSONObject tag : tags) {
            if (tag.getString(Tag.TAG_TITLE).equals(tagTitle)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes an article specified with the given article id. An article is removable if:
     * <ul>
     * <li>No comments</li>
     * <li>No watches, collects, ups, downs</li>
     * <li>No rewards</li>
     * <li>No thanks</li>
     * <li>In valid status</li>
     * </ul>
     * Sees https://github.com/b3log/symphony/issues/450 for more details.
     *
     * @param articleId the given article id
     * @throws ServiceException service exception
     */
    public void removeArticle(final String articleId) throws ServiceException {
        try { removeWithTransaction(articleId, false); }
        catch (final ServiceException e) { throw e; }
        catch (final Exception e) { throw new ServiceException(e); }
    }

    private void removeWithTransaction(final String articleId, final boolean force) throws Exception {
        final var snapshot = articleRepository.get(articleId);
        if (null == snapshot) return;
        withPublicationLock(snapshot.optString(Article.ARTICLE_AUTHOR_ID), () -> {
            final Transaction tx = articleRepository.beginTransaction();
            try {
                final var removal = removeArticleInCurrentTransaction(articleId, force);
                tx.commit();
                return removal;
            } finally { if (tx.isActive()) tx.rollback(); }
        }, this::afterArticleRemoved);
    }

    /** Private persistence snapshot; never serialize this result as an API response. */
    public record Removal(JSONObject article) { }

    /** Database-only removal, shared by legacy wrappers and a future v3 idempotent boundary. */
    public Removal removeArticleInCurrentTransaction(final String articleId, final boolean force) throws Exception {
        if (!articleRepository.hasTransactionBegun() || !ArticlePublicationCaches.active()) {
            throw new IllegalStateException("article removal transaction and cache boundary required");
        }
        if (null == optionRepository.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) {
            throw new IllegalStateException("article statistic option missing");
        }
        // Match creation/edit order: global statistics, author, article.
        final var candidate = articleRepository.select("SELECT articleAuthorId FROM `" + articleRepository.getName() + "` WHERE oId=?", articleId);
        if (candidate.isEmpty()) return null;
        final String authorId = candidate.getFirst().optString(Article.ARTICLE_AUTHOR_ID);
        if (null == userRepository.getForUpdate(authorId)) throw new ServiceException("用户不存在");
        final var article = articleRepository.getForUpdate(articleId);
        if (null == article) return null;
        if (!force) {
            try {
                ArticleRemovalPolicy.checkTiming(article, System.currentTimeMillis());
                ArticleRemovalPolicy.check(article, type -> rewardRepository.existsForUpdate(articleId, type) ? 1L : 0L);
            } catch (final ArticleRemovalPolicy.Rejected e) {
                throw new ServiceException(switch (e.label) {
                    case "weekly_report" -> "摸鱼周报不能被删除，请认真维护！";
                    case "too_young" -> "文章发布 24 小时后才能删除！";
                    default -> langPropsService.get(e.label);
                });
            }
        }
        removeArticleDataInCurrentTransaction(article);
        return new Removal(article);
    }

    /**
     * Generates article's audio.
     *
     * @param article the specified article
     */
    public void genArticleAudio(final JSONObject article) {
        if (Article.ARTICLE_TYPE_C_THOUGHT == article.optInt(Article.ARTICLE_TYPE)
                || Article.ARTICLE_TYPE_C_DISCUSSION == article.optInt(Article.ARTICLE_TYPE)) {
            return;
        }

        final String tags = article.optString(Article.ARTICLE_TAGS);
        if (StringUtils.containsIgnoreCase(tags, Tag.TAG_TITLE_C_SANDBOX)) {
            return;
        }

        final String articleId = article.optString(Keys.OBJECT_ID);
        String previewContent = article.optString(Article.ARTICLE_CONTENT);
        previewContent = Markdowns.toHTML(previewContent);
        final Document doc = Jsoup.parse(previewContent);
        final Elements elements = doc.select("a, img, iframe, object, video");
        for (final Element element : elements) {
            element.remove();
        }
        previewContent = Emotions.clear(doc.text());
        previewContent = StringUtils.substring(previewContent, 0, 512);
        final String contentToTTS = previewContent;
        final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);

        Symphonys.EXECUTOR_SERVICE.submit(() -> {
            try {
                String audioURL = "";
                if (StringUtils.length(contentToTTS) < 128 || Runes.getChinesePercent(contentToTTS) < 40) {
                    LOGGER.trace("Content is too short to TTS [contentToTTS=" + contentToTTS + "]");
                } else {
                    audioURL = audioMgmtService.tts(contentToTTS, Article.ARTICLE, articleId, authorId);
                }
                if (StringUtils.isBlank(audioURL)) {
                    return;
                }

                article.put(Article.ARTICLE_AUDIO_URL, audioURL);

                final JSONObject toUpdate = articleRepository.get(articleId);
                toUpdate.put(Article.ARTICLE_AUDIO_URL, audioURL);
                final Transaction transaction = articleRepository.beginTransaction();
                articleRepository.update(articleId, toUpdate, Article.ARTICLE_AUDIO_URL);
                transaction.commit();
                LOGGER.debug("Generated article [id=" + articleId + "] audio");
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Updates article's audio URL failed", e);
            }
        });
    }

    /**
     * Removes an article specified with the given article id. Calls this method will remove all existed data related
     * with the specified article forcibly.
     *
     * @param articleId the given article id
     */
    public void removeArticleByAdmin(final String articleId) {
        try { removeWithTransaction(articleId, true); }
        catch (final Exception e) {
            LOGGER.error("Removes an article failed [id={}]", articleId, e);
            throw new IllegalStateException("删除文章失败", e);
        }
    }

    private void removeArticleDataInCurrentTransaction(final JSONObject article) throws Exception {
        final String articleId = article.optString(Keys.OBJECT_ID);
        final List<JSONObject> comments = commentRepository.select("SELECT * FROM `" + commentRepository.getName()
                + "` WHERE commentOnArticleId=? ORDER BY oId FOR UPDATE", articleId);
        for (final JSONObject comment : comments) {
            final String commentId = comment.optString(Keys.OBJECT_ID);
            professionSourceEventCaptureService.targetRemoved("comment", commentId);
            commentRepository.removeComment(commentId);
        }

        final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);
        final JSONObject author = userRepository.get(authorId);
        author.put(UserExt.USER_ARTICLE_COUNT, Math.max(0, author.optInt(UserExt.USER_ARTICLE_COUNT) - 1));
        userRepository.update(authorId, author, UserExt.USER_ARTICLE_COUNT);

        final String cityStatId = article.optString(Article.ARTICLE_CITY) + "-ArticleCount";
        final JSONObject cityCount = optionRepository.get(cityStatId);
        if (null != cityCount) {
            cityCount.put(Option.OPTION_VALUE, cityCount.optInt(Option.OPTION_VALUE) - 1);
            optionRepository.update(cityStatId, cityCount);
        }
        final JSONObject total = optionRepository.get(Option.ID_C_STATISTIC_ARTICLE_COUNT);
        total.put(Option.OPTION_VALUE, total.optInt(Option.OPTION_VALUE) - 1);
        optionRepository.update(Option.ID_C_STATISTIC_ARTICLE_COUNT, total);

        longArticleColumnMgmtService.removeChapterInCurrentTransaction(articleId);
        professionSourceEventCaptureService.targetRemoved("article", articleId);
        articleRepository.remove(articleId);

        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(Revision.REVISION_DATA_ID, FilterOperator.EQUAL, articleId),
                new PropertyFilter(Revision.REVISION_DATA_TYPE, FilterOperator.EQUAL, Revision.DATA_TYPE_C_ARTICLE)));
        for (final JSONObject revision : revisionRepository.getList(query)) {
            revisionRepository.remove(revision.optString(Keys.OBJECT_ID));
        }
        for (final JSONObject relation : tagArticleRepository.getByArticleId(articleId)) {
            final String tagId = relation.optString(Tag.TAG + "_" + Keys.OBJECT_ID);
            final JSONObject tag = tagRepository.get(tagId);
            tag.put(Tag.TAG_REFERENCE_CNT, Math.max(0, tag.optInt(Tag.TAG_REFERENCE_CNT) - 1));
            tag.put(Tag.TAG_RANDOM_DOUBLE, Math.random());
            tagRepository.update(tagId, tag);
        }
        tagArticleRepository.removeByArticleId(articleId);
        notificationRepository.removeContentReferences(articleId, true, commentRepository.getName());
        for (final int type : List.of(Reward.TYPE_C_ARTICLE, Reward.TYPE_C_THANK_ARTICLE, Reward.TYPE_C_ACCEPT_COMMENT)) {
            for (final String rewardId : rewardRepository.removeByDataId(articleId, type)) {
                notificationRepository.removeRewardReferences(rewardId, type);
            }
        }
        voteRepository.removeByDataId(articleId, Vote.DATA_TYPE_C_ARTICLE);
        reactionRepository.removeTargetInCurrentTransaction(Reaction.TARGET_TYPE_ARTICLE, articleId);
    }

    /** Best effort external cleanup, only after confirmed commit and cache scope closure. */
    public void afterArticleRemoved(final Removal removal) {
        if (articleRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) {
            throw new IllegalStateException("removal effects before transaction end");
        }
        if (null == removal) return;
        final var article = removal.article();
        if (Symphonys.ALGOLIA_ENABLED) {
            try { searchMgmtService.removeAlgoliaDocument(article); }
            catch (final Exception e) { LOGGER.error("Remove committed article from Algolia failed", e); }
        }
        if (Symphonys.ES_ENABLED) {
            try { searchMgmtService.removeESDocument(article, Article.ARTICLE); }
            catch (final Exception e) { LOGGER.error("Remove committed article from Elasticsearch failed", e); }
        }
        final String audioURL = article.optString(Article.ARTICLE_AUDIO_URL);
        if (StringUtils.isNotBlank(audioURL)) {
            try { audioMgmtService.removeAudioFile(audioURL); }
            catch (final Exception e) { LOGGER.error("Remove committed article audio failed", e); }
        }
    }

    /**
     * Increments the view count of the specified article by the given visit.
     *
     * @param visit the given visit
     */
    public void incArticleViewCount(final JSONObject visit) {
        Symphonys.EXECUTOR_SERVICE.submit(() -> {
            final String visitURL = visit.optString(Visit.VISIT_URL);
            final String articleId = StringUtils.substringAfter(visitURL, "/article/");
            boolean visitedB4 = false;
            try {
                if ("1".equals(optionRepository.get(Option.ID_C_MISC_ARTICLE_VISIT_COUNT_MODE).optString(Option.OPTION_VALUE))) {
                    visitedB4 = visitMgmtService.add(visit);
                }
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Gets visit count mode failed", e);
            }

            if (visitedB4) {
                return;
            }

            final Transaction transaction = ApiV3Transactions.strict(articleRepository.beginTransaction());
            try {
                final JSONObject article = articleRepository.get(articleId);
                if (null == article) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                    return;
                }

                longArticleReadService.record(new LongArticleReadCaptureRequest(articleId,
                        visit.optString(Visit.VISIT_USER_ID), visit.optString(Visit.VISIT_IP),
                        visit.optString(Visit.VISIT_UA)));

                final int viewCnt = article.optInt(Article.ARTICLE_VIEW_CNT);
                article.put(Article.ARTICLE_VIEW_CNT, viewCnt + 1);
                article.put(Article.ARTICLE_RANDOM_DOUBLE, Math.random());

                articleRepository.update(articleId, article, Article.ARTICLE_VIEW_CNT, Article.ARTICLE_RANDOM_DOUBLE);

                transaction.commit();
                articleSearchVisitStatMgmtService.recordReferer(articleId, visit.optString(Visit.VISIT_REFERER_URL));
                articleSearchVisitStatMgmtService.recordClient(articleId, visit.optString(Visit.VISIT_UA));
            } catch (final Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }

                LOGGER.log(Level.ERROR, "Incs an article view count failed", e);
            }
        });
    }

    /**
     * Adds an article with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "articleTitle": "",
     *                          "articleTags": "",
     *                          "articleContent": "",
     *                          "articleEditorType": int, // optional, default to 0: markdown
     *                          "articleAuthorId": "",
     *                          "articleCommentable": boolean, // optional, default to true
     *                          "articleType": int, // optional, default to 0
     *                          "articleRewardContent": "", // optional, default to ""
     *                          "articleRewardPoint": int, // optional, default to 0
     *                          "articleQnAOfferPoint": int, // optional, default to 0
     *                          "articleIP": "", // optional, default to ""
     *                          "articleUA": "", // optional, default to ""
     *                          "articleAnonymous": int, // optional, default to 0 (public)
     *                          "articleAnonymousView": int, // optional, default to 0 (use global)
     *                          "articleNotifyFollowers": boolean
     * @return generated article id
     * @throws ServiceException service exception
     */
    public String addArticle(final JSONObject requestJSONObject) throws ServiceException {
        final Publication publication;
        try {
            publication = withPublicationLock(requestJSONObject.optString(Article.ARTICLE_AUTHOR_ID), () -> {
                final Transaction transaction = articleRepository.beginTransaction();
                try {
                    final Publication result = addArticleInCurrentTransaction(requestJSONObject);
                    transaction.commit();
                    return result;
                } finally {
                    if (transaction.isActive()) transaction.rollback();
                }
            }, this::afterArticlePublished);
        } catch (final ServiceException e) { throw e; }
        catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Adds an article failed", e);
            throw new ServiceException(e);
        }
        return publication.article().optString(Keys.OBJECT_ID);
    }

    /** Lock ordering is service monitor, account pair, then the database transaction. */
    public synchronized <T> T withPublicationLock(final String authorId, final java.util.concurrent.Callable<T> operation) throws Exception {
        return withPublicationLock(authorId, operation, ignored -> { });
    }

    /** Keep publication/edit ordering while running confirmed post-commit effects outside the cache scope. */
    public synchronized <T> T withPublicationLock(final String authorId, final java.util.concurrent.Callable<T> operation,
                                                 final java.util.function.Consumer<T> afterCommit) throws Exception {
        return withPublicationLocks(authorId, List.of(Pointtransfer.ID_C_SYS), operation, afterCommit);
    }

    /** Comments can additionally pay their article author; acquire all destinations before any database lock. */
    public synchronized <T> T withPublicationLocks(final String actor, final List<String> destinations,
                                                  final java.util.concurrent.Callable<T> operation,
                                                  final java.util.function.Consumer<T> afterCommit) throws Exception {
        if (articleRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("publication lock before transaction required");
        return pointtransferMgmtService.executeWithTransferLocks(actor, destinations, () -> {
            final T result;
            try (final ArticlePublicationCaches ignored = ArticlePublicationCaches.open(articleRepository::hasTransactionBegun)) {
                result = operation.call();
            }
            afterCommit.accept(result);
            return result;
        });
    }

    /** Internal result only; contains private persistence fields and must never be serialized to clients. */
    public record Publication(JSONObject article, JSONObject author, JSONObject request, List<String> transferIds) {
        public Publication { transferIds = List.copyOf(transferIds); }
    }

    private static ArticlePublishPoints.Rates publicationPointRates() {
        return new ArticlePublishPoints.Rates(Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE,
                Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_REWARD, Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_BROADCAST,
                Symphonys.ANONYMOUS_POST_POINT, Pointtransfer.TRANSFER_TYPE_C_ADD_ARTICLE,
                Pointtransfer.TRANSFER_TYPE_C_ADD_ARTICLE_REWARD, Pointtransfer.TRANSFER_TYPE_C_ADD_ARTICLE_BROADCAST);
    }

    /** Join the caller's transaction; validation at the HTTP boundary remains the caller's responsibility. */
    public Publication addArticleInCurrentTransaction(final JSONObject requestJSONObject) throws Exception {
        if (!articleRepository.hasTransactionBegun() || !ArticlePublicationCaches.active()) {
            throw new IllegalStateException("publication transaction and cache boundary required");
        }
        // Serialize publication statistics and duplicate-title checks across application instances.
        if (null == optionRepository.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) {
            throw new IllegalStateException("article statistic option missing");
        }
        final long currentTimeMillis = System.currentTimeMillis();
        final String authorId = requestJSONObject.optString(Article.ARTICLE_AUTHOR_ID);
        JSONObject author;
        final ArticlePublishPoints.Plan pointPlan;

        final int rewardPoint = requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT, 0);
        if (rewardPoint < 0) {
            throw new ServiceException(langPropsService.get("invalidRewardPointLabel"));
        }

        final int articleAnonymous = requestJSONObject.optInt(Article.ARTICLE_ANONYMOUS);
        if (Article.ARTICLE_ANONYMOUS_C_PUBLIC != articleAnonymous
                && Article.ARTICLE_ANONYMOUS_C_ANONYMOUS != articleAnonymous) {
            throw new ServiceException("匿名设置不合法");
        }

        String articleTitle = requestJSONObject.optString(Article.ARTICLE_TITLE);
        articleTitle = Emotions.toAliases(articleTitle);
        articleTitle = Pangu.spacingText(articleTitle);
        articleTitle = StringUtils.trim(articleTitle);

        final int qnaOfferPoint = requestJSONObject.optInt(Article.ARTICLE_QNA_OFFER_POINT, 0);

        final int articleType = requestJSONObject.optInt(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
        if (qnaOfferPoint < 0 || (Article.ARTICLE_TYPE_C_QNA == articleType && 20 > qnaOfferPoint)) { // https://github.com/b3log/symphony/issues/672
            throw new ServiceException(langPropsService.get("invalidQnAOfferPointLabel"));
        }

        try {
            // check if admin allow to add article
            final JSONObject option = optionRepository.get(Option.ID_C_MISC_ALLOW_ADD_ARTICLE);

            if (!"0".equals(option.optString(Option.OPTION_VALUE))) {
                throw new ServiceException(langPropsService.get("notAllowAddArticleLabel"));
            }

            author = userRepository.getForUpdate(authorId);
            if (null == author || UserExt.USER_STATUS_C_VALID != author.optInt(UserExt.USER_STATUS)) {
                throw new ServiceException(langPropsService.get("userStatusInvalidLabel"));
            }

            if (currentTimeMillis - author.optLong(Keys.OBJECT_ID) < Symphonys.NEWBIE_FIRST_ARTICLE) {
                String tip = langPropsService.get("newbieFirstArticleLabel");
                final long time = author.optLong(Keys.OBJECT_ID) + Symphonys.NEWBIE_FIRST_ARTICLE;
                final String timeStr = DateFormatUtils.format(time, "yyyy-MM-dd HH:mm:ss");
                tip = tip.replace("${time}", timeStr);

                throw new ServiceException(tip);
            }

            if (currentTimeMillis - author.optLong(UserExt.USER_LATEST_ARTICLE_TIME) < Symphonys.MIN_STEP_ARTICLE_TIME
                    && !Role.ROLE_ID_C_ADMIN.equals(author.optString(User.USER_ROLE))) {
                LOGGER.log(Level.WARN, "Adds article too frequent [userName={}]", author.optString(User.USER_NAME));
                throw new ServiceException(langPropsService.get("tooFrequentArticleLabel"));
            }

            final int balance = author.optInt(UserExt.USER_POINT);
            if (Article.ARTICLE_ANONYMOUS_C_ANONYMOUS == articleAnonymous) {
                final int anonymousPoint = Symphonys.ANONYMOUS_POST_POINT;
                if (balance < anonymousPoint) {
                    String anonymousEnabelPointLabel = langPropsService.get("anonymousEnabelPointLabel");
                    anonymousEnabelPointLabel
                            = anonymousEnabelPointLabel.replace("${point}", String.valueOf(anonymousPoint));
                    throw new ServiceException(anonymousEnabelPointLabel);
                }
            }

            pointPlan = ArticlePublishPoints.plan(publicationPointRates(),
                    Article.ARTICLE_ANONYMOUS_C_ANONYMOUS == articleAnonymous,
                    Article.ARTICLE_TYPE_C_CITY_BROADCAST == articleType,
                    Article.ARTICLE_ANONYMOUS_C_PUBLIC == articleAnonymous
                            ? followQueryService.getFollowerCount(authorId, Follow.FOLLOWING_TYPE_C_USER) : 0,
                    rewardPoint, qnaOfferPoint);
            if (balance < pointPlan.minimumBalance()) {
                throw new ServiceException(langPropsService.get("insufficientBalanceLabel"));
            }

            if (Article.ARTICLE_TYPE_C_DISCUSSION != articleType) {
                final JSONObject maybeExist = articleRepository.getByTitleForUpdate(articleTitle);
                if (null != maybeExist) {
                    final String existArticleAuthorId = maybeExist.optString(Article.ARTICLE_AUTHOR_ID);
                    String msg;
                    if (existArticleAuthorId.equals(authorId)) {
                        msg = langPropsService.get("duplicatedArticleTitleSelfLabel");
                        msg = msg.replace("{article}", "<a target='_blank' href='/article/" + maybeExist.optString(Keys.OBJECT_ID)
                                + "'>" + articleTitle + "</a>");
                    } else {
                        final JSONObject existArticleAuthor = userRepository.get(existArticleAuthorId);
                        final String userName = existArticleAuthor.optString(User.USER_NAME);
                        msg = langPropsService.get("duplicatedArticleTitleLabel");
                        msg = msg.replace("{user}", "<a target='_blank' href='/member/" + userName + "'>" + userName + "</a>");
                        msg = msg.replace("{article}", "<a target='_blank' href='/article/" + maybeExist.optString(Keys.OBJECT_ID)
                                + "'>" + articleTitle + "</a>");
                    }

                    throw new ServiceException(msg);
                }
            }
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }

        final String ret = Ids.genTimeMillisId();
        final JSONObject article = new JSONObject();
        article.put(Keys.OBJECT_ID, ret);

        article.put(Article.ARTICLE_TITLE, articleTitle);
        article.put(Article.ARTICLE_TAGS, requestJSONObject.optString(Article.ARTICLE_TAGS));

        String articleContent = requestJSONObject.optString(Article.ARTICLE_CONTENT);
        articleContent = Emotions.toAliases(articleContent);
        //articleContent = StringUtils.trim(articleContent) + " "; https://github.com/b3log/symphony/issues/389
        articleContent = StringUtils.replace(articleContent, langPropsService.get("uploadingLabel", Locale.SIMPLIFIED_CHINESE), "");
        articleContent = StringUtils.replace(articleContent, langPropsService.get("uploadingLabel", Locale.US), "");
        if (Article.ARTICLE_TYPE_C_THOUGHT != articleType) {
            articleContent = Runes.removeControlChars(articleContent);
        }
        if (HandouPostDetector.isHandouPost(articleContent)) {
            article.put(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_NOT);
        } else {
            article.put(Article.ARTICLE_SHOW_IN_LIST, requestJSONObject.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES));
        }
        article.put(Article.ARTICLE_CONTENT, articleContent);

        String rewardContent = requestJSONObject.optString(Article.ARTICLE_REWARD_CONTENT);
        rewardContent = Emotions.toAliases(rewardContent);
        rewardContent = Runes.removeControlChars(rewardContent);
        article.put(Article.ARTICLE_REWARD_CONTENT, rewardContent);

        article.put(Article.ARTICLE_EDITOR_TYPE, requestJSONObject.optInt(Article.ARTICLE_EDITOR_TYPE));
        article.put(Article.ARTICLE_AUTHOR_ID, authorId);
        article.put(Article.ARTICLE_COMMENT_CNT, 0);
        article.put(Article.ARTICLE_VIEW_CNT, 0);
        article.put(Article.ARTICLE_THANK_CNT, 0);
        article.put(Article.ARTICLE_GOOD_CNT, 0);
        article.put(Article.ARTICLE_BAD_CNT, 0);
        article.put(Article.ARTICLE_COLLECT_CNT, 0);
        article.put(Article.ARTICLE_WATCH_CNT, 0);
        article.put(Article.ARTICLE_COMMENTABLE, requestJSONObject.optBoolean(Article.ARTICLE_COMMENTABLE, true));
        article.put(Article.ARTICLE_STATEMENT, requestJSONObject.optInt(Article.ARTICLE_STATEMENT, 0));
        article.put(Article.ARTICLE_CREATE_TIME, currentTimeMillis);
        article.put(Article.ARTICLE_UPDATE_TIME, currentTimeMillis);
        article.put(Article.ARTICLE_LATEST_CMT_TIME, ret);
        article.put(Article.ARTICLE_LATEST_CMTER_NAME, "");
        article.put(Article.ARTICLE_PERMALINK, "/article/" + ret);
        article.put(Article.ARTICLE_RANDOM_DOUBLE, Math.random());
        article.put(Article.REDDIT_SCORE, 0);
        article.put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_VALID);
        article.put(Article.ARTICLE_TYPE, articleType);
        article.put(Article.ARTICLE_REWARD_POINT, rewardPoint);
        article.put(Article.ARTICLE_QNA_OFFER_POINT, qnaOfferPoint);
        article.put(Article.ARTICLE_PUSH_ORDER, 0);
        article.put(Article.ARTICLE_IMG1_URL, "");
        String city = "";
        if (UserExt.USER_GEO_STATUS_C_PUBLIC == author.optInt(UserExt.USER_GEO_STATUS)) {
            city = author.optString(UserExt.USER_CITY);
        }
        article.put(Article.ARTICLE_CITY, city);
        article.put(Article.ARTICLE_ANONYMOUS, articleAnonymous);
        article.put(Article.ARTICLE_PERFECT, Article.ARTICLE_PERFECT_C_NOT_PERFECT);
        article.put(Article.ARTICLE_GOOD_POST, requestJSONObject.optString(Article.ARTICLE_GOOD_POST));
        article.put(Article.ARTICLE_ANONYMOUS_VIEW,
                requestJSONObject.optInt(Article.ARTICLE_ANONYMOUS_VIEW, Article.ARTICLE_ANONYMOUS_VIEW_C_USE_GLOBAL));
        article.put(Article.ARTICLE_AUDIO_URL, "");

        String articleTags = article.optString(Article.ARTICLE_TAGS);
        articleTags = Tag.formatTags(articleTags);
        if (StringUtils.containsIgnoreCase(articleTags, Tag.TAG_TITLE_C_SANDBOX)) {
            articleTags = Tag.TAG_TITLE_C_SANDBOX;
        }
        if (StringUtils.isBlank(articleTags)) {
            articleTags = "待分类";
        }
        articleTags = Tag.formatTags(articleTags);
        if (Article.ARTICLE_TYPE_C_QNA == articleType && !StringUtils.contains(articleTags, "Q&A")) {
            articleTags += ",Q&A";
        }
        article.put(Article.ARTICLE_TAGS, articleTags);
        final String[] tagTitles = articleTags.split(",");

        tag(tagTitles, article, author);

        final String ip = requestJSONObject.optString(Article.ARTICLE_IP);
        article.put(Article.ARTICLE_IP, ip);

        String ua = requestJSONObject.optString(Article.ARTICLE_UA);
        if (StringUtils.length(ua) > Common.MAX_LENGTH_UA) {
            ua = StringUtils.substring(ua, 0, Common.MAX_LENGTH_UA);
        }
        article.put(Article.ARTICLE_UA, ua);

        article.put(Article.ARTICLE_STICK, 0L);
        final JSONObject articleCntOption = optionRepository.get(Option.ID_C_STATISTIC_ARTICLE_COUNT);
        final int articleCnt = articleCntOption.optInt(Option.OPTION_VALUE);
        articleCntOption.put(Option.OPTION_VALUE, articleCnt + 1);
        optionRepository.update(Option.ID_C_STATISTIC_ARTICLE_COUNT, articleCntOption);

        if (!StringUtils.isBlank(city)) {
            final String cityStatId = city + "-ArticleCount";
            JSONObject cityArticleCntOption = optionRepository.get(cityStatId);

            if (null == cityArticleCntOption) {
                cityArticleCntOption = new JSONObject();
                cityArticleCntOption.put(Keys.OBJECT_ID, cityStatId);
                cityArticleCntOption.put(Option.OPTION_VALUE, 1);
                cityArticleCntOption.put(Option.OPTION_CATEGORY, city + "-statistic");

                optionRepository.add(cityArticleCntOption);
            } else {
                final int cityArticleCnt = cityArticleCntOption.optInt(Option.OPTION_VALUE);
                cityArticleCntOption.put(Option.OPTION_VALUE, cityArticleCnt + 1);

                optionRepository.update(cityStatId, cityArticleCntOption);
            }
        }

        author.put(UserExt.USER_ARTICLE_COUNT, author.optInt(UserExt.USER_ARTICLE_COUNT) + 1);
        author.put(UserExt.USER_LATEST_ARTICLE_TIME, currentTimeMillis);
        // Updates user article count (and new tag count), latest article time
        userRepository.update(author.optString(Keys.OBJECT_ID), author, UserExt.USER_ARTICLE_COUNT,
                UserExt.USER_LATEST_ARTICLE_TIME, UserExt.USER_TAG_COUNT);

        final String articleId = articleRepository.add(article);

        longArticleColumnMgmtService.syncChapterInCurrentTransaction(article.put(Keys.OBJECT_ID, articleId), requestJSONObject);

        if (Article.ARTICLE_TYPE_C_THOUGHT != articleType) {
            final JSONObject revision = new JSONObject();
            revision.put(Revision.REVISION_AUTHOR_ID, authorId);
            final JSONObject revisionData = new JSONObject();
            revisionData.put(Article.ARTICLE_TITLE, articleTitle);
            revisionData.put(Article.ARTICLE_CONTENT, articleContent);
            revision.put(Revision.REVISION_DATA, revisionData.toString());
            revision.put(Revision.REVISION_DATA_ID, articleId);
            revision.put(Revision.REVISION_DATA_TYPE, Revision.DATA_TYPE_C_ARTICLE);
            revisionRepository.add(revision);
        }

        final List<String> transfers = ArticlePublishPoints.charge(new ArticlePublishPoints.Store() {
            public boolean inTransaction() { return articleRepository.hasTransactionBegun(); }
            public int lockAuthorBalance() throws RepositoryException {
                return userRepository.getForUpdate(authorId).optInt(UserExt.USER_POINT);
            }
            public String debit(final int type, final int amount) {
                return pointtransferMgmtService.transferInCurrentTransaction(authorId, Pointtransfer.ID_C_SYS,
                        type, amount, articleId, currentTimeMillis, "", PointtransferSource.EMPTY, false);
            }
        }, pointPlan);
        professionSourceEventCaptureService.articlePublished(article);
        return new Publication(article, userRepository.getForUpdate(authorId),
                new JSONObject(requestJSONObject.toString()), transfers);

    }

    /** Only call after a confirmed fresh commit, never on an idempotency replay. */
    public void afterArticlePublished(final Publication publication) {
        if (articleRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) {
            throw new IllegalStateException("publication side effects before transaction end");
        }
        final JSONObject article = publication.article(), request = publication.request(), author = publication.author();
        final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID), articleId = article.optString(Keys.OBJECT_ID);
        for (final String transferId : publication.transferIds()) {
            try { pointtransferMgmtService.indexTransferAsync(authorId, Pointtransfer.ID_C_SYS, transferId); }
            catch (final RuntimeException e) { LOGGER.error("Index publication transfer failed", e); }
        }
        try { tagMgmtService.relateTags(article.optString(Article.ARTICLE_TAGS)); }
        catch (final Exception e) { LOGGER.error("Grow publication tag graph failed", e); }
        if (Article.ARTICLE_ANONYMOUS_C_PUBLIC == article.optInt(Article.ARTICLE_ANONYMOUS)
                && "yes".equals(request.optString(Article.ARTICLE_GOOD_POST))) {
            try {
                livenessMgmtService.incLiveness(authorId, Liveness.LIVENESS_ARTICLE);
                LogsService.log("simple", LogsService.getTime(), "*", "发帖并标记为有奖励积分",
                        "发布人：" + author.optString(User.USER_NAME) + "，点击跳转至帖子：<a target='_blank' href='/article/"
                                + articleId + "'>" + article.optString(Article.ARTICLE_TITLE) + "</a>", true);
            } catch (final Exception e) { LOGGER.error("Record publication liveness failed", e); }
        }
        final JSONObject eventData = new JSONObject().put(Article.ARTICLE, article)
                .put(Article.ARTICLE_T_NOTIFY_FOLLOWERS, request.optBoolean(Article.ARTICLE_T_NOTIFY_FOLLOWERS))
                .put(Article.ARTICLE_TAGS, request.optString(Article.ARTICLE_TAGS)).put("author", author);
        try { eventManager.fireEventAsynchronously(new Event<>(EventTypes.ADD_ARTICLE, eventData)); }
        catch (final Exception e) { LOGGER.error("Dispatch publication event failed", e); }
    }

    /**
     * Updates an article with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "oId": "",
     *                          "articleTitle": "",
     *                          "articleTags": "",
     *                          "articleContent": "",
     *                          "articleEditorType": "",
     *                          "articleCommentable": boolean, // optional, default to true
     *                          "articleType": int // optional, default to 0
     *                          "articleRewardContent": "", // optional, default to ""
     *                          "articleRewardPoint": int, // optional, default to 0
     *                          "articleQnAOfferPoint": int, // optional, default to 0
     *                          "articleIP": "", // optional, default to ""
     *                          "articleUA": "", // optional default to ""
     *                          "articleNotifyFollowers": boolean
     * @throws ServiceException service exception
     */
    public void updateArticle(final JSONObject requestJSONObject) throws ServiceException {
        try {
            withPublicationLock(requestJSONObject.optString(Article.ARTICLE_AUTHOR_ID), () -> {
                final Transaction tx = articleRepository.beginTransaction();
                try {
                    final var edit = updateArticleInCurrentTransaction(requestJSONObject);
                    tx.commit();
                    return edit;
                } finally { if (tx.isActive()) tx.rollback(); }
            }, this::afterArticleEdited);
        } catch (final ServiceException e) { throw e; }
        catch (final Exception e) { LOGGER.error("Updates an article failed", e); throw new ServiceException(e); }
    }

    /** Private persistence data; only the caller's response whitelist may be returned to clients. */
    public record Edit(JSONObject article, JSONObject previous, boolean notifyFollowers, List<String> transferIds) {
        public Edit { transferIds = List.copyOf(transferIds); }
    }

    public Edit updateArticleInCurrentTransaction(final JSONObject requestJSONObject) throws Exception {
        return updateArticleInCurrentTransaction(requestJSONObject, false);
    }

    public Edit updateArticleInCurrentTransaction(final JSONObject requestJSONObject, final boolean requireUnambiguousParagraphs) throws Exception {
        if (!articleRepository.hasTransactionBegun() || !ArticlePublicationCaches.active()) throw new IllegalStateException("article edit transaction required");
        if (null == optionRepository.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        // Author comes from the authenticated boundary; all edits use the publication lock ordering.
        if (null == userRepository.getForUpdate(requestJSONObject.optString(Article.ARTICLE_AUTHOR_ID))) throw new ServiceException("用户不存在");
        String articleTitle = requestJSONObject.optString(Article.ARTICLE_TITLE);

        String articleId;
        JSONObject articleToUpdate;
        JSONObject oldArticle;
        String authorId;
        JSONObject author;
        int updatePointSum;
        int articleAnonymous;

        try {
            // check if admin allow to add article
            final JSONObject option = optionRepository.get(Option.ID_C_MISC_ALLOW_ADD_ARTICLE);

            if (!"0".equals(option.optString(Option.OPTION_VALUE))) {
                throw new ServiceException(langPropsService.get("notAllowAddArticleLabel"));
            }

            articleId = requestJSONObject.optString(Keys.OBJECT_ID);
            articleToUpdate = articleRepository.getForUpdate(articleId);
            if (null == articleToUpdate) throw new ServiceException("文章不存在");
            if (!requestJSONObject.optString(Article.ARTICLE_AUTHOR_ID).equals(articleToUpdate.optString(Article.ARTICLE_AUTHOR_ID))) throw new ServiceException("无权编辑文章");
            if (Article.ARTICLE_STATUS_C_VALID != articleToUpdate.optInt(Article.ARTICLE_STATUS)) throw new ServiceException(langPropsService.get("articleLockedLabel"));
            if (articleToUpdate.optString(Article.ARTICLE_TITLE).startsWith("摸鱼周报 ")
                    && !articleToUpdate.optString(Article.ARTICLE_TITLE).equals(articleTitle)) throw new ServiceException("无法修改摸鱼周报的标题，请认真维护！");
            oldArticle = JSONs.clone(articleToUpdate);
            authorId = articleToUpdate.optString(Article.ARTICLE_AUTHOR_ID);
            author = userRepository.getForUpdate(authorId);
            if (UserExt.USER_STATUS_C_VALID != author.optInt(UserExt.USER_STATUS)) {
                throw new ServiceException(langPropsService.get("userStatusInvalidLabel"));
            }

            final long followerCnt = followQueryService.getFollowerCount(authorId, Follow.FOLLOWING_TYPE_C_USER);
            final long collectCnt = followQueryService.getFollowerCount(articleId, Follow.FOLLOWING_TYPE_C_ARTICLE);
            final long watchCnt = followQueryService.getFollowerCount(articleId, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH);
            updatePointSum = ArticleEditPoints.fee(Pointtransfer.TRANSFER_SUM_C_UPDATE_ARTICLE, followerCnt, collectCnt, watchCnt);

            articleAnonymous = articleToUpdate.optInt(Article.ARTICLE_ANONYMOUS);

            if (Article.ARTICLE_ANONYMOUS_C_PUBLIC == articleAnonymous) {
                final int balance = author.optInt(UserExt.USER_POINT);
                if (balance < updatePointSum) {
                    throw new ServiceException(langPropsService.get("insufficientBalanceLabel"));
                }
            }

            final JSONObject maybeExist = articleRepository.getByTitleForUpdate(articleTitle);
            if (null != maybeExist) {
                if (!articleToUpdate.optString(Article.ARTICLE_TITLE).equals(articleTitle)) {
                    final String existArticleAuthorId = maybeExist.optString(Article.ARTICLE_AUTHOR_ID);
                    String msg;
                    if (existArticleAuthorId.equals(authorId)) {
                        msg = langPropsService.get("duplicatedArticleTitleSelfLabel");
                        msg = msg.replace("{article}", "<a target='_blank' href='/article/" + maybeExist.optString(Keys.OBJECT_ID)
                                + "'>" + articleTitle + "</a>");
                    } else {
                        final JSONObject existArticleAuthor = userRepository.get(existArticleAuthorId);
                        final String userName = existArticleAuthor.optString(User.USER_NAME);
                        msg = langPropsService.get("duplicatedArticleTitleLabel");
                        msg = msg.replace("{user}", "<a target='_blank' href='/member/" + userName + "'>" + userName + "</a>");
                        msg = msg.replace("{article}", "<a target='_blank' href='/article/" + maybeExist.optString(Keys.OBJECT_ID)
                                + "'>" + articleTitle + "</a>");
                    }

                    throw new ServiceException(msg);
                }
            }
        } catch (final RepositoryException e) {
            throw new ServiceException(e);
        }

        final int qnaOfferPoint = requestJSONObject.optInt(Article.ARTICLE_QNA_OFFER_POINT, 0);
        if (qnaOfferPoint < articleToUpdate.optInt(Article.ARTICLE_QNA_OFFER_POINT)) { // Increase only to prevent lowering points when adopting answer
            throw new ServiceException(langPropsService.get("qnaOfferPointMustMoreThanOldLabel"));
        }
        articleToUpdate.put(Article.ARTICLE_QNA_OFFER_POINT, qnaOfferPoint);

        final int articleType = requestJSONObject.optInt(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
        final int oldArticleType = articleToUpdate.optInt(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);

        requestJSONObject.put(Article.ARTICLE_ANONYMOUS, articleAnonymous);
        processTagsForArticleUpdate(articleToUpdate, requestJSONObject, author);
        userRepository.update(author.optString(Keys.OBJECT_ID), author, UserExt.USER_TAG_COUNT);

        articleTitle = Emotions.toAliases(articleTitle);
        articleTitle = Pangu.spacingText(articleTitle);

        final String oldTitle = articleToUpdate.optString(Article.ARTICLE_TITLE);
        articleToUpdate.put(Article.ARTICLE_TITLE, articleTitle);

        articleToUpdate.put(Article.ARTICLE_TAGS, requestJSONObject.optString(Article.ARTICLE_TAGS));
        articleToUpdate.put(Article.ARTICLE_COMMENTABLE, requestJSONObject.optBoolean(Article.ARTICLE_COMMENTABLE, true));
        articleToUpdate.put(Article.ARTICLE_STATEMENT, requestJSONObject.optInt(Article.ARTICLE_STATEMENT,0));
        articleToUpdate.put(Article.ARTICLE_TYPE, articleType);
        articleToUpdate.put(Article.ARTICLE_SHOW_IN_LIST, requestJSONObject.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES));
        String articleContent = requestJSONObject.optString(Article.ARTICLE_CONTENT);
        articleContent = Emotions.toAliases(articleContent);
        //articleContent = StringUtils.trim(articleContent) + " "; https://github.com/b3log/symphony/issues/389
        articleContent = articleContent.replace(langPropsService.get("uploadingLabel", Locale.SIMPLIFIED_CHINESE), "");
        articleContent = articleContent.replace(langPropsService.get("uploadingLabel", Locale.US), "");
        if (Article.ARTICLE_TYPE_C_THOUGHT != articleType) {
            articleContent = Runes.removeControlChars(articleContent);
        }

        final String oldContent = articleToUpdate.optString(Article.ARTICLE_CONTENT);
        articleToUpdate.put(Article.ARTICLE_CONTENT, articleContent);

        final long currentTimeMillis = System.currentTimeMillis();
        final long createTime = articleToUpdate.optLong(Keys.OBJECT_ID);
        final boolean notIn5m = ArticleEditPoints.pastFreeWindow(createTime, currentTimeMillis);

        articleToUpdate.put(Article.ARTICLE_UPDATE_TIME, currentTimeMillis);

        final int rewardPoint = requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT, 0);
        boolean enableReward = false;
        if (0 < rewardPoint) {
            if (1 > articleToUpdate.optInt(Article.ARTICLE_REWARD_POINT)) {
                enableReward = true;
            }

            String rewardContent = requestJSONObject.optString(Article.ARTICLE_REWARD_CONTENT);
            rewardContent = Emotions.toAliases(rewardContent);
            rewardContent = Runes.removeControlChars(rewardContent);
            articleToUpdate.put(Article.ARTICLE_REWARD_CONTENT, rewardContent);
            articleToUpdate.put(Article.ARTICLE_REWARD_POINT, rewardPoint);
        }

        final String ip = requestJSONObject.optString(Article.ARTICLE_IP);
        articleToUpdate.put(Article.ARTICLE_IP, ip);

        String ua = requestJSONObject.optString(Article.ARTICLE_UA);
        if (StringUtils.length(ua) > Common.MAX_LENGTH_UA) {
            ua = StringUtils.substring(ua, 0, Common.MAX_LENGTH_UA);
        }
        articleToUpdate.put(Article.ARTICLE_UA, ua);
        articleToUpdate.put(Article.ARTICLE_AUDIO_URL, ""); // 小薇语音预览更新 https://github.com/b3log/symphony/issues/791

        articleRepository.update(articleId, articleToUpdate);
        longArticleColumnMgmtService.syncChapterInCurrentTransaction(articleToUpdate, requestJSONObject);

        final boolean titleChanged = !oldTitle.replaceAll("\\s+", "").equals(articleTitle.replaceAll("\\s+", ""));
        final boolean contentChanged = !oldContent.replaceAll("\\s+", "").equals(articleContent.replaceAll("\\s+", ""));
        final boolean paragraphStructureChanged = !StringUtils.equals(oldContent, articleContent);
        if (paragraphStructureChanged && (Article.ARTICLE_TYPE_C_LONG == articleType
                || Article.ARTICLE_TYPE_C_LONG == oldArticleType)) {
            longArticleParagraphService.migrateCommentsInCurrentTransaction(articleId, oldContent, articleContent,
                    requireUnambiguousParagraphs ? paragraphContentQueryService.paragraphTitleSnapshot() : null, requireUnambiguousParagraphs);
        }
        if (notIn5m && Article.ARTICLE_TYPE_C_THOUGHT != articleType
                && (titleChanged || contentChanged)) {
            final JSONObject revision = new JSONObject();
            revision.put(Revision.REVISION_AUTHOR_ID, authorId);
            final JSONObject revisionData = new JSONObject();
            revisionData.put(Article.ARTICLE_TITLE, articleTitle);
            revisionData.put(Article.ARTICLE_CONTENT, articleContent);
            revision.put(Revision.REVISION_DATA, revisionData.toString());
            revision.put(Revision.REVISION_DATA_ID, articleId);
            revision.put(Revision.REVISION_DATA_TYPE, Revision.DATA_TYPE_C_ARTICLE);
            revisionRepository.add(revision);
        }

        final var pointPlan = ArticleEditPoints.plan(Article.ARTICLE_ANONYMOUS_C_ANONYMOUS == articleAnonymous,
                notIn5m, updatePointSum, enableReward, Pointtransfer.TRANSFER_SUM_C_ADD_ARTICLE_REWARD,
                Pointtransfer.TRANSFER_TYPE_C_UPDATE_ARTICLE, Pointtransfer.TRANSFER_TYPE_C_ADD_ARTICLE_REWARD);
        if (Article.ARTICLE_ANONYMOUS_C_PUBLIC == articleAnonymous
                && userRepository.getForUpdate(authorId).optInt(UserExt.USER_POINT) < pointPlan.minimumBalance()) {
            throw new ServiceException(langPropsService.get("insufficientBalanceLabel"));
        }
        final List<String> transfers = ArticlePublishPoints.charge(new ArticlePublishPoints.Store() {
            public boolean inTransaction() { return articleRepository.hasTransactionBegun(); }
            public int lockAuthorBalance() throws RepositoryException { return userRepository.getForUpdate(authorId).optInt(UserExt.USER_POINT); }
            public String debit(final int type, final int amount) {
                return pointtransferMgmtService.transferInCurrentTransaction(authorId, Pointtransfer.ID_C_SYS,
                        type, amount, articleId, currentTimeMillis, "", PointtransferSource.EMPTY, false);
            }
        }, pointPlan);
        return new Edit(articleToUpdate, oldArticle, requestJSONObject.optBoolean(Article.ARTICLE_T_NOTIFY_FOLLOWERS), transfers);
    }

    public void afterArticleEdited(final Edit edit) {
        if (articleRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("edit effects before transaction end");
        final String author = edit.article().optString(Article.ARTICLE_AUTHOR_ID);
        for (final String transfer : edit.transferIds()) {
            try { pointtransferMgmtService.indexTransferAsync(author, Pointtransfer.ID_C_SYS, transfer); }
            catch (final RuntimeException e) { LOGGER.error("Index article edit transfer failed", e); }
        }
        final JSONObject event = new JSONObject().put(Article.ARTICLE, edit.article()).put(Common.OLD_ARTICLE, edit.previous())
                .put(Article.ARTICLE_T_NOTIFY_FOLLOWERS, edit.notifyFollowers());
        try { eventManager.fireEventAsynchronously(new Event<>(EventTypes.UPDATE_ARTICLE, event)); }
        catch (final Exception e) { LOGGER.error("Dispatch committed article edit failed", e); }
    }

    /**
     * Updates the specified article by the given article id.
     * <p>
     * <b>Note</b>: This method just for admin console.
     * </p>
     *
     * @param articleId the given article id
     * @param article   the specified article
     * @throws ServiceException service exception
     */
    public void updateArticleByAdmin(final String articleId, final JSONObject article) throws ServiceException {
        updateArticleByAdmin(articleId, article, null);
    }

    /**
     * Updates the specified article by the given article id.
     * <p>
     * <b>Note</b>: This method just for admin console.
     * </p>
     *
     * @param articleId                 the given article id
     * @param article                   the specified article
     * @param longArticleColumnRequest  long article column request, {@code null} for no column change
     * @throws ServiceException service exception
     */
    public void updateArticleByAdmin(final String articleId, final JSONObject article,
                                     final JSONObject longArticleColumnRequest) throws ServiceException {
        final Transaction transaction = articleRepository.beginTransaction();

        try {
            final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);
            final JSONObject author = userRepository.get(authorId);

            article.put(Article.ARTICLE_COMMENTABLE, Boolean.valueOf(article.optBoolean(Article.ARTICLE_COMMENTABLE)));
            article.put(Article.ARTICLE_STATEMENT, Integer.valueOf(article.optInt(Article.ARTICLE_STATEMENT, 0)));

            final JSONObject oldArticle = articleRepository.get(articleId);
            final int oldArticleType = oldArticle.optInt(Article.ARTICLE_TYPE);
            final String oldContent = oldArticle.optString(Article.ARTICLE_CONTENT);

            if (Article.ARTICLE_STATUS_C_INVALID == article.optInt(Article.ARTICLE_STATUS)) {
                article.put(Article.ARTICLE_TAGS, "回收站");
            }

            processTagsForArticleUpdate(oldArticle, article, author);

            String articleTitle = article.optString(Article.ARTICLE_TITLE);
            articleTitle = Emotions.toAliases(articleTitle);
            article.put(Article.ARTICLE_TITLE, articleTitle);

            if (Article.ARTICLE_TYPE_C_THOUGHT == article.optInt(Article.ARTICLE_TYPE)) {
                article.put(Article.ARTICLE_CONTENT, oldArticle.optString(Article.ARTICLE_CONTENT));
            } else {
                String articleContent = article.optString(Article.ARTICLE_CONTENT);
                articleContent = Emotions.toAliases(articleContent);
                articleContent = Runes.removeControlChars(articleContent);
                article.put(Article.ARTICLE_CONTENT, articleContent);
            }

            final int perfect = article.optInt(Article.ARTICLE_PERFECT);
            if (Article.ARTICLE_PERFECT_C_PERFECT == perfect) {
                // if it is perfect, allow anonymous view
                article.put(Article.ARTICLE_ANONYMOUS_VIEW, Article.ARTICLE_ANONYMOUS_VIEW_C_ALLOW);

                // updates tag-article perfect
                final List<JSONObject> tagArticleRels = tagArticleRepository.getByArticleId(articleId);
                for (final JSONObject tagArticleRel : tagArticleRels) {
                    tagArticleRel.put(Article.ARTICLE_PERFECT, Article.ARTICLE_PERFECT_C_PERFECT);

                    tagArticleRepository.update(tagArticleRel.optString(Keys.OBJECT_ID), tagArticleRel);
                }
            }

            article.put(Article.ARTICLE_AUDIO_URL, ""); // 小薇语音预览更新 https://github.com/b3log/symphony/issues/791

            userRepository.update(authorId, author);
            articleRepository.update(articleId, article);

            final int articleType = article.optInt(Article.ARTICLE_TYPE);
            final String articleContent = article.optString(Article.ARTICLE_CONTENT);
            final boolean paragraphStructureChanged = !StringUtils.equals(oldContent, articleContent);
            if (paragraphStructureChanged && (Article.ARTICLE_TYPE_C_LONG == articleType
                    || Article.ARTICLE_TYPE_C_LONG == oldArticleType)) {
                longArticleParagraphService.migrateCommentsInCurrentTransaction(articleId, oldContent, articleContent);
            }

            if (null != longArticleColumnRequest) {
                article.put(Keys.OBJECT_ID, articleId);
                longArticleColumnMgmtService.syncChapterInCurrentTransaction(article, longArticleColumnRequest);
                updateColumnCoverByAdminInCurrentTransaction(article, longArticleColumnRequest);
            }

            transaction.commit();

            if (Article.ARTICLE_PERFECT_C_NOT_PERFECT == oldArticle.optInt(Article.ARTICLE_PERFECT)
                    && Article.ARTICLE_PERFECT_C_PERFECT == perfect) {
                final JSONObject notification = new JSONObject();
                notification.put(Notification.NOTIFICATION_USER_ID, authorId);
                notification.put(Notification.NOTIFICATION_DATA_ID, articleId);

                notificationMgmtService.addPerfectArticleNotification(notification);

                pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, authorId,
                        Pointtransfer.TRANSFER_TYPE_C_PERFECT_ARTICLE, Pointtransfer.TRANSFER_SUM_C_PERFECT_ARTICLE,
                        articleId, System.currentTimeMillis(), "");
            }
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Updates an article[id=" + articleId + "] failed", e);
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }

            throw new ServiceException(e);
        }
    }

    private void updateColumnCoverByAdminInCurrentTransaction(final JSONObject article,
                                                             final JSONObject request) throws ServiceException {
        if (Article.ARTICLE_TYPE_C_LONG != article.optInt(Article.ARTICLE_TYPE)
                || !request.has(LongArticleColumn.COLUMN_COVER_URL)) {
            return;
        }

        final String columnId = StringUtils.trimToEmpty(request.optString(LongArticleColumn.COLUMN_ID));
        if (StringUtils.isBlank(columnId) || "__NEW__".equals(columnId)) {
            return;
        }

        columnCoverMgmtService.updateOwnedCoverURLInCurrentTransaction(
                article.optString(Article.ARTICLE_AUTHOR_ID),
                columnId,
                request.optString(LongArticleColumn.COLUMN_COVER_URL));
    }

    /**
     * A user specified by the given sender id rewards the author of an article specified by the given article id.
     *
     * @param articleId the given article id
     * @param senderId  the given sender id
     * @throws ServiceException service exception
     */
    public void reward(final String articleId, final String senderId) throws ServiceException {
        legacyArticleReward(articleId, senderId, ArticleRewardMgmtService.Kind.REWARD, (actor, article) -> { });
    }

    /** Explicit reader authorization and purchase eligibility for the legacy paid HTML response. */
    public ArticleRewardMgmtService.Result rewardForViewer(final String articleId, final String senderId,
                                                            final ArticleRewardMgmtService.TargetCheck check) throws ServiceException {
        return legacyArticleReward(articleId, senderId, ArticleRewardMgmtService.Kind.REWARD, check);
    }

    /**
     * A user specified by the given sender id thanks the author of an article specified by the given article id.
     *
     * @param articleId the given article id
     * @param senderId  the given sender id
     * @throws ServiceException service exception
     */
    public void thank(final String articleId, final String senderId) throws ServiceException {
        legacyArticleReward(articleId, senderId, ArticleRewardMgmtService.Kind.THANK, (actor, article) -> { });
    }

    private ArticleRewardMgmtService.Result legacyArticleReward(final String articleId, final String senderId,
                                                                 final ArticleRewardMgmtService.Kind kind,
                                                                 final ArticleRewardMgmtService.TargetCheck check) throws ServiceException {
        try {
            final String expectedAuthor = articleRepository.getCommentPublicationAuthor(articleId);
            if (expectedAuthor.isBlank()) return new ArticleRewardMgmtService.Result(senderId, "", kind, ArticleRewardMgmtService.State.UNAVAILABLE, null);
            return withPublicationLocks(senderId, List.of(Pointtransfer.ID_C_SYS, expectedAuthor), () -> {
                final Transaction transaction = articleRepository.beginTransaction();
                try {
                    final var result = articleRewards.applyInCurrentTransaction(senderId, articleId, expectedAuthor, kind, check);
                    transaction.commit(); return result;
                } finally { if (transaction.isActive()) transaction.rollback(); }
            }, articleRewards::afterApplied);
        } catch (final ServiceException e) { throw e; }
        catch (final Exception e) { LOGGER.error("Article reward failed [id={}]", articleId, e); throw new ServiceException(e); }
    }

    /**
     * Sticks an article specified by the given article id.
     *
     * @param articleId the given article id
     * @throws ServiceException service exception
     */
    public synchronized void stick(final String articleId) throws ServiceException {
        final Transaction transaction = articleRepository.beginTransaction();

        try {
            final JSONObject article = articleRepository.get(articleId);
            if (null == article) {
                return;
            }

            final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);
            final JSONObject author = userRepository.get(authorId);
            final int balance = author.optInt(UserExt.USER_POINT);

            if (balance - Pointtransfer.TRANSFER_SUM_C_STICK_ARTICLE < 0) {
                throw new ServiceException(langPropsService.get("insufficientBalanceLabel"));
            }

            final Query query = new Query().setFilter(new PropertyFilter(Article.ARTICLE_STICK, FilterOperator.GREATER_THAN, 0L));
            final List<JSONObject> articles = articleRepository.getList(query);
            if (!articles.isEmpty()) {
                final Set<String> ids = new HashSet<>();
                for (final JSONObject jsonObject : articles) {
                    ids.add(jsonObject.optString(Keys.OBJECT_ID));
                }

                if (!ids.contains(articleId)) {
                    // throw new ServiceException(langPropsService.get("stickExistLabel"));
                }
            }

            article.put(Article.ARTICLE_STICK, System.currentTimeMillis());

            articleRepository.update(articleId, article, Article.ARTICLE_STICK);

            transaction.commit();

            final boolean succ = null != pointtransferMgmtService.transfer(article.optString(Article.ARTICLE_AUTHOR_ID),
                    Pointtransfer.ID_C_SYS, Pointtransfer.TRANSFER_TYPE_C_STICK_ARTICLE,
                    Pointtransfer.TRANSFER_SUM_C_STICK_ARTICLE, articleId, System.currentTimeMillis(), "");
            if (!succ) {
                throw new ServiceException(langPropsService.get("stickFailedLabel"));
            }
        } catch (final RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Sticks an article[id=" + articleId + "] failed", e);

            throw new ServiceException(langPropsService.get("stickFailedLabel"));
        }
    }

    /**
     * Admin sticks an article specified by the given article id.
     *
     * @param articleId the given article id
     */
    @Transactional
    public synchronized void adminStick(final String articleId) {
        try {
            final JSONObject article = articleRepository.get(articleId);
            if (null == article) {
                return;
            }

            article.put(Article.ARTICLE_STICK, Long.MAX_VALUE);

            articleRepository.update(articleId, article, Article.ARTICLE_STICK);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Admin sticks an article[id=" + articleId + "] failed", e);
        }
    }

    /**
     * Admin cancels stick an article specified by the given article id.
     *
     * @param articleId the given article id
     */
    @Transactional
    public synchronized void adminCancelStick(final String articleId) {
        try {
            final JSONObject article = articleRepository.get(articleId);
            if (null == article) {
                return;
            }

            article.put(Article.ARTICLE_STICK, 0L);

            articleRepository.update(articleId, article, Article.ARTICLE_STICK);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Admin cancel sticks an article[id=" + articleId + "] failed", e);
        }
    }

    /**
     * Expires sticked articles.
     *
     * @throws ServiceException service exception
     */
    @Transactional
    public void expireStick() throws ServiceException {
        try {
            final Query query = new Query().setFilter(new PropertyFilter(Article.ARTICLE_STICK, FilterOperator.GREATER_THAN, 0L));
            final List<JSONObject> articles = articleRepository.getList(query);
            if (articles.isEmpty()) {
                return;
            }

            final long stepTime = Symphonys.STICK_ARTICLE_TIME;
            final long now = System.currentTimeMillis();

            for (final JSONObject article : articles) {
                final long stick = article.optLong(Article.ARTICLE_STICK);
                if (stick >= Long.MAX_VALUE) {
                    continue; // Skip admin stick
                }

                articleRepository.expireStickIfCurrentInCurrentTransaction(
                        article.optString(Keys.OBJECT_ID), stick, now, stepTime);
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Expires sticked articles failed", e);
            throw new ServiceException();
        }
    }

    /**
     * Processes tags for article update.
     * <p>
     * <ul>
     * <li>Un-tags old article, decrements tag reference count</li>
     * <li>Removes old article-tag relations</li>
     * <li>Saves new article-tag relations with tag reference count</li>
     * </ul>
     * </p>
     *
     * @param oldArticle the specified old article
     * @param newArticle the specified new article
     * @param author     the specified author
     * @throws Exception exception
     */
    private synchronized void processTagsForArticleUpdate(final JSONObject oldArticle, final JSONObject newArticle,
                                                          final JSONObject author) throws Exception {
        final String oldArticleId = oldArticle.getString(Keys.OBJECT_ID);
        final List<JSONObject> oldTags = tagRepository.getByArticleId(oldArticleId);
        String tagsString = newArticle.getString(Article.ARTICLE_TAGS);
        tagsString = Tag.formatTags(tagsString);
        if (StringUtils.containsIgnoreCase(tagsString, Tag.TAG_TITLE_C_SANDBOX)) {
            tagsString = Tag.TAG_TITLE_C_SANDBOX;
        }
        final int articleType = newArticle.optInt(Article.ARTICLE_TYPE);
        if (StringUtils.isBlank(tagsString)) {
            tagsString = "待分类";
        }

        tagsString = Tag.formatTags(tagsString);
        if (Article.ARTICLE_TYPE_C_QNA == articleType && !StringUtils.contains(tagsString, "Q&A")) {
            tagsString += ",Q&A";
        }
        newArticle.put(Article.ARTICLE_TAGS, tagsString);
        String[] tagStrings = tagsString.split(",");

        final List<JSONObject> newTags = new ArrayList<>();

        for (final String tagString : tagStrings) {
            final String tagTitle = tagString.trim();
            JSONObject newTag = tagRepository.getByTitle(tagTitle);
            if (null == newTag) {
                newTag = new JSONObject();
                newTag.put(Tag.TAG_TITLE, tagTitle);
            }

            newTags.add(newTag);
        }

        final List<JSONObject> tagsDropped = new ArrayList<>();
        final List<JSONObject> tagsNeedToAdd = new ArrayList<>();

        for (final JSONObject newTag : newTags) {
            final String newTagTitle = newTag.getString(Tag.TAG_TITLE);

            if (!tagExists(newTagTitle, oldTags)) {
                LOGGER.log(Level.DEBUG, "Tag need to add [title={}]", newTagTitle);
                tagsNeedToAdd.add(newTag);
            }
        }
        for (final JSONObject oldTag : oldTags) {
            final String oldTagTitle = oldTag.getString(Tag.TAG_TITLE);

            if (!tagExists(oldTagTitle, newTags)) {
                LOGGER.log(Level.DEBUG, "Tag dropped [title={}]", oldTag);
                tagsDropped.add(oldTag);
            }
        }

        final int articleCmtCnt = oldArticle.getInt(Article.ARTICLE_COMMENT_CNT);

        for (final JSONObject tagDropped : tagsDropped) {
            final String tagId = tagDropped.getString(Keys.OBJECT_ID);
            int refCnt = tagDropped.getInt(Tag.TAG_REFERENCE_CNT) - 1;
            refCnt = refCnt < 0 ? 0 : refCnt;
            tagDropped.put(Tag.TAG_REFERENCE_CNT, refCnt);
            final int tagCmtCnt = tagDropped.getInt(Tag.TAG_COMMENT_CNT);
            tagDropped.put(Tag.TAG_COMMENT_CNT, tagCmtCnt - articleCmtCnt);
            tagDropped.put(Tag.TAG_RANDOM_DOUBLE, Math.random());

            tagRepository.update(tagId, tagDropped);
        }

        final String[] tagIdsDropped = new String[tagsDropped.size()];

        for (int i = 0; i < tagIdsDropped.length; i++) {
            final JSONObject tag = tagsDropped.get(i);
            final String id = tag.getString(Keys.OBJECT_ID);

            tagIdsDropped[i] = id;
        }

        if (0 != tagIdsDropped.length) {
            removeTagArticleRelations(oldArticleId, tagIdsDropped);
            removeUserTagRelations(oldArticle.optString(Article.ARTICLE_AUTHOR_ID), Tag.TAG_TYPE_C_ARTICLE, tagIdsDropped);
        }

        tagStrings = new String[tagsNeedToAdd.size()];
        for (int i = 0; i < tagStrings.length; i++) {
            final JSONObject tag = tagsNeedToAdd.get(i);
            final String tagTitle = tag.getString(Tag.TAG_TITLE);

            tagStrings[i] = tagTitle;
        }

        newArticle.put(Article.ARTICLE_COMMENT_CNT, articleCmtCnt);
        tag(tagStrings, newArticle, author);
    }

    /**
     * Removes tag-article relations by the specified article id and tag ids of the relations to be removed.
     * <p>
     * Removes all relations if not specified the tag ids.
     * </p>
     *
     * @param articleId the specified article id
     * @param tagIds    the specified tag ids of the relations to be removed
     * @throws JSONException       json exception
     * @throws RepositoryException repository exception
     */
    private void removeTagArticleRelations(final String articleId, final String... tagIds)
            throws JSONException, RepositoryException {
        final List<String> tagIdList = Arrays.asList(tagIds);
        final List<JSONObject> tagArticleRelations = tagArticleRepository.getByArticleId(articleId);

        for (int i = 0; i < tagArticleRelations.size(); i++) {
            final JSONObject tagArticleRelation = tagArticleRelations.get(i);
            String relationId;

            if (tagIdList.isEmpty()) { // Removes all if un-specified
                relationId = tagArticleRelation.getString(Keys.OBJECT_ID);
                tagArticleRepository.remove(relationId);
            } else if (tagIdList.contains(tagArticleRelation.getString(Tag.TAG + "_" + Keys.OBJECT_ID))) {
                relationId = tagArticleRelation.getString(Keys.OBJECT_ID);
                tagArticleRepository.remove(relationId);
            }
        }
    }

    /**
     * Removes User-Tag relations by the specified user id, type and tag ids of the relations to be removed.
     *
     * @param userId the specified article id
     * @param type   the specified type
     * @param tagIds the specified tag ids of the relations to be removed
     * @throws RepositoryException repository exception
     */
    private void removeUserTagRelations(final String userId, final int type, final String... tagIds) throws RepositoryException {
        for (final String tagId : tagIds) {
            userTagRepository.removeByUserIdAndTagId(userId, tagId, type);
        }
    }

    /**
     * Tags the specified article with the specified tag titles.
     *
     * @param tagTitles the specified (new) tag titles
     * @param article   the specified article
     * @param author    the specified author
     * @throws RepositoryException repository exception
     */
    private synchronized void tag(final String[] tagTitles, final JSONObject article, final JSONObject author)
            throws RepositoryException {
        String articleTags = article.optString(Article.ARTICLE_TAGS);

        for (final String t : tagTitles) {
            final String tagTitle = t.trim();
            JSONObject tag = tagRepository.getByTitle(tagTitle);
            String tagId;
            int userTagType;
            final int articleCmtCnt = article.optInt(Article.ARTICLE_COMMENT_CNT);
            if (null == tag) {
                LOGGER.log(Level.TRACE, "Found a new tag [title={}] in article [title={}]",
                        tagTitle, article.optString(Article.ARTICLE_TITLE));
                tag = new JSONObject();
                tag.put(Tag.TAG_TITLE, tagTitle);
                final String tagURI = URLs.encode(tagTitle);
                tag.put(Tag.TAG_URI, StringUtils.lowerCase(tagURI));
                tag.put(Tag.TAG_CSS, "");
                tag.put(Tag.TAG_REFERENCE_CNT, 1);
                tag.put(Tag.TAG_COMMENT_CNT, articleCmtCnt);
                tag.put(Tag.TAG_FOLLOWER_CNT, 0);
                tag.put(Tag.TAG_LINK_CNT, 0);
                tag.put(Tag.TAG_DESCRIPTION, "");
                tag.put(Tag.TAG_ICON_PATH, "");
                tag.put(Tag.TAG_STATUS, 0);
                tag.put(Tag.TAG_GOOD_CNT, 0);
                tag.put(Tag.TAG_BAD_CNT, 0);
                tag.put(Tag.TAG_SEO_TITLE, tagTitle);
                tag.put(Tag.TAG_SEO_KEYWORDS, tagTitle);
                tag.put(Tag.TAG_SEO_DESC, "");
                tag.put(Tag.TAG_RANDOM_DOUBLE, Math.random());
                tag.put(Tag.TAG_AD, "");
                tag.put(Tag.TAG_SHOW_SIDE_AD, 0);

                tagId = tagRepository.add(tag);
                tag.put(Keys.OBJECT_ID, tagId);
                userTagType = Tag.TAG_TYPE_C_CREATOR;

                final JSONObject tagCntOption = optionRepository.get(Option.ID_C_STATISTIC_TAG_COUNT);
                final int tagCnt = tagCntOption.optInt(Option.OPTION_VALUE);
                tagCntOption.put(Option.OPTION_VALUE, tagCnt + 1);
                optionRepository.update(Option.ID_C_STATISTIC_TAG_COUNT, tagCntOption);

                author.put(UserExt.USER_TAG_COUNT, author.optInt(UserExt.USER_TAG_COUNT) + 1);
            } else {
                tagId = tag.optString(Keys.OBJECT_ID);
                LOGGER.log(Level.TRACE, "Found a existing tag[title={}, id={}] in article[title={}]",
                        tag.optString(Tag.TAG_TITLE), tag.optString(Keys.OBJECT_ID), article.optString(Article.ARTICLE_TITLE));
                final JSONObject tagTmp = new JSONObject();
                tagTmp.put(Keys.OBJECT_ID, tagId);
                final String title = tag.optString(Tag.TAG_TITLE);

                tagTmp.put(Tag.TAG_TITLE, title);
                tagTmp.put(Tag.TAG_COMMENT_CNT, tag.optInt(Tag.TAG_COMMENT_CNT) + articleCmtCnt);
                tagTmp.put(Tag.TAG_STATUS, tag.optInt(Tag.TAG_STATUS));
                tagTmp.put(Tag.TAG_REFERENCE_CNT, tag.optInt(Tag.TAG_REFERENCE_CNT) + 1);
                tagTmp.put(Tag.TAG_FOLLOWER_CNT, tag.optInt(Tag.TAG_FOLLOWER_CNT));
                tagTmp.put(Tag.TAG_LINK_CNT, tag.optInt(Tag.TAG_LINK_CNT));
                tagTmp.put(Tag.TAG_DESCRIPTION, tag.optString(Tag.TAG_DESCRIPTION));
                tagTmp.put(Tag.TAG_ICON_PATH, tag.optString(Tag.TAG_ICON_PATH));
                tagTmp.put(Tag.TAG_GOOD_CNT, tag.optInt(Tag.TAG_GOOD_CNT));
                tagTmp.put(Tag.TAG_BAD_CNT, tag.optInt(Tag.TAG_BAD_CNT));
                tagTmp.put(Tag.TAG_SEO_DESC, tag.optString(Tag.TAG_SEO_DESC));
                tagTmp.put(Tag.TAG_SEO_KEYWORDS, tag.optString(Tag.TAG_SEO_KEYWORDS));
                tagTmp.put(Tag.TAG_SEO_TITLE, tag.optString(Tag.TAG_SEO_TITLE));
                tagTmp.put(Tag.TAG_RANDOM_DOUBLE, Math.random());
                tagTmp.put(Tag.TAG_URI, tag.optString(Tag.TAG_URI));
                tagTmp.put(Tag.TAG_CSS, tag.optString(Tag.TAG_CSS));
                tagTmp.put(Tag.TAG_AD, tag.optString(Tag.TAG_AD));
                tagTmp.put(Tag.TAG_SHOW_SIDE_AD, tag.optInt(Tag.TAG_SHOW_SIDE_AD));

                tagRepository.update(tagId, tagTmp);

                userTagType = Tag.TAG_TYPE_C_ARTICLE;
            }

            // Tag-Article relation
            final JSONObject tagArticleRelation = new JSONObject();
            tagArticleRelation.put(Tag.TAG + "_" + Keys.OBJECT_ID, tagId);
            tagArticleRelation.put(Article.ARTICLE + "_" + Keys.OBJECT_ID, article.optString(Keys.OBJECT_ID));
            tagArticleRelation.put(Article.ARTICLE_LATEST_CMT_TIME, article.optLong(Article.ARTICLE_LATEST_CMT_TIME));
            tagArticleRelation.put(Article.ARTICLE_COMMENT_CNT, article.optInt(Article.ARTICLE_COMMENT_CNT));
            tagArticleRelation.put(Article.REDDIT_SCORE, article.optDouble(Article.REDDIT_SCORE, 0D));
            tagArticleRelation.put(Article.ARTICLE_PERFECT, article.optInt(Article.ARTICLE_PERFECT));
            tagArticleRepository.add(tagArticleRelation);

            final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);

            // User-Tag relation
            if (Tag.TAG_TYPE_C_ARTICLE == userTagType) {
                userTagRepository.removeByUserIdAndTagId(authorId, tagId, Tag.TAG_TYPE_C_ARTICLE);
            }

            final JSONObject userTagRelation = new JSONObject();
            userTagRelation.put(Tag.TAG + '_' + Keys.OBJECT_ID, tagId);
            if (Article.ARTICLE_ANONYMOUS_C_ANONYMOUS == article.optInt(Article.ARTICLE_ANONYMOUS)) {
                userTagRelation.put(User.USER + '_' + Keys.OBJECT_ID, "0");
            } else {
                userTagRelation.put(User.USER + '_' + Keys.OBJECT_ID, authorId);
            }
            userTagRelation.put(Common.TYPE, userTagType);
            userTagRepository.add(userTagRelation);
        }

        final String[] tags = articleTags.split(",");
        final StringBuilder builder = new StringBuilder();
        for (final String tagTitle : tags) {
            final JSONObject tag = tagRepository.getByTitle(tagTitle);

            builder.append(tag.optString(Tag.TAG_TITLE)).append(",");
        }
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }

        article.put(Article.ARTICLE_TAGS, builder.toString());
    }

    /**
     * Filters the specified article tags.
     *
     * @param articleTags the specified article tags
     * @return filtered tags string
     */
    public String filterReservedTags(final String articleTags) {
        final String[] tags = articleTags.split(",");

        final StringBuilder retBuilder = new StringBuilder();

        for (final String tag : tags) {
            if (!ArrayUtils.contains(Symphonys.RESERVED_TAGS, tag)) {
                retBuilder.append(tag).append(",");
            }
        }
        if (retBuilder.length() > 0) {
            retBuilder.deleteCharAt(retBuilder.length() - 1);
        }

        return retBuilder.toString();
    }

    /**
     * Adds an article with the specified request json object.
     * <p>
     * <b>Note</b>: This method just for admin console.
     * </p>
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          "articleTitle": "",
     *                          "articleTags": "",
     *                          "articleContent": "",
     *                          "articleRewardContent": "",
     *                          "articleRewardPoint": int,
     *                          "userName": "",
     *                          "time": long
     *                          , see {@link Article} for more details
     * @return generated article id
     * @throws ServiceException service exception
     */
    public synchronized String addArticleByAdmin(final JSONObject requestJSONObject) throws ServiceException {
        JSONObject author;

        try {
            author = userRepository.getByName(requestJSONObject.optString(User.USER_NAME));
            if (null == author) {
                throw new ServiceException(langPropsService.get("notFoundUserLabel"));
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.DEBUG, "Admin adds article failed", e);

            throw new ServiceException(e.getMessage());
        }

        final Transaction transaction = articleRepository.beginTransaction();

        try {
            final long time = requestJSONObject.optLong(Common.TIME);
            final String ret = String.valueOf(time);
            final JSONObject article = new JSONObject();
            article.put(Keys.OBJECT_ID, ret);
            article.put(Article.ARTICLE_AUTHOR_ID, author.optString(Keys.OBJECT_ID));
            article.put(Article.ARTICLE_TITLE, Emotions.toAliases(requestJSONObject.optString(Article.ARTICLE_TITLE)));
            article.put(Article.ARTICLE_CONTENT, Emotions.toAliases(requestJSONObject.optString(Article.ARTICLE_CONTENT)));
            article.put(Article.ARTICLE_REWARD_CONTENT, requestJSONObject.optString(Article.ARTICLE_REWARD_CONTENT));
            article.put(Article.ARTICLE_EDITOR_TYPE, 0);
            article.put(Article.ARTICLE_COMMENT_CNT, 0);
            article.put(Article.ARTICLE_VIEW_CNT, 0);
            article.put(Article.ARTICLE_THANK_CNT, 0);
            article.put(Article.ARTICLE_GOOD_CNT, 0);
            article.put(Article.ARTICLE_BAD_CNT, 0);
            article.put(Article.ARTICLE_COLLECT_CNT, 0);
            article.put(Article.ARTICLE_WATCH_CNT, 0);
            article.put(Article.ARTICLE_COMMENTABLE, true);
            article.put(Article.ARTICLE_STATEMENT, 0);
            article.put(Article.ARTICLE_CREATE_TIME, time);
            article.put(Article.ARTICLE_UPDATE_TIME, time);
            article.put(Article.ARTICLE_LATEST_CMT_TIME, ret);
            article.put(Article.ARTICLE_LATEST_CMTER_NAME, "");
            article.put(Article.ARTICLE_PERMALINK, "/article/" + ret);
            article.put(Article.ARTICLE_RANDOM_DOUBLE, Math.random());
            article.put(Article.REDDIT_SCORE, 0);
            article.put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_VALID);
            article.put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
            article.put(Article.ARTICLE_REWARD_POINT, requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT));
            article.put(Article.ARTICLE_QNA_OFFER_POINT, 0);
            article.put(Article.ARTICLE_PUSH_ORDER, 0);
            article.put(Article.ARTICLE_IMG1_URL, "");
            article.put(Article.ARTICLE_CITY, "");
            String articleTags = requestJSONObject.optString(Article.ARTICLE_TAGS);
            articleTags = Tag.formatTags(articleTags);
            if (StringUtils.containsIgnoreCase(articleTags, Tag.TAG_TITLE_C_SANDBOX)) {
                articleTags = Tag.TAG_TITLE_C_SANDBOX;
            }
            if (StringUtils.isBlank(articleTags)) {
                articleTags = "待分类";
            }

            articleTags = Tag.formatTags(articleTags);
            article.put(Article.ARTICLE_TAGS, articleTags);
            String[] tagTitles = articleTags.split(",");

            tag(tagTitles, article, author);

            final String ip = requestJSONObject.optString(Article.ARTICLE_IP);
            article.put(Article.ARTICLE_IP, ip);

            String ua = requestJSONObject.optString(Article.ARTICLE_UA);
            if (StringUtils.length(ua) > Common.MAX_LENGTH_UA) {
                ua = StringUtils.substring(ua, 0, Common.MAX_LENGTH_UA);
            }
            article.put(Article.ARTICLE_UA, ua);

            article.put(Article.ARTICLE_STICK, 0L);
            article.put(Article.ARTICLE_ANONYMOUS, Article.ARTICLE_ANONYMOUS_C_PUBLIC);
            article.put(Article.ARTICLE_PERFECT, Article.ARTICLE_PERFECT_C_NOT_PERFECT);
            article.put(Article.ARTICLE_GOOD_POST, "");
            article.put(Article.ARTICLE_ANONYMOUS_VIEW, Article.ARTICLE_ANONYMOUS_VIEW_C_USE_GLOBAL);
            article.put(Article.ARTICLE_AUDIO_URL, "");
            article.put(Article.ARTICLE_SHOW_IN_LIST, requestJSONObject.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES));
            final JSONObject articleCntOption = optionRepository.get(Option.ID_C_STATISTIC_ARTICLE_COUNT);
            final int articleCnt = articleCntOption.optInt(Option.OPTION_VALUE);
            articleCntOption.put(Option.OPTION_VALUE, articleCnt + 1);
            optionRepository.update(Option.ID_C_STATISTIC_ARTICLE_COUNT, articleCntOption);

            author.put(UserExt.USER_ARTICLE_COUNT, author.optInt(UserExt.USER_ARTICLE_COUNT) + 1);
            author.put(UserExt.USER_LATEST_ARTICLE_TIME, time);
            // Updates user article count (and new tag count), latest article time
            userRepository.update(author.optString(Keys.OBJECT_ID), author);

            articleRepository.add(article);
            professionSourceEventCaptureService.articlePublished(article);

            transaction.commit();

            // Grows the tag graph
            tagMgmtService.relateTags(article.optString(Article.ARTICLE_TAGS));

            final JSONObject eventData = new JSONObject();
            eventData.put(Article.ARTICLE, article);
            eventManager.fireEventAsynchronously(new Event<>(EventTypes.ADD_ARTICLE, eventData));

            return ret;
        } catch (final RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Admin adds an article failed", e);
            throw new ServiceException(e.getMessage());
        }
    }
}
