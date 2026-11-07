#!/usr/bin/env node
/** Isolated real account-pair lock pool and SQLite adapters. No Maven, application DB or HTTP. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const testng=process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`;
const sqlite=process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
const suffix=process.platform==='win32'?'.exe':'',java=path.join(home,'bin',`java${suffix}`),javac=path.join(home,'bin',`javac${suffix}`);
for(const file of [java,javac,testng,sqlite])if(!fs.existsSync(file))throw new Error('Configure Java 25, TestNG and SQLite paths');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-transfer-lock-test-'));
function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit',timeout:60000});if(r.error)throw r.error;if(r.status!==0)throw new Error('Account pair lock tests failed');}
try{
 const classes=path.join(work,'classes'),cp=[testng,sqlite].join(path.delimiter);
 run(javac,['--release','25','-proc:none','-cp',cp,'-d',classes,'src/main/java/org/b3log/symphony/util/AccountPairLocks.java','src/test/java/org/b3log/symphony/util/AccountPairLocksTest.java']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.util.AccountPairLocksTest']);
}finally{fs.rmSync(work,{recursive:true,force:true});}
