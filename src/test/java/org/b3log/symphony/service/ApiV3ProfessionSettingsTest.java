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
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real transport/auth/SQL/coordinator/strict JDBC, using the shared primary SQLite/fault fixture. */
public class ApiV3ProfessionSettingsTest extends ApiV3ProfessionPrimaryTest {
    static final String ROOT = "/api/v3/me/professions/";
    static class SettingsFixture extends Fixture {
        final ApiV3ProfessionSettingsService settings = new ApiV3ProfessionSettingsService();
        final ApiV3ProfessionSettingsProcessor endpoint = new ApiV3ProfessionSettingsProcessor();
        boolean deactivateOnBegin;
        SettingsFixture() throws Exception {
            super();
            var users = new UserRepository() {
                public String getName() { return "x_user"; }
                public List<JSONObject> select(String q,Object...a) throws RepositoryException { return selectSQL("user",q,a); }
            };
            var privacy = new UserProfessionPrivacyRepository() {
                public String getName() { return "x_user_profession_privacy"; }
                public boolean hasTransactionBegun() { return delegate != null && delegate.isActive(); }
                public List<JSONObject> select(String q,Object...a) throws RepositoryException { return selectSQL("privacy",q,a); }
            };
            var field = ApiV3ProfessionPrimaryService.class.getDeclaredField("idempotency");field.setAccessible(true);
            var coordinator = field.get(service);
            field = ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");field.setAccessible(true);
            inject(settings,"users",users);inject(settings,"privacy",privacy);inject(settings,"requests",field.get(coordinator));
            inject(endpoint,"service",settings);
        }
        @Override Transaction begin() {
            if (deactivateOnBegin) {
                deactivateOnBegin = false;
                try { sql(observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",actor); }
                catch(Exception e) { throw new IllegalStateException(e); }
            }
            return super.begin();
        }
        RequestContext call(String path,String token,String key,String body,boolean route) throws Exception {
            var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.PUT,
                    ROOT+path+"?apiKey=legacy&userId=2",Unpooled.copiedBuffer(body,StandardCharsets.UTF_8));
            try {
                raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","2001:db8::"+actor);
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request = new Request(null,raw);request.setBytes(body.getBytes(StandardCharsets.UTF_8));
                request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                var c = new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));
                c.attr("user",new JSONObject().put("oId","2"));
                if(route) {
                    c.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                    c.addHandler(ctx->{
                        var resolution=(org.b3log.latke.http.handler.RouteResolution)ctx.attr("MATCH_RESULT");
                        Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),path);
                        if(path.equals("privacy"))endpoint.privacy(ctx);else endpoint.onboarding(ctx);
                    });c.handle();
                } else {
                    c.addHandler(writes::handle);
                    c.addHandler(ctx->{if(path.equals("privacy"))endpoint.privacy(ctx);else endpoint.onboarding(ctx);});
                    auth.settingsWrite(c);
                }
                return c;
            } finally { raw.release(); }
        }
        RequestContext skip(String key) throws Exception { return call("onboarding","writer",key,"{\"onboardingState\":\"SKIPPED\"}",false); }
        RequestContext privacy(String key,JSONObject body) throws Exception { return call("privacy","writer",key,body.toString(),false); }
    }
    static JSONObject preset(String value) { return new JSONObject().put("preset",value); }
    static JSONObject custom() {
        var map=ApiV3ProfessionSettingsInputs.presetVisibility("ALL_PUBLIC");
        int i=0;for(String key:map.keySet())map.put(key,List.of("PUBLIC","LOGGED_IN","SELF","HIDDEN").get(i++%4));
        return preset("CUSTOM").put("moduleVisibility",map);
    }
    static JSONObject data(RequestContext c) { Assert.assertEquals(c.attr("test.status"),200);return (JSONObject)c.attr("test.data"); }
    static void equalFields(JSONObject before,JSONObject after,String...fields) { for(String field:fields)Assert.assertEquals(after.get(field),before.get(field),field); }
    public static void main(String[] args) throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");
        Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new SettingsFixture()) {
            ProfessionSettingsRouteBindings.register(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            var c=f.call("onboarding","writer","skip-route","{\"onboardingState\":\"SKIPPED\"}",true);
            Assert.assertEquals(data(c).keySet(),Set.of("onboardingState","primaryProfessionId"));
            var row=f.state();Assert.assertEquals(row.getString("onboardingState"),"SKIPPED");Assert.assertEquals(row.getString("primaryProfessionId"),"");
            Assert.assertEquals(row.getLong("userProgressVersion"),1L);Assert.assertEquals(row.getLong("userPrivacyVersion"),0L);Assert.assertEquals(row.getLong("publicStatsVersion"),0L);
            Assert.assertEquals(f.locks,List.of("user","user","privacy"));
            Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");
            Assert.assertEquals(f.scalar("SELECT SUM(userPoint) AS value FROM x_user"),1400L);
            Assert.assertEquals(f.scalar("SELECT SUM(totalExperience) AS value FROM x_user_profession"),55L);
            data(f.call("privacy","writer","privacy-route",preset("ALL_PUBLIC").toString(),true));
        }
        System.out.println("PASS 1 actual onboarding PUT/Bearer/scope/budget/controller/SQL, default skip state and versions, lock order, no points or fake profession progress");
        try(var f=new SettingsFixture()) {
            f.seedState();var before=f.state();
            data(f.skip("skip"));var skipped=f.state();Assert.assertEquals(skipped.getLong("userProgressVersion"),8L);
            Assert.assertEquals(skipped.getString("primaryProfessionId"),"");
            equalFields(before,skipped,"privacyPreset","moduleVisibilityJson","userPrivacyVersion","publicStatsVersion","oId","userId","createdAt");
            data(f.put("later-primary","11"));Assert.assertEquals(f.state().getString("primaryProfessionId"),"11");int writes=f.mutations;
            data(f.skip("skip"));Assert.assertEquals(f.mutations,writes);Assert.assertEquals(f.state().getString("primaryProfessionId"),"11");
            data(f.skip("new-skip"));Assert.assertEquals(f.state().getString("primaryProfessionId"),"");Assert.assertEquals(f.state().getLong("userProgressVersion"),10L);
        }
        System.out.println("PASS 2 skipping an already selected profession clears selection; field-only update preserves privacy and replay never clears a later primary selection");
        try(var f=new SettingsFixture()) {
            f.seedState();var before=f.state();
            for(String name:List.of("ALL_PUBLIC","LEVEL_ONLY","PRIMARY_ONLY","SELF_ONLY","FULLY_HIDDEN")) {
                var c=f.call("privacy","writer","preset-"+name,preset(name).toString(),false);
                var out=data(c);Assert.assertEquals(out.keySet(),Set.of("privacyPreset","moduleVisibility"));
                Assert.assertEquals(out.getString("privacyPreset"),name);
                Assert.assertEquals(ApiV3Json.canonical(out.getJSONObject("moduleVisibility")),ApiV3Json.canonical(ApiV3ProfessionSettingsInputs.presetVisibility(name)));
                equalFields(before,f.state(),"onboardingState","primaryProfessionId","userProgressVersion","createdAt","oId","userId");
            }
            Assert.assertEquals(f.state().getLong("userPrivacyVersion"),13L);Assert.assertEquals(f.state().getLong("publicStatsVersion"),14L);
            var custom=custom();data(f.privacy("custom",custom));Assert.assertEquals(ApiV3Json.canonical(new JSONObject(f.state().getString("moduleVisibilityJson"))),ApiV3Json.canonical(custom.getJSONObject("moduleVisibility")));
            Assert.assertEquals(f.state().getLong("userPrivacyVersion"),14L);Assert.assertEquals(f.state().getLong("publicStatsVersion"),15L);
            int writes=f.mutations;data(f.privacy("custom",new JSONObject(custom.toString())));Assert.assertEquals(f.mutations,writes);
            data(f.privacy("later-private",preset("SELF_ONLY")));data(f.privacy("custom",custom));Assert.assertEquals(f.state().getString("privacyPreset"),"SELF_ONLY");
            Assert.assertEquals(f.privacy("custom",preset("ALL_PUBLIC")).attr("test.status"),409);
            var supplied=preset("LEVEL_ONLY").put("moduleVisibility",new JSONObject().put("ranking","PUBLIC"));
            data(f.privacy("normalized",supplied));writes=f.mutations;data(f.privacy("normalized",preset("LEVEL_ONLY")));Assert.assertEquals(f.mutations,writes);
        }
        System.out.println("PASS 3 exact six preset/CUSTOM rules, required complete ten-module map, privacy-only versions, normalized ignored-map replay and old-key non-overwrite");
        try(var f=new SettingsFixture()) {
            data(f.privacy("first-private",preset("FULLY_HIDDEN")));var row=f.state();Assert.assertEquals(row.getString("onboardingState"),"UNDECIDED");Assert.assertEquals(row.getString("primaryProfessionId"),"");
            Assert.assertEquals(row.getLong("userProgressVersion"),0L);Assert.assertEquals(row.getLong("userPrivacyVersion"),1L);Assert.assertEquals(row.getLong("publicStatsVersion"),1L);
            Assert.assertEquals(f.call("privacy","other","other-key",preset("ALL_PUBLIC").toString(),false).attr("test.status"),200);
            Assert.assertEquals(f.state().getString("privacyPreset"),"FULLY_HIDDEN");Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM x_user_profession_privacy"),2L);
        }
        System.out.println("PASS 4 first privacy update creates only default selection state and isolated owner privacy, never writes another actor or progress");
        try(var f=new SettingsFixture()) {
            for(String path:List.of("onboarding","privacy"))for(String token:Arrays.asList(null,"invalid","reader")) {
                int roots=f.roots;Assert.assertEquals(f.call(path,token,"no-auth",path.equals("privacy")?preset("ALL_PUBLIC").toString():"{\"onboardingState\":\"SKIPPED\"}",false).attr("test.status"),token==null||token.equals("invalid")?401:403);Assert.assertEquals(f.roots,roots);
            }
            for(String body:List.of("{}","[]","{\"onboardingState\":null}","{\"onboardingState\":\"SELECTED\"}","{\"onboardingState\":\" skipped \"}","{\"onboardingState\":\"SKIPPED\",\"userId\":\"2\"}","{\"onboardingState\":\"SKIPPED\",\"onboardingState\":\"SKIPPED\"}"))Assert.assertNotEquals(f.call("onboarding","writer","bad",body,false).attr("test.status"),200);
            for(String body:List.of("{}","{\"preset\":null}","{\"preset\":\"all_public\"}","{\"preset\":\"CUSTOM\"}","{\"preset\":\"CUSTOM\",\"moduleVisibility\":{}}","{\"preset\":\"ALL_PUBLIC\",\"moduleVisibility\":null}","{\"preset\":\"ALL_PUBLIC\",\"moduleVisibility\":{\"unknown\":\"PUBLIC\"}}","{\"preset\":\"ALL_PUBLIC\",\"moduleVisibility\":{\"ranking\":true}}","{\"preset\":\"ALL_PUBLIC\",\"moduleVisibility\":{\"ranking\":\"BAD\"}}","{\"preset\":\"ALL_PUBLIC\",\"userId\":\"2\"}"))Assert.assertNotEquals(f.call("privacy","writer","bad",body,false).attr("test.status"),200);
            Assert.assertEquals(f.call("privacy","writer",null,preset("ALL_PUBLIC").toString(),false).attr("test.status"),422);
            Assert.assertEquals(f.call("onboarding","writer","bad key","{\"onboardingState\":\"SKIPPED\"}",false).attr("test.status"),422);
            Assert.assertEquals(f.call("privacy","writer","large"," ".repeat(4097),false).attr("test.status"),413);
            Assert.assertEquals(f.call("privacy","writer","large-utf8","\u4e2d".repeat(1366),false).attr("test.status"),413);
            Assert.assertNull(f.state());Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS 5 Cookie/apiKey/owner forgery, missing Bearer/scope/key, duplicate JSON/extra fields/enums/maps and UTF-8 body bounds cannot write state or receipts");
        try(var f=new SettingsFixture()) {
            data(f.skip("replay"));sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);
            Assert.assertEquals(f.skip("replay").attr("test.status"),404);
        }
        for(boolean replay:List.of(false,true))try(var f=new SettingsFixture()) {
            if(replay)data(f.privacy("race",preset("SELF_ONLY")));
            f.deactivateOnBegin=true;Assert.assertEquals(f.privacy("race",preset("SELF_ONLY")).attr("test.status"),404);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),replay?1L:0L);
            Assert.assertEquals(f.call("onboarding","disabled","disabled","{\"onboardingState\":\"SKIPPED\"}",false).attr("test.status"),404);
        }
        System.out.println("PASS 6 every attempt and replay currently locks active actor; deactivation between identity/preflight and root cannot replay or mutate");
        for(boolean skip:List.of(false,true))for(boolean existing:List.of(false,true))for(String fault:List.of("placeholder","user","privacy","business","receipt","commit","savepoint","release"))try(var f=new SettingsFixture()) {
            if(existing)f.seedState();String before=f.state()==null?null:f.state().toString();f.fault=fault;
            var c=skip?f.skip("fault"):f.privacy("fault",preset("SELF_ONLY"));Assert.assertEquals(c.attr("test.status"),503,fault);
            Assert.assertFalse(String.valueOf(c.attr("test.message")).contains("PRIVATE"));
            Assert.assertEquals(f.state()==null?null:f.state().toString(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);Assert.assertNull(JdbcRepository.TX.get());
            f.fault="";data(skip?f.skip("fault"):f.privacy("fault",preset("SELF_ONLY")));
        }
        System.out.println("PASS 7 new/existing skip/privacy and receipt fully roll back on actual JDBC placeholder/read/write/receipt/commit/savepoint/release failures, recover with same key");
        for(boolean skip:List.of(false,true))try(var f=new SettingsFixture()) {
            f.fault="lost-ack";var c=skip?f.skip("lost"):f.privacy("lost",preset("SELF_ONLY"));Assert.assertEquals(c.attr("test.status"),503);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),1L);f.fault="";
            if(skip)data(f.put("new-primary","11"));else data(f.privacy("new-privacy",preset("ALL_PUBLIC")));
            int writes=f.mutations;data(skip?f.skip("lost"):f.privacy("lost",preset("SELF_ONLY")));Assert.assertEquals(f.mutations,writes);
            Assert.assertEquals(f.state().getString(skip?"primaryProfessionId":"privacyPreset"),skip?"11":"ALL_PUBLIC");
        }
        System.out.println("PASS 8 actual committed-but-lost JDBC acknowledgement retains state and receipt; old-key recovery never increments again or overwrites later primary/privacy");
        for(boolean skip:List.of(false,true))try(var f=new SettingsFixture()) {
            try { if(skip)f.settings.skipInCurrentTransaction(f.actor);else f.settings.privacyInCurrentTransaction(f.actor,preset("SELF_ONLY"));Assert.fail(); }catch(IllegalStateException expected) { }
            var root=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="business";
            try { if(skip)f.settings.skipInCurrentTransaction(f.actor);else f.settings.privacyInCurrentTransaction(f.actor,preset("SELF_ONLY"));Assert.fail(); }catch(RepositoryException expected) { }
            f.fault="";root.commit();Assert.assertNull(f.state());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),2L);
            root=f.begin();if(skip)f.settings.skipInCurrentTransaction(f.actor);else f.settings.privacyInCurrentTransaction(f.actor,preset("SELF_ONLY"));Assert.assertNull(f.state());root.rollback();Assert.assertNull(f.state());
        }
        try(var f=new SettingsFixture()) {
            var root=f.begin();sql(f.writer,"UPDATE unrelated SET value=2");f.fault="rollback-point";
            try { f.settings.skipInCurrentTransaction(f.actor);Assert.fail(); }catch(RepositoryException expected) { }
            Assert.assertFalse(root.isActive());Assert.assertNull(f.state());Assert.assertEquals(f.scalar("SELECT value FROM unrelated"),1L);
        }
        System.out.println("PASS 9 primitives require caller transaction and never commit it, savepoint failure preserves unrelated work and failed savepoint rollback aborts entire root");
        for(String column:List.of("userProgressVersion","userPrivacyVersion","publicStatsVersion"))for(Object value:List.of(-1L,Long.MAX_VALUE,1.5,"broken"))try(var f=new SettingsFixture()) {
            f.seedState();sql(f.observer,"UPDATE x_user_profession_privacy SET "+column+"=? WHERE userId=?",value,f.actor);var before=f.state().toString();
            var c=column.equals("userProgressVersion")?f.skip("bad-version"):f.privacy("bad-version",preset("SELF_ONLY"));
            Assert.assertEquals(c.attr("test.status"),503);Assert.assertEquals(f.state().toString(),before);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        try(var f=new SettingsFixture()) {
            f.seedState();sql(f.observer,"ALTER TABLE x_user_profession_privacy RENAME TO duplicate_backup");
            sql(f.observer,"CREATE TABLE x_user_profession_privacy AS SELECT * FROM duplicate_backup");
            sql(f.observer,"INSERT INTO x_user_profession_privacy SELECT * FROM duplicate_backup");
            Assert.assertEquals(f.skip("duplicate").attr("test.status"),503);Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM receipts"),0L);
        }
        System.out.println("PASS 10 duplicate owner privacy and nonnumeric/fractional/negative/overflow versions fail closed without changes or receipts");
        try(var f=new SettingsFixture()) {
            data(f.put("primary-first","10"));data(f.privacy("privacy-first",preset("SELF_ONLY")));
            Assert.assertEquals(f.state().getString("primaryProfessionId"),"10");
            Assert.assertEquals(f.state().getLong("userProgressVersion"),1L);
            data(f.skip("skip-first"));Assert.assertEquals(f.state().getString("privacyPreset"),"SELF_ONLY");
            data(f.put("primary-later","11"));data(f.skip("skip-first"));
            Assert.assertEquals(f.state().getString("primaryProfessionId"),"11");
            data(f.privacy("privacy-later",preset("FULLY_HIDDEN")));data(f.privacy("privacy-first",preset("SELF_ONLY")));
            Assert.assertEquals(f.state().getString("privacyPreset"),"FULLY_HIDDEN");
            Assert.assertEquals(f.state().getLong("userProgressVersion"),3L);
            Assert.assertEquals(f.state().getLong("userPrivacyVersion"),2L);
            Assert.assertEquals(f.state().getLong("publicStatsVersion"),2L);
        }
        System.out.println("PASS 11 actual PrimaryService and Settings alternate selection/privacy/skip/reselection with isolated versions; both old receipts preserve later committed values");
        System.out.println("Profession settings isolated tests passed: 11 groups");
    }
}
