#!/usr/bin/env node
/** 使用现有应用类和运行依赖回归旧站退出，不运行 Maven 或连接数据库。 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const javaHome = process.env.JAVA_HOME;
const classpath = process.env.API_V3_TEST_CLASSPATH;
if (!javaHome || !classpath) {
  throw new Error('Set JAVA_HOME to Java 25 and API_V3_TEST_CLASSPATH to the application classes and runtime dependencies');
}
const extension = process.platform === 'win32' ? '.exe' : '';
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-website-logout-'));
const classes = path.join(work, 'classes');

function run(name, args, cp) {
  const result = spawnSync(path.join(javaHome, 'bin', name + extension), args, {
    stdio: 'inherit',
    timeout: 60_000,
    env: { ...process.env, CLASSPATH: cp },
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${name} failed (${result.status})`);
}

try {
  run('javac', ['--release', '25', '-encoding', 'UTF-8', '-proc:none', '-d', classes,
    'src/main/java/org/b3log/symphony/util/Sessions.java',
    'src/main/java/org/b3log/symphony/util/WebsiteLoginCookies.java',
    'src/main/java/org/b3log/symphony/util/ApiV3RefreshCookies.java',
    'src/main/java/org/b3log/symphony/util/ApiV3TokenUtil.java',
    'src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java',
    'src/main/java/org/b3log/symphony/repository/ApiV3ClientRepository.java',
    'src/main/java/org/b3log/symphony/repository/ApiV3TokenRepository.java',
    'src/main/java/org/b3log/symphony/repository/UserRepository.java',
    'src/test/java/org/b3log/latke/http/WebsiteLoginCookiesTest.java'], classpath);
  run('java', ['org.b3log.latke.http.WebsiteLoginCookiesTest'], [classes, classpath].join(path.delimiter));
} finally {
  fs.rmSync(work, { recursive: true, force: true });
}
