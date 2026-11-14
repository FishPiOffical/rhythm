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

import org.b3log.latke.Keys;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Reward;
import org.json.JSONObject;
import java.util.List;

/**
 * Reward repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.0.0, Sep 15, 2018
 * @since 1.3.0
 */
@Repository
public class RewardRepository extends AbstractRepository {

    @Override
    public JSONObject get(String id) throws RepositoryException {
        JSONObject result = super.get(id);
        if (result == null) {
            return new JSONObject();
        }
        return result;
    }

    /** Current locking read; a failed query must never authorize article removal. */
    public boolean existsForUpdate(final String dataId, final int type) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("reward locking read requires transaction");
        return !select("SELECT oId FROM `" + getName() + "` WHERE dataId=? AND `type`=? LIMIT 1 FOR UPDATE", dataId, type).isEmpty();
    }

    /**
     * Removes rewards for one typed target inside the caller's transaction.
     *
     * @param dataId the specified data id
     * @param type the reward kind from Reward.TYPE_C_*
     * @return removed reward record IDs for associated notification cleanup in the same transaction
     * @throws RepositoryException repository exception
     */
    public List<String> removeByDataId(final String dataId, final int type) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("reward removal requires transaction");
        if (type != Reward.TYPE_C_ARTICLE && type != Reward.TYPE_C_THANK_ARTICLE
                && type != Reward.TYPE_C_ACCEPT_COMMENT && type != Reward.TYPE_C_COMMENT && type != Reward.TYPE_C_USER) {
            throw new IllegalArgumentException("unknown reward target type");
        }
        final var rows = select("SELECT oId FROM `" + getName()
                + "` WHERE dataId=? AND `type`=? ORDER BY oId FOR UPDATE", dataId, type);
        for (final JSONObject row : rows) remove(row.getString(Keys.OBJECT_ID));
        return rows.stream().map(row -> row.getString(Keys.OBJECT_ID)).toList();
    }

    /**
     * Public constructor.
     */
    public RewardRepository() {
        super(Reward.REWARD);
    }
}
