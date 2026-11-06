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

import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.sql.*;
import java.util.*;

public class ApiV3CurrentContentTest {
    private static Connection db;
    private static final Map<String,Map<String,JSONObject>> cache=new HashMap<>();
    private static boolean queryFailure;
    public static JSONObject cached(String table,String id){return cache.getOrDefault(table,Map.of()).get(id);}
    public static JSONObject cachedByName(String table,String name){return cache.getOrDefault(table,Map.of()).values().stream().filter(v->name.equals(v.optString("userName"))).findFirst().orElse(null);}
    public static List<JSONObject> select(String sql,Object...args)throws RepositoryException{
        if(queryFailure)throw new RepositoryException("fixture SQL failure");
        try(var p=db.prepareStatement(sql)){
            for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);
            try(var r=p.executeQuery()){var out=new ArrayList<JSONObject>();var m=r.getMetaData();while(r.next()){var row=new JSONObject();for(int i=1;i<=m.getColumnCount();i++){var value=r.getObject(i);row.put(m.getColumnLabel(i),value==null?JSONObject.NULL:value);}out.add(row);}return out;}
        }catch(Exception e){throw new RepositoryException(e);}
    }
    private static void sql(String command)throws Exception{try(var p=db.createStatement()){p.execute(command);}db.commit();}
    private static void inject(Object target,String field,Object value)throws Exception{var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    private static void check(boolean value){if(!value)throw new AssertionError();}
    private static void missing(Runnable query){try{query.run();throw new AssertionError("expected hidden target");}catch(ApiV3Exception e){check(e.status==404);}}
    public static void main(String[] args)throws Exception{
        try(var connection=DriverManager.getConnection(System.getProperty("rhythm.content.mysqlUrl","jdbc:sqlite::memory:"))){
            db=connection;db.setAutoCommit(false);
            sql("CREATE TABLE users(oId VARCHAR(19) PRIMARY KEY,userStatus INTEGER,userName TEXT,userRole TEXT,userNickname TEXT,userAvatarURL TEXT)");
            sql("INSERT INTO users(oId,userStatus,userName,userRole,userNickname,userAvatarURL) VALUES('1',0,'author','defaultRole','Current Author','https://example.test/a.png'),('2',0,'reader','defaultRole','Reader','https://example.test/b.png'),('3',0,'commenter','defaultRole','Commenter','https://example.test/c.png')");
            sql("CREATE TABLE articles(oId VARCHAR(19) PRIMARY KEY,articleAuthorId TEXT,articleStatus INTEGER,articleType INTEGER,articleAnonymousView INTEGER,articleContent TEXT,articleTitle TEXT,articleAnonymous INTEGER,articleRewardPoint INTEGER,articleRewardContent TEXT)");
            sql("INSERT INTO articles VALUES('10','1',0,0,0,'CURRENT_BODY','Current Title',0,5,'PAID_RAW')");
            sql("ALTER TABLE articles ADD COLUMN articlePermalink TEXT");sql("UPDATE articles SET articlePermalink='https://site.test/article/10'");
            sql("CREATE TABLE comments(oId VARCHAR(19) PRIMARY KEY,commentAuthorId TEXT,commentOnArticleId TEXT,commentStatus INTEGER,commentVisible INTEGER,commentAnonymous INTEGER,commentType INTEGER,commentContent TEXT,commentScore REAL)");
            sql("INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentAnonymous,commentType,commentContent,commentScore) VALUES('30','3','10',0,0,0,0,'CURRENT_COMMENT',1)");
            sql("ALTER TABLE articles ADD COLUMN articleQnAOfferPoint INTEGER DEFAULT 0");
            sql("ALTER TABLE articles ADD COLUMN articlePerfect INTEGER DEFAULT 0");
            sql("ALTER TABLE articles ADD COLUMN articleCommentable CHAR(1) DEFAULT '1'");
            sql("ALTER TABLE articles ADD COLUMN articleStatement INTEGER DEFAULT 0");
            for(String field:List.of("articleTags"))sql("ALTER TABLE articles ADD COLUMN "+field+" TEXT");
            for(String field:List.of("articleCreateTime","articleUpdateTime","articleCommentCount","articleViewCount","articleStick","articleShowInList"))sql("ALTER TABLE articles ADD COLUMN "+field+" BIGINT DEFAULT 0");
            sql("ALTER TABLE comments ADD COLUMN commentQnAOffered INTEGER DEFAULT 0");
            sql("ALTER TABLE articles ADD COLUMN articleIP TEXT");sql("ALTER TABLE articles ADD COLUMN articleUA TEXT");sql("UPDATE articles SET articleIP='PRIVATE_IP',articleUA='PRIVATE_UA'");
            sql("ALTER TABLE users ADD COLUMN userPassword TEXT");sql("ALTER TABLE users ADD COLUMN userPhone TEXT");sql("UPDATE users SET userPassword='PRIVATE_PASSWORD',userPhone='PRIVATE_PHONE'");
            sql("ALTER TABLE comments ADD COLUMN commentIP TEXT");sql("ALTER TABLE comments ADD COLUMN commentUA TEXT");sql("UPDATE comments SET commentIP='PRIVATE_IP',commentUA='PRIVATE_UA'");
            for(String table:List.of("articles","comments","users")){var entries=new HashMap<String,JSONObject>();for(var row:select("SELECT * FROM "+table))entries.put(row.getString("oId"),new JSONObject(row.toString()));cache.put(table,entries);}
            cache.get("articles").get("10").put("articleContent","STALE_BODY");cache.get("users").get("1").put("userNickname","STALE_AUTHOR");
            var articles=new ArticleRepository();var users=new UserRepository();var comments=new CommentRepository();var content=new ApiV3ContentQueryService();var dto=new ApiV3ContentDtoService();
            inject(dto,"userRepository",users);inject(dto,"avatarQueryService",new AvatarQueryService());
            inject(content,"articleRepository",articles);inject(content,"commentRepository",comments);inject(content,"userRepository",users);inject(content,"contentDtoService",dto);inject(content,"rewardQueryService",new RewardQueryService());
            var viewer=new JSONObject().put("oId","2").put("userName","reader").put("userRole","adminRole");
            var current=content.viewableArticle("10",viewer);check(current!=null&&current.getString("articleContent").equals("CURRENT_BODY"));var detail=content.detail(current,viewer);check(detail.getString("content").equals("CURRENT_BODY"));check(detail.getJSONObject("author").getString("userNickname").equals("Current Author"));check(detail.getString("rewardContent").isEmpty());check(!detail.toString().contains("v3ContentAuthor")&&!detail.toString().contains("PRIVATE_"));
            System.out.println("PASS current content 1 body, authorization and public author snapshot ignore warm stale entity/user caches; paid source and internal fields remain hidden");
            sql("UPDATE articles SET articleStatus=1 WHERE oId='10'");check(content.viewableArticle("10",viewer)==null&&content.viewableArticle("10",null)==null);check(!content.paragraphTitleSnapshot().apply("10").publiclyReadable());
            sql("UPDATE articles SET articleStatus=0,articleAnonymousView=1 WHERE oId='10'");check(content.viewableArticle("10",null)==null&&content.viewableArticle("10",viewer)!=null);
            sql("UPDATE articles SET articleAnonymousView=0,articleType=1,articleContent='private @author' WHERE oId='10'");check(content.viewableArticle("10",viewer)==null);
            sql("UPDATE articles SET articleContent='invite @reader' WHERE oId='10'");check(content.viewableArticle("10",viewer)!=null);
            sql("UPDATE users SET userName='renamed' WHERE oId='2'");check(content.viewableArticle("10",viewer)==null);
            sql("UPDATE articles SET articleContent='invite @renamed' WHERE oId='10'");check(content.viewableArticle("10",viewer)!=null);sql("UPDATE users SET userName='reader' WHERE oId='2'");
            sql("UPDATE articles SET articleContent='no invitations' WHERE oId='10'");sql("UPDATE users SET userRole='adminRole' WHERE oId='2'");check(content.viewableArticle("10",viewer)!=null);sql("UPDATE users SET userRole='defaultRole' WHERE oId='2'");check(content.viewableArticle("10",viewer)==null);
            sql("UPDATE articles SET articleType=0,articleContent='CURRENT_BODY' WHERE oId='10'");
            System.out.println("PASS current content 2 current invalid/anonymous/discussion/invitation and exact reader role decisions cannot reuse stale grants");
            for(int status:new int[]{1,4}){sql("UPDATE users SET userStatus="+status+" WHERE oId='1'");check(content.viewableArticle("10",viewer)==null);check(!content.paragraphTitleSnapshot().apply("10").publiclyReadable());}sql("UPDATE users SET userStatus=0 WHERE oId='1'");
            sql("UPDATE users SET userStatus=4 WHERE oId='2'");check(content.viewableArticle("10",viewer)==null);sql("UPDATE users SET userStatus=0 WHERE oId='2'");
            check(content.viewableArticle("missing",viewer)==null);sql("DELETE FROM users WHERE oId='1'");check(content.viewableArticle("10",viewer)==null);sql("INSERT INTO users(oId,userStatus,userName,userRole,userNickname,userAvatarURL) VALUES('1',0,'author','defaultRole','Current Author','https://example.test/a.png')");
            sql("UPDATE articles SET articleStatus=2 WHERE oId='10'");check(content.viewableArticle("10",viewer)!=null);sql("UPDATE articles SET articleStatus=0 WHERE oId='10'");
            System.out.println("PASS current content 3 current missing/blocked authors and readers fail closed, paragraph title is hidden; locked readable article policy stays intact");
            sql("UPDATE comments SET commentVisible=1,commentContent='NEW_PRIVATE',commentAnonymous=1 WHERE oId='30'");var comment=content.currentComment("30");var article=content.viewableArticle("10",viewer);var hidden=content.commentDto(comment,article,viewer);check(!hidden.toString().contains("PRIVATE_"));check(!hidden.getBoolean("contentVisible")&&hidden.getString("content").isEmpty()&&hidden.getJSONObject("author").getString("id").isEmpty());
            var owner=new JSONObject().put("oId","1");check(content.commentDto(comment,article,owner).getString("content").equals("NEW_PRIVATE"));
            var page=content.comments(article,viewer,1,20,"oldest");check(page.getJSONArray("list").length()==1&&!page.getJSONArray("list").getJSONObject(0).getBoolean("contentVisible"));
            sql("UPDATE users SET userStatus=4 WHERE oId='3'");comment=content.currentComment("30");check(!content.commentDto(comment,article,owner).getBoolean("contentVisible"));sql("UPDATE users SET userStatus=0 WHERE oId='3'");
            System.out.println("PASS current content 4 current comment body/privacy/anonymity/author are projected without cached comment/user decisions; pagination keeps metadata-only private rows");
            var reactions=new ApiV3ReactionTargetService();var reactionRepo=new ReactionRepository();inject(reactions,"content",content);inject(reactions,"articles",articles);inject(reactions,"reactions",reactionRepo);inject(reactions,"chat",new ChatRoomRepository());
            missing(()->{try{reactions.read(viewer,"comment","30");}catch(RepositoryException e){throw new RuntimeException(e);}});check(reactions.read(owner,"comment","30")!=null);
            sql("UPDATE articles SET articleStatus=1 WHERE oId='10'");missing(()->{try{reactions.read(viewer,"article","10");}catch(RepositoryException e){throw new RuntimeException(e);}});sql("UPDATE articles SET articleStatus=0 WHERE oId='10'");
            check(reactions.validate(owner,"comment",content.currentComment("30"),true)!=null&&reactionRepo.locks==1);
            queryFailure=true;try{content.viewableArticle("10",viewer);throw new AssertionError();}catch(RepositoryException expected){}finally{queryFailure=false;}
            System.out.println("PASS current content 5 reaction read and locked-parent validation share current visibility; SQL failures propagate instead of granting cached access");
            sql("UPDATE articles SET articleAnonymous=1,articleTitle='NEW_TITLE',articleContent='NEW_BODY' WHERE oId='10'");
            detail=content.detail(content.viewableArticle("10",viewer),viewer);check(detail.getString("title").equals("NEW_TITLE")&&detail.getString("content").equals("NEW_BODY")&&detail.getJSONObject("author").getString("id").isEmpty());
            sql("DELETE FROM comments WHERE oId='30'");check(content.currentComment("30")==null);check(cached("comments","30")!=null);
            for(String bad:List.of("users; DROP TABLE articles","users`", "../users")){try{ApiV3ContentSnapshots.article("articles",bad,"comments");throw new AssertionError();}catch(IllegalArgumentException expected){}}
            System.out.println("PASS current content 6 current anonymity/title/body and physical comment deletion override stale copies; internal table names cannot become SQL input");
            sql("INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentAnonymous,commentType,commentContent,commentScore) VALUES('30','3','10',0,1,1,0,'NEW_PRIVATE',1)");
            var bridges=new CurrentCommentReadBridges(content,dto);check(bridges.comment("30",owner).getString("content").equals("NEW_PRIVATE"));check(!bridges.comment("30",viewer).getBoolean("contentVisible"));
            bridges.commentQueryService.href="<a href='https://site.test/article/999#30'>stale reference</a>";
            var notification=bridges.notification("30",viewer);check(notification.getBoolean("available"));check(notification.getJSONObject("data").getString("url").equals("https://site.test/article/10#30"));check(!notification.toString().contains("NEW_PRIVATE"));
            bridges.commentQueryService.href="<a href='https://site.test/article/10?p=2&amp;m=0#30'>current reference</a>";
            notification=bridges.notification("30",owner);check(notification.getJSONObject("data").getString("url").contains("?p=2&m=0#30"));
            sql("UPDATE comments SET commentOnArticleId='11' WHERE oId='30'");check(!bridges.notification("30",viewer).getBoolean("available"));
            sql("DELETE FROM comments WHERE oId='30'");check(!bridges.notification("30",viewer).getBoolean("available"));
            try{bridges.comment("30",viewer);throw new AssertionError();}catch(ApiV3Exception expected){check(expected.status==404);}
            System.out.println("PASS current content 7 actual normal-comment and notification read methods use current privacy/target/deletion, reject stale reference URLs and preserve valid paging links");
            var notifications=new NotificationRepository();var rewards=new RewardRepository();var notificationQuery=new ApiV3NotificationQueryService();
            inject(notificationQuery,"notificationRepository",notifications);inject(notificationQuery,"rewardRepository",rewards);
            inject(notificationQuery,"contentQueryService",content);inject(notificationQuery,"contentDtoService",dto);
            inject(notificationQuery,"notificationQueryService",new NotificationQueryService());inject(notificationQuery,"langPropsService",new org.b3log.latke.service.LangPropsService());
            inject(notificationQuery,"commentQueryService",new CommentQueryService());
            sql("UPDATE articles SET articleTitle='Current <img src=x> & title',articleAnonymous=0 WHERE oId='10'");
            sql("UPDATE users SET userName='current_sender' WHERE oId='3'");
            sql("INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentAnonymous,commentType,commentContent,commentScore) VALUES('30','3','10',0,1,1,0,'DO_NOT_INCLUDE_COMMENT_BODY',1)");
            for(int type:new int[]{7,8,12,15,22,33}){
                String id="900"+type;var row=new JSONObject().put("oId",id).put("userId","1").put("dataType",type).put("dataId",type==15||type==22?"10":"r"+type);
                notifications.rows.put(id,row);rewards.rows.put("r"+type,new JSONObject().put("dataId",type==8?"30":"10").put("senderId","3"));
                var result=notificationQuery.get(owner,id);check(result.getBoolean("contentAvailable"));check(result.getString("description").contains("Current <img src=x> & title"));
                check(!result.getString("descriptionHtml").contains("<img"));check(!result.toString().contains("DO_NOT_INCLUDE_COMMENT_BODY"));
                if(type==7||type==8||type==12)check(result.getString("description").contains("current_sender"));
            }
            sql("UPDATE articles SET articleStatus=1 WHERE oId='10'");check(!notificationQuery.get(owner,"90012").getBoolean("contentAvailable"));
            sql("UPDATE articles SET articleStatus=0 WHERE oId='10'");sql("DELETE FROM comments WHERE oId='30'");check(!notificationQuery.get(owner,"9008").getBoolean("contentAvailable"));
            check(cached("articles","10").getString("articleTitle").equals("Current Title"));
            System.out.println("PASS current content 8 complete notification query uses current authorized titles and sender names for six content categories, escapes title HTML and hides removed targets without legacy cache rendering");
            sql("INSERT INTO users(oId,userStatus,userName,userRole,userNickname,userAvatarURL,userPassword,userPhone) VALUES('4',0,'renamed_actor','defaultRole','Fresh Actor','https://example.test/avatar.png?sig=fresh&size=2','PRIVATE_ACTOR_PASSWORD','PRIVATE_ACTOR_PHONE')");
            cache.get("users").put("4",new JSONObject().put("oId","4").put("userStatus",0).put("userName","stale_actor").put("userNickname","STALE_ACTOR").put("userAvatarURL","https://example.test/stale.png").put("userPassword","PRIVATE_ACTOR_PASSWORD"));
            sql("INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentAnonymous,commentType,commentContent,commentScore) VALUES('40','2','10',0,0,0,0,'ACTOR_TEST_PUBLIC',0)");
            try {
                for(int type:new int[]{17,23,24,25,26,27,28}){
                    String id="910"+type;notifications.rows.put(id,new JSONObject().put("oId",id).put("userId","1").put("dataType",type).put("dataId",type==17?"4":(type==25||type==26?"40":"10")+"-4"));
                    var result=notificationQuery.get(owner,id);var actor=result.getJSONObject("actor");
                    check(actor.getString("userName").equals("renamed_actor"));check(actor.getString("userNickname").equals("Fresh Actor"));
                    check(actor.getString("userAvatarURL").equals("https://example.test/avatar.png?sig=fresh&size=2"));check(actor.keySet().equals(Set.of("id","userName","userNickname","userAvatarURL")));
                    check(!result.toString().contains("PRIVATE_")&&!result.toString().contains("stale_actor"));
                    if(type==17)check(result.getString("url").equals("/member/renamed_actor"));
                }
                sql("UPDATE users SET userName='renamed_again',userNickname='Actor Updated' WHERE oId='4'");
                for(int type:new int[]{17,23,24,25,26,27,28}){var result=notificationQuery.get(owner,"910"+type);check(result.getJSONObject("actor").getString("userName").equals("renamed_again"));check(result.getJSONObject("actor").getString("userNickname").equals("Actor Updated"));}
                System.out.println("PASS current notification actor 1 follower, follow/watch and four vote kinds use current names, nickname and signed avatar after cache warmup, with public whitelist only");
                final var chatService=new ChatRoomService();inject(notificationQuery,"chatRoomService",chatService);
                for(int type:new int[]{38,39})for(Object authorId:List.of("4",4L)){
                    String id="920"+type+(authorId instanceof String?"0":"1");
                    final var envelope=new JSONObject().put("userOId",authorId).put("userName","stale_actor").put("content",type==39?new JSONObject().put("msg","packet text").toString():"chat text");
                    chatService.rows.put(id,new JSONObject().put("content",envelope.toString()));
                    notifications.rows.put(id,new JSONObject().put("oId",id).put("userId","1").put("dataType",type).put("dataId",id));
                    var result=notificationQuery.get(owner,id);check(result.getJSONObject("actor").getString("userName").equals("renamed_again"));
                    check(result.getString("description").equals(type==39?"packet text":"chat text"));
                }
                System.out.println("PASS current notification actor 4 chat mentions and packet notices with persisted string/integer IDs use current identity instead of stale username aliases");
                int legacyIndex=0;
                for(int type:new int[]{38,39})for(Object missing:Arrays.asList(null,"",JSONObject.NULL,"invalid","0004")){
                    String id="930"+(++legacyIndex);var envelope=new JSONObject().put("userName","stale_actor")
                            .put("content",type==39?new JSONObject().put("msg","legacy packet text").toString():"legacy chat text");
                    if(missing!=null)envelope.put("userOId",missing);
                    chatService.rows.put(id,new JSONObject().put("content",envelope.toString()));
                    notifications.rows.put(id,new JSONObject().put("oId",id).put("userId","1").put("dataType",type).put("dataId",id));
                    var result=notificationQuery.get(owner,id);check(result.getBoolean("contentAvailable"));
                    check(!result.has("actor"));check(result.getString("url").equals("/cr"));
                    check(result.getString("description").equals(type==39?"legacy packet text":"legacy chat text"));
                }
                System.out.println("PASS current notification actor 5 missing/null/blank/invalid stable IDs never resolve a reusable username; current readable content and chat link remain");

                sql("DELETE FROM users WHERE oId='4'");
                var goneFollower=notificationQuery.get(owner,"91017");check(!goneFollower.getBoolean("contentAvailable")&&!goneFollower.has("actor")&&goneFollower.getString("url").isEmpty());
                for(int type:new int[]{23,24,25,26,27,28}){var result=notificationQuery.get(owner,"910"+type);check(result.getBoolean("contentAvailable")&&!result.has("actor"));}
                for(String id:List.of("920380","920381","920390","920391")){var result=notificationQuery.get(owner,id);check(result.getBoolean("contentAvailable")&&!result.has("actor"));}
                check(cached("users","4")!=null);
                System.out.println("PASS current notification actor 2 deleted cached follower becomes unavailable, while readable vote/follow targets retain content and omit missing actor");
                queryFailure=true;try{notificationQuery.get(owner,"91017");throw new AssertionError("cached actor bypassed failed current lookup");}catch(RepositoryException expected){}finally{queryFailure=false;}
                System.out.println("PASS current notification actor 3 current actor lookup failure propagates instead of falling back to stale identity");
            } finally {queryFailure=false;sql("DELETE FROM users WHERE oId='4'");sql("DELETE FROM comments WHERE oId='40'");cache.get("users").remove("4");}
            var profileViewer=new JSONObject().put("oId","2").put("userName","reader").put("userRole","defaultRole");
            var discovery=new ApiV3DiscoveryQueryService();inject(discovery,"userRepository",users);inject(discovery,"contentDtoService",dto);inject(discovery,"breezemoonRepository",new BreezemoonRepository());
            for(String field:List.of("userArticleStatus","userCommentStatus","userBreezemoonStatus","userPointStatus","userFollowerStatus","userFollowingUserStatus","userFollowingTagStatus","userFollowingArticleStatus","userWatchingArticleStatus")){
                sql("ALTER TABLE users ADD COLUMN "+field+" INTEGER DEFAULT 0");
                discovery.requireUserList(discovery.targetUser("author"),profileViewer,field);
                sql("UPDATE users SET "+field+"=1 WHERE oId='1'");
                try{discovery.requireUserList(discovery.targetUser("author"),profileViewer,field);throw new AssertionError("hidden profile list exposed");}catch(ApiV3Exception denied){check(denied.status==403);}
                discovery.requireUserList(discovery.targetUser("author"),owner,field);
                discovery.requireUserList(discovery.targetUser("author"),new JSONObject().put("oId","9").put("userRole","adminRole"),field);
                try{discovery.requireUserList(discovery.targetUser("author"),new JSONObject().put("oId","9").put("userRole","ADMINROLE"),field);throw new AssertionError();}catch(ApiV3Exception denied){check(denied.status==403);}
                sql("UPDATE users SET "+field+"=0 WHERE oId='1'");
            }
            sql("UPDATE users SET userNickname='Fresh public name',userPassword='PRIVATE_PASSWORD',userPhone='PRIVATE_PHONE' WHERE oId='1'");
            var publicProfile=discovery.profile(discovery.targetUser("author"));check(publicProfile.getString("userNickname").equals("Fresh public name")&&!publicProfile.toString().contains("PRIVATE_"));
            for(int status:new int[]{1,4}){sql("UPDATE users SET userStatus="+status+" WHERE oId='1'");try{discovery.targetUser("author");throw new AssertionError();}catch(ApiV3Exception denied){check(denied.status==404);}}
            sql("UPDATE users SET userStatus=0,userName='renamed_author' WHERE oId='1'");try{discovery.targetUser("author");throw new AssertionError();}catch(ApiV3Exception denied){check(denied.status==404);}check(discovery.targetUser("renamed_author").getString("oId").equals("1"));
            sql("UPDATE users SET userName='author' WHERE oId='1'");
            System.out.println("PASS current content 9 actual profile target/privacy methods use current status, rename and all nine list flags despite warm cached users; owner/exact-admin rules and public field whitelist remain");
            sql("CREATE TABLE breezemoon(oId VARCHAR(19) PRIMARY KEY,breezemoonAuthorId VARCHAR(19),breezemoonStatus INTEGER,breezemoonContent TEXT,breezemoonCreated BIGINT,breezemoonUpdated BIGINT)");
            sql("INSERT INTO breezemoon VALUES('50','1',0,'CURRENT_BREEZEMOON',1,2)");
            check(discovery.breezemoon("50",profileViewer).getString("content").equals("CURRENT_BREEZEMOON"));
            sql("UPDATE users SET userBreezemoonStatus=1 WHERE oId='1'");try{discovery.breezemoon("50",profileViewer);throw new AssertionError();}catch(ApiV3Exception denied){check(denied.status==404);}check(discovery.breezemoon("50",owner)!=null);
            sql("UPDATE users SET userStatus=4 WHERE oId='1'");try{discovery.breezemoon("50",owner);throw new AssertionError();}catch(ApiV3Exception denied){check(denied.status==404);}
            sql("UPDATE users SET userStatus=0,userBreezemoonStatus=0 WHERE oId='1'");queryFailure=true;try{discovery.targetUser("author");throw new AssertionError();}catch(RepositoryException expected){}finally{queryFailure=false;}
            check(cached("users","1").getInt("userStatus")==0);
            System.out.println("PASS current content 10 actual breezemoon details use current author/privacy, hide deactivated authors and propagate SQL failure without a cached fallback");
            final String signed="https://example.test/avatar.png?sig=a%2Bb+c&x=1&x=2#literal";
            sql("UPDATE users SET userNickname='V3_LATEST_AUTHOR',userAvatarURL='"+signed+"' WHERE oId='1'");
            var unjoined=select("SELECT * FROM articles WHERE oId='10'").getFirst();
            var latestAuthor=dto.summary(unjoined).getJSONObject("author");check(latestAuthor.getString("userNickname").equals("V3_LATEST_AUTHOR")&&latestAuthor.getString("userAvatarURL").equals(signed));
            check(dto.summary(content.viewableArticle("10",profileViewer)).getJSONObject("author").getString("userAvatarURL").equals(signed));
            check(discovery.profile(discovery.targetUser("author")).getString("userAvatarURL").equals(signed));
            check(!latestAuthor.toString().contains("PRIVATE_"));check(cached("users","1").getString("userNickname").equals("STALE_AUTHOR"));
            check(ApiV3AvatarUrl.stored("http://legacy.test/a.png?sig=x").equals("http://legacy.test/a.png?sig=x"));
            for(String bad:List.of("javascript:alert(1)","https://u:p@example.test/a.png","//example.test/a.png","https://example.test/a\\b.png","https://example.test/a\nb.png"))check(ApiV3AvatarUrl.stored(bad).isEmpty());
            check(dto.author(new JSONObject().put("oId","1").put("userAvatarURL","javascript:alert(1)"),false).getString("userAvatarURL").equals("https://example.test/default-avatar.png"));
            check(dto.author(content.currentAuthor("1"),true).getString("userAvatarURL").isEmpty());
            queryFailure=true;try{dto.summary(unjoined);throw new AssertionError();}catch(ApiV3Exception unavailable){check(unavailable.status==503);}finally{queryFailure=false;}
            System.out.println("PASS current content 11 unjoined article summaries read current public authors; joined/detail/profile preserve signed avatar query exactly, invalid URLs use safe default and SQL errors never fall back to cache");
            sql("UPDATE articles SET articleType=5,articleQnAOfferPoint=20 WHERE oId='10'");
            sql("INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentQnAOffered) VALUES('91','3','other',1)");
            var question = content.detail(content.viewableArticle("10", null), null).getJSONObject("qna");
            check(question.keySet().equals(Set.of("offerPoint", "accepted")) && question.getInt("offerPoint")==20 && !question.getBoolean("accepted"));
            sql("INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentQnAOffered,commentAnonymous,commentVisible,commentStatus) VALUES('92','3','10',1,1,1,1)");
            var acceptedSnapshot = content.viewableArticle("10", null);
            question = content.detail(acceptedSnapshot, null).getJSONObject("qna");
            check(question.getBoolean("accepted") && question.length()==2 && !question.toString().contains("CURRENT_COMMENT"));
            sql("UPDATE comments SET commentQnAOffered=0 WHERE oId='92'");
            check(content.detail(acceptedSnapshot, null).getJSONObject("qna").getBoolean("accepted"));
            check(!content.detail(content.viewableArticle("10", null), null).getJSONObject("qna").getBoolean("accepted"));
            sql("UPDATE articles SET articleQnAOfferPoint=37 WHERE oId='10'");
            check(content.detail(content.viewableArticle("10", null), null).getJSONObject("qna").getInt("offerPoint")==37);
            sql("UPDATE articles SET articleType=0 WHERE oId='10'");
            check(content.detail(content.viewableArticle("10", null), null).isNull("qna"));
            sql("UPDATE articles SET articleType=5,articleQnAOfferPoint=-1 WHERE oId='10'");
            try { content.detail(content.viewableArticle("10", null), null); throw new AssertionError(); }
            catch (ApiV3Exception expected) { check(expected.status==503); }
            System.out.println("PASS current content 12 question offer and accepted existence share the authorized article SQL snapshot, count hidden/anonymous answers without identity, remain current and expose null for non-questions");
            sql("UPDATE articles SET articleType=0,articleQnAOfferPoint=0 WHERE oId='10'");
            for (int type : List.of(0, 6)) {
                for (int perfect : List.of(0, 1, 2)) {
                    sql("UPDATE articles SET articleType="+type+",articlePerfect="+perfect+" WHERE oId='10'");
                    var target = content.viewableArticle("10", null);
                    check(dto.summary(target).getBoolean("perfect") == (perfect==1));
                    check(content.detail(target, null).getBoolean("perfect") == (perfect==1));
                }
            }
            sql("UPDATE articles SET articlePerfect=1 WHERE oId='10'");
            var perfectSnapshot = content.viewableArticle("10", null);
            sql("UPDATE articles SET articlePerfect=0 WHERE oId='10'");
            check(content.detail(perfectSnapshot, null).getBoolean("perfect"));
            check(!content.detail(content.viewableArticle("10", null), null).getBoolean("perfect"));
            sql("UPDATE articles SET articlePerfect=1,articleStatus=1 WHERE oId='10'");
            check(content.viewableArticle("10", null)==null);
            System.out.println("PASS current content 13 perfect flag is an exact public boolean for ordinary/long summaries and details, follows the current snapshot and never overrides target visibility");
            sql("UPDATE articles SET articleStatus=0,articleType=0 WHERE oId='10'");
            for (int flag : List.of(0,1)) for (int statement : List.of(0,1,2,3)) {
                sql("UPDATE articles SET articleCommentable="+flag+",articleStatement="+statement+" WHERE oId='10'");
                var policy = content.detail(content.viewableArticle("10", null), null);
                check(policy.getBoolean("commentable")== (flag==1));
                check(policy.getInt("statement")==statement);
            }
            sql("UPDATE articles SET articleCommentable=NULL,articleStatement=9 WHERE oId='10'");
            var unknownPolicy=content.detail(content.viewableArticle("10",null),null);
            check(unknownPolicy.isNull("commentable") && unknownPolicy.isNull("statement"));
            System.out.println("PASS current content 14 current SQL comment switch and all statement values project strictly; invalid legacy values remain unknown");
            ApiV3CommentLocationTest.run(db,content,comments);

        }
    }
}
