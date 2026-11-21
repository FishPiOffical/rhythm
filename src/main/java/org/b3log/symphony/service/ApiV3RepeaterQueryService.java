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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.RepeaterLikeRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3RepeaterDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 列表按热度展示，随机发现覆盖全部有效复读内容。
 */
@Service
public class ApiV3RepeaterQueryService {
    @Inject
    private RepeaterContentRepository contents;
    @Inject
    private RepeaterLikeRepository likes;
    @Inject
    private UserRepository users;

    private void actor(final String actor) throws RepositoryException {
        if (actor.isEmpty()) return;
        if (!actor.matches("[0-9]{1,32}")) throw ApiV3Requests.invalid();
        if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=? LIMIT 2", actor, UserExt.USER_STATUS_C_VALID).size() != 1)
            throw ApiV3Requests.notFound();
    }

    private List<JSONObject> pool(final String actor, final String type, final int size) throws RepositoryException {
        final String columns = columns();
        return contents.select(columns + " FROM `" + contents.getName() + "` c LEFT JOIN `" + users.getName() + "` u ON u.oId=c.repeaterContentAuthorId WHERE c.repeaterContentStatus=0"
                        + (type.isEmpty() ? "" : " AND c.repeaterContentType=?") + " ORDER BY c.repeaterContentLikeCount DESC,c.repeaterContentCreatedTime DESC,c.oId DESC LIMIT ?",
                type.isEmpty() ? new Object[]{actor, actor, size} : new Object[]{actor, actor, type, size});
    }

    private String columns() {
        return "SELECT c.oId AS id,c.repeaterContentType AS type,SUBSTR(c.repeaterContent,1,501) AS content,c.repeaterContentLikeCount AS likeCount,c.repeaterContentCreatedTime AS createdTime,"
                + "CASE WHEN u.userStatus=0 THEN u.oId ELSE '' END AS authorId,CASE WHEN u.userStatus=0 THEN SUBSTR(u.userName,1,65) ELSE '' END AS authorName,"
                + "CASE WHEN ?<>'' AND EXISTS(SELECT 1 FROM `" + likes.getName() + "` k WHERE k.repeaterLikeContentId=c.oId AND k.repeaterLikeUserId=?) THEN 1 ELSE 0 END AS liked";
    }

    public JSONObject items(final String actor, final String type, final int size) {
        if (actor == null || type == null || !type.isEmpty() && !RepeaterContent.isSupportedType(type) || size < 1 || size > 50)
            throw ApiV3Requests.invalid();
        try {
            actor(actor);
            final List<JSONObject> rows = pool(actor, type, size);
            final JSONArray items = new JSONArray();
            final java.util.Set<String> seen = new java.util.HashSet<>();
            for (final JSONObject row : rows) {
                final JSONObject item = ApiV3RepeaterDto.item(row);
                if (!seen.add(item.getString("id")))
                    throw ApiV3RepeaterDto.unavailable();
                items.put(item);
            }
            // 返回之前复查候选池及读者，发现查询间状态变化时拒绝旧快照。
            actor(actor);
            if (!new JSONArray(rows).similar(new JSONArray(pool(actor, type, size))))
                throw ApiV3RepeaterDto.unavailable();
            actor(actor);
            return new JSONObject().put("items", items);
        } catch (final RepositoryException e) {
            throw ApiV3RepeaterDto.unavailable();
        }
    }

    public JSONObject next(final String actor, final String type, final String excludeId) {
        if (excludeId == null || !excludeId.isEmpty() && !excludeId.matches("[0-9]{1,32}"))
            throw ApiV3Requests.invalid();
        if (actor == null || type == null || !type.isEmpty() && !RepeaterContent.isSupportedType(type))
            throw ApiV3Requests.invalid();
        try {
            actor(actor);
            final String where = " WHERE c.repeaterContentStatus=0"
                    + (type.isEmpty() ? "" : " AND c.repeaterContentType=?") + " AND c.oId<>?";
            final var args = new java.util.ArrayList<Object>();
            if (!type.isEmpty()) args.add(type);
            args.add(excludeId);
            final long count = contents.select("SELECT COUNT(*) AS total FROM `" + contents.getName() + "` c" + where,
                    args.toArray()).getFirst().getLong("total");
            if (count == 0) return new JSONObject().put("item", JSONObject.NULL);
            final var readArgs = new java.util.ArrayList<Object>(List.of(actor, actor));
            readArgs.addAll(args);
            readArgs.add(ThreadLocalRandom.current().nextLong(count));
            final String from = " FROM `" + contents.getName() + "` c LEFT JOIN `" + users.getName()
                    + "` u ON u.oId=c.repeaterContentAuthorId";
            final var rows = contents.select(columns() + from + where + " ORDER BY c.repeaterContentLikeCount,c.oId LIMIT 1 OFFSET ?", readArgs.toArray());
            if (rows.isEmpty()) return new JSONObject().put("item", JSONObject.NULL);
            final JSONObject item = ApiV3RepeaterDto.item(rows.getFirst());
            final var current = contents.select(columns() + from + " WHERE c.oId=? AND c.repeaterContentStatus=0",
                    actor, actor, item.getString("id"));
            actor(actor);
            if (current.size() != 1 || !item.similar(ApiV3RepeaterDto.item(current.getFirst()))) throw ApiV3RepeaterDto.unavailable();
            return new JSONObject().put("item", item);
        } catch (final RepositoryException e) {
            throw ApiV3RepeaterDto.unavailable();
        }
    }
}
