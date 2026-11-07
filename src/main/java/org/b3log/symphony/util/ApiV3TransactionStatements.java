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
package org.b3log.symphony.util;

import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import java.sql.SQLException;

/** Parameterized repository mutations on the caller's existing transaction, never a new connection. */
public final class ApiV3TransactionStatements {
    private ApiV3TransactionStatements(){ }
    public static int execute(String sql,Object...args)throws RepositoryException {
        final var tx=JdbcRepository.TX.get();
        if(tx==null||!tx.isActive())throw new IllegalStateException("owner transaction required");
        try(final var statement=tx.getConnection().prepareStatement(sql)){
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement.executeUpdate();
        }catch(SQLException e){throw new RepositoryException(e);}
    }
}
