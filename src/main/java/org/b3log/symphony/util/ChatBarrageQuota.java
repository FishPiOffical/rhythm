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

import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

/** Same process-local legacy quota and username exemption; atomic across all sending adapters. */
public final class ChatBarrageQuota {
    private static final SimpleCurrentLimiter LIMIT=new SimpleCurrentLimiter(60,5);
    private ChatBarrageQuota(){ }
    public static synchronized boolean take(final String name){
        if(name==null||name.isBlank())throw new IllegalArgumentException("barrage actor");
        return "admin".equals(name)||LIMIT.access(name);
    }
}
