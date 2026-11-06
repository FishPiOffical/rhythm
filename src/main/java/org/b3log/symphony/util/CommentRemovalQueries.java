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


/** Locking read statements used by comment removal. Every external value is bound separately. */
public final class CommentRemovalQueries {
    private CommentRemovalQueries() { }
    private static String table(final String name) {
        if (null == name || !name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("invalid internal table identifier");
        return "`" + name + "`";
    }
    public static String replies(final String name) {
        return "SELECT oId FROM " + table(name) + " WHERE commentOriginalCommentId=? LIMIT 1 FOR UPDATE";
    }
    public static String votes(final String name) {
        return "SELECT oId FROM " + table(name) + " WHERE dataId=? AND dataType=? LIMIT 1 FOR UPDATE";
    }
    public static String latest(final String name) {
        return "SELECT oId,commentAuthorId,commentAnonymous FROM " + table(name)
                + " WHERE commentOnArticleId=? ORDER BY oId DESC LIMIT 1 FOR UPDATE";
    }
    public static String revisions(final String name) {
        return "SELECT oId FROM " + table(name) + " WHERE revisionDataId=? AND revisionDataType=? ORDER BY oId FOR UPDATE";
    }
    public static String relations(final String name) {
        return "SELECT oId FROM " + table(name) + " WHERE article_oId=? ORDER BY oId FOR UPDATE";
    }
    public static String userName(final String name) {
        return "SELECT userName FROM " + table(name) + " WHERE oId=? LIMIT 1";
    }
}
