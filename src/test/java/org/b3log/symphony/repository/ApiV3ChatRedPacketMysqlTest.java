package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.service.ApiV3ChatRedPacketService;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** One JVM per contender. MySQL fixture adaptations are explicit temporary sources in the runner. */
public final class ApiV3ChatRedPacketMysqlTest {
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        final boolean setup=args[0].equals("setup");System.setProperty("rhythm.mysql.fixtureSetup",String.valueOf(setup));
        try(var f=new ApiV3ChatRedPacketTest.F()){
            System.setProperty("rhythm.mysql.fixtureSetup","true");
            if(setup){
                var body=args.length>1&&args[1].equals("rps")?new JSONObject().put("type","rockPaperScissors").put("money",256).put("count",1).put("gesture",0).put("riskAccepted",true):ApiV3ChatRedPacketTest.average().put("count",1);f.create(body);
                System.out.println("SETUP_READY");return;
            }
            if(args[0].equals("verify")){
                var users=query(f.reader,"SELECT oId,userPoint FROM user ORDER BY oId");var result=new JSONObject();for(var user:users)result.put(user.getString("oId"),user.getLong("userPoint"));
                result.put("got",query(f.reader,"SELECT got FROM packets").getFirst().getInt("got"));result.put("transfers",f.count("transfers")).put("receipts",f.count("receipts")).put("events",f.count("event"));
                System.out.println("VERIFY="+result);return;
            }
            final String actor=args[1],role=args[2];final Path release=Path.of(args[3]);
            if(role.equals("holder-fails"))f.faults.add("slots.after");
            if(role.startsWith("holder")){
                var hold=new ChatRedPacketRepository(){
                    boolean once;
                    public String getName(){return "packets";}public boolean hasTransactionBegun(){return f.active();}
                    public List<JSONObject> select(String sql,Object...values)throws RepositoryException{return query(f.writer,sql,values);}
                    public void update(String id,JSONObject row,String...fields)throws RepositoryException{f.packets.update(id,row,fields);}
                    public JSONObject current(String id,boolean lock)throws RepositoryException {
                        final JSONObject row=super.current(id,lock);
                        if(lock&&!once){once=true;System.out.println("HEAD_HELD");System.out.flush();long deadline=System.nanoTime()+20_000_000_000L;
                            while(!Files.exists(release)){if(System.nanoTime()>deadline)throw new RepositoryException("verification holder timeout");try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RepositoryException(e);}}}
                        return row;
                    }
                };
                inject(f.service,ApiV3ChatRedPacketService.class,"packets",hold);
            }else{
                var field=ApiV3ChatRedPacketService.class.getDeclaredField("users");field.setAccessible(true);final var original=(UserRepository)field.get(f.service);
                var observed=new UserRepository(){
                    public JSONObject getForUpdate(String id)throws RepositoryException{System.out.println("BEFORE_USER_LOCK");System.out.flush();return original.getForUpdate(id);}
                    public List<JSONObject> select(String sql,Object...values)throws RepositoryException{return original.select(sql,values);}
                    public void refreshSettingsCache(String id)throws RepositoryException{original.refreshSettingsCache(id);}
                    public void evictSettingsCaches(String id){original.evictSettingsCaches(id);}
                };
                inject(f.service,ApiV3ChatRedPacketService.class,"users",observed);
            }
            int status=200;JSONObject result;
            try{result=args.length>4?f.service.claim(f.viewer(actor),"123",new JSONObject().put("riskAccepted",true).put("gesture",Integer.parseInt(args[4])),role+"-"+actor):f.service.claim(f.viewer(actor),"123",role+"-"+actor);}catch(ApiV3Exception error){status=error.status;result=new JSONObject().put("error",error.code);}
            System.out.println("RESULT="+new JSONObject().put("status",status).put("result",result));
        }
    }
}
