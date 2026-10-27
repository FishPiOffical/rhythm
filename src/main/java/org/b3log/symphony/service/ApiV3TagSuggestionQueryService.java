package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.repository.TagRepository;
import org.b3log.symphony.repository.TagArticleRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Locale;

@Service
public class ApiV3TagSuggestionQueryService {
    @Inject private UserRepository users;
    @Inject private TagRepository tags;
    @Inject private TagArticleRepository relations;
    @Inject private ArticleRepository articles;
    public static String prefix(String value) {
        if(value==null)return "";
        // Same outer whitespace set as the browser's trim, including Unicode separators/BOM.
        final String text=value.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "");
        if(text.length()>64||!text.isEmpty()&&!Tag.TAG_TITLE_PATTERN.matcher(text).matches())throw ApiV3Requests.invalid();
        return text.toLowerCase(Locale.ROOT);
    }
    public JSONObject suggestions(String actor,String raw) {
        final String prefix=prefix(raw);
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.forbidden();
        try {
            if(users.select("SELECT oId FROM `"+users.getName()+"` WHERE oId=? AND userStatus=0 LIMIT 1",actor).size()!=1)throw ApiV3Requests.notFound();
            final var rows=tags.select("SELECT t.tagTitle FROM `"+tags.getName()+"` t WHERE t.tagStatus=0 AND t.tagReferenceCount>0"
                    +" AND EXISTS(SELECT 1 FROM `"+relations.getName()+"` r JOIN `"+articles.getName()+"` a ON a.oId=r.article_oId WHERE r.tag_oId=t.oId)"
                    +(prefix.isEmpty()?"":" AND LOWER(t.tagTitle) LIKE ? ESCAPE '='")
                    +" ORDER BY t.tagReferenceCount DESC,LOWER(t.tagTitle),t.oId LIMIT 7",
                    prefix.isEmpty()?new Object[]{}:new Object[]{prefix.replace("_","=_")+"%"});
            if(rows.size()>7)throw unavailable();
            final JSONArray list=new JSONArray();final var seen=new HashSet<String>();
            for(final var row:rows){
                final String title=row.optString("tagTitle"),lower=title.toLowerCase(Locale.ROOT);
                if(title.isEmpty()||title.length()>255||!Tag.TAG_TITLE_PATTERN.matcher(title).matches()||!lower.startsWith(prefix)||!seen.add(lower))throw unavailable();
                list.put(title);
            }
            return new JSONObject().put("list",list);
        }catch(RepositoryException error){throw unavailable();}
    }
    private static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","标签联想暂不可用");}
}
