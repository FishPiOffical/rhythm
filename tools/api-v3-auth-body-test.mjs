#!/usr/bin/env node
// Real controller methods, raw Netty + Latke.parseForm; service counters only, no server or database.
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
const home=process.env.JAVA_HOME||'/Applications/Android Studio.app/Contents/jbr/Contents/Home',dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-auth-body-')),cp='src/main/resources/lib/latke-core.jar';
function method(source,key){const start=source.indexOf(key);if(start<0)throw Error('Missing '+key);let depth=0;for(let i=source.indexOf('{',start);i<source.length;i++){if(source[i]==='{')depth++;if(source[i]==='}'&&--depth===0)return source.slice(start,i+1);}throw Error(key);}
function write(name,body){const p=path.join(dir,name+'.java');fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,body);return p;}
function run(bin,args){const r=spawnSync(bin,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status)throw Error('Auth body regression failed');}
try{
 const before=fs.readFileSync('src/main/java/org/b3log/symphony/processor/BeforeRequestHandler.java','utf8');
 const beforeMethod=method(before,'public void handle(');
 if(!beforeMethod.trimStart().startsWith('public void handle(final RequestContext context) {\n        ApiV3RefreshCookies.discardFrameworkEcho(context);'))throw Error('Refresh echo hook must precede all early returns');
 const source=fs.readFileSync('src/main/java/org/b3log/symphony/processor/ApiV3Processor.java','utf8');
 for(const endpoint of ['login','refresh','logout'])if(!source.includes(`Dispatcher.post("/api/v3/auth/${endpoint}", processor::${endpoint}, anonymous::handle);`))throw Error('Review anonymous auth gate');
 const methods=['public void login(','public void refresh(','public void logout(','private static JSONObject authenticationBody(','private static String cookie(','private static boolean isSameOrigin(','private static boolean matchesHost(','private static void setRefreshCookie(','private static void clearRefreshCookie('].map(key=>method(source,key)).join('\n');
 const constants=['MAX_AUTH_BODY_BYTES','MAX_LOGIN_FIELD'].map(name=>source.match(new RegExp(`private static final int ${name} = [^;]+;`))?.[0]||(()=>{throw Error(name)})()).join('\n');
 const processor=write('org/b3log/symphony/processor/ApiV3Processor',`package org.b3log.symphony.processor;
 import org.b3log.latke.http.*;import org.b3log.latke.Keys;import org.b3log.latke.model.User;import org.b3log.latke.service.ServiceException;import org.b3log.latke.util.Requests;import org.b3log.symphony.util.*;import org.apache.commons.lang.StringUtils;import org.json.JSONObject;import java.util.*;import java.net.URI;
 public class ApiV3Processor {${constants} private static final String REFRESH_COOKIE=ApiV3RefreshCookies.NAME;
 public static final AuthBodyTest.Tokens apiV3TokenMgmtService=new AuthBodyTest.Tokens();public static final LoginProcessor loginProcessor=new LoginProcessor();
 public static void success(RequestContext c,Object data){c.attr("status",200);c.attr("data",data);}public static void error(RequestContext c,int status,String code,String message){c.attr("status",status);c.attr("code",code);c.getResponse().setHeader("Cache-Control","no-store");}private static void audit(RequestContext c,String action,String result,String user){}${methods}}
 `);
 const login=write('org/b3log/symphony/processor/LoginProcessor',`package org.b3log.symphony.processor;import org.json.JSONObject;import org.b3log.latke.service.ServiceException;public class LoginProcessor{public int calls;public String name,password,mfa;public record LoginResult(boolean success,String code,String message,JSONObject user){}public LoginResult authenticate(String n,String p,String m,String ip)throws ServiceException{calls++;name=n;password=p;mfa=m;return new LoginResult(true,"","",new JSONObject().put("oId","1"));}}`);
 const real=['util/ApiV3Exception','util/ApiV3Json','util/ApiV3WriteInputs','util/ApiV3RefreshCookies','util/ApiV3TokenUtil'];
 run(home+'/bin/javac',['--release','25','-proc:none','-cp',cp,'-d',dir,processor,login,'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java',...real.map(n=>'src/main/java/org/b3log/symphony/'+n+'.java'),'src/test/java/org/b3log/symphony/processor/AuthBodyTest.java','src/test/java/org/b3log/symphony/processor/RefreshCookieEchoTest.java']);
 run(home+'/bin/java',['-cp',dir+path.delimiter+cp,'org.b3log.symphony.processor.AuthBodyTest']);
 run(home+'/bin/java',['-cp',dir+path.delimiter+cp,'org.b3log.symphony.processor.RefreshCookieEchoTest']);
}finally{fs.rmSync(dir,{recursive:true,force:true});}
