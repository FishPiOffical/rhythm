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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.model.UserProfessionPrivacy;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 读取遵从用户职业隐私设置的公开职业榜单。 */
@Service
public class ProfessionRankingQueryService {

    private static final int RANKING_LIMIT = 20;

    @Inject private ProfessionCatalogQueryService catalogQueryService;
    @Inject private ProfessionPrivacyMgmtService privacyMgmtService;
    @Inject private UserProfessionQueryService userProfessionQueryService;
    @Inject private UserQueryService userQueryService;
    @Inject private AvatarQueryService avatarQueryService;

    public RankingPage getPage(final String requestedProfessionId, final String viewerUserId, final boolean darkMode)
            throws RepositoryException {
        final List<JSONObject> definitions = definitions();
        final JSONObject selected = select(definitions, requestedProfessionId);
        return new RankingPage(definitions, selected, entries(selected.getString("professionId"), viewerUserId, darkMode));
    }

    private List<JSONObject> definitions() throws RepositoryException {
        final List<JSONObject> result = new ArrayList<>();
        final JSONArray values = catalogQueryService.getPublished();
        for (final Object value : values) result.add((JSONObject) value);
        return result;
    }

    private JSONObject select(final List<JSONObject> definitions, final String requestedProfessionId) {
        if (definitions.isEmpty()) throw new IllegalArgumentException("暂无已发布职业");
        if (null == requestedProfessionId || requestedProfessionId.isBlank()) return definitions.getFirst();
        return definitions.stream().filter(item -> requestedProfessionId.equals(item.optString("professionId"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("职业不存在或尚未发布"));
    }

    private List<JSONObject> entries(final String professionId, final String viewerUserId, final boolean darkMode)
            throws RepositoryException {
        final List<JSONObject> result = new ArrayList<>();
        int rank = 0;
        for (final JSONObject progress : userProfessionQueryService.getRanking(
                new UserProfessionQueryService.RankingQuery(professionId, 0L, "", RANKING_LIMIT))) {
            rank++;
            final String userId = progress.getString(UserProfession.USER_ID);
            final JSONObject visibility = visibility(userId);
            if (!visible(visibility.optString("ranking"), userId, viewerUserId)) continue;
            final JSONObject user = userQueryService.getUser(userId);
            if (null == user || "_".equals(user.optString(User.USER_NAME))) continue;
            result.add(entry(progress, user, visibility, viewerUserId, rank, darkMode));
        }
        return result;
    }

    private JSONObject visibility(final String userId) throws RepositoryException {
        final JSONObject privacy = privacyMgmtService.get(userId);
        return new JSONObject(privacy.getString(UserProfessionPrivacy.MODULE_VISIBILITY_JSON));
    }

    private JSONObject entry(final JSONObject progress, final JSONObject user, final JSONObject visibility,
                             final String viewerUserId, final int rank, final boolean darkMode) throws RepositoryException {
        avatarQueryService.fillUserAvatarURL(user);
        final JSONObject view = catalogQueryService.getProgressView(progress, "ranking", darkMode);
        final JSONObject presentation = view.optJSONObject("presentation");
        final String userName = user.optString(User.USER_NAME);
        final String userNickname = user.optString(UserExt.USER_NICKNAME).isBlank()
                ? userName : user.optString(UserExt.USER_NICKNAME);
        final JSONObject result = new JSONObject()
                .put("rank", rank)
                .put("userName", userName)
                .put("userNickname", userNickname)
                .put("userAvatarURL48", user.optString(UserExt.USER_AVATAR_URL + "48"))
                .put("professionName", view.optString("displayName"))
                .put("levelName", view.optString("levelName"))
                .put("iconUrl", image(presentation))
                .put("primaryColor", null == presentation ? "#2563eb"
                        : presentation.optString("primaryColor", "#2563eb"));
        if (visible(visibility.optString("experience"), progress.getString(UserProfession.USER_ID), viewerUserId)) {
            result.put("totalExperience", view.optLong("totalExperience"));
        }
        return result;
    }

    private String image(final JSONObject presentation) {
        if (null == presentation) return "";
        final String iconUrl = presentation.optString("iconUrl");
        if (!iconUrl.isBlank()) return iconUrl;
        return presentation.optString("imageUrl", presentation.optString("badgeUrl"));
    }

    private boolean visible(final String scope, final String targetUserId, final String viewerUserId) {
        return switch (scope) {
            case "PUBLIC" -> true;
            case "LOGGED_IN" -> null != viewerUserId && !viewerUserId.isBlank();
            case "SELF" -> targetUserId.equals(viewerUserId);
            default -> false;
        };
    }

    public record RankingPage(List<JSONObject> definitions, JSONObject selected, List<JSONObject> entries) {
    }
}
