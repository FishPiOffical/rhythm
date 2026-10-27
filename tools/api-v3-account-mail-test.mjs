#!/usr/bin/env node
// Compile the actual account-mail methods with fake external transports. Never sends mail.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const home=process.env.JAVA_HOME||'/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-account-mail-'));
const source=fs.readFileSync('src/main/java/org/b3log/symphony/util/Mails.java','utf8');
function method(signature){const start=source.indexOf(signature);if(start<0)throw Error(signature);let depth=0;for(let p=source.indexOf('{',start);p<source.length;p++){if(source[p]==='{')depth++;if(source[p]==='}'&&--depth===0)return source.slice(start,p+1);}throw Error('unclosed method');}
const files=[];
function put(name,body){const file=path.join(dir,name+'.java');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,body);files.push(file);}
function run(exe,args){const r=spawnSync(exe,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw Error('Account mail tests failed');}
try{
put('org/b3log/symphony/util/Mails',`package org.b3log.symphony.util;
import java.util.*;import java.text.SimpleDateFormat;import org.apache.commons.lang.StringUtils;import org.b3log.latke.util.Crypts;import org.b3log.latke.util.Strings;import org.b3log.latke.util.URLs;import jodd.http.*;
public class Mails {static final Map<String,String> DOMAIN_CHANNEL=new HashMap<>();static final Log LOGGER=new Log();static class Log{List<String> lines=new ArrayList<>();void warn(String text){lines.add(text);}void debug(String text){lines.add(text);}}
${method('public static boolean sendAccountBindingCode(')}
${method('private static boolean aliSendHtmlAccepted(')}
${method('private static String percentEncode(')}
${method('private static String getISO8601Time(')}
}
class MailSender{static String username="user",password="pass";static int calls;static boolean fail;static final MailSender INSTANCE=new MailSender();static MailSender getInstance(){return INSTANCE;}void sendAccountMessage(String[] to,String subject,String html)throws Exception{calls++;if(fail)throw new Exception("private");if(to.length!=1||!html.contains("123456"))throw new AssertionError();}}
`);
put('org/b3log/symphony/util/Symphonys',`package org.b3log.symphony.util;public class Symphonys {public static String MAIL_CHANNEL="local",MAIL_ALIYUN_AK="ak",MAIL_ALIYUN_SK="sk",MAIL_ALIYUN_FROM="from@example.com",MAIL_SENDCLOUD_API_USER="api",MAIL_SENDCLOUD_API_KEY="key",MAIL_SENDCLOUD_FROM="from@example.com",USER_AGENT_BOT="fixture";}`);
put('org/b3log/symphony/util/Common',`package org.b3log.symphony.util;public class Common{public static final String USER_AGENT="User-Agent";}`);
put('jodd/http/HttpRequest',`package jodd.http;import java.util.*;public class HttpRequest {public static String endpoint;public static Map<String,Object> captured;public static boolean fail;public static HttpRequest post(String url){endpoint=url;return new HttpRequest();}public HttpRequest form(Map<String,Object> data){captured=data;return this;}public HttpRequest header(String k,String v){return this;}public HttpRequest connectionTimeout(int n){return this;}public HttpRequest timeout(int n){return this;}public HttpResponse send(){if(fail)throw new IllegalStateException("private");return new HttpResponse();}}`);
put('jodd/http/HttpResponse',`package jodd.http;public class HttpResponse {public static int status=200,closed;public static String body="{}";public HttpResponse charset(String v){return this;}public int statusCode(){return status;}public String bodyText(){return body;}public void close(){closed++;}}`);
const cp='src/main/resources/lib/latke-core.jar';
run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',dir,...files,'src/main/java/org/b3log/symphony/util/ApiV3Exception.java','src/main/java/org/b3log/symphony/util/ApiV3Json.java','src/main/java/org/b3log/symphony/util/AccountMailResponses.java','src/test/java/org/b3log/symphony/util/AccountMailTest.java']);
run(path.join(home,'bin','java'),['-cp',dir+path.delimiter+cp,'org.b3log.symphony.util.AccountMailTest']);
}finally{fs.rmSync(dir,{recursive:true,force:true});}
