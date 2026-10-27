package org.b3log.symphony.util;
import org.b3log.latke.http.Cookie;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.util.Crypts;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.service.UserMgmtService;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;

/** Current SQL version guard and AES payloads; cache/IoC only are explicit fixture adapters. */
public class OAuthCookieTest {
 static final String PASSWORD="a".repeat(32);
 static class F implements AutoCloseable {
  final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");final Map<String,JSONObject> cache=new HashMap<>();int sqlReads;boolean fail;
  F()throws Exception{
   sql("CREATE TABLE u(oId TEXT PRIMARY KEY,userPassword TEXT,userStatus INT)");sql("INSERT INTO u VALUES('1',?,0),('2',?,0)",PASSWORD,PASSWORD);sql("CREATE TABLE credentials(oId TEXT PRIMARY KEY,credentialEpoch INT)");
   cache.put("1",new JSONObject().put("oId","1").put("userPassword",PASSWORD).put("userStatus",0));cache.put("2",new JSONObject(cache.get("1").toString()).put("oId","2"));
   var users=new UserRepository(){public String getName(){return "u";}public JSONObject get(String id){JSONObject user=cache.get(id);return user==null?null:new JSONObject(user.toString());}};
   var states=new AccountCredentialStateRepository(){public String getName(){return "credentials";}public boolean hasTransactionBegun(){return false;}public List<JSONObject> select(String q,Object...args)throws RepositoryException{sqlReads++;Assert.assertEquals(q,"SELECT u.oId,u.userStatus,u.userPassword,COALESCE(s.credentialEpoch,0) AS credentialEpoch FROM `u` u LEFT JOIN `credentials` s ON s.oId=u.oId WHERE u.oId=?");if(fail)throw new RepositoryException("PRIVATE_SQL");try(var p=db.prepareStatement(q)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){var list=new ArrayList<JSONObject>();while(r.next())list.add(new JSONObject().put("oId",r.getString(1)).put("userStatus",r.getInt(2)).put("userPassword",r.getString(3)).put("credentialEpoch",r.getLong(4)));return list;}}catch(SQLException e){throw new RepositoryException(e);}}};
   var field=AccountCredentialStateRepository.class.getDeclaredField("users");field.setAccessible(true);field.set(states,users);BeanManager.put(AccountCredentialStateRepository.class,states);BeanManager.put(UserRepository.class,users);BeanManager.put(UserMgmtService.class,new UserMgmtService());Sessions.SESSION_CACHE.clear();Sessions.SESSION_CACHE.putAll(cache);AccountCredentialVersions.endHttp();
  }
  void sql(String q,Object...args)throws SQLException{try(var p=db.prepareStatement(q)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();}}
  String credential(String actor,boolean cookie,Long epoch)throws Exception{var value=new JSONObject().put("oId",actor).put("token",PASSWORD+":"+"r".repeat(16));if(cookie)value.put("rememberLogin",true);if(epoch!=null)value.put("credentialEpoch",epoch);return Crypts.encryptByAES(value.toString(),cookie?Symphonys.COOKIE_SECRET:Symphonys.COOKIE_SECRET_API);}
  Request request(String cookie,String query,String body,DefaultFullHttpRequest raw){var r=new Request(null,raw){public Set<Cookie> getCookies(){return cookie==null?Set.of():Set.of(new Cookie(Sessions.COOKIE_NAME,cookie));}public String getRemoteAddr(){return "192.0.2.1";}};r.setParameter("apiKey",query==null?"":query);r.setJSON(body==null?new JSONObject():new JSONObject().put("apiKey",body));return r;}
  public void close()throws Exception{AccountCredentialVersions.endHttp();Sessions.current=null;Sessions.SESSION_CACHE.clear();db.close();}
 }
 static DefaultFullHttpRequest raw(){return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/oauth/authorize");}
 public static void main(String[]args)throws Exception{
  var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
  try(var f=new F()){
   var raw=raw();try{String b=f.credential("2",false,null);var req=f.request(null,b,null,raw);Assert.assertEquals(Sessions.currentUser(req).getString("oId"),"2");Assert.assertNull(Sessions.currentCookieUser(req));Assert.assertNull(AccountCredentialVersions.saved());
    String a=f.credential("1",true,null);req=f.request(a,b,null,raw);Assert.assertEquals(Sessions.currentUser(req).getString("oId"),"2");Assert.assertEquals(Sessions.currentCookieUser(req).getString("oId"),"1");Assert.assertEquals(AccountCredentialVersions.saved().actor(),"1");
    req=f.request(a,null,b,raw);Assert.assertEquals(Sessions.currentUser(req).getString("oId"),"2");Assert.assertEquals(Sessions.currentCookieUser(req).getString("oId"),"1");Sessions.current=f.cache.get("2");String cookieCsrf=Sessions.getCSRFTokenForUser(Sessions.currentCookieUser(req)),promoted=Sessions.getCSRFToken(null);Assert.assertNotEquals(cookieCsrf,promoted);Assert.assertEquals(Sessions.getCSRFTokenForUser(f.cache.get("1")),cookieCsrf);Assert.assertEquals(Sessions.getCSRFTokenForUser(null),"");Assert.assertFalse(Sessions.currentCookieUser(req).has("credentialEpoch"));
   }finally{raw.release();}
  }System.out.println("PASS cookie 1 real AES legacy query/body apiKey resolves its owner while OAuth Cookie-only resolution and explicit CSRF remain bound to the actual Cookie user, no private epoch DTO");
  try(var f=new F()){
   String a=f.credential("1",true,null),key=f.credential("1",false,null);var raw=raw();try{var req=f.request(a,null,null,raw);Assert.assertNotNull(Sessions.currentCookieUser(req));int reads=f.sqlReads;f.sql("INSERT INTO credentials VALUES('1',1)");Assert.assertEquals(Sessions.SESSION_CACHE.get("1").getInt("userStatus"),0);Assert.assertEquals(Sessions.SESSION_CACHE.get("1").getString("userPassword"),PASSWORD);Assert.assertNull(Sessions.currentCookieUser(req));Assert.assertTrue(f.sqlReads>reads);Assert.assertNull(AccountCredentialVersions.saved());try{ApiProcessor.getUserByKey(key);Assert.fail();}catch(NullPointerException expected){}
    Assert.assertNull(Sessions.currentCookieUser(f.request(f.credential("1",true,0L),null,null,raw)));Assert.assertNotNull(Sessions.currentCookieUser(f.request(f.credential("1",true,1L),null,null,raw)));Assert.assertEquals(AccountCredentialVersions.saved().epoch(),1L);
    Assert.assertNotNull(ApiProcessor.getUserByKey(f.credential("1",false,1L)));
   }finally{raw.release();}
  }System.out.println("PASS cookie 2 actual version SQL invalidates stale cached Cookie/API key after epoch increment; missing/zero epoch works only at zero, current explicit epoch works");
  try(var f=new F()){
   var raw=raw();try{String a=f.credential("1",true,null);var req=f.request(a,null,null,raw);Sessions.SESSION_CACHE.remove("1");Assert.assertNotNull(Sessions.currentCookieUser(req));f.sql("UPDATE u SET userPassword=? WHERE oId='1'","b".repeat(32));Assert.assertNull(Sessions.currentCookieUser(req));f.sql("UPDATE u SET userPassword=?,userStatus=4 WHERE oId='1'",PASSWORD);Assert.assertNull(Sessions.currentCookieUser(req));f.sql("UPDATE u SET userStatus=0 WHERE oId='1'");f.fail=true;Assert.assertNull(Sessions.currentCookieUser(req));Assert.assertNull(AccountCredentialVersions.saved());
    f.fail=false;Assert.assertNull(Sessions.currentCookieUser(f.request("bad",null,null,raw)));Assert.assertNull(Sessions.currentCookieUser(f.request("A".repeat(224),null,null,raw)));Assert.assertNull(AccountCredentialVersions.saved());
   }finally{raw.release();}
  }System.out.println("PASS cookie 3 actual cache-miss Cookie branch still checks fresh SQL password/status, database errors and malformed AES fail closed and clear authentication proof");
 }
}
