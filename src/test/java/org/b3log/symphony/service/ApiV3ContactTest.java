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
import org.b3log.symphony.repository.AccountContactCodeRepository;
import org.b3log.symphony.processor.ApiV3ContactSettingsProcessor;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** 联系方式集成测试复用安全测试中的严格根事务、身份验证与凭据 SQL 夹具。 */
public class ApiV3ContactTest {
 static final String PHONE="13900000001",EMAIL="new@example.com";
 static final String CAPTCHA=new JSONObject().put("lot_number","lot").put("captcha_output","output").put("pass_token","pass").put("gen_time","time").toString();
 static ApiV3ContactInputs.Input input(boolean phone,boolean sending,String proof){return ApiV3ContactInputs.parse(new JSONObject().put("target",phone?PHONE:EMAIL).put("currentPassword",ApiV3SecurityTest.OLD).put(sending?"captcha":"code",proof),phone,sending);}
 static class F extends ApiV3SecurityTest.F {
  boolean delivery=true,captcha=true,quota=true,providerThrows;int sent,codeSequence=100000;String delivered="",race="";long clock=ApiV3SecurityTest.NOW;
  ApiV3ContactSettingsProcessor contactController=new ApiV3ContactSettingsProcessor();
  ApiV3ContactSettingsService contact = new ApiV3ContactSettingsService() {
   protected long now() {
    return clock;
   }

   protected String newCode() {
    return String.valueOf(++codeSequence);
   }

   protected boolean captcha(String p) {
    return captcha;
   }

   protected boolean allowed(String ip, String name, String t, boolean p) {
    Assert.assertEquals(name, "reader");
    return quota;
   }

   protected boolean send(String target, String code, boolean phone) {
    Assert.assertFalse(active(), "provider cannot run under transaction");
    sent++;
    delivered = code;
    try {
     switch (race) {
      case "epoch" -> sql(db, "INSERT INTO states VALUES('1',1,0)");
      case "previous" -> sql(db, "UPDATE u SET userPhone='13800000009' WHERE oId='1'");
      case "occupied" -> sql(db, "UPDATE u SET userPhone=? WHERE oId='2'", PHONE);
      case "post-commit" -> fault = "commit";
      case "store" -> fault = "store";
      case "post-begin" -> fault = "begin";
      default -> {}
     }
    } catch (Exception e) {
     throw new RuntimeException(e);
    }
    if (providerThrows) throw new RuntimeException("PRIVATE_PROVIDER_" + target + code);
    return delivery;
   }
  };
  F()throws Exception {
   super();sql(db,"ALTER TABLE u ADD userName TEXT NOT NULL DEFAULT 'reader'");sql(db,"ALTER TABLE u ADD userPhone TEXT NOT NULL DEFAULT ''");sql(db,"ALTER TABLE u ADD userEmail TEXT NOT NULL DEFAULT ''");sql(db,"UPDATE u SET userPhone='13800000001',userEmail='old@example.com' WHERE oId='1'");sql(db,"UPDATE u SET userPhone='13800000002',userEmail='other@example.com',userName='other' WHERE oId='2'");
   sql(db,"CREATE TABLE contact(oId TEXT PRIMARY KEY,userId TEXT,purpose TEXT,clientId TEXT,credentialEpoch INT,target TEXT,codeHash TEXT,previousContactHash TEXT,expiresAt INT,createdAt INT,UNIQUE(userId,purpose))");
   var codes=new AccountContactCodeRepository(){public String getName(){return "contact";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}};
   ApiV3SecurityTest.inject(contact,"codes",codes);ApiV3SecurityTest.inject(contact,"users",userRepository());ApiV3SecurityTest.inject(contact,"proofs",proof);ApiV3SecurityTest.inject(contact,"credentials",credentials);ApiV3SecurityTest.inject(contactController,"settings",contact);
  }
  org.b3log.symphony.repository.UserRepository userRepository()throws Exception {var f=ApiV3SecurityProofService.class.getDeclaredField("users");f.setAccessible(true);return (org.b3log.symphony.repository.UserRepository)f.get(proof);}
  @Override
  Transaction begin() {
   if (fault.equals("begin")) throw new IllegalStateException("PRIVATE_BEGIN");
   var result = super.begin();
   try {
    var original = writer;
    // 在真实 SQL 写入后注入故障，便于断言根事务撤回已执行的写入。
    writer = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p, m, a) -> {
     try {
      if (m.getName().equals("prepareStatement")) {
       String q = (String) a[0];
       var s = (PreparedStatement) m.invoke(original, a);
       return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PreparedStatement.class}, (x, n, b) -> {
        try {
         var out = n.invoke(s, b);
         if (n.getName().equals("executeUpdate") && ((fault.equals("store") && q.startsWith("INSERT INTO `contact`"))
                 || (fault.equals("contact-write") && q.startsWith("UPDATE `u`"))))
          throw new SQLException("PRIVATE_CONTACT");
         return out;
        } catch (InvocationTargetException e) {
         throw e.getCause();
        }
       });
      }
      return m.invoke(original, a);
     } catch (InvocationTargetException e) {
      throw e.getCause();
     }
    });
    var f = org.b3log.latke.repository.jdbc.JdbcTransaction.class.getDeclaredField("connection");
    f.setAccessible(true);
    f.set(tx, writer);
    return result;
   } catch (Exception e) {
    throw new IllegalStateException(e);
   }
  }
  JSONObject row()throws Exception{var r=rows(db,"SELECT * FROM contact ORDER BY purpose");return r.isEmpty()?null:r.getFirst();}
  void request(boolean phone)throws Exception{contact.request(ApiV3SecurityTest.identity(),input(phone,true,CAPTCHA),phone,"ip");}
  JSONObject change(boolean phone,String code)throws Exception{return contact.change(ApiV3SecurityTest.identity(),input(phone,false,code),phone,"ip");}
  RequestContext http(String path,String body,boolean sending,String token)throws Exception{
   var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
           sending ? io.netty.handler.codec.http.HttpMethod.POST : io.netty.handler.codec.http.HttpMethod.PUT,
           path, io.netty.buffer.Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
   try {
    if (token != null) raw.headers().set("Authorization", "Bearer " + token);
    raw.headers().set("Cookie", "legacy");
    raw.headers().set("Content-Type", "application/json");
    raw.headers().set("X-Real-IP", "127.0.0.2");
    var req = new Request(null, raw);
    req.setBytes(body.getBytes(StandardCharsets.UTF_8));
    req.setParams(new HashMap<>(Map.of("apiKey", "legacy")));
    var ctx = new RequestContext(req, new Response(null,
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
    ctx.attr("user", new JSONObject().put("oId", "2"));
    ctx.addHandler(new org.b3log.latke.http.handler.RouteHandler());
    ctx.addHandler(c -> {
     var r = (org.b3log.latke.http.handler.RouteResolution) c.attr("MATCH_RESULT");
     switch (r.getContextHandlerMeta().getInvokeHolder().getName()) {
      case "phoneCode" -> contactController.phoneCode(c);
      case "emailCode" -> contactController.emailCode(c);
      case "phone" -> contactController.phone(c);
      case "email" -> contactController.email(c);
      default -> throw new AssertionError();
     }
    });
    ctx.handle();
    return ctx;
   } finally {
    raw.release();
   }
  }
 }
 static void sql(Connection c,String q,Object...a)throws RepositoryException{ApiV3SecurityTest.sql(c,q,a);}static List<JSONObject> rows(Connection c,String q,Object...a)throws RepositoryException{return ApiV3SecurityTest.rows(c,q,a);}static void error(int status,ApiV3SecurityTest.A a)throws Exception{ApiV3SecurityTest.error(status,a);}
 static JSONObject body(boolean phone,boolean sending,String code){return new JSONObject().put("target",phone?PHONE:EMAIL).put("currentPassword",ApiV3SecurityTest.OLD).put(sending?"captcha":"code",sending?CAPTCHA:code);}
 public static void main(String[]args)throws Exception {
  var p=new Properties();p.setProperty("runtimeDatabase","NONE");p.setProperty("contextPath","");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
  for(boolean phone:List.of(true,false))try(var f=new F()){
   f.request(phone);String first=f.delivered;var old=f.row();Assert.assertEquals(old.getString("target"),phone?PHONE:EMAIL);Assert.assertEquals(old.getString("clientId"),"website_v3");Assert.assertEquals(old.getLong("credentialEpoch"),0L);Assert.assertEquals(old.getLong("expiresAt"),f.clock+600000);Assert.assertTrue(old.getString("codeHash").matches("[0-9a-f]{64}"));Assert.assertTrue(old.getString("previousContactHash").matches("[0-9a-f]{64}"));
   f.request(phone);Assert.assertNotEquals(f.delivered,first);error(422,()->f.change(phone,first));var changed=f.change(phone,f.delivered);Assert.assertEquals(changed.keySet(),Set.of("changed","reauthenticationRequired"));Assert.assertNull(f.row());Assert.assertEquals(rows(f.db,"SELECT "+(phone?"userPhone":"userEmail")+" AS target FROM u WHERE oId='1'").getFirst().getString("target"),phone?PHONE:EMAIL);Assert.assertEquals(rows(f.db,"SELECT credentialEpoch FROM states WHERE oId='1'").getFirst().getLong("credentialEpoch"),1L);Assert.assertEquals(rows(f.db,"SELECT state FROM tokens WHERE oId='100'").getFirst().getInt("state"),2);
  }System.out.println("PASS contact 1 actual phone/email request outside locks, HMAC-only owner/client/epoch/target proofs, 10minute expiry, resend invalidates old, consume+change+all revoke atomic");
  for(String failure:List.of("delivery","throw","store","post-commit","post-begin"))try(var f=new F()){
   f.request(true);String before=f.row().toString();if(failure.equals("delivery"))f.delivery=false;else if(failure.equals("throw"))f.providerThrows=true;else f.race=failure;error(503,()->f.request(true));Assert.assertEquals(f.row().toString(),before);
  }System.out.println("PASS contact 2 provider reject/throw and post-delivery persistence/commit failures retain prior accepted code without leaking delivery exceptions");
  // 在发送验证码期间修改凭据版本、原联系方式或占用状态，验证保存前复核。
  for(String race:List.of("epoch","previous","occupied"))try(var f=new F()){
   f.request(true);String before=f.row().toString();f.race=race;error(422,()->f.request(true));Assert.assertEquals(f.row().toString(),before);
  }System.out.println("PASS contact 3 provider-window epoch/old-contact/occupancy races reject new proof and preserve previous successful code");
  for(String change:List.of("expiresAt=1","clientId='third'","credentialEpoch=1","target='13900000009'","purpose='email'","previousContactHash='"+"0".repeat(64)+"'","userId='2'","codeHash='"+"0".repeat(64)+"'"))try(var f=new F()){
   f.request(true);sql(f.db,"UPDATE contact SET "+change);error(422,()->f.change(true,f.delivered));Assert.assertNotNull(f.row());Assert.assertEquals(rows(f.db,"SELECT userPhone FROM u WHERE oId='1'").getFirst().getString("userPhone"),"13800000001");
  }
  try(var f=new F()){f.request(true);sql(f.db,"UPDATE u SET userPhone='13800000009' WHERE oId='1'");error(422,()->f.change(true,f.delivered));}
  System.out.println("PASS contact 4 consumed proof current owner/client/purpose/target/epoch/old-contact/expiry/HMAC binding, wrong attempts retain code and contacts");
  // 先模拟提交前各阶段失败，再模拟成功提交后回执丢失。
  for(String fault:List.of("contact-write","revoke","commit"))try(var f=new F()){
   f.request(true);String before=f.row().toString();f.fault=fault;error(503,()->f.change(true,f.delivered));Assert.assertEquals(f.row().toString(),before);Assert.assertEquals(rows(f.db,"SELECT userPhone FROM u WHERE oId='1'").getFirst().getString("userPhone"),"13800000001");Assert.assertEquals(rows(f.db,"SELECT COUNT(*) AS n FROM states").getFirst().getInt("n"),0);
  }
  try(var f=new F()){f.request(true);f.fault="ack";error(503,()->f.change(true,f.delivered));Assert.assertNull(f.row());Assert.assertEquals(rows(f.db,"SELECT userPhone FROM u WHERE oId='1'").getFirst().getString("userPhone"),PHONE);f.fault="";error(403,()->f.change(true,f.delivered));}
  System.out.println("PASS contact 5 actual SQL contact/revocation/commit failures roll back field+consumption+epoch, lost acknowledgement leaves committed change and revoked old access");
  try(var f=new F()){
   f.captcha=false;error(422,()->f.request(true));Assert.assertEquals(f.sent,0);f.captcha=true;f.quota=false;error(429,()->f.request(true));f.quota=true;f.allowed=false;int roots=f.roots;error(429,()->f.request(false));Assert.assertEquals(f.roots,roots);
  }
  Assert.assertEquals(ApiV3ContactInputs.target(" New@Example.COM ",false),EMAIL);
  for(JSONObject bad:List.of(body(true,true,"").put("target","+8613900000001"),body(true,true,"").put("currentPassword","BAD"),body(true,true,"").put("captcha","{}"),body(false,true,"").put("target","a@example.com\nBcc:x@example.com"),body(true,false,"12345"),body(true,false,"123456").put("owner","2")))error(422,()->ApiV3ContactInputs.parse(bad,bad.getString("target").equals(EMAIL)?false:true,bad.has("captcha")));
  System.out.println("PASS contact 6 target/proof/body normalization, captcha/SMS/shared attempt gates before send, email lowercase and malformed address/header/code/owner rejection");
  for(String corrupt:List.of("codeHash=NULL","codeHash='bad'","previousContactHash='bad'","credentialEpoch='bad'"))try(var f=new F()){
   f.request(true);sql(f.db,"UPDATE contact SET "+corrupt);error(503,()->f.change(true,f.delivered));Assert.assertNotNull(f.row());
  }
  try(var f=new F()){f.request(true);sql(f.db,"UPDATE u SET userEmail='LEGACY@example.com' WHERE oId='1'");f.request(false);sql(f.db,"UPDATE u SET userEmail='legacy@example.com' WHERE oId='1'");error(422,()->f.change(false,f.delivered));}
  System.out.println("PASS contact 8 malformed stored hashes/version fail closed, old email snapshot exact despite normalized target identity, post-provider root-begin failure sanitized");
  for(String timeChange:List.of("expiresAt=expiresAt+1","createdAt=createdAt+600001,expiresAt=expiresAt+600001"))try(var f=new F()){
   f.request(true);sql(f.db,"UPDATE contact SET "+timeChange);error(422,()->f.change(true,f.delivered));Assert.assertNotNull(f.row());
  }
  try(var f=new F()){
   ApiV3ContactSettingsProcessor.registerRoutes(f.contactController,f.auth,f.writes);Dispatcher.mapping();for(String token:Arrays.asList(null,"bad","wrong"))Assert.assertEquals(f.http("/api/v3/me/settings/phone/verification",body(true,true,"").toString(),true,token).attr("test.status"),"wrong".equals(token)?403:401);
   for(boolean phone:List.of(true,false))Assert.assertEquals(f.http("/api/v3/me/settings/"+(phone?"phone":"email")+"/verification",body(phone,true,"").toString(),true,"owner").attr("test.status"),200);
   var changed=f.http("/api/v3/me/settings/email",body(false,false,f.delivered).toString(),false,"owner");Assert.assertEquals(changed.attr("test.status"),200);Assert.assertEquals(changed.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(f.http("/api/v3/me/settings/phone",body(true,false,"100001").toString(),false,"owner").attr("test.status"),401);
  }
  try(var f=new F()){ApiV3ContactSettingsProcessor.registerRoutes(f.contactController,f.auth,f.writes);Dispatcher.mapping();f.request(true);Assert.assertEquals(f.http("/api/v3/me/settings/phone",body(true,false,f.delivered).toString(),false,"owner").attr("test.status"),200);}
  System.out.println("PASS contact 7 all four real Dispatcher/auth/write/controller routes, Cookie/query identity rejected, current old access revoked and no-store success");
 }
}
