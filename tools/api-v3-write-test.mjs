#!/usr/bin/env node
/** 只将真实的 v3 写入协议、事务辅助代码和对应测试编译到临时目录。
 * JDBC 故障用例使用临时 SQLite，无需 Maven、运行中服务、应用数据库、编译产物或生成的替身。
 * JAVA_HOME 指向 Java 25，API_V3_TESTNG_JAR 指向已安装的 TestNG，macOS 默认从 Android Studio 取用。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide = '/Applications/Android Studio.app/Contents';
const javaHome = process.env.JAVA_HOME || `${ide}/jbr/Contents/Home`;
const testng = process.env.API_V3_TESTNG_JAR || `${ide}/plugins/testng/lib/testng.jar`;
const sqlite = process.env.API_V3_SQLITE_JAR || `${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
const extension = process.platform === 'win32' ? '.exe' : '';
const java = path.join(javaHome, 'bin', `java${extension}`);
const javac = path.join(javaHome, 'bin', `javac${extension}`);
const latke = 'src/main/resources/lib/latke-core.jar';
for (const file of [java, javac, testng, sqlite, latke]) if (!fs.existsSync(file)) throw new Error('Configure Java 25, TestNG and SQLite jar paths');
const main = ['util/LivenessRewardClaim','model/ApiV3NotificationRevision','repository/ApiV3RevisionRepository','repository/ApiV3NotificationRevisionRepository','util/ApiV3Exception', 'util/ApiV3MutationStore', 'util/ApiV3Json', 'util/ApiV3Transactions', 'util/ApiV3TokenUtil', 'util/ApiV3WriteInputs',
  'util/ApiV3DraftInputs', 'util/ApiV3DraftDto', 'model/ApiV3Request', 'model/Article', 'model/ArticleDraft', 'model/LongArticleColumn',
  'repository/ApiV3RequestRepository', 'repository/ArticleDraftRepository', 'service/ArticleDraftMgmtService',
  'service/ApiV3DraftMgmtService', 'service/ApiV3IdempotencyMgmtService'];
const suites = ['util/ApiV3JsonTest', 'util/ApiV3WriteInputsTest', 'util/ApiV3DraftInputsTest', 'service/ApiV3IdempotencyMgmtServiceTest', 'service/ApiV3DraftMgmtServiceTest', 'service/ApiV3CommitFailureTest'];
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-write-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('V3 write tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng, sqlite].join(path.delimiter);
  run(javac, ['--release', '25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java', '-proc:none', '-cp', cp, '-d', classes,
    ...main.map(name => `src/main/java/org/b3log/symphony/${name}.java`),
    ...suites.map(name => `src/test/java/org/b3log/symphony/${name}.java`)]);
  for (const suite of suites) run(java, ['-cp', [classes, cp].join(path.delimiter), `org.b3log.symphony.${suite.replaceAll('/', '.')}`]);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
