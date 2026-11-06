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
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual legacy repository boundaries; only generated-ID inserts and filter SQL dialect are adapters. */
public class LegacyPrivateMessageRevisionTest {
    static class Legacy extends PrivateMessagePublicationTest.Publication {
        final ChatInfoRepository oldMessages;
        final ChatUnreadRepository oldUnread;
        Legacy()throws Exception {
            oldMessages=new ChatInfoRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override protected String insertMessage(JSONObject row)throws RepositoryException{
                    String id=row.getString("oId");
                    try{ApiV3TransactionStatements.execute("INSERT INTO "+info+" VALUES(?,?,?,?,?,?)",id,row.getString("fromId"),row.getString("toId"),"untrusted","2026-10-20 12:00:00",row.getString("content"));return id;}
                    catch(RepositoryException e){throw e;}catch(Exception e){throw new RepositoryException(e);}
                }
            };
            oldUnread=new ChatUnreadRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override protected String insertUnread(JSONObject row)throws RepositoryException{
                    String id=row.getString("oId");
                    try{ApiV3TransactionStatements.execute("INSERT INTO "+table+" VALUES(?,?,?,?,?)",id,row.getString("fromId"),row.getString("toId"),"untrusted",row.optString("messageId"));return id;}
                    catch(RepositoryException e){throw e;}catch(Exception e){throw new RepositoryException(e);}
                }
                @Override protected void removeUnread(Query q)throws RepositoryException {
                    var args=new ArrayList<Object>();String where=filter(q.getFilter(),args);
                    try{ApiV3TransactionStatements.execute("DELETE FROM "+table+" WHERE "+where,args.toArray());}
                    catch(RepositoryException e){throw e;}catch(Exception e){throw new RepositoryException(e);}
                }
            };
            inject(oldMessages,ChatInfoRepository.class,"revisions",revisions);
            inject(oldUnread,ChatUnreadRepository.class,"revisions",revisions);
        }
    }
    static String filter(Filter f,List<Object> values){
        if(f instanceof PropertyFilter p){Assert.assertTrue(Set.of("fromId","toId").contains(p.getKey()));Assert.assertEquals(p.getOperator(),FilterOperator.EQUAL);values.add(p.getValue());return p.getKey()+"=?";}
        var c=(CompositeFilter)f;Assert.assertEquals(c.getOperator(),CompositeFilterOperator.AND);
        return "("+String.join(" AND ",c.getSubFilters().stream().map(part->filter(part,values)).toList())+")";
    }
    static JSONObject message(){return new JSONObject().put("oId","500").put("fromId","1").put("toId","2").put("content","PRIVATE_LEGACY_BODY");}
    static JSONObject unread(){return new JSONObject().put("oId","501").put("fromId","1").put("toId","2").put("messageId","500");}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Legacy()){
            String id=f.oldMessages.addLegacy(message());Assert.assertEquals(id,"500");Assert.assertNull(JdbcRepository.TX.get());
            var sent=f.revisionRows.snapshot(Set.of("1","2","3"));Assert.assertEquals(sent.keySet(),Set.of("1","2"));
            Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.listing).isEmpty());
            f.oldUnread.addLegacy(unread());Assert.assertNull(JdbcRepository.TX.get());var received=f.revisionRows.snapshot(sent.keySet());
            Assert.assertEquals(received.get("1"),sent.get("1"));Assert.assertNotEquals(received.get("2"),sent.get("2"));
            f.projection.refreshLegacy(id);var listed=f.revisionRows.snapshot(sent.keySet());
            Assert.assertNotEquals(listed.get("1"),received.get("1"));Assert.assertNotEquals(listed.get("2"),received.get("2"));
            Assert.assertEquals(query(f.reader,"SELECT lastMessageId FROM "+f.listing).getFirst().getString("lastMessageId"),id);
            Assert.assertFalse(query(f.reader,"SELECT * FROM private_revision").toString().contains("PRIVATE_LEGACY_BODY"));
        }
        System.out.println("PASS legacy message, unread and delayed list commits each invalidate only affected owners, release root JDBC resources, preserve separate-step semantics and never put the private body in markers");
        for(String operation:List.of("message","unread","read","list","commit"))try(var f=new Legacy()){
            String before=f.all();
            if(!operation.equals("commit"))exec(f.writer,"CREATE TRIGGER fail_legacy_marker BEFORE INSERT ON private_revision WHEN NEW.oId='2' BEGIN SELECT RAISE(ABORT,'PRIVATE_MARKER_ERROR'); END");
            if(operation.equals("commit"))f.faults.add("commit");
            if(operation.equals("read")){
                f.begin();exec(f.writer,"INSERT INTO unrelated VALUES('earlier')");
                try{f.oldUnread.removeForOwnerInCurrentTransaction("2",new Query());Assert.fail();}catch(RepositoryException expected){}f.commit();Assert.assertEquals(query(f.reader,"SELECT * FROM unrelated").size(),1);
            }else if(operation.equals("list")){
                try{f.projection.refreshLegacy("100");Assert.fail();}catch(RepositoryException expected){}
            }else{
                try{if(operation.equals("unread"))f.oldUnread.addLegacy(unread());else f.oldMessages.addLegacy(message());Assert.fail();}catch(RepositoryException expected){}
            }
            Assert.assertEquals(f.all(),before,operation);Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());
        }
        System.out.println("PASS legacy marker and strict-commit failures roll back the whole affected step and close its root; caught owner-read failures preserve deletions and unrelated outer work without half a marker");
        try(var f=new Legacy()){
            f.oldMessages.addLegacy(message());f.oldUnread.addLegacy(unread());var before=f.revisionRows.snapshot(Set.of("1","2"));
            f.begin();f.oldUnread.removeForOwnerInCurrentTransaction("2",new Query());
            Assert.assertEquals(f.revisionRows.select("SELECT * FROM private_revision WHERE oId=?","2").getFirst().getString("revision").equals(before.get("2")),false);
            Assert.assertEquals(query(f.reader,"SELECT revision FROM private_revision WHERE oId='2'").getFirst().getString("revision"),before.get("2"));f.commit();
            Assert.assertTrue(query(f.reader,"SELECT * FROM "+f.table+" WHERE toId='2'").isEmpty());Assert.assertFalse(query(f.reader,"SELECT * FROM "+f.table+" WHERE toId='1'").isEmpty());
            Assert.assertEquals(f.revisionRows.snapshot(Set.of("1")).get("1"),before.get("1"));
            String rows=f.data();f.atomic(()->f.oldUnread.removeForOwnerInCurrentTransaction("2",new Query().setFilter(new PropertyFilter("toId",FilterOperator.EQUAL,"1"))));Assert.assertEquals(f.data(),rows);
            try{f.oldMessages.add(message());Assert.fail();}catch(IllegalStateException expected){}
            f.begin();try{f.oldMessages.addLegacy(message());Assert.fail();}catch(IllegalStateException expected){}finally{f.rollback();}
        }
        System.out.println("PASS legacy bulk read changes only authenticated-owner records even for broad/conflicting queries; its marker is invisible before commit and repository/root transaction ownership is enforced");
    }
}
