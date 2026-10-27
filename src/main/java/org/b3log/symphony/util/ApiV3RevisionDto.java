package org.b3log.symphony.util;

import org.json.JSONObject;

/** Revision responses contain only metadata and cleaned title/content, never editor identities. */
public final class ApiV3RevisionDto {
    private ApiV3RevisionDto() { }
    public static JSONObject metadata(final String id,final Long time,final int index) {
        return new JSONObject().put("id",id).put("time",time==null?JSONObject.NULL:time).put("index",index).put("current","current".equals(id));
    }
    public static JSONObject detail(final JSONObject metadata,final JSONObject data,final boolean article) {
        final JSONObject dto=new JSONObject(metadata.toString());
        if(article)dto.put("title",Escapes.escapeHTML(text(data,"articleTitle")));
        final String content=text(data,article?"articleContent":"commentContent");
        dto.put("content",Markdowns.clean(content.replace("\n","_esc_br_"),"").replace("_esc_br_","\n"));
        if(dto.toString().length()>2097152)throw unavailable();
        return dto;
    }
    public static String text(final JSONObject data,final String field) {
        if(!(data.opt(field) instanceof String text))throw unavailable();
        return text;
    }
    public static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","历史版本暂不可用");}
}
