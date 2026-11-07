#!/usr/bin/env node
// Render actual PC/mobile templates; browser origin behavior is verified separately.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents';
const home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp=['src/main/resources/lib/latke-core.jar',`${ide}/plugins/testng/lib/testng.jar`].join(path.delimiter);
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-oauth-template-'));
function run(command,args){const r=spawnSync(command,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status)throw Error('OAuth template regression failed');}
try{
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',work,'src/test/java/org/b3log/symphony/util/ApiV3OAuthTemplateTest.java']);
 run(path.join(home,'bin','java'),['-cp',[work,cp].join(path.delimiter),'org.b3log.symphony.util.ApiV3OAuthTemplateTest']);
}finally{fs.rmSync(work,{recursive:true,force:true});}
