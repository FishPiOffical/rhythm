#!/usr/bin/env node
// 临时输出编译结果，运行持久化 Java 回归；不启动服务或连接真实数据库。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const javaHome = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-pets-'));
function jars(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? jars(file) : file.endsWith('.jar') && !/-sources\.jar$|-javadoc\.jar$|log4j-to-slf4j|slf4j-simple|logback-classic/.test(file) ? [file] : [];
  });
}
const dependencies = process.env.API_V3_TEST_CLASSPATH || [path.resolve('target/classes'),
  path.resolve('src/main/resources/lib/latke-core.jar'), ...jars(path.join(os.homedir(), '.m2/repository'))].join(path.delimiter);
function run(name, arguments_) {
  const result = spawnSync(path.join(javaHome, 'bin', name), arguments_, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('宠物专项失败');
}
try {
  const sources = ['util/ApiV3PetRules', 'service/ApiV3PetService', 'processor/ApiV3PetProcessor', 'processor/ApiV3Processor',
    'util/WebsiteLoginCookies', 'util/ApiV3RefreshCookies', 'util/Sessions', 'service/ApiV3TokenMgmtService',
    'processor/ApiV3ChatTailProcessor', 'service/ApiV3ChatTailService', 'util/ApiV3ChatTail', 'repository/UserChatTailRepository',
    'repository/PetAccountRepository', 'repository/UserPetRepository', 'repository/PetInventoryRepository', 'repository/PetLedgerRepository'];
  const tests = ['ApiV3SecurityTest', 'ApiV3IdentityApplicationTest', 'ApiV3QqSettingsTest', 'ApiV3PetTest'];
  run('javac', ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', dependencies, '-d', output,
    ...sources.map(file => `src/main/java/org/b3log/symphony/${file}.java`),
    ...tests.map(file => `src/test/java/org/b3log/symphony/service/${file}.java`)]);
  // 仅替换 HTTP 输出端口以断言状态码；真实路由、中间件、凭据与事务均保留。
  const shim = path.join(output, 'ApiV3Processor.java');
  fs.writeFileSync(shim, `package org.b3log.symphony.processor;
public class ApiV3Processor {
    public static final String IDENTITY = "apiV3Identity";
    public static String requestId(org.b3log.latke.http.RequestContext context) { return "test-pets"; }
    public static void success(org.b3log.latke.http.RequestContext context, Object data) { context.attr("test.status", 200); context.attr("test.data", data); }
    public static void error(org.b3log.latke.http.RequestContext context, int status, String code, String msg) { context.attr("test.status", status); context.attr("test.code", code); }
}`);
  run('javac', ['--release', '25', '-cp', dependencies, '-d', output, shim]);
  run('java', ['-cp', output + path.delimiter + dependencies, 'org.b3log.symphony.service.ApiV3PetTest']);
} finally {
  fs.rmSync(output, { recursive: true, force: true });
}
