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
import org.b3log.latke.repository.RepositoryException;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.PrivateMessageReadStateTest.reject;

/** Actual marker SQL and data mutations with an independent observer, including locally caught faults. */
public class PrivateMessageRevisionTest {
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new PrivateMessageWithdrawalTest.Withdrawal()){
            Assert.assertTrue(f.revisionRows.snapshot(Set.of("1","2")).isEmpty());String id=f.send("2","PRIVATE_BODY","send").getString("id");
            var initial=f.revisionRows.snapshot(Set.of("1","2","3"));Assert.assertEquals(initial.keySet(),Set.of("1","2"));Assert.assertFalse(query(f.reader,"SELECT * FROM private_revision").toString().contains("PRIVATE_BODY"));
            f.begin();f.state.markInCurrentTransaction("2","1");Assert.assertEquals(query(f.reader,"SELECT revision FROM private_revision WHERE oId='2'").getFirst().getString("revision"),initial.get("2"));
            try{f.revisionRows.snapshot(Set.of("1"));Assert.fail();}catch(IllegalStateException expected){}f.rollback();Assert.assertEquals(f.revisionRows.snapshot(initial.keySet()),initial);
            f.atomic(()->f.state.markInCurrentTransaction("2","1"));var read=f.revisionRows.snapshot(initial.keySet());Assert.assertEquals(read.get("1"),initial.get("1"));Assert.assertNotEquals(read.get("2"),initial.get("2"));
            f.remove("1",id,"remove");var removed=f.revisionRows.snapshot(initial.keySet());Assert.assertNotEquals(removed.get("1"),read.get("1"));Assert.assertNotEquals(removed.get("2"),read.get("2"));
            f.remove("1",id,"remove");Assert.assertEquals(f.revisionRows.snapshot(initial.keySet()),removed);
        }
        System.out.println("PASS committed private markers cover only affected owners, are invisible to an independent reader until commit, survive rollback unchanged, include read and withdrawal changes, and never advance on idempotent replay");
        try(var f=new PrivateMessageWithdrawalTest.Withdrawal()){
            f.send(org.b3log.symphony.util.ApiV3PrivateMessages.FILE_TRANSFER_ID,"file-private","file");Assert.assertEquals(f.revisionRows.snapshot(Set.of("1",org.b3log.symphony.util.ApiV3PrivateMessages.FILE_TRANSFER_ID)).keySet(),Set.of("1"));
            try{f.revisions.touchInCurrentTransaction(Set.of("1"));Assert.fail();}catch(IllegalStateException expected){}
        }
        System.out.println("PASS private marker writes require the caller's transaction, store no file-transfer pseudo owner and contain no private body or credentials");
        for(String operation:List.of("send","read","remove"))try(var f=new PrivateMessageWithdrawalTest.Withdrawal()){
            String id=f.send("2","private","send").getString("id"),before=f.all();exec(f.writer,"CREATE TRIGGER reject_private_revision BEFORE INSERT ON private_revision WHEN NEW.oId='2' BEGIN SELECT RAISE(ABORT,'PRIVATE_REVISION_SQL'); END");
            if(operation.equals("send"))reject(()->f.send("2","new-private","fault"),503);
            else if(operation.equals("remove"))reject(()->f.remove("1",id,"fault"),503);
            else{f.begin();exec(f.writer,"INSERT INTO unrelated VALUES('earlier')");try{f.state.markInCurrentTransaction("2","1");Assert.fail();}catch(RepositoryException expected){}f.commit();Assert.assertEquals(query(f.reader,"SELECT * FROM unrelated").size(),1);}
            Assert.assertEquals(f.all(),before,operation);
        }
        System.out.println("PASS a later owner's marker failure restores an earlier marker and all message/unread/list data; locally caught read-marker failure cannot leak deletion into an owner commit");
    }
}
