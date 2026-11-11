#!/usr/bin/env node
/** 用已构建的应用依赖运行真实复读服务测试；仓库替身不连接数据库。 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';

if (!process.env.API_V3_RUNTIME_CLASSPATH || !process.env.API_V3_TESTNG_JAR)
  throw Error('Set API_V3_RUNTIME_CLASSPATH and API_V3_TESTNG_JAR; use Java 25.');
const cp = [process.env.API_V3_RUNTIME_CLASSPATH, process.env.API_V3_TESTNG_JAR,
  process.env.API_V3_JCOMMANDER_JAR].filter(Boolean).join(path.delimiter);
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-repeater-write-'));
const java = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name) : name;
function run(name, args) {
  const result = spawnSync(java(name), args, {stdio: 'inherit', timeout: 60000});
  if (result.error) throw result.error;
  if (result.status !== 0) throw Error(`${name} failed (${result.status})`);
}
try {
  const sources = ['model/RepeaterContent', 'repository/RepeaterContentRepository',
    'repository/OperationRepository', 'service/RepeaterQueryService', 'service/RepeaterMgmtService',
    'service/RepeaterContentModerationService', 'service/ApiV3RepeaterPublicationService',
    'service/ApiV3IdempotencyMgmtService', 'util/RepeaterWriteSecurity', 'util/ApiV3RepeaterPublicationInputs'];
  const tests = ['RepeaterContentModerationServiceTest', 'RepeaterStationHardeningTest'];
  run('javac', ['--release', '25', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', dir,
    ...sources.map(s => `src/main/java/org/b3log/symphony/${s}.java`),
    ...tests.map(s => `src/test/java/org/b3log/symphony/service/${s}.java`)]);
  run('java', ['-cp', [dir, cp].join(path.delimiter), 'org.testng.TestNG', '-usedefaultlisteners', 'false',
    '-testclass', tests.map(s => `org.b3log.symphony.service.${s}`).join(',')]);
} finally {
  const resolved = path.resolve(dir);
  if (path.dirname(resolved) !== path.resolve(os.tmpdir()) || !path.basename(resolved).startsWith('rhythm-v3-repeater-write-'))
    throw Error('Unexpected temporary test directory');
  fs.rmSync(resolved, {recursive: true, force: true});
}
