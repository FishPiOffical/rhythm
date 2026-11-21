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

import org.b3log.symphony.model.UserExt;

/** Existing registration/rename contract, shared without session or request dependencies. */
public final class UserNameRules {
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 64;
    private UserNameRules() { }
    public static boolean invalid(final String name) {
        if (null == name || name.length() < MIN_LENGTH || name.length() > MAX_LENGTH
                || "FileTransfer".equals(name) || UserExt.isReservedUserName(name)) return true;
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9') && c != '-') return true;
        }
        return false;
    }
}
