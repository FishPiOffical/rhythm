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
import org.b3log.latke.repository.Transaction;
import org.json.JSONObject;

/**
 * 存储接口用于隔离验证事务语义，无需启动数据库或应用。
 */
public interface ApiV3MutationStore {
    Transaction beginTransaction();

    boolean hasTransactionBegun();

    JSONObject getForUpdate(String id) throws RepositoryException;

    String add(JSONObject row) throws RepositoryException;

    void update(String id, JSONObject row, String... propertyNames) throws RepositoryException;
}
