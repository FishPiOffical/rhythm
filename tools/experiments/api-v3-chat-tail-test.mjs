// 使用真实工具与 DTO 做离线回归；依赖已构建的项目类，不启动服务、不发送业务请求。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const javaHome = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-chat-tail-'));
function jars(root) {
  if (!fs.existsSync(root)) return [];
  return fs.readdirSync(root, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(root, entry.name);
    return entry.isDirectory() ? jars(file) : file.endsWith('.jar') && !/-sources\.jar$|-javadoc\.jar$/.test(file) ? [file] : [];
  });
}
const cp = process.env.RHYTHM_TEST_CLASSPATH || [
  path.join(os.homedir(), '.m2/repository/org/jsoup/jsoup/1.21.1/jsoup-1.21.1.jar'),
  path.resolve('target/classes'), path.resolve('src/main/resources/lib/latke-core.jar'),
  ...jars(path.join(os.homedir(), '.m2/repository')),
].join(path.delimiter);
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status) throw new Error('小尾巴专项失败');
}
try {
  run(path.join(javaHome, 'bin/javac'), ['--release', '25', '-proc:none', '-cp', cp, '-d', dir,
    'src/main/java/org/b3log/symphony/util/ApiV3ChatTail.java',
    'src/main/java/org/b3log/symphony/util/ApiV3ChatMessageDto.java',
    'src/test/java/org/b3log/symphony/util/ApiV3ChatTailTest.java']);
  run(path.join(javaHome, 'bin/java'), ['-cp', dir + path.delimiter + cp, 'org.b3log.symphony.util.ApiV3ChatTailTest']);
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
