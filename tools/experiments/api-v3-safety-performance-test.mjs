/** Java 25 定向回归；每个进程限时 60 秒，不连接运行中的服务或数据库。 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const javaHome = process.env.JAVA_HOME;
if (!javaHome) throw new Error('需要 Java 25 JAVA_HOME');
function jars(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? jars(file) : file.endsWith('.jar')
      && !/-sources|-javadoc|log4j-to-slf4j|log4j-slf4j-impl|slf4j-jdk14|slf4j-simple|logback-classic/.test(file) ? [file] : [];
  });
}
const allJars = jars(path.join(os.homedir(), '.m2/repository'));
const priority = allJars.filter(file => /jsoup[\\/]1\.21\.1|json[\\/]20250517/.test(file));
const dependencies = [path.join(root, 'src/main/resources/lib/latke-core.jar'), ...jars(path.join(root, 'src/main/resources/lib')), ...priority,
  path.join(root, 'target/classes'), ...allJars].join(path.delimiter);
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-safety-'));
function run(binary, args) {
  const result = spawnSync(path.join(javaHome, 'bin', binary), args,
    { cwd: root, stdio: 'inherit', timeout: 60_000 });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${binary} 退出 ${result.status}, 信号 ${result.signal || '无'}`);
}
const production = ['repository/ApiV3TokenRepository', 'service/ApiV3TokenMgmtService',
  'service/ApiV3ChatQueryService', 'service/ApiV3RealtimeService', 'util/ApiV3ChatStream',
  'util/ApiV3Cors', 'util/WebsiteLoginCookies', 'util/ApiV3ChatMessageDto',
  'util/AnonymousCaptchaShield', 'processor/BeforeRequestHandler', 'processor/CaptchaProcessor',
  'processor/middleware/AnonymousViewCheckMidware', 'processor/ApiV3Processor',
  'processor/ApiV3OAuthProcessor', 'service/ApiV3OAuthCorsService', 'util/ApiV3RealtimeSessions',
  'service/ApiV3SocketTicketService', 'repository/ApiV3ChatEventRepository'];
const tests = ['util/ApiV3CorsTest', 'service/ApiV3TokenActivityTest', 'service/ApiV3ChatBatchTest',
  'util/ApiV3ChatStreamTest'];
const argsFile = path.join(output, 'compile.args');
const arguments_ = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', dependencies,
  '-d', output, ...production.map(file => `src/main/java/org/b3log/symphony/${file}.java`),
  ...[...tests, 'util/AnonymousCaptchaShieldTest'].map(file => `src/test/java/org/b3log/symphony/${file}.java`)];
fs.writeFileSync(argsFile, arguments_.map(value => JSON.stringify(value)).join('\n'));
run('javac', [`@${argsFile}`]);
// Windows 启动器的参数文件使用系统代码页；清单 URL 保证中文路径及长 classpath 都可读取。
const manifest = path.join(output, 'classpath.mf');
const manifestLine = 'Class-Path: ' + [output, ...dependencies.split(path.delimiter)]
  .map(file => pathToFileURL(file).href + (fs.statSync(file).isDirectory() ? '/' : '')).join(' ');
fs.writeFileSync(manifest, 'Manifest-Version: 1.0\r\n' + manifestLine.match(/.{1,70}/g).join('\r\n ') + '\r\n\r\n');
const runtimeJar = path.join(output, 'classpath.jar');
run('jar', ['--create', '--file', runtimeJar, '--manifest', manifest]);
for (const test of tests) {
  run('java', ['-Dfile.encoding=UTF-8', '-cp', runtimeJar, `org.b3log.symphony.${test.replaceAll('/', '.')}`]);
}
run('java', ['-Dfile.encoding=UTF-8', '-cp', runtimeJar, 'org.testng.TestNG', '-usedefaultlisteners', 'false',
  '-testclass', 'org.b3log.symphony.util.AnonymousCaptchaShieldTest']);
console.log('PASS all safety/performance suites (60-second process deadlines)');
