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
package org.b3log.symphony.processor.middleware.validate;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.bot.ChatRoomBot;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.b3log.symphony.service.LogsService;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.service.TagQueryService;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Validates for article adding locally.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 0.2.0
 */
@Singleton
public class ArticlePostValidationMidware {

    /**
     * Max article title length.
     */
    public static final int MAX_ARTICLE_TITLE_LENGTH = 255;

    /**
     * Max article content length.
     */
    public static final int MAX_ARTICLE_CONTENT_LENGTH = 1024000;

    /**
     * Min article content length.
     */
    public static final int MIN_ARTICLE_CONTENT_LENGTH = 4;

    /**
     * Max article reward content length.
     */
    public static final int MAX_ARTICLE_REWARD_CONTENT_LENGTH = 102400;

    /**
     * Min article reward content length.
     */
    public static final int MIN_ARTICLE_REWARD_CONTENT_LENGTH = 4;

    final private static SimpleCurrentLimiter addArticleLimiter = new SimpleCurrentLimiter(60 * 30, 10);

    public void handle(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        final BeanManager beanManager = BeanManager.getInstance();
        final LangPropsService langPropsService = beanManager.getReference(LangPropsService.class);
        final TagQueryService tagQueryService = beanManager.getReference(TagQueryService.class);
        final OptionQueryService optionQueryService = beanManager.getReference(OptionQueryService.class);

        final JSONObject exception = new JSONObject();
        exception.put(Keys.CODE, StatusCodes.ERR);

        requestJSONObject.put(Article.ARTICLE_TITLE, ReservedWords.processReservedWord(requestJSONObject.optString(Article.ARTICLE_TITLE)));
        String articleTitle = requestJSONObject.optString(Article.ARTICLE_TITLE);
        articleTitle = StringUtils.trim(articleTitle);
        articleTitle = Emotions.clear(articleTitle);
        if (StringUtils.isBlank(articleTitle) || articleTitle.length() > MAX_ARTICLE_TITLE_LENGTH) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("articleTitleErrorLabel")));
            context.abort();
            return;
        }

        requestJSONObject.put(Article.ARTICLE_TITLE, articleTitle);

        final int articleType = requestJSONObject.optInt(Article.ARTICLE_TYPE);
        if (Article.isInvalidArticleType(articleType)) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("articleTypeErrorLabel")));
            context.abort();
            return;
        }

        requestJSONObject.put(Article.ARTICLE_TAGS, ReservedWords.processReservedWord(requestJSONObject.optString(Article.ARTICLE_TAGS)));
        String articleTags = requestJSONObject.optString(Article.ARTICLE_TAGS);
        articleTags = Tag.formatTags(articleTags);

        if (StringUtils.isBlank(articleTags)) {
            // 发帖时标签改为非必填 https://github.com/b3log/symphony/issues/811
            articleTags = "待分类";
        }

        if (StringUtils.isBlank(articleTags)) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("tagsEmptyErrorLabel")));
            context.abort();
            return;
        }

        if (StringUtils.isNotBlank(articleTags)) {
            String[] tagTitles = articleTags.split(",");

            tagTitles = new LinkedHashSet<>(Arrays.asList(tagTitles)).toArray(new String[0]);
            final List<String> invalidTags = tagQueryService.getInvalidTags();

            final StringBuilder tagBuilder = new StringBuilder();
            for (int i = 0; i < tagTitles.length; i++) {
                final String tagTitle = tagTitles[i].trim();

                if (StringUtils.isBlank(tagTitle)) {
                    context.renderJSON(exception.put(Keys.MSG, langPropsService.get("tagsErrorLabel")));
                    context.abort();
                    return;
                }

                if (!Tag.containsWhiteListTags(tagTitle)) {
                    if (!Tag.TAG_TITLE_PATTERN.matcher(tagTitle).matches()) {
                        context.renderJSON(exception.put(Keys.MSG, langPropsService.get("tagsErrorLabel")));
                        context.abort();
                        return;
                    }

                    if (tagTitle.length() > Tag.MAX_TAG_TITLE_LENGTH) {
                        context.renderJSON(exception.put(Keys.MSG, langPropsService.get("tagsErrorLabel")));
                        context.abort();
                        return;
                    }
                }

                JSONObject currentUser = Sessions.getUser();
                try {
                    currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
                } catch (NullPointerException ignored) {
                }
                try {
                    currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
                } catch (NullPointerException ignored) {
                }
                if ((
                        !Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE)) && !"1630552921050".equals(currentUser.optString(User.USER_ROLE))
                )
                        && ArrayUtils.contains(Symphonys.RESERVED_TAGS, tagTitle)) {
                    context.renderJSON(exception.put(Keys.MSG, langPropsService.get("articleTagReservedLabel") + " [" + tagTitle + "]"));
                    context.abort();
                    return;
                }

                if (invalidTags.contains(tagTitle)) {
                    continue;
                }

                tagBuilder.append(tagTitle).append(",");
            }
            if (tagBuilder.length() > 0) {
                tagBuilder.deleteCharAt(tagBuilder.length() - 1);
            }
            requestJSONObject.put(Article.ARTICLE_TAGS, tagBuilder.toString());
        }

        requestJSONObject.put(Article.ARTICLE_CONTENT, ReservedWords.processReservedWord(requestJSONObject.optString(Article.ARTICLE_CONTENT)));
        String articleContent = requestJSONObject.optString(Article.ARTICLE_CONTENT);
        articleContent = StringUtils.trim(articleContent);
        if (StringUtils.isBlank(articleContent) || articleContent.length() > MAX_ARTICLE_CONTENT_LENGTH
                || articleContent.length() < MIN_ARTICLE_CONTENT_LENGTH) {
            String msg = langPropsService.get("articleContentErrorLabel");
            msg = msg.replace("{maxArticleContentLength}", String.valueOf(MAX_ARTICLE_CONTENT_LENGTH));

            context.renderJSON(exception.put(Keys.MSG, msg));
            context.abort();
            return;
        }

        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == currentUser) {
            context.sendError(401);
            context.abort();
            return;
        }

        // 频率检测
        if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE)) && !"1630552921050".equals(currentUser.optString(User.USER_ROLE))) {
            if (!addArticleLimiter.access(currentUser.optString(Keys.OBJECT_ID))) {
                context.renderJSON(exception.put(Keys.MSG, "操作过于频繁，请稍候重试。"));
                context.abort();
                return;
            }
        }

        final int rewardPoint = requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT, 0);
        if (rewardPoint < 0) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("invalidRewardPointLabel")));
            context.abort();
            return;
        }

        final int articleQnAOfferPoint = requestJSONObject.optInt(Article.ARTICLE_QNA_OFFER_POINT, 0);
        if (articleQnAOfferPoint < 0) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("invalidQnAOfferPointLabel")));
            context.abort();
            return;
        }

        final String articleRewardContnt = requestJSONObject.optString(Article.ARTICLE_REWARD_CONTENT);
        if (StringUtils.isNotBlank(articleRewardContnt) && 1 > rewardPoint) {
            context.renderJSON(exception.put(Keys.MSG, langPropsService.get("invalidRewardPointLabel")));
            context.abort();
            return;
        }

        if (rewardPoint > 0) {
            if (articleRewardContnt.length() > MAX_ARTICLE_CONTENT_LENGTH || StringUtils.length(articleRewardContnt) < 4) {
                String msg = langPropsService.get("articleRewardContentErrorLabel");
                msg = msg.replace("{maxArticleRewardContentLength}", String.valueOf(MAX_ARTICLE_REWARD_CONTENT_LENGTH));

                context.renderJSON(exception.put(Keys.MSG, msg));
                context.abort();
                return;
            }
        }

        // 敏感词检测
        CensorResult titleCensorResult = CensorFactory.getTextCensor().censor(articleTitle + " 标签：" + articleTags);
        CensorResult articleCensorResult = CensorFactory.getTextCensor().censor(articleContent);
        // 组合标题和文章的bannedWords数组（如果标题或文章没有则不显示额外字符），组成用于显示的字符串
        String titleBannedWords = "标题和标签" + titleCensorResult.showBannedWords() + "；";
        String articleBannedWords = "内容" + articleCensorResult.showBannedWords();
        String bannedWords = titleBannedWords + articleBannedWords;
        if (titleCensorResult.isBlocked() || articleCensorResult.isBlocked()) {
            // 违规内容，不予显示
            context.renderJSON(exception.put(Keys.MSG, "您的文章经过AI审核存在违规内容，请修改内容后重试。" + bannedWords));
            // 记录日志
            LogsService.censorLog(context, currentUser.optString(Keys.OBJECT_ID), "用户：" + currentUser.optString(User.USER_NAME) + " 违规上传文章：" + articleTitle + " 内容：" + articleContent + " 标题违规判定：" + titleCensorResult + " 内容违规判定：" + articleCensorResult);
            System.out.println("用户：" + currentUser.optString(User.USER_NAME) + " 违规上传文章：" + articleTitle + " 内容：" + articleContent + " 标题违规判定：" + titleCensorResult + " 内容违规判定：" + articleCensorResult);
            context.abort();
            return;
        }

        context.handle();
    }
}
