#!/usr/bin/env node
/** Isolated Java 25 token tests. No Maven or application class output.
 * Set JAVA_HOME and API_V3_TESTNG_JAR if Java/TestNG are not in the default IDE location.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const ide = '/Applications/Android Studio.app/Contents';
const javaHome = process.env.JAVA_HOME || `${ide}/jbr/Contents/Home`;
const testng = process.env.API_V3_TESTNG_JAR || `${ide}/plugins/testng/lib/testng.jar`;
const latke = 'src/main/resources/lib/latke-core.jar';
const extension = process.platform === 'win32' ? '.exe' : '';
const java = path.join(javaHome, 'bin', `java${extension}`);
const javac = path.join(javaHome, 'bin', `javac${extension}`);
for (const file of [java, javac, testng, latke]) {
  if (!fs.existsSync(file)) throw new Error('Set JAVA_HOME to Java 25 and API_V3_TESTNG_JAR to an installed TestNG JAR');
}
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-token-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Isolated token tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng].join(path.delimiter);
  run(javac, ['--release', '25', '-proc:none', '-cp', cp, '-d', classes,
    'src/main/java/org/b3log/symphony/util/ApiV3TokenUtil.java',
    'src/main/java/org/b3log/symphony/util/ApiV3RefreshCookies.java',
    'src/main/java/org/b3log/symphony/util/ApiV3Pkce.java',
    'src/test/java/org/b3log/symphony/util/ApiV3TokenUtilTest.java',
    'src/test/java/org/b3log/symphony/util/ApiV3RefreshCookiesTest.java',
    'src/test/java/org/b3log/symphony/util/ApiV3PkceTest.java']);
  for (const name of ['ApiV3TokenUtilTest', 'ApiV3RefreshCookiesTest','ApiV3PkceTest']) run(java, ['-cp', [classes, cp].join(path.delimiter), `org.b3log.symphony.util.${name}`]);
} finally {
  fs.rmSync(work, { recursive: true, force: true });
}
