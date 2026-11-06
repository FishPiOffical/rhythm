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
import org.b3log.symphony.model.ApiV3ClientRedirect;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.json.JSONObject;

@Repository
public class ApiV3ClientRedirectRepository extends AbstractRepository {
    public ApiV3ClientRedirectRepository(){super(ApiV3ClientRedirect.TABLE);}
    public static String identity(final String client,final String uri)throws Exception{return ApiV3TokenUtil.hash("oauth-redirect\0"+client+"\0"+uri);}
    public JSONObject getRegistered(final String client,final String uri)throws Exception {
        return read(client,uri,false);
    }
    public JSONObject getRegisteredForUpdate(final String client,final String uri)throws Exception {
        if(!hasTransactionBegun())throw new IllegalStateException("registered redirect transaction required");
        return read(client,uri,true);
    }
    private JSONObject read(final String client,final String uri,final boolean lock)throws Exception {
        final String key=identity(client,uri);final var rows=select("SELECT * FROM `"+getName()+"` WHERE oId=?"+(lock?" FOR UPDATE":""),key);
        if(rows.size()>1)throw new RepositoryException("duplicate client redirect");
        if(rows.isEmpty())return null;final JSONObject row=rows.getFirst();
        // SQL collations may fold case; the registered identity and complete URI must still match exactly.
        return key.equals(row.optString("oId"))&&client.equals(row.optString(ApiV3ClientRedirect.CLIENT_ID))
                &&uri.equals(row.optString(ApiV3ClientRedirect.REDIRECT_URI))
                &&row.optInt(ApiV3ClientRedirect.STATUS,-1)==ApiV3ClientRedirect.ACTIVE?row:null;
    }
}
