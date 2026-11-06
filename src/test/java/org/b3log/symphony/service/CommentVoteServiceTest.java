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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.CommentRemoval;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.List;
import java.util.Properties;

/** Actual comment-vote service and SQL; SQLite adapters do not emulate MySQL locks or HTTP. */
public class CommentVoteServiceTest {
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,props);}
        try(var f=new Votes()){
            var first=f.voting.toggle("older","30",Vote.TYPE_C_UP,true);Assert.assertEquals(first.previous(),-1);Assert.assertEquals(first.current(),Vote.TYPE_C_UP);
            Assert.assertTrue(first.notified());Assert.assertEquals(f.scalar("SELECT commentGoodCnt FROM comment WHERE oId='30'"),1L);
            var clear=f.voting.toggle("older","30",Vote.TYPE_C_UP,true);Assert.assertEquals(clear.previous(),Vote.TYPE_C_UP);Assert.assertEquals(clear.current(),-1);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"),0L);Assert.assertEquals(f.activityEvents,1);
            f.voting.toggle("older","30",Vote.TYPE_C_DOWN,true);Assert.assertEquals(f.scalar("SELECT commentBadCnt FROM comment WHERE oId='30'"),1L);
            f.voting.toggle("older","30",Vote.TYPE_C_UP,true);Assert.assertEquals(f.scalar("SELECT commentBadCnt FROM comment WHERE oId='30'"),0L);
            Assert.assertEquals(f.scalar("SELECT commentGoodCnt FROM comment WHERE oId='30'"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification WHERE dataId='30-older' AND dataType="+Notification.DATA_TYPE_C_COMMENT_VOTE_UP),1L);
            Assert.assertEquals(f.activityEvents,3);Assert.assertEquals(f.refreshed,List.of("owner"));
            Assert.assertEquals(f.scalar("SELECT commentThankCnt FROM comment WHERE oId='30'"),7L);Assert.assertEquals(f.text("SELECT commentContent FROM comment WHERE oId='30'"),"SECRET-body");
            Assert.assertTrue(Double.parseDouble(f.text("SELECT commentScore FROM comment WHERE oId='30'"))>0);
        }
        System.out.println("PASS legacy toggle/switch/clear preserves counts and score, posts one up notification, and does not overwrite other comment fields");
        for(String failure:List.of("vote.select","vote.add","comment.update","notification.add")){
            try(var f=new Votes()){
                String before=f.snapshot();f.fail=failure;
                try{f.voting.toggle("older","30",Vote.TYPE_C_UP,true);Assert.fail();}catch(RepositoryException rejected){}
                Assert.assertEquals(f.snapshot(),before);Assert.assertFalse(f.active());Assert.assertEquals(f.activityEvents,0);Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        try(var f=new Votes()){
            f.voting.toggle("older","30",Vote.TYPE_C_UP,false);String before=f.snapshot();f.fail="vote.remove";
            try{f.voting.toggle("older","30",Vote.TYPE_C_DOWN,false);Assert.fail();}catch(RepositoryException rejected){}
            Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.activityEvents,1);
        }
        System.out.println("PASS vote lookup/insert/remove/counter/notification faults roll back the complete mutation and suppress effects");
        try(var f=new Votes()){
            var target=f.thanks.target("30");
            try{f.voting.setInCurrentTransaction("older",target,Vote.TYPE_C_UP,false,false,(a,b,c)->{});Assert.fail();}catch(IllegalStateException rejected){}
            String before=f.snapshot();try{f.set(target,Vote.TYPE_C_UP,(a,b,c)->{throw new CommentVoteMgmtService.Rejected("forbidden");});Assert.fail();}catch(CommentVoteMgmtService.Rejected rejected){}
            Assert.assertEquals(f.snapshot(),before);
            try{f.voting.toggle("owner","30",Vote.TYPE_C_UP,false);Assert.fail();}catch(CommentVoteMgmtService.Rejected rejected){Assert.assertEquals(rejected.code,"self");}
            Assert.assertEquals(f.snapshot(),before);f.exec("UPDATE user SET userRole='adminRole' WHERE oId='owner'");
            Assert.assertEquals(f.voting.toggle("owner","30",Vote.TYPE_C_UP,true).current(),Vote.TYPE_C_UP);Assert.assertTrue(f.refreshed.isEmpty());
        }
        System.out.println("PASS transaction and explicit authorization guards reject before writing; legacy admin self-vote exemption remains");
        try(var f=new Votes()){
            var target=f.thanks.target("30");var result=f.set(target,Vote.TYPE_C_UP,(a,b,c)->{});f.voting.afterVote("older",result);
            String before=f.snapshot();var same=f.set(target,Vote.TYPE_C_UP,(a,b,c)->{});f.voting.afterVote("older",same);
            Assert.assertFalse(same.changed());Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.activityEvents,1);
            f.set(target,-1,(a,b,c)->{});Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"),0L);
            Assert.assertEquals(f.scalar("SELECT commentGoodCnt FROM comment WHERE oId='30'"),0L);
        }
        System.out.println("PASS caller-owned set/clear is idempotent without toggle or duplicate activity");
        try(var f=new Votes()){
            f.exec("UPDATE comment SET commentThankCnt=0 WHERE oId='30'");var target=f.thanks.target("30");
            f.transaction(()->f.remove("owner","30"));String before=f.snapshot();
            try{f.set(target,Vote.TYPE_C_UP,(a,b,c)->{});Assert.fail();}catch(CommentVoteMgmtService.Rejected rejected){Assert.assertEquals(rejected.code,"not_found");}
            Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Votes()){
            f.exec("UPDATE comment SET commentThankCnt=0 WHERE oId='30'");f.voting.toggle("older","30",Vote.TYPE_C_UP,true);String before=f.snapshot();
            try{f.transaction(()->f.remove("owner","30"));Assert.fail();}catch(CommentRemoval.Rejected rejected){Assert.assertEquals(rejected.code,"has_votes");}
            Assert.assertEquals(f.snapshot(),before);f.voting.toggle("older","30",Vote.TYPE_C_UP,true);
            f.transaction(()->f.remove("owner","30"));Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"),0L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification WHERE dataId='30-older'"),0L);
        }
        System.out.println("PASS both serialized vote/delete orders reject the losing operation; clearing the vote permits deletion and cleans its notification");
        for(String sql:List.of("INSERT INTO vote VALUES('duplicate1','30',1,'older',0),('duplicate2','30',1,'older',1)","INSERT INTO vote VALUES('invalid','30',1,'older',9)","UPDATE comment SET commentGoodCnt=2147483647 WHERE oId='30'","UPDATE comment SET commentGoodCnt=-2147483648 WHERE oId='30'")){
            try(var f=new Votes()){
                f.exec(sql);String before=f.snapshot();try{f.voting.toggle("older","30",Vote.TYPE_C_UP,true);Assert.fail();}catch(RuntimeException rejected){}
                Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.activityEvents,0);
            }
        }
        System.out.println("PASS corrupt duplicate/type rows and counter overflow fail closed without partial writes");
    }
    static class Votes extends CommentThankServiceTest.Thanks {
        final CommentVoteMgmtService voting=new CommentVoteMgmtService();
        Votes()throws Exception {
            exec("ALTER TABLE vote ADD COLUMN userId TEXT");exec("ALTER TABLE vote ADD COLUMN type INT");
            exec("ALTER TABLE comment ADD COLUMN commentScore DOUBLE DEFAULT 0");exec("ALTER TABLE user ADD COLUMN userRole TEXT");
            exec("UPDATE comment SET commentThankCnt=7 WHERE oId='30'");
            // Base deletion fixtures include historical vote notifications; this suite creates its own.
            exec("DELETE FROM notification WHERE dataType="+Notification.DATA_TYPE_C_COMMENT_VOTE_UP);
            for(Object bean:List.of(new CommentRepository(),new ArticleRepository(),new UserRepository(),new OptionRepository(),new VoteRepository(),new NotificationMgmtService(),new LivenessMgmtService(),new ArticleMgmtService(),thanks)){
                String field=switch(bean.getClass().getSimpleName()){case "CommentRepository"->"comments";case "ArticleRepository"->"articles";case "UserRepository"->"users";case "OptionRepository"->"options";case "VoteRepository"->"votes";case "NotificationMgmtService"->"notifications";case "LivenessMgmtService"->"liveness";case "ArticleMgmtService"->"articleWrites";default->"targets";};
                var target=CommentVoteMgmtService.class.getDeclaredField(field);target.setAccessible(true);target.set(voting,bean);
            }
        }
        CommentVoteMgmtService.Result set(CommentThankMgmtService.Target expected,int choice,CommentThankMgmtService.TargetCheck check)throws Exception {
            return new ArticleMgmtService().withPublicationLock("older",()->{
                var tx=new CommentRepository().beginTransaction();try{var result=voting.setInCurrentTransaction("older",expected,choice,false,true,check);tx.commit();return result;}finally{if(tx.isActive())tx.rollback();}
            },ignored->{});
        }
    }
}
