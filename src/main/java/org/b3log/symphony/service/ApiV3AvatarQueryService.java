package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3AvatarUrl;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

/** Stored owner avatar, without page-session display modes, remote fetching or a full user response. */
@Service
public class ApiV3AvatarQueryService {
    @Inject private UserRepository users;
    public JSONObject read(String actor)throws RepositoryException {
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}"))throw ApiV3WriteInputs.invalid();
        final java.util.List<JSONObject> rows;
        try{rows=users.select("SELECT userAvatarURL FROM `"+users.getName()+"` WHERE oId=? AND userStatus=?",actor,UserExt.USER_STATUS_C_VALID);}
        catch(RepositoryException e){
            org.apache.logging.log4j.LogManager.getLogger(getClass()).warn("Avatar settings read unavailable");
            throw new ApiV3Exception(503,"temporarily_unavailable","头像设置暂不可用");
        }
        if(rows.size()!=1)throw ApiV3Requests.notFound();
        return new JSONObject().put("avatarURL",ApiV3AvatarUrl.stored(rows.getFirst().optString("userAvatarURL")));
    }
}
