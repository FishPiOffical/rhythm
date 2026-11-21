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
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.LongArticleColumn;

/**
 * Long article column repository.
 *
 * @author Zephyr
 * @since 3.8.0
 */
@Repository
public class LongArticleColumnRepository extends AbstractRepository {

    public LongArticleColumnRepository() {
        super(LongArticleColumn.COLUMN);
    }
    public JSONObject getForUpdate(final String id) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("column transaction required");
        final var rows = select("SELECT * FROM " + getName() + " WHERE oId=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject findByTitleForUpdate(final String owner, final String title) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("column transaction required");
        final var rows = select("SELECT * FROM " + getName() + " WHERE columnAuthorId=? AND columnTitle=? FOR UPDATE", owner, title);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
