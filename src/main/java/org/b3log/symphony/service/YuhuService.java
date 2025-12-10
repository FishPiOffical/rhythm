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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.CompositeFilter;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.service.MembershipQueryService;
import org.b3log.symphony.model.Follow;
import org.b3log.symphony.repository.FollowRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Service
public class YuhuService {
    @Inject
    private YuhuBookRepository bookRepository;
    @Inject
    private YuhuVolumeRepository volumeRepository;
    @Inject
    private YuhuChapterRepository chapterRepository;
    @Inject
    private YuhuUserProfileRepository profileRepository;
    @Inject
    private YuhuBookmarkRepository bookmarkRepository;
    @Inject
    private YuhuReadingProgressRepository progressRepository;
    @Inject
    private YuhuCommentRepository commentRepository;
    @Inject
    private YuhuTagRepository tagRepository;
    @Inject
    private YuhuBookTagRepository bookTagRepository;
    @Inject
    private YuhuSubscriptionRepository subscriptionRepository;
    @Inject
    private YuhuVoteRepository voteRepository;

    @Inject
    private MembershipQueryService membershipQueryService;

    @Inject
    private FollowRepository followRepository;

    public JSONObject ensureProfile(final String linkedUserId) throws RepositoryException {
        final Query q = new Query().setFilter(new PropertyFilter(YuhuUserProfile.YUHU_USER_PROFILE_LINKED_USER_ID, FilterOperator.EQUAL, linkedUserId)).setPage(1,1);
        final JSONObject ret = profileRepository.getFirst(q);
        if (ret != null) return ret;
        final JSONObject profile = new JSONObject();
        profile.put(YuhuUserProfile.YUHU_USER_PROFILE_LINKED_USER_ID, linkedUserId);
        profile.put(YuhuUserProfile.YUHU_USER_PROFILE_ROLE, YuhuUserProfile.ROLE_C_READER);
        profile.put(YuhuUserProfile.YUHU_USER_PROFILE_PREF_THEME, "light");
        profile.put(YuhuUserProfile.YUHU_USER_PROFILE_PREF_FONT_SIZE, 18);
        profile.put(YuhuUserProfile.YUHU_USER_PROFILE_PREF_PAGE_WIDTH, 800);
        final String id = profileRepository.add(profile);
        profile.put(Keys.OBJECT_ID, id);
        return profile;
    }

    public String addBook(final JSONObject req) throws RepositoryException {
        final JSONObject book = new JSONObject();
        book.put(YuhuBook.YUHU_BOOK_TITLE, req.optString("title"));
        book.put(YuhuBook.YUHU_BOOK_INTRO, req.optString("intro"));
        book.put(YuhuBook.YUHU_BOOK_AUTHOR_PROFILE_ID, req.optString("authorProfileId"));
        book.put(YuhuBook.YUHU_BOOK_COVER_URL, req.optString("coverURL"));
        book.put(YuhuBook.YUHU_BOOK_STATUS, "serializing");
        book.put(YuhuBook.YUHU_BOOK_WORD_COUNT, 0);
        final String id = bookRepository.add(book);
        return id;
    }

    public JSONObject listBooks(final String tagAlias, final String q, final String sort, final int page, final int size) throws RepositoryException {
        final Query query = new Query().setPage(page, size).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        if (StringUtils.isNotBlank(q)) {
            query.setFilter(new PropertyFilter(YuhuBook.YUHU_BOOK_TITLE, FilterOperator.LIKE, q));
        }
        final JSONObject result = bookRepository.get(query);
        final JSONArray arr = (JSONArray) result.opt(Keys.RESULTS);
        final JSONObject ret = new JSONObject();
        ret.put("list", arr);
        final JSONObject pagination = result.optJSONObject("pagination");
        ret.put("pagination", pagination);
        return ret;
    }

