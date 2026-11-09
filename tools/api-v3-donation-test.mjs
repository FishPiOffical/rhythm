#!/usr/bin/env node
/** 在 Java 25 上运行实际捐助链接、路由与鉴权测试，令牌存储和响应封装由夹具适配。 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';

const home = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const cp = 'src/main/resources/lib/latke-core.jar',
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-donation-')),
  shims = [];

function shim(name, body) {
  const p = path.join(dir, name + '.java');
  fs.mkdirSync(path.dirname(p), {recursive: true});
  fs.writeFileSync(p, body);
  shims.push(p);
}

function run(bin, args) {
  const r = spawnSync(bin, args, {stdio: 'inherit'});
  if (r.error) throw r.error;
  if (r.status !== 0) throw Error('Donation-page tests failed');
}

try {
  // 在隔离夹具中复用真实权限比较，保持令牌存储替身与权限判定分离。
  const source = fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java', 'utf8');
  const scope = source.match(/public static boolean hasScope\([^]*?\n    }/);
  if (!scope) throw Error('Review actual scope implementation');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService','package org.b3log.symphony.service;import org.json.JSONObject;import java.util.*;import org.apache.commons.lang.StringUtils;public class ApiV3TokenMgmtService {public JSONObject authenticate(String token){return null;}'+scope[0]+'}');
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "donation-test";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String message){c.attr("test.status",status);c.attr("test.code",code);}}');
  const real = [
    'util/ApiV3DonationPage', 'util/ApiV3Requests', 'util/ApiV3Exception',
    'processor/ApiV3DonationProcessor', 'processor/middleware/ApiV3AuthMidware'
  ];
  run(path.join(home, 'bin/javac'), [
    '--release', '25', '-proc:none', '-cp', cp, '-d', dir, ...shims,
    ...real.map(p => 'src/main/java/org/b3log/symphony/' + p + '.java'),
    'src/test/java/org/b3log/symphony/processor/ApiV3DonationTest.java'
  ]);
  run(path.join(home, 'bin/java'), ['-cp', [dir, cp].join(path.delimiter), 'org.b3log.symphony.processor.ApiV3DonationTest']);
} finally {
  fs.rmSync(dir, {recursive: true, force: true});
}
