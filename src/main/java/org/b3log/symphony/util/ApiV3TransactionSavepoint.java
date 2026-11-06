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

import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import java.sql.Savepoint;
import java.util.concurrent.Callable;

/** Local all-or-nothing boundary inside an already owned JDBC transaction. Never commits its owner. */
public final class ApiV3TransactionSavepoint {
    private ApiV3TransactionSavepoint(){ }
    public static <T>T run(final Callable<T> work)throws Exception {
        final JdbcTransaction transaction=JdbcRepository.TX.get();
        if(transaction==null||!transaction.isActive())throw new IllegalStateException("owner transaction required");
        final var connection=transaction.getConnection();final Savepoint point=connection.setSavepoint();
        try{
            final T result=work.call();
            if(!transaction.isActive()||JdbcRepository.TX.get()!=transaction)throw new IllegalStateException("owner transaction ended inside savepoint");
            connection.releaseSavepoint(point);return result;
        }catch(Exception|Error error){
            // An inner primitive may already have aborted and disposed this root.
            // Preserve its failure rather than touching the closed connection again.
            if(!transaction.isActive()||JdbcRepository.TX.get()!=transaction)throw error;
            try{connection.rollback(point);}
            catch(Exception|Error failure){
                error.addSuppressed(failure);
                try{transaction.rollback();}catch(Exception|Error rollbackFailure){error.addSuppressed(rollbackFailure);}
                throw error;
            }
            try{connection.releaseSavepoint(point);}catch(Exception|Error failure){error.addSuppressed(failure);}
            throw error;
        }
    }
}
