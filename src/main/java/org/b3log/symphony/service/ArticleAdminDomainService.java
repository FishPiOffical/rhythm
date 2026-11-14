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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.event.Event;
import org.b3log.latke.event.EventManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管理员文章领域服务：封装严格单事务边界与后置副作用，供后台管理接口复用，避免污染通用 {@link ArticleMgmtService}。
 *
 * @since 2026
 */
@Service
public class ArticleAdminDomainService {

    /**
     * 日志记录器。
     */
    private static final Logger LOGGER = LogManager.getLogger(ArticleAdminDomainService.class);

    @Inject
    private ArticleRepository articleRepository;

    @Inject
    private ArticleMgmtService articleMgmtService;

    @Inject
    private UserRepository userRepository;

    @Inject
    private OptionRepository optionRepository;

    @Inject
    private TagRepository tagRepository;

    @Inject
    private TagArticleRepository tagArticleRepository;

    @Inject
    private UserTagRepository userTagRepository;

    @Inject
    private LangPropsService langPropsService;

    @Inject
    private TagMgmtService tagMgmtService;

    @Inject
    private EventManager eventManager;

    @Inject
    private LongArticleColumnMgmtService longArticleColumnMgmtService;

    @Inject
    private LongArticleParagraphService longArticleParagraphService;

    @Inject
    private ProfessionSourceEventCaptureService professionSourceEventCaptureService;

    @Inject
    private NotificationMgmtService notificationMgmtService;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    @Inject
    private SearchMgmtService searchMgmtService;

