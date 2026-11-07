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
package org.b3log.symphony.cache;

import org.b3log.latke.cache.Cache;
import org.b3log.latke.cache.CacheFactory;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.model.Membership;
import org.b3log.symphony.util.JSONs;
import org.b3log.symphony.util.MembershipStyleCache;
import java.util.Objects;
import org.json.JSONObject;

/**
 * Membership status cache (keyed by userId).
 */
@Singleton
public class MembershipCache {

    /**
     * Cache name.
     */
    private static final String CACHE_NAME = "membership_status";

    /**
     * Membership cache.
     */
    private static final Cache CACHE = CacheFactory.getCache(CACHE_NAME);

    /**
     * Gets cached active membership by userId.
     *
     * @param userId user id
     * @return membership JSON, or null if not found
     */
    public JSONObject get(final String userId) {
        final JSONObject membership = CACHE.get(userId);
        if (null == membership) {
            return null;
        }
        return JSONs.clone(membership);
    }

    /**
     * Puts an active membership into cache keyed by userId.
     *
     * @param membership membership JSON
     */
    public void put(final JSONObject membership) {
        if (null == membership) {
            return;
        }
        final String userId = membership.optString(Membership.USER_ID);
        if (null == userId || userId.isEmpty()) {
            return;
        }
        final JSONObject previous = CACHE.get(userId);
        final boolean changed = previous == null || java.util.stream.Stream.of("lvCode", "state", "expiresAt", "configJson")
                .anyMatch(field -> !Objects.equals(previous.opt(field), membership.opt(field)));
        CACHE.put(userId, JSONs.clone(membership));
        if (changed) MembershipStyleCache.invalidate();
    }

    /**
     * Removes cached membership by userId.
     *
     * @param userId user id
     */
    public void remove(final String userId) {
        CACHE.remove(userId);
        MembershipStyleCache.invalidate();
    }
}