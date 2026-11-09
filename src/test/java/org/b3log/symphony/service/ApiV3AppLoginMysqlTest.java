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
import org.b3log.symphony.util.ApiV3AppLoginProtocol;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 使用生产二维码 DDL 和实际签发、凭据重置操作，辅助账号 DAO 适配测试专用表。 */
public class ApiV3AppLoginMysqlTest extends ApiV3AppLoginServiceTest {
    static class Mysql extends Fixture {
        boolean hold;String release;String faultStage="";int tokenWrites;
        Mysql()throws Exception {super();realGrant=true;System.setProperty("rhythm.mysql.fixtureSetup","true");}
        @Override Transaction begin() {
            final Transaction result=super.begin();if(!hold&&faultStage.isEmpty())return result;final Connection previous=writer;
            writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                try {
                    if(hold&&method.getName().equals("commit")) {
                        System.out.println("BEFORE_COMMIT");System.out.flush();final long deadline=System.currentTimeMillis()+20_000;
                        while(!Files.exists(Path.of(release))){if(System.currentTimeMillis()>deadline)throw new SQLException("test release timeout");Thread.sleep(20);}
                    }
                    final Object resultValue=method.invoke(previous,args);
                    if(method.getName().equals("prepareStatement")&&!faultStage.isEmpty()){
                        final String query=(String)args[0];final PreparedStatement statement=(PreparedStatement)resultValue;
                        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
                            try{
                                final Object value=m.invoke(statement,a);
                                if(m.getName().equals("executeUpdate")){
                                    if(query.startsWith("INSERT INTO tokens(")&&query.contains("tokenHash")){
                                        tokenWrites++;
                                        if(faultStage.equals("token-first")&&tokenWrites==1||faultStage.equals("token-second")&&tokenWrites==2)throw new SQLException("test native token write failure");
                                    }
                                    if(faultStage.equals("consume")&&query.startsWith("UPDATE `qr` SET state="))throw new SQLException("test consume write failure");
                                }
                                return value;
                            }catch(InvocationTargetException error){throw error.getCause();}
                        });
                    }
                    return resultValue;
                }catch(InvocationTargetException error){throw error.getCause();}
            });
            try{final var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,writer);}
            catch(Exception error){throw new IllegalStateException(error);}return result;
        }
    }
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        System.setProperty("rhythm.mysql.fixtureSetup",Boolean.toString(args[0].equals("setup")||args[0].equals("fault")));
        try(var f=new Mysql()) {
            final var environment=rows(f.db,"SELECT VERSION() AS version,@@transaction_isolation AS isolation,@@sql_mode AS mode").getFirst();
            Assert.assertTrue(environment.getString("version").startsWith("8.4."));
            Assert.assertEquals(environment.getString("isolation"), "REPEATABLE-READ");
            Assert.assertTrue(environment.getString("mode").contains("STRICT_TRANS_TABLES"));
            Assert.assertEquals(rows(f.db,"SELECT TABLE_COLLATION AS kind FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='qr'").getFirst().getString("kind"),"utf8mb4_bin");
            final String race=args[1];
            switch(args[0]) {
                case "setup" -> {
                    if(race.startsWith("claim"))f.create();else f.approved();
                    System.out.println("SETUP_READY "+race);
                }
                case "fault" -> {
                    f.approved();
                    if(race.equals("commit")||race.equals("ack"))f.fault=race;else f.faultStage=race;
                    error(503, () -> f.service.exchange(RAW, PROOF));
                    f.fault = "";
                    f.faultStage = "";
                    if(race.equals("ack")) {
                        Assert.assertEquals(f.row().getString("state"), "CONSUMED");
                        Assert.assertEquals(f.pairs(), 2);
                        error(410,()->f.service.exchange(RAW,PROOF));
                    }else{
                        Assert.assertEquals(f.row().getString("state"), "WEB_APPROVED");
                        Assert.assertEquals(f.pairs(), 0);
                        f.service.exchange(RAW, PROOF);
                        Assert.assertEquals(f.pairs(), 2);
                        Assert.assertEquals(f.row().getString("state"), "CONSUMED");
                    }
                    final var pair = rows(f.db, "SELECT familyId,tokenHash FROM tokens WHERE clientId='fishpi_app_v3'");
                    Assert.assertEquals(pair.size(), 2);
                    Assert.assertEquals(pair.get(0).getString("familyId"), pair.get(1).getString("familyId"));
                    Assert.assertNotEquals(pair.get(0).getString("tokenHash"), pair.get(1).getString("tokenHash"));
                    System.out.println("PASS app login mysql actual production native-pair "+race+" failure/commit recovery preserves exact terminal state and one persisted family");
                }
                case "worker" -> {
                    final boolean holder = args[2].equals("holder");
                    f.hold = holder;
                    f.release = args[3];
                    System.out.println("STARTING_WORK");System.out.flush();
                    try {
                        if(race.startsWith("claim"))f.service.claim(RAW,ApiV3AppLoginProtocol.challenge(race.equals("claim-same")||holder?PROOF:RAW));
                        else if((race.equals("deny-first")&&holder)||(race.equals("consume-first")&&!holder))f.service.webDecision(identity(),RAW,"deny");
                        else if((race.equals("reset-first")&&holder)||(race.equals("consume-reset")&&!holder))f.settings.password(identity(),body(),"fixture-ip");
                        else f.service.exchange(RAW,PROOF);
                        // 即使使用隔离数据库，也禁止输出签发的访问令牌与刷新令牌。
                        System.out.println("RESULT="+new JSONObject().put("status",200));
                    }catch(ApiV3Exception error){System.out.println("RESULT="+new JSONObject().put("status",error.status));}
                }
                case "verify" -> {
                    Assert.assertEquals(rows(f.db,"SELECT COUNT(*) AS n FROM qr").getFirst().getInt("n"),1);
                    final String state=f.row().getString("state");
                    if(race.startsWith("claim")) {
                        Assert.assertEquals(state, "CLAIMED");
                        Assert.assertEquals(f.row().getString("codeChallenge"), ApiV3AppLoginProtocol.challenge(PROOF));
                        Assert.assertEquals(f.pairs(), 0);
                    } else if (race.equals("deny-first")) {
                        Assert.assertEquals(state, "DENIED");
                        Assert.assertEquals(f.pairs(), 0);
                    } else if (race.equals("reset-first")) {
                        Assert.assertEquals(state, "WEB_APPROVED");
                        Assert.assertEquals(f.pairs(), 0);
                    }
                    else {
                        Assert.assertEquals(state, "CONSUMED");
                        Assert.assertEquals(f.pairs(), 2);
                        final var pair=rows(f.db,"SELECT tokenType,familyId,tokenHash,scope FROM tokens WHERE clientId='fishpi_app_v3' ORDER BY tokenType");
                        Assert.assertEquals(pair.get(0).getString("tokenType"), "access");
                        Assert.assertEquals(pair.get(1).getString("tokenType"), "refresh");
                        Assert.assertEquals(pair.get(0).getString("familyId"), pair.get(1).getString("familyId"));
                        Assert.assertNotEquals(pair.get(0).getString("tokenHash"), pair.get(1).getString("tokenHash"));
                    }
                    if(race.equals("reset-first")||race.equals("consume-reset")) {
                        Assert.assertEquals(rows(f.db,"SELECT credentialEpoch FROM states WHERE oId='1'").getFirst().getLong("credentialEpoch"),1L);
                        Assert.assertEquals(rows(f.db,"SELECT COUNT(*) AS n FROM tokens WHERE userId='1' AND state=0").getFirst().getInt("n"),0);
                    }
                    System.out.println("PASS app login mysql committed "+race+" current state, exact native family/pair count and reset revocation from independent JDBC observer");
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }
}
