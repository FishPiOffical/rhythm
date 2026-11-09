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
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.ApiV3MfaProcessor;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import io.netty.buffer.Unpooled;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** 执行实际身份验证、凭据撤销、MFA 准备状态持久化、回执和路由。 */
public class ApiV3MfaTest extends ApiV3SecurityTest {
 static String code(String secret,long time)throws Exception{var m=MultiFactorAuthenticator.class.getDeclaredMethod("verifyCode",byte[].class,long.class);m.setAccessible(true);return String.format(Locale.ROOT,"%06d",((Number)m.invoke(null,new org.apache.commons.codec.binary.Base32().decode(secret),time/30000)).intValue());}
 static JSONObject proofBody(){return new JSONObject().put("currentPassword",OLD);}
 static class M extends F {
  long clock=NOW;
  final ApiV3MfaService mfa=new ApiV3MfaService(){protected long now(){return clock;}};
  final ApiV3MfaProcessor endpoint=new ApiV3MfaProcessor();
  M()throws Exception{
   super();sql(db,"ALTER TABLE u ADD COLUMN userUpdateTime INT DEFAULT 0");
   sql(db,"CREATE TABLE prep(oId TEXT PRIMARY KEY,userId TEXT,preparationHash TEXT,secret TEXT,proofBinding TEXT,state INT,createdAt INT,expiresAt INT)");sql(db,"CREATE TABLE receipts(oId TEXT PRIMARY KEY,value TEXT)");
   var preparations=new ApiV3MfaPreparationRepository(){public String getName(){return "prep";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{Assert.assertTrue(q.endsWith(" FOR UPDATE"));if(fault.equals("prep-read"))throw new RepositoryException("PRIVATE");return selectSQL(q,a);}};
   var receipts = new ApiV3RequestRepository() {
    public boolean hasTransactionBegun() {
     return active();
    }

    public Transaction beginTransaction() {
     return begin();
    }

    public JSONObject getForUpdate(String id) throws RepositoryException {
     var rs = rows(writer, "SELECT value FROM receipts WHERE oId=?", id);
     return rs.isEmpty() ? null : new JSONObject(rs.getFirst().getString("value"));
    }

    public String add(JSONObject row) throws RepositoryException {
     sql(writer, "INSERT INTO receipts VALUES(?,?)", row.getString("oId"), row.toString());
     if (fault.equals("placeholder")) throw new RepositoryException("PRIVATE");
     return row.getString("oId");
    }

    public void update(String id, JSONObject row, String... fields) throws RepositoryException {
     sql(writer, "UPDATE receipts SET value=? WHERE oId=?", row.toString(), id);
     if (fault.equals("receipt")) throw new RepositoryException("PRIVATE");
    }
   };
   inject(mfa,"proofs",proof);inject(mfa,"credentials",credentials);inject(mfa,"preparations",preparations);inject(mfa,"requests",receipts);var f=ApiV3SecurityProofService.class.getDeclaredField("users");f.setAccessible(true);inject(mfa,"users",f.get(proof));inject(endpoint,"service",mfa);
  }
  @Override Transaction begin(){
   try{
    var root=super.begin();var inner=writer;
    writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
     try{
      // 分别在创建、释放及回滚到保存点的阶段模拟 JDBC 故障。
      if (m.getName().equals("setSavepoint") && fault.equals("savepoint"))
       throw new SQLException("PRIVATE");
      if (m.getName().equals("releaseSavepoint") && fault.equals("release"))
       throw new SQLException("PRIVATE");
      if (m.getName().equals("rollback") && a != null && a.length == 1 && fault.equals("rollback-point"))
       throw new SQLException("PRIVATE");
      if(m.getName().equals("prepareStatement")){String q=(String)a[0];var ps=(PreparedStatement)m.invoke(inner,a);return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p2,m2,a2)->{try{var out=m2.invoke(ps,a2);if(m2.getName().equals("executeUpdate")&&((fault.equals("prep-write")&&q.contains("`prep`"))||(fault.equals("mfa-write")&&q.startsWith("UPDATE `u`"))||(fault.equals("rollback-point")&&q.contains("`prep`"))))throw new SQLException("PRIVATE");if(m2.getName().equals("executeUpdate")&&fault.equals("unchanged")&&q.startsWith("UPDATE `u`"))return 0;return out;}catch(InvocationTargetException e){throw e.getCause();}});}
      return m.invoke(inner,a);
     }catch(InvocationTargetException e){throw e.getCause();}
    });var f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(tx,writer);return root;
   }catch(Exception e){throw new IllegalStateException(e);}
  }
  void bind()throws Exception{var quota=org.b3log.symphony.processor.middleware.ApiV3WriteMidware.class.getDeclaredField("COUNTS");quota.setAccessible(true);((com.github.benmanes.caffeine.cache.Cache<?,?>)quota.get(null)).invalidateAll();ApiV3MfaProcessor.registerRoutes(endpoint,auth,writes);Dispatcher.mapping();}
  RequestContext call(String action,String token,String key,JSONObject body)throws Exception{
   final String method=action.equals("prepare")?"POST":action.equals("enable")?"PUT":"DELETE";
   final String uri="/api/v3/me/security/mfa"+(action.equals("prepare")?"/preparations":"");final String text=body.toString();
   var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),uri+"?apiKey=legacy",Unpooled.copiedBuffer(text,StandardCharsets.UTF_8));
   try{if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);raw.headers().set("Cookie","legacy");raw.headers().set("Content-Type","application/json");raw.headers().set("X-Real-IP","127.0.0.1");var req=new Request(null,raw);req.setBytes(text.getBytes(StandardCharsets.UTF_8));req.setParams(new HashMap<>(Map.of("apiKey","legacy")));var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{var resolution=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),action);if(action.equals("prepare"))endpoint.prepare(ctx);else if(action.equals("enable"))endpoint.enable(ctx);else endpoint.disable(ctx);});c.handle();return c;}finally{raw.release();}
  }
  JSONObject prepare()throws Exception{return data(call("prepare","owner",null,proofBody()));}
  JSONObject enableBody(JSONObject prepared)throws Exception{return proofBody().put("preparationId",prepared.getString("preparationId")).put("newTotp",code(prepared.getString("secret"),clock));}
  void relogin()throws Exception{sql(db,"UPDATE tokens SET state=0,expiresAt=? WHERE oId='100'",NOW+86400000L);}
  String secret()throws Exception{return rows(db,"SELECT secret2fa FROM u WHERE oId='1'").getFirst().getString("secret2fa");}
  long count(String table)throws Exception{return rows(db,"SELECT COUNT(*) AS n FROM "+table).getFirst().getLong("n");}
 }
 static JSONObject data(RequestContext c){Assert.assertEquals(c.attr("test.status"),200);return (JSONObject)c.attr("test.data");}
 public static void main(String[] args) throws Exception {
  var p = new Properties();
  p.setProperty("runtimeDatabase", "NONE");
  p.setProperty("contextPath", "");
  Latkes.setLatkeProps(p);
  Latkes.setLocalProps(p);
  Assert.assertTrue(ApiV3MfaSecrets.check("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","287082",59000));
  try(var f=new M()){
   f.bind();
   var prepared = f.prepare();
   Assert.assertEquals(prepared.keySet(), Set.of("preparationId", "secret", "otpauthUri", "expiresAt"));
   Assert.assertEquals(prepared.getLong("expiresAt") - f.clock, 600000L);
   Assert.assertTrue(prepared.getString("otpauthUri").startsWith("otpauth://totp/FishPi%3A1?secret="));
   Assert.assertFalse(prepared.toString().contains("http"));
   Assert.assertEquals(f.count("receipts"), 0L);
   Assert.assertFalse(rows(f.db, "SELECT * FROM prep").toString().contains(prepared.getString("preparationId")));

   // 启用后核对准备密钥清除、凭据版本递增及旧凭据撤销。
   var ctx = f.call("enable", "owner", "enable", f.enableBody(prepared));
   Assert.assertTrue(data(ctx).getBoolean("enabled"));
   Assert.assertEquals(ctx.getResponse().getHeader("Cache-Control"), "no-store");
   Assert.assertEquals(f.secret(), prepared.getString("secret"));
   Assert.assertEquals(rows(f.db, "SELECT secret,state FROM prep").getFirst().getString("secret"), "");
   Assert.assertEquals(rows(f.db, "SELECT credentialEpoch FROM states WHERE oId='1'").getFirst().getLong("credentialEpoch"), 1L);
   Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM tokens WHERE userId='1' AND state=0").getFirst().getLong("n"), 0L);
   Assert.assertEquals(rows(f.db, "SELECT state FROM openid WHERE userId='1'").getFirst().getInt("state"), 2);
   Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM requests WHERE userId='1'").getFirst().getLong("n"), 1L);
   Assert.assertEquals(rows(f.db, "SELECT COUNT(*) AS n FROM codes WHERE userId='1'").getFirst().getLong("n"), 1L);
   String stored = rows(f.db, "SELECT value FROM receipts").toString();
   for (String sensitive : List.of(OLD, prepared.getString("secret"), prepared.getString("preparationId"),
       f.enableBody(prepared).getString("newTotp"))) Assert.assertFalse(stored.contains(sensitive));

   Assert.assertEquals(f.call("disable", "owner", "disable", proofBody()).attr("test.status"), 401);
   f.relogin();
   var current = proofBody().put("currentTotp", code(prepared.getString("secret"), f.clock));
   Assert.assertFalse(data(f.call("disable", "owner", "disable", current)).getBoolean("enabled"));
   Assert.assertEquals(f.secret(), "");
   Assert.assertEquals(f.count("prep"), 0L);
   Assert.assertEquals(rows(f.db, "SELECT credentialEpoch FROM states WHERE oId='1'").getFirst().getLong("credentialEpoch"), 2L);
  }
  System.out.println("PASS MFA 1 RFC TOTP and real prepare/enable/disable Dispatcher/proof/SQL, local URI, secret only first response, hashed handle, consumed preparation, all credential families atomically revoked and private receipts");
  try(var f=new M()){
   f.bind();
   var first = f.prepare();
   var second = f.prepare();
   Assert.assertNotEquals(first.getString("secret"), second.getString("secret"));
   Assert.assertEquals(f.count("prep"), 1L);
   Assert.assertEquals(f.call("enable", "owner", "old", f.enableBody(first)).attr("test.status"), 409);
   Assert.assertEquals(f.secret(), "");
   f.clock = second.getLong("expiresAt");
   Assert.assertEquals(f.call("enable", "owner", "expired", f.enableBody(second)).attr("test.status"), 409);
   Assert.assertEquals(f.count("receipts"), 0L);
  }
  try(var f=new M()){
   f.bind();
   var prepared = f.prepare();
   sql(f.db, "INSERT INTO states VALUES('1',1,1)");
   Assert.assertEquals(f.call("enable", "owner", "epoch", f.enableBody(prepared)).attr("test.status"), 409);
   Assert.assertEquals(f.secret(), "");
  }
  System.out.println("PASS MFA 2 fresh preparation invalidates old; exact ten-minute expiry boundary, current credential-epoch binding and failed attempts do not consume preparation/retain receipt");
  try(var f=new M()){
   f.bind();
   var prepared = f.prepare();
   var wrong = f.enableBody(prepared).put("newTotp", "000000");
   if (code(prepared.getString("secret"), f.clock).equals("000000")) wrong.put("newTotp", "000001");
   Assert.assertEquals(f.call("enable", "owner", "bad", wrong).attr("test.status"), 422);
   Assert.assertEquals(rows(f.db, "SELECT state FROM prep").getFirst().getInt("state"), 0);
   Assert.assertEquals(f.call("prepare", "owner", null, proofBody().put("currentPassword", NEW)).attr("test.status"), 422);
   Assert.assertEquals(f.count("receipts"), 0L);
   data(f.call("enable", "owner", "ok", f.enableBody(prepared)));
   f.relogin();
   Assert.assertEquals(f.call("disable", "owner", "missing", proofBody()).attr("test.status"), 422);
   Assert.assertEquals(f.call("prepare", "owner", null,
       proofBody().put("currentTotp", code(prepared.getString("secret"), f.clock))).attr("test.status"), 409);
   Assert.assertEquals(f.call("enable", "owner", "ok-again",
       f.enableBody(prepared).put("currentTotp", code(prepared.getString("secret"), f.clock))).attr("test.status"), 409);
  }
  System.out.println("PASS MFA 3 wrong new/current security factors, already enabled prepare/enable rejection, valid unconsumed preparation survives wrong code");
  for(String fault:List.of("read","prep-read","prep-write","commit","savepoint","release"))try(var f=new M()){
   f.bind();
   f.fault = fault;
   Assert.assertEquals(f.call("prepare", "owner", null, proofBody()).attr("test.status"), 503, fault);
   Assert.assertEquals(f.count("prep"), 0L);
   Assert.assertEquals(f.secret(), "");
   Assert.assertEquals(f.count("receipts"), 0L);
  }
  for(String fault:List.of("read","placeholder","prep-read","prep-write","mfa-write","revoke","receipt","commit","savepoint","release"))try(var f=new M()){
   f.bind();
   var prepared = f.prepare();
   f.fault = fault;
   Assert.assertEquals(f.call("enable", "owner", "fault", f.enableBody(prepared)).attr("test.status"), 503, fault);
   Assert.assertEquals(f.secret(), "");
   Assert.assertEquals(rows(f.db, "SELECT state,secret FROM prep").getFirst().getInt("state"), 0);
   Assert.assertEquals(f.count("states"), 0L);
   Assert.assertEquals(f.count("receipts"), 0L);
   Assert.assertEquals(rows(f.db, "SELECT state FROM tokens WHERE oId='100'").getFirst().getInt("state"), 0);
   f.fault = "";
   data(f.call("enable", "owner", "fault", f.enableBody(prepared)));
  }
  System.out.println("PASS MFA 4 actual prepare/consume/user-secret/revoke/epoch/receipt/read/savepoint/strict-commit failures roll back all fields and credentials, recovered same key succeeds");
  for(String fault:List.of("prep-write","mfa-write","revoke","receipt","commit","savepoint","release"))try(var f=new M()){
   f.bind();
   sql(f.db, "UPDATE u SET secret2fa=? WHERE oId='1'", SECRET);
   f.fault = fault;
   Assert.assertEquals(f.call("disable", "owner", "fault", proofBody().put("currentTotp", totp())).attr("test.status"), 503, fault);
   Assert.assertEquals(f.secret(), SECRET);
   Assert.assertEquals(f.count("states"), 0L);
   Assert.assertEquals(f.count("receipts"), 0L);
  }
  System.out.println("PASS MFA 5 disable secret/preparation/revocation/receipt/commit faults atomically preserve enabled factor and all current credentials");
  try(var f=new M()){
   f.bind();
   for (String action : List.of("prepare", "enable", "disable"))
    for (String token : Arrays.asList(null, "bad", "wrong"))
     Assert.assertEquals(f.call(action, token, "auth", proofBody()).attr("test.status"), "wrong".equals(token) ? 403 : 401);
   for (String mutation : List.of("UPDATE tokens SET clientId='third' WHERE oId='100'",
       "UPDATE tokens SET expiresAt=1 WHERE oId='100'", "UPDATE clients SET status=1 WHERE clientId='website_v3'",
       "UPDATE u SET userStatus=4 WHERE oId='1'")) try (var other = new M()) {
    other.bind();
    sql(other.db, mutation);
    Assert.assertEquals(other.call("prepare", "owner", null, proofBody()).attr("test.status"), 403);
    Assert.assertEquals(other.count("prep"), 0L);
   }
   f.bind();
   Assert.assertEquals(f.call("prepare", "owner", null, proofBody().put("owner", "2")).attr("test.status"), 422);
   Assert.assertEquals(f.call("prepare", "owner", null, proofBody().put("currentPassword", OLD.toUpperCase())).attr("test.status"), 422);
   f.allowed = false;
   Assert.assertEquals(f.call("prepare", "owner", null, proofBody()).attr("test.status"), 429);
   Assert.assertEquals(f.roots, 0);
  }
  System.out.println("PASS MFA 6 first-party/current access/client/status/scopes and attempt gate, no Cookie/apiKey/owner forgery, typed bodies and factors");
  try(var f=new M()){
   f.bind();
   f.fault = "ack";
   Assert.assertEquals(f.call("prepare", "owner", null, proofBody()).attr("test.status"), 503);
   Assert.assertEquals(f.count("prep"), 1L);
   f.fault = "";
   var prepared = f.prepare();
   f.fault = "ack";
   Assert.assertEquals(f.call("enable", "owner", "ack", f.enableBody(prepared)).attr("test.status"), 503);
   Assert.assertEquals(f.secret(), prepared.getString("secret"));
   Assert.assertEquals(f.call("enable", "owner", "ack", f.enableBody(prepared)).attr("test.status"), 401);
   f.fault = "";
   f.relogin();
   var retry = f.enableBody(prepared).put("currentTotp", code(prepared.getString("secret"), f.clock));
   Assert.assertTrue(data(f.call("enable", "owner", "ack", retry)).getBoolean("enabled"));
   Assert.assertEquals(rows(f.db, "SELECT credentialEpoch FROM states").getFirst().getLong("credentialEpoch"), 1L);
  }
  System.out.println("PASS MFA 7 lost acknowledgements never replay secret material, old token rejected; reauthenticated fresh proof can recover historical boolean receipt without duplicate epoch mutation");
  try(var f=new M()){
   f.bind();
   var prepared = f.prepare();
   sql(f.db, "UPDATE u SET userPassword=? WHERE oId='1'", NEW);
   Assert.assertEquals(f.call("enable", "owner", "password-changed",
       f.enableBody(prepared).put("currentPassword", NEW)).attr("test.status"), 409);
   Assert.assertEquals(f.secret(), "");
  }
  try(var f=new M()){
   f.bind();
   var prepared = f.prepare();
   sql(f.db, "UPDATE prep SET userId='2'");
   Assert.assertEquals(f.call("enable", "owner", "wrong-owner", f.enableBody(prepared)).attr("test.status"), 409);
   Assert.assertEquals(f.secret(), "");
  }
  try(var f=new M()){
   try {
    f.mfa.prepareInCurrentTransaction(identity(), ApiV3MfaInputs.parse(proofBody(), false));
    Assert.fail();
   } catch (IllegalStateException expected) {
   }
   var root = f.begin();
   sql(f.writer, "UPDATE u SET secret2fa='unrelated' WHERE oId='2'");
   f.fault = "prep-write";
   try {
    f.mfa.prepareInCurrentTransaction(identity(), ApiV3MfaInputs.parse(proofBody(), false));
    Assert.fail();
   } catch (RepositoryException expected) {
   }
   f.fault = "";
   root.commit();
   Assert.assertEquals(f.count("prep"), 0L);
   Assert.assertEquals(rows(f.db, "SELECT secret2fa FROM u WHERE oId='2'").getFirst().getString("secret2fa"), "unrelated");
  }
  try(var f=new M()){
   var root = f.begin();
   sql(f.writer, "UPDATE u SET secret2fa='unrelated' WHERE oId='2'");
   f.fault = "rollback-point";
   try {
    f.mfa.prepareInCurrentTransaction(identity(), ApiV3MfaInputs.parse(proofBody(), false));
    Assert.fail();
   } catch (RepositoryException expected) {
   }
   Assert.assertFalse(root.isActive());
   Assert.assertEquals(f.count("prep"), 0L);
   Assert.assertEquals(rows(f.db, "SELECT secret2fa FROM u WHERE oId='2'").getFirst().getString("secret2fa"), "");
  }
  System.out.println("PASS MFA 8 preparation actor/password binding; primitive root/savepoint ownership and rollback failure aborting whole root without committing unrelated work");
  try(var f=new M()){
   f.bind();
   sql(f.db, "UPDATE u SET userUpdateTime=? WHERE oId='1'", f.clock);
   f.fault = "unchanged";
   Assert.assertFalse(data(f.call("disable", "owner", "unchanged", proofBody())).getBoolean("enabled"));
   Assert.assertEquals(rows(f.db, "SELECT credentialEpoch FROM states").getFirst().getLong("credentialEpoch"), 1L);
  }
  System.out.println("PASS MFA 9 already-disabled matched actor tolerates zero changedRows while still atomically revoking credentials");
  System.out.println("MFA isolated tests passed: 9 groups");
 }
}
