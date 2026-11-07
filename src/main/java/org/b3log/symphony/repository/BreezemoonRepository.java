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
package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Follow;

/**
 * Breezemoon repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, May 21, 2018
 * @since 2.8.0
 */
@Repository
public class BreezemoonRepository extends AbstractRepository {

    /**
     * Public constructor.
     */
    public BreezemoonRepository() {
        super(Breezemoon.BREEZEMOON);
    }
    public JSONObject getOwnedForUpdate(final String userId, final String id) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("breezemoon transaction required");
        final List<JSONObject> rows = select("SELECT * FROM " + getName() + " WHERE oId=? AND breezemoonAuthorId=? FOR UPDATE", id, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public JSONObject getForUpdate(final String id) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("breezemoon transaction required");
        final List<JSONObject> rows = select("SELECT * FROM " + getName() + " WHERE oId=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Page getV3FollowingPage(final String usersTable, final String followsTable, final String actor, final int p, final int size,
                                  final int invalidUser, final int deactivatedUser, final int publicStatus) throws RepositoryException {
        final String from = " FROM " + getName() + " b JOIN " + usersTable + " u ON u.oId=b.breezemoonAuthorId"
                + " WHERE b.breezemoonStatus=? AND u.userStatus NOT IN (?,?) AND u.userBreezemoonStatus=?"
                + " AND b.breezemoonAuthorId IN (SELECT f.followingId FROM " + followsTable + " f WHERE f.followerId=? AND f.followingType=?)";
        return page(from, new ArrayList<>(List.of(Breezemoon.BREEZEMOON_STATUS_C_VALID, invalidUser, deactivatedUser,
                publicStatus, actor, Follow.FOLLOWING_TYPE_C_USER)), p, size);
    }

    public record Page(List<JSONObject> rows, long total) { }

    /** A non-null owner is only used after the caller has checked that owner's list privacy. */
    public Page getV3Page(final String usersTable, final String owner, final int p, final int size,
                         final int invalidUser, final int deactivatedUser, final int publicStatus) throws RepositoryException {
        final String from = " FROM " + getName() + " b JOIN " + usersTable + " u ON u.oId=b.breezemoonAuthorId"
                + " WHERE b.breezemoonStatus=? AND u.userStatus NOT IN (?,?)"
                + (null == owner ? " AND u.userBreezemoonStatus=?" : " AND b.breezemoonAuthorId=?");
        final List<Object> args = new ArrayList<>(List.of(Breezemoon.BREEZEMOON_STATUS_C_VALID, invalidUser, deactivatedUser));
        args.add(null == owner ? publicStatus : owner);
        return page(from, args, p, size);
    }

    private Page page(final String from, final List<Object> args, final int p, final int size) throws RepositoryException {
        final long total = select("SELECT COUNT(1) AS total" + from, args.toArray()).getFirst().optLong("total");
        args.add((p - 1) * size); args.add(size);
        return new Page(select("SELECT b.*" + from + " ORDER BY b.oId DESC LIMIT ?,?", args.toArray()), total);
    }
}
