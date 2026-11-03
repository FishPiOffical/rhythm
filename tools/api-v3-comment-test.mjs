#!/usr/bin/env node
/** Isolated shared comment validation/quota checks. No Maven, application requests or output artifacts. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const testng=process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`;
const sqlite=process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
const suffix=process.platform==='win32'?'.exe':'',java=path.join(home,'bin',`java${suffix}`),javac=path.join(home,'bin',`javac${suffix}`),latke='src/main/resources/lib/latke-core.jar';
for(const file of [java,javac,testng,sqlite,latke])if(!fs.existsSync(file))throw new Error('Configure Java 25 and TestNG paths');
const sources=['model/Comment','model/Role','model/UserExt','util/ApiV3ContentUtil','util/ApiV3CommentTarget','util/CommentRealtimeVisibility','util/ApiV3ParagraphQueries','util/ApiV3ParagraphDto','censor/CensorResult','util/CommentValidation','util/CommentWriteQuota','util/CommentPublishPoints','model/Article','model/Common','util/ArticlePublicationCaches','util/CommentEditing','util/ApiV3CommentInputs','util/ApiV3WriteInputs','util/ApiV3Json','util/ApiV3Exception','util/ApiV3ArticlePolicy'];
const limiter=['main/SimpleCurrentLimiter','control/MainControl','cache/MainCache','cache/pair/CachePair'];
const suites=['ApiV3ArticlePolicyTest','CommentValidationTest','CommentWriteQuotaTest','ApiV3CommentInputsTest','ApiV3CommentPublicationTest','ApiV3ParagraphReadTest','CommentEditingTest','CommentPublishPointsTest'];
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-comment-test-'));
function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw new Error('Comment preparation tests failed');}
try{
 // UserExt's reserved-name configuration is unrelated to these visibility tests.
 const shim=path.join(work,'Symphonys.java');
 fs.writeFileSync(shim,'package org.b3log.symphony.util; public final class Symphonys { public static final String[] RESERVED_USER_NAMES = {}; }');
 const classes=path.join(work,'classes'),cp=[latke,testng,sqlite].join(path.delimiter);
 run(javac,['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',classes,shim,...sources.map(s=>`src/main/java/org/b3log/symphony/${s}.java`),...limiter.map(s=>`src/main/java/pers/adlered/simplecurrentlimiter/${s}.java`),...suites.map(s=>`src/test/java/org/b3log/symphony/util/${s}.java`),'src/test/java/org/b3log/symphony/util/ApiV3ContentUtilTest.java']);
 for(const suite of suites)run(java,['-cp',[classes,cp].join(path.delimiter),`org.b3log.symphony.util.${suite}`]);
}finally{fs.rmSync(work,{recursive:true,force:true});}