    public JSONObject getBook(final String bookId) throws RepositoryException {
        final JSONObject book = bookRepository.get(bookId);
        final List<JSONObject> volumes = volumeRepository.getList(new Query().setFilter(new PropertyFilter(YuhuVolume.YUHU_VOLUME_BOOK_ID, FilterOperator.EQUAL, bookId)).addSort(YuhuVolume.YUHU_VOLUME_INDEX, SortDirection.ASCENDING).setPage(1, Integer.MAX_VALUE));
        final List<JSONObject> chapters = chapterRepository.getList(new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
            add(new PropertyFilter(YuhuChapter.YUHU_CHAPTER_BOOK_ID, FilterOperator.EQUAL, bookId));
            add(new PropertyFilter(YuhuChapter.YUHU_CHAPTER_STATUS, FilterOperator.EQUAL, "normal"));
        }})).addSort(YuhuChapter.YUHU_CHAPTER_INDEX, SortDirection.ASCENDING).setPage(1, Integer.MAX_VALUE));
        final JSONObject ret = new JSONObject();
        ret.put("book", book);
        ret.put("volumes", (Object) volumes);
        ret.put("chapters", (Object) chapters);
        return ret;
    }

    public JSONObject addVolume(final String bookId, final JSONObject req) throws RepositoryException {
        final List<JSONObject> list = volumeRepository.getList(new Query().setFilter(new PropertyFilter(YuhuVolume.YUHU_VOLUME_BOOK_ID, FilterOperator.EQUAL, bookId)).addSort(YuhuVolume.YUHU_VOLUME_INDEX, SortDirection.DESCENDING).setPage(1,1));
        int nextIndex = 1;
        if (!list.isEmpty()) nextIndex = list.get(0).optInt(YuhuVolume.YUHU_VOLUME_INDEX) + 1;
        final JSONObject vol = new JSONObject();
        vol.put(YuhuVolume.YUHU_VOLUME_BOOK_ID, bookId);
        vol.put(YuhuVolume.YUHU_VOLUME_INDEX, nextIndex);
        vol.put(YuhuVolume.YUHU_VOLUME_TITLE, req.optString("title"));
        vol.put(YuhuVolume.YUHU_VOLUME_INTRO, req.optString("intro"));
        final String id = volumeRepository.add(vol);
        return new JSONObject().put(Keys.OBJECT_ID, id).put(YuhuVolume.YUHU_VOLUME_INDEX, nextIndex);
    }

    public List<JSONObject> listVolumes(final String bookId) throws RepositoryException {
        return volumeRepository.getList(new Query().setFilter(new PropertyFilter(YuhuVolume.YUHU_VOLUME_BOOK_ID, FilterOperator.EQUAL, bookId)).addSort(YuhuVolume.YUHU_VOLUME_INDEX, SortDirection.ASCENDING).setPage(1, Integer.MAX_VALUE));
    }

    public JSONObject addChapterDraft(final String bookId, final JSONObject req) throws RepositoryException {
        final List<JSONObject> list = chapterRepository.getList(new Query().setFilter(new PropertyFilter(YuhuChapter.YUHU_CHAPTER_BOOK_ID, FilterOperator.EQUAL, bookId)).addSort(YuhuChapter.YUHU_CHAPTER_INDEX, SortDirection.DESCENDING).setPage(1,1));
        int nextIndex = 1;
        if (!list.isEmpty()) nextIndex = list.get(0).optInt(YuhuChapter.YUHU_CHAPTER_INDEX) + 1;
        final JSONObject ch = new JSONObject();
        ch.put(YuhuChapter.YUHU_CHAPTER_BOOK_ID, bookId);
        ch.put(YuhuChapter.YUHU_CHAPTER_VOLUME_ID, req.optString("volumeId"));
        ch.put(YuhuChapter.YUHU_CHAPTER_INDEX, nextIndex);
        ch.put(YuhuChapter.YUHU_CHAPTER_TITLE, req.optString("title"));
        ch.put(YuhuChapter.YUHU_CHAPTER_CONTENT_MD, req.optString("contentMD"));
        ch.put(YuhuChapter.YUHU_CHAPTER_CONTENT_HTML, "");
        ch.put(YuhuChapter.YUHU_CHAPTER_WORD_COUNT, 0);
        ch.put(YuhuChapter.YUHU_CHAPTER_IS_PAID, req.optBoolean("isPaid"));
        ch.put(YuhuChapter.YUHU_CHAPTER_STATUS, "draft");
        final String id = chapterRepository.add(ch);
        return new JSONObject().put(Keys.OBJECT_ID, id).put(YuhuChapter.YUHU_CHAPTER_INDEX, nextIndex).put("status","draft");
    }

    public JSONObject updateChapterDraft(final String chapterId, final JSONObject req) throws RepositoryException {
        final JSONObject ch = chapterRepository.get(chapterId);
        if (!"draft".equals(ch.optString(YuhuChapter.YUHU_CHAPTER_STATUS))) throw new RepositoryException("invalid state");
        if (req.has("title")) ch.put(YuhuChapter.YUHU_CHAPTER_TITLE, req.optString("title"));
        if (req.has("contentMD")) ch.put(YuhuChapter.YUHU_CHAPTER_CONTENT_MD, req.optString("contentMD"));
        chapterRepository.update(chapterId, ch);
        return new JSONObject().put("status","draft");
    }

    public JSONObject publishChapter(final String chapterId) throws RepositoryException {
        final JSONObject ch = chapterRepository.get(chapterId);
        if (!"draft".equals(ch.optString(YuhuChapter.YUHU_CHAPTER_STATUS))) throw new RepositoryException("invalid state");
        ch.put(YuhuChapter.YUHU_CHAPTER_STATUS, "pending");
        chapterRepository.update(chapterId, ch);
        return new JSONObject().put("status","pending");
    }

    public JSONObject approveChapter(final String chapterId, final String reviewerProfileId, final String note) throws RepositoryException {
        final JSONObject ch = chapterRepository.get(chapterId);
        if (!"pending".equals(ch.optString(YuhuChapter.YUHU_CHAPTER_STATUS))) throw new RepositoryException("invalid state");
        final String md = ch.optString(YuhuChapter.YUHU_CHAPTER_CONTENT_MD);
        final String html = Markdowns.toHTML(md);
        ch.put(YuhuChapter.YUHU_CHAPTER_CONTENT_HTML, html);
        ch.put(YuhuChapter.YUHU_CHAPTER_WORD_COUNT, md == null ? 0 : md.length());
        ch.put(YuhuChapter.YUHU_CHAPTER_STATUS, "normal");
        ch.put(YuhuChapter.YUHU_CHAPTER_PUBLISHED_AT, System.currentTimeMillis());
        ch.put("yuhuChapterReviewedAt", System.currentTimeMillis());
        ch.put("yuhuChapterReviewerProfileId", reviewerProfileId);
        ch.put("yuhuChapterReviewResult", "approved");
        if (note != null) ch.put("yuhuChapterReviewNote", note);
        chapterRepository.update(chapterId, ch);
        final String bookId = ch.optString(YuhuChapter.YUHU_CHAPTER_BOOK_ID);
        final JSONObject book = bookRepository.get(bookId);
        book.put(YuhuBook.YUHU_BOOK_LATEST_CHAPTER_ID, chapterId);
        int wc = book.optInt(YuhuBook.YUHU_BOOK_WORD_COUNT);
        wc += ch.optInt(YuhuChapter.YUHU_CHAPTER_WORD_COUNT);
        book.put(YuhuBook.YUHU_BOOK_WORD_COUNT, wc);
        bookRepository.update(bookId, book);
        return new JSONObject().put("status","normal").put(YuhuChapter.YUHU_CHAPTER_PUBLISHED_AT, ch.optLong(YuhuChapter.YUHU_CHAPTER_PUBLISHED_AT));
    }

    public JSONObject rejectChapter(final String chapterId, final String reviewerProfileId, final String note) throws RepositoryException {
        final JSONObject ch = chapterRepository.get(chapterId);
        if (!"pending".equals(ch.optString(YuhuChapter.YUHU_CHAPTER_STATUS))) throw new RepositoryException("invalid state");
        ch.put(YuhuChapter.YUHU_CHAPTER_STATUS, "draft");
        ch.put("yuhuChapterReviewedAt", System.currentTimeMillis());
        ch.put("yuhuChapterReviewerProfileId", reviewerProfileId);
        ch.put("yuhuChapterReviewResult", "rejected");
        if (note != null) ch.put("yuhuChapterReviewNote", note);
        chapterRepository.update(chapterId, ch);
        return new JSONObject().put("status","draft").put("reason", note == null ? "" : note);
    }

    public void setChapterState(final String chapterId, final String state) throws RepositoryException {
        final JSONObject ch = chapterRepository.get(chapterId);
        ch.put(YuhuChapter.YUHU_CHAPTER_STATUS, state);
        chapterRepository.update(chapterId, ch);
    }

    public JSONObject getPrefs(final String linkedUserId) throws RepositoryException {
        final JSONObject profile = ensureProfile(linkedUserId);
        final JSONObject ret = new JSONObject();
        ret.put("theme", profile.optString(YuhuUserProfile.YUHU_USER_PROFILE_PREF_THEME));
        ret.put("fontSize", profile.optInt(YuhuUserProfile.YUHU_USER_PROFILE_PREF_FONT_SIZE));
        ret.put("pageWidth", profile.optInt(YuhuUserProfile.YUHU_USER_PROFILE_PREF_PAGE_WIDTH));
        return ret;
    }

    public void setPrefs(final String linkedUserId, final JSONObject req) throws RepositoryException {
        final JSONObject profile = ensureProfile(linkedUserId);
        if (req.has("theme")) profile.put(YuhuUserProfile.YUHU_USER_PROFILE_PREF_THEME, req.optString("theme"));
        if (req.has("fontSize")) profile.put(YuhuUserProfile.YUHU_USER_PROFILE_PREF_FONT_SIZE, req.optInt("fontSize"));
        if (req.has("pageWidth")) profile.put(YuhuUserProfile.YUHU_USER_PROFILE_PREF_PAGE_WIDTH, req.optInt("pageWidth"));
        profileRepository.update(profile.optString(Keys.OBJECT_ID), profile);
    }

    public JSONObject getProgress(final String profileId, final String bookId) throws RepositoryException {
        final Query q = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
            add(new PropertyFilter(YuhuReadingProgress.YUHU_READING_PROGRESS_PROFILE_ID, FilterOperator.EQUAL, profileId));
            add(new PropertyFilter(YuhuReadingProgress.YUHU_READING_PROGRESS_BOOK_ID, FilterOperator.EQUAL, bookId));
        }})).setPage(1,1);
        final JSONObject ret = progressRepository.getFirst(q);
        if (ret == null) return new JSONObject();
        final JSONObject r = new JSONObject();
        r.put("chapterId", ret.optString(YuhuReadingProgress.YUHU_READING_PROGRESS_CHAPTER_ID));
        r.put("percent", ret.optInt(YuhuReadingProgress.YUHU_READING_PROGRESS_PERCENT));
        return r;
    }

    public void setProgress(final String profileId, final String bookId, final JSONObject req) throws RepositoryException {
        final Query q = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
            add(new PropertyFilter(YuhuReadingProgress.YUHU_READING_PROGRESS_PROFILE_ID, FilterOperator.EQUAL, profileId));
            add(new PropertyFilter(YuhuReadingProgress.YUHU_READING_PROGRESS_BOOK_ID, FilterOperator.EQUAL, bookId));
        }})).setPage(1,1);
        JSONObject ret = progressRepository.getFirst(q);
        if (ret == null) ret = new JSONObject();
        ret.put(YuhuReadingProgress.YUHU_READING_PROGRESS_PROFILE_ID, profileId);
        ret.put(YuhuReadingProgress.YUHU_READING_PROGRESS_BOOK_ID, bookId);
        ret.put(YuhuReadingProgress.YUHU_READING_PROGRESS_CHAPTER_ID, req.optString("chapterId"));
        ret.put(YuhuReadingProgress.YUHU_READING_PROGRESS_PERCENT, req.optInt("percent"));
        ret.put(YuhuReadingProgress.YUHU_READING_PROGRESS_UPDATED, System.currentTimeMillis());
        if (ret.has(Keys.OBJECT_ID)) {
            progressRepository.update(ret.optString(Keys.OBJECT_ID), ret);
        } else {
            final String id = progressRepository.add(ret);
            ret.put(Keys.OBJECT_ID, id);
        }
    }

    public String addBookmark(final JSONObject req) throws RepositoryException {
        final JSONObject bm = new JSONObject();
        bm.put(YuhuBookmark.YUHU_BOOKMARK_PROFILE_ID, req.optString("profileId"));
        bm.put(YuhuBookmark.YUHU_BOOKMARK_BOOK_ID, req.optString("bookId"));
        bm.put(YuhuBookmark.YUHU_BOOKMARK_CHAPTER_ID, req.optString("chapterId"));
        bm.put(YuhuBookmark.YUHU_BOOKMARK_PARAGRAPH_ID, req.optString("paragraphId"));
        bm.put(YuhuBookmark.YUHU_BOOKMARK_OFFSET, req.optInt("offset"));
        bm.put(YuhuBookmark.YUHU_BOOKMARK_CREATED, System.currentTimeMillis());
        return bookmarkRepository.add(bm);
    }

    public List<JSONObject> listBookmarks(final String profileId, final String bookId) throws RepositoryException {
        final List<JSONObject> list = bookmarkRepository.getList(new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
            add(new PropertyFilter(YuhuBookmark.YUHU_BOOKMARK_PROFILE_ID, FilterOperator.EQUAL, profileId));
            add(new PropertyFilter(YuhuBookmark.YUHU_BOOKMARK_BOOK_ID, FilterOperator.EQUAL, bookId));
        }})).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING).setPage(1, Integer.MAX_VALUE));
        return list;
    }

    public void deleteBookmark(final String id) throws RepositoryException {
        bookmarkRepository.remove(id);
    }

    public String addComment(final JSONObject req) throws RepositoryException {
        final JSONObject c = new JSONObject();
        c.put(YuhuComment.YUHU_COMMENT_PROFILE_ID, req.optString("profileId"));
        c.put(YuhuComment.YUHU_COMMENT_BOOK_ID, req.optString("bookId"));
        c.put(YuhuComment.YUHU_COMMENT_CHAPTER_ID, req.optString("chapterId"));
        c.put(YuhuComment.YUHU_COMMENT_PARAGRAPH_ID, req.optString("paragraphId"));
        c.put(YuhuComment.YUHU_COMMENT_CONTENT, req.optString("content"));
        c.put(YuhuComment.YUHU_COMMENT_CREATED, System.currentTimeMillis());
        c.put(YuhuComment.YUHU_COMMENT_STATUS, "normal");
        c.put(YuhuComment.YUHU_COMMENT_LIKE_CNT, 0);
        c.put(YuhuComment.YUHU_COMMENT_DISLIKE_CNT, 0);
        return commentRepository.add(c);
    }

    public List<JSONObject> listComments(final String chapterId, final int page, final int size) throws RepositoryException {
        return commentRepository.getList(new Query().setFilter(new PropertyFilter(YuhuComment.YUHU_COMMENT_CHAPTER_ID, FilterOperator.EQUAL, chapterId)).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING).setPage(page, size));
    }

    public JSONObject listCommentsAdmin(final String bookId, final String chapterId, final String profileId, final String status, final String q, final int page, final int size) throws RepositoryException {
        final java.util.List<org.b3log.latke.repository.Filter> filters = new ArrayList<>();
        if (bookId != null && !bookId.isEmpty()) filters.add(new PropertyFilter(YuhuComment.YUHU_COMMENT_BOOK_ID, FilterOperator.EQUAL, bookId));
        if (chapterId != null && !chapterId.isEmpty()) filters.add(new PropertyFilter(YuhuComment.YUHU_COMMENT_CHAPTER_ID, FilterOperator.EQUAL, chapterId));
        if (profileId != null && !profileId.isEmpty()) filters.add(new PropertyFilter(YuhuComment.YUHU_COMMENT_PROFILE_ID, FilterOperator.EQUAL, profileId));
        if (status != null && !status.isEmpty()) filters.add(new PropertyFilter(YuhuComment.YUHU_COMMENT_STATUS, FilterOperator.EQUAL, status));
        Query query = new Query().addSort(Keys.OBJECT_ID, SortDirection.DESCENDING).setPage(page, size);
        if (!filters.isEmpty()) query.setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));
        final List<JSONObject> list = commentRepository.getList(query);
        // 简单包含查询（content like q）在内存过滤，避免跨数据库差异
        final List<JSONObject> filtered = new ArrayList<>();
        if (q != null && !q.isEmpty()) {
            final String term = q.toLowerCase();
            for (final JSONObject c : list) {
                if (c.optString(YuhuComment.YUHU_COMMENT_CONTENT).toLowerCase().contains(term)) filtered.add(c);
            }
        } else {
            filtered.addAll(list);
        }
        final JSONObject ret = new JSONObject();
        ret.put("list", (Object) filtered);
        ret.put("pagination", new JSONObject().put("page", page).put("size", size).put("total", filtered.size()));
        return ret;
    }

    public void deleteComment(final String id) throws RepositoryException {
        commentRepository.remove(id);
    }

    public void updateComment(final String id, final JSONObject req) throws RepositoryException {
        final JSONObject c = commentRepository.get(id);
        if (c == null) return;
        if (req.has("content")) c.put(YuhuComment.YUHU_COMMENT_CONTENT, req.optString("content"));
        if (req.has("status")) c.put(YuhuComment.YUHU_COMMENT_STATUS, req.optString("status"));
        commentRepository.update(id, c);
    }

    public JSONObject getComment(final String id) throws RepositoryException {
        return commentRepository.get(id);
    }

    public JSONObject getChapter(final String id) throws RepositoryException {
        return chapterRepository.get(id);
    }

    public String addTag(final JSONObject req) throws RepositoryException {
        final String alias = req.optString("aliasEN").toLowerCase();
        final JSONObject tag = new JSONObject();
        tag.put(YuhuTag.YUHU_TAG_NAME, req.optString("name"));
        tag.put(YuhuTag.YUHU_TAG_ALIAS_EN, alias);
        tag.put(YuhuTag.YUHU_TAG_DESC, req.optString("desc"));
        return tagRepository.add(tag);
    }

    public List<JSONObject> listTags() throws RepositoryException {
        return tagRepository.getList(new Query().addSort(Keys.OBJECT_ID, SortDirection.DESCENDING).setPage(1, Integer.MAX_VALUE));
    }

    public void bindTagsToBook(final String bookId, final List<String> aliases) throws RepositoryException {
        final List<JSONObject> tags = new ArrayList<>();
        for (String alias : aliases) {
            final List<JSONObject> list = tagRepository.getList(new Query().setFilter(new PropertyFilter(YuhuTag.YUHU_TAG_ALIAS_EN, FilterOperator.EQUAL, alias)).setPage(1,1));
            if (!list.isEmpty()) tags.add(list.get(0));
        }
        for (JSONObject t : tags) {
            final JSONObject bt = new JSONObject();
            bt.put(YuhuBookTag.YUHU_BOOK_TAG_BOOK_ID, bookId);
            bt.put(YuhuBookTag.YUHU_BOOK_TAG_TAG_ID, t.optString(Keys.OBJECT_ID));
            bookTagRepository.add(bt);
        }
    }

    public void subscribe(final String profileId, final String bookId) throws RepositoryException {
        final JSONObject s = new JSONObject();
        s.put(YuhuSubscription.YUHU_SUBSCRIPTION_PROFILE_ID, profileId);
        s.put(YuhuSubscription.YUHU_SUBSCRIPTION_BOOK_ID, bookId);
        s.put(YuhuSubscription.YUHU_SUBSCRIPTION_CREATED, System.currentTimeMillis());
        subscriptionRepository.add(s);
    }

    public void unsubscribe(final String profileId, final String bookId) throws RepositoryException {
        final List<JSONObject> list = subscriptionRepository.getList(new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
            add(new PropertyFilter(YuhuSubscription.YUHU_SUBSCRIPTION_PROFILE_ID, FilterOperator.EQUAL, profileId));
            add(new PropertyFilter(YuhuSubscription.YUHU_SUBSCRIPTION_BOOK_ID, FilterOperator.EQUAL, bookId));
        }})).setPage(1, Integer.MAX_VALUE));
        for (JSONObject s : list) subscriptionRepository.remove(s.optString(Keys.OBJECT_ID));
    }

    public List<JSONObject> listSubscriptions(final String profileId) throws RepositoryException {
        return subscriptionRepository.getList(new Query().setFilter(new PropertyFilter(YuhuSubscription.YUHU_SUBSCRIPTION_PROFILE_ID, FilterOperator.EQUAL, profileId)).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING).setPage(1, Integer.MAX_VALUE));
    }

    public JSONObject vote(final String profileId, final JSONObject req) throws RepositoryException {
        final int type = typeByString(req.optString("type"));
        final JSONObject v = new JSONObject();
        v.put(YuhuVote.YUHU_VOTE_PROFILE_ID, profileId);
        v.put(YuhuVote.YUHU_VOTE_TARGET_TYPE, targetTypeByString(req.optString("targetType")));
        v.put(YuhuVote.YUHU_VOTE_TARGET_ID, req.optString("targetId"));
        v.put(YuhuVote.YUHU_VOTE_TYPE, type);
        v.put(YuhuVote.YUHU_VOTE_VALUE, req.optInt("value"));
        v.put(YuhuVote.YUHU_VOTE_POINTS_COST, pointsCost(type, req.optInt("value")));
        v.put(YuhuVote.YUHU_VOTE_CREATED, System.currentTimeMillis());
        // TODO: 付费开启后在此处写入积分流水（Pointtransfer）并/或写入通用打赏表（Reward），当前为免费模式不进行实际扣积分
        final String id = voteRepository.add(v);
        return new JSONObject().put("accepted", true).put("pointsCost", v.optInt(YuhuVote.YUHU_VOTE_POINTS_COST)).put(Keys.OBJECT_ID, id);
    }

    public JSONObject voteStats(final String bookId) throws RepositoryException {
        final Query q = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
            add(new PropertyFilter(YuhuVote.YUHU_VOTE_TARGET_TYPE, FilterOperator.EQUAL, YuhuVote.TARGET_TYPE_C_BOOK));
            add(new PropertyFilter(YuhuVote.YUHU_VOTE_TARGET_ID, FilterOperator.EQUAL, bookId));
        }})).setPage(1, Integer.MAX_VALUE);
        final List<JSONObject> list = voteRepository.getList(q);
        int monthly = 0, recommend = 0, tipSum = 0, tipCnt = 0, up = 0, down = 0;
        final List<Integer> ratings = new ArrayList<>();
        for (JSONObject v : list) {
            final int t = v.optInt(YuhuVote.YUHU_VOTE_TYPE);
            final int val = v.optInt(YuhuVote.YUHU_VOTE_VALUE);
            if (t == YuhuVote.TYPE_C_MONTHLY) monthly += val;
            else if (t == YuhuVote.TYPE_C_RECOMMEND) recommend += val;
            else if (t == YuhuVote.TYPE_C_TIP) { tipSum += val; tipCnt++; }
            else if (t == YuhuVote.TYPE_C_THUMB_UP) up += val;
            else if (t == YuhuVote.TYPE_C_THUMB_DOWN) down += val;
            else if (t == YuhuVote.TYPE_C_RATING) ratings.add(val);
        }
        double avgRating = 0;
        if (!ratings.isEmpty()) {
            int s = 0; for (int r : ratings) s += r; avgRating = ((double)s)/ratings.size();
        }
        double score = monthly + recommend + tipSum + (up - down) + avgRating;
        final JSONObject ret = new JSONObject();
        ret.put("monthly", monthly);
        ret.put("recommend", recommend);
        ret.put("tip", new JSONObject().put("sum", tipSum).put("count", tipCnt));
        ret.put("thumbUp", up);
        ret.put("thumbDown", down);
        ret.put("avgRating", avgRating);
        ret.put("score", score);
        return ret;
    }

    public JSONObject getAuthorProfile(final String profileId) throws RepositoryException {
        final JSONObject p = profileRepository.get(profileId);
        if (p == null) return new JSONObject();
        final JSONObject ret = new JSONObject();
        ret.put("profileId", p.optString(Keys.OBJECT_ID));
        ret.put("nickname", p.optString(YuhuUserProfile.YUHU_USER_PROFILE_NICKNAME));
        ret.put("intro", p.optString(YuhuUserProfile.YUHU_USER_PROFILE_INTRO));
        ret.put("avatarURL", p.optString(YuhuUserProfile.YUHU_USER_PROFILE_AVATAR_URL));
        ret.put("role", p.optString(YuhuUserProfile.YUHU_USER_PROFILE_ROLE));
        ret.put("created", p.optLong(YuhuUserProfile.YUHU_USER_PROFILE_CREATED));
        ret.put("updated", p.optLong(YuhuUserProfile.YUHU_USER_PROFILE_UPDATED));
        long created = p.optLong(YuhuUserProfile.YUHU_USER_PROFILE_CREATED);
        if (created > 0) {
            long days = Math.max(1, (System.currentTimeMillis() - created) / (1000L*60*60*24));
            ret.put("creationDays", days);
        }
        // works & word count summary
        final List<JSONObject> books = bookRepository.getList(new Query().setFilter(new PropertyFilter(YuhuBook.YUHU_BOOK_AUTHOR_PROFILE_ID, FilterOperator.EQUAL, profileId)).setPage(1, Integer.MAX_VALUE));
        ret.put("works", books.size());
        int wordCount = 0;
        for (final JSONObject b : books) wordCount += b.optInt(YuhuBook.YUHU_BOOK_WORD_COUNT);
        ret.put("wordCount", wordCount);
        // level & badges via membership
        final String linkedUserId = p.optString(YuhuUserProfile.YUHU_USER_PROFILE_LINKED_USER_ID);
        try {
            final org.json.JSONObject m = membershipQueryService.getStatusByUserId(linkedUserId);
            final int state = m.optInt(org.b3log.symphony.model.Membership.STATE, 0);
            final String lvCode = m.optString(org.b3log.symphony.model.Membership.LV_CODE, "");
            ret.put("levelCode", lvCode);
            ret.put("levelActive", state == 1);
            final org.json.JSONArray badges = new org.json.JSONArray();
            if (state == 1 && !lvCode.isEmpty()) {
                badges.put(lvCode);
            }
            ret.put("badges", badges);
        } catch (final Exception ignore) {
            ret.put("levelCode", "");
            ret.put("levelActive", false);
            ret.put("badges", new org.json.JSONArray());
        }
        // fans: followers count on linked user
        try {
            final Query fq = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new java.util.ArrayList<>() {{
                add(new PropertyFilter(Follow.FOLLOWING_ID, FilterOperator.EQUAL, linkedUserId));
                add(new PropertyFilter(Follow.FOLLOWING_TYPE, FilterOperator.EQUAL, Follow.FOLLOWING_TYPE_C_USER));
            }}));
            final long followers = followRepository.count(fq);
            ret.put("followers", followers);
        } catch (final RepositoryException ignore) {
            ret.put("followers", 0);
        }
        return ret;
    }

    public JSONObject getAuthorByBook(final String bookId) throws RepositoryException {
        final JSONObject book = bookRepository.get(bookId);
        if (book == null) return new JSONObject();
        final String profileId = book.optString(YuhuBook.YUHU_BOOK_AUTHOR_PROFILE_ID);
        return getAuthorProfile(profileId);
    }

    public JSONObject getAuthorStats(final String profileId) throws RepositoryException {
        final List<JSONObject> books = bookRepository.getList(new Query().setFilter(new PropertyFilter(YuhuBook.YUHU_BOOK_AUTHOR_PROFILE_ID, FilterOperator.EQUAL, profileId)).setPage(1, Integer.MAX_VALUE));
        int works = books.size();
        int wordCount = 0;
        int chaptersPublished = 0;
        int subscribers = 0;
        int comments = 0;
        int bookmarks = 0;
        int monthly = 0, recommend = 0, tipSum = 0, tipCnt = 0, up = 0, down = 0;
        final List<Integer> ratings = new ArrayList<>();
        for (final JSONObject b : books) {
            wordCount += b.optInt(YuhuBook.YUHU_BOOK_WORD_COUNT);
            final String bookId = b.optString(Keys.OBJECT_ID);
            chaptersPublished += chapterRepository.count(new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
                add(new PropertyFilter(YuhuChapter.YUHU_CHAPTER_BOOK_ID, FilterOperator.EQUAL, bookId));
                add(new PropertyFilter(YuhuChapter.YUHU_CHAPTER_STATUS, FilterOperator.EQUAL, "normal"));
            }})));
            subscribers += subscriptionRepository.count(new Query().setFilter(new PropertyFilter(YuhuSubscription.YUHU_SUBSCRIPTION_BOOK_ID, FilterOperator.EQUAL, bookId)));
            comments += commentRepository.count(new Query().setFilter(new PropertyFilter(YuhuComment.YUHU_COMMENT_BOOK_ID, FilterOperator.EQUAL, bookId)));
            bookmarks += bookmarkRepository.count(new Query().setFilter(new PropertyFilter(YuhuBookmark.YUHU_BOOKMARK_BOOK_ID, FilterOperator.EQUAL, bookId)));
            final List<JSONObject> vlist = voteRepository.getList(new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
                add(new PropertyFilter(YuhuVote.YUHU_VOTE_TARGET_TYPE, FilterOperator.EQUAL, YuhuVote.TARGET_TYPE_C_BOOK));
                add(new PropertyFilter(YuhuVote.YUHU_VOTE_TARGET_ID, FilterOperator.EQUAL, bookId));
            }})).setPage(1, Integer.MAX_VALUE));
            for (final JSONObject v : vlist) {
                final int t = v.optInt(YuhuVote.YUHU_VOTE_TYPE);
                final int val = v.optInt(YuhuVote.YUHU_VOTE_VALUE);
                if (t == YuhuVote.TYPE_C_MONTHLY) monthly += val;
                else if (t == YuhuVote.TYPE_C_RECOMMEND) recommend += val;
                else if (t == YuhuVote.TYPE_C_TIP) { tipSum += val; tipCnt++; }
                else if (t == YuhuVote.TYPE_C_THUMB_UP) up += val;
                else if (t == YuhuVote.TYPE_C_THUMB_DOWN) down += val;
                else if (t == YuhuVote.TYPE_C_RATING) ratings.add(val);
            }
        }
        double avgRating = 0;
        if (!ratings.isEmpty()) {
            int s = 0; for (int r : ratings) s += r; avgRating = ((double) s) / ratings.size();
        }
        double score = monthly + recommend + tipSum + (up - down) + avgRating;
        final JSONObject ret = new JSONObject();
        ret.put("works", works);
        ret.put("wordCount", wordCount);
        ret.put("chaptersPublished", chaptersPublished);
        ret.put("subscribers", subscribers);
        ret.put("comments", comments);
        ret.put("bookmarks", bookmarks);
        ret.put("monthly", monthly);
        ret.put("recommend", recommend);
        ret.put("tip", new JSONObject().put("sum", tipSum).put("count", tipCnt));
        ret.put("thumbUp", up);
        ret.put("thumbDown", down);
        ret.put("avgRating", avgRating);
        ret.put("ratingsCount", ratings.size());
        ret.put("score", score);
        return ret;
    }

    public JSONObject getAuthorProfilePrivate(final String profileId) throws RepositoryException {
        final JSONObject base = getAuthorProfile(profileId);
        final List<JSONObject> books = bookRepository.getList(new Query().setFilter(new PropertyFilter(YuhuBook.YUHU_BOOK_AUTHOR_PROFILE_ID, FilterOperator.EQUAL, profileId)).setPage(1, Integer.MAX_VALUE));
        int monthly = 0, recommend = 0, tipSum = 0, tipCnt = 0, up = 0, down = 0;
        final List<Integer> ratings = new ArrayList<>();
        for (final JSONObject b : books) {
            final String bookId = b.optString(Keys.OBJECT_ID);
            final List<JSONObject> vlist = voteRepository.getList(new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, new ArrayList<>() {{
                add(new PropertyFilter(YuhuVote.YUHU_VOTE_TARGET_TYPE, FilterOperator.EQUAL, YuhuVote.TARGET_TYPE_C_BOOK));
                add(new PropertyFilter(YuhuVote.YUHU_VOTE_TARGET_ID, FilterOperator.EQUAL, bookId));
            }})).setPage(1, Integer.MAX_VALUE));
            for (final JSONObject v : vlist) {
                final int t = v.optInt(YuhuVote.YUHU_VOTE_TYPE);
                final int val = v.optInt(YuhuVote.YUHU_VOTE_VALUE);
                if (t == YuhuVote.TYPE_C_MONTHLY) monthly += val;
                else if (t == YuhuVote.TYPE_C_RECOMMEND) recommend += val;
                else if (t == YuhuVote.TYPE_C_TIP) { tipSum += val; tipCnt++; }
                else if (t == YuhuVote.TYPE_C_THUMB_UP) up += val;
                else if (t == YuhuVote.TYPE_C_THUMB_DOWN) down += val;
                else if (t == YuhuVote.TYPE_C_RATING) ratings.add(val);
            }
        }
        double avgRating = 0;
        if (!ratings.isEmpty()) {
            int s = 0; for (int r : ratings) s += r; avgRating = ((double) s) / ratings.size();
        }
        double score = monthly + recommend + tipSum + (up - down) + avgRating;
        base.put("monthly", monthly);
        base.put("recommend", recommend);
        base.put("tip", new JSONObject().put("sum", tipSum).put("count", tipCnt));
        base.put("thumbUp", up);
        base.put("thumbDown", down);
        base.put("avgRating", avgRating);
        base.put("ratingsCount", ratings.size());
        base.put("score", score);
        return base;
    }

    public JSONObject subscriptionStats(final String bookId) throws RepositoryException {
        final long subscribers = subscriptionRepository.count(new Query().setFilter(new PropertyFilter(YuhuSubscription.YUHU_SUBSCRIPTION_BOOK_ID, FilterOperator.EQUAL, bookId)));
        return new JSONObject().put("subscribers", subscribers);
    }

    public List<JSONObject> listAuthorBooks(final String profileId, final int page, final int size) throws RepositoryException {
        return bookRepository.getList(new Query().setFilter(new PropertyFilter(YuhuBook.YUHU_BOOK_AUTHOR_PROFILE_ID, FilterOperator.EQUAL, profileId)).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING).setPage(page, size));
    }

    private int typeByString(final String s) {
        if ("monthly".equals(s)) return YuhuVote.TYPE_C_MONTHLY;
        if ("recommend".equals(s)) return YuhuVote.TYPE_C_RECOMMEND;
        if ("tip".equals(s)) return YuhuVote.TYPE_C_TIP;
        if ("thumbUp".equals(s)) return YuhuVote.TYPE_C_THUMB_UP;
        if ("thumbDown".equals(s)) return YuhuVote.TYPE_C_THUMB_DOWN;
        return YuhuVote.TYPE_C_RATING;
    }

    private int targetTypeByString(final String s) {
        if ("chapter".equals(s)) return YuhuVote.TARGET_TYPE_C_CHAPTER;
        return YuhuVote.TARGET_TYPE_C_BOOK;
    }

    private int pointsCost(final int type, final int value) {
        // TODO: 免费模式下可统一返回 0；若开启付费请改为读取配置计算实际消耗
        if (type == YuhuVote.TYPE_C_RECOMMEND) return 512;
        if (type == YuhuVote.TYPE_C_MONTHLY) return 0;
        if (type == YuhuVote.TYPE_C_TIP) return value;
        return 0;
    }
}
