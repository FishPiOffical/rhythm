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

import org.b3log.symphony.util.UserOnlineStatus;
import org.apache.commons.lang.time.DateUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.Filter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Domain;
import org.b3log.symphony.model.Follow;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.SystemSettings;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.BreezemoonRepository;
import org.b3log.symphony.repository.DomainRepository;
import org.b3log.symphony.repository.DomainTagRepository;
import org.b3log.symphony.repository.FollowRepository;
import org.b3log.symphony.repository.TagArticleRepository;
import org.b3log.symphony.repository.TagRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.SystemSettingsRepository;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3ContentSnapshots;
import org.b3log.symphony.util.ApiV3ArticleStickState;
import org.b3log.symphony.util.Symphonys;
import org.b3log.symphony.util.ApiV3ProfileQueries;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3AvatarUrl;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 提供目录、搜索和公开资料的只读投影。
 */
@Service
public class ApiV3DiscoveryQueryService {
    @Inject
    private TagRepository tagRepository;
    @Inject
    private DomainRepository domainRepository;
    @Inject
    private TagArticleRepository tagArticleRepository;
    @Inject
    private DomainTagRepository domainTagRepository;
    @Inject
    private FollowRepository followRepository;
    @Inject
    private ArticleRepository articleRepository;
    @Inject
    private UserRepository userRepository;
    @Inject
    private SystemSettingsRepository systemSettingsRepository;
    @Inject
    private BreezemoonRepository breezemoonRepository;
    @Inject
    private ApiV3ContentDtoService contentDtoService;
    @Inject
    private RoleQueryService roleQueryService;

    public record Following(String ownerId, String source) {
    }

    public record Selection(String userId, String tagId, String domainId, String keyword, String collectionUserId,
                            String city, Following following) {
        public Selection(final String userId, final String tagId, final String domainId, final String keyword, final String collectionUserId, final String city) {
            this(userId, tagId, domainId, keyword, collectionUserId, city, null);
        }

        public static Selection watching(final String ownerId, final String source) {
            return new Selection(null, null, null, null, null, null, new Following(ownerId, source));
        }

        public Selection(final String userId, final String tagId, final String domainId, final String keyword) {
            this(userId, tagId, domainId, keyword, null, null);
        }

        public Selection(final String userId, final String tagId, final String domainId, final String keyword, final String collectionUserId) {
            this(userId, tagId, domainId, keyword, collectionUserId, null);
        }

        public static Selection all() {
            return new Selection(null, null, null, null);
        }
    }

