#!/usr/bin/env node
/** Real signer, v3 grant service and legacy RhypicProcessor. User/config/request-error adapters only; no HTTP/DB/image-host calls. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const testng=process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,sqlite=process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`,latke='src/main/resources/lib/latke-core.jar';
const java=path.join(home,'bin','java'),javac=path.join(home,'bin','javac'),work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-upload-test-')),sources=[];
function shim(name,source){const file=path.join(work,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,source);sources.push(file);}
function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw new Error('Upload tests failed');}
try{
 shim('org/b3log/symphony/repository/UserRepository.java',`package org.b3log.symphony.repository; public class UserRepository {public String getName(){return "symphony_user";}public java.util.List<org.json.JSONObject> select(String sql,Object...args)throws org.b3log.latke.repository.RepositoryException{return org.b3log.symphony.service.ApiV3UploadTicketServiceTest.lookup(sql,args);}}`);
 shim('org/b3log/symphony/util/ApiV3Requests.java',`package org.b3log.symphony.util; public class ApiV3Requests {public static ApiV3Exception notFound(){return new ApiV3Exception(404,"not_found","unavailable");}}`);
 shim('org/b3log/symphony/processor/middleware/LoginCheckMidware.java',`package org.b3log.symphony.processor.middleware; public class LoginCheckMidware {public void handle(org.b3log.latke.http.RequestContext context){throw new UnsupportedOperationException("not a HTTP test");}}`);
 shim('org/b3log/symphony/util/Symphonys.java',`package org.b3log.symphony.util; public class Symphonys {public static final String[] RESERVED_USER_NAMES={};public static boolean RHPIC_ENABLED=true;public static String RHPIC_UPLOAD_URL="https://pic.example.test",RHPIC_TICKET_PRIVATE_KEY="",RHPIC_INTERNAL_URL="http://internal.invalid",RHPIC_INTERNAL_TOKEN="INTERNAL-PRIVATE";}`);
 const real=['model/Common','model/UserExt','util/StatusCodes','util/Results','util/ApiV3Exception','util/ApiV3Json','util/ApiV3WriteInputs','util/RhypicUploadTickets','util/ApiV3UploadTargets','processor/RhypicProcessor','service/ApiV3UploadTicketService'];
 const cp=[latke,testng,sqlite].join(path.delimiter),classes=path.join(work,'classes');
 run(javac,['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',classes,...sources,...real.map(n=>`src/main/java/org/b3log/symphony/${n}.java`),'src/test/java/org/b3log/symphony/service/ApiV3UploadTicketServiceTest.java']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3UploadTicketServiceTest']);
}finally{fs.rmSync(work,{recursive:true,force:true});}
