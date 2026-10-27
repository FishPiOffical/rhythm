package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** Independent JVM writers exercise exact production slot DDL, SQL and strict transaction commits. */
public class ApiV3GameSaveMysqlTest extends ApiV3GameSaveTest {
    static class Mysql extends Fixture {
        boolean hold;String release;
        Mysql()throws Exception {super();System.setProperty("rhythm.mysql.fixtureSetup","true");}
        @Override Transaction begin(){
            final Transaction result=super.begin();if(!hold)return result;final Connection previous=writer;
            writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                try{
                    if(method.getName().equals("commit")){
                        System.out.println("BEFORE_COMMIT");System.out.flush();final long deadline=System.currentTimeMillis()+20000;
                        while(!Files.exists(Path.of(release))){if(System.currentTimeMillis()>deadline)throw new SQLException("release timeout");Thread.sleep(20);}
                    }
                    return method.invoke(previous,args);
                }catch(InvocationTargetException error){throw error.getCause();}
            });
            try{final var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,writer);}catch(Exception error){throw new IllegalStateException(error);}return result;
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        System.setProperty("rhythm.mysql.fixtureSetup",Boolean.toString(args[0].equals("setup")));
        try(var f=new Mysql()){
            final var environment=rows(f.db,"SELECT VERSION() AS version,@@transaction_isolation AS isolation,@@sql_mode AS mode").getFirst();
            Assert.assertTrue(environment.getString("version").startsWith("8.4."));Assert.assertEquals(environment.getString("isolation"),"REPEATABLE-READ");Assert.assertTrue(environment.getString("mode").contains("STRICT_TRANS_TABLES"));
            Assert.assertEquals(rows(f.db,"SELECT TABLE_COLLATION AS kind FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='save_slots'").getFirst().getString("kind"),"utf8mb4_bin");
            final String race=args[1];
            switch(args[0]){
                case "setup" -> {
                    if(race.equals("old-first")||race.equals("v3-first"))f.save("seed",0,"seed");
                    if(race.contains("migrate"))f.legacy("11","1",GAME,DATA);
                    System.out.println("SETUP_READY "+race);
                }
                case "worker" -> {
                    final boolean holder=args[2].equals("holder");f.hold=holder;f.release=args[3];System.out.println("STARTING_WORK");System.out.flush();
                    try{
                        if(race.equals("old-first")&&holder||race.equals("v3-first")&&!holder||race.equals("migrate-first")&&!holder||race.equals("old-before-migrate")&&holder)
                            f.service.legacySave("1",f.legacyProof(),GAME,"old");
                        else if(race.equals("migrate-first")&&holder||race.equals("old-before-migrate")&&!holder||race.equals("migrate-create")&&holder)
                            f.service.migrate(identity(),GAME,"migration");
                        else f.save(race.equals("same-key")?DATA:holder?"holder":"contender",race.equals("old-first")||race.equals("v3-first")?1:0,race.equals("same-key")?"shared":holder?"holder":"contender");
                        System.out.println("RESULT="+new JSONObject().put("status",200));
                    }catch(ApiV3Exception error){System.out.println("RESULT="+new JSONObject().put("status",error.status));}
                }
                case "verify" -> {
                    Assert.assertEquals(f.count("save_slots"),1);final var value=f.service.get(identity(),GAME);
                    final long revision=race.equals("v3-first")?3:List.of("old-first","migrate-first").contains(race)?2:1;
                    Assert.assertEquals(value.getLong("revision"),revision);
                    final String data=List.of("old-first","v3-first","migrate-first","old-before-migrate").contains(race)?"old":race.equals("same-key")||race.equals("migrate-create")?DATA:"holder";
                    Assert.assertEquals(value.getString("data"),data);
                    if(race.contains("migrate")){Assert.assertEquals(f.count("legacy_cloud"),1);Assert.assertEquals(rows(f.db,"SELECT data FROM legacy_cloud").getFirst().getString("data"),DATA);}
                    final long receipts=List.of("old-first","migrate-first","same-key","cas-conflict","migrate-create").contains(race)?1:race.equals("v3-first")?2:0;
                    Assert.assertEquals(f.count("receipts"),receipts);System.out.println("PASS game save committed "+race+" exact version/data/receipt count and legacy source preserved");
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }
}
