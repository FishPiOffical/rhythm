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

import org.b3log.latke.Keys;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Follow;
import org.json.JSONObject;

import java.util.List;

/** Relationship transition shared by every writer. Transaction ownership stays with the caller. */
public final class FollowMutation {
    private FollowMutation() { }
    public interface Store {
        boolean hasTransaction();
        boolean lockActor(String actor) throws RepositoryException;
        JSONObject lockTarget(String target, int type) throws RepositoryException;
        List<JSONObject> relations(String actor, String target, int type) throws RepositoryException;
        void add(JSONObject relation) throws RepositoryException;
        void remove(String id) throws RepositoryException;
        void adjust(JSONObject target, String id, int type, int delta) throws RepositoryException;
    }
    @FunctionalInterface public interface TargetCheck { void check(JSONObject target) throws RepositoryException; }
    public static boolean set(final Store store, final String actor, final String targetId, final int type,
                              final boolean enabled, final TargetCheck check) throws RepositoryException {
        if (!store.hasTransaction()) throw new IllegalStateException("follow transaction required");
        if (type < Follow.FOLLOWING_TYPE_C_USER || type > Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH) throw new IllegalArgumentException("Unknown follow type");
        if (!store.lockActor(actor)) throw new RepositoryException("Follower not found");
        final JSONObject target = store.lockTarget(targetId, type);
        if (enabled && null != check) check.check(target);
        if (enabled && null == target) return false;
        final List<JSONObject> existing = store.relations(actor, targetId, type);
        if ((enabled && !existing.isEmpty()) || (!enabled && existing.isEmpty())) return false;
        if (enabled) store.add(new JSONObject().put(Follow.FOLLOWER_ID, actor).put(Follow.FOLLOWING_ID, targetId).put(Follow.FOLLOWING_TYPE, type));
        else for (final JSONObject relation : existing) store.remove(relation.optString(Keys.OBJECT_ID));
        if (null != target && type != Follow.FOLLOWING_TYPE_C_USER) store.adjust(target, targetId, type, enabled ? 1 : -existing.size());
        return true;
    }
}
