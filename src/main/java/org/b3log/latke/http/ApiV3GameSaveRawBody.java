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
package org.b3log.latke.http;

/** Narrow compatibility adapter for bundled Latke: parseForm normalizes getBytes() through lossy UTF-8. */
public final class ApiV3GameSaveRawBody {
    private ApiV3GameSaveRawBody() { }
    public static final class TooLarge extends IllegalArgumentException {
        private TooLarge(){super("request body too large");}
    }
    /** Copy original Netty bytes without moving its reader index or retaining the buffer. */
    public static byte[] copy(Request request,int maximum) {
        if(request==null||request.req==null||maximum<0)throw new IllegalStateException("raw request body unavailable");
        final var body=request.req.content();final int length=body.readableBytes();
        if(length>maximum)throw new TooLarge();final byte[] result=new byte[length];body.getBytes(body.readerIndex(),result);return result;
    }
}
