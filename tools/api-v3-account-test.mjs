#!/usr/bin/env node
// 运行实际账号规则、控制器、模型常量、用户名校验和响应辅助方法。
// 设置、用户、权限存储及全局匿名中间件使用显式适配器。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=['src/main/resources/lib/latke-core.jar',process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`].join(path.delimiter);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-account-test-')),shims=[];
function shim(name, source) {
 const file = path.join(dir, `${name}.java`);
 fs.mkdirSync(path.dirname(file), { recursive: true });
 fs.writeFileSync(file, source);
 shims.push(file);
}

function method(signature) {
 const source = fs.readFileSync('src/main/java/org/b3log/symphony/processor/ApiV3Processor.java', 'utf8'), start = source.indexOf(signature);
 if (start < 0) throw new Error(signature);
 let depth = 0;
 for (let i = source.indexOf('{', start); i < source.length; i++) {
  if (source[i] === '{') depth++;
  if (source[i] === '}' && --depth === 0) return source.slice(start, i + 1);
 }
 throw new Error('Unclosed method');
}

function run(binary, args) {
 const r = spawnSync(binary, args, { stdio: 'inherit' });
 if (r.error) throw r.error;
 if (r.status !== 0) throw new Error('Account tests failed');
}
try{
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys {public static String get(String key){return "fixture-public-id";}public static final String[] RESERVED_USER_NAMES={"admin"};}');
 shim('org/b3log/symphony/repository/OptionRepository','package org.b3log.symphony.repository;public class OptionRepository extends org.b3log.latke.repository.AbstractRepository {public OptionRepository(){super("option");}}');
 shim('org/b3log/symphony/service/UserQueryService','package org.b3log.symphony.service;public class UserQueryService {public org.json.JSONObject getUserByName(String name){throw new UnsupportedOperationException();}}');
 shim('org/b3log/symphony/service/RoleQueryService','package org.b3log.symphony.service;public class RoleQueryService {public java.util.Map<String,org.json.JSONObject> getUserPermissionsGrantMap(String id){throw new UnsupportedOperationException();}}');
 shim('org/b3log/symphony/processor/middleware/AnonymousViewCheckMidware','package org.b3log.symphony.processor.middleware;public class AnonymousViewCheckMidware {public void handle(org.b3log.latke.http.RequestContext c){throw new UnsupportedOperationException("not an application captcha test");}}');
 shim('org/b3log/symphony/processor/ApiV3Processor',`package org.b3log.symphony.processor;import org.b3log.latke.http.RequestContext;import org.json.JSONObject;import java.util.UUID;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";${method('public static void success(')}${method('public static void error(')}${method('public static String requestId(')}}`);
 shim('org/b3log/symphony/service/AccountPasswordResetService','package org.b3log.symphony.service;public class AccountPasswordResetService {public org.json.JSONObject complete(String phone,String code,String password)throws Exception{throw new UnsupportedOperationException("Password reset tested by its SQL runner");}}');
 shim('org/b3log/symphony/util/PasswordResetQuota','package org.b3log.symphony.util;public class PasswordResetQuota {public static boolean access(String ip){throw new UnsupportedOperationException("Reset quota tested separately");}}');
 shim('org/b3log/symphony/service/AccountRegistrationService','package org.b3log.symphony.service;public class AccountRegistrationService {public org.json.JSONObject complete(String phone,String code,String password,int role,String mbti,String referral)throws Exception{throw new UnsupportedOperationException("Registration tested by its SQL runner");}}');
 shim('org/b3log/symphony/util/RegistrationVerificationQuota','package org.b3log.symphony.util;public class RegistrationVerificationQuota {public static boolean access(String ip){throw new UnsupportedOperationException("Quota tested separately");}}');
 shim('org/b3log/symphony/service/AccountPasswordResetRequestService','package org.b3log.symphony.service;public class AccountPasswordResetRequestService {public org.json.JSONObject request(String phone,String captcha,String ip)throws Exception{throw new UnsupportedOperationException("Request tested with SQL");}}');
 shim('org/b3log/symphony/service/AccountRegistrationRequestService','package org.b3log.symphony.service;public class AccountRegistrationRequestService {public org.json.JSONObject request(String name,String phone,String captcha,String invite,String referral,String language,String ip)throws Exception{throw new UnsupportedOperationException("Request tested with SQL");}}');
 const sources=['model/UserExt','model/Option','model/Permission','util/UserNameRules','util/ApiV3Exception','util/ApiV3Requests','util/ApiV3WriteInputs','util/ApiV3Json','util/ApiV3OAuthTransportLogFilter','service/AccountCaptchaService','service/ApiV3RegistrationPolicyService','processor/ApiV3AccountProcessor'];
 run(path.join(home,'bin','javac'),['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',dir,...shims,...sources.map(s=>`src/main/java/org/b3log/symphony/${s}.java`),'src/test/java/org/b3log/symphony/service/ApiV3RegistrationPolicyTest.java']);
 run(path.join(home,'bin','java'),['-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3RegistrationPolicyTest']);
}finally{fs.rmSync(dir,{recursive:true,force:true});}
