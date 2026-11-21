#!/usr/bin/env node
/** 在隔离 SQLite 中验证公开专栏服务的真实 SQL 和 DTO，仓库、框架配置及 Markdown 使用适配器，无需连接真实数据库或运行 Maven。 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`,work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-public-column-'));
const cp=['src/main/resources/lib/latke-core.jar',process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`,process.env.API_V3_JSOUP_JAR||`${ide}/lib/intellij.libraries.jsoup.jar`,process.env.API_V3_ENCODER_JAR||path.join(os.homedir(),'.m2/repository/org/owasp/encoder/encoder/1.2.2/encoder-1.2.2.jar')].join(path.delimiter);
const shims = [];
function shim(name, source) {
  const f = path.join(work, name + '.java');
  fs.mkdirSync(path.dirname(f), {recursive: true});
  fs.writeFileSync(f, source);
  shims.push(f)
}

function run(cmd, args) {
  const r = spawnSync(cmd, args, {stdio: 'inherit'});
  if (r.error) throw r.error;
  if (r.status !== 0) throw Error('public column test failed')
}
try{
 for(const[name,table]of[['UserRepository','user'],['ArticleRepository','article']])shim('org/b3log/symphony/repository/'+name,`package org.b3log.symphony.repository;public class ${name} extends org.b3log.latke.repository.AbstractRepository{public ${name}(){super("${table}");}}`);
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys{public static final String[] RESERVED_USER_NAMES={};}');
 shim('org/b3log/symphony/util/Markdowns','package org.b3log.symphony.util;public class Markdowns{public static String toHTML(String v){return v;}}');
 shim('org/b3log/symphony/util/ApiV3Requests','package org.b3log.symphony.util;import org.json.*;public class ApiV3Requests{public static ApiV3Exception invalid(){return new ApiV3Exception(422,"invalid_request","bad input");}public static ApiV3Exception notFound(){return new ApiV3Exception(404,"not_found","not found");}public static JSONObject pagination(JSONArray l,int p,int s,long n){return new JSONObject().put("list",l).put("pagination",new JSONObject().put("p",p).put("size",s).put("total",n).put("pageCount",(n+s-1)/s));}}');
 const controller=fs.readFileSync('src/main/java/org/b3log/symphony/processor/ApiV3Processor.java','utf8'),routes=controller.split('\n').filter(s=>s.includes('Dispatcher.get("/api/v3/columns'));
 if(routes.length!==4||routes.some(s=>!s.includes('auth::optionalContent, anonymous::handle')))throw Error('all four existing routes must authenticate optional current Bearer and anonymous shield');
 for(const entry of ['publicColumns.list(ApiV3Requests.viewer(context)','publicColumns.detail(ApiV3Requests.viewer(context)','publicColumns.chapters(ApiV3Requests.viewer(context)'])if(!controller.includes(entry))throw Error('current viewer must reach SQL visibility filter');
 if(controller.includes('public, max-age=60')||controller.includes('longArticleColumnQueryService'))throw Error('legacy shared public cache/read delegate remains');
 const sandbox=fs.readFileSync('src/main/java/org/b3log/symphony/model/Tag.java','utf8').match(/TAG_TITLE_C_SANDBOX = "([^"]+)"/)[1];
 shim('org/b3log/symphony/model/Tag',`package org.b3log.symphony.model;public class Tag{public static final String TAG_TITLE_C_SANDBOX=${JSON.stringify(sandbox)};}`);
 const sources=['model/UserExt','model/Article','model/Comment','model/Role','model/Common','model/LongArticleColumn','repository/LongArticleColumnRepository','repository/LongArticleChapterRepository','util/ApiV3Exception','util/ApiV3ContentUtil','util/UserMentionCandidates','util/Escapes','service/ApiV3PublicColumnQueryService'];
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',work,...shims,...sources.map(n=>'src/main/java/org/b3log/symphony/'+n+'.java'),'src/test/java/org/b3log/symphony/service/ApiV3PublicColumnQueryTest.java']);
 run(path.join(home,'bin','java'),['-cp',[work,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3PublicColumnQueryTest']);
 if(!controller.includes('Dispatcher.get("/api/v3/articles/{articleId}/column", processor::articleColumn, auth::optionalContent, anonymous::handle)'))throw Error('article context must keep scope and anonymous protection');
 console.log('PASS public column 9 four unchanged routes retain anonymous shield, optional content scope, current viewer and no shared public cache (source wiring check)');
}finally{fs.rmSync(work,{recursive:true,force:true})}
