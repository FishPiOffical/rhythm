#!/usr/bin/env node
/** Isolated Java 25 discovery contract test. No network/server/database/payment calls. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const home = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const suffix = process.platform === 'win32' ? '.exe' : '';
const java = path.join(home, 'bin', `java${suffix}`);
const javac = path.join(home, 'bin', `javac${suffix}`);
const latke = 'src/main/resources/lib/latke-core.jar';
for (const file of [java, javac, latke]) if (!fs.existsSync(file)) throw new Error('Configure JAVA_HOME for Java 25');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-red-packet-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Red-packet discovery tests failed');
}
try {
  run(javac, ['--release', '25', '-proc:none', '-cp', latke, '-d', work,
    'src/main/java/org/b3log/symphony/util/ApiV3RedPacketTypes.java',
    'src/test/java/org/b3log/symphony/util/ApiV3RedPacketTypesTest.java']);
  run(java, ['-cp', [work, latke].join(path.delimiter), 'org.b3log.symphony.util.ApiV3RedPacketTypesTest']);
} finally {
  fs.rmSync(work, { recursive: true, force: true });
}
