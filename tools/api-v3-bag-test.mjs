#!/usr/bin/env node
/** 隔离测试背包状态转换和 SQL，使用独立测试数据库。
 * 覆盖范围不含 HTTP、完整背包服务、MySQL 行锁或定时签到。
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
const latke = 'src/main/resources/lib/latke-core.jar';
for (const file of [java, javac, testng, sqlite, latke]) if (!fs.existsSync(file)) throw new Error('Configure Java 25, TestNG and SQLite JDBC paths');
const sources = ['util/BagMutation', 'repository/CloudRepository'];
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-bag-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Bag tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng, sqlite].join(path.delimiter);
  run(javac, ['--release', '25', '-proc:none', '-cp', cp, '-d', classes,
    ...sources.map(name => `src/main/java/org/b3log/symphony/${name}.java`),
    'src/test/java/org/b3log/symphony/repository/ApiV3BagSqlTest.java']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.repository.ApiV3BagSqlTest']);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
