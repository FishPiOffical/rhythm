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
import org.b3log.symphony.model.UserProfessionContribution;

/** 用户职业贡献聚合存储。 */
@Repository
public class UserProfessionContributionRepository extends AbstractRepository {

    public UserProfessionContributionRepository() {
        super(UserProfessionContribution.USER_PROFESSION_CONTRIBUTION);
    }

    public void increment(final ProfessionContributionIncrement increment) throws RepositoryException {
        final String sql = "INSERT INTO `" + getName() + "` (`oId`,`userId`,`professionId`,`actionCode`,"
                + "`experienceSum`,`eventCount`,`lastOccurredAt`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE `experienceSum`=`experienceSum`+VALUES(`experienceSum`),"
                + "`eventCount`=`eventCount`+VALUES(`eventCount`),`lastOccurredAt`=GREATEST(`lastOccurredAt`,VALUES(`lastOccurredAt`)),"
                + "`updatedAt`=VALUES(`updatedAt`)";
        AtomicRepositorySupport.executeUpdate(sql, increment.id(), increment.userId(), increment.professionId(),
                increment.actionCode(), increment.experienceDelta(), increment.eventCountDelta(), increment.occurredAt(),
                increment.now(), increment.now());
    }
}
