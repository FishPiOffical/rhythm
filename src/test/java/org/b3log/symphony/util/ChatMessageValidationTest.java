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

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.model.User;
import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.*;
import org.b3log.symphony.service.ChatMessageValidationService;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.middleware.validate.ChatMsgAddValidationMidware;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual core and legacy adapter, including ordering and unchanged error envelope. */
public class ChatMessageValidationTest {
    static final long NOW=10000;
    static JSONObject actor(String id,long time,String role){return new JSONObject().put("oId",id).put(User.USER_ROLE,role).put(UserExt.USER_LATEST_CMT_TIME,time);}
    interface Work {void run();}
    static void rejected(int status,String code,Work work){try{work.run();Assert.fail();}catch(ChatMessageValidation.Rejected e){Assert.assertEquals(e.status,status);Assert.assertEquals(e.code,code);}}
    static class Fixed extends ChatMessageValidationService {@Override protected long now(){return NOW;}}
    static void inject(Object bean,String name,Object value)throws Exception{var f=bean.getClass().getDeclaredField(name);f.setAccessible(true);f.set(bean,value);}
    static class Fixture implements AutoCloseable {
        final DefaultFullHttpRequest raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.POST,"/chat-room/send");final RequestContext context;
        Fixture(JSONObject body)throws Exception{raw.headers().set("Content-Type","application/json");var request=new Request(null,raw);request.setBytes(body.toString().getBytes(StandardCharsets.UTF_8));request.setJSON(body);context=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));context.addHandler(c->c.attr("accepted",true));}
        public void close(){raw.release();}
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        final var user=actor("123",9000,"member");final int[] reserved={0};
        Assert.assertEquals(ChatMessageValidation.prepare(user," \nhello\t ",NOW,1000,text->{reserved[0]++;return text;}),"hello");
        rejected(429,"too_frequent",()->ChatMessageValidation.prepare(actor("123",9001,"member"),"x",NOW,1000,text->{reserved[0]++;return text;}));Assert.assertEquals(reserved[0],1);
        Assert.assertEquals(ChatMessageValidation.prepare(actor("123",NOW+1000,Role.ROLE_ID_C_ADMIN),"admin",NOW,1000,text->text),"admin");
        rejected(429,"too_frequent",()->ChatMessageValidation.prepare(actor("123",NOW+1000,"other-role"),"x",NOW,0,text->text));
        System.out.println("PASS existing interval boundary, future timestamps and admin-only exception; rejected frequency runs before reserved-word processing");
        for(String content:List.of(""," ","\n\t","x".repeat(4097)))rejected(422,"invalid_content",()->ChatMessageValidation.prepare(user,content,NOW,0,text->text));
        Assert.assertEquals(ChatMessageValidation.prepare(user,"x".repeat(4096),NOW,0,text->text).length(),4096);
        Assert.assertEquals(ChatMessageValidation.prepare(user,"😀".repeat(2048),NOW,0,text->text).length(),4096);
        rejected(422,"invalid_content",()->ChatMessageValidation.prepare(user,"😀".repeat(2049),NOW,0,text->text));
        rejected(422,"invalid_content",()->ChatMessageValidation.prepare(user,"short",NOW,0,text->"x".repeat(4097)));
        Assert.assertEquals(ChatMessageValidation.prepare(user,"x".repeat(4097),NOW,0,text->"replaced"),"replaced");
        System.out.println("PASS old UTF-16 length, trim and blank rules remain; final reserved-word output controls the length decision");
        for(String content:List.of("<b>正文</b>","[music]payload[/music]","执法 查询","zf 查询","{\"msgType\":\"redPacket\"}"))Assert.assertEquals(ChatMessageValidation.prepare(user,content,NOW,0,text->text),content);
        System.out.println("PASS shared base does not invent special-command filters, HTML stripping or business side effects while the v3 command policy is pending");
        Sessions.current=actor("shadow",NOW+1000,"member");Sessions.reads=0;ReservedWords.calls=0;
        Assert.assertEquals(new Fixed().prepare(actor("123",0,"member")," RESERVED "),"❤️");Assert.assertEquals(Sessions.reads,0);Assert.assertEquals(ReservedWords.calls,1);
        System.out.println("PASS service adapter uses the supplied actor and existing reserved-word call without session identity resolution");
        for(String client:List.of("Web","PC","Mobile","CLI","uTools","Windows","macOS","Linux","IceNet","ElvesOnline","iOS","Android","Extension","IDEA","Chrome","Edge","VSCode","Python","Golang","Bird","Dart","Harmony","Rust","Other"))Assert.assertEquals(ChatMessageClients.normalize(client+"/1.0"),client+"/1.0");
        Assert.assertEquals(ChatMessageClients.normalize("Web/版本 1.0<script>"),"Web/版本 1.0script");
        Assert.assertEquals(ChatMessageClients.normalize("Web/"+"x".repeat(32)).length(),36);Assert.assertEquals(ChatMessageClients.normalize("Web/"+"x".repeat(33)),"Web/"+"x".repeat(31));
        Assert.assertEquals(ChatMessageClients.normalize("Web/1.0/ignored"),"Web/1.0");Assert.assertEquals(ChatMessageClients.normalize("Web//ignored"),"Web/");
        for(String invalid:Arrays.asList(null,"","Web","Web/","web/1.0","Unknown/1.0"))Assert.assertEquals(ChatMessageClients.normalize(invalid),"");
        System.out.println("PASS shared client-label parsing preserves all old brands, character filtering, slash handling and the historical 32/33-character boundary");
        final var middleware=new ChatMsgAddValidationMidware();inject(middleware,"validation",new Fixed());
        inject(middleware,"langPropsService",new LangPropsService(){@Override public String get(String key){Assert.assertEquals(key,"tooFrequentCmtLabel");return "原频控提示";}});
        Sessions.current=actor("123",0,"member");
        try(var f=new Fixture(new JSONObject().put("content"," RESERVED ").put("client","Web/Test"))){middleware.handle(f.context);Assert.assertEquals(f.context.attr("accepted"),true);var request=(JSONObject)f.context.attr(Keys.REQUEST);Assert.assertEquals(request.getString("content"),"❤️");Assert.assertEquals(request.getString("client"),"Web/Test");}
        for(String content:List.of(" ","x".repeat(4097)))try(var f=new Fixture(new JSONObject().put("content",content))){middleware.handle(f.context);Assert.assertNull(f.context.attr("accepted"));var json=((JsonRenderer)f.context.getRenderer()).getJSONObject();Assert.assertEquals(json.keySet(),Set.of("msg"));Assert.assertEquals(json.getString("msg"),"聊天消息内容为空或过多（大于4096字符）");}
        Sessions.current=actor("123",NOW+1,"member");ReservedWords.calls=0;
        try(var f=new Fixture(new JSONObject().put("content","RESERVED"))){middleware.handle(f.context);Assert.assertNull(f.context.attr("accepted"));var json=((JsonRenderer)f.context.getRenderer()).getJSONObject();Assert.assertEquals(json.keySet(),Set.of("msg"));Assert.assertEquals(json.getString("msg"),"原频控提示");Assert.assertEquals(ReservedWords.calls,0);}
        ApiProcessor.users.put("ADMIN",actor("321",NOW+1000,Role.ROLE_ID_C_ADMIN));try(var f=new Fixture(new JSONObject().put("content"," api ").put("apiKey","ADMIN"))){middleware.handle(f.context);Assert.assertEquals(f.context.attr("accepted"),true);Assert.assertEquals(((JSONObject)f.context.attr(Keys.REQUEST)).getString("content"),"api");}
        try(var f=new Fixture(new JSONObject().put("content","[setdiscuss]话题[/setdiscuss]").put("apiKey","ADMIN"))){
            f.context.attr(User.USER,actor("444",0,"member"));middleware.handle(f.context);Assert.assertEquals(f.context.attr("accepted"),true);
        }
        try(var f=new Fixture(new JSONObject().put("content","[setdiscuss]话题[/setdiscuss]").put("apiKey","ADMIN"))){
            f.context.attr(User.USER,actor("444",NOW+1,"member"));middleware.handle(f.context);Assert.assertNull(f.context.attr("accepted"));
        }
        System.out.println("PASS real legacy middleware normalizes accepted requests, preserves msg-only rejection/localization and existing session/body-apiKey identity order");
        for(String tag:List.of("redpacket","weather","music","setdiscuss","barrager")){
            special("["+tag+"]body[/"+tag+"]");special(" \n["+tag+"]body[/"+tag+"] \t");
            ChatMessageText.requireOrdinary("引用 ["+tag+"]body[/"+tag+"]");
            ChatMessageText.requireOrdinary("["+tag+"]缺少结束标签");
        }
        for(String command:List.of("执法","执法 禁言 User 1","zf","zf jy User 1","　zf jy User 1　"))special(command);
        for(String card:List.of("redPacket","weather","music"))special(new JSONObject().put("msgType",card).put("content","body").toString());
        for(String ordinary:List.of("普通消息😀","**Markdown**","<script>raw text</script>","{broken", "{\"msgType\":\"other\"}","{\"msgType\":\"redPacket\",\"msgType\":\"music\"}","说明：执法命令", "[music]在代码块中讲解"))ChatMessageText.requireOrdinary(ordinary);
        ChatMessageText.requireOrdinary("{\"nested\":".repeat(40)+"0"+"}".repeat(40));
        for(String invalid:Arrays.asList(null,""," ","x".repeat(4097))){try{ChatMessageText.requireOrdinary(invalid);Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,422);Assert.assertEquals(e.code,"invalid_content");}}
        System.out.println("PASS v3 ordinary text rejects complete legacy business wrappers, operator prefixes and typed card JSON without interpreting Markdown/HTML, malformed or deeply nested JSON");
    }
    static void special(String text){try{ChatMessageText.requireOrdinary(text);Assert.fail(text);}catch(ApiV3Exception e){Assert.assertEquals(e.status,422);Assert.assertEquals(e.code,"special_message_required");}}
}
