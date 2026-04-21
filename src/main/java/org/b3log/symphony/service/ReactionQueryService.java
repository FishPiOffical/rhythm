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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ReactionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reaction query service.
 */
@Service
public class ReactionQueryService {

    private static final Logger LOGGER = LogManager.getLogger(ReactionQueryService.class);

    @Inject
    private ReactionRepository reactionRepository;

    @Inject
    private UserQueryService userQueryService;

    @Inject
    private AvatarQueryService avatarQueryService;

    public void fillCommentReaction(final JSONObject comment, final String currentUserId) {
        fillReaction(comment, Reaction.TARGET_TYPE_COMMENT, currentUserId);
    }

    public void fillCommentReactions(final List<JSONObject> comments, final String currentUserId) {
        fillReactions(comments, Reaction.TARGET_TYPE_COMMENT, currentUserId);
    }

    public void fillChatReaction(final JSONObject message, final String currentUserId) {
        fillReaction(message, Reaction.TARGET_TYPE_CHAT, currentUserId);
    }

    public void fillArticleReaction(final JSONObject article, final String currentUserId) {
        fillReaction(article, Reaction.TARGET_TYPE_ARTICLE, currentUserId);
    }

    public void fillChatReactions(final List<JSONObject> messages, final String currentUserId) {
        fillReactions(messages, Reaction.TARGET_TYPE_CHAT, currentUserId);
    }

    public JSONObject buildOperationResult(final String targetType, final String targetId, final String currentUserId) {
        final List<JSONObject> reactions = getTargetReactions(targetType, targetId);
        final Map<String, JSONObject> userProfiles = buildUserProfiles(reactions);
        final JSONObject result = new JSONObject();
        result.put(Reaction.RESPONSE_TARGET_ID, targetId);
        result.put(Reaction.RESPONSE_TARGET_TYPE, targetType);
        result.put(Reaction.RESPONSE_GROUP_TYPE, Reaction.GROUP_EMOJI);
        result.put(Reaction.FIELD_CURRENT_USER_REACTION, findCurrentUserReaction(reactions, currentUserId));
        result.put(Reaction.RESPONSE_SUMMARY, buildSummary(reactions, currentUserId, userProfiles));
        return result;
    }

    public JSONArray getSummary(final String targetType, final String targetId, final String currentUserId) {
        final List<JSONObject> reactions = getTargetReactions(targetType, targetId);
        return buildSummary(reactions, currentUserId, buildUserProfiles(reactions));
    }

    private void fillReaction(final JSONObject target, final String targetType, final String currentUserId) {
        if (target == null) {
            return;
        }
        initReactionFields(target);
        final String targetId = target.optString(Keys.OBJECT_ID);
        if (StringUtils.isBlank(targetId)) {
            return;
        }
        final List<JSONObject> reactions = getTargetReactions(targetType, targetId);
        applyReaction(target, reactions, currentUserId, buildUserProfiles(reactions));
    }

