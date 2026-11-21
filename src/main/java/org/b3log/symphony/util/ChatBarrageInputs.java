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

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import java.util.Locale;

/** Approved v3 plain-text policy; legacy input/truncation remains in its existing adapter. */
public record ChatBarrageInputs(String content,String color) {
    public static ChatBarrageInputs prepare(final String raw,final String color){
        if(raw==null||raw.length()>4096||raw.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw invalid();
        try{ApiV3Json.canonical(raw);}catch(IllegalArgumentException e){throw invalid();}
        final String text=Jsoup.parseBodyFragment(Jsoup.clean(raw,Safelist.none())).text().strip();
        if(text.isEmpty()||text.length()>32||text.codePoints().anyMatch(c->Character.isISOControl(c)))throw invalid();
        final String paint=color==null||color.isEmpty()?"#FFFFFF":color;
        if(!paint.matches("#[0-9a-fA-F]{6}"))throw invalid();
        return new ChatBarrageInputs(text,paint.toUpperCase(Locale.ROOT));
    }
    private static ApiV3Exception invalid(){return new ApiV3Exception(422,"invalid_barrage","弹幕须为1至32个字符，颜色须为#RRGGBB");}
}