    /**
     * 在当前外部事务与缓存作用域内以管理员身份新建文章（不在此提交事务）。
     *
     * @param requestJSONObject 请求参数对象（需包含 userName、articleTitle、articleTags、articleContent 等）
     * @return 新建后的文章数据快照
     * @throws ServiceException 业务或持久化异常
     */
    public synchronized JSONObject addArticleByAdminInCurrentTransaction(final JSONObject requestJSONObject) throws ServiceException {
        if (!articleRepository.hasTransactionBegun() || !ArticlePublicationCaches.active()) {
            throw new IllegalStateException("管理员新建文章必须在活动事务与发布缓存边界内执行");
        }
        try {
            if (null == optionRepository.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) {
                throw new IllegalStateException("缺少全站文章统计配置项");
            }
            final String userName = StringUtils.trimToEmpty(requestJSONObject.optString(User.USER_NAME));
            final List<JSONObject> authorIds = userRepository.select(
                    "SELECT oId FROM `" + userRepository.getName() + "` WHERE userName=? LIMIT 1", userName);
            if (authorIds.isEmpty()) {
                throw new ServiceException(langPropsService.get("notFoundUserLabel"));
            }
            final String authorId = authorIds.getFirst().optString(Keys.OBJECT_ID);
            final JSONObject author = userRepository.getForUpdate(authorId);
            if (null == author) {
                throw new ServiceException(langPropsService.get("notFoundUserLabel"));
            }

            long time = requestJSONObject.optLong(Common.TIME, 0L);
            if (time <= 0L) {
                time = System.currentTimeMillis();
            }
            final String articleId = Ids.genTimeMillisId();
            final JSONObject article = new JSONObject();
            article.put(Keys.OBJECT_ID, articleId);
            article.put(Article.ARTICLE_AUTHOR_ID, authorId);
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
            article.put(Article.ARTICLE_COMMENTABLE, requestJSONObject.optBoolean(Article.ARTICLE_COMMENTABLE, true));
            article.put(Article.ARTICLE_STATEMENT, requestJSONObject.optInt(Article.ARTICLE_STATEMENT, 0));
            article.put(Article.ARTICLE_CREATE_TIME, time);
            article.put(Article.ARTICLE_UPDATE_TIME, time);
            article.put(Article.ARTICLE_LATEST_CMT_TIME, Long.parseLong(articleId));
            article.put(Article.ARTICLE_LATEST_CMTER_NAME, "");
            article.put(Article.ARTICLE_PERMALINK, "/article/" + articleId);
            article.put(Article.ARTICLE_RANDOM_DOUBLE, Math.random());
            article.put(Article.REDDIT_SCORE, 0);
            article.put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_VALID);
            final int articleType = requestJSONObject.optInt(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
            article.put(Article.ARTICLE_TYPE, articleType);
            article.put(Article.ARTICLE_REWARD_POINT, Math.max(0, requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT, 0)));
            article.put(Article.ARTICLE_QNA_OFFER_POINT, Math.max(0, requestJSONObject.optInt(Article.ARTICLE_QNA_OFFER_POINT, 0)));
            article.put(Article.ARTICLE_PUSH_ORDER, 0);
            article.put(Article.ARTICLE_IMG1_URL, "");
            article.put(Article.ARTICLE_CITY, "");

            String articleTags = Tag.formatTags(requestJSONObject.optString(Article.ARTICLE_TAGS));
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
            tag(articleTags.split(","), article, author);

            article.put(Article.ARTICLE_IP, requestJSONObject.optString(Article.ARTICLE_IP));
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
            article.put(Article.ARTICLE_SHOW_IN_LIST,
                    requestJSONObject.optInt(Article.ARTICLE_SHOW_IN_LIST, Article.ARTICLE_SHOW_IN_LIST_C_YES));

            final JSONObject articleCntOption = optionRepository.get(Option.ID_C_STATISTIC_ARTICLE_COUNT);
            articleCntOption.put(Option.OPTION_VALUE, articleCntOption.optInt(Option.OPTION_VALUE) + 1);
            optionRepository.update(Option.ID_C_STATISTIC_ARTICLE_COUNT, articleCntOption);

            author.put(UserExt.USER_ARTICLE_COUNT, author.optInt(UserExt.USER_ARTICLE_COUNT) + 1);
            author.put(UserExt.USER_LATEST_ARTICLE_TIME, time);
            userRepository.update(authorId, author,
                    UserExt.USER_ARTICLE_COUNT, UserExt.USER_LATEST_ARTICLE_TIME, UserExt.USER_TAG_COUNT);

            articleRepository.add(article);
            longArticleColumnMgmtService.syncChapterInCurrentTransaction(article, requestJSONObject);
            professionSourceEventCaptureService.articlePublished(article);
            return new JSONObject(article.toString());
        } catch (final ServiceException e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "当前事务内管理员新建文章失败", e);
            throw new ServiceException(e);
        }
    }

    /**
     * 管理员新建文章事务确认提交后的异步扩展副作用（标签图谱与搜索索引事件）。
     *
     * @param article 已提交的文章数据快照
     */
    public void afterAdminArticleAdded(final JSONObject article) {
        if (articleRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) {
            throw new IllegalStateException("事务尚未结束前禁止触发发布后置操作");
        }
        if (null == article) {
            return;
        }
        try {
            tagMgmtService.relateTags(article.optString(Article.ARTICLE_TAGS));
        } catch (final Exception e) {
            LOGGER.error("管理员新建文章更新标签关联失败", e);
        }
        final JSONObject eventData = new JSONObject();
        eventData.put(Article.ARTICLE, article);
        try {
            eventManager.fireEventAsynchronously(new Event<>(EventTypes.ADD_ARTICLE, eventData));
        } catch (final Exception e) {
            LOGGER.error("管理员新建文章发送异步事件失败", e);
        }
    }

    /**
     * 在当前外部事务与缓存作用域内以管理员身份编辑文章并同步长文专栏与标签计数。
     *
     * @param articleId                目标文章 ID
     * @param patch                    本次需更新的文章字段集合
     * @param longArticleColumnRequest 长文专栏章节与封面请求参数，无需同步时传 {@code null}
     * @return 更新结果，包含 {@code article}、{@code authorId} 与 {@code newlySetPerfect}
     * @throws ServiceException 业务或持久化异常
     */
    public synchronized JSONObject updateArticleByAdminInCurrentTransaction(final String articleId,
                                                                            final JSONObject patch,
                                                                            final JSONObject longArticleColumnRequest) throws ServiceException {
        if (!articleRepository.hasTransactionBegun() || !ArticlePublicationCaches.active()) {
            throw new IllegalStateException("管理员编辑文章必须在活动事务与发布缓存边界内执行");
        }
        try {
            if (null == optionRepository.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) {
                throw new IllegalStateException("缺少全站文章统计配置项");
            }
            final var ownerRows = articleRepository.select(
                    "SELECT articleAuthorId FROM `" + articleRepository.getName() + "` WHERE oId=?", articleId);
            if (ownerRows.isEmpty()) {
                throw new ServiceException("文章不存在");
            }
            final String authorId = ownerRows.getFirst().optString(Article.ARTICLE_AUTHOR_ID);
            final JSONObject author = userRepository.getForUpdate(authorId);
            if (null == author) {
                throw new ServiceException("文章作者账号不存在");
            }
            final JSONObject lockedArticle = articleRepository.getForUpdate(articleId);
            if (null == lockedArticle) {
                throw new ServiceException("文章不存在");
            }

            final JSONObject oldArticle = JSONs.clone(lockedArticle);
            final int oldArticleType = oldArticle.optInt(Article.ARTICLE_TYPE);
            final String oldContent = oldArticle.optString(Article.ARTICLE_CONTENT);

            final JSONObject nextArticle = JSONs.clone(lockedArticle);
            for (final String key : patch.keySet()) {
                nextArticle.put(key, patch.opt(key));
            }
            nextArticle.put(Article.ARTICLE_COMMENTABLE, nextArticle.optBoolean(Article.ARTICLE_COMMENTABLE));
            nextArticle.put(Article.ARTICLE_STATEMENT, nextArticle.optInt(Article.ARTICLE_STATEMENT, 0));
            nextArticle.put(Article.ARTICLE_UPDATE_TIME, System.currentTimeMillis());

            if (Article.ARTICLE_STATUS_C_INVALID == nextArticle.optInt(Article.ARTICLE_STATUS)) {
                nextArticle.put(Article.ARTICLE_TAGS, "回收站");
            }

            processTagsForArticleUpdate(oldArticle, nextArticle, author);

            final String articleTitle = Emotions.toAliases(nextArticle.optString(Article.ARTICLE_TITLE));
            nextArticle.put(Article.ARTICLE_TITLE, articleTitle);

            final int nextType = nextArticle.optInt(Article.ARTICLE_TYPE);
            if (Article.ARTICLE_TYPE_C_THOUGHT == nextType) {
                nextArticle.put(Article.ARTICLE_CONTENT, oldContent);
            } else {
                String articleContent = Emotions.toAliases(nextArticle.optString(Article.ARTICLE_CONTENT));
                articleContent = Runes.removeControlChars(articleContent);
                nextArticle.put(Article.ARTICLE_CONTENT, articleContent);
            }

            final int perfect = nextArticle.optInt(Article.ARTICLE_PERFECT);
            if (Article.ARTICLE_PERFECT_C_PERFECT == perfect) {
                // 优选帖默认允许未登录游客浏览，并同步标签关联表优选状态
                nextArticle.put(Article.ARTICLE_ANONYMOUS_VIEW, Article.ARTICLE_ANONYMOUS_VIEW_C_ALLOW);
                final List<JSONObject> tagArticleRels = tagArticleRepository.getByArticleId(articleId);
                for (final JSONObject tagArticleRel : tagArticleRels) {
                    tagArticleRel.put(Article.ARTICLE_PERFECT, Article.ARTICLE_PERFECT_C_PERFECT);
                    tagArticleRepository.update(tagArticleRel.optString(Keys.OBJECT_ID), tagArticleRel);
                }
            }

            nextArticle.put(Article.ARTICLE_AUDIO_URL, "");

            userRepository.update(authorId, author, UserExt.USER_TAG_COUNT);
            articleRepository.update(articleId, nextArticle);

            final String finalContent = nextArticle.optString(Article.ARTICLE_CONTENT);
            final boolean paragraphStructureChanged = !StringUtils.equals(oldContent, finalContent);
            if (paragraphStructureChanged
                    && (Article.ARTICLE_TYPE_C_LONG == nextType || Article.ARTICLE_TYPE_C_LONG == oldArticleType)) {
                longArticleParagraphService.migrateCommentsInCurrentTransaction(articleId, oldContent, finalContent);
            }

            if (null != longArticleColumnRequest) {
                nextArticle.put(Keys.OBJECT_ID, articleId);
                longArticleColumnMgmtService.syncChapterInCurrentTransaction(nextArticle, longArticleColumnRequest);
                articleMgmtService.updateColumnCoverByAdminInCurrentTransaction(nextArticle, longArticleColumnRequest);
            }

            final boolean newlySetPerfect = Article.ARTICLE_PERFECT_C_NOT_PERFECT == oldArticle.optInt(Article.ARTICLE_PERFECT)
                    && Article.ARTICLE_PERFECT_C_PERFECT == perfect;
            return new JSONObject()
                    .put("article", JSONs.clone(nextArticle))
                    .put("authorId", authorId)
                    .put("newlySetPerfect", newlySetPerfect);
        } catch (final ServiceException e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "当前事务内管理员更新文章 [id={}] 失败", articleId, e);
            throw new ServiceException(e);
        }
    }

    /**
     * 管理员编辑文章事务确认提交后的副作用（新设优选通知与积分发放、搜索引擎同步）。
     *
     * @param updateOutcome {@link #updateArticleByAdminInCurrentTransaction} 返回的结果对象
     */
    public void afterAdminArticleUpdated(final JSONObject updateOutcome) {
        if (articleRepository.hasTransactionBegun() || ArticlePublicationCaches.active()) {
            throw new IllegalStateException("事务尚未结束前禁止触发更新后置操作");
        }
        if (null == updateOutcome) {
            return;
        }
        final JSONObject article = updateOutcome.optJSONObject("article");
        if (null == article) {
            return;
        }
        final String articleId = article.optString(Keys.OBJECT_ID);
        final String authorId = updateOutcome.optString("authorId");
        final boolean newlySetPerfect = updateOutcome.optBoolean("newlySetPerfect");

        if (newlySetPerfect && StringUtils.isNotBlank(authorId)) {
            try {
                final JSONObject notification = new JSONObject();
                notification.put(Notification.NOTIFICATION_USER_ID, authorId);
                notification.put(Notification.NOTIFICATION_DATA_ID, articleId);
                notificationMgmtService.addPerfectArticleNotification(notification);
                pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, authorId,
                        Pointtransfer.TRANSFER_TYPE_C_PERFECT_ARTICLE, Pointtransfer.TRANSFER_SUM_C_PERFECT_ARTICLE,
                        articleId, System.currentTimeMillis(), "");
            } catch (final Exception e) {
                LOGGER.error("发放优选文章通知或积分失败, articleId={}", articleId, e);
            }
        }

        final int articleType = article.optInt(Article.ARTICLE_TYPE);
        if (Article.ARTICLE_TYPE_C_DISCUSSION == articleType || Article.ARTICLE_TYPE_C_THOUGHT == articleType) {
            return;
        }
        if (Symphonys.ALGOLIA_ENABLED) {
            try {
                searchMgmtService.updateAlgoliaDocument(JSONs.clone(article));
            } catch (final Exception e) {
                LOGGER.error("更新 Algolia 文章索引失败, articleId={}", articleId, e);
            }
        }
        if (Symphonys.ES_ENABLED) {
            try {
                searchMgmtService.updateESDocument(article, Article.ARTICLE);
            } catch (final Exception e) {
                LOGGER.error("更新 Elasticsearch 文章索引失败, articleId={}", articleId, e);
            }
        }
    }

    /**
     * 在当前外部事务内设置或取消文章置顶时间戳。
     *
     * @param articleId 目标文章 ID
     * @param authorId  文章作者 ID（可为空）
     * @param stick     {@code true} 表示置顶，{@code false} 表示取消置顶
     * @return 更新后的置顶时间戳；若文章不存在则返回 {@code null}
     * @throws RepositoryException 持久化异常
     */
    public Long setArticleStickInCurrentTransaction(final String articleId,
                                                    final String authorId,
                                                    final boolean stick) throws RepositoryException {
        if (!articleRepository.hasTransactionBegun()) {
            throw new IllegalStateException("设置文章置顶必须在活动事务边界内执行");
        }
        if (StringUtils.isNotBlank(authorId)) {
            userRepository.getForUpdate(authorId);
        }
        final JSONObject lockedArticle = articleRepository.getForUpdate(articleId);
        if (null == lockedArticle) {
            return null;
        }
        final long stickTime = stick ? System.currentTimeMillis() : 0L;
        lockedArticle.put(Article.ARTICLE_STICK, stickTime);
        articleRepository.update(articleId, lockedArticle);
        return stickTime;
    }

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
                tagsNeedToAdd.add(newTag);
            }
        }
        for (final JSONObject oldTag : oldTags) {
            final String oldTagTitle = oldTag.getString(Tag.TAG_TITLE);
            if (!tagExists(oldTagTitle, newTags)) {
                tagsDropped.add(oldTag);
            }
        }

        final int articleCmtCnt = oldArticle.getInt(Article.ARTICLE_COMMENT_CNT);
        for (final JSONObject tagDropped : tagsDropped) {
            final String tagId = tagDropped.getString(Keys.OBJECT_ID);
            final int refCnt = Math.max(0, tagDropped.getInt(Tag.TAG_REFERENCE_CNT) - 1);
            tagDropped.put(Tag.TAG_REFERENCE_CNT, refCnt);
            final int tagCmtCnt = tagDropped.getInt(Tag.TAG_COMMENT_CNT);
            tagDropped.put(Tag.TAG_COMMENT_CNT, tagCmtCnt - articleCmtCnt);
            tagDropped.put(Tag.TAG_RANDOM_DOUBLE, Math.random());
            tagRepository.update(tagId, tagDropped);
        }

        final String[] tagIdsDropped = new String[tagsDropped.size()];
        for (int i = 0; i < tagIdsDropped.length; i++) {
            tagIdsDropped[i] = tagsDropped.get(i).getString(Keys.OBJECT_ID);
        }
        if (0 != tagIdsDropped.length) {
            removeTagArticleRelations(oldArticleId, tagIdsDropped);
            removeUserTagRelations(oldArticle.optString(Article.ARTICLE_AUTHOR_ID), Tag.TAG_TYPE_C_ARTICLE, tagIdsDropped);
        }

        tagStrings = new String[tagsNeedToAdd.size()];
        for (int i = 0; i < tagStrings.length; i++) {
            tagStrings[i] = tagsNeedToAdd.get(i).getString(Tag.TAG_TITLE);
        }

        newArticle.put(Article.ARTICLE_COMMENT_CNT, articleCmtCnt);
        tag(tagStrings, newArticle, author);
    }

    private void removeTagArticleRelations(final String articleId, final String... tagIds)
            throws JSONException, RepositoryException {
        final List<String> tagIdList = Arrays.asList(tagIds);
        final List<JSONObject> tagArticleRelations = tagArticleRepository.getByArticleId(articleId);
        for (final JSONObject tagArticleRelation : tagArticleRelations) {
            final String relationId = tagArticleRelation.getString(Keys.OBJECT_ID);
            if (tagIdList.isEmpty() || tagIdList.contains(tagArticleRelation.getString(Tag.TAG + "_" + Keys.OBJECT_ID))) {
                tagArticleRepository.remove(relationId);
            }
        }
    }

    private void removeUserTagRelations(final String userId, final int type, final String... tagIds) throws RepositoryException {
        for (final String tagId : tagIds) {
            userTagRepository.removeByUserIdAndTagId(userId, tagId, type);
        }
    }

    private synchronized void tag(final String[] tagTitles, final JSONObject article, final JSONObject author)
            throws RepositoryException {
        final String articleTags = article.optString(Article.ARTICLE_TAGS);
        for (final String t : tagTitles) {
            final String tagTitle = t.trim();
            JSONObject tag = tagRepository.getByTitle(tagTitle);
            final String tagId;
            final int userTagType;
            final int articleCmtCnt = article.optInt(Article.ARTICLE_COMMENT_CNT);
            if (null == tag) {
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
                tagCntOption.put(Option.OPTION_VALUE, tagCntOption.optInt(Option.OPTION_VALUE) + 1);
                optionRepository.update(Option.ID_C_STATISTIC_TAG_COUNT, tagCntOption);

                author.put(UserExt.USER_TAG_COUNT, author.optInt(UserExt.USER_TAG_COUNT) + 1);
            } else {
                tagId = tag.optString(Keys.OBJECT_ID);
                final JSONObject tagTmp = new JSONObject();
                tagTmp.put(Keys.OBJECT_ID, tagId);
                tagTmp.put(Tag.TAG_TITLE, tag.optString(Tag.TAG_TITLE));
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

            final JSONObject tagArticleRelation = new JSONObject();
            tagArticleRelation.put(Tag.TAG + "_" + Keys.OBJECT_ID, tagId);
            tagArticleRelation.put(Article.ARTICLE + "_" + Keys.OBJECT_ID, article.optString(Keys.OBJECT_ID));
            tagArticleRelation.put(Article.ARTICLE_LATEST_CMT_TIME, article.optLong(Article.ARTICLE_LATEST_CMT_TIME));
            tagArticleRelation.put(Article.ARTICLE_COMMENT_CNT, article.optInt(Article.ARTICLE_COMMENT_CNT));
            tagArticleRelation.put(Article.REDDIT_SCORE, article.optDouble(Article.REDDIT_SCORE, 0D));
            tagArticleRelation.put(Article.ARTICLE_PERFECT, article.optInt(Article.ARTICLE_PERFECT));
            tagArticleRepository.add(tagArticleRelation);

            final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);
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

    private static boolean tagExists(final String tagTitle, final List<JSONObject> tags) throws JSONException {
        for (final JSONObject tag : tags) {
            if (tag.getString(Tag.TAG_TITLE).equals(tagTitle)) {
                return true;
            }
        }
        return false;
    }
}
