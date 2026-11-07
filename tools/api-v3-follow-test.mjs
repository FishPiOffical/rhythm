#!/usr/bin/env node
/** Isolated relationship transition and SQL tests; never connects to the application database.
 * HTTP, MySQL row locks, cache and notification delivery are not covered.
 * Set JAVA_HOME (Java 25), API_V3_TESTNG_JAR and API_V3_SQLITE_JAR if outside the default IDE installation.
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
const sources = ['model/Follow', 'util/FollowMutation', 'repository/FollowRepository'];
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-follow-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Follow tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng, sqlite].join(path.delimiter);
  run(javac, ['--release', '25', '-proc:none', '-cp', cp, '-d', classes,
    ...sources.map(name => `src/main/java/org/b3log/symphony/${name}.java`),
    'src/test/java/org/b3log/symphony/repository/ApiV3FollowSqlTest.java', 'src/test/java/org/b3log/symphony/util/FollowMutationTest.java']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.repository.ApiV3FollowSqlTest']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.util.FollowMutationTest']);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
