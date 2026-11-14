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


import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Milestone;
import org.json.JSONObject;
import java.util.List;

/**
 * Milestone repository.
 *
 * @author rhythm
 * @version 1.0.0.0, Jan 15, 2026
 * @since 3.7.0
 */
@Repository
public class MilestoneRepository extends AbstractRepository {

    /**
     * Public constructor.
     */
    public MilestoneRepository() {
        super(Milestone.MILESTONE);
    }

    private static final String OWNED = " WHERE milestoneAuthorId=? AND milestoneStatus IN (1,2,3)";

    public JSONObject getOwned(final String actor, final String id) throws RepositoryException {
        final var rows = select("SELECT * FROM " + getName() + OWNED + " AND oId=?", actor, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<JSONObject> pageOwned(final String actor, final int status, final int page, final int size) throws RepositoryException {
        return select("SELECT * FROM " + getName() + OWNED + " AND (?=0 OR milestoneStatus=?)"
                + " ORDER BY milestoneCreateTime DESC,oId DESC LIMIT ? OFFSET ?", actor, status, status, size, ((long) page - 1) * size);
    }

    public long countOwned(final String actor, final int status) throws RepositoryException {
        return select("SELECT COUNT(1) AS total FROM " + getName() + OWNED + " AND (?=0 OR milestoneStatus=?)", actor, status, status).getFirst().optLong("total");
    }
}
