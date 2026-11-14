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
import org.b3log.symphony.model.ApiV3ChatEvent;

@Repository
public class ApiV3ChatStateRepository extends AbstractRepository {
    public ApiV3ChatStateRepository(){super(ApiV3ChatEvent.STATE_REPOSITORY);}
    public long head()throws RepositoryException {
        if(hasTransactionBegun())throw new IllegalStateException("chat snapshot inside writer transaction");
        return revision(false);
    }
    public long lockHeadInCurrentTransaction()throws RepositoryException {
        if(!hasTransactionBegun())throw new IllegalStateException("chat event transaction required");
        return revision(true);
    }
    private long revision(boolean lock)throws RepositoryException {
        final var rows=select("SELECT revision FROM `"+getName()+"` WHERE oId=?"+(lock?" FOR UPDATE":""),ApiV3ChatEvent.ROOM);
        if(rows.size()!=1)throw new RepositoryException("chat event state unavailable");
        final long revision;
        try{revision=rows.getFirst().getLong("revision");}
        catch(RuntimeException e){throw new RepositoryException(e);}
        if(revision<0)throw new RepositoryException("invalid chat event state");
        return revision;
    }
}
