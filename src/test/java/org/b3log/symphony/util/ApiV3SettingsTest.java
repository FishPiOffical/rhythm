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
package org.b3log.symphony.util;

import org.b3log.latke.repository.RepositoryException;
import org.json.JSONObject;
import org.jsoup.nodes.Entities;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;

/** 验证实际设置入参、DTO 和状态转换，仓库缓存与站点服务另行验证。 */
public class ApiV3SettingsTest {
    public static void main(final String[] args) throws Exception {
        Assert.assertEquals(ApiV3Settings.normalize("privacy", new JSONObject().put("articles", false)).getInt("userArticleStatus"), 1);
        Assert.assertEquals(ApiV3Settings.normalize("profile", new JSONObject().put("userNickname", " name ")).getString("userNickname"), "name");
        Assert.assertEquals(ApiV3Settings.normalize("profile", new JSONObject().put("userIntro", "line\nnext")).getString("userIntro"), "line\nnext");
        for (final JSONObject bad : List.of(new JSONObject(), new JSONObject().put("userNickname", "x".repeat(21)), new JSONObject().put("userIntro", "x".repeat(256)),
                new JSONObject().put("userURL", "https://example.test/" + "x".repeat(100)), new JSONObject().put("userURL", "javascript:alert(1)"),
                new JSONObject().put("userURL", "https://user:pass@example.test/"), new JSONObject().put("userNickname", "x\ny"), new JSONObject().put("userNickname", 1),
                new JSONObject().put("userId", "other"), new JSONObject().put("userEmail", "private@example.test"))) {
            try {
                ApiV3Settings.normalize("profile", bad);
                Assert.fail();
            } catch (final ApiV3Exception expected) {
            }
        }
        for (final JSONObject bad : List.of(new JSONObject().put("articles", 0), new JSONObject().put("articles", "false"), new JSONObject().put("userArticleStatus", true))) {
            try {
                ApiV3Settings.normalize("privacy", bad);
                Assert.fail();
            } catch (final ApiV3Exception expected) {
            }
        }
        for (final JSONObject bad : List.of(new JSONObject().put("commentViewMode", 2), new JSONObject().put("avatarViewMode", 0.5), new JSONObject().put("skin", "x".repeat(33)), new JSONObject().put("skin", "../pc"), new JSONObject().put("skin", "/classic/pc"), new JSONObject().put("mobileSkin", "classic\\mobile"))) {
            try {
                ApiV3Settings.normalize("display", bad);
                Assert.fail();
            } catch (final ApiV3Exception expected) {
            }
        }
        System.out.println("PASS typed partial settings, limits, privacy booleans, modes, URLs and skin path boundary");
        for (final JSONObject bad : List.of(new JSONObject().put("listPageSize",9),new JSONObject().put("listPageSize",97),new JSONObject().put("listViewMode",2),new JSONObject().put("notifications",1))) {
            try {
                ApiV3Settings.normalize("functions", bad);
                Assert.fail();
            } catch (final ApiV3Exception expected) {
            }
        }
        Assert.assertEquals(ApiV3Settings.normalize("functions",new JSONObject().put("listPageSize",96)).getInt("userListPageSize"),96);
        Assert.assertEquals(ApiV3Settings.normalize("location",new JSONObject().put("publicLocation",false)).getInt("userGeoStatus"),1);
        try {
            ApiV3Settings.normalize("location", new JSONObject().put("city", "Beijing"));
            Assert.fail();
        } catch (final ApiV3Exception expected) {
        }
        for(final JSONObject bad:List.of(new JSONObject().put("language","ja_JP"),new JSONObject().put("timezone","Not/AZone"),new JSONObject().put("language",1))) {
            try {
                ApiV3Settings.normalize("locale", bad);
                Assert.fail();
            } catch (final ApiV3Exception expected) {
            }
        }
        Assert.assertEquals(ApiV3Settings.normalize("locale",new JSONObject().put("timezone","Asia/Shanghai")).getString("userTimezone"),"Asia/Shanghai");
        final String site="https://fishpi.cn/rhythm";
        Assert.assertEquals(SettingsRedirects.normalize("https://fishpi.cn:443/rhythm/recent?p=2",site),"https://fishpi.cn:443/rhythm/recent?p=2");
        Assert.assertEquals(SettingsRedirects.normalize("https://fishpi.cn/rhythm/?p=2",site),"");
        Assert.assertEquals(SettingsRedirects.normalize("https://fishpi.cn/rhythm/recent#list",site),"https://fishpi.cn/rhythm/recent#list");
        Assert.assertEquals(SettingsRedirects.normalize("https://fishpi.cn/rhythm/a%2Etxt",site),"https://fishpi.cn/rhythm/a%2Etxt");
        for(final String good:List.of("https://fishpi.cn/rhythm/a%2Fb","https://fishpi.cn/rhythm/100%25done","https://fishpi.cn/rhythm/a//b","https://fishpi.cn/rhythm/中文?q=a%20b#段落"))Assert.assertEquals(SettingsRedirects.normalize(good,site),good);
        Assert.assertEquals(SettingsRedirects.normalize("https://fishpi.cn/rhythm/a/../recent",site),"https://fishpi.cn/rhythm/a/../recent");
        Assert.assertEquals(SettingsRedirects.normalize("https://fishpi.cn/recent","https://fishpi.cn"),"https://fishpi.cn/recent");
        Assert.assertEquals(SettingsRedirects.normalize("https://fishpi.cn/","https://fishpi.cn"),"");
        Assert.assertEquals(SettingsRedirects.normalize("http://localhost:8080/recent","http://localhost:8080"),"http://localhost:8080/recent");
        Assert.assertEquals(SettingsRedirects.normalize("http://localhost:80/recent","http://localhost"),"http://localhost:80/recent");
        for(final String bad:List.of("https://fishpi.cn.evil/rhythm/recent","http://fishpi.cn/rhythm/recent","https://fishpi.cn:444/rhythm/recent",
                "https://user@fishpi.cn/rhythm/recent","https://fishpi.cn/other","https://fishpi.cn/rhythm/../other","https://fishpi.cn/rhythm/%2e%2e/other",
                "https://fishpi.cn/rhythm/%252e%252e/other","https://fishpi.cn/rhythm/%0d%0ax",
                "https://fishpi.cn/rhythm-other/recent","https://fishpi.cn/rhythm/%5cother","https://fishpi.cn/rhythm/%252e%252e%252fother")) {
            try{SettingsRedirects.normalize(bad,site);Assert.fail(bad);}catch(final ApiV3Exception expected){}
        }
        final JSONObject options=SettingsLocaleOptions.dto();
        Assert.assertTrue(options.getJSONArray("languages").toList().contains("zh_CN"));
        Assert.assertFalse(options.getJSONArray("timezones").isEmpty());
        for(final Object value:options.getJSONArray("timezones")) {
            final JSONObject zone = (JSONObject) value;
            Assert.assertEquals(zone.keySet(), Set.of("id", "name"));
            ApiV3Settings.normalize("locale",new JSONObject().put("timezone",zone.getString("id")));
        }
        Assert.assertTrue(org.b3log.latke.util.Strings.isURL("https://fishpi.cn.evil/recent"));
        System.out.println("PASS function/location/locale inputs, actual locale catalog and exact-origin/context redirect boundary");

        try (final Fixture f = new Fixture()) {
            try { UserSettingsMutation.apply(f, "owner", "privacy", new JSONObject().put("userArticleStatus", 1), true, 1); Assert.fail(); } catch (final IllegalStateException expected) { }
            f.begin(); final var privacy = UserSettingsMutation.apply(f, "owner", "privacy", new JSONObject().put("userArticleStatus", 1), true, 1); f.commit();
            Assert.assertFalse(privacy.getBoolean("articles")); Assert.assertTrue(privacy.getBoolean("comments")); Assert.assertEquals(privacy.length(), 13);
            Assert.assertEquals(f.row("owner").getInt("userPoint"), 100); Assert.assertEquals(f.row("other").getInt("userArticleStatus"), 0);
            f.begin(); final var display = UserSettingsMutation.apply(f, "owner", "display", new JSONObject().put("userCommentViewMode", 1).put("userSkin", "classic"), true, 1); f.commit();
            Assert.assertEquals(display.getInt("commentViewMode"), 1); Assert.assertEquals(display.getString("skin"), "classic/pc"); Assert.assertEquals(display.getString("mobileSkin"), "classic/mobile");
            Assert.assertEquals(f.row("owner").getInt("userArticleStatus"), 1);
            f.begin();
            try { UserSettingsMutation.apply(f, "owner", "display", new JSONObject().put("userSkin", "classic/mobile"), true, 1); Assert.fail(); } catch (final ApiV3Exception expected) { f.rollback(); }
            Assert.assertEquals(f.row("owner").getString("userSkin"), "classic/pc");
            System.out.println("PASS actual mutation keeps omitted/other-user/point fields, checks device skin and returns group whitelist");
            f.begin(); final JSONObject profile = UserSettingsMutation.apply(f, "owner", "profile", new JSONObject().put("userNickname", "<x>"), true, 123); f.commit();
            Assert.assertEquals(profile.getString("userNickname"), "<x>"); Assert.assertFalse(f.row("owner").getString("userNickname").contains("<x>"));
            Assert.assertEquals(profile.keySet(), Set.of("userNickname", "userIntro", "userURL")); Assert.assertEquals(f.row("owner").getLong("userUpdateTime"), 123L); Assert.assertTrue(f.validated);
            f.begin();
            try { UserSettingsMutation.apply(f, "owner", "profile", new JSONObject().put("userNickname", "<".repeat(20)), true, 123); Assert.fail(); }
            catch (final ApiV3Exception expected) { f.rollback(); }
            f.block = true; f.begin();
            try { UserSettingsMutation.apply(f, "owner", "profile", new JSONObject().put("userIntro", "blocked"), true, 124); Assert.fail(); } catch (final ApiV3Exception expected) { f.rollback(); }
            Assert.assertEquals(f.row("owner").getString("userIntro"), "intro"); f.block = false;
            f.failUpdate = true; f.begin();
            try { UserSettingsMutation.apply(f, "owner", "privacy", new JSONObject().put("userArticleStatus", 0), true, 1); Assert.fail(); } catch (final RepositoryException expected) { f.rollback(); }
            Assert.assertEquals(f.row("owner").getInt("userArticleStatus"), 1); f.failUpdate = false;
            f.begin();
            try { UserSettingsMutation.apply(f, "missing", "privacy", new JSONObject().put("userArticleStatus", 1), true, 1); Assert.fail(); } catch (final ApiV3Exception expected) { Assert.assertEquals(expected.status,404); f.rollback(); }
            System.out.println("PASS stored-text escaping/DTO decoding, profile validation, failed update rollback and missing owner");
            f.begin(); final var functions=UserSettingsMutation.apply(f,"owner","functions",ApiV3Settings.normalize("functions",new JSONObject().put("notifications",false).put("listPageSize",96)),true,1);f.commit();
            Assert.assertFalse(functions.getBoolean("notifications"));Assert.assertEquals(functions.getInt("listPageSize"),96);Assert.assertEquals(f.row("owner").getInt("userPoint"),100);
            f.begin();final var location=UserSettingsMutation.apply(f,"owner","location",ApiV3Settings.normalize("location",new JSONObject().put("publicLocation",false)),true,1);f.commit();Assert.assertEquals(location.keySet(),Set.of("publicLocation"));Assert.assertFalse(location.getBoolean("publicLocation"));
            f.begin();final var locale=UserSettingsMutation.apply(f,"owner","locale",ApiV3Settings.normalize("locale",new JSONObject().put("language","en_US")),true,1);f.commit();Assert.assertEquals(locale.getString("language"),"en_US");Assert.assertEquals(locale.getString("timezone"),"Asia/Shanghai");
            f.begin();
            try {
                UserSettingsMutation.apply(f,"owner","functions",new JSONObject().put("userNotifyStatus",0),false,1);
                UserSettingsMutation.apply(f,"owner","display",new JSONObject().put("userSkin","classic/mobile"),false,1);Assert.fail();
            }catch(final ApiV3Exception expected){f.rollback();}
            Assert.assertEquals(f.row("owner").getInt("userNotifyStatus"),1);
            System.out.println("PASS new group partial mutations, location-only DTO, locale retention and multi-group rollback");

        }
        for (final String good : List.of("https://external.example/avatar", "https://example.test/image?signature=a%2Fb&size=1", "HTTPS://example.test/x", "https://[::1]:8443/p", "https://example.test/" + "x".repeat(234))) {
            Assert.assertEquals(ApiV3Settings.normalize("avatar",new JSONObject().put("avatarURL"," " + good + " ")).getString("userAvatarURL"),good);
        }
        for (final Object bad : List.of("", " ", "http://example.test/a.png", "/avatar.png", "//example.test/a", "javascript:alert(1)", "data:image/png,xx", "https://user:pass@example.test/a", "https://example.test:65536/a", "https://example.test:/a", "https://example.test/a\\b", "https://example.test/a b", "https://example.test/a\npath", "https:///a", "https://example.test/" + "x".repeat(235), 1, JSONObject.NULL)) {
            try {
                ApiV3Settings.normalize("avatar", new JSONObject().put("avatarURL", bad));
                Assert.fail(String.valueOf(bad));
            } catch (final ApiV3Exception expected) {
                Assert.assertEquals(expected.status, 422);
            }
        }
        for (final JSONObject bad : List.of(new JSONObject(),new JSONObject().put("avatarURL","https://example.test/x").put("userAvatarType",2),new JSONObject().put("userId","other"))) {
            try {
                ApiV3Settings.normalize("avatar", bad);
                Assert.fail();
            } catch (final ApiV3Exception expected) {
            }
        }
        System.out.println("PASS external HTTPS avatars, signed URLs, exact 255 limit and invalid protocols/credentials/ports/fields");
        try (final Fixture f = new Fixture()) {
            final JSONObject requested=ApiV3Settings.normalize("avatar",new JSONObject().put("avatarURL","https://external.example/image?sig=a%2Fb"));
            f.begin();final JSONObject result=UserSettingsMutation.apply(f,"owner","avatar",requested,true,456);f.commit();
            Assert.assertEquals(result.keySet(),Set.of("avatarURL"));Assert.assertEquals(result.getString("avatarURL"),requested.getString("userAvatarURL"));
            Assert.assertEquals(f.row("owner").getInt("userAvatarType"),2);Assert.assertEquals(f.row("owner").getLong("userUpdateTime"),456L);
            Assert.assertEquals(f.row("owner").getInt("userPoint"),100);Assert.assertEquals(f.row("owner").getString("userPassword"),"secret");
            Assert.assertEquals(f.row("other").getString("userAvatarURL"),"https://old.example/image");Assert.assertFalse(f.validated);
            f.failUpdate=true;f.begin();try{UserSettingsMutation.apply(f,"owner","avatar",new JSONObject().put("userAvatarURL","https://new.example/x"),true,789);Assert.fail();}catch(final RepositoryException expected){f.rollback();}f.failUpdate=false;
            Assert.assertEquals(f.row("owner").getLong("userUpdateTime"),456L);
            f.exec("UPDATE users SET userStatus=1 WHERE oId=?","owner");f.begin();try{UserSettingsMutation.apply(f,"owner","avatar",requested,true,789);Assert.fail();}catch(final ApiV3Exception expected){Assert.assertEquals(expected.status,404);f.rollback();}
            Assert.assertEquals(f.row("owner").getLong("userUpdateTime"),456L);
            f.begin();try{UserSettingsMutation.apply(f,"missing","avatar",requested,true,789);Assert.fail();}catch(final ApiV3Exception expected){Assert.assertEquals(expected.status,404);f.rollback();}
            System.out.println("PASS avatar transaction field whitelist, current owner status, other-user isolation and failure rollback");
        }
        for (final String type : ApiV3Personality.TYPES) for (final String suffix : List.of("", "-A", "-T")) {
            final var changes=ApiV3Settings.normalize("personality",new JSONObject().put("mbti"," " + (type+suffix).toLowerCase(Locale.ROOT) + " "));
            Assert.assertEquals(changes.getString("mbti"),type+suffix);
        }
        Assert.assertEquals(ApiV3Settings.normalize("personality",new JSONObject().put("mbti"," ")).getString("mbti"),"");
        final Locale oldLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Assert.assertEquals(ApiV3Personality.normalize("intj-a"), "INTJ-A");
        } finally {
            Locale.setDefault(oldLocale);
        }
        for (final Object bad : List.of("INTJ-A-extra", "INTJ-", "INTJ-X", "INTJ A", "INTJ\nA", "<html>",
                "ＩＮＴＪ", "ınfj", "İNFJ", 1, JSONObject.NULL)) try {
            ApiV3Settings.normalize("personality", new JSONObject().put("mbti", bad));
            Assert.fail();
        } catch (final ApiV3Exception expected) {
            Assert.assertEquals(expected.status, 422);
        }
        try(var f=new Fixture()){
            f.begin();var dto=UserSettingsMutation.apply(f,"owner","personality",new JSONObject().put("mbti","enfp-t"),true,999);f.commit();
            Assert.assertEquals(dto.keySet(),Set.of("mbti"));Assert.assertEquals(dto.getString("mbti"),"ENFP-T");Assert.assertEquals(f.row("owner").getLong("userUpdateTime"),999L);Assert.assertEquals(f.row("other").optString("mbti"),"");Assert.assertEquals(f.row("owner").getInt("userPoint"),100);
            f.begin();try{UserSettingsMutation.apply(f,"owner","personality",new JSONObject().put("mbti","INTJ-A-extra"),true,1000);Assert.fail();}catch(final ApiV3Exception expected){f.rollback();}
            Assert.assertEquals(f.row("owner").getString("mbti"),"ENFP-T");
            f.begin();UserSettingsMutation.apply(f,"owner","personality",new JSONObject().put("mbti",""),true,1001);f.commit();Assert.assertEquals(f.row("owner").getString("mbti"),"");
        }
        System.out.println("PASS MBTI 48 choices, clearing, locale-independent normalization, strict invalid-value rejection and current-transaction field isolation");
        // 模型初始化依赖运行中的站点，此处直接验证接口与存储的字段映射对应实际模型键。
        final String model = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/b3log/symphony/model/UserExt.java"));
        for (final String group : List.of("profile", "privacy", "display", "functions", "location", "locale", "avatar")) for (final String field : ApiV3Settings.fields(group).values()) {
            if (!field.equals("userURL")) Assert.assertTrue(model.contains("= \"" + field + "\";"), field);
        }
        System.out.println("PASS storage whitelist values match the actual UserExt model");
    }
    private static class Fixture implements UserSettingsMutation.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        boolean block, failUpdate, validated;
        Fixture() throws Exception {
            final var fields = new TreeSet<String>(); for (final String g : List.of("profile","privacy","display","functions","location","locale","avatar","personality")) fields.addAll(ApiV3Settings.fields(g).values());
            fields.addAll(Set.of("oId", "userName", "userPassword", "userPoint", "userUpdateTime", "userAvatarType", "userStatus"));
            exec("CREATE TABLE users (" + fields.stream().map(f -> f + " TEXT").collect(Collectors.joining(",")) + ")");
            for (final String actor : List.of("owner", "other")) {
                final JSONObject row = new JSONObject().put("oId", actor).put("userName", actor).put("userPassword", "secret").put("userPoint", 100).put("userUpdateTime", 0)
                        .put("userAvatarURL","https://old.example/image").put("userAvatarType",0).put("userStatus",0)
                        .put("userNickname", "name").put("userIntro", "intro").put("userURL", "").put("userCommentViewMode", 0).put("userAvatarViewMode", 0).put("userSkin", "classic/pc").put("userMobileSkin", "classic/mobile");
                for (final String field : ApiV3Settings.PRIVACY.values()) row.put(field, 0);
                for(final String key:ApiV3Settings.FUNCTION_SWITCHES)row.put(ApiV3Settings.FUNCTIONS.get(key),0);
                row.put("userListPageSize",20).put("userListViewMode",0).put("userIndexRedirectURL","").put("userGeoStatus",0).put("userLanguage","zh_CN").put("userTimezone","Asia/Shanghai");
                final var keys = new ArrayList<>(row.keySet()); exec("INSERT INTO users (" + String.join(",",keys) + ") VALUES (" + keys.stream().map(k->"?").collect(Collectors.joining(",")) + ")", keys.stream().map(row::get).toArray());
            }
        }
        void begin() throws Exception { db.setAutoCommit(false); } void commit() throws Exception { db.commit(); db.setAutoCommit(true); } void rollback() throws Exception { db.rollback(); db.setAutoCommit(true); }
        public boolean hasTransaction() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new IllegalStateException(e); } }
        public JSONObject lockOwner(final String actor) throws RepositoryException { Assert.assertTrue(hasTransaction()); return row(actor); }
        JSONObject row(final String actor) throws RepositoryException {
            try (final var s=db.prepareStatement("SELECT * FROM users WHERE oId=?")) { s.setString(1,actor); try (final var rs=s.executeQuery()) { if (!rs.next()) return null; final JSONObject row=new JSONObject(); for(int i=1;i<=rs.getMetaData().getColumnCount();i++) row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i)); return row; } } catch(final Exception e){ throw new RepositoryException(e); }
        }
        public String canonicalSkin(final String skin, final String device) {
            if ("classic".equals(skin) && "pc".equals(device)) return "classic/pc";
            if (!skin.equals("classic/" + device)) throw new ApiV3Exception(422,"invalid_skin","invalid"); return skin;
        }
        public String escapeText(final String text) { return Entities.escape(text); }
        public void validatePersonality(final String actor, final boolean quota) { }
        public void validateProfile(final String actor, final JSONObject merged, final boolean quota) { validated=true; if(block) throw new ApiV3Exception(422,"content_rejected","blocked"); }
        public void update(final String actor, final JSONObject changes) throws RepositoryException {
            final var keys=new ArrayList<>(changes.keySet()); final List<Object> args=new ArrayList<>(); for(final String key:keys) args.add(changes.get(key)); args.add(actor);
            exec("UPDATE users SET " + keys.stream().map(k->k+"=?").collect(Collectors.joining(",")) + " WHERE oId=?", args.toArray()); if(failUpdate)throw new RepositoryException("simulated");
        }
        void exec(final String sql,final Object... args)throws RepositoryException { try(final var s=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(final Exception e){throw new RepositoryException(e);} }
        public void close() throws Exception { db.close(); }
    }
}
