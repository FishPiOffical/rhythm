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
package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.ApiV3PrivateRevisionRepository;
import org.b3log.symphony.util.ApiV3PrivateMessages;
import java.util.*;

/** Caller owns the transaction and savepoint containing both data and these invalidation markers. */
@Service
public class PrivateMessageRevisionService {
    @Inject private ApiV3PrivateRevisionRepository revisions;
    public void touchInCurrentTransaction(Collection<String> owners)throws Exception {
        if(!revisions.hasTransactionBegun())throw new IllegalStateException("private revision transaction required");
        final var ids=new TreeSet<String>();
        for(String owner:owners){ApiV3PrivateMessages.requireId(owner);if(!ApiV3PrivateMessages.FILE_TRANSFER_ID.equals(owner))ids.add(owner);}
        for(String owner:ids)revisions.touchInCurrentTransaction(owner);
    }
}
