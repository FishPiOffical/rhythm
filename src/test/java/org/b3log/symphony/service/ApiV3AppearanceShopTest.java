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
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.b3log.symphony.model.PointtransferSource;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import java.util.concurrent.Callable;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 隔离 SQLite 覆盖真实事务；积分适配器只模拟余额和流水落库，不访问真实账号。 */
public class ApiV3AppearanceShopTest {
    static class Fixture extends ApiV3ChatAppearanceTest.Fixture {
        long clock = NOW;
        boolean failIndex;
        final ApiV3AppearanceShopService shop = new ApiV3AppearanceShopService() {
            protected long now() { return clock; }
        };
        Fixture() throws Exception {
            sql(db, "ALTER TABLE u ADD COLUMN userPoint INTEGER DEFAULT 10000");
            sql(db, "CREATE TABLE product(oId TEXT PRIMARY KEY,price7 INT,price30 INT,enabled INT,revision BIGINT)");
            sql(db, "CREATE TABLE entitlement(oId TEXT PRIMARY KEY,userId TEXT,productId TEXT,expiresAt BIGINT,equipped INT,updatedAt BIGINT,UNIQUE(userId,productId))");
            sql(db, "CREATE TABLE purchase(oId TEXT PRIMARY KEY,userId TEXT,productId TEXT,days INT,paidPoints INT,expiresAt BIGINT,transferId TEXT,createdAt BIGINT)");
            sql(db, "CREATE TABLE transfers(oId TEXT PRIMARY KEY,paid INT)");
            sql(db, "CREATE TABLE transfer_index(oId TEXT PRIMARY KEY,userId TEXT,rawId TEXT)");
            for (var item : ApiV3AppearanceCatalog.PRODUCTS) sql(db, "INSERT INTO product VALUES(?,1024,3584,1,1)", item.id());
            inject(shop, "products", new AppearanceProductRepository() {
                public String getName() { return "product"; }
                public List<JSONObject> select(String q, Object... a) throws RepositoryException { return selectSQL(q,a); }
            });
            inject(shop, "entitlements", new AppearanceEntitlementRepository() {
                public String getName() { return "entitlement"; }
                public List<JSONObject> select(String q, Object... a) throws RepositoryException { return selectSQL(q,a); }
                public String add(JSONObject row) throws RepositoryException { return insert("entitlement",row); }
                public void update(String id, JSONObject row, String... fields) throws RepositoryException { modify("entitlement",id,row,fields); }
            });
            inject(shop, "purchases", new AppearancePurchaseRepository() {
                public String add(JSONObject row) throws RepositoryException { return insert("purchase",row); }
            });
            inject(shop, "transferIndex", new PointtransferRedcRepository() {
                public String add(JSONObject row) throws RepositoryException {
                    if (failIndex) throw new RepositoryException("索引故障");
                    return insert("transfer_index",row);
                }
            });
            inject(shop, "users", new UserRepository() {
                public String getName() { return "u"; }
                public List<JSONObject> select(String q, Object... a) throws RepositoryException { return selectSQL(q,a); }
            });
            inject(shop, "points", new PointtransferMgmtService() {
                public <T> T executeWithTransferLocks(String actor,List<String> destinations,Callable<T> call) throws Exception { return call.call(); }
                public String transferInCurrentTransaction(String from,String to,int type,int sum,String data,long time,String memo,PointtransferSource source,boolean async) {
                    Assert.assertTrue(active()); Assert.assertFalse(async);
                    try {
                        sql(writer,"UPDATE u SET userPoint=userPoint-? WHERE oId=?",sum,from);
                        return insert("transfers",new JSONObject().put("oId",org.b3log.latke.util.Ids.genTimeMillisId()).put("paid",sum));
                    } catch (RepositoryException failure) { throw new IllegalStateException(failure); }
                }
            });
            inject(shop,"requests",ApiV3QqSettingsTest.field(service,ApiV3IdentityApplicationService.class,"requests"));
            inject(shop,"proofs",proof);
            inject(appearance,"shop",shop);
        }
        String insert(String table,JSONObject row) throws RepositoryException {
            Assert.assertTrue(active());
            final List<String> keys = new ArrayList<>(row.keySet());
            sql(writer,"INSERT INTO " + table + "("+String.join(",",keys)+") VALUES("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(row::get).toArray());
            return row.getString("oId");
        }
        void modify(String table,String id,JSONObject row,String... fields) throws RepositoryException {
            Assert.assertTrue(active());
            final List<Object> args = new ArrayList<>();
            for (String field:fields) args.add(row.get(field));
            args.add(id);
            sql(writer,"UPDATE "+table+" SET "+String.join(",",Arrays.stream(fields).map(f->f+"=?").toList())+" WHERE oId=?",args.toArray());
        }
        int balance() throws Exception { return rows(db,"SELECT userPoint FROM u WHERE oId='1'").getFirst().getInt("userPoint"); }
    }
    static JSONObject purchase(String id) { return new JSONObject().put("productId",id).put("days",7).put("revision",1); }
    public static void main(String[] args) throws Exception {
        final Properties props = new Properties(); props.setProperty("runtimeDatabase","NONE"); props.setProperty("contextPath",""); Latkes.setLatkeProps(props); Latkes.setLocalProps(props);
        final String mount="mount-paipai-scooter";
        try(var f=new Fixture()) {
            Assert.assertEquals(f.shop.catalog().getJSONArray("list").length(),9);
            error(403,()->f.shop.equipMount(identity(),mount,"unowned"));
            final JSONObject first=f.shop.buy(identity(),purchase(mount),"buy");
            Assert.assertEquals(f.balance(),8976); Assert.assertEquals(f.count("purchase"),1);
            Assert.assertTrue(first.similar(f.shop.buy(identity(),purchase(mount),"buy")));
            Assert.assertEquals(f.balance(),8976);
            Assert.assertEquals(f.shop.inventory(identity()).getString("mountId"),"");
            f.shop.equipMount(identity(),mount,"equip");
            Assert.assertEquals(f.shop.inventory(identity()).getString("mountId"),"paipai-scooter");
            final JSONObject second=f.shop.buy(identity(),purchase(mount),"renew");
            Assert.assertEquals(second.getLong("expiresAt"),NOW+14*86400000L);
            Assert.assertEquals(f.count("entitlement"),1);
            f.clock=second.getLong("expiresAt");
            Assert.assertEquals(f.shop.inventory(identity()).getString("mountId"),"");
            Assert.assertTrue(f.shop.mounts(List.of("1"),f.clock).isEmpty());
            error(403,()->f.shop.equipMount(identity(),mount,"expired"));
            f.shop.buy(identity(),purchase(mount),"renew-expired");
            sql(f.db,"UPDATE product SET revision=2");
            error(409,()->f.shop.buy(identity(),purchase(mount),"stale"));
            sql(f.db,"UPDATE tokens SET state=1 WHERE oId='100'");
            error(403,()->f.shop.buy(identity(),purchase(mount),"buy"));
        }
        System.out.println("PASS 商城购买、同键恢复、续期、到期、未购买禁止装备、旧报价和凭据撤销");
        try(var f=new Fixture()) {
            f.failIndex=true;
            try { f.shop.buy(identity(),purchase(mount),"rollback"); Assert.fail("故障必须回滚"); } catch(RepositoryException expected) { }
            Assert.assertEquals(f.balance(),10000); Assert.assertEquals(f.count("purchase"),0); Assert.assertEquals(f.count("entitlement"),0); Assert.assertEquals(f.count("receipt"),0); Assert.assertEquals(f.count("transfers"),0);
            f.failIndex=false; f.shop.buy(identity(),purchase(mount),"rollback");
            Assert.assertEquals(f.balance(),8976); Assert.assertEquals(f.count("transfer_index"),2);
            sql(f.db,"UPDATE u SET userPoint=0"); error(409,()->f.shop.buy(identity(),purchase(mount),"poor"));
        }
        System.out.println("PASS 扣款后索引失败整体回滚，原键可恢复且余额不足不写权益");
        try(var f=new Fixture()) {
            error(403,()->f.appearance.update(identity(),ApiV3ChatAppearanceTest.input("sea-soda","none"),"not-owned"));
            f.shop.buy(identity(),purchase("bubble-sea-soda"),"bubble");
            f.appearance.update(identity(),ApiV3ChatAppearanceTest.input("sea-soda","none"),"wear");
            Assert.assertEquals(f.snapshot("10").getJSONObject("appearance").getString("bubbleId"),"sea-soda");
            f.clock=NOW+8*86400000L;
            Assert.assertEquals(f.snapshot("11").getJSONObject("appearance").getString("bubbleId"),"classic");
            Assert.assertEquals(f.projected("10").getJSONObject("appearance").getString("bubbleId"),"sea-soda");
        }
        final JSONObject user=new JSONObject().put("oId","1").put("userName","owner").put("userNickname","昵称").put("userStatus",0).put("userOnlineStatus",0);
        Assert.assertNotNull(ApiV3RealtimeService.chatMountNotice(user,ApiV3AppearanceCatalog.find(mount),NOW));
        user.put("userOnlineStatus",1);
        Assert.assertNotNull(ApiV3RealtimeService.chatMountNotice(user,ApiV3AppearanceCatalog.find(mount),NOW));
        user.remove("userOnlineStatus");
        Assert.assertNotNull(ApiV3RealtimeService.chatMountNotice(user,ApiV3AppearanceCatalog.find(mount),NOW));
        Assert.assertNull(ApiV3RealtimeService.chatMountNotice(user,null,NOW));
        user.put("userStatus",-1);
        Assert.assertNull(ApiV3RealtimeService.chatMountNotice(user,ApiV3AppearanceCatalog.find(mount),NOW));
        System.out.println("PASS 消息外观历史快照和到期回退、座驾独立于在线隐私");
    }
}
