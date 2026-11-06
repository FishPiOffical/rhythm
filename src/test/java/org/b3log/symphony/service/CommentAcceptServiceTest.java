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
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.CommentRemoval;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.List;
import java.util.Properties;

/** Actual acceptance core, SQL and idempotency coordinator with SQLite runtime adapters. */
public class CommentAcceptServiceTest {
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,props);}
        try(var f=new Acceptance()){
            try{f.acceptance.acceptInCurrentTransaction("older",f.thanks.target("30"),(a,b,c)->{});Assert.fail();}catch(IllegalStateException expected){}
            var expected=f.thanks.target("30");
            AccountPairLocks.execute(List.of(new AccountPairLocks.Pair("older",Pointtransfer.ID_C_SYS)),()->{
                try(var scope=ArticlePublicationCaches.open(f::active)){
                    var tx=new CommentRepository().beginTransaction();
                    try{f.acceptance.acceptInCurrentTransaction("older",expected,(a,b,c)->{});Assert.fail();}
                    catch(IllegalStateException rejected){Assert.assertTrue(rejected.getMessage().contains("account-pair"));}
                    finally{if(tx.isActive())tx.rollback();}
                }return null;
            });
            f.acceptance.accept("older","30");
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='older'"),800L);Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='owner'"),600L);
            Assert.assertEquals(f.scalar("SELECT commentQnAOffered FROM comment WHERE oId='30'"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reward WHERE senderId='older' AND dataId='10' AND type="+Reward.TYPE_C_ACCEPT_COMMENT),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification WHERE dataType="+Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT),1L);
            Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.activityFields,List.of(Liveness.LIVENESS_ACCEPT_ANSWER));Assert.assertEquals(f.refreshed,List.of("owner"));
            String before=f.snapshot();f.acceptance.accept("older","30");f.acceptance.accept("older","40");Assert.assertEquals(f.snapshot(),before);
            Assert.assertEquals(f.scalar("SELECT commentQnAOffered FROM comment WHERE oId='40'"),0L);Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.activityEvents,1);
        }
        System.out.println("PASS acceptance commits one transfer/flag/reward/notification, posts effects after commit, and repeated or competing legacy candidates do not pay twice");
        for(String failure:List.of("comment.select","comment.update","reward.add","notification.add","debit-null","debit-blank","debit-failure")){
            try(var f=new Acceptance()){
                var expected=f.thanks.target("30");String before=f.snapshot();f.fail=failure;
                try{f.acceptanceCore(expected,(a,b,c)->{});Assert.fail(failure);}catch(Exception rejected){}
                Assert.assertEquals(f.snapshot(),before);Assert.assertFalse(f.active());Assert.assertEquals(f.indexEvents,0);Assert.assertEquals(f.activityEvents,0);Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        System.out.println("PASS acceptance query/counter/reward/notification and transfer faults roll payment, acceptance and related writes back");
        for(String change:List.of("UPDATE comment SET commentAnonymous=1 WHERE oId='30'","UPDATE article SET articleQnAOfferPoint=0 WHERE oId='10'")){
            try(var f=new Acceptance()){
                f.exec(change);f.acceptance.accept("older","30");Assert.assertEquals(f.scalar("SELECT commentQnAOffered FROM comment WHERE oId='30'"),1L);
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reward"),1L);Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='older'"),900L);
                Assert.assertEquals(f.scalar("SELECT COALESCE(SUM(amount),0) FROM ledger"),0L);
            }
        }
        System.out.println("PASS anonymous acceptance skips transfer and zero-offer public acceptance retains the original zero-fee behavior");
        for(String change:List.of("UPDATE user SET userPoint=0 WHERE oId='older'","UPDATE article SET articleQnAOfferPoint=-1 WHERE oId='10'","UPDATE article SET articleAuthorId='owner' WHERE oId='10'")){
            try(var f=new Acceptance()){
                f.exec(change);String before=f.snapshot();try{f.acceptance.accept("older","30");Assert.fail();}catch(CommentAcceptMgmtService.Rejected rejected){}
                Assert.assertEquals(f.snapshot(),before);
            }
        }
        try(var f=new Acceptance()){
            String before=f.snapshot();try{f.acceptance.accept("older","20");Assert.fail();}catch(CommentAcceptMgmtService.Rejected rejected){Assert.assertEquals(rejected.code,"self");}
            Assert.assertEquals(f.snapshot(),before);
            var expected=f.thanks.target("30");try{f.acceptanceCore(expected,(a,b,c)->{throw new CommentAcceptMgmtService.Rejected("forbidden");});Assert.fail();}catch(CommentAcceptMgmtService.Rejected rejected){}
            Assert.assertEquals(f.snapshot(),before);f.exec("UPDATE comment SET commentOnArticleId='other' WHERE oId='30'");before=f.snapshot();
            try{f.acceptanceCore(expected,(a,b,c)->{});Assert.fail();}catch(CommentAcceptMgmtService.Rejected rejected){Assert.assertEquals(rejected.code,"target_changed");}
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS self acceptance, non-owner actor, insufficient balance, invalid offer, explicit read denial and stale target binding cannot write");
        try(var f=new Acceptance()){
            var expected=f.thanks.target("30");f.transaction(()->f.remove("owner","30"));String before=f.snapshot();
            try{f.acceptanceCore(expected,(a,b,c)->{});Assert.fail();}catch(CommentAcceptMgmtService.Rejected rejected){Assert.assertEquals(rejected.code,"not_found");}
            Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Acceptance()){
            f.acceptance.accept("older","30");String before=f.snapshot();
            try{f.transaction(()->f.remove("owner","30"));Assert.fail();}catch(CommentRemoval.Rejected rejected){Assert.assertEquals(rejected.code,"accepted");}
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS both serialized acceptance/delete orders reject the losing mutation without orphan payment or a deletion fee (not concurrent MySQL proof)");
        try(var f=new Acceptance()){
            var first=f.idempotent("accept-once","30");Assert.assertTrue(first.applied());Assert.assertFalse(f.idempotent("accept-once","30").applied());
            Assert.assertTrue(f.idempotent("accept-new","30").applied());Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);Assert.assertEquals(f.indexEvents,1);
            String before=f.snapshot();try{f.idempotent("other-candidate","40");Assert.fail();}catch(CommentAcceptMgmtService.Rejected rejected){Assert.assertEquals(rejected.code,"already_accepted");}
            Assert.assertEquals(f.snapshot(),before);f.exec("DELETE FROM comment WHERE oId='30'");Assert.assertEquals(f.idempotent("accept-once","30").result().toString(),first.result().toString());
        }
        try(var f=new Acceptance()){
            String before=f.snapshot();f.failResults=true;try{f.idempotent("persist-failure","30");Assert.fail();}catch(RepositoryException rejected){}
            Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.indexEvents,0);f.failResults=false;Assert.assertTrue(f.idempotent("persist-failure","30").applied());
        }
        System.out.println("PASS real idempotency coordinator replays without payment/events, rejects another candidate, and result persistence failure rolls the acceptance transaction back");
        for (int type : List.of(0, 1, 2, 3, 6)) {
            for (boolean anonymous : List.of(false, true)) {
                try (var f = new Acceptance()) {
                    f.exec("UPDATE article SET articleType=" + type + " WHERE oId='10'");
                    if (anonymous) f.exec("UPDATE comment SET commentAnonymous=1 WHERE oId='30'");
                    String before = f.snapshot();
                    try { f.v3Idempotent("invalid-type", "30"); Assert.fail(); }
                    catch (ApiV3Exception rejected) { Assert.assertEquals(rejected.status, 422); }
                    Assert.assertEquals(f.snapshot(), before);
                    Assert.assertEquals(f.indexEvents, 0);
                }
            }
        }
        System.out.println("PASS v3 rejects every non-question type for public and anonymous answers without payment, flags, notification or receipt");
        for (int offer : List.of(-1, 0, 19)) {
            for (boolean anonymous : List.of(false, true)) {
                try (var f = new Acceptance()) {
                    f.exec("UPDATE article SET articleQnAOfferPoint=" + offer + " WHERE oId='10'");
                    if (anonymous) f.exec("UPDATE comment SET commentAnonymous=1 WHERE oId='30'");
                    String before = f.snapshot();
                    try { f.v3Idempotent("invalid-offer", "30"); Assert.fail(); }
                    catch (ApiV3Exception rejected) { Assert.assertEquals(rejected.status, 422); }
                    Assert.assertEquals(f.snapshot(), before);
                }
            }
        }
        System.out.println("PASS v3 rejects subminimum offers before public or anonymous settlement and rolls back the whole mutation");
        for (boolean anonymous : List.of(false, true)) {
            try (var f = new Acceptance()) {
                f.exec("UPDATE article SET articleQnAOfferPoint=20 WHERE oId='10'");
                if (anonymous) f.exec("UPDATE comment SET commentAnonymous=1 WHERE oId='30'");
                var first = f.v3Idempotent("valid-v3", "30");
                Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='older'"), anonymous ? 900L : 880L);
                String before = f.snapshot();
                Assert.assertEquals(f.v3Idempotent("valid-v3", "30").result().toString(), first.result().toString());
                Assert.assertEquals(f.snapshot(), before);
                f.exec("UPDATE article SET articleType=0,articleQnAOfferPoint=0 WHERE oId='10'");
                before = f.snapshot();
                Assert.assertFalse(f.v3Idempotent("valid-v3", "30").applied());
                Assert.assertEquals(f.snapshot(), before);
                try { f.v3Idempotent("fresh-invalid", "30"); Assert.fail(); }
                catch (ApiV3Exception rejected) { Assert.assertEquals(rejected.status, 422); }
                Assert.assertEquals(f.snapshot(), before);
            }
        }
        System.out.println("PASS v3 minimum offer succeeds, anonymous keeps no-transfer rule, successful receipt replays after type changes but fresh key rechecks current eligibility");
    }
    static class Acceptance extends CommentThankServiceTest.Thanks {
        final CommentAcceptMgmtService acceptance=new CommentAcceptMgmtService();CommentAcceptMgmtService.Result pending;
        Acceptance()throws Exception {
            exec("ALTER TABLE article ADD COLUMN articleQnAOfferPoint INT DEFAULT 100");
            exec("ALTER TABLE article ADD COLUMN articleType INT DEFAULT 5");
            exec("INSERT INTO comment(oId,commentAuthorId,commentOnArticleId,commentOriginalCommentId,commentContent) VALUES('40','owner','10','','other candidate')");
            for(Object bean:List.of(new CommentRepository(),new ArticleRepository(),new UserRepository(),new OptionRepository(),new RewardRepository(),new PointtransferMgmtService(),new NotificationMgmtService(),new LivenessMgmtService(),new ArticleMgmtService(),thanks)){
                String field=switch(bean.getClass().getSimpleName()){case "CommentRepository"->"comments";case "ArticleRepository"->"articles";case "UserRepository"->"users";case "OptionRepository"->"options";case "RewardRepository"->"rewards";case "PointtransferMgmtService"->"points";case "NotificationMgmtService"->"notifications";case "LivenessMgmtService"->"liveness";case "ArticleMgmtService"->"articleWrites";default->"targets";};
                var target=CommentAcceptMgmtService.class.getDeclaredField(field);target.setAccessible(true);target.set(acceptance,bean);
            }
        }
        CommentAcceptMgmtService.Result acceptanceCore(CommentThankMgmtService.Target expected,CommentThankMgmtService.TargetCheck check)throws Exception {
            return new ArticleMgmtService().withPublicationLocks("older",List.of(Pointtransfer.ID_C_SYS,expected.author()),()->{
                var tx=new CommentRepository().beginTransaction();try{var result=acceptance.acceptInCurrentTransaction("older",expected,check);tx.commit();return result;}finally{if(tx.isActive())tx.rollback();}
            },ignored->{});
        }
        ApiV3IdempotencyMgmtService.Execution idempotent(String key,String id)throws Exception {
            return idempotent(key,id,false);
        }
        ApiV3IdempotencyMgmtService.Execution v3Idempotent(String key,String id)throws Exception {
            return idempotent(key,id,true);
        }
        ApiV3IdempotencyMgmtService.Execution idempotent(String key,String id,boolean v3)throws Exception {
            var expected=thanks.target(id);
            return new ArticleMgmtService().withPublicationLocks("older",null==expected?List.of(Pointtransfer.ID_C_SYS):List.of(Pointtransfer.ID_C_SYS,expected.author()),()->
                ApiV3IdempotencyMgmtService.runAtomicWithOutcome(requestStore(),"older","comments.accept:"+id,key,new JSONObject(),()->{
                    if(null==expected)throw new CommentAcceptMgmtService.Rejected("not_found");
                    pending=acceptance.acceptInCurrentTransaction("older",expected,(a,b,c)->{if(v3)CommentAcceptMgmtService.requireV3Question(b);});
                    if(!id.equals(pending.acceptedId()))throw new CommentAcceptMgmtService.Rejected("already_accepted");
                    return new JSONObject().put("id",id).put("accepted",true);
                }),execution->{if(execution.applied())acceptance.afterAccepted(pending);});
        }
    }
}
