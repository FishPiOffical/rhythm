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
import java.net.URI;
import java.util.Set;

/** 校验分组成员元数据，以区分大小写的完整 HTTPS 地址标识图片，不访问远端。 */
public final class ApiV3EmojiItemInputs {
    private ApiV3EmojiItemInputs() { }
    public static JSONObject metadata(final JSONObject body) {
        if(body==null||!Set.of("name","sort").containsAll(body.keySet()))throw ApiV3WriteInputs.invalid();
        final Object raw=body.has("name")?body.opt("name"):"";
        if(!(raw instanceof String name))throw ApiV3WriteInputs.invalid();
        // 复用分组解析器，保持非空纯文本和 Unicode 校验规则一致。
        final boolean empty=name.strip().isEmpty();
        final JSONObject value=ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name",empty?"x"+name:name).put("sort",body.has("sort")?body.opt("sort"):0));
        return new JSONObject().put("name",empty?"":value.getString("name")).put("sort",value.getInt("sort"));
    }
    public static JSONObject addition(final JSONObject body,final boolean allOnly) {
        if(body==null||!Set.of("name","sort","url","emojiId").containsAll(body.keySet())
                ||body.has("url")==body.has("emojiId")||allOnly&&body.has("emojiId"))throw ApiV3WriteInputs.invalid();
        final JSONObject meta=new JSONObject();for(String k:Set.of("name","sort"))if(body.has(k))meta.put(k,body.opt(k));
        final JSONObject ret=metadata(meta);
        if(body.has("emojiId")){if(!(body.opt("emojiId") instanceof String id)||!id.matches("[1-9][0-9]{0,18}"))throw ApiV3WriteInputs.invalid();ret.put("emojiId",id);}
        else ret.put("url",url(body.opt("url")));
        return ret;
    }
    public static String url(final Object raw) {
        if(!(raw instanceof String text)||text.isEmpty()||text.length()>512||!text.equals(text.trim()))throw ApiV3WriteInputs.invalid();
        for(int i=0;i<text.length();i++) {
            final char c=text.charAt(i);
            if(Character.isISOControl(c)||Character.isWhitespace(c)||c=='\\')throw ApiV3WriteInputs.invalid();
            if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw ApiV3WriteInputs.invalid();}
            else if(Character.isLowSurrogate(c))throw ApiV3WriteInputs.invalid();
        }
        try {
            final URI uri=new URI(text);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isEmpty()||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null
                    ||uri.getPort()<-1||uri.getPort()>65535)throw ApiV3WriteInputs.invalid();
            return text;
        }catch(java.net.URISyntaxException error){throw ApiV3WriteInputs.invalid();}
    }
}
