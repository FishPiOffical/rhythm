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

import org.json.JSONObject;
import org.jsoup.Jsoup;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.function.Function;

/** Private DTOs are projected only after participant authorization; no legacy session/envelope fields. */
public final class ApiV3PrivateMessages {
    public static final String READ_SCOPE="messages.read";
    public static final String FILE_TRANSFER_ID="1000000000086";
    public static final String FILE_TRANSFER_AVATAR="https://file.fishpi.cn/2022/06/e1541bfe4138c144285f11ea858b6bf6-ba777366.jpeg";
    public static final String ID_PATTERN="^[1-9][0-9]{0,18}$";
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final int MAX_CONTENT=10240;
    private ApiV3PrivateMessages(){ }
    public static boolean validId(String value){return value!=null&&value.matches(ID_PATTERN);}
    public static void requireId(String value){if(!validId(value))throw ApiV3WriteInputs.invalid();}
    public static JSONObject message(JSONObject row,String actor,Function<String,String> render){
        final String from=row.optString("fromId"),to=row.optString("toId"),id=row.optString("oId");
        if(!validId(id)||!validId(from)||!validId(to)||from.equals(to)||!actor.equals(from)&&!actor.equals(to))throw ApiV3Requests.notFound();
        final var dto=new JSONObject().put("id",id).put("fromId",from).put("toId",to).put("fromMe",actor.equals(from))
                .put("sentAt",time(row.optString("time"))).put("content","").put("contentHtml","").put("preview","").put("available",false);
        if(!(row.opt("content") instanceof String content)||content.length()>MAX_CONTENT)return dto;
        String html="";try{html=render.apply(content);if(html==null)html="";}catch(RuntimeException ignored){/* Never log a renderer exception containing private text. */}
        final String plain=Jsoup.parse(html.isEmpty()?content:html).text().strip();
        final String preview=plain.codePointCount(0,plain.length())>120?plain.substring(0,plain.offsetByCodePoints(0,120)):plain;
        return dto.put("content",content).put("contentHtml",html).put("preview",preview).put("available",true);
    }
    private static String time(String value){
        if(value.length()!=19)return "";try{LocalDateTime.parse(value,TIME);return value;}catch(RuntimeException e){return "";}
    }
}
