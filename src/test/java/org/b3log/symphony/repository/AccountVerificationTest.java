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
import org.b3log.symphony.model.Verifycode;
import org.b3log.symphony.service.AccountVerificationService;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual bound SQL/consumption core; owner mutation and framework storage explicitly adapted to SQLite. */
public class AccountVerificationTest {
    static class Verification extends Fixture {
        long clock=100_000;int deletes;final List<String> locks=new ArrayList<>();
        final AccountVerificationService verification=new AccountVerificationService(){@Override protected long now(){return clock;}};
        Verification()throws Exception{
            exec(writer,"ALTER TABLE user ADD COLUMN userPhone TEXT");exec(writer,"ALTER TABLE user ADD COLUMN userStatus INTEGER");exec(writer,"ALTER TABLE user ADD COLUMN password TEXT");
            exec(writer,"UPDATE user SET userPhone='13800000001',userStatus=2,password='before' WHERE oId='1'");
            exec(writer,"INSERT INTO user VALUES('2','13800000002',0,'other')");
            exec(writer,"ALTER TABLE user ADD COLUMN userName TEXT");exec(writer,"UPDATE user SET userName='pending-user'");
            exec(writer,"CREATE TABLE verifycode(oId TEXT PRIMARY KEY,userId TEXT,receiver TEXT,type INTEGER,bizType INTEGER,code TEXT,status INTEGER,expired BIGINT,accountContext TEXT DEFAULT '')");
            inject(verification,AccountVerificationService.class,"users",new UserRepository(){
                @Override public String getName(){return "user";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{locks.add("user");return query(writer,sql,args);}
            });
            inject(verification,AccountVerificationService.class,"codes",new VerifycodeRepository(){
                @Override public String getName(){return "verifycode";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{locks.add("codes");return query(writer,sql,args);}
                @Override public void remove(String id)throws RepositoryException{
                    exec(writer,"DELETE FROM verifycode WHERE oId=?",id);deletes++;
                    if(faults.contains("delete."+deletes))throw new RepositoryException("fixture delete failure");
                }
            });
        }
        void code(String id,String user,String phone,int type,int purpose,String value,int status,long expiry)throws Exception{
            exec(writer,"INSERT INTO verifycode(oId,userId,receiver,type,bizType,code,status,expired) VALUES(?,?,?,?,?,?,?,?)",id,user,phone,type,purpose,value,status,expiry);
        }
        void valid(String id)throws Exception{code(id,"1","13800000001",1,0,"123456",0,200_000);}
        String snapshot()throws Exception{return query(reader,"SELECT * FROM user ORDER BY oId").toString()+query(reader,"SELECT * FROM verifycode ORDER BY oId");}
        boolean complete(String user,String code,int purpose)throws Exception{
            begin();try{boolean verified=verification.consumePhoneCodeInCurrentTransaction(user,code,purpose);
                if(verified)exec(writer,"UPDATE user SET password='after' WHERE oId=?",user);commit();return verified;
            }finally{if(active())rollback();}
        }
    }
    public static void main(String[] args)throws Exception{
        var properties=new Properties();properties.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(properties);Latkes.setLocalProps(properties);
        try(var f=new Verification()){
            f.valid("1");f.code("2","1","13800000001",1,0,"123456",1,200_000);
            f.code("3","2","13800000002",1,1,"123456",0,200_000);
            f.code("4","1","13800000001",1,1,"123456",0,200_000);
            f.code("5","1","13800000001",0,0,"123456",0,200_000);
            f.code("6","1","13800000001",1,0,"654321",0,200_000);
            Assert.assertTrue(f.complete("1","123456",0));Assert.assertEquals(f.deletes,2);Assert.assertEquals(f.locks,List.of("user","codes"));
            Assert.assertEquals(query(f.reader,"SELECT oId FROM verifycode ORDER BY oId").stream().map(v->v.getString("oId")).toList(),List.of("3","4","5","6"));
            Assert.assertFalse(f.complete("1","123456",0));Assert.assertTrue(f.complete("2","123456",1));
        }
        System.out.println("PASS actual SQL binds phone code to current user/phone/type/purpose, locks user before codes and consumes all live duplicates once with the owner mutation while leaving other users, purposes, types and codes untouched");
        for(String mutation:List.of("UPDATE user SET userStatus=0 WHERE oId='1'","UPDATE user SET userStatus=1 WHERE oId='1'","UPDATE user SET userPhone='13800000003' WHERE oId='1'","UPDATE user SET userPhone='' WHERE oId='1'"))try(var f=new Verification()){
            f.valid("1");exec(f.writer,mutation);String before=f.snapshot();Assert.assertFalse(f.complete("1","123456",0));Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Verification()){
            f.valid("1");String before=f.snapshot();Assert.assertFalse(f.complete("1","123456",1));Assert.assertFalse(f.complete("999","123456",0));Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS current account status and current phone are re-read under lock: activated/disabled/changed-phone/missing owners cannot consume a registration code and registration proof cannot reset a password");
        for(long expiry:List.of(99_999L,100_000L))try(var f=new Verification()){
            f.code("1","1","13800000001",1,0,"123456",0,expiry);String before=f.snapshot();Assert.assertFalse(f.complete("1","123456",0));Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Verification()){
            f.code("1","1","13800000001",1,0,"123456",5,200_000);String before=f.snapshot();Assert.assertFalse(f.complete("1","123456",0));Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS exact expiry boundary and corrupt verification status fail closed without consuming or mutating an account");
        for(String fault:List.of("delete.1","delete.2","release.after","commit"))try(var f=new Verification()){
            f.valid("1");f.valid("2");String before=f.snapshot();f.faults.add(fault);try{f.complete("1","123456",0);Assert.fail(fault);}catch(Exception expected){}Assert.assertEquals(f.snapshot(),before,fault);
        }
        try(var f=new Verification()){
            f.valid("1");f.valid("2");String before=f.snapshot();f.begin();f.faults.add("delete.2");
            try{f.verification.consumePhoneCodeInCurrentTransaction("1","123456",0);Assert.fail();}catch(RepositoryException expected){}f.faults.clear();f.commit();Assert.assertEquals(f.snapshot(),before);
            f.begin();Assert.assertTrue(f.verification.consumePhoneCodeInCurrentTransaction("1","123456",0));f.rollback();Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS first/second deletion, savepoint release and strict commit faults roll back account plus all consumption; caught local failure and owner rollback preserve the entire verification proof");
        try(var f=new Verification()){
            try{f.verification.consumePhoneCodeInCurrentTransaction("1","123456",0);Assert.fail();}catch(IllegalStateException expected){}
            f.begin();for(String input:List.of("","12345","1234567","１２３４５６","12 456")){
                try{f.verification.consumePhoneCodeInCurrentTransaction("1",input,0);Assert.fail();}catch(IllegalArgumentException expected){}
            }
            try{f.verification.consumePhoneCodeInCurrentTransaction("1","123456",Verifycode.BIZ_TYPE_C_BIND_PHONE);Assert.fail();}catch(IllegalArgumentException expected){}
            f.commit();
        }
        System.out.println("PASS an owner transaction, six ASCII SMS digits and an account registration/reset purpose are mandatory; no HTTP route or attempt-budget bypass is exposed by this primitive");
        for(String change:List.of("name","referral","referralId","invite","invalid","missing-caller"))try(var f=new Verification()){
            f.valid("1");String context=org.b3log.symphony.util.RegistrationProofContext.encode("pending-user","referrer","2","19");
            exec(f.writer,"UPDATE verifycode SET accountContext=?",change.equals("invalid")?"{invalid":context);
            if(change.equals("name"))exec(f.writer,"UPDATE user SET userName='renamed' WHERE oId='1'");
            String before=f.snapshot();f.begin();
            boolean result=change.equals("missing-caller")?f.verification.consumePhoneCodeInCurrentTransaction("1","123456",0):
                    f.verification.consumeRegistrationCodeInCurrentTransaction("1","123456",change.equals("referral")?"different":"referrer",change.equals("referralId")?"3":"2",change.equals("invite")?"20":"19");
            Assert.assertFalse(result);f.commit();Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Verification()){
            f.valid("1");exec(f.writer,"UPDATE verifycode SET accountContext=?",org.b3log.symphony.util.RegistrationProofContext.encode("pending-user","referrer","2","19"));
            f.begin();Assert.assertTrue(f.verification.consumeRegistrationCodeInCurrentTransaction("1","123456","referrer","2","19"));f.rollback();Assert.assertEquals(query(f.reader,"SELECT * FROM verifycode").size(),1);
            f.begin();Assert.assertTrue(f.verification.consumeRegistrationCodeInCurrentTransaction("1","123456","referrer","2","19"));f.commit();Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode").isEmpty());
        }
        System.out.println("PASS new registration proofs bind exact current name, canonical referral and invitation identity; renamed/cross-referral/cross-invite/corrupt proofs and a generic caller cannot consume them, while legacy empty context and owner rollback remain compatible");

        try(var f=new Verification()){
            f.valid("1");f.valid("2");
            exec(f.writer,"UPDATE verifycode SET accountContext=? WHERE oId='1'",org.b3log.symphony.util.RegistrationProofContext.encode("older-name","referrer","2","19"));
            exec(f.writer,"UPDATE verifycode SET accountContext=? WHERE oId='2'",org.b3log.symphony.util.RegistrationProofContext.encode("pending-user","referrer","2","19"));
            String original=f.snapshot();f.begin();Assert.assertTrue(f.verification.consumeRegistrationCodeInCurrentTransaction("1","123456","referrer","2","19"));f.rollback();Assert.assertEquals(f.snapshot(),original);
            f.begin();Assert.assertTrue(f.verification.consumeRegistrationCodeInCurrentTransaction("1","123456","referrer","2","19"));f.commit();Assert.assertTrue(query(f.reader,"SELECT * FROM verifycode").isEmpty());
        }
        System.out.println("PASS equal six-digit values from different registration contexts do not disable a valid current proof; one compatible live proof authorizes the operation and every same-owner/purpose live duplicate is consumed atomically, so an older context cannot be replayed later");

    }
}
