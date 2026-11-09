#!/usr/bin/env node
/** 测试实际安全证明、凭据撤销、SQL、严格 JDBC 根事务、鉴权及 Dispatcher。 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=['src/main/resources/lib/latke-core.jar',`${ide}/lib/intellij.libraries.caffeine.jar`,`${ide}/plugins/testng/lib/testng.jar`,`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-mfa-')),shims=[];
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
 if (r.status !== 0) throw Error('MFA tests failed');
}
try{
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys {public static final String[] RESERVED_USER_NAMES={};}');
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;public class UserRepository extends org.b3log.latke.repository.AbstractRepository {public UserRepository(){super("user");}public void evictSettingsCaches(String actor){}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService',`package org.b3log.symphony.service;import org.json.JSONObject;import org.apache.commons.lang.StringUtils;import java.util.*;public class ApiV3TokenMgmtService {public static final String WEBSITE_CLIENT="website_v3";public JSONObject authenticate(String value){return null;}${method(tokens,'public static boolean hasScope(')}}`);
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "fixture-security";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);}}');
 const real=['model/UserExt','model/Role','model/ApiV3Client','model/ApiV3Token','model/ApiV3AuthorizationRequest','model/ApiV3AuthorizationCode','model/AccountCredentialState','model/OpenIdRefreshToken','repository/ApiV3ClientRepository','repository/ApiV3TokenRepository','repository/ApiV3AuthorizationCodeRepository','repository/ApiV3AuthorizationRequestRepository','repository/AccountCredentialStateRepository','repository/OpenIdRefreshTokenRepository','util/ApiV3Requests','util/ApiV3Exception','util/ApiV3Json','util/ApiV3WriteInputs','util/ApiV3Transactions','util/ApiV3TransactionStatements','util/MultiFactorAuthenticator','util/ApiV3SecurityInputs','service/AccountCredentialMgmtService','service/ApiV3SecurityProofService','service/ApiV3SecuritySettingsService','processor/ApiV3SecurityProcessor','model/ApiV3Request','repository/ApiV3RequestRepository','repository/ApiV3MfaPreparationRepository','util/ApiV3MutationStore','util/ApiV3TokenUtil','util/ApiV3TransactionSavepoint','util/ApiV3MfaInputs','util/ApiV3MfaSecrets','service/ApiV3IdempotencyMgmtService','service/ApiV3MfaService','processor/ApiV3MfaProcessor','processor/middleware/ApiV3AuthMidware','processor/middleware/ApiV3WriteMidware'];
 run(path.join(home,'bin','javac'),['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',dir,...shims,...real.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),'src/test/java/org/b3log/symphony/service/ApiV3SecurityTest.java','src/test/java/org/b3log/symphony/service/ApiV3MfaTest.java']);
 run(path.join(home,'bin','java'),['-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3MfaTest']);
}finally{fs.rmSync(dir,{recursive:true,force:true});}
