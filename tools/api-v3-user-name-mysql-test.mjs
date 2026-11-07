#!/usr/bin/env node
/** Disposable MySQL 8.4 + real Connector/J and production rename/points/receipt transaction core. */
import fs from 'node:fs';import path from 'node:path';import os from 'node:os';import crypto from 'node:crypto';import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const driver=process.env.API_V3_MYSQL_DRIVER||path.join(os.homedir(),'.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar');
const encoder=process.env.API_V3_ENCODER_JAR||'/tmp/rhythm-v3-encoder-1.2.2.jar';
for(const p of [driver,encoder])if(!fs.existsSync(p))throw Error('Missing test dependency: '+p);
const cp=[driver,encoder,'src/main/resources/lib/latke-core.jar',process.env.API_V3_CAFFEINE_JAR||`${ide}/lib/intellij.libraries.caffeine.jar`,process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`].join(path.delimiter);
const id=crypto.randomBytes(6).toString('hex'),container='rhythm-v3-username-'+id,schema='rhythm_v3_username_'+id;
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-username-mysql-')),shims=[];let started=false;
function shim(name,body){const file=path.join(dir,name+'.java');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,body);shims.push(file);}
function method(source,signature){const begin=source.indexOf(signature);if(begin<0)throw Error(signature);let depth=0;for(let i=source.indexOf('{',begin);i<source.length;i++){if(source[i]==='{')depth++;if(source[i]==='}'&&--depth===0)return source.slice(begin,i+1);}throw Error('Unclosed method');}
function run(bin,args,options={}){const r=spawnSync(bin,args,{encoding:'utf8',timeout:120000,...options});if(r.error)throw r.error;if(r.status!==0)throw Error(`${bin} failed (${r.status}): ${r.stderr||''}`);return r.stdout?.trim()||'';}
const config=fs.readFileSync('src/main/resources/symphony.properties','utf8');
const price=Number(config.match(/^pointChangeUsername=(\d+)$/m)?.[1]);if(!Number.isSafeInteger(price)||price<=0)throw Error('Review actual pointChangeUsername configuration');
const repositories=JSON.parse(fs.readFileSync('src/main/resources/repository.json')).repositories;
const selected={user:['oId','userName','userStatus','userPoint','userUsedPoint'],option:['oId','optionValue'],pointtransfer:null,pointtransfer_redc:null};
const ddl=[];
for(const [name,fields]of Object.entries(selected)){
 const table=repositories.find(r=>r.name===name);if(!table)throw Error('Missing repository '+name);
 const keys=table.keys.filter(k=>fields===null||fields.includes(k.name));
 const columns=keys.map(k=>'`'+k.name+'` '+(k.type==='String'?(k.length>16383?'MEDIUMTEXT':'VARCHAR('+k.length+')'):k.type==='long'?'BIGINT':k.type==='int'?'INT':(()=>{throw Error('Unhandled schema type '+k.type)})())+' NOT NULL');
 columns.push('PRIMARY KEY (`oId`)');if(name==='user')columns.push('INDEX name_lookup (`userName`)');if(name==='pointtransfer_redc')columns.push('INDEX idx_pointtransfer_redc_raw_user (`rawId`,`userId`)');
 ddl.push('CREATE TABLE `'+name+'` ('+columns.join(',')+') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci');
}
const requestMigration=fs.readFileSync('sql/20261016_api_v3.sql','utf8').match(/CREATE TABLE IF NOT EXISTS `symphony_api_v3_request`[\s\S]*?;/)?.[0];if(!requestMigration)throw Error('Review receipt migration');ddl.push(requestMigration.replace('IF NOT EXISTS ','').replace('symphony_api_v3_request','api_v3_request').replace(/;$/,''));
const schemaFile=path.join(dir,'schema.sql');fs.writeFileSync(schemaFile,ddl.join(';\n')+';');
try{
 const point=fs.readFileSync('src/main/java/org/b3log/symphony/model/Pointtransfer.java','utf8');
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys {public static final String[] RESERVED_USER_NAMES={"admin"};public static final long INVITECODE_EXPIRED=259200000L;'+[...new Set([...point.matchAll(/Symphonys\.(POINT_[A-Z_]+)/g)].map(m=>m[1]))].map(k=>'public static final int '+k+'='+(k==='POINT_CHANGE_USERNAME'?price:0)+';').join('')+'}');
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;import org.b3log.latke.repository.RepositoryException;public class UserRepository extends AbstractRepository {public UserRepository(){super("user");}public void updateColumnsInCurrentTransaction(String id,org.json.JSONObject row)throws RepositoryException{throw new UnsupportedOperationException();}public void evictUserNameCaches(String id,String name){}public void refreshSettingsCache(String id)throws RepositoryException{}public void evictSettingsCaches(String id){}public org.json.JSONObject getForUpdate(String id)throws RepositoryException{throw new UnsupportedOperationException();}public org.json.JSONObject getByName(String name)throws RepositoryException{throw new UnsupportedOperationException();}}');
 shim('org/b3log/symphony/repository/PointtransferRedcRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;public class PointtransferRedcRepository extends AbstractRepository {public PointtransferRedcRepository(){super("pointtransfer_redc");}public static void addRecordAsync(String user,String raw){throw new AssertionError("unexpected legacy async indexing");}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService',`package org.b3log.symphony.service;import org.json.JSONObject;import org.apache.commons.lang.StringUtils;import java.util.*;public class ApiV3TokenMgmtService {public JSONObject authenticate(String value){return null;}${method(tokens,'public static boolean hasScope(')}}`);
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "fixture-invitation-purchase";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);c.attr("test.message",msg);}}');
 shim('org/b3log/symphony/cache/OptionCache','package org.b3log.symphony.cache;public class OptionCache {public void removeOption(String id){}public org.json.JSONObject getOption(String id){throw new AssertionError("cached option forbidden");}public void putOption(org.json.JSONObject value){throw new AssertionError("cached option forbidden");}}');
 shim('org/b3log/symphony/service/BagMgmtService','package org.b3log.symphony.service;public class BagMgmtService {public org.json.JSONObject mutateInCurrentTransaction(String actor,java.util.function.UnaryOperator<org.json.JSONObject> change){throw new AssertionError("rename must not spend a card");}}');
 shim('org/b3log/symphony/censor/CensorFactory','package org.b3log.symphony.censor;public class CensorFactory {public static TextCensor getTextCensor(){throw new AssertionError("External provider adapted at service boundary");}}');

 const real=['util/UserNamePointData','util/Escapes','util/UserNameRules','util/BagMutation','util/ProfileWriteQuota','service/UserNameMgmtService','service/ApiV3UserNameService','processor/ApiV3UserNameProcessor','censor/TextCensor','censor/CensorResult','model/UserExt','model/Role','model/Common','model/Notification','model/Profession','model/UserProfessionPrivacy','model/ApiV3Request','model/Pointtransfer','model/PointtransferSource','repository/ProfessionRepository','repository/UserProfessionPrivacyRepository','repository/ApiV3RequestRepository','repository/PointtransferRepository','util/ApiV3Requests','util/ApiV3Exception','util/ApiV3WriteInputs','util/ApiV3Json','util/ApiV3TokenUtil','util/ApiV3MutationStore','util/ApiV3Transactions','util/ApiV3TransactionSavepoint','util/ApiV3TransactionStatements','util/AccountPairLocks','util/ArticlePublicationCaches','service/ApiV3IdempotencyMgmtService','service/PointtransferMgmtService','model/Invitecode','repository/InvitecodeRepository','model/Option','repository/OptionRepository','processor/middleware/ApiV3AuthMidware','processor/middleware/ApiV3WriteMidware'];

 run(path.join(home,'bin','javac'),['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',dir,...shims,...real.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),'src/test/java/org/b3log/symphony/service/ApiV3UserNameMysqlTest.java',...['main/SimpleCurrentLimiter','control/MainControl','cache/MainCache','cache/pair/CachePair'].map(n=>'src/main/java/pers/adlered/simplecurrentlimiter/'+n+'.java')],{stdio:'inherit'});
 const image=run('docker',['image','inspect',process.env.API_V3_MYSQL_IMAGE||'mysql:8.4','--format','{{.Id}}']);
 run('docker',['run','-d','--rm','--pull','never','--memory','768m','--label','rhythm.api-v3-verification=true','--label','rhythm.api-v3-username-verification='+id,'--name',container,'-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes','-e','MYSQL_ROOT_HOST=%','-p','127.0.0.1::3306',image,'--skip-log-bin','--performance-schema=OFF','--innodb-buffer-pool-size=32M']);started=true;
 console.log('Disposable container '+container+' (unique label '+id+'), image '+image);
 const deadline=Date.now()+90000;
 while(true){
  const ready=spawnSync('docker',['exec',container,'mysqladmin','-uroot','ping'],{encoding:'utf8',timeout:5000});
  const process=spawnSync('docker',['exec',container,'cat','/proc/1/comm'],{encoding:'utf8',timeout:5000});
  if(ready.status===0&&process.stdout.trim()==='mysqld')break;
  if(Date.now()>deadline)throw Error('Disposable mysql startup timeout');await new Promise(r=>setTimeout(r,350));
 }
 const published=run('docker',['port',container,'3306/tcp']);if(!/^127\.0\.0\.1:[0-9]+$/.test(published))throw Error('Unsafe bind: '+published);const port=published.split(':')[1];if(port==='3306')throw Error('Do not use existing business port');
 run('docker',['exec',container,'mysql','-uroot','-e','CREATE DATABASE `'+schema+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci']);
 const url='jdbc:mysql://127.0.0.1:'+port+'/'+schema+'?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=20000';
 console.log('Test-only JDBC '+url+', actual configured price '+price);
 run(path.join(home,'bin','java'),['-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3UserNameMysqlTest',url,schemaFile,String(price)],{stdio:'inherit'});
}finally{
 if(started){run('docker',['rm','-f',container]);const r=spawnSync('docker',['ps','-aq','--filter','label=rhythm.api-v3-username-verification='+id],{encoding:'utf8',timeout:20000});if(r.status!==0||r.stdout.trim())throw Error('Disposable container cleanup not confirmed');console.log('Disposable MySQL container removed; unique label has no remaining containers');}
 fs.rmSync(dir,{recursive:true,force:true});
}
