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

import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;

/** Actual bounded cursor protocol with deterministic source/sink faults; SQL is covered separately. */
public class ApiV3ChatStreamTest {
    static class Fixture implements ApiV3ChatStream.Source,ApiV3ChatStream.Sink {
        long head;int sync,reads;boolean failRead,failSend,appendDuringRead,rawRows;final List<JSONObject> rows=new ArrayList<>();final List<Long> delivered=new ArrayList<>();
        final ApiV3ChatStream stream=new ApiV3ChatStream(this,this);
        void add(){head++;rows.add(new JSONObject().put("revision",head).put("messageId",Long.toString(1000-head)).put("kind","created"));}
        void add(int count){for(int i=0;i<count;i++){head++;rows.add(new JSONObject().put("revision",head).put("messageId",Long.toString(head+10000)).put("kind","created"));}}
        public long head(){return head;}
        public List<JSONObject> after(long revision,long through,int limit){
            reads++;Assert.assertEquals(limit,200);if(failRead)throw new IllegalStateException("unavailable");
            if(appendDuringRead){appendDuringRead=false;add();}
            if(rawRows)return rows;
            return rows.stream().filter(r->r.getLong("revision")>revision&&r.getLong("revision")<=through).limit(limit).toList();
        }
        public void changed(String kind,String id,long revision){if(failSend)throw new IllegalStateException("sink unavailable");delivered.add(revision);}
        public void syncRequired(){sync++;}
    }
    public static void main(String[] args)throws Exception {
        var f=new Fixture();f.add(3);f.stream.poll();Assert.assertEquals(f.sync,1);Assert.assertTrue(f.delivered.isEmpty());f.add();f.stream.poll();Assert.assertEquals(f.delivered,List.of(4L));f.stream.poll();Assert.assertEquals(f.delivered,List.of(4L));f.stream.reset();f.stream.poll();Assert.assertEquals(f.sync,2);Assert.assertEquals(f.delivered,List.of(4L));
        System.out.println("PASS stream baseline/reset requests HTTP synchronization and subsequent polls deliver each new committed revision without replaying old history");
        f=new Fixture();f.stream.poll();f.reads=0;f.add(1201);f.stream.poll();Assert.assertEquals(f.reads,5);Assert.assertEquals(f.delivered.size(),1000);f.stream.poll();Assert.assertEquals(f.delivered.size(),1201);Assert.assertEquals(new HashSet<>(f.delivered).size(),1201);Assert.assertEquals(f.delivered.getLast(),1201L);
        System.out.println("PASS backlog work is bounded per poll and remaining contiguous revisions carry forward without truncation or numeric cursor reordering");
        for(boolean all:new boolean[]{false,true}){
            f=new Fixture();f.stream.poll();f.add(4);if(all)f.rows.clear();else f.rows.remove(1);f.stream.poll();Assert.assertEquals(f.sync,2);f.add();f.stream.poll();Assert.assertEquals(f.delivered.getLast(),5L);
        }
        f=new Fixture();f.add(4);f.stream.poll();f.head=1;f.rows.clear();f.stream.poll();Assert.assertEquals(f.sync,2);f.add();f.stream.poll();Assert.assertEquals(f.delivered,List.of(2L));
        System.out.println("PASS retained-event gaps, fully pruned ranges and head rollback demand synchronization before a fresh baseline rather than silently skipping changes");
        f=new Fixture();f.stream.poll();f.add();f.appendDuringRead=true;f.stream.poll();Assert.assertEquals(f.delivered,List.of(1L));f.stream.poll();Assert.assertEquals(f.delivered,List.of(1L,2L));
        f=new Fixture();f.stream.poll();f.add();f.failRead=true;try{f.stream.poll();Assert.fail();}catch(IllegalStateException expected){}f.failRead=false;f.failSend=true;try{f.stream.poll();Assert.fail();}catch(IllegalStateException expected){}f.failSend=false;f.stream.poll();Assert.assertEquals(f.delivered,List.of(1L));
        System.out.println("PASS poll head bounds concurrent new commits and source/sink failures cannot advance past an undelivered revision");
        for(String field:List.of("messageId","kind","revision")){
            f=new Fixture();f.stream.poll();f.add();f.rawRows=true;f.rows.getFirst().put(field,field.equals("revision")?0:field.equals("kind")?"PRIVATE-command":"1' OR 1=1");
            try{f.stream.poll();Assert.fail();}catch(IllegalStateException expected){}Assert.assertTrue(f.delivered.isEmpty());
        }
        System.out.println("PASS corrupt event IDs/kinds/revisions fail closed before a payload reaches the sink");
        f=new Fixture();f.stream.poll();f.add();f.rows.getFirst().put("kind","topic").put("messageId","0");f.stream.poll();Assert.assertEquals(f.delivered,List.of(1L));
        f=new Fixture();f.stream.poll();f.add();f.rows.getFirst().put("kind","topic").put("messageId","123");try{f.stream.poll();Assert.fail();}catch(IllegalStateException expected){}Assert.assertTrue(f.delivered.isEmpty());
        System.out.println("PASS committed topic markers share the ordered cursor while invalid topic target identities are rejected before delivery");
    }
}
