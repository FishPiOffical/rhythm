#!/usr/bin/env node
/** Real medal-image service SQL/DTO on isolated SQLite; repository/framework configuration and Markdown adapters. No live DB or Maven. */
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import{spawnSync}from'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`,work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-medal-image-'));
const cp=['src/main/resources/lib/latke-core.jar',process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`,process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`,process.env.API_V3_JSOUP_JAR||`${ide}/lib/intellij.libraries.jsoup.jar`,process.env.API_V3_ENCODER_JAR||path.join(os.homedir(),'.m2/repository/org/owasp/encoder/encoder/1.2.2/encoder-1.2.2.jar')].join(path.delimiter);
const shims=[];function shim(name,source){const f=path.join(work,name+'.java');fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,source);shims.push(f)}function run(cmd,args){const r=spawnSync(cmd,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw Error('medal image test failed')}
try{
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;public class UserRepository extends org.b3log.latke.repository.AbstractRepository{public UserRepository(){super("user");}}');
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys{public static String get(String k){return "http://127.0.0.1:3001/gen";}}');
 shim('org/b3log/symphony/util/ApiV3Requests','package org.b3log.symphony.util;public class ApiV3Requests{public static ApiV3Exception invalid(){return new ApiV3Exception(422,"invalid_request","bad");}public static ApiV3Exception notFound(){return new ApiV3Exception(404,"not_found","missing");}}');
 const source=fs.readFileSync('src/main/java/org/b3log/symphony/processor/ApiV3MedalImageProcessor.java','utf8');
 for(const route of ['/api/v3/me/medals/image','/api/v3/users/{userName}/medals/image'])if(!source.includes('Dispatcher.get("'+route+'",')||!source.includes('auth::profileRead'))throw Error('Missing route');
 if(!fs.readFileSync('src/main/java/org/b3log/symphony/processor/ApiV3Processor.java','utf8').includes('ApiV3MedalImageProcessor.register();'))throw Error('Missing root');
 const sources=['repository/MedalRepository','repository/UserMedalRepository','util/ApiV3Exception','util/ApiV3MedalSvg','service/ApiV3MedalImageQueryService','service/ApiV3MedalImageService'];
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',work,...shims,...sources.map(n=>'src/main/java/org/b3log/symphony/'+n+'.java'),'src/test/java/org/b3log/symphony/service/ApiV3MedalImageTest.java']);
 run(path.join(home,'bin','java'),['-cp',[work,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3MedalImageTest']);
 console.log('PASS medal image 8 two profile.read routes and root source wiring');
}finally{fs.rmSync(work,{recursive:true,force:true})}
