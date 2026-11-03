#!/usr/bin/env node
/** Isolated settings input/DTO and real shared transition tests; never connects to the application database.
 * HTTP, UserRepository/cache/session, real censor/skin discovery, MySQL and v3 coordination are not covered.
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
const jsoup = process.env.API_V3_JSOUP_JAR || `${ide}/lib/intellij.libraries.jsoup.jar`;
const latke = 'src/main/resources/lib/latke-core.jar';
for (const file of [java, javac, testng, sqlite, jsoup, latke]) if (!fs.existsSync(file)) throw new Error('Configure Java 25, TestNG and SQLite JDBC paths');
const sources = ['util/ApiV3Personality', 'util/ApiV3Exception', 'util/ApiV3Json', 'util/ApiV3WriteInputs', 'util/ApiV3Settings', 'util/SettingsRedirects', 'util/SettingsLocaleOptions', 'util/Languages', 'util/UserSettingsMutation', 'util/ProfileWriteQuota'];
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-settings-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Settings tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng, sqlite, jsoup].join(path.delimiter);
  const model = path.join(work, 'UserExt.java');
  const actualModel = fs.readFileSync('src/main/java/org/b3log/symphony/model/UserExt.java', 'utf8');
  for (const [name, value] of [['USER_STATUS_C_VALID', 0], ['USER_AVATAR_TYPE_C_UPLOAD', 2]]) {
    if (!actualModel.includes(`public static final int ${name} = ${value};`)) throw new Error('Update model adapter');
  }
  fs.writeFileSync(model, 'package org.b3log.symphony.model;public class UserExt {public static final int USER_STATUS_C_VALID=0, USER_AVATAR_TYPE_C_UPLOAD=2;}');
  run(javac, ['--release', '25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java', '-proc:none', '-cp', cp, '-d', classes,
    model, ...sources.map(name => `src/main/java/org/b3log/symphony/${name}.java`),
    ...['main/SimpleCurrentLimiter', 'control/MainControl', 'cache/MainCache', 'cache/pair/CachePair'].map(name => `src/main/java/pers/adlered/simplecurrentlimiter/${name}.java`),
    'src/test/java/org/b3log/symphony/util/ApiV3SettingsTest.java']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.util.ApiV3SettingsTest']);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
