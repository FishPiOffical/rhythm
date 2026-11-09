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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3IdentityImage;
import org.json.JSONObject;
import org.testng.Assert;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 使用认证申请的实际 MySQL DDL 和生产服务，基础账号及回执存储由夹具适配。 */
public class ApiV3IdentityApplicationMysqlTest extends ApiV3IdentityApplicationTest {
    static class Mysql extends Fixture {
        boolean hold;String release;
        Mysql()throws Exception {super();System.setProperty("rhythm.mysql.fixtureSetup","true");}
        @Override Transaction begin(){
            final Transaction result=super.begin();if(!hold)return result;final Connection previous=writer;
            writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
                try{
                    if(m.getName().equals("commit")){
                        System.out.println("BEFORE_COMMIT");System.out.flush();long deadline=System.currentTimeMillis()+20000;
                        while(!Files.exists(Path.of(release))){if(System.currentTimeMillis()>deadline)throw new SQLException("fixture release timeout");Thread.sleep(20);}
                    }
                    return m.invoke(previous,a);
                }catch(InvocationTargetException error){throw error.getCause();}
            });
            try{final var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,writer);}
            catch(Exception error){throw new IllegalStateException(error);}return result;
        }
    }
    public static void main(String[] args) throws Exception {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        System.setProperty("rhythm.mysql.fixtureSetup",Boolean.toString(args[0].equals("setup")));
        try(var f=new Mysql()) {
            final var environment=rows(f.db,"SELECT VERSION() AS version,@@transaction_isolation AS isolation,@@sql_mode AS mode").getFirst();
            Assert.assertTrue(environment.getString("version").startsWith("8.4."));
            Assert.assertEquals(environment.getString("isolation"), "REPEATABLE-READ");
            Assert.assertTrue(environment.getString("mode").contains("STRICT_TRANS_TABLES"));
            Assert.assertEquals(rows(f.db,"SELECT DATA_TYPE AS kind FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='identity_material' AND COLUMN_NAME='data'").getFirst().getString("kind"),"longtext");
            final String race=args[1];
            switch(args[0]) {
                case "setup" -> {
                    if(race.equals("open")||race.startsWith("decision")){
                        final String id=f.submit(image("png"),"seed").getString("id");
                        if(race.startsWith("decision"))f.service.open(reviewer(),id);
                    }
                    System.out.println("SETUP_READY "+race);
                }
                case "worker" -> {
                    f.hold = args[2].equals("holder");
                    f.release = args[3];
                    System.out.println("STARTING_WORK");
                    System.out.flush();
                    try {
                        if(race.startsWith("decision")){
                            final String id=rows(f.db,"SELECT oId FROM identity_app").getFirst().getString("oId");
                            final String status=race.equals("decision-same")||args[2].equals("holder")?"approved":"rejected";
                            final var decision=f.service.decide(reviewer(),id,new JSONObject().put("status",status),race.equals("decision-same")?"review-shared":args[2]);
                            System.out.println("RESULT="+decision.put("decision",decision.getString("status")).put("status",200));
                        }else if(race.equals("open")){
                            final String id=rows(f.db,"SELECT oId FROM identity_app").getFirst().getString("oId");
                            final var opened=f.service.open(reviewer(),id);
                            Assert.assertEquals(opened.bytes(),image("png"));
                            System.out.println("RESULT="+new JSONObject().put("status",200).put("sha256",ApiV3IdentityImage.hash(opened.bytes())));
                        } else {
                            final var result=f.submit(image("png"),race.equals("same-key")?"shared":args[2]);
                            System.out.println("RESULT="+result.put("status",200));
                        }
                    } catch (ApiV3Exception error) {
                        System.out.println("RESULT=" + new JSONObject().put("status", error.status));
                    }
                }
                case "verify" -> {
                    // 独立读连接核对竞态最终只留下一个申请及相符的材料状态。
                    Assert.assertEquals(f.count("identity_app"), 1);
                    Assert.assertEquals(f.count("receipt"), race.startsWith("decision") ? 2 : 1);
                    final var app = rows(f.db, "SELECT status,materialReadAt,pendingOwnerId FROM identity_app").getFirst();
                    if (race.startsWith("decision")) {
                        Assert.assertEquals(app.getString("status"), "approved");
                        Assert.assertTrue(app.isNull("pendingOwnerId"));
                    } else {
                        Assert.assertEquals(app.getString("status"), "pending");
                        Assert.assertEquals(app.getString("pendingOwnerId"), "1");
                    }
                    if (race.equals("open") || race.startsWith("decision")) {
                        Assert.assertEquals(f.count("identity_material"), 0);
                        Assert.assertEquals(app.getLong("materialReadAt"), NOW);
                    } else {
                        Assert.assertEquals(f.count("identity_material"), 1);
                        Assert.assertEquals(app.getLong("materialReadAt"), 0L);
                    }
                    System.out.println("PASS identity mysql committed "+race+" one application/receipt and exact private-material state from independent JDBC connection");
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }
}
