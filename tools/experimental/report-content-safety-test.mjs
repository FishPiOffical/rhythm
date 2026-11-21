#!/usr/bin/env node
/** 隔离实验入口：以 Java 25 编译真实源码验证举报正文及两端模板，不接入默认构建、不使用运行服务。 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const javaHome = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-report-content-'));

function jars(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, {withFileTypes: true}).flatMap(entry => {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) return jars(file);
    return file.endsWith('.jar') && !/-sources\.jar$|-javadoc\.jar$|log4j-to-slf4j|slf4j-simple|logback-classic/.test(file)
      ? [file] : [];
  });
}

const dependencies = [
  ...jars(path.join(root, 'src/main/resources/lib')),
  ...jars(process.env.REPORT_TEST_M2_REPOSITORY || path.join(os.homedir(), '.m2/repository'))
    .sort((left, right) => right.localeCompare(left, undefined, {numeric: true})),
].join(path.delimiter);

function run(command, args) {
  const result = spawnSync(path.join(javaHome, 'bin', command), args, {
    cwd: root,
    stdio: 'inherit',
    timeout: 120000,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} failed: ${result.status}`);
}

try {
  run('javac', ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-Xprefer:source',
    '-sourcepath', path.join(root, 'src/main/java'), '-cp', dependencies, '-d', work,
    'src/test/java/org/b3log/symphony/service/ReportQueryServiceTest.java',
    'src/test/java/org/b3log/symphony/service/ReportContentSafetyTest.java']);
  run('java', ['--enable-native-access=ALL-UNNAMED', '-cp',
    [work, path.join(root, 'src/main/resources'), dependencies].join(path.delimiter),
    'org.testng.TestNG', '-usedefaultlisteners', 'false', '-verbose', '2', '-testclass',
    'org.b3log.symphony.service.ReportQueryServiceTest,org.b3log.symphony.service.ReportContentSafetyTest']);
} finally {
  fs.rmSync(work, {recursive: true, force: true});
}
