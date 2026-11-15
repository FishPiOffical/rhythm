#!/usr/bin/env node
/** Java 25 定向类型检查与真实服务测试，不运行 Maven、不连接应用数据库或外部服务。
 * API_V3_RUNTIME_CLASSPATH 提供项目依赖；不要包含冲突的日志桥接实现。
 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
const home = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const dependencies = process.env.API_V3_RUNTIME_CLASSPATH;
if (!dependencies) throw new Error('请设置 API_V3_RUNTIME_CLASSPATH 为当前项目依赖类路径');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-admin-v3-test-'));
const cp = ['src/main/resources/lib/latke-core.jar', 'src/main/resources/lib/net/pusuo/patchca-0.5.0.jar', dependencies].join(path.delimiter);
const sources = [
  'processor/AdminProcessor', 'processor/ApiV3AdminStage4Processor', 'service/ApiV3AdminService',
  'service/ApiV3AdminSystemService', 'service/ApiV3AdminOpsService', 'service/ArticleMgmtService',
  'service/OptionMgmtService', 'service/AdminOperationTaskService', 'service/SearchMgmtService',
  'repository/AdminOperationTaskRepository', 'util/ApiV3ProfessionInputs', 'util/ApiV3Json',
  'util/ApiV3WriteInputs', 'util/Firewall'
];
const suites = ['service/ArticleAdminCompatibilityTest', 'service/AdminOperationTaskServiceTest', 'service/AdminImageReviewServiceTest', 'util/ApiV3ProfessionInputsTest'];
function run(binary, args) {
  const result = spawnSync(path.join(home, 'bin', binary), args, { stdio: 'inherit', timeout: 60000 });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${binary} failed: ${result.status}`);
}
try {
  run('javac', ['--release', '25', '-proc:none', '-cp', cp, '-sourcepath', 'src/main/java', '-d', work,
    ...sources.map(s => `src/main/java/org/b3log/symphony/${s}.java`),
    ...suites.map(s => `src/test/java/org/b3log/symphony/${s}.java`)]);
  for (const suite of suites) run('java', ['-cp', [work, 'src/main/resources', cp].join(path.delimiter), `org.b3log.symphony.${suite.replaceAll('/', '.')}`]);
} finally { fs.rmSync(work, { recursive: true, force: true }); }