    public JSONObject tags(final int p, final int size) throws RepositoryException {
        final Filter filter = CompositeFilterOperator.and(
                new PropertyFilter(Tag.TAG_STATUS, FilterOperator.EQUAL, Tag.TAG_STATUS_C_VALID),
                new PropertyFilter(Tag.TAG_REFERENCE_CNT, FilterOperator.GREATER_THAN, 0));
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1)
                .addSort(Tag.TAG_REFERENCE_CNT, SortDirection.DESCENDING).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject tag : tagRepository.getList(query)) list.put(tagDto(tag));
        return ApiV3Requests.pagination(list, p, size, tagRepository.count(new Query().setFilter(filter)));
    }

    public JSONObject tag(final String id) throws RepositoryException {
        final JSONObject tag = tagRepository.get(id);
        if (null == tag || Tag.TAG_STATUS_C_VALID != tag.optInt(Tag.TAG_STATUS)) throw ApiV3Requests.notFound();
        return tagDto(tag);
    }

    public JSONObject domains(final int p, final int size) throws RepositoryException {
        final Filter filter = new PropertyFilter(Domain.DOMAIN_STATUS, FilterOperator.EQUAL, Domain.DOMAIN_STATUS_C_VALID);
        final Query query = new Query().setFilter(filter).setPage(p, size).setPageCount(1)
                .addSort(Domain.DOMAIN_SORT, SortDirection.ASCENDING).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        final JSONArray list = new JSONArray();
        for (final JSONObject domain : domainRepository.getList(query)) list.put(domainDto(domain));
        return ApiV3Requests.pagination(list, p, size, domainRepository.count(new Query().setFilter(filter)));
    }

    public JSONObject domain(final String id) throws RepositoryException {
        final JSONObject domain = domainRepository.get(id);
        if (null == domain || Domain.DOMAIN_STATUS_C_VALID != domain.optInt(Domain.DOMAIN_STATUS))
            throw ApiV3Requests.notFound();
        return domainDto(domain);
    }

    public JSONObject domainTags(final String id, final int p, final int size) throws RepositoryException {
        domain(id);
        final String from = " FROM " + tagRepository.getName() + " t WHERE t.tagStatus=? AND EXISTS (SELECT 1 FROM "
                + domainTagRepository.getName() + " dt WHERE dt.tag_oId=t.oId AND dt.domain_oId=?)";
        final long total = tagRepository.select("SELECT COUNT(1) AS total" + from, Tag.TAG_STATUS_C_VALID, id).get(0).optLong("total");
        final List<JSONObject> rows = tagRepository.select("SELECT t.*" + from + " ORDER BY t.tagReferenceCount DESC,t.oId DESC LIMIT ?,?",
                Tag.TAG_STATUS_C_VALID, id, (p - 1) * size, size);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : rows) list.put(tagDto(row));
        return ApiV3Requests.pagination(list, p, size, total);
    }

    /**
     * 先筛选再计数和分页。SQL 标识符来自内部定义，查询值通过参数绑定。
     */
    public JSONObject articles(final JSONObject viewer, final int p, final int size, final String sort,
                               final String type, final Selection selection) throws RepositoryException {
        final StringBuilder from = new StringBuilder(" FROM ").append(articleRepository.getName())
                .append(" a JOIN ").append(userRepository.getName()).append(" u ON u.oId=a.articleAuthorId WHERE ")
                .append("a.articleStatus<>? AND a.articleType<>? AND a.articleShowInList<>? AND a.articleTags<>? AND a.articleTags NOT LIKE ?")
                .append(" AND u.userStatus NOT IN (?,?)");
        final List<Object> args = new ArrayList<>(List.of(Article.ARTICLE_STATUS_C_INVALID, Article.ARTICLE_TYPE_C_DISCUSSION,
                Article.ARTICLE_SHOW_IN_LIST_C_NOT, Tag.TAG_TITLE_C_SANDBOX, "B3log%", UserExt.USER_STATUS_C_INVALID, UserExt.USER_STATUS_C_DEACTIVATED));
        switch (type) {
            case "all" -> {
            }
            case "perfect" -> {
                from.append(" AND a.articlePerfect=?");
                args.add(Article.ARTICLE_PERFECT_C_PERFECT);
            }
            case "qna" -> {
                from.append(" AND a.articleType=?");
                args.add(Article.ARTICLE_TYPE_C_QNA);
            }
            default -> {
                from.append(" AND a.articleType").append("long".equals(type) ? "=?" : "<>?");
                args.add(Article.ARTICLE_TYPE_C_LONG);
            }
        }
        if ("month-comments".equals(sort)) {
            from.append(" AND a.oId>=?");
            args.add(String.valueOf(DateUtils.addMonths(new Date(), -1).getTime()));
        }
        if (null == viewer) {
            from.append(" AND a.articleAnonymousView<>?");
            args.add(Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW);
        }
        if (null != selection.userId()) {
            from.append(" AND a.articleAuthorId=? AND a.articleAnonymous=?");
            args.add(selection.userId());
            args.add(Article.ARTICLE_ANONYMOUS_C_PUBLIC);
        }
        if (null != selection.tagId()) {
            from.append(" AND EXISTS (SELECT 1 FROM ").append(tagArticleRepository.getName())
                    .append(" ta WHERE ta.article_oId=a.oId AND ta.tag_oId=?)");
            args.add(selection.tagId());
        }
        if (null != selection.domainId()) {
            from.append(" AND EXISTS (SELECT 1 FROM ").append(tagArticleRepository.getName()).append(" ta JOIN ")
                    .append(domainTagRepository.getName()).append(" dt ON dt.tag_oId=ta.tag_oId JOIN ")
                    .append(tagRepository.getName()).append(" t ON t.oId=ta.tag_oId WHERE ta.article_oId=a.oId AND dt.domain_oId=? AND t.tagStatus=?)");
            args.add(selection.domainId());
            args.add(Tag.TAG_STATUS_C_VALID);
        }
        if (null != selection.collectionUserId()) {
            from.append(" AND EXISTS (SELECT 1 FROM ").append(followRepository.getName())
                    .append(" f WHERE f.followingId=a.oId AND f.followerId=? AND f.followingType=?)");
            args.add(selection.collectionUserId());
            args.add(Follow.FOLLOWING_TYPE_C_ARTICLE);
        }
        if (null != selection.city()) {
            from.append(" AND a.articleCity=? AND a.articleAnonymous=? AND EXISTS (SELECT 1 FROM ")
                    .append(userRepository.getName()).append(" geo WHERE geo.oId=a.articleAuthorId AND geo.userGeoStatus=? AND geo.userStatus=?)");
            args.add(selection.city());
            args.add(Article.ARTICLE_ANONYMOUS_C_PUBLIC);
            args.add(UserExt.USER_GEO_STATUS_C_PUBLIC);
            args.add(UserExt.USER_STATUS_C_VALID);
        }
        if (null != selection.following()) {
            final ApiV3ProfileQueries.Fragment fragment = ApiV3ProfileQueries.following(followRepository.getName(), tagArticleRepository.getName(),
                    tagRepository.getName(), selection.following().ownerId(), selection.following().source(), Tag.TAG_STATUS_C_VALID);
            from.append(fragment.sql());
            args.addAll(fragment.args());
        }
        if (null != selection.keyword()) {
            from.append(" AND (a.articleTitle LIKE ? ESCAPE '!' OR a.articleContent LIKE ? ESCAPE '!')");
            final String pattern = "%" + escapeLike(selection.keyword()) + "%";
            args.add(pattern);
            args.add(pattern);
        }
        final long total = articleRepository.select("SELECT COUNT(1) AS total" + from, args.toArray()).get(0).optLong("total");
        final String order = switch (sort) {
            case "active" -> "a.articleLatestCmtTime DESC,";
            case "hot" -> "a.redditScore DESC,";
            case "comments" -> "a.articleCommentCount DESC,";
            case "month-comments" ->
                    "recent".equals(type) ? "a.articleCommentCount DESC," : "a.articleStick DESC,a.articleCommentCount DESC,";
            default -> "";
        };
        final String stickOrder;
        if ("recent".equals(type)) {
            final long threshold = ApiV3ArticleStickState.threshold(System.currentTimeMillis(), Symphonys.STICK_ARTICLE_TIME);
            stickOrder = "CASE WHEN a.articleStick=? THEN 2 WHEN a.articleStick>=? THEN 1 ELSE 0 END DESC,"
                    + "CASE WHEN a.articleStick>=? THEN a.articleStick ELSE 0 END DESC,";
            args.add(Long.MAX_VALUE);
            args.add(threshold);
            args.add(threshold);
        } else stickOrder = "";
        args.add((p - 1) * size);
        args.add(size);
        final List<JSONObject> rows = articleRepository.select("SELECT a.*," + ApiV3ContentSnapshots.authorColumns() + from + " ORDER BY " + stickOrder + order + "a.oId DESC LIMIT ?,?", args.toArray());
        final JSONArray list = new JSONArray();
        for (final JSONObject row : rows) list.put(contentDtoService.summary(row));
        return ApiV3Requests.pagination(list, p, size, total);
    }

    public JSONObject targetUser(final String name) throws RepositoryException {
        final JSONObject user = currentProfileUser("userName", name);
        if (null == user || UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)
                || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS))
            throw ApiV3Requests.notFound();
        return user;
    }

    /**
     * 查询当前隐私、状态和用户名，公开资料权限以数据库当前状态为准。
     */
    private JSONObject currentProfileUser(final String field, final String value) throws RepositoryException {
        if (!java.util.Set.of("oId", "userName").contains(field)) throw new IllegalArgumentException("profile lookup");
        final var rows = userRepository.select("SELECT * FROM `" + userRepository.getName() + "` WHERE " + field + "=? LIMIT 2", value);
        if (rows.size() > 1) throw new RepositoryException("Duplicate v3 profile target");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject profile(final JSONObject user) throws RepositoryException {
        final long points = user.getLong(UserExt.USER_POINT);
        final long onlineMinutes = user.getLong(UserExt.ONLINE_MINUTE);
        if (points < -9007199254740991L || points > 9007199254740991L || onlineMinutes < 0 || onlineMinutes > 9007199254740991L) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "用户统计暂不可用");
        }
        final String memberNumber = user.optString(UserExt.USER_NO);
        final String roleId = user.optString(User.USER_ROLE);
        final JSONObject role = roleId.isEmpty() ? null : roleQueryService.getRole(roleId);
        // 仅当用户明确公开位置时返回城市，不投影其他地理资料。
        final String city = user.optInt(UserExt.USER_GEO_STATUS, -1) == UserExt.USER_GEO_STATUS_C_PUBLIC
                ? user.optString(UserExt.USER_CITY) : "";
        return author(user).put("userIntro", user.optString(UserExt.USER_INTRO))
                .put("userURL", user.optString(User.USER_URL))
                .put("cardBg", cardBackground(user.optString(Keys.OBJECT_ID)))
                .put("userNo", memberNumber.matches("[1-9][0-9]{0,18}") ? memberNumber : "")
                .put("userRole", role == null ? "" : role.optString(Role.ROLE_NAME))
                .put("userCity", city)
                .put("userOnlineFlag", UserOnlineStatus.publicFlag(user))
                .put("userPoint", points).put("onlineMinute", onlineMinutes);
    }

    private String cardBackground(final String userId) throws RepositoryException {
        final var rows = systemSettingsRepository.select("SELECT settings FROM `" + systemSettingsRepository.getName()
                + "` WHERE userId=? LIMIT 2", userId);
        if (rows.isEmpty()) return "";
        if (rows.size() != 1) throw new ApiV3Exception(503, "temporarily_unavailable", "个性卡片配置暂不可用");
        try {
            final JSONObject settings = new JSONObject(rows.getFirst().getString(SystemSettings.SETTINGS));
            final Object background = settings.opt("cardBg");
            return background instanceof String url ? ApiV3AvatarUrl.stored(url) : "";
        } catch (final org.json.JSONException e) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "个性卡片配置暂不可用");
        }
    }

    public void requireUserList(final JSONObject user, final JSONObject viewer, final String field) {
        if (!ApiV3ContentUtil.canViewUserList(user, viewer, field)) throw ApiV3Requests.forbidden();
    }

    /**
     * kind 是由处理器选择的内部枚举，SQL 标识符由内部映射确定。
     */
    public JSONObject userRelations(final String userId, final int p, final int size, final String kind) throws RepositoryException {
        final boolean tags = "tags".equals(kind);
        final boolean followers = "followers".equals(kind);
        final String table = tags ? tagRepository.getName() : userRepository.getName();
        final String entityId = followers ? "f.followerId" : "f.followingId";
        final String owner = followers ? "f.followingId" : "f.followerId";
        final String from = " FROM " + table + " e WHERE "
                + (tags ? "e.tagStatus=?" : "e.userStatus NOT IN (?,?)")
                + " AND EXISTS (SELECT 1 FROM " + followRepository.getName() + " f WHERE " + entityId + "=e.oId AND "
                + owner + "=? AND f.followingType=?)";
        final List<Object> args = new ArrayList<>();
        if (tags) args.add(Tag.TAG_STATUS_C_VALID);
        else {
            args.add(UserExt.USER_STATUS_C_INVALID);
            args.add(UserExt.USER_STATUS_C_DEACTIVATED);
        }
        args.add(userId);
        args.add(tags ? Follow.FOLLOWING_TYPE_C_TAG : Follow.FOLLOWING_TYPE_C_USER);
        final long total = followRepository.select("SELECT COUNT(1) AS total" + from, args.toArray()).get(0).optLong("total");
        args.add((p - 1) * size);
        args.add(size);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : followRepository.select("SELECT e.*" + from + " ORDER BY e.oId DESC LIMIT ?,?", args.toArray())) {
            list.put(tags ? tagDto(row) : author(row));
        }
        return ApiV3Requests.pagination(list, p, size, total);
    }

    public JSONObject breezemoons(final String userId, final int p, final int size) throws RepositoryException {
        final BreezemoonRepository.Page page = breezemoonRepository.getV3Page(userRepository.getName(), userId, p, size,
                UserExt.USER_STATUS_C_INVALID, UserExt.USER_STATUS_C_DEACTIVATED, UserExt.USER_XXX_STATUS_C_ENABLED);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : page.rows()) list.put(breezemoonDto(row));
        return ApiV3Requests.pagination(list, p, size, page.total());
    }


    public JSONObject followingBreezemoons(final String actor, final int p, final int size) throws RepositoryException {
        final BreezemoonRepository.Page page = breezemoonRepository.getV3FollowingPage(userRepository.getName(), followRepository.getName(), actor, p, size,
                UserExt.USER_STATUS_C_INVALID, UserExt.USER_STATUS_C_DEACTIVATED, UserExt.USER_XXX_STATUS_C_ENABLED);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : page.rows()) list.put(breezemoonDto(row));
        return ApiV3Requests.pagination(list, p, size, page.total());
    }

    public JSONObject breezemoonDto(final JSONObject row) throws RepositoryException {
        final JSONObject author = currentProfileUser("oId", row.optString(Breezemoon.BREEZEMOON_AUTHOR_ID));
        if (null == author || UserExt.USER_STATUS_C_INVALID == author.optInt(UserExt.USER_STATUS)
                || UserExt.USER_STATUS_C_DEACTIVATED == author.optInt(UserExt.USER_STATUS))
            throw ApiV3Requests.notFound();
        return breezemoonDto(row, author);
    }

    private JSONObject breezemoonDto(final JSONObject row, final JSONObject currentAuthor) {
        final String content = row.optString(Breezemoon.BREEZEMOON_CONTENT);
        return new JSONObject().put("id", row.optString(Keys.OBJECT_ID)).put("content", content)
                .put("contentHtml", Markdowns.clean(Markdowns.toHTML(Emotions.convert(content)), ""))
                .put("createdAt", row.optLong(Breezemoon.BREEZEMOON_CREATED)).put("updatedAt", row.optLong(Breezemoon.BREEZEMOON_UPDATED))
                .put("author", author(currentAuthor));
    }

    public JSONObject breezemoon(final String id, final JSONObject viewer) throws RepositoryException {
        final JSONObject row = breezemoonRepository.get(id);
        if (null == row || Breezemoon.BREEZEMOON_STATUS_C_VALID != row.optInt(Breezemoon.BREEZEMOON_STATUS))
            throw ApiV3Requests.notFound();
        final JSONObject author = currentProfileUser("oId", row.optString(Breezemoon.BREEZEMOON_AUTHOR_ID));
        if (null == author || UserExt.USER_STATUS_C_INVALID == author.optInt(UserExt.USER_STATUS) || UserExt.USER_STATUS_C_DEACTIVATED == author.optInt(UserExt.USER_STATUS)
                || !ApiV3ContentUtil.canViewUserList(author, viewer, UserExt.USER_BREEZEMOON_STATUS))
            throw ApiV3Requests.notFound();
        return breezemoonDto(row, author);
    }

    private JSONObject author(final JSONObject user) {
        return contentDtoService.author(user, false);
    }

    public static String escapeLike(final String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static JSONObject tagDto(final JSONObject tag) {
        return new JSONObject().put("id", tag.optString(Keys.OBJECT_ID)).put("title", tag.optString(Tag.TAG_TITLE))
                .put("uri", tag.optString(Tag.TAG_URI)).put("description", tag.optString(Tag.TAG_DESCRIPTION))
                .put("iconURL", tag.optString(Tag.TAG_ICON_PATH)).put("articleCount", tag.optInt(Tag.TAG_REFERENCE_CNT))
                .put("followerCount", tag.optInt(Tag.TAG_FOLLOWER_CNT));
    }

    static JSONObject domainDto(final JSONObject domain) {
        return new JSONObject().put("id", domain.optString(Keys.OBJECT_ID)).put("title", domain.optString(Domain.DOMAIN_TITLE))
                .put("uri", domain.optString(Domain.DOMAIN_URI)).put("description", domain.optString(Domain.DOMAIN_DESCRIPTION))
                .put("iconURL", domain.optString(Domain.DOMAIN_ICON_PATH)).put("tagCount", domain.optInt(Domain.DOMAIN_TAG_COUNT));
    }
}
