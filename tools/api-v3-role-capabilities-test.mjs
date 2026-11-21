#!/usr/bin/env node
// 在系统临时目录编译当前源码并使用隔离 SQLite；不使用本地服务数据库，也不下载依赖。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const bundledJdk = '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const javaHome = process.env.JAVA_HOME || (fs.existsSync(bundledJdk) ? bundledJdk : '');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-role-capabilities-'));
function jars(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? jars(file)
      : file.endsWith('.jar') && !/-sources\.jar$|-javadoc\.jar$|log4j-to-slf4j|slf4j-simple|logback-classic/.test(file) ? [file] : [];
  });
}
const dependencies = process.env.API_V3_TEST_CLASSPATH || [
  ...jars(path.join(root, 'src/main/resources/lib')),
  ...jars(path.join(os.homedir(), '.m2/repository')).sort((a, b) => b.localeCompare(a, undefined, { numeric: true })),
].join(path.delimiter);
function run(command, args) {
  const result = spawnSync(javaHome ? path.join(javaHome, 'bin', command) : command, args,
    { cwd: root, stdio: 'inherit', timeout: 120_000 });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`角色权限专项 ${command} 失败，退出码 ${result.status}`);
}
try {
  // 编译真实控制器和依赖闭包，防止旧 target/classes 掩盖接口签名问题。
  run('javac', ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-Xprefer:source',
    '-sourcepath', path.join(root, 'src/main/java'), '-cp', dependencies, '-d', work,
    'src/main/java/org/b3log/symphony/processor/ApiV3Processor.java',
    'src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java',
    'src/main/java/org/b3log/symphony/service/ApiV3DiscoveryQueryService.java',
    'src/test/java/org/b3log/symphony/service/ApiV3SecurityTest.java',
    'src/test/java/org/b3log/symphony/service/ApiV3RoleCapabilitiesTest.java']);
  run('java', ['--enable-native-access=ALL-UNNAMED', '-cp',
    [work, path.join(root, 'src/main/resources'), dependencies].join(path.delimiter),
    'org.b3log.symphony.service.ApiV3RoleCapabilitiesTest']);
} finally {
  fs.rmSync(work, { recursive: true, force: true });
}
