#!/usr/bin/env node
/** 执行正式关注状态 SQL，认证与控制器独立于应用 IoC 编译。 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`,dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-follow-state-'));
const mysql = process.argv.includes('--mysql');
let container;
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
 if (r.status !== 0) throw Error('follow state test failed')
}
try{
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;public class UserRepository extends org.b3log.latke.repository.AbstractRepository{public UserRepository(){super("user");}}');
 shim('org/b3log/symphony/repository/TagRepository','package org.b3log.symphony.repository;public class TagRepository extends org.b3log.latke.repository.AbstractRepository{public TagRepository(){super("tag");}}');
 shim('org/b3log/symphony/model/Tag','package org.b3log.symphony.model;public class Tag{public static final int TAG_STATUS_C_VALID=0;}');
 shim('org/b3log/symphony/model/UserExt','package org.b3log.symphony.model;public class UserExt{public static final int USER_STATUS_C_VALID=0;}');
 shim('org/b3log/symphony/service/ApiV3ContentQueryService','package org.b3log.symphony.service;public class ApiV3ContentQueryService{public static class VisibleArticle{String id(){throw new AssertionError();} org.json.JSONObject viewer(){throw new AssertionError();}}public org.json.JSONObject viewableArticle(String id,org.json.JSONObject viewer){throw new AssertionError();}}');
 shim('org/b3log/symphony/service/ApiV3ContentDtoService','package org.b3log.symphony.service;public class ApiV3ContentDtoService{public org.json.JSONObject summary(org.json.JSONObject value){throw new AssertionError();}}');
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor{public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "follow-state";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("status",200);c.attr("data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("status",status);}}');
 shim('org/b3log/symphony/service/ApiV3FollowMgmtService','package org.b3log.symphony.service;public class ApiV3FollowMgmtService{public org.json.JSONObject set(org.json.JSONObject v,String id,int type,boolean enabled,String key){throw new AssertionError();}}');
 shim('org/b3log/symphony/service/ApiV3DiscoveryQueryService','package org.b3log.symphony.service;public class ApiV3DiscoveryQueryService{public org.json.JSONObject userRelations(String id,int p,int size,String kind){throw new AssertionError();}}');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService','package org.b3log.symphony.service;public class ApiV3TokenMgmtService{public org.json.JSONObject authenticate(String value){return null;}public static boolean hasScope(String scope,String required){return java.util.Arrays.asList(scope.split(" ")).contains(required);}}');
 let test='src/test/java/org/b3log/symphony/service/ApiV3FollowStateTest.java';
 if (mysql) {
  const f = path.join(dir, 'ApiV3FollowStateTest.java');
  fs.writeFileSync(f, fs.readFileSync(test, 'utf8')
   .replace('DriverManager.getConnection("jdbc:sqlite::memory:")', 'DriverManager.getConnection(System.getProperty("rhythm.follow.mysqlUrl"))')
   .replaceAll('oId TEXT PRIMARY KEY', 'oId VARCHAR(19) PRIMARY KEY'));
  test = f;
 }
 const sources = [
  'model/Follow',
  'repository/FollowRepository',
  'util/ApiV3Exception',
  'util/ApiV3Requests',
  'service/ApiV3FollowQueryService',
  'processor/ApiV3FollowProcessor',
  'processor/middleware/ApiV3AuthMidware',
  'processor/middleware/ApiV3WriteMidware',
  'util/ApiV3WriteInputs',
  'util/ApiV3Json'
 ];
 run(home+'/bin/javac',['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',dir,...shims,...sources.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),test]);
 const opts=[];
 if(mysql){
  function docker(args) {
   const r = spawnSync('docker', args, { encoding: 'utf8', timeout: 20000 });
   if (r.status !== 0) throw Error(r.stderr);
   return r.stdout.trim()
  }
  container = 'rhythm-v3-follow-state-' + Date.now();
  docker(['run', '-d', '--rm', '--pull', 'never', '--memory', '768m', '--name', container,
   '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', '-e', 'MYSQL_ROOT_HOST=%', '-p', '127.0.0.1::3306',
   'mysql:8.4', '--skip-log-bin', '--performance-schema=OFF', '--innodb-buffer-pool-size=32M']);
  const end = Date.now() + 65000;
  while (true) {
   const ping = spawnSync('docker', ['exec', container, 'mysqladmin', '-uroot', 'ping'], { encoding: 'utf8', timeout: 4000 });
   if (ping.status === 0 && docker(['exec', container, 'cat', '/proc/1/comm']) === 'mysqld') break;
   if (Date.now() > end) throw Error('MySQL startup timeout');
   await new Promise(r => setTimeout(r, 250));
  }
  const bound = docker(['port', container, '3306/tcp']);
  if (!/^127\.0\.0\.1:[0-9]+$/.test(bound) || bound.endsWith(':3306')) throw Error('Unsafe MySQL port');
  docker(['exec', container, 'mysql', '-uroot', '-e', 'CREATE DATABASE follow_state CHARACTER SET utf8mb4;']);
  opts.push('-Drhythm.follow.mysqlUrl=jdbc:mysql://'+bound+'/follow_state?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=20000');
 }
 run(home+'/bin/java',[...opts,'-cp' ,[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3FollowStateTest']);
} finally {
 if (container) {
  const r = spawnSync('docker', ['rm', '-f', container], { encoding: 'utf8', timeout: 20000 });
  if (r.status !== 0) throw Error(r.stderr);
  console.log('Temporary follow-state MySQL removed');
 }
 fs.rmSync(dir, { recursive: true, force: true })
}
