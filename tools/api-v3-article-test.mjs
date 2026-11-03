#!/usr/bin/env node
/** Isolated creation input, shared quotas, points/cache boundaries and submission-policy transition tests; no HTTP.
 * Uses Java 25, TestNG and SQLite. Transaction tests use an adapter, not the application database or points service.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const testng=process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`;
const sqlite=process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
const suffix=process.platform==='win32'?'.exe':'',java=path.join(home,'bin',`java${suffix}`),javac=path.join(home,'bin',`javac${suffix}`),latke='src/main/resources/lib/latke-core.jar';
for(const file of [java,javac,testng,sqlite,latke])if(!fs.existsSync(file))throw new Error('Configure Java 25, TestNG and SQLite paths');
const sources=['model/Article','model/Reward','model/Role','censor/CensorResult','model/ArticleDraft','model/LongArticleColumn','repository/LongArticleColumnRepository','repository/RewardRepository','service/ColumnCoverMgmtService',
  'util/ApiV3Exception','util/ApiV3Json','util/ApiV3WriteInputs','util/ApiV3ArticleInputs','util/ApiV3ArticleEditorDto','util/ApiV3ArticleEditInputs','util/ApiV3ArticleEditing','util/ArticlePublishPoints','util/ArticleEditPoints','util/ArticleRemovalPolicy','util/ArticlePublicationCaches','util/ArticleSubmissionPolicy','util/ArticleSubmission','util/ArticleValidation','util/ApiV3ArticleCreation','util/ArticleWriteQuota','util/ProfileWriteQuota','util/BreezemoonWriteQuota','util/FishGameWriteQuota'];
const limiter=['main/SimpleCurrentLimiter','control/MainControl','cache/MainCache','cache/pair/CachePair'];
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-article-test-'));
function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw new Error('Article preparation tests failed');}
try{
  const classes=path.join(work,'classes'),cp=[latke,testng,sqlite].join(path.delimiter);
  run(javac,['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',classes,...sources.map(s=>`src/main/java/org/b3log/symphony/${s}.java`),
    ...limiter.map(s=>`src/main/java/pers/adlered/simplecurrentlimiter/${s}.java`),
    ...['ApiV3ArticleInputsTest','WriteQuotaTest','ArticlePublishPointsTest','ArticlePublicationCachesTest','ArticleSubmissionPolicyTest','ArticleSubmissionTest','ArticleValidationTest','ApiV3ArticleCreationTest','ArticleEditPointsTest','ApiV3ArticleEditInputsTest','ApiV3ArticleEditingTest','ArticleThoughtTemplateTest','ArticleRemovalPolicyTest','ArticleRemovalSqlTest','ApiV3ArticleEditorDtoTest'].map(n=>`src/test/java/org/b3log/symphony/util/${n}.java`)]);
  for(const name of ['ApiV3ArticleInputsTest','WriteQuotaTest','ArticlePublishPointsTest','ArticlePublicationCachesTest','ArticleSubmissionPolicyTest','ArticleSubmissionTest','ArticleValidationTest','ApiV3ArticleCreationTest','ArticleEditPointsTest','ApiV3ArticleEditInputsTest','ApiV3ArticleEditingTest','ArticleThoughtTemplateTest','ArticleRemovalPolicyTest','ArticleRemovalSqlTest','ApiV3ArticleEditorDtoTest'])run(java,['-cp',[classes,cp].join(path.delimiter),`org.b3log.symphony.util.${name}`,...(name==='ArticleThoughtTemplateTest'?[path.join(os.tmpdir(),'rhythm-thought-template-fixtures.json')]:[])]);
}finally{fs.rmSync(work,{recursive:true,force:true});}
