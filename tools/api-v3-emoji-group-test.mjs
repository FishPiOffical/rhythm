#!/usr/bin/env node
/** 在隔离仓储连接上运行实际表情分组入口、写服务及 SQL。 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=['src/main/resources/lib/latke-core.jar',process.env.API_V3_JSOUP_JAR||`${ide}/lib/intellij.libraries.jsoup.jar`,process.env.API_V3_CAFFEINE_JAR||`${ide}/lib/intellij.libraries.caffeine.jar`,process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-emoji-group-')),shims=[];
function shim(name, body) {
 const file = path.join(dir, name + '.java');
 fs.mkdirSync(path.dirname(file), { recursive: true });
 fs.writeFileSync(file, body);
 shims.push(file);
}

function method(source, signature) {
 const begin = source.indexOf(signature);
 if (begin < 0) throw Error(signature);
 let depth = 0;
 for (let i = source.indexOf('{', begin); i < source.length; i++) {
  if (source[i] === '{') depth++;
  if (source[i] === '}' && --depth === 0) return source.slice(begin, i + 1);
 }
 throw Error('Unclosed method');
}

function run(bin, args) {
 const r = spawnSync(bin, args, { stdio: 'inherit' });
 if (r.error) throw r.error;
 if (r.status !== 0) throw Error('Emoji group tests failed');
}
try{
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys {public static final String[] RESERVED_USER_NAMES={};}');
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;public class UserRepository extends org.b3log.latke.repository.AbstractRepository {public UserRepository(){super("user");}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService',`package org.b3log.symphony.service;import org.json.JSONObject;import org.apache.commons.lang.StringUtils;import java.util.*;public class ApiV3TokenMgmtService {public JSONObject authenticate(String value){return null;}${method(tokens,'public static boolean hasScope(')}}`);
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "fixture-repeater";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);c.attr("test.message",msg);}}');
 shim('org/b3log/symphony/processor/ProfessionPrimaryRouteBindings','package org.b3log.symphony.processor;public class ProfessionPrimaryRouteBindings {public static void register(ApiV3ProfessionPrimaryProcessor p,org.b3log.symphony.processor.middleware.ApiV3AuthMidware a,org.b3log.symphony.processor.middleware.ApiV3WriteMidware w){}}');
 const routes=fs.readFileSync('src/main/java/org/b3log/symphony/processor/ApiV3EmojiGroupProcessor.java','utf8').split('\n').filter(l=>l.includes('Dispatcher.post(')||l.includes('Dispatcher.put(')||l.includes('Dispatcher.delete('));
 if(routes.length!==3||routes.some(l=>!l.includes('auth::settingsWrite, writes::handle')))throw Error('Review route gates');
 shim('org/b3log/symphony/processor/EmojiGroupRouteBindings',`package org.b3log.symphony.processor;import org.b3log.latke.http.Dispatcher;import org.b3log.symphony.processor.middleware.*;public class EmojiGroupRouteBindings {public static void register(ApiV3EmojiGroupProcessor p,ApiV3AuthMidware auth,ApiV3WriteMidware writes){${routes.join('\n')}}}`);
 const real=['model/UserExt','model/Role','model/Profession','model/UserProfessionPrivacy','model/ApiV3Request','repository/ProfessionRepository','repository/UserProfessionPrivacyRepository','repository/ApiV3RequestRepository','util/ApiV3Requests','util/ApiV3Exception','util/ApiV3WriteInputs','util/ApiV3Json','util/ApiV3TokenUtil','util/ApiV3MutationStore','util/ApiV3Transactions','util/ApiV3TransactionSavepoint','util/ApiV3TransactionStatements','service/ApiV3IdempotencyMgmtService','service/ApiV3ProfessionPrimaryService','processor/ApiV3ProfessionPrimaryProcessor','model/EmojiGroup','model/EmojiGroupItem','repository/EmojiGroupRepository','repository/EmojiGroupItemRepository','util/ApiV3EmojiGroupInputs','service/ApiV3EmojiGroupService','processor/ApiV3EmojiGroupProcessor','processor/middleware/ApiV3AuthMidware','processor/middleware/ApiV3WriteMidware'];
 run(path.join(home,'bin','javac'),['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',dir,...shims,...real.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),]);
}finally{fs.rmSync(dir,{recursive:true,force:true});}
