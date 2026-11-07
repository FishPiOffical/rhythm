#!/usr/bin/env node
/** Java25 real route/controller/auth/SQL/DTO on SQLite; token persistence and response envelope are adapted. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide = '/Applications/Android Studio.app/Contents';
const home = process.env.JAVA_HOME || `${ide}/jbr/Contents/Home`;
const cp = ['src/main/resources/lib/latke-core.jar', process.env.API_V3_JSOUP_JAR || `${ide}/lib/intellij.libraries.jsoup.jar`,
  process.env.API_V3_SQLITE_JAR || `${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`].join(path.delimiter);
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-membership-test-'));
const shims = [];
function shim(name, code) {
  const file = path.join(work, `${name}.java`); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, code); shims.push(file);
}
function method(source, signature) {
  const start = source.indexOf(signature); if (start < 0) throw new Error('Missing production method');
  let depth = 0; for (let i = source.indexOf('{', start); i < source.length; i++) { if (source[i] === '{') depth++; if (source[i] === '}' && --depth === 0) return source.slice(start, i + 1); }
  throw new Error('Unclosed production method');
}
function run(command, args) { const result = spawnSync(command, args, { stdio: 'inherit' }); if (result.error) throw result.error; if (result.status !== 0) throw new Error('Membership tests failed'); }
try {
  shim('org/b3log/symphony/util/Symphonys', 'package org.b3log.symphony.util;public class Symphonys {public static final String[] RESERVED_USER_NAMES={};}');
  shim('org/b3log/symphony/repository/UserRepository', 'package org.b3log.symphony.repository;public class UserRepository extends org.b3log.latke.repository.AbstractRepository {public UserRepository(){super("user");}}');
  const tokens = fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java', 'utf8');
  shim('org/b3log/symphony/service/ApiV3TokenMgmtService', `package org.b3log.symphony.service;import java.util.*;import org.apache.commons.lang.StringUtils;public class ApiV3TokenMgmtService {public org.json.JSONObject authenticate(String value){throw new UnsupportedOperationException("token persistence is adapted");}${method(tokens, 'public static boolean hasScope(')}}`);
  shim('org/b3log/symphony/processor/ApiV3Processor', 'package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "membership-test";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);}}');
  const real = ['model/UserExt', 'model/Membership', 'model/MembershipLevel', 'repository/MembershipRepository', 'repository/MembershipLevelRepository',
    'util/ApiV3Exception', 'util/ApiV3Json', 'util/ApiV3Requests', 'util/ApiV3MembershipDto', 'service/ApiV3MembershipQueryService',
    'processor/middleware/ApiV3AuthMidware', 'processor/ApiV3MembershipProcessor'];
  run(path.join(home, 'bin', 'javac'), ['--release', '25', '-proc:none', '-cp', cp, '-d', work, ...shims,
    ...real.map(name => `src/main/java/org/b3log/symphony/${name}.java`), 'src/test/java/org/b3log/symphony/processor/ApiV3MembershipTest.java']);
  run(path.join(home, 'bin', 'java'), ['-cp', [work, cp].join(path.delimiter), 'org.b3log.symphony.processor.ApiV3MembershipTest']);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
