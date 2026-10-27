#!/usr/bin/env node
/** Registered game slots: production service/SQL/strict transactions and real HTTP/legacy middleware; explicit account IO adapters. */
import fs from 'node:fs';import path from 'node:path';import os from 'node:os';import {spawnSync,spawn} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=[path.join(os.homedir(),'.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar'),'src/main/resources/lib/latke-core.jar',`${ide}/lib/intellij.libraries.caffeine.jar`,`${ide}/plugins/testng/lib/testng.jar`,`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-game-save-')),shims=[];
function shim(name,body){const file=path.join(dir,name+'.java');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,body);shims.push(file);}
function method(source,signature){const begin=source.indexOf(signature);if(begin<0)throw Error(signature);let depth=0;for(let i=source.indexOf('{',begin);i<source.length;i++){if(source[i]==='{')depth++;if(source[i]==='}'&&--depth===0)return source.slice(begin,i+1);}throw Error('Unclosed method');}
function run(bin,args){const r=spawnSync(bin,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw Error('Game save tests failed');}
let container;const children=[];
function docker(args){const r=spawnSync('docker',args,{encoding:'utf8',timeout:25000});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim();}
try{
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys {public static final String[] RESERVED_USER_NAMES={};}');
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;public class UserRepository extends org.b3log.latke.repository.AbstractRepository {public UserRepository(){super("user");}public void evictSettingsCaches(String actor){}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService',`package org.b3log.symphony.service;import org.json.JSONObject;import org.apache.commons.lang.StringUtils;import java.util.*;public class ApiV3TokenMgmtService {public static final String WEBSITE_CLIENT="website_v3";public JSONObject authenticate(String value){return null;}${method(tokens,'public static boolean hasScope(')}}`);
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "fixture-security";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);}}');
 shim('org/b3log/symphony/repository/CloudRepository','package org.b3log.symphony.repository;public class CloudRepository extends org.b3log.latke.repository.AbstractRepository {public CloudRepository(){super("cloud");}}');
 shim('org/b3log/symphony/service/CloudService','package org.b3log.symphony.service;public class CloudService {public int calls;public String lastGame;public void sync(String actor,String game,String data){calls++;lastGame=game;}public void sync(String actor,String game,org.json.JSONObject data){calls++;lastGame=game;}public String getFromCloud(String actor,String game){calls++;lastGame=game;return "unchanged-legacy";}}');
 shim('org/b3log/symphony/util/Sessions','package org.b3log.symphony.util;public class Sessions {public static org.json.JSONObject current;public static String getCSRFToken(org.b3log.latke.http.RequestContext c){return "fixture";}public static org.json.JSONObject getUser(){if(current!=null)AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(current.getString("oId"),0,"a".repeat(32)));return current;}}');
 shim('org/b3log/symphony/processor/ApiProcessor','package org.b3log.symphony.processor;public class ApiProcessor {public static org.json.JSONObject getUserByKey(String key){if(!java.util.Set.of("query2","body1").contains(key==null?"":key))throw new NullPointerException();String actor=key.equals("query2")?"2":"1";org.b3log.symphony.util.AccountCredentialVersions.remember(new org.b3log.symphony.util.AccountCredentialVersions.Proof(actor,0,"a".repeat(32)));return new org.json.JSONObject().put("oId",actor).put("userName","owner"+actor);}}');
 const real=['util/ApiV3TokenUtil','model/Common','model/ApiV3Request','repository/ApiV3RequestRepository','util/ApiV3MutationStore','service/ApiV3IdempotencyMgmtService','model/ApiV3GameSave','repository/ApiV3GameSaveRepository','util/ApiV3GameSaveInputs','service/ApiV3GameSaveService','processor/ApiV3GameSaveProcessor','processor/CloudProcessor','processor/middleware/LoginCheckMidware','processor/middleware/CSRFMidware','util/StatusCodes','util/AccountCredentialVersions','util/ApiV3OAuthForms','util/ApiV3OAuthTransportLogFilter','model/UserExt','model/Role','model/ApiV3Client','model/ApiV3Token','model/ApiV3AuthorizationRequest','model/ApiV3AuthorizationCode','model/AccountCredentialState','model/OpenIdRefreshToken','repository/ApiV3ClientRepository','repository/ApiV3TokenRepository','repository/ApiV3AuthorizationCodeRepository','repository/ApiV3AuthorizationRequestRepository','repository/AccountCredentialStateRepository','repository/OpenIdRefreshTokenRepository','util/ApiV3Requests','util/ApiV3Exception','util/ApiV3Json','util/ApiV3WriteInputs','util/ApiV3Transactions','util/ApiV3TransactionStatements','util/MultiFactorAuthenticator','util/ApiV3SecurityInputs','service/AccountCredentialMgmtService','service/ApiV3SecurityProofService','service/ApiV3SecuritySettingsService','processor/ApiV3SecurityProcessor','processor/middleware/ApiV3AuthMidware','processor/middleware/ApiV3WriteMidware'];
 const fixtures=[];
 for(const name of ['ApiV3SecurityTest','ApiV3GameSaveTest']){
  let source=fs.readFileSync('src/test/java/org/b3log/symphony/service/'+name+'.java','utf8');
  if(name==='ApiV3SecurityTest'){
   source=source.replace('String url="jdbc:sqlite:"+file','String url=System.getProperty("rhythm.gameSave.mysqlUrl")').replaceAll('q.replace(" FOR UPDATE","")','q');
   source=source.replace('static void sql(Connection c,String q,Object...a)throws RepositoryException{',`static void sql(Connection c,String q,Object...a)throws RepositoryException{
    try{if(!Boolean.parseBoolean(System.getProperty("rhythm.mysql.fixtureSetup","true"))&&c.getAutoCommit())return;}catch(SQLException error){throw new RepositoryException(error);}
    if(q.startsWith("CREATE TABLE")&&!q.startsWith("CREATE TABLE IF NOT EXISTS"))q=q.replace("TEXT PRIMARY KEY","VARCHAR(64) PRIMARY KEY").replace(" INT"," BIGINT").replace(" TEXT"," MEDIUMTEXT");`);
   source=source.replace('F()throws Exception {',`F()throws Exception {
    if(Boolean.parseBoolean(System.getProperty("rhythm.mysql.fixtureReset","false")))for(String table:List.of("save_slots","legacy_cloud","receipts","u","clients","tokens","states","openid","requests","codes"))sql(db,"DROP TABLE IF EXISTS "+table);`);
  }else{
   const ddl=fs.readFileSync('sql/20261025_api_v3_game_save.sql','utf8').match(/CREATE TABLE IF NOT EXISTS `symphony_api_v3_game_save`[\s\S]*?;/)?.[0]?.replace('`symphony_api_v3_game_save`','`save_slots`');
   if(!ddl)throw Error('Missing exact game save DDL');
   const original=source.split('\n').find(line=>line.includes('sql(db,"CREATE TABLE save_slots('));
   if(!original)throw Error('Missing fixture slots');source=source.replace(original,'            sql(db,'+JSON.stringify(ddl)+');');
   source=source.replace('q.replace("OCTET_LENGTH(data)","length(CAST(data AS BLOB))").replace("BINARY gameId","gameId COLLATE BINARY")','q');
  }
  const target=path.join(dir,'org/b3log/symphony/service/'+name+'.java');fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,source);fixtures.push(target);
 }
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',dir,...shims,'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java',...real.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),...fixtures,'src/test/java/org/b3log/symphony/service/ApiV3GameSaveMysqlTest.java']);
 container='rhythm-v3-game-save-'+Date.now();
 docker(['run','-d','--rm','--pull','never','--memory','768m','--name',container,'-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes','-e','MYSQL_ROOT_HOST=%','-p','127.0.0.1::3306','mysql:8.4','--skip-log-bin','--performance-schema=OFF','--innodb-buffer-pool-size=32M']);
 const deadline=Date.now()+65000;while(true){const ready=spawnSync('docker',['exec',container,'mysqladmin','-uroot','ping'],{encoding:'utf8',timeout:5000});const comm=spawnSync('docker',['exec',container,'cat','/proc/1/comm'],{encoding:'utf8',timeout:5000});if(ready.status===0&&comm.stdout.trim()==='mysqld')break;if(Date.now()>deadline)throw Error('MySQL startup timeout');await new Promise(r=>setTimeout(r,250));}
 const bound=docker(['port',container,'3306/tcp']);if(!/^127\.0\.0\.1:[0-9]+$/.test(bound)||bound.endsWith(':3306'))throw Error('Unsafe port binding');
 const base=['-Drhythm.gameSave.mysqlUrl=jdbc:mysql://'+bound+'/rhythm_game_save?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000','-cp',[dir,cp].join(path.delimiter)];
 const klass='org.b3log.symphony.service.ApiV3GameSaveMysqlTest';
 function java(args){const r=spawnSync(path.join(home,'bin','java'),[...base,klass,...args],{encoding:'utf8',timeout:45000});if(r.status!==0)throw Error(r.stdout+' '+r.stderr);return r.stdout;}
 function fresh(){docker(['exec',container,'mysql','-uroot','-e','DROP DATABASE IF EXISTS rhythm_game_save;CREATE DATABASE rhythm_game_save CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;']);}
 fresh();
 const migration=fs.readFileSync('sql/20261025_api_v3_game_save.sql','utf8');
 const schema=spawnSync('docker',['exec','-i',container,'mysql','-uroot','rhythm_game_save'],{input:'CREATE TABLE symphony_cloud(oId VARCHAR(19) PRIMARY KEY,userId VARCHAR(19),gameId VARCHAR(19),data MEDIUMTEXT) ENGINE=InnoDB;\n'+migration+'\n'+migration,encoding:'utf8',timeout:25000});
 if(schema.status!==0)throw Error(schema.stderr);
 const columns=docker(['exec',container,'mysql','-uroot','-N','-B','rhythm_game_save','-e',"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_cloud' AND index_name='idx_cloud_owner_game_id';SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_api_v3_game_save' AND index_name='uk_v3_game_save_owner_game';"]);
 if(columns!=='3\n2')throw Error('Unexpected game save indexes '+columns);
 const metadata=JSON.parse(fs.readFileSync('src/main/resources/repository.json','utf8')).repositories.find(r=>r.name==='api_v3_game_save');
 if(!metadata||metadata.keys.find(k=>k.name==='data')?.length!==500000)throw Error('Missing game save repository metadata');
 console.log('PASS exact full migration twice, legacy lookup/unique slot indexes and repository metadata');
 fresh();const unit=spawnSync(path.join(home,'bin','java'),['-Drhythm.mysql.fixtureReset=true',...base,'org.b3log.symphony.service.ApiV3GameSaveTest'],{encoding:'utf8',timeout:60000});if(unit.status!==0)throw Error(unit.stdout+' '+unit.stderr);console.log(unit.stdout);
 function worker(extra){const p=spawn(path.join(home,'bin','java'),[...base,klass,...extra]),state={p,out:'',err:'',done:false};p.stdout.on('data',x=>state.out+=x);p.stderr.on('data',x=>state.err+=x);state.finished=new Promise((resolve,reject)=>{p.on('error',reject);p.on('close',code=>{state.done=true;code===0?resolve():reject(Error(state.out+' '+state.err));});});state.finished.catch(()=>{});children.push(state);return state;}
 async function until(state,marker){const end=Date.now()+15000;while(!state.out.includes(marker)){if(state.done)throw Error(state.out+' '+state.err);if(Date.now()>end)throw Error('Marker timeout '+marker);await new Promise(r=>setTimeout(r,20));}}
 for(const race of ['same-key','cas-conflict','old-first','v3-first','migrate-first','old-before-migrate','migrate-create']){
  fresh();console.log(java(['setup',race]));const release=path.join(dir,'release-'+race);
  const a=worker(['worker',race,'holder',release]);await until(a,'BEFORE_COMMIT');
  const b=worker(['worker',race,'contender',release]);await until(b,'STARTING_WORK');
  let waited=false;const end=Date.now()+10000;while(Date.now()<end){if(Number(docker(['exec',container,'mysql','-uroot','-N','-B','-e',"SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'"]))>0){waited=true;break;}if(b.done)throw Error('Contender did not block '+b.out);await new Promise(r=>setTimeout(r,50));}
  if(!waited)throw Error('No InnoDB lock wait for '+race);fs.writeFileSync(release,'release');await Promise.all([a.finished,b.finished]);
  const ra=JSON.parse(a.out.match(/RESULT=(.*)/)[1]),rb=JSON.parse(b.out.match(/RESULT=(.*)/)[1]);
  const expected=['cas-conflict','old-first','old-before-migrate','migrate-create'].includes(race)?409:200;
  if(ra.status!==200||rb.status!==expected)throw Error('Unexpected race '+race+' '+a.out+' '+b.out);
  console.log(java(['verify',race]));console.log('PASS game save mysql race '+race+' independent JVMs with observed InnoDB lock wait');
 }
}finally{
 for(const child of children)if(!child.done)child.p.kill('SIGKILL');await Promise.allSettled(children.map(c=>c.finished));
 if(container){docker(['rm','-f',container]);console.log('Disposable game-save MySQL removed; random loopback port only');}
 fs.rmSync(dir,{recursive:true,force:true});
}
