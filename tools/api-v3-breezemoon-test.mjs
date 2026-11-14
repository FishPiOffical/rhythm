#!/usr/bin/env node
/** 隔离测试清风明月输入与 SQL，使用独立测试数据库。
 * 覆盖范围不含 HTTP、内容审核、职业事件及 MySQL 锁。
 * 若不使用默认 IDE 安装，请配置 JAVA_HOME（Java 25）、API_V3_TESTNG_JAR 和 API_V3_SQLITE_JAR。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide = '/Applications/Android Studio.app/Contents';
const home = process.env.JAVA_HOME || `${ide}/jbr/Contents/Home`;
const testng = process.env.API_V3_TESTNG_JAR || `${ide}/plugins/testng/lib/testng.jar`;
const sqlite = process.env.API_V3_SQLITE_JAR || `${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
const suffix = process.platform === 'win32' ? '.exe' : '';
const java = path.join(home, 'bin', `java${suffix}`);
const javac = path.join(home, 'bin', `javac${suffix}`);
const jsoup = process.env.API_V3_JSOUP_JAR || `${ide}/lib/intellij.libraries.jsoup.jar`;
const latke = 'src/main/resources/lib/latke-core.jar';
for (const file of [java, javac, testng, sqlite, jsoup, latke]) if (!fs.existsSync(file)) throw new Error('Configure Java 25, TestNG and SQLite JDBC paths');
const sources = ['model/Breezemoon', 'model/Follow', 'util/ApiV3Exception', 'util/ApiV3Json', 'util/ApiV3WriteInputs', 'util/ApiV3BreezemoonInputs', 'util/ApiV3BreezemoonEdit', 'repository/BreezemoonRepository'];
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-breezemoon-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Breezemoon tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng, sqlite, jsoup].join(path.delimiter);
  run(javac, ['--release', '25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java', '-proc:none', '-cp', cp, '-d', classes,
    ...sources.map(name => `src/main/java/org/b3log/symphony/${name}.java`),
    'src/test/java/org/b3log/symphony/repository/ApiV3BreezemoonTest.java']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.repository.ApiV3BreezemoonTest']);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
