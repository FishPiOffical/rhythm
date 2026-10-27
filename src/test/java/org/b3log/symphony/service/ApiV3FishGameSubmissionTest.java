package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/** Real SQL, legacy review persistence, strict roots and routed Bearer; SQLite adapter cannot prove MySQL row locking. */
public class ApiV3FishGameSubmissionTest extends ApiV3ProfessionPrimaryTest {
    static void inject(Object target,String name,Object value)throws Exception{Class<?> t=target.getClass();while(t!=null){try{var f=t.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){t=t.getSuperclass();}}throw new NoSuchFieldException(name);}
    static String login(String target,String realm,String scopes) {
        return "https://fishpi.cn/openid/login?openid.ns="+enc("http://specs.openid.net/auth/2.0")+"&openid.mode=checkid_setup&openid.identity="+enc("http://specs.openid.net/auth/2.0/identifier_select")
                +"&openid.claimed_id="+enc("http://specs.openid.net/auth/2.0/identifier_select")+"&openid.return_to="+enc(target)+"&openid.realm="+enc(realm)+(scopes==null?"":"&fishpi.scope="+enc(scopes));
    }
    static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static JSONObject input(String path){return new JSONObject().put("name"," <b>游戏</b> ").put("description","说明").put("url","https://game.example/"+path).put("iconUrl","https://images.example/icon.png").put("oauthLoginUrl",login("https://game.example/callback?state=local","https://game.example",null));}
    static class GameFixture extends Fixture {
        boolean quota=true;int quotaCalls;String localProvider="";
        final ApiV3FishGameSubmissionService submission=new ApiV3FishGameSubmissionService(){protected long now(){return 2000000000000L;}protected boolean allowed(String a){quotaCalls++;return quota;}protected String developmentProvider(){return localProvider;}};
        final ApiV3FishGameSubmissionProcessor endpoint=new ApiV3FishGameSubmissionProcessor();
        final FishGameMgmtService legacy=new FishGameMgmtService();
        GameFixture()throws Exception {
            sql(observer,"CREATE TABLE namespaces(oId TEXT PRIMARY KEY)");sql(observer,"INSERT INTO namespaces VALUES(?)",ApiV3FishGameSubmissionService.NAMESPACE);
            sql(observer,"CREATE TABLE requests(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt INT)");
            sql(observer,"CREATE TABLE games(oId TEXT PRIMARY KEY,fishGameName TEXT,fishGameDescription TEXT,fishGameUrl TEXT UNIQUE,fishGameIconUrl TEXT,fishGameOauthLoginUrl TEXT DEFAULT '',fishGamePendingOauthLoginUrl TEXT DEFAULT '',fishGameAuthorId TEXT,fishGameStatus INT,fishGameEditPending INT,fishGamePendingName TEXT,fishGamePendingDescription TEXT,fishGamePendingUrl TEXT,fishGamePendingIconUrl TEXT,fishGameLikeCount INT,fishGameDislikeCount INT,fishGameCreatedTime INT,fishGameUpdatedTime INT)");
            var userRows=new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("owner",q,a);}};
            var gameRows=new FishGameRepository(){
                public String getName(){return "games";}public Transaction beginTransaction(){return begin();}public boolean hasTransactionBegun(){return active();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("game",q,a);}
                public JSONObject get(String id)throws RepositoryException {var r=Fixture.rows(active()?writer:observer,"SELECT * FROM games WHERE oId=?",id);return r.isEmpty()?null:r.getFirst();}
                public JSONObject getByUrl(String url)throws RepositoryException {var r=Fixture.rows(active()?writer:observer,"SELECT * FROM games WHERE fishGameUrl=?",url);return r.isEmpty()?null:r.getFirst();}
                public String add(JSONObject row)throws RepositoryException {insert(row);if(fault.equals("game-write"))throw new RepositoryException("PRIVATE_GAME");return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException {String[] names=fields.length==0?row.keySet().stream().filter(x->!"oId".equals(x)).toArray(String[]::new):fields;var a=new ArrayList<Object>();for(String name:names)a.add(row.get(name));a.add(id);sql(writer,"UPDATE games SET "+String.join(",",Arrays.stream(names).map(x->x+"=?").toList())+" WHERE oId=?",a.toArray());}
            };
            var receipts=new ApiV3RequestRepository(){
                public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}
                public JSONObject getForUpdate(String id)throws RepositoryException{var r=query("receipt","SELECT * FROM requests WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public String add(JSONObject row)throws RepositoryException {var columns=new ArrayList<>(row.keySet());sql(writer,"INSERT INTO requests("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(row::get).toArray());return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{sql(writer,"UPDATE requests SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);if(fault.equals("receipt-write"))throw new RepositoryException("PRIVATE_RECEIPT");}
            };
            inject(submission,"users",userRows);inject(submission,"games",gameRows);inject(submission,"requests",receipts);
            inject(submission,"options",new OptionRepository(){public String getName(){return "namespaces";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query("namespace",q,a);}});
            inject(endpoint,"service",submission);inject(legacy,"fishGameRepository",gameRows);inject(legacy,"fishGameValidationService",new FishGameValidationService());
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){return Set.of("writer","reader","other","disabled").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":token.equals("disabled")?"4":actor)).put("scope",token.equals("reader")?"content.read":"content.write"):null;}});
        }
        boolean active(){return delegate!=null&&delegate.isActive();}
        List<JSONObject> query(String stage,String q,Object...a)throws RepositoryException {Assert.assertTrue(active());Assert.assertTrue(q.endsWith(" FOR UPDATE"));locks.add(stage);if(fault.equals(stage))throw new RepositoryException("PRIVATE_READ");return Fixture.rows(writer,q,a);}
        void insert(JSONObject row)throws RepositoryException {var columns=new ArrayList<>(row.keySet());sql(writer,"INSERT INTO games("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(row::get).toArray());}
        JSONObject save(String id,JSONObject body,String key)throws Exception{return submission.submit(actor,id,body,key);}
        JSONObject row(String id)throws RepositoryException{return Fixture.rows(observer,"SELECT * FROM games WHERE oId=?",id).getFirst();}
        long count(String table)throws RepositoryException{return scalar("SELECT COUNT(*) AS value FROM "+table);}
        RequestContext post(String token,String key,String body,String id,boolean route)throws Exception {
            String path="/api/v3/fish-games"+(id==null?"":"/"+id);
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,id==null?io.netty.handler.codec.http.HttpMethod.POST:io.netty.handler.codec.http.HttpMethod.PUT,path+"?apiKey=legacy",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try {raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","127.0.0.1");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy")));var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(ctx->{if(id==null)endpoint.submit(ctx);else endpoint.edit(ctx);});c.handle();}
                else{c.addHandler(writes::handle);c.addHandler(endpoint::submit);auth.contentWrite(c);}return c;
            }finally{raw.release();}
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new GameFixture()) {
            FishGameSubmissionRouteBindings.register(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            var c=f.post("writer","submit",input("play").toString(),null,true);Assert.assertEquals(c.attr("test.status"),200);
            var result=(JSONObject)c.attr("test.data");Assert.assertEquals(result.keySet(),Set.of("id","status","editPending"));var row=f.row(result.getString("id"));Assert.assertEquals(row.getString(FishGame.NAME),"游戏");Assert.assertEquals(row.getInt(FishGame.STATUS),0);Assert.assertEquals(row.getString(FishGame.OAUTH_LOGIN_URL),input("play").getString("oauthLoginUrl"));
            Assert.assertEquals(f.locks.subList(0,3),List.of("namespace","owner","receipt"));f.quota=false;Assert.assertEquals(f.save(null,input("play"),"submit").toString(),result.toString());Assert.assertEquals(f.count("games"),1);Assert.assertEquals(f.quotaCalls,1);error(409,()->f.save(null,input("changed"),"submit"));
            f.quota=true;sql(f.observer,"UPDATE games SET fishGameStatus=1 WHERE oId=?",result.getString("id"));
            Assert.assertEquals(f.post("writer","routed-edit",input("route-edit").toString(),result.getString("id"),true).attr("test.status"),200);
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);error(404,()->f.save(null,input("play"),"submit"));
        }
        System.out.println("PASS 1 actual routes/Bearer, normalized pending row+OAuth+receipt commit, namespace/actor locks, current actor replay and key conflicts");
        for(String scopes:Arrays.asList(null,""," ","profile.read","points.read","profile.detail.read, membership.read","articles.read points.read","  points.read, membership.read  "))ApiV3FishGameSubmissionInputs.parse(input("ok").put("oauthLoginUrl",login("https://game.example:443/callback?state=abc","https://game.example:443",scopes)),"");
        var local=input("local").put("oauthLoginUrl",login("https://game.example/callback","https://game.example",null).replace("https://fishpi.cn/openid/login","http://localhost:8080/openid/login"));ApiV3FishGameSubmissionInputs.parse(local,"http://localhost:8080/openid/login");error(422,()->ApiV3FishGameSubmissionInputs.parse(local,""));
        String bounded=login("https://game.example/callback","https://game.example","profile.read");bounded+="+".repeat(4096-bounded.length());ApiV3FishGameSubmissionInputs.parse(input("limit").put("oauthLoginUrl",bounded),"");
        final String tooLong=bounded+"+";error(422,()->ApiV3FishGameSubmissionInputs.parse(input("limit").put("oauthLoginUrl",tooLong),""));
        String callbackPrefix="https://game.example/callback?state=";
        ApiV3FishGameSubmissionInputs.parse(input("limit").put("name","n".repeat(80)).put("description","d".repeat(1000)).put("oauthLoginUrl",login(callbackPrefix+"a".repeat(2048-callbackPrefix.length()),"https://game.example",null)),"");
        error(422,()->ApiV3FishGameSubmissionInputs.parse(input("limit").put("name","n".repeat(81)),""));
        error(422,()->ApiV3FishGameSubmissionInputs.parse(input("limit").put("oauthLoginUrl",login(callbackPrefix+"a".repeat(2049-callbackPrefix.length()),"https://game.example",null)),""));
        System.out.println("PASS 2 documented scopes including explicit non-profile scope, default profile and exact development provider; callback state and default HTTPS port");
        var base=input("bad");String good=base.getString("oauthLoginUrl");
        for(String url:List.of(good.replace("fishpi.cn/","fishpi.cn.evil/"),good.replace("fishpi.cn/","evil@fishpi.cn/"),good+"#fragment",good+"&openid.mode=checkid_setup",good+"&fishpi.extra=true",good.replace("checkid_setup","check_authentication"),good.replace(enc("identifier_select"),enc("other")),login("https://game.example.evil/cb","https://game.example.evil",null),login("https://game.example:444/cb","https://game.example:444",null),login("https://evil@game.example/cb","https://game.example",null),login("https://game.example/cb#secret","https://game.example",null),login("http://game.example/cb","http://game.example",null),login("https://game.example/cb?access_token=secret","https://game.example",null),login("https://game.example/cb","https://game.example.evil",null),login("https://game.example/cb","https://game.example","unknown.read")))error(422,()->ApiV3FishGameSubmissionInputs.parse(input("bad").put("oauthLoginUrl",url),""));
        System.out.println("PASS 3 trusted exact endpoint/fixed protocol/unique known parameters, same-origin callbacks/realm and credential/fragment/userinfo/scope rejection");
        try(var f=new GameFixture()) {
            String id=f.save(null,input("old"),"first").getString("id");error(409,()->f.save(id,input("new"),"pending-edit"));
            sql(f.observer,"UPDATE games SET fishGameStatus=1 WHERE oId=?",id);
            var edited=f.save(id,input("new"),"edit");Assert.assertTrue(edited.getBoolean("editPending"));var row=f.row(id);Assert.assertEquals(row.getString(FishGame.URL),"https://game.example/old");Assert.assertEquals(row.getString(FishGame.PENDING_URL),"https://game.example/new");Assert.assertEquals(row.getString(FishGame.PENDING_OAUTH_LOGIN_URL),input("new").getString("oauthLoginUrl"));error(409,()->f.save(id,input("third"),"again"));
            f.legacy.review(id,FishGame.STATUS_APPROVED,null);row=f.row(id);Assert.assertEquals(row.getString(FishGame.URL),"https://game.example/new");Assert.assertEquals(row.getString(FishGame.OAUTH_LOGIN_URL),input("new").getString("oauthLoginUrl"));Assert.assertEquals(row.getString(FishGame.PENDING_OAUTH_LOGIN_URL),"");Assert.assertEquals(row.getInt(FishGame.EDIT_PENDING),0);
            f.save(id,input("rejected"),"reject");f.legacy.review(id,FishGame.STATUS_REJECTED,null);row=f.row(id);Assert.assertEquals(row.getInt(FishGame.STATUS),1);Assert.assertEquals(row.getString(FishGame.URL),"https://game.example/new");Assert.assertEquals(row.getString(FishGame.PENDING_OAUTH_LOGIN_URL),"");
            var manual=new JSONObject().put(FishGame.NAME,"管理员名称").put(FishGame.DESCRIPTION,"说明").put(FishGame.URL,"https://game.example/admin-change").put(FishGame.ICON_URL,"https://images.example/a.png");f.legacy.updateByAdmin(id,manual);Assert.assertEquals(f.row(id).getString(FishGame.OAUTH_LOGIN_URL),"");
            sql(f.observer,"UPDATE games SET fishGameOauthLoginUrl=? WHERE oId=?",input("new").getString("oauthLoginUrl"),id);
            f.legacy.updateByAdmin(id,manual);Assert.assertEquals(f.row(id).getString(FishGame.OAUTH_LOGIN_URL),input("new").getString("oauthLoginUrl"));
            manual.put(FishGame.URL,"https://game.example/review-manual");f.legacy.review(id,FishGame.STATUS_APPROVED,manual);Assert.assertEquals(f.row(id).getString(FishGame.OAUTH_LOGIN_URL),"");
        }
        System.out.println("PASS 4 edit never publishes before review, real legacy approval promotes OAuth with URL, rejection retains published content, manual URL change clears stale OAuth");
        try(var f=new GameFixture()) {
            String id=f.save(null,input("unique"),"first").getString("id");error(409,()->f.save(null,input("unique"),"duplicate"));error(404,()->f.submission.submit("2",id,input("other"),"owner"));sql(f.observer,"UPDATE games SET fishGameStatus=1 WHERE oId=?",id);f.save(id,input("reserved"),"reserve");error(409,()->f.save(null,input("reserved"),"new-reserved"));Assert.assertEquals(f.count("games"),1);Assert.assertEquals(f.count("requests"),2);
        }
        System.out.println("PASS 5 owner/status checks and current/pending URL conflicts reject without publishing or orphan receipts");
        for(String fault:List.of("namespace","owner","receipt","game","game-write","receipt-write","commit"))try(var f=new GameFixture()) {f.fault=fault;error(503,()->f.save(null,input("fault"),"fault"));Assert.assertEquals(f.count("games"),0);Assert.assertEquals(f.count("requests"),0);f.fault="";f.save(null,input("fault"),"fault");Assert.assertEquals(f.count("games"),1);}
        try(var f=new GameFixture()){f.fault="lost-ack";error(503,()->f.save(null,input("lost"),"lost"));Assert.assertEquals(f.count("games"),1);f.fault="";f.save(null,input("lost"),"lost");Assert.assertEquals(f.count("games"),1);Assert.assertEquals(f.count("requests"),1);}
        for(String fault:List.of("receipt-write","commit"))try(var f=new GameFixture()) {
            String id=f.save(null,input("stable"),"stable").getString("id");sql(f.observer,"UPDATE games SET fishGameStatus=1 WHERE oId=?",id);f.fault=fault;error(503,()->f.save(id,input("failed-edit"),"failed-edit"));
            Assert.assertEquals(f.row(id).getInt(FishGame.EDIT_PENDING),0);Assert.assertEquals(f.row(id).getString(FishGame.PENDING_OAUTH_LOGIN_URL),"");Assert.assertEquals(f.count("requests"),1);
        }
        System.out.println("PASS 6 SQL/receipt/swallowed commit faults roll back whole write, lost commit acknowledgement recovers same pending row");
        try(var f=new GameFixture()) {
            for(String token:Arrays.asList(null,"invalid","reader","disabled"))Assert.assertEquals(f.post(token,"auth",input("auth").toString(),null,false).attr("test.status"),token==null||token.equals("invalid")?401:token.equals("reader")?403:404);
            Assert.assertEquals(f.post("writer",null,input("key").toString(),null,false).attr("test.status"),422);Assert.assertEquals(f.post("writer","large"," ".repeat(16385),null,false).attr("test.status"),413);
            for(JSONObject body:List.of(input("bad").put("fromId","2"),input("bad").put("name"," "),input("bad").put("description","a".repeat(1001)),input("bad").put("url","https://game.example/#x"),input("bad").put("oauthLoginUrl","a".repeat(4097))))Assert.assertEquals(f.post("writer","bad",body.toString(),null,false).attr("test.status"),422);
            f.quota=false;error(429,()->f.save(null,input("quota"),"quota"));Assert.assertEquals(f.count("games"),0);Assert.assertEquals(f.count("requests"),0);
        }
        System.out.println("PASS 7 wrong/no Bearer scope, spoofed Cookie/apiKey, body/key/field/length rules and quota reject before persistent effects");
        System.out.println("Fish game submission isolated tests passed: 7 groups");
    }
}
