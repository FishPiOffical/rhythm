import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
const home=process.env.JAVA_HOME||'/Applications/Android Studio.app/Contents/jbr/Contents/Home'
const dir=mkdtempSync(path.join(tmpdir(),'rhythm-app-login-protocol-'))
function run(bin,args){const result=spawnSync(path.join(home,'bin',bin),args,{encoding:'utf8'});process.stdout.write(result.stdout||'');process.stderr.write(result.stderr||'');if(result.error)throw result.error;if(result.status!==0)throw Error(`${bin} failed: ${result.status}`)}
try{run('javac',['--release','25','-proc:none','-d',dir,'src/main/java/org/b3log/symphony/util/ApiV3AppLoginProtocol.java','src/test/java/org/b3log/symphony/util/ApiV3AppLoginProtocolTest.java']);run('java',['-cp',dir,'org.b3log.symphony.util.ApiV3AppLoginProtocolTest'])}finally{rmSync(dir,{recursive:true,force:true})}
