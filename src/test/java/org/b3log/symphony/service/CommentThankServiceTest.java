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
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.CommentRemoval;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.List;
import java.util.Properties;

/** Executes the actual thanks service using the same SQLite adapters as the deletion suite. */
public class CommentThankServiceTest {
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,props);}
        try(var f=new Thanks()){
            try{f.thanks.thankInCurrentTransaction("older",f.thanks.target("30"),(a,b,c)->{});Assert.fail();}catch(IllegalStateException expected){}
            final var expected=f.thanks.target("30");
            AccountPairLocks.execute(List.of(new AccountPairLocks.Pair("older",Pointtransfer.ID_C_SYS)),()->{
                try(var scope=ArticlePublicationCaches.open(f::active)){
                    Transaction tx=new CommentRepository().beginTransaction();
                    try{f.thanks.thankInCurrentTransaction("older",expected,(a,b,c)->{});Assert.fail();}
                    catch(IllegalStateException rejected){Assert.assertTrue(rejected.getMessage().contains("account-pair"));}
                    finally{if(tx.isActive())tx.rollback();}
                }return null;
            });
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),0L);
            f.thanks.thank("older","30");
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='older'"),885L);
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='owner'"),515L);
            Assert.assertEquals(f.scalar("SELECT commentThankCnt FROM comment WHERE oId='30'"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reward WHERE senderId='older' AND dataId='30'"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification WHERE dataType="+Notification.DATA_TYPE_C_POINT_COMMENT_THANK),1L);
            Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.activityEvents,1);Assert.assertEquals(f.refreshed,List.of("owner"));
            String before=f.snapshot();f.thanks.thank("older","30");Assert.assertEquals(f.snapshot(),before);
            Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.activityEvents,1);Assert.assertEquals(f.refreshed,List.of("owner"));
        }
        System.out.println("PASS actual thanks service commits payment/counter/reward/notification together, indexes only after commit, and duplicates do not pay or publish");
        for(String failure:List.of("reward.select","comment.update","reward.add","notification.add","debit-null","debit-blank","debit-failure")){
            try(var f=new Thanks()){
                String before=f.snapshot();f.fail=failure;
                try{f.thanks.thank("older","30");Assert.fail(failure);}catch(Exception expected){}
                Assert.assertEquals(f.snapshot(),before,failure);Assert.assertFalse(f.active());Assert.assertEquals(f.indexEvents,0);
                Assert.assertEquals(f.activityEvents,0);Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        System.out.println("PASS lookup/counter/reward/notification failures and null/blank/failed transfers roll all thanks writes back without effects");
        try(var f=new Thanks()){
            f.exec("UPDATE comment SET commentAnonymous=1,commentVisible=1 WHERE oId='30'");
            f.exec("UPDATE user SET userPoint=0 WHERE oId='older'");
            f.thanks.thank("older","30");
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),0L);Assert.assertEquals(f.scalar("SELECT commentThankCnt FROM comment WHERE oId='30'"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reward"),1L);Assert.assertEquals(f.indexEvents,0);Assert.assertEquals(f.activityEvents,1);
        }
        System.out.println("PASS anonymous/private legacy thanks retains no-transfer behavior even with zero sender balance");
        for(String change:List.of("DELETE FROM comment WHERE oId='30'","UPDATE comment SET commentStatus=1 WHERE oId='30'","DELETE FROM user WHERE oId='older'","UPDATE user SET userStatus=1 WHERE oId='older'","DELETE FROM user WHERE oId='owner'","UPDATE user SET userStatus=1 WHERE oId='owner'")){
            try(var f=new Thanks()){
                f.exec(change);String before=f.snapshot();f.thanks.thank("older","30");Assert.assertEquals(f.snapshot(),before);
                Assert.assertEquals(f.indexEvents,0);Assert.assertEquals(f.activityEvents,0);
            }
        }
        try(var f=new Thanks()){
            String before=f.snapshot();try{f.thanks.thank("owner","30");Assert.fail();}catch(CommentThankMgmtService.Rejected e){Assert.assertEquals(e.code,"self");}
            Assert.assertEquals(f.snapshot(),before);
            f.exec("UPDATE user SET userPoint=0 WHERE oId='older'");before=f.snapshot();
            try{f.thanks.thank("older","30");Assert.fail();}catch(CommentThankMgmtService.Rejected e){Assert.assertEquals(e.code,"transfer_failed");}
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS missing/invalid targets or actors remain legacy no-ops; self-thanks and insufficient balance fail without writes");
        try(var f=new Thanks()){
            var expected=f.thanks.target("30");String before=f.snapshot();
            try{f.core(expected,(a,b,c)->{throw new CommentRemoval.Rejected("forbidden");});Assert.fail();}catch(CommentRemoval.Rejected e){}
            Assert.assertEquals(f.snapshot(),before);
            f.exec("UPDATE comment SET commentAuthorId='older' WHERE oId='30'");before=f.snapshot();
            try{f.core(expected,(a,b,c)->{});Assert.fail();}catch(CommentThankMgmtService.Rejected e){Assert.assertEquals(e.code,"target_changed");}
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS explicit Bearer authorization callback runs before payment, and changed author binding cannot expand the prelocked account set");
        try(var f=new Thanks()){
            var expected=f.thanks.target("30");f.transaction(()->f.remove("owner","30"));String before=f.snapshot();
            Assert.assertNull(f.core(expected,(a,b,c)->{}));Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Thanks()){
            f.thanks.thank("older","30");String before=f.snapshot();
            try{f.transaction(()->f.remove("owner","30"));Assert.fail();}catch(CommentRemoval.Rejected e){Assert.assertEquals(e.code,"has_thanks");}
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS both serialized delete/thanks orders preserve consistency, including a stale prelock target projection (not a MySQL concurrency test)");
        try(var f=new Thanks()){
            var first=f.idempotent("thanks-once");Assert.assertTrue(first.applied());Assert.assertEquals(f.mutations,1);
            Assert.assertFalse(f.idempotent("thanks-once").applied());Assert.assertEquals(f.mutations,1);
            Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.activityEvents,1);
            Assert.assertTrue(f.idempotent("thanks-new-key").applied());Assert.assertEquals(f.mutations,2);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.activityEvents,1);
            f.exec("DELETE FROM comment WHERE oId='30'");Assert.assertEquals(f.idempotent("thanks-once").result().toString(),first.result().toString());
            Assert.assertEquals(f.mutations,2);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM requests"),2L);
            try{f.idempotent("missing-new-key");Assert.fail();}catch(CommentThankMgmtService.Rejected e){Assert.assertEquals(e.code,"not_found");}
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM requests"),2L);
        }
        try(var f=new Thanks()){
            String before=f.snapshot();f.failResults=true;
            try{f.idempotent("persist-failure");Assert.fail();}catch(RepositoryException expected){}
            Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.indexEvents,0);Assert.assertEquals(f.activityEvents,0);
            f.failResults=false;Assert.assertTrue(f.idempotent("persist-failure").applied());Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);
        }
        System.out.println("PASS actual idempotency coordinator replays thanks without writes/events (even after deletion), new keys do not pay twice, and result failures roll the whole transaction back");
        try(var f=new Thanks()){
            f.fail="index-event";f.thanks.thank("older","30");
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reward"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);
            Assert.assertEquals(f.refreshed,List.of("owner"));Assert.assertEquals(f.activityEvents,1);Assert.assertFalse(f.active());
        }
        System.out.println("PASS post-commit indexing failure preserves committed thanks and permits notification/activity effects");
    }
    static class Thanks extends CommentRemovalServiceTest.Fixture {
        final CommentThankMgmtService thanks=new CommentThankMgmtService();
        Thanks()throws Exception {
            exec("ALTER TABLE comment ADD COLUMN commentStatus INT DEFAULT 0");exec("ALTER TABLE reward ADD COLUMN senderId TEXT");
            for(Object bean:List.of(new CommentRepository(),new ArticleRepository(),new UserRepository(),new OptionRepository(),new RewardRepository(),new PointtransferMgmtService(),new NotificationMgmtService(),new LivenessMgmtService(),new ArticleMgmtService())){
                String field=switch(bean.getClass().getSimpleName()){case "CommentRepository"->"comments";case "ArticleRepository"->"articles";case "UserRepository"->"users";case "OptionRepository"->"options";case "RewardRepository"->"rewards";case "PointtransferMgmtService"->"points";case "NotificationMgmtService"->"notifications";case "LivenessMgmtService"->"liveness";default->"articleWrites";};
                var target=CommentThankMgmtService.class.getDeclaredField(field);target.setAccessible(true);target.set(thanks,bean);
            }
        }
        CommentThankMgmtService.Result core(CommentThankMgmtService.Target target,CommentThankMgmtService.TargetCheck check)throws Exception {
            return AccountPairLocks.execute(List.of(new AccountPairLocks.Pair("older",Pointtransfer.ID_C_SYS),new AccountPairLocks.Pair("older",target.author())),()->{
                try(var scope=ArticlePublicationCaches.open(this::active)){
                    Transaction tx=new CommentRepository().beginTransaction();
                    try{var result=thanks.thankInCurrentTransaction("older",target,check);tx.commit();return result;}finally{if(tx.isActive())tx.rollback();}
                }
            });
        }
        ApiV3IdempotencyMgmtService.Execution idempotent(String key)throws Exception {
            final var expected=thanks.target("30");
            return new ArticleMgmtService().withPublicationLocks("older",null==expected?List.of(Pointtransfer.ID_C_SYS):List.of(Pointtransfer.ID_C_SYS,expected.author()),()->{
                return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(requestStore(),"older","comments.thank:30",key,new JSONObject(),()->{
                    if(null==expected)throw new CommentThankMgmtService.Rejected("not_found");
                    mutations++;pending=thanks.thankInCurrentTransaction("older",expected,(a,b,c)->{});
                    if(null==pending)throw new CommentThankMgmtService.Rejected("not_found");
                    return new JSONObject().put("id","30").put("thanked",true);
                });
            },execution->{if(execution.applied())thanks.afterThanked(pending);});
        }
        CommentThankMgmtService.Result pending;
    }
}
