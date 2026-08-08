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

import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.jdbc.JdbcRepository;

import java.sql.PreparedStatement;

final class AtomicRepositorySupport {

    private AtomicRepositorySupport() {
    }

    static int executeUpdate(final String sql, final Object... parameters) throws RepositoryException {
        final var transaction = JdbcRepository.TX.get();
        if (null == transaction || !transaction.isActive()) {
            throw new RepositoryException("数据库原子写入需要活动事务");
        }
        try (PreparedStatement statement = transaction.getConnection().prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement.executeUpdate();
        } catch (final Exception e) {
            throw new RepositoryException(e);
        }
    }
}
