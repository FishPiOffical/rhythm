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
package org.b3log.symphony.model;

/**
 * Article draft model constants.
 */
public final class ArticleDraft {

    private ArticleDraft() {
    }

    public static final String ARTICLE_DRAFT = "article_draft";

    public static final String ARTICLE_DRAFT_ID = "articleDraftId";
    public static final String USER_ID = "articleDraftUserId";
    public static final String TITLE = "articleDraftTitle";
    public static final String SUMMARY = "articleDraftSummary";
    public static final String CONTENT = "articleDraftContent";
    public static final String THOUGHT_CONTENT = "articleDraftThoughtContent";
    public static final String TAGS = "articleDraftTags";
    public static final String ARTICLE_TYPE = "articleDraftType";
    public static final String COLUMN_ID = "articleDraftColumnId";
    public static final String COLUMN_TITLE = "articleDraftColumnTitle";
    public static final String CHAPTER_NO = "articleDraftChapterNo";
    public static final String REWARD_CONTENT = "articleDraftRewardContent";
    public static final String REWARD_POINT = "articleDraftRewardPoint";
    public static final String QNA_OFFER_POINT = "articleDraftQnAOfferPoint";
    public static final String COMMENTABLE = "articleDraftCommentable";
    public static final String ANONYMOUS = "articleDraftAnonymous";
    public static final String NOTIFY_FOLLOWERS = "articleDraftNotifyFollowers";
    public static final String SHOW_IN_LIST = "articleDraftShowInList";
    public static final String STATEMENT = "articleDraftStatement";
    public static final String CREATED_TIME = "articleDraftCreatedTime";
    public static final String UPDATED_TIME = "articleDraftUpdatedTime";

    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_SUMMARY_LENGTH = 160;
    public static final int MAX_TAGS_LENGTH = 255;
    public static final int MAX_COLUMN_ID_LENGTH = 19;
    public static final int MAX_CHAPTER_NO_LENGTH = 32;
    public static final int MAX_CONTENT_LENGTH = 1024000;
    public static final int MAX_THOUGHT_CONTENT_LENGTH = 1024000;
    public static final int MAX_REWARD_CONTENT_LENGTH = 102400;
}
