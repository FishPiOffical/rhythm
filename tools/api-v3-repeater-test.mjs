#!/usr/bin/env node
/** Java25 isolated real SQL/controller/auth/routes; SQLite, Token, repository names and envelope adapters. */
import fs from 'node:fs';import path from 'node:path';import os from 'node:os';import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||(fs.existsSync(ide)?`${ide}/jbr/Contents/Home`:'');
// Shared captcha dependencies come from the built application; do not replace its protection with more shims.
if(!process.env.API_V3_RUNTIME_CLASSPATH)throw Error('Set API_V3_RUNTIME_CLASSPATH to the built application classes and dependency jars (Java 25).');
const cp=[process.env.API_V3_RUNTIME_CLASSPATH,process.env.API_V3_CAFFEINE_JAR,process.env.API_V3_TESTNG_JAR,process.env.API_V3_SQLITE_JAR].filter(Boolean).join(path.delimiter);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-repeater-')),shims=[];
function shim(name,body){const file=path.join(dir,name+'.java');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,body);shims.push(file);}
function method(source,signature){const begin=source.indexOf(signature);if(begin<0)throw Error(signature);let depth=0;for(let i=source.indexOf('{',begin);i<source.length;i++){if(source[i]==='{')depth++;if(source[i]==='}'&&--depth===0)return source.slice(begin,i+1);}throw Error('Unclosed method');}
function run(bin,args){const r=spawnSync(bin,args,{stdio:'inherit',timeout:60000});if(r.error)throw r.error;if(r.status!==0)throw Error('Repeater tests failed');}
try{
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;public class UserRepository extends org.b3log.latke.repository.AbstractRepository {public UserRepository(){super("user");}}');
 shim('org/b3log/symphony/repository/ArticleRepository','package org.b3log.symphony.repository;public class ArticleRepository extends org.b3log.latke.repository.AbstractRepository {public ArticleRepository(){super("article");}}');
 shim('org/b3log/symphony/service/OptionQueryService','package org.b3log.symphony.service;public class OptionQueryService {public org.json.JSONObject getOption(String key){return new org.json.JSONObject().put("optionValue","0");}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService',`package org.b3log.symphony.service;import org.json.JSONObject;import org.apache.commons.lang.StringUtils;import java.util.*;public class ApiV3TokenMgmtService {public JSONObject authenticate(String value){return null;}${method(tokens,'public static boolean hasScope(')}}`);
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "fixture-profession";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);}}');

 const real=['model/UserExt','model/Role','model/Article','model/Common','model/Option','model/RepeaterContent','model/RepeaterLike','repository/RepeaterContentRepository','repository/RepeaterLikeRepository','util/ApiV3Requests','util/ApiV3Exception','util/ApiV3RepeaterDto','util/AnonymousCaptchaShield','service/ApiV3RepeaterQueryService','processor/ApiV3RepeaterProcessor','processor/middleware/ApiV3AuthMidware','processor/middleware/AnonymousViewCheckMidware'];
 const javaBin=name=>home?path.join(home,'bin',name):name;
 run(javaBin('javac'),['--release','25','-encoding','UTF-8','-proc:none','-cp',cp,'-d',dir,...shims,...real.map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),'src/test/java/org/b3log/symphony/service/ApiV3RepeaterQueryTest.java']);
 run(javaBin('java'),['-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3RepeaterQueryTest']);
}finally{
 const resolved=path.resolve(dir),temporaryRoot=path.resolve(os.tmpdir());
 if(path.dirname(resolved)!==temporaryRoot||!path.basename(resolved).startsWith('rhythm-v3-repeater-'))throw Error('Unexpected temporary test directory');
 fs.rmSync(resolved,{recursive:true,force:true});
}
