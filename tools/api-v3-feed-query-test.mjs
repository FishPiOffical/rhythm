#!/usr/bin/env node
// 在 SQLite 中原样执行正式文章查询方法，DTO、仓储与分页使用适配器。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents';
const home=process.env.JAVA_HOME||ide+'/jbr/Contents/Home';
const cp=['src/main/resources/lib/latke-core.jar',ide+'/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar'].join(path.delimiter);
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-feed-query-'));
const source=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3DiscoveryQueryService.java','utf8');
const method=source.slice(source.indexOf('    public JSONObject articles('),source.indexOf('    public JSONObject targetUser(')).trim();
const records=source.slice(source.indexOf('    public record Following('),source.indexOf('    public JSONObject tags('));
if(!method.startsWith('public JSONObject articles(')||!method.endsWith('}'))throw Error('Review production method boundary');
function put(name, value) {
 const file = path.join(work, name + '.java');
 fs.writeFileSync(file, value);
 return file;
}

function run(command, args) {
 const result = spawnSync(command, args, { stdio: 'inherit' });
 if (result.error) throw result.error;
 if (result.status !== 0) throw Error('Feed query verification failed');
}
try{
 const symphonys=put('Symphonys','package org.b3log.symphony.util;public class Symphonys{public static final long STICK_ARTICLE_TIME=1800000L;public static final String[] RESERVED_TAGS={},WHITE_LIST_TAGS={},RESERVED_USER_NAMES={};}');
 const requests=put('ApiV3Requests','package org.b3log.symphony.util;import org.json.*;public class ApiV3Requests{public static JSONObject pagination(JSONArray list,int p,int size,long total){return new JSONObject().put("list",list).put("pagination",new JSONObject().put("p",p).put("size",size).put("total",total).put("pageCount",(total+size-1)/size));}}');
 const tagSource=fs.readFileSync('src/main/java/org/b3log/symphony/model/Tag.java','utf8');
 const constants=['TAG_TITLE_C_SANDBOX','TAG_STATUS_C_VALID'].map(name=>{const value=tagSource.match(new RegExp('public static final (?:String|int) '+name+' = [^;]+;'));if(!value)throw Error('Review tag constant '+name);return value[0];});
 const tag=put('Tag','package org.b3log.symphony.model;public class Tag{'+constants.join('')+'}');
 const fixture=fs.readFileSync('tools/fixtures/v3-feed-query.java.txt','utf8');
 const java=put('FeedQueryTest',fixture.replace('// PRODUCTION_RECORDS',records).replace('// PRODUCTION_METHOD',method));
 const models=['Article','Comment','Follow','UserExt'].map(name=>'src/main/java/org/b3log/symphony/model/'+name+'.java');
 run(path.join(home,'bin/javac'),['--release','25','-proc:none','-cp',cp,'-d',work,symphonys,requests,tag,java,...models,'src/main/java/org/b3log/symphony/util/ApiV3ContentSnapshots.java','src/main/java/org/b3log/symphony/util/ApiV3ArticlePolicy.java','src/main/java/org/b3log/symphony/util/ApiV3ProfileQueries.java','src/main/java/org/b3log/symphony/util/ApiV3ArticleStickState.java','src/main/java/org/b3log/symphony/util/ApiV3Exception.java']);
 run(path.join(home,'bin/java'),['-cp',[work,cp].join(path.delimiter),'FeedQueryTest']);
} finally {
 fs.rmSync(work, { recursive: true, force: true });
}