    private void fillReactions(final List<JSONObject> targets, final String targetType, final String currentUserId) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        final List<String> targetIds = new ArrayList<>();
        for (final JSONObject target : targets) {
            initReactionFields(target);
            final String targetId = target.optString(Keys.OBJECT_ID);
            if (StringUtils.isNotBlank(targetId)) {
                targetIds.add(targetId);
            }
        }
        if (targetIds.isEmpty()) {
            return;
        }
        final List<JSONObject> reactions = getTargetReactions(targetType, targetIds);
        final Map<String, List<JSONObject>> grouped = groupByTarget(reactions);
        final Map<String, JSONObject> userProfiles = buildUserProfiles(reactions);
        for (final JSONObject target : targets) {
            applyReaction(target, grouped.get(target.optString(Keys.OBJECT_ID)), currentUserId, userProfiles);
        }
    }

    private void applyReaction(final JSONObject target, final List<JSONObject> reactions, final String currentUserId,
                               final Map<String, JSONObject> userProfiles) {
        final List<JSONObject> safeReactions = reactions == null ? new ArrayList<JSONObject>() : reactions;
        target.put(Reaction.FIELD_CURRENT_USER_REACTION, findCurrentUserReaction(safeReactions, currentUserId));
        target.put(Reaction.FIELD_SUMMARY, buildSummary(safeReactions, currentUserId, userProfiles).toList());
    }

    private void initReactionFields(final JSONObject target) {
        target.put(Reaction.FIELD_SUMMARY, new ArrayList<>());
        target.put(Reaction.FIELD_CURRENT_USER_REACTION, "");
    }

    private List<JSONObject> getTargetReactions(final String targetType, final String targetId) {
        final List<String> targetIds = new ArrayList<>();
        targetIds.add(targetId);
        final List<JSONObject> reactions = getTargetReactions(targetType, targetIds);
        return reactions == null ? new ArrayList<JSONObject>() : reactions;
    }

    private List<JSONObject> getTargetReactions(final String targetType, final Collection<String> targetIds) {
        try {
            return reactionRepository.getByTargetsGroup(targetType, targetIds, Reaction.GROUP_EMOJI);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get reactions failed", e);
            return new ArrayList<>();
        }
    }

    private Map<String, List<JSONObject>> groupByTarget(final List<JSONObject> reactions) {
        final Map<String, List<JSONObject>> grouped = new HashMap<>();
        for (final JSONObject reaction : reactions) {
            final String targetId = reaction.optString(Reaction.TARGET_ID);
            List<JSONObject> targetReactions = grouped.get(targetId);
            if (targetReactions == null) {
                targetReactions = new ArrayList<>();
                grouped.put(targetId, targetReactions);
            }
            targetReactions.add(reaction);
        }
        return grouped;
    }

    private String findCurrentUserReaction(final List<JSONObject> reactions, final String currentUserId) {
        if (StringUtils.isBlank(currentUserId)) {
            return "";
        }
        for (final JSONObject reaction : reactions) {
            if (StringUtils.equals(currentUserId, reaction.optString(Reaction.USER_ID))) {
                return reaction.optString(Reaction.VALUE);
            }
        }
        return "";
    }

    private JSONArray buildSummary(final List<JSONObject> reactions, final String currentUserId,
                                   final Map<String, JSONObject> userProfiles) {
        final Map<String, Integer> counts = countByValue(reactions);
        final Map<String, List<JSONObject>> userDetailsByValue = collectUserDetailsByValue(reactions, userProfiles);
        final String currentUserReaction = findCurrentUserReaction(reactions, currentUserId);
        final JSONArray summary = new JSONArray();
        for (final String value : Reaction.emojiValues()) {
            final int count = counts.getOrDefault(value, 0);
            if (count < 1) {
                continue;
            }
            summary.put(buildSummaryItem(value, count, currentUserReaction, userDetailsByValue.get(value)));
        }
        return summary;
    }

    private Map<String, Integer> countByValue(final List<JSONObject> reactions) {
        final Map<String, Integer> counts = new HashMap<>();
        for (final JSONObject reaction : reactions) {
            final String value = reaction.optString(Reaction.VALUE);
            counts.put(value, counts.getOrDefault(value, 0) + 1);
        }
        return counts;
    }

    private Map<String, List<JSONObject>> collectUserDetailsByValue(final List<JSONObject> reactions,
                                                                    final Map<String, JSONObject> userProfiles) {
        final Map<String, List<JSONObject>> usersByValue = new HashMap<>();
        for (final JSONObject reaction : reactions) {
            final JSONObject userProfile = userProfiles.get(reaction.optString(Reaction.USER_ID));
            if (userProfile == null || StringUtils.isBlank(userProfile.optString("displayName"))) {
                continue;
            }
            final String value = reaction.optString(Reaction.VALUE);
            List<JSONObject> users = usersByValue.get(value);
            if (users == null) {
                users = new ArrayList<>();
                usersByValue.put(value, users);
            }
            users.add(userProfile);
        }
        return usersByValue;
    }

    private Map<String, JSONObject> buildUserProfiles(final List<JSONObject> reactions) {
        final Map<String, JSONObject> userProfiles = new HashMap<>();
        for (final JSONObject reaction : reactions) {
            final String userId = reaction.optString(Reaction.USER_ID);
            if (StringUtils.isBlank(userId) || userProfiles.containsKey(userId)) {
                continue;
            }
            userProfiles.put(userId, buildUserProfile(userId));
        }
        return userProfiles;
    }

    private JSONObject buildUserProfile(final String userId) {
        final JSONObject profile = new JSONObject();
        final JSONObject user = userQueryService.getUser(userId);
        if (user == null) {
            return profile;
        }
        profile.put("userName", user.optString(User.USER_NAME));
        profile.put("displayName", resolveUserDisplayName(user));
        profile.put("avatarURL", avatarQueryService.getAvatarURLByUser(user, "48"));
        return profile;
    }

    private String resolveUserDisplayName(final JSONObject user) {
        final String userName = user.optString(User.USER_NAME);
        final String nickname = user.optString(UserExt.USER_NICKNAME);
        if (StringUtils.isBlank(nickname) || StringUtils.equals(nickname, userName)) {
            return userName;
        }
        return nickname + " (" + userName + ")";
    }

    private List<String> extractDisplayNames(final List<JSONObject> userDetails) {
        final List<String> users = new ArrayList<>();
        if (userDetails == null) {
            return users;
        }
        for (final JSONObject userDetail : userDetails) {
            final String displayName = userDetail.optString("displayName");
            if (StringUtils.isNotBlank(displayName)) {
                users.add(displayName);
            }
        }
        return users;
    }

    private JSONObject buildSummaryItem(final String value, final int count, final String currentUserReaction,
                                        final List<JSONObject> userDetails) {
        final JSONObject item = new JSONObject();
        item.put("value", value);
        item.put("emoji", Reaction.emojiOf(value));
        item.put("count", count);
        item.put("selected", StringUtils.equals(value, currentUserReaction));
        item.put("users", extractDisplayNames(userDetails));
        item.put("userDetails", userDetails == null ? new JSONArray() : new JSONArray(userDetails));
        return item;
    }
}
