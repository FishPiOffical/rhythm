#!/usr/bin/env node
/** 在 SQLite 或 --mysql 模式执行生产读取 SQL、实际鉴权与控制器，应用 IoC 另行验证。 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`,dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-comment-rewards-'));
const mysql=process.argv.includes('--mysql');let container;
const cp=[...(mysql?[path.join(os.homedir(),'.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar')]:[]),`${ide}/lib/intellij.libraries.caffeine.jar`,'src/main/resources/lib/latke-core.jar',`${ide}/plugins/testng/lib/testng.jar`,`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter),shims=[];
function shim(name, body) {
 const f = path.join(dir, name + '.java');
 fs.mkdirSync(path.dirname(f), { recursive: true });
 fs.writeFileSync(f, body);
 shims.push(f)
}

function run(binary, args) {
 const r = spawnSync(binary, args, { stdio: 'inherit' });
 if (r.error) throw r.error;
 if (r.status !== 0) throw Error('comment reward read test failed')
}
try{
 for(const name of ['User','Comment','Reward'])shim('org/b3log/symphony/repository/'+name+'Repository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;public class '+name+'Repository extends AbstractRepository{public '+name+'Repository(){super("'+name.toLowerCase()+'");}}');
 shim('org/b3log/symphony/model/UserExt','package org.b3log.symphony.model;public class UserExt{public static final int USER_STATUS_C_VALID=0,USER_XXX_STATUS_C_ENABLED=0;public static final String USER_NICKNAME="userNickname",USER_AVATAR_URL="userAvatarURL";}');
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys{public static final int POINT_THANK_COMMENT=15;}');
 shim('org/b3log/symphony/service/ApiV3ContentQueryService','package org.b3log.symphony.service;public class ApiV3ContentQueryService{public org.json.JSONObject viewableArticle(String id,org.json.JSONObject viewer){throw new AssertionError();}}');
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor{public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "comment-reward-read";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("status",200);c.attr("data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("status",status);}}');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService','package org.b3log.symphony.service;public class ApiV3TokenMgmtService{public org.json.JSONObject authenticate(String token){return null;}public static boolean hasScope(String scope,String required){return java.util.Arrays.asList(scope.split(" ")).contains(required);}}');
 let test='src/test/java/org/b3log/symphony/service/ApiV3CommentRewardQueryTest.java';
 if(mysql){const f=path.join(dir,'ApiV3CommentRewardQueryTest.java');fs.writeFileSync(f,fs.readFileSync(test,'utf8').replace('DriverManager.getConnection("jdbc:sqlite::memory:")','DriverManager.getConnection(System.getProperty("rhythm.commentRewards.mysqlUrl"))').replaceAll('oId TEXT PRIMARY KEY','oId VARCHAR(19) PRIMARY KEY'));test=f;}
 const sources=['model/Article','model/Comment','model/Reward','model/Role','util/ApiV3Exception','util/ApiV3Requests','util/ApiV3ContentUtil','service/ApiV3CommentRewardQueryService','processor/ApiV3CommentRewardProcessor','processor/middleware/ApiV3AuthMidware'];
 run(home+'/bin/javac',['--release','25','-proc:none','-cp',cp,'-d',dir,...shims,...sources.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),test]);
 const opts=[];
 if(mysql){
  function docker(args){const r=spawnSync('docker',args,{encoding:'utf8',timeout:20000});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim()}
  container='rhythm-v3-comment-rewards-'+Date.now();docker(['run','-d','--rm','--pull','never','--memory','768m','--name',container,'-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes','-e','MYSQL_ROOT_HOST=%','-p','127.0.0.1::3306','mysql:8.4','--skip-log-bin','--performance-schema=OFF','--innodb-buffer-pool-size=32M']);
  const end=Date.now()+65000;while(true){const ping=spawnSync('docker',['exec',container,'mysqladmin','-uroot','ping'],{encoding:'utf8',timeout:4000});if(ping.status===0&&docker(['exec',container,'cat','/proc/1/comm'])==='mysqld')break;if(Date.now()>end)throw Error('MySQL startup timeout');await new Promise(r=>setTimeout(r,250));}
  const bound=docker(['port',container,'3306/tcp']);if(!/^127\.0\.0\.1:[0-9]+$/.test(bound)||bound.endsWith(':3306'))throw Error('Unsafe MySQL port');docker(['exec',container,'mysql','-uroot','-e','CREATE DATABASE comment_rewards CHARACTER SET utf8mb4;']);
  opts.push('-Drhythm.commentRewards.mysqlUrl=jdbc:mysql://'+bound+'/comment_rewards?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=20000');
 }
 run(home+'/bin/java',[...opts,'-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3CommentRewardQueryTest']);
}finally{if(container){const r=spawnSync('docker',['rm','-f',container],{encoding:'utf8',timeout:20000});if(r.status!==0)throw Error(r.stderr);console.log('Temporary comment rewards MySQL removed');}fs.rmSync(dir,{recursive:true,force:true})}
