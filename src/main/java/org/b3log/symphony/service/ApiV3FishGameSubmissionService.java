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
package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.FishGameRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3FishGameSubmissionInputs;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.FishGameWriteQuota;
import org.json.JSONObject;
import java.net.URI;
import java.util.Set;

/** 校验旧版 OpenID 登录网址格式后，创建待审核投稿或编辑。 */
@Service
public class ApiV3FishGameSubmissionService {
    public static final String NAMESPACE = "apiV3FishGameSubmission";
    @Inject
    private UserRepository users;
    @Inject
    private FishGameRepository games;
    @Inject
    private OptionRepository options;
    @Inject
    private ApiV3RequestRepository requests;

    protected long now() {
        return System.currentTimeMillis();
    }

    protected boolean allowed(String actor) {
        return FishGameWriteQuota.access(FishGameWriteQuota.Kind.SUBMISSION, actor);
    }

    protected String developmentProvider() {
        try {
            if(Latkes.RuntimeMode.DEVELOPMENT!=Latkes.getRuntimeMode())return "";
            URI base=new URI(Latkes.getServePath());
            if(!Set.of("localhost","127.0.0.1","[::1]","::1").contains(base.getHost())||base.getRawUserInfo()!=null||base.getRawQuery()!=null||base.getRawFragment()!=null
                    ||!("http".equals(base.getScheme())||"https".equals(base.getScheme()))||!(base.getPath().isEmpty()||"/".equals(base.getPath())))return "";
            return Latkes.getServePath().replaceAll("/$","")+"/openid/login";
        } catch (Exception e) {
            return "";
        }
    }
    public JSONObject submit(String actor,String gameId,JSONObject body,String key)throws Exception {
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}")||gameId!=null&&!gameId.matches("[1-9][0-9]{0,18}"))throw invalid();
        final var input=ApiV3FishGameSubmissionInputs.parse(body,developmentProvider());
        final String operation=gameId==null?"fish-games.submit":"fish-games.edit:"+gameId;
        try {
            ApiV3MutationStore store=new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    return ApiV3Transactions.strict(requests.beginTransaction());
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }

                public JSONObject getForUpdate(String id) throws RepositoryException {
                    // 先锁命名空间以串行检查 v3 投稿网址，再锁当前投稿者及其幂等回执。
                    var namespaces = options.select("SELECT oId FROM `" + options.getName() + "` WHERE oId=? FOR UPDATE", NAMESPACE);
                    if (namespaces.size() != 1)
                        throw unavailable();
                    var owners = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
                    if (owners.size() != 1 || !actor.equals(owners.getFirst().optString("oId"))
                            || integer(owners.getFirst().opt("userStatus")) != UserExt.USER_STATUS_C_VALID)
                        throw ApiV3Requests.notFound();
                    return requests.getForUpdate(id);
                }

                public String add(JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    requests.update(id, row, fields);
                }
            };
            return result(ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store,actor,operation,key,input.normalized(),()->apply(actor,gameId,input)).result());
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
    }
    private JSONObject apply(String actor,String gameId,ApiV3FishGameSubmissionInputs.Submission input)throws Exception {
        if(!requests.hasTransactionBegun())throw new IllegalStateException("fish game root required");
        if(!allowed(actor))throw new ApiV3Exception(429,"rate_limited","操作过于频繁，请稍后再试");
        JSONObject game=null;
        if(gameId!=null) {
            var rows=games.select("SELECT oId,fishGameAuthorId,fishGameStatus,fishGameEditPending FROM `"+games.getName()+"` WHERE oId=? FOR UPDATE",gameId);
            if(rows.size()!=1||!actor.equals(rows.getFirst().optString(FishGame.AUTHOR_ID)))throw ApiV3Requests.notFound();
            game=rows.getFirst();
            if(integer(game.opt(FishGame.STATUS))!=FishGame.STATUS_APPROVED)throw new ApiV3Exception(409,"game_not_approved","仅已审核鱼游可申请编辑");
            if(integer(game.opt(FishGame.EDIT_PENDING))!=0)throw new ApiV3Exception(409,"edit_pending","已有待审核修改");
        }
        var conflicts=games.select("SELECT oId FROM `"+games.getName()+"` WHERE (fishGameUrl=? OR (fishGameEditPending=1 AND fishGamePendingUrl=?))"+(gameId==null?"":" AND oId<>?")+" ORDER BY oId LIMIT 2 FOR UPDATE",
                gameId==null?new Object[]{input.url(),input.url()}:new Object[]{input.url(),input.url(),gameId});
        if(!conflicts.isEmpty())throw new ApiV3Exception(409,"game_url_taken","目标网址已有投稿或待审核修改");
        long time = now();
        if (time <= 0)
            throw unavailable();
        if(game==null) {
            String id=Ids.genTimeMillisId();
            JSONObject row=new JSONObject().put("oId",id).put(FishGame.NAME,input.name()).put(FishGame.DESCRIPTION,input.description()).put(FishGame.URL,input.url()).put(FishGame.ICON_URL,input.iconUrl())
                    .put(FishGame.OAUTH_LOGIN_URL,input.oauthLoginUrl()).put(FishGame.PENDING_OAUTH_LOGIN_URL,"")
                    .put(FishGame.AUTHOR_ID,actor).put(FishGame.STATUS,FishGame.STATUS_PENDING).put(FishGame.EDIT_PENDING,0)
                    .put(FishGame.PENDING_NAME,"").put(FishGame.PENDING_DESCRIPTION,"").put(FishGame.PENDING_URL,"").put(FishGame.PENDING_ICON_URL,"")
                    .put(FishGame.LIKE_COUNT,0).put(FishGame.DISLIKE_COUNT,0).put(FishGame.CREATED_TIME,time).put(FishGame.UPDATED_TIME,time);
            if(!id.equals(games.add(row)))throw unavailable();
            return new JSONObject().put("id",id).put("status",FishGame.STATUS_PENDING).put("editPending",false);
        }
        if(ApiV3TransactionStatements.execute("UPDATE `"+games.getName()+"` SET fishGamePendingName=?,fishGamePendingDescription=?,fishGamePendingUrl=?,fishGamePendingIconUrl=?,fishGamePendingOauthLoginUrl=?,fishGameEditPending=1,fishGameUpdatedTime=? WHERE oId=?",
                input.name(),input.description(),input.url(),input.iconUrl(),input.oauthLoginUrl(),time,gameId)!=1)throw unavailable();
        return new JSONObject().put("id",gameId).put("status",FishGame.STATUS_APPROVED).put("editPending",true);
    }
    private static JSONObject result(JSONObject value) {
        if(!value.keySet().equals(Set.of("id","status","editPending"))||!(value.opt("id") instanceof String)||!value.optString("id").matches("[1-9][0-9]{0,18}")
                ||integer(value.opt("status"))!=FishGame.STATUS_PENDING&&integer(value.opt("status"))!=FishGame.STATUS_APPROVED
                ||!(value.opt("editPending") instanceof Boolean pending)||pending!=(integer(value.opt("status"))==FishGame.STATUS_APPROVED))throw unavailable();
        return new JSONObject().put("id",value.getString("id")).put("status",integer(value.opt("status"))).put("editPending",pending);
    }
    private static int integer(Object value) {
        if (!(value instanceof Number n))
            throw unavailable();
        try {
            return new java.math.BigDecimal(n.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw unavailable();
        }
    }

    private static ApiV3Exception invalid() {
        return new ApiV3Exception(422, "invalid_request", "请求字段不合法");
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "鱼游投稿暂不可用");
    }
}
