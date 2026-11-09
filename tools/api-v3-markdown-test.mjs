#!/usr/bin/env node
// 使用 Java 25 测试实际 HTTP、鉴权、原始 JSON 及正式私有解析和清洗方法。
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`,jars=process.env.API_V3_MARKDOWN_JARS||'/tmp/rhythm-v3-markdown-jars';
const deps=['flexmark','flexmark-formatter','flexmark-util','flexmark-ext-autolink','flexmark-ext-gfm-strikethrough','flexmark-ext-gfm-tasklist','flexmark-ext-tables','autolink'].map(n=>path.join(jars,n+'.jar'));
for(const p of deps)if(!fs.existsSync(p))throw Error('Supply pom.xml flexmark 0.50.40 and autolink 0.6.0 jars in API_V3_MARKDOWN_JARS: '+p);
const cp=[...deps,`${ide}/lib/intellij.libraries.jsoup.jar`,`${ide}/lib/intellij.libraries.caffeine.jar`,'src/main/resources/lib/latke-core.jar'].join(path.delimiter),dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-markdown-')),shims=[];
function shim(name, body) {
 const p = path.join(dir, name + '.java');
 fs.mkdirSync(path.dirname(p), { recursive: true });
 fs.writeFileSync(p, body);
 shims.push(p)
}

function method(s, key) {
 const begin = s.indexOf(key);
 if (begin < 0) throw Error(key);
 let depth = 0;
 for (let i = s.indexOf('{', begin); i < s.length; i++) {
  if (s[i] === '{') depth++;
  if (s[i] === '}' && --depth === 0) return s.slice(begin, i + 1)
 }
 throw Error(key)
}

function run(bin, args) {
 const r = spawnSync(bin, args, { stdio: 'inherit' });
 if (r.error) throw r.error;
 if (r.status) throw Error('Markdown test failed')
}
try{
 const token=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService','package org.b3log.symphony.service;import org.json.JSONObject;import java.util.*;import org.apache.commons.lang.StringUtils;public class ApiV3TokenMgmtService{public JSONObject authenticate(String token){return null;}'+method(token,'public static boolean hasScope(')+'}');
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor{public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "preview-test";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("status",200);c.attr("data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("status",status);c.attr("code",code);}}');
 // 表情目录替换为原样返回的适配器，解析选项和清洗器按源码原样提取。
 shim('org/b3log/symphony/util/Emotions','package org.b3log.symphony.util;public class Emotions{public static String convert(String text){return text;}}');
 const md=fs.readFileSync('src/main/java/org/b3log/symphony/util/Markdowns.java','utf8'),fields=md.slice(md.indexOf('private static final DataHolder OPTIONS'),md.indexOf('/**\n     * Whether Lute'));
 shim('org/b3log/symphony/util/Markdowns','package org.b3log.symphony.util;import java.util.*;import org.apache.commons.lang.StringUtils;import org.jsoup.*;import org.jsoup.nodes.*;import org.jsoup.safety.*;import org.b3log.latke.Latkes;import com.vladsch.flexmark.ext.autolink.AutolinkExtension;import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;import com.vladsch.flexmark.ext.tables.TablesExtension;import com.vladsch.flexmark.html.HtmlRenderer;import com.vladsch.flexmark.util.data.*;public class Markdowns{'+fields+method(md,'public static String toPrivateHTML(')+method(md,'private static String toHtmlByFlexmark(')+'}');
 const real=['util/ApiV3Exception','util/ApiV3Requests','util/ApiV3Json','util/ApiV3WriteInputs','processor/ApiV3MarkdownProcessor','processor/middleware/ApiV3AuthMidware'];
 run(home+'/bin/javac',['--release','25','-proc:none','-cp',cp,'-d',dir,...shims,'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java',...real.map(n=>'src/main/java/org/b3log/symphony/'+n+'.java'),'src/test/java/org/b3log/symphony/processor/ApiV3MarkdownTest.java']);
 run(home+'/bin/java',['-cp',dir+path.delimiter+cp,'org.b3log.symphony.processor.ApiV3MarkdownTest']);
}finally{fs.rmSync(dir,{recursive:true,force:true})}
