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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ApiV3Client;
import org.json.JSONObject;
import java.util.List;

@Repository
public class ApiV3ClientRepository extends AbstractRepository {
    public ApiV3ClientRepository() {
        super(ApiV3Client.API_V3_CLIENT);
    }

    public JSONObject getByClientId(final String clientId) throws RepositoryException {
        return getFirst(new Query().setFilter(new PropertyFilter(ApiV3Client.CLIENT_ID,
                FilterOperator.EQUAL, clientId)).setPageCount(1));
    }
    /** All token family writers acquire the client row before token rows, across backend instances. */
    public JSONObject getByClientIdForUpdate(final String clientId) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("client transaction required");
        final List<JSONObject> rows=select("SELECT * FROM `"+getName()+"` WHERE clientId=? FOR UPDATE",clientId);
        if(rows.size()>1)throw new RepositoryException("duplicate v3 client");
        return rows.isEmpty()?null:rows.getFirst();
    }
    /** Password reset precedes user/code/token locks with the same client locks as OAuth writers. */
    public void lockForAccountResetInCurrentTransaction()throws RepositoryException {
        if(!hasTransactionBegun())throw new IllegalStateException("client transaction required");
        select("SELECT clientId FROM `"+getName()+"` ORDER BY clientId FOR UPDATE");
    }
}
