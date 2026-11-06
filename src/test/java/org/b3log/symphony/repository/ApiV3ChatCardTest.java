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
package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import java.time.LocalDate;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatPublicationTest.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.exec;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.query;

/** Typed input + actual shared publication/controller/transaction/event path; ancillary ports use the ordinary fixture. */
public class ApiV3ChatCardTest {
    static JSONObject music() { return new JSONObject().put("title", " Song \" & 😀 ").put("from", " Artist ")
            .put("source", "https://audio.example/song.mp3?signature=a%2Bb&x=1").put("coverURL", "https://img.example/a.png?s=a%2Fb&b=2"); }
    static JSONObject weather() {
        final JSONArray days = new JSONArray();
        for (int i=0;i<5;i++) days.put(new JSONObject().put("date", LocalDate.of(2026,12,30).plusDays(i).toString())
                .put("code", "CLEAR_DAY").put("min", -100+i).put("max",100-i));
        return new JSONObject().put("city", " 北京 ").put("description", " 客户端预报 \" & 😀 ").put("days",days);
    }
    static JSONObject send(Fixture f,String kind,JSONObject body,String key)throws Exception { return f.publication.publishCard(f.viewer(),kind,body,key); }
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        Latkes.setServerScheme("http");Latkes.setServerHost("localhost");Latkes.setServerPort("8080");Latkes.setContextPath("");
        final JSONObject m=ApiV3ChatCardInputs.music(music()); Assert.assertEquals(m.getString("title"),"Song \" & 😀");
        Assert.assertEquals(m.getString("source"),music().getString("source"));
        for (Object source:List.of("http://audio.example/x", "javascript:alert(1)", "https://u:p@audio.example/x", "https://audio.example/x#f", "https://audio.example:99999/x", "1".repeat(33), 123)) {
            rejected(422,"invalid_request",()->ApiV3ChatCardInputs.music(music().put("source",source)));
        }
        for (Object title:List.of(" ","x".repeat(129),"<img src=x>","x\n", "\uD800", JSONObject.NULL)) {
            rejected(422,"invalid_request",()->ApiV3ChatCardInputs.music(music().put("title",title)));
        }
        Assert.assertEquals(ApiV3ChatCardInputs.music(music().put("source","1".repeat(32))).getString("source"),"1".repeat(32));
        rejected(422,"invalid_request",()->ApiV3ChatCardInputs.music(music().put("userOId","2")));
        rejected(422,"invalid_request",()->ApiV3ChatCardInputs.music(music().put("from","x".repeat(65))));
        Assert.assertEquals(ApiV3ChatCardInputs.weather(weather()).getString("city"),"北京");
        for (String field:List.of("date","code","min","max")) for(Object bad:field.equals("date")?List.of("2026-02-30","0000-01-01","2026-12-31"):
                field.equals("code")?List.of("UNKNOWN","../CLEAR_DAY") : List.of(-101,101,"1",1.5,JSONObject.NULL)) {
            final JSONObject w=weather();w.getJSONArray("days").getJSONObject(0).put(field,bad);
            rejected(422,"invalid_request",()->ApiV3ChatCardInputs.weather(w));
        }
        final JSONObject reversed=weather();reversed.getJSONArray("days").getJSONObject(0).put("min",1).put("max",0);
        rejected(422,"invalid_request",()->ApiV3ChatCardInputs.weather(reversed));
        for(String field:List.of("city","description")) {
            rejected(422,"invalid_request",()->ApiV3ChatCardInputs.weather(weather().put(field,"x".repeat(field.equals("city")?65:129))));
            rejected(422,"invalid_request",()->ApiV3ChatCardInputs.weather(weather().put(field,"<b>text</b>")));
        }
        final JSONObject shortDays=weather();shortDays.getJSONArray("days").remove(0);
        rejected(422,"invalid_request",()->ApiV3ChatCardInputs.weather(shortDays));
        Assert.assertEquals(new JSONObject(ApiV3ChatCardInputs.content("weather",ApiV3ChatCardInputs.weather(weather()))).getString("date"),"2026-12-30,2026-12-31,2027-01-01,2027-01-02,2027-01-03");
        System.out.println("PASS card 1 strict music text/HTTPS/IDs/Unicode and five-day weather dates/codes/integers/bounds; cross-year is valid and signed URLs stay exact");
        for(String kind:List.of("music","weather"))try(var f=new Fixture()) {
            f.delivery.mentionUsers=List.of(new JSONObject().put("oId","2"));
            final JSONObject body=kind.equals("music")?music():weather();
            final JSONObject first=send(f,kind,body,"card-one"); Assert.assertEquals(first.keySet(),Set.of("id","createdAt"));
            Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.count("event"),1);Assert.assertEquals(f.count("profession"),1);Assert.assertEquals(f.count("request"),1);
            Assert.assertEquals(f.delivery.activities,1);Assert.assertEquals(f.notifications.mentions.size(),0);
            final JSONObject envelope=new JSONObject(query(f.reader,"SELECT content FROM chat").getFirst().getString("content"));
            final JSONObject content=new JSONObject(envelope.getString("content"));Assert.assertEquals(content.getString("msgType"),kind);
            Assert.assertEquals(envelope.getLong("userOId"),1L);Assert.assertFalse(envelope.toString().contains("PRIVATE"));
            final JSONObject dto=ApiV3ChatMessageDto.from(new JSONObject().put("oId",first.getString("id")).put("content",envelope.toString()),text->text);
            final JSONObject projected=dto.getJSONObject("card");Assert.assertEquals(dto.getString("type"),kind);
            Assert.assertFalse(projected.has("payload"));Assert.assertFalse(projected.has("cardVersion"));
            if(kind.equals("music")){Assert.assertEquals(projected.getString("source"),music().getString("source"));Assert.assertEquals(projected.getString("title"),"Song \" & 😀");}
            else Assert.assertEquals(projected.getString("t"),"北京");
            f.restriction("sys-mute",Long.toString(NOW+1000));Assert.assertEquals(send(f,kind,body,"card-one").toString(),first.toString());
            Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.delivery.activities,1);
            final JSONObject changed=new JSONObject(body.toString()).put(kind.equals("music")?"title":"city","Other");
            rejected(409,"idempotency_conflict",()->send(f,kind,changed,"card-one"));
            exec(f.writer,"UPDATE user SET userStatus=1 WHERE oId='1'");
            rejected(404,"not_found",()->send(f,kind,body,"card-one"));
        }
        System.out.println("PASS card 2 both typed cards commit message/event/profession/latest-time/receipt once, derive current author, never generate JSON mentions; replay ignores mute but rechecks current actor");
        for(String fault:List.of("profession.after","event.before","latest.after","request.after","commit"))try(var f=new Fixture()) {
            f.faults.add(fault);try{send(f,"music",music(),"fault");Assert.fail(fault);}catch(Exception expected){}f.noPublication();
        }
        try(var f=new Fixture()) {
            f.faults.add("commit.lost");try{send(f,"weather",weather(),"lost");Assert.fail();}catch(Exception expected){}
            Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.count("request"),1);
            send(f,"weather",weather(),"lost");Assert.assertEquals(f.count("chat"),1);Assert.assertEquals(f.delivery.activities,0);
        }
        System.out.println("PASS card 3 message/profession/event/latest/receipt/commit faults roll back together; lost ACK replay never creates or broadcasts a second card");
        try(var f=new Fixture()) {
            f.restriction("sys-risk",Long.toString(NOW+1000));
            rejected(409,"confirmation_required",()->send(f,"music",music(),"risk"));
            Assert.assertTrue(send(f,"music",music(),"risk").has("id"));
        }
        try(var f=new Fixture()) {
            exec(f.writer,"UPDATE user SET userPhone='' WHERE oId='1'");rejected(403,"phone_required",()->send(f,"music",music(),"phone"));f.noPublication();
        }
        try(var f=new Fixture()) {
            ReservedWords.process=s->s.replace("Song","Filtered");
            try { send(f,"music",music(),"moderate"); } finally { ReservedWords.process=s->s; }
            final JSONObject content=new JSONObject(new JSONObject(query(f.reader,"SELECT content FROM chat").getFirst().getString("content")).getString("content"));
            Assert.assertTrue(content.getString("title").startsWith("Filtered"));Assert.assertEquals(content.getString("source"),music().getString("source"));
        }
        System.out.println("PASS card 4 shared phone/risk restrictions and text moderation apply while URLs remain opaque identifiers");
        try(var f=new Fixture()) {
            final String prefix="https://audio.example/";
            final String longURL=prefix+"x".repeat(2048-prefix.length());
            final JSONObject body=music().put("source",longURL).put("coverURL",longURL).put("title","x".repeat(128));
            final JSONObject receipt=send(f,"music",body,"max-urls");Assert.assertTrue(receipt.has("id"));
            final JSONObject stored=new JSONObject(query(f.reader,"SELECT content FROM chat").getFirst().getString("content"));
            Assert.assertTrue(stored.getString("content").length()>4096);
            Assert.assertEquals(ApiV3ChatMessageDto.from(new JSONObject().put("oId",receipt.getString("id")).put("content",stored.toString()),s->s)
                    .getJSONObject("card").getString("source"),longURL);
        }
        System.out.println("PASS card 5 independent card bounds admit both 2048-character URLs without ordinary-text truncation; exact signed source survives stored DTO projection");
        for(int op:List.of(3,4))try(var f=new Fixture()) {
            final String raw=(op==3?music():weather()).toString();
            Assert.assertEquals(f.request(null,"application/json",raw,"http",op).getInt("status"),401);
            f.scope="profile.read";Assert.assertEquals(f.request("Bearer VALID","application/json",raw,"http",op).getInt("status"),403);
            f.scope="content.write";
            Assert.assertEquals(f.request("Bearer VALID","application/json",raw,null,op).getInt("status"),422);
            Assert.assertEquals(f.request("Bearer VALID","text/plain",raw,"http",op).getInt("status"),415);
            Assert.assertEquals(f.request("Bearer VALID","application/json",raw,"http",op).getInt("status"),200);
        }
        System.out.println("PASS card 6 actual music/weather HTTP handlers require content.write Bearer, JSON and idempotency key; legacy Cookie/apiKey cannot authenticate");
    }
}
