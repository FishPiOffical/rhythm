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
package org.b3log.symphony.util;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.service.ColumnCoverMgmtService;
import org.b3log.symphony.censor.CensorResult;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/** Shared legacy/v3 article validator. Infrastructure adapters supply the existing business rules. */
public final class ArticleValidation {
    public static final int MAX_ARTICLE_TITLE_LENGTH = 255;
    public static final int MAX_ARTICLE_CONTENT_LENGTH = 1024000;
    public static final int MIN_ARTICLE_CONTENT_LENGTH = 4;
    public static final int MAX_ARTICLE_REWARD_CONTENT_LENGTH = 102400;
    public static final int MIN_ARTICLE_REWARD_CONTENT_LENGTH = 4;
    private ArticleValidation() { }
    public interface Rules {
        String label(String key);
        String reservedWords(String text);
        String clearEmotions(String text);
        String formatTags(String text);
        List<String> invalidTags();
        boolean whiteListedTag(String tag);
        boolean validTag(String tag);
        int maxTagLength();
        boolean reservedTag(String tag);
        boolean allowWrite(String actor);
        CensorResult censor(String text);
    }
    public static final class Rejected extends RuntimeException {
        public final int status;
        public final String code;
        public Rejected(final int status, final String code, final String message) { super(message); this.status = status; this.code = code; }
    }
    private static Rejected invalid(final String message) { return new Rejected(422, "invalid_article", message); }
    public static void validate(final JSONObject requestJSONObject, final JSONObject currentUser, final boolean adding,
                                final Rules rules, final Consumer<String> rejectedLog) {
        final boolean bypassCensor = requestJSONObject.optBoolean(Article.ARTICLE_BYPASS_CENSOR, false);
        if (bypassCensor && !Role.canBypassArticleCensor(currentUser.optString(User.USER_ROLE))) {
            throw new Rejected(403, "forbidden", "无权使用免审发帖。");
        }

        if (!bypassCensor) {
            requestJSONObject.put(Article.ARTICLE_TITLE,
                    rules.reservedWords(requestJSONObject.optString(Article.ARTICLE_TITLE)));
        }
        String articleTitle = requestJSONObject.optString(Article.ARTICLE_TITLE);
        articleTitle = StringUtils.trim(articleTitle);
        articleTitle = rules.clearEmotions(articleTitle);
        if (StringUtils.isBlank(articleTitle) || articleTitle.length() > MAX_ARTICLE_TITLE_LENGTH) {
            throw invalid(rules.label("articleTitleErrorLabel"));
        }

        requestJSONObject.put(Article.ARTICLE_TITLE, articleTitle);

        final int articleType = requestJSONObject.optInt(Article.ARTICLE_TYPE);
        if (Article.isInvalidArticleType(articleType)) {
            throw invalid(rules.label("articleTypeErrorLabel"));
        }

        if (adding && Article.ARTICLE_TYPE_C_THOUGHT == articleType) {
            throw invalid(rules.label("thoughtArticleDisabledLabel"));
        }

        if (Article.ARTICLE_TYPE_C_LONG == articleType) {
            final String columnId = StringUtils.trim(requestJSONObject.optString(LongArticleColumn.COLUMN_ID));
            final String columnTitle = StringUtils.trim(requestJSONObject.optString(LongArticleColumn.COLUMN_TITLE));
            final String chapterNo = StringUtils.trim(requestJSONObject.optString(LongArticleColumn.CHAPTER_NO));
            final String columnCoverURL;
            try {
                columnCoverURL = ColumnCoverMgmtService.normalizeCoverURL(
                        requestJSONObject.optString(LongArticleColumn.COLUMN_COVER_URL));
            } catch (final Exception e) {
                throw invalid(e.getMessage());
            }
            requestJSONObject.put(LongArticleColumn.COLUMN_ID, columnId);
            requestJSONObject.put(LongArticleColumn.COLUMN_TITLE, columnTitle);
            requestJSONObject.put(LongArticleColumn.CHAPTER_NO, chapterNo);
            requestJSONObject.put(LongArticleColumn.COLUMN_COVER_URL, columnCoverURL);

            if (columnTitle.length() > LongArticleColumn.MAX_COLUMN_TITLE_LENGTH) {
                throw invalid("专栏名称长度不能超过 " + LongArticleColumn.MAX_COLUMN_TITLE_LENGTH + " 个字符");
            }

            if (StringUtils.isNotBlank(chapterNo) && !chapterNo.matches("^[1-9]\\d*$")) {
                throw invalid("章节号必须是正整数");
            }
        }

        if (!bypassCensor) {
            requestJSONObject.put(Article.ARTICLE_TAGS,
                    rules.reservedWords(requestJSONObject.optString(Article.ARTICLE_TAGS)));
        }
        String articleTags = requestJSONObject.optString(Article.ARTICLE_TAGS);
        articleTags = rules.formatTags(articleTags);

        if (StringUtils.isBlank(articleTags)) {
            // 发帖时标签改为非必填 https://github.com/b3log/symphony/issues/811
            articleTags = "待分类";
        }

        if (StringUtils.isBlank(articleTags)) {
            throw invalid(rules.label("tagsEmptyErrorLabel"));
        }

        if (StringUtils.isNotBlank(articleTags)) {
            String[] tagTitles = articleTags.split(",");

            tagTitles = new LinkedHashSet<>(Arrays.asList(tagTitles)).toArray(new String[0]);
            final List<String> invalidTags = rules.invalidTags();

            final StringBuilder tagBuilder = new StringBuilder();
            for (int i = 0; i < tagTitles.length; i++) {
                final String tagTitle = tagTitles[i].trim();

                if (StringUtils.isBlank(tagTitle)) {
                    throw invalid(rules.label("tagsErrorLabel"));
                }

                if (!rules.whiteListedTag(tagTitle)) {
                    if (!rules.validTag(tagTitle)) {
                        throw invalid(rules.label("tagsErrorLabel"));
                    }

                    if (tagTitle.length() > rules.maxTagLength()) {
                        throw invalid(rules.label("tagsErrorLabel"));
                    }
                }

                if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))
                        && !Role.ROLE_ID_C_OP.equals(currentUser.optString(User.USER_ROLE))
                        && rules.reservedTag(tagTitle)) {
                    throw invalid(rules.label("articleTagReservedLabel") + " [" + tagTitle + "]");
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

        if (!bypassCensor) {
            requestJSONObject.put(Article.ARTICLE_CONTENT,
                    rules.reservedWords(requestJSONObject.optString(Article.ARTICLE_CONTENT)));
        }
        String articleContent = requestJSONObject.optString(Article.ARTICLE_CONTENT);
        articleContent = StringUtils.trim(articleContent);
        if (StringUtils.isBlank(articleContent) || articleContent.length() > MAX_ARTICLE_CONTENT_LENGTH
                || articleContent.length() < MIN_ARTICLE_CONTENT_LENGTH) {
            String msg = rules.label("articleContentErrorLabel");
            msg = msg.replace("{maxArticleContentLength}", String.valueOf(MAX_ARTICLE_CONTENT_LENGTH));

            throw invalid(msg);
        }

        // 频率检测
        if (!Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))
                && !Role.ROLE_ID_C_OP.equals(currentUser.optString(User.USER_ROLE))) {
            if (!rules.allowWrite(currentUser.optString(Keys.OBJECT_ID))) {
                throw new Rejected(429, "too_many_requests", "操作过于频繁，请稍候重试。");
            }
        }

        final int rewardPoint = requestJSONObject.optInt(Article.ARTICLE_REWARD_POINT, 0);
        if (rewardPoint < 0) {
            throw invalid(rules.label("invalidRewardPointLabel"));
        }

        final int articleQnAOfferPoint = requestJSONObject.optInt(Article.ARTICLE_QNA_OFFER_POINT, 0);
        if (articleQnAOfferPoint < 0) {
            throw invalid(rules.label("invalidQnAOfferPointLabel"));
        }

        final String articleRewardContnt = requestJSONObject.optString(Article.ARTICLE_REWARD_CONTENT);
        if (StringUtils.isNotBlank(articleRewardContnt) && 1 > rewardPoint) {
            throw invalid(rules.label("invalidRewardPointLabel"));
        }

        if (rewardPoint > 0) {
            if (articleRewardContnt.length() > MAX_ARTICLE_CONTENT_LENGTH || StringUtils.length(articleRewardContnt) < 4) {
                String msg = rules.label("articleRewardContentErrorLabel");
                msg = msg.replace("{maxArticleRewardContentLength}", String.valueOf(MAX_ARTICLE_REWARD_CONTENT_LENGTH));

                throw invalid(msg);
            }
        }

        if (!bypassCensor) {
            // 敏感词检测
            CensorResult titleCensorResult = rules.censor(articleTitle + " 标签：" + articleTags);
            CensorResult articleCensorResult = rules.censor(articleContent);
            // 组合标题和文章的bannedWords数组（如果标题或文章没有则不显示额外字符），组成用于显示的字符串
            String titleBannedWords = "标题和标签" + titleCensorResult.showBannedWords() + "；";
            String articleBannedWords = "内容" + articleCensorResult.showBannedWords();
            String bannedWords = titleBannedWords + articleBannedWords;
            if (titleCensorResult.isBlocked() || articleCensorResult.isBlocked()) {
                rejectedLog.accept("用户：" + currentUser.optString(User.USER_NAME) + " 违规上传文章：" + articleTitle + " 内容：" + articleContent + " 标题违规判定：" + titleCensorResult + " 内容违规判定：" + articleCensorResult);
                throw new Rejected(422, "content_rejected", "您的文章经过AI审核存在违规内容，请修改内容后重试。" + bannedWords);
            }
        }

    }
}
