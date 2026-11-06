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

import org.b3log.latke.model.User;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.processor.RhypicProcessor;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import io.netty.handler.codec.http.*;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;

public class ApiV3UploadTicketServiceTest {
    static final Map<String,JSONObject> USERS=new HashMap<>();
    static boolean lookupFailure;
    public static List<JSONObject> lookup(String sql,Object...args)throws org.b3log.latke.repository.RepositoryException {
        Assert.assertEquals(sql,"SELECT oId,userName,userNickname FROM `symphony_user` WHERE oId=? AND userStatus=? LIMIT 1");
        Assert.assertEquals(args.length,2);Assert.assertEquals(args[1],0);
        if(lookupFailure)throw new org.b3log.latke.repository.RepositoryException("injected read failure");
        var user=USERS.get(args[0]);return user==null||user.getInt("userStatus")!=0?List.of():List.of(user);
    }
    static JSONObject payload(String ticket,PublicKey key)throws Exception {
        String[] parts=ticket.split("\\.");Assert.assertEquals(parts.length,3);
        var header=new JSONObject(new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8));
        Assert.assertEquals(header.toString(),new JSONObject().put("alg","EdDSA").put("typ","JWT").toString());
        var signature=Signature.getInstance("Ed25519");signature.initVerify(key);signature.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.UTF_8));Assert.assertTrue(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
        Assert.assertFalse(ticket.contains("="));return new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8));
    }
    static void status(int status,Throwing action)throws Exception{boolean failed=false;try{action.run();}catch(ApiV3Exception e){failed=true;Assert.assertEquals(e.status,status);}Assert.assertTrue(failed);}
    interface Throwing{void run()throws Exception;}
    // Only the isolated runner's mutable configuration adapter is supported here.
    static void config(String key,Object value)throws Exception{Symphonys.class.getField(key).set(null,value);}
    public static void main(String[] args)throws Exception {
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();String privateKey=Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());config("RHPIC_TICKET_PRIVATE_KEY",privateKey);
        var raw=RhypicUploadTickets.sign(privateKey,"123","Sample-Name","昵称\"<>\n",1000);
        var claims=payload(raw,pair.getPublic());Assert.assertEquals(claims.keySet(),Set.of("iss","aud","sub","scope","name","nickname","iat","exp","jti"));
        Assert.assertEquals(claims.getString("iss"),"rhythm");Assert.assertEquals(claims.getString("aud"),"rhypic");Assert.assertEquals(claims.getString("scope"),"file:upload");Assert.assertEquals(claims.getLong("exp"),1180L);Assert.assertEquals(claims.getString("nickname"),"昵称\"<>\n");Assert.assertTrue(claims.getString("jti").matches("[a-f0-9]{32}"));
        Assert.assertNotEquals(payload(RhypicUploadTickets.sign(privateKey,"123","Sample-Name","",1000),pair.getPublic()).getString("jti"),claims.getString("jti"));
        System.out.println("PASS Ed25519 signature, strict claims, exact TTL, unique grant IDs and Unicode JSON roundtrip");
        for(String base:List.of("https://pic.example.test","https://pic.example.test/","https://pic.example.test/base/","http://localhost:8090","http://127.0.0.1:8090","http://[::1]:8090"))Assert.assertTrue(ApiV3UploadTargets.endpoint(base).endsWith("/api/v1/files"));
        for(String base:List.of("http://public.example.test","https://user:secret@pic.example.test","https://pic.example.test/?key=private","https://pic.example.test/#private","//pic.example.test","javascript:alert(1)","https://pic.example.test/a/../b","https://pic.example.test/%2e%2e/","https://pic.example.test//b","https://pic.example.test:0","https://pic.example.test:65536"," https://pic.example.test","https://pic.example.test\n")){
            boolean failed=false;try{ApiV3UploadTargets.endpoint(base);}catch(IllegalArgumentException e){failed=true;}Assert.assertTrue(failed,base);
        }
        System.out.println("PASS public upload endpoint validation and loopback development exceptions reject credentials, query, fragments and ambiguous paths");
        var service=new ApiV3UploadTicketService();var field=ApiV3UploadTicketService.class.getDeclaredField("users");field.setAccessible(true);field.set(service,new UserRepository());
        USERS.put("123",new JSONObject().put("oId","123").put("userStatus",0).put("userName","Current-Name").put("userNickname","昵称").put("userEmail","PRIVATE-email").put("apiKey","PRIVATE-key"));
        USERS.put("456",new JSONObject().put("oId","456").put("userStatus",0).put("userName","Other-Name"));
        var first=service.issue("123");Assert.assertEquals(first.keySet(),Set.of("ticket","tokenType","uploadURL","fieldName","expiresIn"));Assert.assertEquals(first.getString("uploadURL"),"https://pic.example.test/api/v1/files");Assert.assertEquals(first.getString("fieldName"),"file");Assert.assertEquals(first.getString("tokenType"),"Bearer");Assert.assertEquals(first.getLong("expiresIn"),180L);
        var firstClaims=payload(first.getString("ticket"),pair.getPublic());Assert.assertEquals(firstClaims.getString("sub"),"123");Assert.assertEquals(firstClaims.getString("name"),"Current-Name");Assert.assertFalse(firstClaims.toString().contains("PRIVATE"));
        Assert.assertEquals(payload(service.issue("456").getString("ticket"),pair.getPublic()).getString("sub"),"456");Assert.assertNotEquals(service.issue("123").getString("ticket"),first.getString("ticket"));
        Assert.assertTrue(Math.abs(firstClaims.getLong("iat")-System.currentTimeMillis()/1000)<5);
        System.out.println("PASS actual service binds current queried identity, emits only grant DTO, isolates actors and always issues fresh credentials without private fields");
        status(404,()->service.issue("missing"));status(404,()->service.issue("999"));USERS.get("123").put("userStatus",4);status(404,()->service.issue("123"));USERS.get("123").put("userStatus",0);
        config("RHPIC_ENABLED",false);status(503,()->service.issue("123"));config("RHPIC_ENABLED",true);
        config("RHPIC_UPLOAD_URL","https://secret@pic.example.test");status(503,()->service.issue("123"));config("RHPIC_UPLOAD_URL","https://pic.example.test");
        config("RHPIC_TICKET_PRIVATE_KEY","invalid");status(503,()->service.issue("123"));config("RHPIC_TICKET_PRIVATE_KEY",privateKey);
        lookupFailure=true;try{service.issue("123");Assert.fail();}catch(org.b3log.latke.repository.RepositoryException expected){}lookupFailure=false;
        System.out.println("PASS missing/inactive owners, disabled channel, bad configuration/key and lookup failures cannot issue a grant");
        var legacy=payload(RhypicProcessor.mintUploadTicket("123","Legacy-Name","旧昵称"),pair.getPublic());Assert.assertEquals(legacy.getString("sub"),"123");Assert.assertEquals(legacy.getString("name"),"Legacy-Name");Assert.assertEquals(legacy.getLong("exp")-legacy.getLong("iat"),180L);Assert.assertEquals(legacy.keySet(),firstClaims.keySet());
        System.out.println("PASS actual legacy mint helper retains claims, signature, names and 180-second lifetime with shared signer");
        for(String body:List.of("{\"userId\":\"456\"}","{\"uploadURL\":\"https://evil.test\"}","{\"scope\":\"admin\"}","{\"apiKey\":\"old\"}","{\"expiresIn\":999999}")){
            var http=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.POST,"/api/v3/uploads/tickets");http.headers().set("Content-Type","application/json");var request=new Request(null,http);http.content().clear().writeBytes(body.getBytes(StandardCharsets.UTF_8));request.setBytes(body.getBytes(StandardCharsets.UTF_8));
            var context=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));try{status(422,()->ApiV3WriteInputs.body(context,Set.of(),true));}finally{http.release();}
        }
        System.out.println("PASS real Latke request parser rejects caller identity, target, scope, credentials and lifetime fields");
        // Execute the real policy UPDATE predicates, adapting only MySQL CONCAT to SQLite concatenation.
        final String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java"));
        final var matcher=java.util.regex.Pattern.compile("DEFAULT_SCOPES = \"([^\"]+)\"").matcher(source);Assert.assertTrue(matcher.find());final String defaults=matcher.group(1);Assert.assertTrue(defaults.length()<=255);Assert.assertTrue(Arrays.asList(defaults.split(" ")).contains("uploads.write"));
        final String old="profile.read content.read activity.read content.write comments.write reactions.write follows.write";
        final String sql=java.nio.file.Files.readString(java.nio.file.Path.of("sql/20261016_api_v3.sql"));
        final var updates=java.util.regex.Pattern.compile("UPDATE `symphony_api_v3_client`[\\s\\S]*?;").matcher(sql);final var commands=new ArrayList<String>();
        while(updates.find())commands.add(updates.group().replaceAll("CONCAT\\(`scope`, '([^']*)'\\)","(`scope` || '$1')"));Assert.assertEquals(commands.size(),6);
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")){
            try(var s=db.createStatement()){s.execute("CREATE TABLE symphony_api_v3_client(clientId TEXT,scope TEXT)");}
            for(String[] row:List.of(new String[]{"website_v3",old},new String[]{"website_v3",defaults.replace(" uploads.write","").replace(" messages.read","").replace(" messages.write","")},new String[]{"website_v3","profile.read custom.write"},new String[]{"third_party",old})){
                try(var s=db.prepareStatement("INSERT INTO symphony_api_v3_client VALUES(?,?)")){s.setString(1,row[0]);s.setString(2,row[1]);s.executeUpdate();}
            }
            for(int round=0;round<2;round++)for(String command:commands)try(var s=db.createStatement()){s.executeUpdate(command);}
            try(var s=db.createStatement();var r=s.executeQuery("SELECT scope FROM symphony_api_v3_client ORDER BY rowid")){
                for(String expected:List.of(defaults,defaults,"profile.read custom.write",old)){Assert.assertTrue(r.next());Assert.assertEquals(r.getString(1),expected);}
            }
        }
        System.out.println("PASS real default-policy migration predicates add uploads.write once, preserve customized/other clients and match current issuing policy");
    }
}
