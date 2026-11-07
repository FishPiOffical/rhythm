#!/usr/bin/env node
/** Actual restriction SQL, confirmation and repeat limiter; isolated SQLite, no app DB or HTTP. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=['src/main/resources/lib/latke-core.jar',process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-chat-admission-'));
function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw new Error('Chat admission tests failed');}
try{
 const real=['util/ApiV3Exception','util/BagMutation','util/ChatMessageAbuseState','util/ChatMessageRestrictions','service/ChatMessageRestrictionQueryService','repository/CloudRepository'];
 const limiter=['main/SimpleCurrentLimiter','control/MainControl','cache/MainCache','cache/pair/CachePair'];
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',work,...real.map(s=>`src/main/java/org/b3log/symphony/${s}.java`),...limiter.map(s=>`src/main/java/pers/adlered/simplecurrentlimiter/${s}.java`),'src/test/java/org/b3log/symphony/util/ChatMessageAdmissionTest.java']);
 run(path.join(home,'bin','java'),['-cp',[work,cp].join(path.delimiter),'org.b3log.symphony.util.ChatMessageAdmissionTest']);
}finally{fs.rmSync(work,{recursive:true,force:true});}
