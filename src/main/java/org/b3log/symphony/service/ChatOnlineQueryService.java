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

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3CommunityDto;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ChatOnlineSnapshot;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 合并当前本地连接与配置节点的最近采样，按当前账号资料投影名单。
 */
@Service
public class ChatOnlineQueryService {
    private static final Pattern CLASSIC_USER_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    @Inject
    private UserRepository users;
    @Inject
    private ApiV3RealtimeService realtime;
    @Inject
    private AvatarQueryService avatars;

    protected ChatOnlineSnapshot.Sample remote() {
        return ChatOnlineSnapshot.current();
    }

    protected Set<String> localIds() {
        final var ids = new HashSet<>(realtime.chatUserIds());
        for (final var user : ChatroomChannel.onlineUsers.values()) {
            final String id = user.optString(Keys.OBJECT_ID);
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    protected List<JSONObject> lookup(final String column, final List<String> values) throws RepositoryException {
        if (!Set.of("oId", "userName").contains(column))
            throw new IllegalArgumentException("invalid membership column");
        return users.select("SELECT oId,userName,userAvatarURL,userOnlineStatus,userStatus FROM `" + users.getName()
                + "` WHERE " + column + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")", values.toArray());
    }

    private void load(final String column, final Collection<String> membership, final Map<String, JSONObject> result) throws RepositoryException {
        final var values = new ArrayList<>(new HashSet<>(membership));
        for (int start = 0; start < values.size(); start += 200) {
            for (final var user : lookup(column, values.subList(start, Math.min(start + 200, values.size())))) {
                result.put(user.getString(Keys.OBJECT_ID), user);
            }
        }
    }

    private List<JSONObject> visibleMembers(final ChatOnlineSnapshot.Sample sample, final Predicate<JSONObject> visibility)
            throws RepositoryException {
        final Map<String, JSONObject> current = new HashMap<>();
        // 将节点用户名和本机连接 ID 解析成当前账号记录，按账号 ID 合并去重。
        load("userName", sample.names(), current);
        load("oId", localIds(), current);
        return current.values().stream()
                .filter(user -> user.optInt(UserExt.USER_STATUS, -1) == UserExt.USER_STATUS_C_VALID)
                .filter(visibility)
                .sorted(Comparator.comparing((JSONObject user) -> user.getString(User.USER_NAME)).thenComparing(user -> user.getString(Keys.OBJECT_ID)))
                .toList();
    }

    public JSONObject classicBroadcast(final String topic) throws RepositoryException {
        final var members = new JSONArray();
        final var visible = visibleMembers(remote(), user ->
                user.optInt(UserExt.USER_ONLINE_STATUS, -1) == UserExt.USER_XXX_STATUS_C_ENABLED);
        for (final var user : visible) {
            final String name = user.getString(User.USER_NAME);
            if (!CLASSIC_USER_NAME.matcher(name).matches()) {
                throw new IllegalStateException("Invalid classic online username for account " + user.getString(Keys.OBJECT_ID));
            }
            final var member = new JSONObject()
                    .put(User.USER_NAME, name)
                    .put(UserExt.USER_AVATAR_URL, ApiV3CommunityDto.safeUrl(user.optString(UserExt.USER_AVATAR_URL)))
                    .put("homePage", Latkes.getStaticServePath() + "/member/" + name);
            avatars.fillUserAvatarURL(member);
            members.put(member);
        }
        return new JSONObject().put(Common.TYPE, "online").put(Common.ONLINE_CHAT_CNT, members.length())
                .put("users", members).put("discussing", topic);
    }

    public JSONObject list(final JSONObject viewer, final int page, final int size) throws RepositoryException {
        if (page < 1 || page > Integer.MAX_VALUE / 50 || size < 1 || size > 50) {
            throw new ApiV3Exception(422, "invalid_parameter", "请求参数不合法");
        }
        final var sample = remote();
        final var visible = visibleMembers(sample, user ->
                ApiV3ContentUtil.canViewUserList(user, viewer, UserExt.USER_ONLINE_STATUS));
        final int start = Math.min((page - 1) * size, visible.size());
        final var list = new JSONArray();
        for (final var user : visible.subList(start, Math.min(start + size, visible.size()))) {
            list.put(new JSONObject().put("id", user.getString(Keys.OBJECT_ID)).put("userName", user.getString(User.USER_NAME))
                    .put("userAvatarURL", ApiV3CommunityDto.safeUrl(user.optString(UserExt.USER_AVATAR_URL))));
        }
        return ApiV3Requests.pagination(list, page, size, visible.size())
                .put("hasMore", start + list.length() < visible.size()).put("nodesSampledAt", sample.sampledAt()).put("nodesComplete", sample.complete());
    }
}
