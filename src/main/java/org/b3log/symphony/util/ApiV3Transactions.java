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

import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import java.sql.SQLException;

/** Preserve the bundled JDBC transaction's ownership/disposal, but propagate commit failures. */
public final class ApiV3Transactions {
    private ApiV3Transactions() { }
    /** Background and socket callbacks have no HTTP end handler to return thread-local JDBC connections. */
    public static void releaseThreadResources(){
        try{
            final JdbcTransaction transaction=JdbcRepository.TX.get();
            if(transaction!=null&&transaction.isActive())transaction.rollback();
        }finally{JdbcRepository.dispose();}
    }
    public static Transaction strict(final Transaction transaction) {
        if (null == transaction) throw new IllegalArgumentException("transaction required");
        if (!(transaction instanceof JdbcTransaction jdbc)) return transaction;
        return new Transaction() {
            @Override public String getId() { return jdbc.getId(); }
            @Override public boolean isActive() { return jdbc.isActive(); }
            @Override public void rollback() { jdbc.rollback(); }
            @Override public void commit() {
                if (!jdbc.isActive()) throw new IllegalStateException("transaction is not active");
                try {
                    // JdbcTransaction.commit logs and swallows SQLException. Keep its connection
                    // alive on failure so the owning service's rollback/finally still works.
                    jdbc.getConnection().commit();
                } catch (final SQLException e) {
                    throw new IllegalStateException("API v3 transaction commit failed", e);
                }
                jdbc.dispose();
            }
        };
    }
}
