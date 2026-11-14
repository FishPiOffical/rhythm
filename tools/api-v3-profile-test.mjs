#!/usr/bin/env node
/** 隔离验证公开资料 SQL，不连接应用数据库。
 * HTTP、资料隐私和 HTML 渲染须另行验证。
 * 未使用默认 IDE 安装时，请配置 Java 25 的 JAVA_HOME、API_V3_TESTNG_JAR 和 API_V3_SQLITE_JAR。
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
const latke = 'src/main/resources/lib/latke-core.jar';
for (const file of [java, javac, testng, sqlite, latke]) if (!fs.existsSync(file)) throw new Error('Configure Java 25, TestNG and SQLite JDBC paths');
const sources = ['model/Article', 'model/Comment', 'model/Follow', 'util/ApiV3ProfileQueries'];
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-profile-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Profile SQL tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng, sqlite].join(path.delimiter);
  run(javac, ['--release', '25', '-proc:none', '-cp', cp, '-d', classes,
    ...sources.map(name => `src/main/java/org/b3log/symphony/${name}.java`),
    'src/test/java/org/b3log/symphony/util/ApiV3ProfileQueriesTest.java']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.util.ApiV3ProfileQueriesTest']);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
