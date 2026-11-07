#!/usr/bin/env node
/** Real daily/patch/name/bag/idempotency services with SQLite repository, points and cache adapters; no HTTP/MySQL runtime. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const testng=process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,sqlite=process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
const latke='src/main/resources/lib/latke-core.jar',suffix=process.platform==='win32'?'.exe':'',java=path.join(home,'bin',`java${suffix}`),javac=path.join(home,'bin',`javac${suffix}`);
for(const file of [java,javac,testng,sqlite,latke])if(!fs.existsSync(file))throw new Error('Configure Java 25, TestNG and SQLite paths');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-checkin-test-')),sources=[];
function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw new Error('Checkin service tests failed');}
function shim(name,source){const file=path.join(work,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,source);sources.push(file);}
try{
 const base='org.b3log.symphony.service.CheckinMgmtServiceTest';
 shim('org/b3log/symphony/repository/UserRepository.java',`package org.b3log.symphony.repository; import org.json.JSONObject; import org.b3log.latke.repository.RepositoryException;
 public class UserRepository extends ${base}.Rows {
 public UserRepository(){super("user");}public JSONObject getForUpdate(String id)throws RepositoryException{return first(select("SELECT * FROM user WHERE oId=? FOR UPDATE",id));}
 public void updateColumnsInCurrentTransaction(String id,JSONObject values)throws RepositoryException{update(id,values,values.keySet().toArray(new String[0]));}
 public void refreshSettingsCache(String actor)throws RepositoryException{${base}.refresh(actor);}
 public void evictSettingsCaches(String actor){${base}.evict(actor);}
 public void evictUserNameCaches(String actor,String oldName){${base}.evictName(actor,oldName);}
 }`);
 shim('org/b3log/symphony/repository/PointtransferRepository.java',`package org.b3log.symphony.repository; public class PointtransferRepository extends ${base}.Rows {public PointtransferRepository(){super("pointtransfer");}}`);
 shim('org/b3log/symphony/repository/OptionRepository.java',`package org.b3log.symphony.repository; import org.json.JSONObject; import org.b3log.latke.repository.RepositoryException; public class OptionRepository extends ${base}.Rows {public OptionRepository(){super("option");} public JSONObject getForUpdate(String id)throws RepositoryException{if(!hasTransactionBegun())throw new IllegalStateException();return first(select("SELECT * FROM option WHERE oId=? FOR UPDATE",id));}}`);
 shim('org/b3log/symphony/service/PointtransferMgmtService.java',`package org.b3log.symphony.service; public class PointtransferMgmtService extends ${base}.Payments {}`);
 const fields=['ID_C_SYS','TRANSFER_TYPE_C_ACTIVITY_CHECKIN','TRANSFER_TYPE_C_ACTIVITY_CHECKIN_STREAK','TRANSFER_SUM_C_ACTIVITY_CHECKIN_MIN','TRANSFER_SUM_C_ACTIVITY_CHECKIN_MAX','TRANSFER_SUM_C_ACTIVITY_CHECKINT_STREAK'];
 const pointSource=fs.readFileSync('src/main/java/org/b3log/symphony/model/Pointtransfer.java','utf8');
 const constants=fields.map(name=>{const m=pointSource.match(new RegExp(`public static final (String|int)\\s+${name}\\s*=\\s*("[^"]*"|[0-9]+|Symphonys\\.[A-Za-z0-9_]+);`));if(!m)throw new Error(name);return m[0];});
 shim('org/b3log/symphony/model/Pointtransfer.java',`package org.b3log.symphony.model; import org.b3log.symphony.util.Symphonys; public class Pointtransfer {${constants.join('\n')}}`);
 shim('org/b3log/symphony/util/Symphonys.java',`package org.b3log.symphony.util; public class Symphonys {
 public static final String[] RESERVED_USER_NAMES;static final java.util.Properties props=new java.util.Properties();
 static{try(var in=new java.io.FileInputStream("src/main/resources/symphony.properties")){props.load(in);}catch(Exception e){throw new RuntimeException(e);}}
 static{RESERVED_USER_NAMES=java.util.Arrays.stream(props.getProperty("reservedUserNames","").split(",")).map(String::trim).filter(v->!v.isEmpty()).toArray(String[]::new);}
 public static final int POINT_ACTIVITY_CHECKIN_MIN=Integer.parseInt(props.getProperty("pointActivityCheckinMin")),POINT_ACTIVITY_CHECKIN_MAX=Integer.parseInt(props.getProperty("pointActivityCheckinMax")),POINT_ACTIVITY_CHECKINT_STREAK=Integer.parseInt(props.getProperty("pointActivityCheckinStreak"));}`);
 const real=['model/Option','util/UserNameRules','service/UserNameMgmtService','model/UserExt','model/PointtransferSource','model/ApiV3Request','repository/CloudRepository','repository/ApiV3RequestRepository','util/BagMutation','util/CheckinPatchMutation','util/DailyCheckinMutation','util/AccountPairLocks','util/ArticlePublicationCaches','util/ApiV3Exception','util/ApiV3Json','util/ApiV3Transactions', 'util/ApiV3TokenUtil','util/ApiV3MutationStore','service/BagMgmtService','service/CheckinMgmtService','service/ApiV3BagMgmtService','service/ApiV3IdempotencyMgmtService'];
 const cp=[latke,testng,sqlite].join(path.delimiter),classes=path.join(work,'classes');
 run(javac,['--release','25','-proc:none','-cp',cp,'-d',classes,...sources,...real.map(n=>`src/main/java/org/b3log/symphony/${n}.java`),'src/test/java/org/b3log/symphony/service/CheckinMgmtServiceTest.java','src/test/java/org/b3log/symphony/util/CheckinPatchMutationTest.java','src/test/java/org/b3log/symphony/service/UserNameMgmtServiceTest.java']);
 for(const name of ['org.b3log.symphony.service.CheckinMgmtServiceTest','org.b3log.symphony.util.CheckinPatchMutationTest','org.b3log.symphony.service.UserNameMgmtServiceTest'])run(java,['-cp',[classes,cp].join(path.delimiter),name]);
}finally{fs.rmSync(work,{recursive:true,force:true});}
