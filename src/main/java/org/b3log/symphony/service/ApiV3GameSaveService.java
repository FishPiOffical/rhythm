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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;
import java.math.BigDecimal;

/** Private registered game slots. New/legacy writers share the current owner lock; raw payloads never enter receipts. */
@Service
public class ApiV3GameSaveService {
    @Inject private ApiV3GameSaveRepository saves;
    @Inject private CloudRepository legacy;
    @Inject private UserRepository users;
    @Inject private AccountCredentialStateRepository states;
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private ApiV3RequestRepository requests;
    protected long now(){return System.currentTimeMillis();}
    public JSONObject get(JSONObject identity,String game) {
        ApiV3GameSaveInputs.game(game);final String actor=actor(identity);
        try {
            authorize(identity,"activity.read",false);
            final JSONObject row=saves.current(actor,game,false);if(row==null)throw missing();
            final JSONObject result=project(row,actor,game,true);authorize(identity,"activity.read",false);return result;
        }catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}
    }
    public JSONObject save(JSONObject identity,String game,JSONObject body,String key) {
        ApiV3GameSaveInputs.game(game);final String actor=actor(identity);final var input=ApiV3GameSaveInputs.save(body);
        final JSONObject normalized=new JSONObject().put("gameId",game).put("expectedRevision",input.expectedRevision())
                .put("sha256",input.content().sha256()).put("byteLength",input.content().byteLength());
        try {
            final JSONObject result=ApiV3IdempotencyMgmtService.runAtomic(guarded(identity),actor,"game-saves.save",key,normalized,
                    ()->write(actor,game,input.content(),input.expectedRevision(),false));
            final JSONObject receipt=receipt(result,game,false);
            if(receipt.getLong("revision")!=input.expectedRevision()+1||!receipt.getString("sha256").equals(input.content().sha256())
                    ||receipt.getInt("byteLength")!=input.content().byteLength())throw unavailable();return receipt;
        }catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}
    }
    public JSONObject migrate(JSONObject identity,String game,String key) {
        ApiV3GameSaveInputs.game(game);final String actor=actor(identity);
        try {
            final JSONObject result=ApiV3IdempotencyMgmtService.runAtomic(guarded(identity),actor,"game-saves.migrate",key,new JSONObject().put("gameId",game),()->{
                if(saves.current(actor,game,true)!=null)throw conflict("当前已有新存档，不能迁移覆盖");
                final var source=legacyContent(actor,game,true);if(source==null)throw missing();
                return write(actor,game,source,0,false).put("migrated",true);
            });return receipt(result,game,true);
        }catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}
    }
    /** Called only for a whitelisted legacy endpoint after LoginCheck has established this exact credential snapshot. */
    public JSONObject legacySave(String actor,AccountCredentialVersions.Proof proof,String game,Object data) {
        id(actor);ApiV3GameSaveInputs.game(game);final var content=ApiV3GameSaveInputs.content(data);
        if(saves.hasTransactionBegun())throw new IllegalStateException("legacy game save strict root required");
        Transaction tx=null;
        try {
            tx=ApiV3Transactions.strict(saves.beginTransaction());legacyAuthorize(actor,proof,true);
            final JSONObject result=write(actor,game,content,0,true);tx.commit();return result;
        }catch(ApiV3Exception error){rollback(tx);throw error;}catch(Exception error){rollback(tx);throw unavailable();}
    }
    public String legacyGet(String actor,AccountCredentialVersions.Proof proof,String game) {
        id(actor);ApiV3GameSaveInputs.game(game);
        try {
            legacyAuthorize(actor,proof,false);final JSONObject row=saves.current(actor,game,false);
            final String data;
            if(row!=null)data=project(row,actor,game,true).getString("data");
            else {final var content=legacyContent(actor,game,false);data=content==null?"":content.data();}
            legacyAuthorize(actor,proof,false);return data;
        }catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}
    }
    private ApiV3MutationStore guarded(JSONObject identity) {
        return new ApiV3MutationStore(){
            public boolean hasTransactionBegun(){return requests.hasTransactionBegun();}
            public Transaction beginTransaction(){return ApiV3Transactions.strict(requests.beginTransaction());}
            public JSONObject getForUpdate(String id)throws RepositoryException {authorize(identity,"activity.write",true);return requests.getForUpdate(id);}
            public String add(JSONObject value)throws RepositoryException{return requests.add(value);}
            public void update(String id,JSONObject value,String...fields)throws RepositoryException{requests.update(id,value,fields);}
        };
    }
    private JSONObject write(String actor,String game,ApiV3GameSaveInputs.Content data,long expected,boolean blind)throws Exception {
        if(!saves.hasTransactionBegun())throw new IllegalStateException("game save transaction required");
        final JSONObject previous=saves.current(actor,game,true);
        final long revision=previous==null?0:project(previous,actor,game,false).getLong("revision");
        if(!blind&&revision!=expected)throw conflict("存档版本已变化，请重新读取后决定");
        if(revision>=ApiV3GameSaveInputs.MAX_REVISION)throw unavailable();
        final long time=now();if(time<=0||time>8_640_000_000_000_000L)throw unavailable();
        final String slot=slot(actor,game);
        final int changed=previous==null
                ?ApiV3TransactionStatements.execute("INSERT INTO `"+saves.getName()+"` (oId,userId,gameId,revision,updatedAt,byteLength,sha256,data) VALUES(?,?,?,?,?,?,?,?)",slot,actor,game,1L,time,data.byteLength(),data.sha256(),data.data())
                :ApiV3TransactionStatements.execute("UPDATE `"+saves.getName()+"` SET revision=?,updatedAt=?,byteLength=?,sha256=?,data=? WHERE oId=? AND userId=? AND gameId=? AND revision=?",revision+1,time,data.byteLength(),data.sha256(),data.data(),slot,actor,game,revision);
        if(changed!=1)throw unavailable();
        return new JSONObject().put("gameId",game).put("revision",revision+1).put("updatedAt",time).put("byteLength",data.byteLength()).put("sha256",data.sha256());
    }
    private ApiV3GameSaveInputs.Content legacyContent(String actor,String game,boolean lock)throws Exception {
        final var rows=legacy.select("SELECT oId,userId,gameId,CASE WHEN OCTET_LENGTH(data)<=2097152 THEN data ELSE NULL END AS data FROM `"+legacy.getName()+"` WHERE userId=? AND BINARY gameId=? LIMIT 2"+(lock?" FOR UPDATE":""),actor,game);
        if(rows.size()>1)throw conflict("旧存档存在重复记录，请先核实，不能自动选择");if(rows.isEmpty())return null;
        final JSONObject row=rows.getFirst();if(!actor.equals(row.optString("userId"))||!game.equals(row.optString("gameId")))throw unavailable();
        return ApiV3GameSaveInputs.content(row.opt("data"));
    }
    private void authorize(JSONObject identity,String scope,boolean lock)throws RepositoryException {
        final String actor=actor(identity),tokenId=identity.optString("tokenId");id(tokenId);
        if(lock)clients.lockForAccountResetInCurrentTransaction();
        owner(actor,lock);
        final String suffix=lock?" FOR UPDATE":"";
        final var access=tokens.select("SELECT oId,userId,clientId,tokenType,state,expiresAt,scope FROM `"+tokens.getName()+"` WHERE oId=?"+suffix,tokenId);
        if(access.size()!=1)throw forbidden();final JSONObject token=access.getFirst();
        if(!tokenId.equals(token.optString("oId"))||!actor.equals(token.optString("userId"))||!"access".equals(token.optString("tokenType"))
                ||number(token.opt("state"))!=0||number(token.opt("expiresAt"))<=now()||!ApiV3TokenMgmtService.hasScope(token.optString("scope"),scope))throw forbidden();
        final String clientId=token.optString("clientId");
        final var registrations=clients.select("SELECT clientId,status,scope FROM `"+clients.getName()+"` WHERE clientId=?"+suffix,clientId);
        if(registrations.size()!=1||!clientId.equals(registrations.getFirst().optString("clientId"))||number(registrations.getFirst().opt("status"))!=0
                ||!ApiV3TokenMgmtService.hasScope(registrations.getFirst().optString("scope"),scope))throw forbidden();
    }
    private void owner(String actor,boolean lock)throws RepositoryException {
        final var rows=users.select("SELECT oId,userStatus FROM `"+users.getName()+"` WHERE oId=?"+(lock?" FOR UPDATE":""),actor);
        if(rows.size()!=1||!actor.equals(rows.getFirst().optString("oId"))||number(rows.getFirst().opt("userStatus"))!=0)throw forbidden();
    }
    private void legacyAuthorize(String actor,AccountCredentialVersions.Proof proof,boolean lock)throws RepositoryException {
        if(proof==null||!actor.equals(proof.actor()))throw forbidden();owner(actor,lock);
        final JSONObject current=states.current(actor);
        if(current==null||!actor.equals(current.optString("oId"))||number(current.opt("userStatus"))!=0
                ||AccountCredentialStateRepository.epoch(current.opt("credentialEpoch"))!=proof.epoch()||!proof.password().equals(current.optString("userPassword")))throw forbidden();
    }
    private JSONObject project(JSONObject row,String actor,String game,boolean includeData) throws Exception {
        if(!actor.equals(row.optString("userId"))||!game.equals(row.optString("gameId"))||!slot(actor,game).equals(row.optString("oId")))throw unavailable();
        final ApiV3GameSaveInputs.Content data;
        try{data=ApiV3GameSaveInputs.content(row.opt("data"));}catch(ApiV3Exception badStored){throw unavailable();}
        final JSONObject value=metadata(row,game);
        if(!data.sha256().equals(value.getString("sha256"))||data.byteLength()!=value.getInt("byteLength"))throw unavailable();
        if(includeData)value.put("data",data.data());return value;
    }
    private JSONObject receipt(JSONObject value,String game,boolean migrated) {
        if(!value.keySet().equals(migrated?Set.of("gameId","revision","updatedAt","byteLength","sha256","migrated"):Set.of("gameId","revision","updatedAt","byteLength","sha256")))throw unavailable();
        final JSONObject result=metadata(value,game);if(migrated){if(!Boolean.TRUE.equals(value.opt("migrated")))throw unavailable();result.put("migrated",true);}return result;
    }
    private JSONObject metadata(JSONObject row,String game) {
        final long revision,time,bytes;
        try{revision=ApiV3GameSaveInputs.revision(row.opt("revision"),false);time=number(row.opt("updatedAt"));bytes=number(row.opt("byteLength"));}catch(Exception invalid){throw unavailable();}
        if(!game.equals(row.optString("gameId"))||time<=0||time>8_640_000_000_000_000L||bytes<0||bytes>ApiV3GameSaveInputs.MAX_BYTES||!(row.opt("sha256") instanceof String hash)||!hash.matches("[0-9a-f]{64}"))throw unavailable();
        return new JSONObject().put("gameId",game).put("revision",revision).put("updatedAt",time).put("byteLength",bytes).put("sha256",hash);
    }
    private static long number(Object value) {if(!(value instanceof Number n))throw unavailable();try{return new BigDecimal(n.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException invalid){throw unavailable();}}
    private static String slot(String actor,String game)throws Exception{return ApiV3TokenUtil.hash("private-game-save\0"+actor+"\0"+game);}
    private static String actor(JSONObject identity){final JSONObject user=identity==null?null:identity.optJSONObject("user");final String actor=user==null?"":user.optString("oId");id(actor);return actor;}
    private static void id(String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw ApiV3WriteInputs.invalid();}
    private static void rollback(Transaction tx){if(tx!=null&&tx.isActive())tx.rollback();}
    private static ApiV3Exception forbidden(){return new ApiV3Exception(403,"forbidden","当前授权不可访问该游戏存档");}
    private static ApiV3Exception missing(){return new ApiV3Exception(404,"not_found","没有可读取或迁移的存档");}
    private static ApiV3Exception conflict(String message){return new ApiV3Exception(409,"game_save_conflict",message);}
    private static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","游戏存档暂不可用");}
}
