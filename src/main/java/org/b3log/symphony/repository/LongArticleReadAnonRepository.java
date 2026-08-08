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
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.LongArticleReadClaim;
import org.b3log.symphony.model.LongArticleRead;
import org.json.JSONObject;

import java.util.List;

/**
 * Long article read anonymous repository.
 *
 * @author Zephyr
 * @since 3.7.0
 */
@Repository
public class LongArticleReadAnonRepository extends AbstractRepository {

    public LongArticleReadAnonRepository() {
        super(LongArticleRead.ANON);
    }

    public boolean claim(final LongArticleReadClaim claim) throws RepositoryException {
        final String sql = "INSERT INTO `" + getName() + "` (`oId`,`articleId`,`windowStart`,`readerHash`,`firstReadAt`) "
                + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE `oId`=`oId`";
        AtomicRepositorySupport.executeUpdate(sql, claim.id(), claim.articleId(), claim.windowStart(),
                claim.subjectId(), claim.occurredAt());
        final String selectSql = "SELECT `oId` FROM `" + getName() + "` WHERE `articleId`=? "
                + "AND `windowStart`=? AND `readerHash`=?";
        final List<JSONObject> stored = select(selectSql, claim.articleId(), claim.windowStart(), claim.subjectId());
        return !stored.isEmpty() && claim.id().equals(stored.getFirst().getString("oId"));
    }
}
