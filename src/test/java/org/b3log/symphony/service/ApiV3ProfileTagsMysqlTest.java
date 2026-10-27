package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.json.JSONObject;
import org.testng.Assert;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** Independent JVMs share InnoDB; cache, censor, quota and ancillary auth remain explicit fixture ports. */
public class ApiV3ProfileTagsMysqlTest {
    static class Mysql extends ApiV3ProfileTagsTest.Fixture {
        boolean hold; String release;
        Mysql() throws Exception {
            super(); Assert.assertEquals(db.getTransactionIsolation(),Connection.TRANSACTION_REPEATABLE_READ);
            Assert.assertEquals(rows(db,"SELECT ENGINE engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='tag_data'").getFirst().getString("engine"),"InnoDB");
        }
        @Override Transaction begin() {
            final Transaction transaction = super.begin(); if (!hold) return transaction;
            final Connection previous = writer;
            writer = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (proxy, method, args) -> {
                try {
                    if (method.getName().equals("commit")) {
                        System.out.println("BEFORE_COMMIT"); System.out.flush(); final long deadline=System.currentTimeMillis()+20000;
                        while (!Files.exists(Path.of(release))) { if (System.currentTimeMillis()>deadline) throw new SQLException("fixture release timeout"); Thread.sleep(20); }
                    }
                    return method.invoke(previous,args);
                } catch (InvocationTargetException error) { throw error.getCause(); }
            });
            try { final var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,writer); }
            catch (Exception error) { throw new IllegalStateException(error); } return transaction;
        }
    }
    public static void main(String[] args) throws Exception {
        final var properties=new Properties();properties.setProperty("runtimeDatabase","NONE");properties.setProperty("contextPath","");Latkes.setLatkeProps(properties);Latkes.setLocalProps(properties);
        final String action=args[0],race=args[1];System.setProperty("rhythm.mysql.fixtureSetup",String.valueOf(action.equals("setup")));
        if (action.equals("setup")) { try (var f=new Mysql()) { Assert.assertEquals(f.tagCount(),1); } System.out.println("SETUP_OK");return; }
        if (action.equals("worker")) {
            try (var f=new Mysql()) {
                final boolean holder=args[2].equals("holder");f.hold=holder;f.release=args[3];
                final String actor=!holder&&race.equals("shared-tag")?"2":"1";
                final String value=race.equals("shared-tag")?"Shared":holder?"A":race.equals("clear")?"":"B";
                final String key=race.equals("same-key")?"same":holder?"holder":"contender";
                if (!holder&&race.equals("failure")) f.writeFault="UPDATE option_data";
                System.out.println("STARTING_WORK");System.out.flush();
                try {
                    // Same-key contenders must use exactly the holder's original request.
                    final JSONObject result=f.tagSettings.update(actor,new JSONObject().put("tags",race.equals("same-key")?"A":value),key);
                    System.out.println("RESULT="+result.put("status",200));
                } catch (org.b3log.symphony.util.ApiV3Exception error) { System.out.println("RESULT="+new JSONObject().put("status",error.status)); }
            } return;
        }
        if (action.equals("verify")) {
            try (var f=new Mysql()) {
                final boolean same=race.equals("same-key"),shared=race.equals("shared-tag"),clear=race.equals("clear"),failure=race.equals("failure");
                Assert.assertEquals(f.tagCount(),same||shared||clear||failure?2:3);Assert.assertEquals(f.statistic(),f.tagCount());
                Assert.assertEquals(f.count("receipt"),same||failure?1:2);
                Assert.assertEquals(f.self(),clear?0:1);Assert.assertEquals(f.storedTags(),shared?"Shared":clear?"":same||failure?"A":"B");
                if (shared) {
                    Assert.assertEquals(rows(f.db,"SELECT userTags FROM u WHERE oId='2'").getFirst().getString("userTags"),"Shared");
                    Assert.assertEquals(rows(f.db,"SELECT COUNT(*) n FROM tag_data WHERE tagTitle='Shared'").getFirst().getInt("n"),1);
                    Assert.assertEquals(rows(f.db,"SELECT COUNT(*) n FROM user_tag_data WHERE user_oId='2' AND type=2").getFirst().getInt("n"),1);
                    Assert.assertEquals(rows(f.db,"SELECT COUNT(*) n FROM user_tag_data u JOIN tag_data t ON u.tag_oId=t.oId WHERE u.type=2 AND t.tagTitle='Shared' AND u.user_oId IN('1','2')").getFirst().getInt("n"),2);
                }
                Assert.assertEquals(rows(f.db,"SELECT COUNT(*) n FROM user_tag_data u LEFT JOIN tag_data t ON u.tag_oId=t.oId WHERE t.oId IS NULL").getFirst().getInt("n"),0);
            } System.out.println("VERIFY_OK "+race);return;
        }
        throw new IllegalArgumentException("fixture action");
    }
}
