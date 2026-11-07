#!/usr/bin/env node
/** Production identity workflow on exact MySQL material/application DDL; explicit authentication and ancillary adapters. */
import fs from 'node:fs';import path from 'node:path';import os from 'node:os';import {spawnSync,spawn} from 'node:child_process';import crypto from 'node:crypto';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=[path.join(os.homedir(),'.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar'),'src/main/resources/lib/latke-core.jar',process.env.API_V3_CAFFEINE_JAR||`${ide}/lib/intellij.libraries.caffeine.jar`,process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-identity-mysql-')),shims=[];
function shim(name,body){const file=path.join(dir,name+'.java');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,body);shims.push(file);}
function method(source,signature){const begin=source.indexOf(signature);if(begin<0)throw Error(signature);let depth=0;for(let i=source.indexOf('{',begin);i<source.length;i++){if(source[i]==='{')depth++;if(source[i]==='}'&&--depth===0)return source.slice(begin,i+1);}throw Error('Unclosed method');}
function run(bin,args){const r=spawnSync(bin,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw Error('Identity application tests failed');}
let container;const children=[];
function docker(args){const result=spawnSync('docker',args,{encoding:'utf8',timeout:25000});if(result.status!==0)throw Error(result.stderr);return result.stdout.trim();}
try{
 const point=fs.readFileSync('src/main/java/org/b3log/symphony/model/Pointtransfer.java','utf8');
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys {public static final String[] RESERVED_USER_NAMES={"admin"};public static final long INVITECODE_EXPIRED=259200000L;'+[...new Set([...point.matchAll(/Symphonys\.(POINT_[A-Z_]+)/g)].map(m=>m[1]))].map(k=>'public static final int '+k+'='+((k==='POINT_BUY_INVITECODE'||k==='POINT_CHANGE_USERNAME'||k==='POINT_DATA_EXPORT')?100:0)+';').join('')+'}');
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;import org.b3log.latke.repository.RepositoryException;public class UserRepository extends AbstractRepository {public UserRepository(){super("user");}public void updateColumnsInCurrentTransaction(String id,org.json.JSONObject row)throws RepositoryException{throw new UnsupportedOperationException();}public void evictUserNameCaches(String id,String name){}public void refreshSettingsCache(String id)throws RepositoryException{}public void evictSettingsCaches(String id){}public org.json.JSONObject getForUpdate(String id)throws RepositoryException{throw new UnsupportedOperationException();}public org.json.JSONObject getByName(String name)throws RepositoryException{throw new UnsupportedOperationException();}}');
 shim('org/b3log/symphony/repository/PointtransferRedcRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;public class PointtransferRedcRepository extends AbstractRepository {public PointtransferRedcRepository(){super("pointtransfer_redc");}public static void addRecordAsync(String user,String raw){throw new AssertionError("unexpected legacy async indexing");}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService',`package org.b3log.symphony.service;import org.json.JSONObject;import org.apache.commons.lang.StringUtils;import java.util.*;public class ApiV3TokenMgmtService {public static final String WEBSITE_CLIENT="website_v3";public JSONObject authenticate(String value){return null;}${method(tokens,'public static boolean hasScope(')}}`);
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "fixture-post-export";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);c.attr("test.message",msg);}}');
 shim('org/b3log/symphony/repository/ArticleRepository','package org.b3log.symphony.repository;public class ArticleRepository extends org.b3log.latke.repository.AbstractRepository {public ArticleRepository(){super("article");}}');
 shim('org/b3log/symphony/repository/CommentRepository','package org.b3log.symphony.repository;public class CommentRepository extends org.b3log.latke.repository.AbstractRepository {public CommentRepository(){super("comment");}}');
 const real=['model/UserExt','model/Role','model/Common','model/ApiV3Client','model/ApiV3Token','model/ApiV3AuthorizationRequest','model/ApiV3AuthorizationCode','model/AccountCredentialState','model/OpenIdRefreshToken','model/ApiV3Request','model/Pointtransfer','model/PointtransferSource','repository/ApiV3ClientRepository','repository/ApiV3TokenRepository','repository/ApiV3AuthorizationCodeRepository','repository/ApiV3AuthorizationRequestRepository','repository/AccountCredentialStateRepository','repository/OpenIdRefreshTokenRepository','repository/ApiV3RequestRepository','repository/PointtransferRepository','repository/ApiV3PostExportRepository','repository/ApiV3PostExportChunkRepository','util/ApiV3Requests','util/ApiV3Exception','util/ApiV3Json','util/ApiV3WriteInputs','util/ApiV3Transactions','util/ApiV3TransactionStatements','util/ApiV3MutationStore','util/ApiV3TokenUtil','util/MultiFactorAuthenticator','util/ApiV3SecurityInputs','util/AccountPairLocks','util/ArticlePublicationCaches','util/ApiV3PostExportFiles','service/AccountCredentialMgmtService','service/ApiV3SecurityProofService','service/ApiV3SecuritySettingsService','service/ApiV3IdempotencyMgmtService','service/PointtransferMgmtService','service/ApiV3PostExportSnapshotService','service/ApiV3PostExportService','processor/ApiV3SecurityProcessor','repository/ApiV3IdentityApplicationRepository','repository/ApiV3IdentityMaterialRepository','repository/ApiV3IdentityReviewerRepository','util/ApiV3OAuthTransportLogFilter','util/ApiV3IdentityImage','service/ApiV3IdentityApplicationService','processor/ApiV3IdentityApplicationProcessor','processor/ApiV3PostExportProcessor','processor/middleware/ApiV3AuthMidware','processor/middleware/ApiV3WriteMidware'];
 const fixtures=[];
 for(const name of ['ApiV3SecurityTest','ApiV3IdentityApplicationTest']){
  let source=fs.readFileSync('src/test/java/org/b3log/symphony/service/'+name+'.java','utf8');
  if(name==='ApiV3SecurityTest'){
   source=source.replace('String url="jdbc:sqlite:"+file','String url=System.getProperty("rhythm.identity.mysqlUrl")');
   source=source.replaceAll('q.replace(" FOR UPDATE","")','q');
   source=source.replace('static void sql(Connection c,String q,Object...a)throws RepositoryException{',`static void sql(Connection c,String q,Object...a)throws RepositoryException{
    try{if(!Boolean.parseBoolean(System.getProperty("rhythm.mysql.fixtureSetup","true"))&&c.getAutoCommit())return;}catch(SQLException error){throw new RepositoryException(error);}
    if(q.startsWith("CREATE TABLE")&&!q.startsWith("CREATE TABLE IF NOT EXISTS"))q=q.replace("TEXT PRIMARY KEY","VARCHAR(64) PRIMARY KEY").replace(" INT"," BIGINT").replace(" TEXT"," MEDIUMTEXT");`);
   source=source.replace('F()throws Exception {',`F()throws Exception {
    if(Boolean.parseBoolean(System.getProperty("rhythm.mysql.fixtureReset","false")))for(String table:List.of("identity_app","identity_material","identity_reviewer","receipt","u","clients","tokens","states","openid","requests","codes"))sql(db,"DROP TABLE IF EXISTS "+table);`);
  }else{
   const migration=fs.readFileSync('sql/20261024_api_v3_identity_application.sql','utf8');
   for(const [table,alias]of [['api_v3_identity_application','identity_app'],['api_v3_identity_material','identity_material'],['api_v3_identity_reviewer','identity_reviewer']]){
    const ddl=migration.match(new RegExp('CREATE TABLE IF NOT EXISTS `symphony_'+table+'`[\\s\\S]*?;'))?.[0]?.replace('`symphony_'+table+'`','`'+alias+'`');
    if(!ddl)throw Error('Missing identity migration '+table);
    const original=new RegExp('sql\\(db,"CREATE TABLE '+alias+'\\([^\\n]+?\\);');
    if(!original.test(source))throw Error('Missing fixture DDL '+alias);
    source=source.replace(original,()=>`sql(db,${JSON.stringify(ddl)});`);
   }
  }
  const target=path.join(dir,'org/b3log/symphony/service/'+name+'.java');fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,source);fixtures.push(target);
 }
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',dir,...shims,'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java',...[...new Set(real)].map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),...fixtures,'src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationMysqlTest.java']);
 const image=docker(['image','inspect','mysql:8.4','--format','{{.Id}}']);container='rhythm-v3-identity-'+crypto.randomBytes(6).toString('hex');
 docker(['run','-d','--rm','--pull','never','--memory','768m','--name',container,'-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes','-e','MYSQL_ROOT_HOST=%','-p','127.0.0.1::3306',image,'--skip-log-bin','--performance-schema=OFF','--innodb-buffer-pool-size=32M']);
 const deadline=Date.now()+90000;while(true){const ready=spawnSync('docker',['exec',container,'mysqladmin','-uroot','ping'],{encoding:'utf8',timeout:5000});const comm=spawnSync('docker',['exec',container,'cat','/proc/1/comm'],{encoding:'utf8',timeout:5000});if(ready.status===0&&comm.stdout.trim()==='mysqld')break;if(Date.now()>deadline)throw Error('MySQL startup timeout');await new Promise(r=>setTimeout(r,250));}
 const bound=docker(['port',container,'3306/tcp']);if(!/^127\.0\.0\.1:[0-9]+$/.test(bound)||bound.endsWith(':3306'))throw Error('Unsafe port binding');
 const base=['-Drhythm.identity.mysqlUrl=jdbc:mysql://'+bound+'/rhythm_identity?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000','-cp',[dir,cp].join(path.delimiter)];
 const klass='org.b3log.symphony.service.ApiV3IdentityApplicationMysqlTest';
 function java(args){const result=spawnSync(path.join(home,'bin','java'),[...base,klass,...args],{encoding:'utf8',timeout:45000});if(result.status!==0)throw Error(result.stdout+' '+result.stderr);return result.stdout;}
 function fresh(){docker(['exec',container,'mysql','-uroot','-e','DROP DATABASE IF EXISTS rhythm_identity;CREATE DATABASE rhythm_identity CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;']);}
 fresh();const unit=spawnSync(path.join(home,'bin','java'),['-Drhythm.mysql.fixtureReset=true',...base,'org.b3log.symphony.service.ApiV3IdentityApplicationTest'],{encoding:'utf8',timeout:60000});if(unit.status!==0)throw Error(unit.stdout+' '+unit.stderr);console.log(unit.stdout);
 function worker(extra){const p=spawn(path.join(home,'bin','java'),[...base,klass,...extra]),state={p,out:'',err:'',done:false};p.stdout.on('data',x=>state.out+=x);p.stderr.on('data',x=>state.err+=x);state.finished=new Promise((resolve,reject)=>{p.on('error',reject);p.on('close',code=>{state.done=true;code===0?resolve():reject(Error(state.out+' '+state.err));});});state.finished.catch(()=>{});children.push(state);return state;}
 async function until(state,marker){const end=Date.now()+15000;while(!state.out.includes(marker)){if(state.done)throw Error(state.out+' '+state.err);if(Date.now()>end)throw Error('Marker timeout '+marker);await new Promise(r=>setTimeout(r,20));}}
 for(const race of ['same-key','different-key','open','decision-same','decision-conflict']){
  fresh();console.log(java(['setup',race]));const release=path.join(dir,'release-'+race);
  const a=worker(['worker',race,'holder',release]);await until(a,'BEFORE_COMMIT');
  const b=worker(['worker',race,'contender',release]);await until(b,'STARTING_WORK');
  let waited=false;const end=Date.now()+10000;while(Date.now()<end){if(Number(docker(['exec',container,'mysql','-uroot','-N','-B','-e',"SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'"]))>0){waited=true;break;}if(b.done)throw Error('Contender did not block '+b.out);await new Promise(r=>setTimeout(r,50));}
  if(!waited)throw Error('No InnoDB lock wait for '+race);fs.writeFileSync(release,'release');await Promise.all([a.finished,b.finished]);
  const resultA=JSON.parse(a.out.match(/RESULT=(.*)/)[1]),resultB=JSON.parse(b.out.match(/RESULT=(.*)/)[1]);
  if(race==='same-key'&&(resultA.status!==200||resultB.status!==200||!resultA.id||resultA.id!==resultB.id))throw Error('Same-key returned different applications');
  if(race==='different-key'&&(resultA.status!==200||resultB.status!==409))throw Error('Second pending application was accepted');
  if(race==='open'&&(resultA.status!==200||resultB.status!==410))throw Error('Duplicate or lost one-shot read');
  if(race==='decision-same'&&(resultA.status!==200||resultB.status!==200||resultA.decision!=='approved'||resultB.decision!=='approved'||resultA.id!==resultB.id))throw Error('Decision replay conflict');
  if(race==='decision-conflict'&&(resultA.status!==200||resultB.status!==409))throw Error('Conflicting decision accepted');
  console.log(java(['verify',race]));console.log('PASS identity mysql race '+race+' independent JVMs with observed InnoDB lock wait');
 }
}finally{
 for(const child of children)if(!child.done)child.p.kill('SIGKILL');await Promise.allSettled(children.map(c=>c.finished));
 if(container){docker(['rm','-f',container]);if(docker(['ps','-aq','--filter','name=^/'+container+'$']))throw Error('Container cleanup failed');console.log('Disposable identity MySQL removed; random loopback port only');}
 fs.rmSync(dir,{recursive:true,force:true});
}
