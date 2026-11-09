import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`,dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-chat-mutes-')),shims=[];
const mysql=process.argv.includes('--mysql');let container;
const cp=[...(mysql?[path.join(os.homedir(),'.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar')]:[]),`${ide}/lib/intellij.libraries.caffeine.jar`,'src/main/resources/lib/latke-core.jar',`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
function shim(name, body) {
 const p = path.join(dir, name + '.java');
 fs.mkdirSync(path.dirname(p), { recursive: true });
 fs.writeFileSync(p, body);
 shims.push(p)
}

function run(bin, args) {
 const r = spawnSync(bin, args, { stdio: 'inherit' });
 if (r.error) throw r.error;
 if (r.status) throw Error('Chat mute tests failed')
}
try{
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;public class UserRepository extends AbstractRepository{public UserRepository(){super("user");}}');
 shim('org/b3log/symphony/repository/CloudRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;public class CloudRepository extends AbstractRepository{public CloudRepository(){super("cloud");}}');
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor{public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "suggest-test";}public static void success(org.b3log.latke.http.RequestContext c,Object d){c.attr("status",200);c.attr("data",d);}public static void error(org.b3log.latke.http.RequestContext c,int s,String code,String msg){c.attr("status",s);c.attr("code",code);}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');const scope=tokens.match(/public static boolean hasScope\([^]*?\n    }/)[0];
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService','package org.b3log.symphony.service;import java.util.*;import org.apache.commons.lang.StringUtils;import org.json.JSONObject;public class ApiV3TokenMgmtService{public JSONObject authenticate(String token){return null;}'+scope+'}');
 const source=['model/FishGame','model/FishGameVote','model/Milestone','util/ApiV3CommunityDto','util/ApiV3Exception','util/ApiV3Requests','util/ApiV3Json','util/ApiV3OAuthForms','service/ApiV3ChatMuteQueryService','processor/ApiV3ChatMuteProcessor','processor/middleware/ApiV3AuthMidware'];
 let test='src/test/java/org/b3log/symphony/service/ApiV3ChatMutesTest.java';
 if(mysql){const file=path.join(dir,'ApiV3ChatMutesTest.java');fs.writeFileSync(file,fs.readFileSync(test,'utf8').replace('DriverManager.getConnection("jdbc:sqlite::memory:")','DriverManager.getConnection(System.getProperty("suggest.mysql"))').replaceAll('oId TEXT PRIMARY KEY','oId VARCHAR(19) PRIMARY KEY'));test=file;}
 run(home+'/bin/javac',['--release','25','-proc:none','-cp',cp,'-d',dir,...shims,...source.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),test]);
 const opts=[];
 if(mysql){
  function docker(args){const r=spawnSync('docker',args,{encoding:'utf8',timeout:20000});if(r.status)throw Error(r.stderr);return r.stdout.trim()}
  container='rhythm-v3-user-suggest-'+Date.now();docker(['run','-d','--rm','--pull','never','--memory','768m','--name',container,'-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes','-e','MYSQL_ROOT_HOST=%','-p','127.0.0.1::3306','mysql:8.4','--skip-log-bin','--performance-schema=OFF','--innodb-buffer-pool-size=32M']);
  const end=Date.now()+65000;while(true){const ping=spawnSync('docker',['exec',container,'mysqladmin','-uroot','ping'],{encoding:'utf8',timeout:4000});if(ping.status===0&&docker(['exec',container,'cat','/proc/1/comm'])==='mysqld')break;if(Date.now()>end)throw Error('MySQL startup timeout');await new Promise(r=>setTimeout(r,250))}
  const bound=docker(['port',container,'3306/tcp']);if(!/^127\.0\.0\.1:[0-9]+$/.test(bound)||bound.endsWith(':3306'))throw Error('Unsafe test port');docker(['exec',container,'mysql','-uroot','-e','CREATE DATABASE suggestions CHARACTER SET utf8mb4;']);opts.push('-Dsuggest.mysql=jdbc:mysql://'+bound+'/suggestions?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=20000');
 }
 run(home+'/bin/java',[...opts,'-cp',dir+path.delimiter+cp,'org.b3log.symphony.service.ApiV3ChatMutesTest']);
} finally {
 if (container) {
  const r = spawnSync('docker', ['rm', '-f', container], { encoding: 'utf8', timeout: 20000 });
  if (r.status) throw Error(r.stderr);
  console.log('Temporary suggestions MySQL removed')
 }
 fs.rmSync(dir, { recursive: true, force: true })
}
