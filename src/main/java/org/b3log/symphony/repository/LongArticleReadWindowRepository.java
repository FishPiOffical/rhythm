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
import org.b3log.symphony.model.LongArticleRead;
import org.b3log.symphony.model.LongArticleReadWindowIncrement;
import org.b3log.symphony.model.LongArticleReadWindowPage;
import org.json.JSONObject;

import java.util.List;

/** 长篇阅读窗口计数存储。 */
@Repository
public class LongArticleReadWindowRepository extends AbstractRepository {

    private static final String OPEN = "OPEN";
    private static final String SETTLING = "SETTLING";
    private static final String SETTLED = "SETTLED";

    public LongArticleReadWindowRepository() {
        super(LongArticleRead.WINDOW);
    }

    public boolean increment(final LongArticleReadWindowIncrement increment) throws RepositoryException {
        final String sql = "INSERT INTO `" + getName() + "` (`oId`,`articleId`,`windowStart`,`registeredCnt`,"
                + "`anonymousCnt`,`state`,`settlementId`,`settledAt`,`createdAt`,`updatedAt`) VALUES "
                + "(?,?,?,?,?,'OPEN','',0,?,?) ON DUPLICATE KEY UPDATE "
                + "`registeredCnt`=IF(`state`='OPEN',`registeredCnt`+VALUES(`registeredCnt`),`registeredCnt`),"
                + "`anonymousCnt`=IF(`state`='OPEN',`anonymousCnt`+VALUES(`anonymousCnt`),`anonymousCnt`),"
                + "`updatedAt`=IF(`state`='OPEN',VALUES(`updatedAt`),`updatedAt`)";
        return AtomicRepositorySupport.executeUpdate(sql, increment.id(), increment.articleId(),
                increment.windowStart(), increment.registeredIncrement(), increment.anonymousIncrement(),
                increment.occurredAt(), increment.occurredAt()) > 0;
    }

    public boolean startSettlement(final String windowId, final String settlementId, final long now)
            throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `state`=?,`settlementId`=?,`updatedAt`=? "
                + "WHERE `oId`=? AND `state`=?";
        return AtomicRepositorySupport.executeUpdate(sql, SETTLING, settlementId, now, windowId, OPEN) == 1;
    }

    public void completeSettlement(final String windowId, final long now) throws RepositoryException {
        final String sql = "UPDATE `" + getName() + "` SET `state`=?,`settledAt`=?,`updatedAt`=? WHERE `oId`=?";
        AtomicRepositorySupport.executeUpdate(sql, SETTLED, now, now, windowId);
    }

    public List<JSONObject> getOpenBefore(final LongArticleReadWindowPage page) throws RepositoryException {
        final String filter = null == page.articleId() ? "" : "`articleId`=? AND ";
        final String sql = "SELECT * FROM `" + getName() + "` WHERE " + filter + "`state`=? AND `windowStart`<? "
                + "AND (`windowStart`>? OR (`windowStart`=? AND `oId`>?)) ORDER BY `windowStart`,`oId` LIMIT ?";
        return null == page.articleId()
                ? select(sql, OPEN, page.beforeWindowStart(), page.afterWindowStart(), page.afterWindowStart(),
                page.afterId(), page.limit())
                : select(sql, page.articleId(), OPEN, page.beforeWindowStart(), page.afterWindowStart(),
                page.afterWindowStart(), page.afterId(), page.limit());
    }
}
