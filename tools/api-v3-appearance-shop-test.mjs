#!/usr/bin/env node
// 使用已编译的工程依赖作为底座，再编译本功能真实源码；不启动服务或连接真实数据库。
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
const dependencies = process.env.API_V3_TEST_CLASSPATH
if (!dependencies) throw Error('请通过 API_V3_TEST_CLASSPATH 提供 IDEA 编译输出和工程依赖（含 SQLite、TestNG）')
const home = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-appearance-shop-'))
function run(name, args) {
  const result = spawnSync(path.join(home, 'bin', name), args, { stdio: 'inherit' })
  if (result.error) throw result.error
  if (result.status !== 0) throw Error(`${name} 校验失败`)
}
try {
  const files = [
    'model/Pointtransfer', 'util/ApiV3AppearanceCatalog', 'util/ApiV3ChatAppearance', 'util/ApiV3RealtimeSessions',
    'repository/AppearanceProductRepository', 'repository/AppearanceEntitlementRepository', 'repository/AppearancePurchaseRepository',
    'service/ApiV3AppearanceShopService', 'service/ApiV3ChatAppearanceService', 'service/ApiV3RealtimeService',
    'service/PointtransferMgmtService', 'service/PointtransferQueryService', 'processor/ApiV3ChatAppearanceProcessor',
  ].map(name => `src/main/java/org/b3log/symphony/${name}.java`)
  const tests = ['ApiV3SecurityTest','ApiV3IdentityApplicationTest','ApiV3QqSettingsTest','ApiV3ChatAppearanceTest','ApiV3AppearanceShopTest','ApiV3SocketTicketServiceTest','ApiV3RealtimeSessionsTest']
  run('javac', ['--release','25','-proc:none','-encoding','UTF-8','-cp',dependencies,'-d',dir,...files,...tests.map(name => `src/test/java/org/b3log/symphony/service/${name}.java`)])
  // 仅替换 HTTP 输出端口，使测试可断言状态；鉴权、SQL、事务和实时注册表使用真实代码。
  const shim = path.join(dir, 'ApiV3Processor.java')
  fs.writeFileSync(shim, `package org.b3log.symphony.processor;
public class ApiV3Processor {
    public static final String IDENTITY = "apiV3Identity";
    public static String requestId(org.b3log.latke.http.RequestContext c) { return "test-shop"; }
    public static void success(org.b3log.latke.http.RequestContext c, Object data) { c.attr("test.status", 200); c.attr("test.data", data); }
    public static void error(org.b3log.latke.http.RequestContext c, int status, String code, String msg) { c.attr("test.status", status); c.attr("test.code", code); }
}`)
  run('javac',['--release','25','-cp',dependencies,'-d',dir,shim])
  for (const name of ['ApiV3AppearanceShopTest','ApiV3ChatAppearanceTest','ApiV3RealtimeSessionsTest']) run('java',['-cp',dir+path.delimiter+dependencies,`org.b3log.symphony.service.${name}`])
} finally { fs.rmSync(dir,{recursive:true,force:true}) }
