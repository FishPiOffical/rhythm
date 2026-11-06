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
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.model.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import java.nio.file.*;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual pending primitive/defaults/SQL with real repository metadata; SQLite persistence/cache adapters. */
public class AccountPendingRegistrationTest {
    static final String NAME="pending-new",PHONE="13800000001";
    static class Pending extends Fixture {
        int additions,evictions,idSequence=100;final List<String> aliases=new ArrayList<>();final List<String> order=new ArrayList<>();JSONObject inserted;
        final AccountPendingRegistrationService service=new AccountPendingRegistrationService(){@Override protected String newId(){return Integer.toString(idSequence++);}};
        final UserRepository owners;
        Pending()throws Exception {
            final var metadata=new JSONObject(Files.readString(Path.of("src/main/resources/repository.json"))).getJSONArray("repositories");
            JSONObject table=null;for(int i=0;i<metadata.length();i++)if(metadata.getJSONObject(i).getString("name").equals("user"))table=metadata.getJSONObject(i);
            Assert.assertNotNull(table);for(Object value:table.getJSONArray("keys")){
                final var key=(JSONObject)value;final String name=key.getString("name");if(name.equals("oId"))continue;
                exec(writer,"ALTER TABLE user ADD COLUMN `"+name+"` "+(key.getString("type").equals("String")?"TEXT":"BIGINT"));
            }
            exec(writer,"DELETE FROM user");exec(writer,"CREATE TABLE option(oId TEXT PRIMARY KEY,optionValue TEXT)");
            exec(writer,"INSERT INTO option VALUES(?,'0')",Option.ID_C_MISC_ALLOW_REGISTER);exec(writer,"INSERT INTO option VALUES(?,'0')",Option.ID_C_MISC_LANGUAGE);exec(writer,"INSERT INTO option VALUES(?,'10')",Option.ID_C_STATISTIC_MEMBER_COUNT);
            owners=new UserRepository(){
                @Override public String getName(){return "user";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return tx;}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{order.add("users");return query(active()?writer:reader,sql,args);}
                @Override public void updateColumnsInCurrentTransaction(String id,JSONObject row)throws RepositoryException{Assert.assertTrue(active());Assert.assertTrue(ArticlePublicationCaches.active());AccountPendingRegistrationTest.Pending.this.update("user",id,row);fault("user."+id);}
                @Override public String add(JSONObject row)throws RepositoryException{
                    Assert.assertTrue(active());Assert.assertTrue(ArticlePublicationCaches.active());additions++;inserted=new JSONObject(row.toString());
                    final var keys=new ArrayList<>(row.keySet());Collections.sort(keys);final var values=keys.stream().map(row::get).toArray();
                    exec(writer,"INSERT INTO user ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",values);fault("add");return row.getString("oId");
                }
                @Override public void evictUserNameCaches(String id,String name){Assert.assertFalse(active());Assert.assertFalse(ArticlePublicationCaches.active());evictions++;aliases.add(name);}
            };
            final var opts=new OptionRepository(){
                @Override public String getName(){return "option";}
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}
                @Override public JSONObject getForUpdate(String id)throws RepositoryException{Assert.assertTrue(active());order.add(id.equals(Option.ID_C_STATISTIC_MEMBER_COUNT)?"namespace":id.equals(Option.ID_C_MISC_ALLOW_REGISTER)?"gate":"language");final var rows=query(writer,"SELECT * FROM option WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
                @Override public void update(String id,JSONObject changes,String... fields)throws RepositoryException{Assert.assertTrue(active());Assert.assertTrue(ArticlePublicationCaches.active());Assert.assertEquals(fields,new String[]{Option.OPTION_VALUE});AccountPendingRegistrationTest.Pending.this.update("option",id,changes);fault("statistic");}
            };
            final var names=new UserNameMgmtService();inject(names,UserNameMgmtService.class,"users",owners);inject(names,UserNameMgmtService.class,"options",opts);
            inject(service,AccountPendingRegistrationService.class,"users",owners);inject(service,AccountPendingRegistrationService.class,"options",opts);inject(service,AccountPendingRegistrationService.class,"names",names);
        }
        void fault(String key)throws RepositoryException{if(faults.contains(key))throw new RepositoryException("fixture pending mutation failure");}
        void update(String table,String id,JSONObject row)throws RepositoryException{final var keys=new ArrayList<>(row.keySet());Collections.sort(keys);final var values=new ArrayList<Object>();keys.forEach(key->values.add(row.get(key)));values.add(id);exec(writer,"UPDATE "+table+" SET "+String.join(",",keys.stream().map(key->key+"=?").toList())+" WHERE oId=?",values.toArray());}
        void user(String id,String name,String phone,int status,long last)throws Exception {exec(writer,"INSERT INTO user(oId,userName,userPhone,userStatus,userLatestLoginTime,userPoint,userNo,userPassword,userRole,mbti) VALUES(?,?,?,?,?,17,9,'before','defaultRole','existing')",id,name,phone,status,last);}
        AccountPendingRegistrationService.Pending prepare(String name,String phone,long time)throws Exception {
            try(var caches=ArticlePublicationCaches.open(this::active)){
                begin();try{var pending=service.prepareInCurrentTransaction(name,phone,"en_US",time);commit();return pending;}finally{if(tx.isActive())rollback();}
            }
        }
        AccountPendingRegistrationService.Pending prepare()throws Exception{return prepare(NAME,PHONE,1_800_000_000_000L);}
        String all()throws Exception{return query(reader,"SELECT * FROM user ORDER BY oId").toString()+query(reader,"SELECT * FROM option ORDER BY oId");}
    }
    interface Work{void run()throws Exception;}
    static void rejected(Work work,int status)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    public static void main(String[] args)throws Exception {
        final var p=new Properties();p.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
        try(var f=new Pending()){
            var result=f.prepare();Assert.assertTrue(result.created());Assert.assertEquals(result.userId(),"100");Assert.assertEquals(f.order.subList(0,3),List.of("namespace","gate","users"));
            final var row=query(f.reader,"SELECT * FROM user WHERE oId='100'").getFirst();Assert.assertEquals(row.getString("userName"),NAME);Assert.assertEquals(row.getString("userPhone"),PHONE);
            Assert.assertEquals(row.getInt("userStatus"),UserExt.USER_STATUS_C_NOT_VERIFIED);Assert.assertEquals(row.getString("userPassword"),"");Assert.assertEquals(row.getString("userRole"),Role.ROLE_ID_C_DEFAULT);Assert.assertEquals(row.getInt("userNo"),11);Assert.assertEquals(row.getInt("userPoint"),0);Assert.assertEquals(row.getString("userLanguage"),"en_US");Assert.assertEquals(row.getString("userTimezone"),"Asia/Shanghai");Assert.assertFalse(row.getString("userAvatarURL").isEmpty());
            Assert.assertTrue(f.evictions>0);Assert.assertEquals(query(f.reader,"SELECT optionValue FROM option WHERE oId=?",Option.ID_C_STATISTIC_MEMBER_COUNT).getFirst().getString("optionValue"),"11");
        }
        for(int count:List.of(8887,9998))try(var f=new Pending()){
            exec(f.writer,"UPDATE option SET optionValue=? WHERE oId=?",Integer.toString(count),Option.ID_C_STATISTIC_MEMBER_COUNT);f.prepare();Assert.assertEquals(query(f.reader,"SELECT userNo FROM user").getFirst().getInt("userNo"),count+2);
        }
        System.out.println("PASS actual pending primitive inserts a metadata-compatible inactive default-role account, allocates original member numbers including skipped values, updates the statistic in the owner transaction and evicts caches only after it ends");
        try(var f=new Pending()){
            f.user("1","old-pending",PHONE,2,0);String before=query(f.reader,"SELECT userPoint,userPassword,userRole,userNo,mbti FROM user WHERE oId='1'").toString();
            var pending=f.prepare();Assert.assertFalse(pending.created());Assert.assertEquals(pending.userId(),"1");Assert.assertEquals(f.additions,0);Assert.assertEquals(query(f.reader,"SELECT userPoint,userPassword,userRole,userNo,mbti FROM user WHERE oId='1'").toString(),before);Assert.assertEquals(query(f.reader,"SELECT userName FROM user WHERE oId='1'").getFirst().getString("userName"),NAME);Assert.assertEquals(query(f.reader,"SELECT optionValue FROM option WHERE oId=?",Option.ID_C_STATISTIC_MEMBER_COUNT).getFirst().getString("optionValue"),"10");
        }
        try(var f=new Pending()){f.user("2",NAME,"13800000002",2,0);Assert.assertTrue(f.prepare().created());Assert.assertEquals(query(f.reader,"SELECT * FROM user").size(),2);}
        for(int status:List.of(0,1,3,4))try(var f=new Pending()){
            f.user("2",NAME,"13800000002",status,0);String before=f.all();rejected(f::prepare,409);Assert.assertEquals(f.all(),before);
        }
        for(int status:List.of(0,1,3))try(var f=new Pending()){
            f.user("1","occupied-phone",PHONE,status,0);String before=f.all();rejected(f::prepare,409);Assert.assertEquals(f.all(),before);
        }
        try(var f=new Pending()){
            f.user("1","old-pending",PHONE,2,0);
            try(var caches=ArticlePublicationCaches.open(f::active)){
                f.begin();f.service.prepareInCurrentTransaction("first-name",PHONE,"en_US",1_800_000_000_000L);
                f.service.prepareInCurrentTransaction("second-name",PHONE,"en_US",1_800_000_000_000L);Assert.assertTrue(f.aliases.isEmpty());f.commit();
            }
            Assert.assertEquals(new HashSet<>(f.aliases),Set.of("old-pending","first-name","second-name"));
        }
        System.out.println("PASS the same pending phone reuses its ID without resetting points/profile/statistics, other pending names retain the legacy coexistence policy and active/restricted/deactivated name or occupied-phone conflicts preserve all data");
        final Calendar start=Calendar.getInstance();start.clear();start.set(2024,Calendar.JANUARY,31,12,0);final var eligible=(Calendar)start.clone();eligible.add(Calendar.MONTH,1);
        try(var f=new Pending()){f.user("1","deactivated",PHONE,4,start.getTimeInMillis());String before=f.all();rejected(()->f.prepare(NAME,PHONE,eligible.getTimeInMillis()-1),409);Assert.assertEquals(f.all(),before);Assert.assertTrue(f.prepare(NAME,PHONE,eligible.getTimeInMillis()).created());Assert.assertEquals(query(f.reader,"SELECT userPhone FROM user WHERE oId='1'").getFirst().getString("userPhone"),"");}
        try(var f=new Pending()){f.user("1",NAME,PHONE,4,start.getTimeInMillis());String before=f.all();rejected(()->f.prepare(NAME,PHONE,eligible.getTimeInMillis()),409);Assert.assertEquals(f.all(),before);}
        for(long timestamp:List.of(-1L,Long.MAX_VALUE))try(var f=new Pending()){
            f.user("1","deactivated",PHONE,4,timestamp);String before=f.all();
            try{f.prepare();Assert.fail();}catch(ApiV3Exception expected){Assert.assertEquals(expected.status,409);}catch(IllegalStateException expected){}
            Assert.assertEquals(f.all(),before);
        }
        System.out.println("PASS deactivated phone reuse follows the existing calendar-month boundary including leap February, releases only its phone with the new pending insert, and never reclaims an occupied deactivated username");
        for(String fault:List.of("add","statistic","commit"))try(var f=new Pending()){
            f.faults.add(fault);String before=f.all();try{f.prepare();Assert.fail();}catch(Exception expected){}Assert.assertEquals(f.all(),before);Assert.assertFalse(ArticlePublicationCaches.active());Assert.assertTrue(f.evictions>0);
        }
        for(String fault:List.of("user.1","add","statistic","commit"))try(var f=new Pending()){
            f.user("1","deactivated",PHONE,4,0);f.faults.add(fault);String before=f.all();try{f.prepare();Assert.fail();}catch(Exception expected){}Assert.assertEquals(f.all(),before);
        }
        try(var f=new Pending()){f.user("1","old-pending",PHONE,2,0);f.faults.add("user.1");String before=f.all();try{f.prepare();Assert.fail();}catch(Exception expected){}Assert.assertEquals(f.all(),before);}
        System.out.println("PASS phone release/rename/insert/statistic/strict commit faults roll back the whole pending operation, preserve existing balances and release the cache boundary only after rollback");
        for(String mode:List.of("1","bad"))try(var f=new Pending()){
            exec(f.writer,"UPDATE option SET optionValue=? WHERE oId=?",mode,Option.ID_C_MISC_ALLOW_REGISTER);String before=f.all();try{f.prepare();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,403);}catch(IllegalStateException e){Assert.assertEquals(mode,"bad");}Assert.assertEquals(f.all(),before);
        }
        for(String counter:List.of("-1","bad",Integer.toString(Integer.MAX_VALUE)))try(var f=new Pending()){
            exec(f.writer,"UPDATE option SET optionValue=? WHERE oId=?",counter,Option.ID_C_STATISTIC_MEMBER_COUNT);String before=f.all();try{f.prepare();Assert.fail();}catch(Exception expected){}Assert.assertEquals(f.all(),before);
        }
        try(var f=new Pending()){
            try{f.service.prepareInCurrentTransaction(NAME,PHONE,"en_US",1_800_000_000_000L);Assert.fail();}catch(IllegalStateException expected){}
            f.begin();try{f.service.prepareInCurrentTransaction(NAME,PHONE,"en_US",1_800_000_000_000L);Assert.fail();}catch(IllegalStateException expected){}f.rollback();
            rejected(()->f.prepare("bad/name",PHONE,1_800_000_000_000L),422);rejected(()->f.prepare(NAME,"+8613800000001",1_800_000_000_000L),422);
        }
        for(String fault:List.of("add","statistic"))try(var f=new Pending()){
            String before=f.all();f.faults.add(fault);
            try(var caches=ArticlePublicationCaches.open(f::active)){
                f.begin();exec(f.writer,"INSERT INTO unrelated VALUES('kept')");
                try{f.service.prepareInCurrentTransaction(NAME,PHONE,"en_US",1_800_000_000_000L);Assert.fail();}catch(RepositoryException expected){}
                f.faults.clear();f.commit();
            }
            Assert.assertEquals(f.all(),before);Assert.assertEquals(query(f.reader,"SELECT * FROM unrelated").size(),1);
        }
        for(String fault:List.of("release.after","rollback.point"))try(var f=new Pending()){
            String before=f.all();f.faults.add(fault);if(fault.equals("rollback.point"))f.faults.add("add");
            try{f.prepare();Assert.fail();}catch(Exception expected){}Assert.assertEquals(f.all(),before);Assert.assertFalse(f.active());
        }
        System.out.println("PASS a caught pending-account failure cannot leak writes into an owner commit; earlier unrelated work survives local rollback and lost savepoint/rollback acknowledgements force a full rollback instead of partial account creation");
        try(var f=new Pending()){
            String before=f.all();try(var caches=ArticlePublicationCaches.open(f::active)){
                f.begin();for(String invalid:List.of("<script>","a".repeat(17),"en\u0000"))rejected(()->f.service.prepareInCurrentTransaction(NAME,PHONE,invalid,1_800_000_000_000L),422);f.commit();
            }Assert.assertEquals(f.all(),before);
        }
        System.out.println("PASS current registration closure, invalid/corrupt/overflowing statistics, root/cache ownership and bounded name/phone inputs fail before partial writes; this primitive does not expose an HTTP or invitation/captcha bypass");
        final var legacy=new JSONObject().put("userName","legacy-user").put("userPhone",PHONE).put("userEmail"," A@EXAMPLE.TEST ").put("userPassword","legacy-hash").put("userAppRole",1).put("userLanguage","en_US").put("userTimezone","UTC").put("userStatus",0).put("userRole","OP").put("apiKey","must-not-copy").put("secret2fa","must-not-copy").put("userPoint",999999);
        final var defaults=UserRegistrationDefaults.create(legacy,"INFJ-T","0",123456);Assert.assertEquals(defaults.getString("userEmail"),"a@example.test");Assert.assertEquals(defaults.getString("userPassword"),"legacy-hash");Assert.assertEquals(defaults.getInt("userAppRole"),1);Assert.assertEquals(defaults.getString("userRole"),"OP");Assert.assertEquals(defaults.getString("userLanguage"),"en_US");Assert.assertEquals(defaults.getString("userTimezone"),"UTC");Assert.assertEquals(defaults.getString("mbti"),"INFJ-T");Assert.assertEquals(defaults.getInt("userPoint"),0);Assert.assertEquals(defaults.getString("secret2fa"),"");Assert.assertFalse(defaults.has("apiKey"));Assert.assertEquals(UserRegistrationDefaults.create(legacy,"","zh_CN",123456).getString("userLanguage"),"zh_CN");
        final var oldSource=Files.readString(Path.of("src/main/java/org/b3log/symphony/service/UserMgmtService.java"));Assert.assertTrue(oldSource.contains("user = UserRegistrationDefaults.create(requestJSONObject, mbti, optionLanguage.optString(Option.OPTION_VALUE), System.currentTimeMillis());"));
        System.out.println("PASS the legacy addUser now calls the actual shared default builder: requested legacy role/status/password/language overrides remain, private extra fields are not copied and untrusted points/2FA do not become account defaults");
    }
}
