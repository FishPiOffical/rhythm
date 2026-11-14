// 宠物专项共用编译闭包；缓存只保存测试类，不连接真实数据库。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const javaHome = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
function jars(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? jars(file) : file.endsWith('.jar') && !/-sources\.jar$|-javadoc\.jar$|log4j-to-slf4j|slf4j-simple|logback-classic/.test(file) ? [file] : [];
  }).sort();
}
export const dependencies = process.env.API_V3_TEST_CLASSPATH || [path.join(root, 'target/classes'),
  path.join(root, 'src/main/resources/lib/latke-core.jar'), ...jars(path.join(os.homedir(), '.m2/repository'))].join(path.delimiter);
export const suites = ['ApiV3PetTest', 'ApiV3PetActivitiesTest', 'ApiV3PetWorkTest', 'ApiV3PetExplorationTest', 'ApiV3PetProgressionTest', 'ApiV3PetBalanceTest', 'ApiV3PetRiskTest', 'ApiV3PetRiskHttpTest'];
export const sources = ['util/ApiV3PetRules', 'util/ApiV3PetActivities', 'util/ApiV3PetWork', 'util/ApiV3PetExploration', 'util/ApiV3PetJournal', 'util/ApiV3PetStories', 'util/ApiV3PetAdvancedCourses', 'service/ApiV3PetService', 'service/ApiV3PetRiskService', 'service/ApiV3PetCaptchaService', 'repository/PetRiskStateRepository', 'repository/PetRiskAuditRepository', 'repository/PetRiskProofRepository', 'processor/ApiV3PetProcessor', 'processor/ApiV3Processor',
    'util/WebsiteLoginCookies', 'util/ApiV3RefreshCookies', 'util/Sessions', 'service/ApiV3TokenMgmtService',
    'processor/ApiV3ChatTailProcessor', 'service/ApiV3ChatTailService', 'util/ApiV3ChatTail', 'repository/UserChatTailRepository',
    'repository/PetStoryProgressRepository', 'repository/PetArchiveRepository', 'repository/PetCollectionRepository', 'repository/PetJournalRepository', 'repository/PetJobProgressRepository', 'repository/PetIncomeAccountRepository', 'repository/PetActivityResultRepository', 'repository/PetActivityRepository', 'repository/PetSkillRepository', 'repository/PetAccountRepository', 'repository/UserPetRepository', 'repository/PetInventoryRepository', 'repository/PetLedgerRepository'];
export const tests = ['ApiV3SecurityTest', 'ApiV3IdentityApplicationTest', 'ApiV3QqSettingsTest', 'ApiV3PetTest', 'ApiV3PetActivitiesTest', 'ApiV3PetWorkTest', 'ApiV3PetExplorationTest', 'ApiV3PetProgressionTest', 'ApiV3PetBalanceTest', 'ApiV3PetSequenceTest', 'ApiV3PetRiskTest', 'ApiV3PetRiskHttpTest'];

export function runJava(name, arguments_, options = {}) {
  const result = spawnSync(path.join(javaHome, 'bin', name), arguments_, { cwd: root, stdio: 'inherit', timeout: 1_200_000, ...options });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`宠物专项 ${name} 退出 ${result.status}，信号 ${result.signal || '无'}`);
}

export function compileRuntime(output, options = {}) {
  const run = (name, args) => runJava(name, args, options);
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

}

export function cachedRuntime(cacheDirectory, options = {}) {
  // 源码、运行依赖和 JDK 变更均失效；缓存目录只能由协调器给到本地临时目录。
  const hash = createHash('sha256');
  hash.update(fs.readFileSync(fileURLToPath(import.meta.url)));
  for (const file of [...sources.map(x => `src/main/java/org/b3log/symphony/${x}.java`),
    ...tests.map(x => `src/test/java/org/b3log/symphony/service/${x}.java`)]) {
    hash.update(file).update(fs.readFileSync(path.join(root, file)));
  }
  function stamp(file) {
    const info = fs.statSync(file);
    hash.update(`${file}:${info.size}:${info.mtimeMs}`);
    if (info.isDirectory()) for (const child of fs.readdirSync(file).sort()) stamp(path.join(file, child));
  }
  hash.update(dependencies);
  for (const file of dependencies.split(path.delimiter)) stamp(file);
  stamp(path.join(javaHome, 'bin/javac'));
  const fingerprint = hash.digest('hex');
  const output = path.join(path.resolve(cacheDirectory), fingerprint);
  const marker = path.join(output, '.complete');
  if (fs.existsSync(marker)) return { output, fingerprint, compiled: false };
  fs.mkdirSync(cacheDirectory, { recursive: true });
  const temporary = fs.mkdtempSync(path.join(path.resolve(cacheDirectory), '.compile-'));
  try {
    compileRuntime(temporary, options);
    fs.writeFileSync(path.join(temporary, '.complete'), fingerprint);
    try { fs.renameSync(temporary, output); }
    catch (error) { if (!fs.existsSync(marker)) throw error; }
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
  return { output, fingerprint, compiled: true };
}
