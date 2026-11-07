#!/usr/bin/env node
/** Actual article/comment hooks and reaction service with separate SQLite readers; no application DB, MySQL locks or HTTP. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=['src/main/resources/lib/latke-core.jar',process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-article-realtime-')),shims=[];
function shim(name,source){const file=path.join(work,`${name}.java`);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,source);shims.push(file);}
function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw new Error('Article realtime tests failed');}
try{
 for(const [type,key] of [['Article','article'],['Comment','comment']])shim(`org/b3log/symphony/cache/${type}Cache`,`package org.b3log.symphony.cache; public class ${type}Cache {public org.json.JSONObject get${type}(String id){return null;}public void put${type}(org.json.JSONObject row){}public void remove${type}(String id){}}`);
 for(const [name,table]of [['User','user'],['ChatRoom','chat'],['Revision','revision'],['Option','option']])shim(`org/b3log/symphony/repository/${name}Repository`,`package org.b3log.symphony.repository; public class ${name}Repository extends org.b3log.latke.repository.AbstractRepository {public ${name}Repository(){super("${table}");}@Override public String getName(){return "${table}";}}`);
 shim('org/b3log/symphony/repository/NotificationRepository','package org.b3log.symphony.repository; public class NotificationRepository extends org.b3log.latke.repository.AbstractRepository {public NotificationRepository(){super("notification");}public void removeContentReferences(String id,boolean article,String table){}public void removeRewardReferences(String id,int type){}}');
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util; public class Symphonys {public static final String[] RESERVED_USER_NAMES={};}');
 const sources=['model/ApiV3ChatEvent','repository/ApiV3ChatStateRepository','repository/ApiV3ChatEventRepository','util/ApiV3ChatTransaction','util/ApiV3Transactions','model/Article','model/Comment','model/Reaction','model/Reward','model/Vote','model/Revision','model/Option','model/UserExt','model/Common','model/LongArticleColumn','model/ApiV3ArticleRevision',
 'repository/ApiV3RevisionRepository','repository/ApiV3ArticleRevisionRepository','repository/ArticleRepository','repository/CommentRepository','repository/ReactionRepository','repository/RewardRepository','repository/VoteRepository',
 'util/JSONs','util/ArticleSubmissionPolicy','util/ArticlePublicationCaches','util/ApiV3ArticleEditorDto','util/CommentEditing','util/ReactionMutation','service/ReactionMgmtService'];
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',work,...shims,...sources.map(s=>`src/main/java/org/b3log/symphony/${s}.java`),'src/test/java/org/b3log/symphony/repository/ApiV3ArticleRevisionTest.java']);
 run(path.join(home,'bin','java'),['-cp',[work,cp].join(path.delimiter),'org.b3log.symphony.repository.ApiV3ArticleRevisionTest']);
}finally{fs.rmSync(work,{recursive:true,force:true});}
