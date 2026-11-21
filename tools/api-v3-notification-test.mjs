#!/usr/bin/env node
/** 在临时内存 SQLite 中验证通知 SQL 与状态，不连接应用数据库。
 * 查询会移除 FOR UPDATE，失效标记的 UPSERT 使用 SQLite 语法，MySQL 行锁和 HTTP 渲染须另行验证。
 * 未使用默认 IDE 安装时，请配置 Java 25 的 JAVA_HOME、API_V3_TESTNG_JAR 和 API_V3_SQLITE_JAR。
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
const latke = 'src/main/resources/lib/latke-core.jar';
for (const file of [java, javac, testng, sqlite, latke]) if (!fs.existsSync(file)) throw new Error('Configure Java 25, TestNG and SQLite JDBC paths');
const sources = ['model/Notification', 'model/ApiV3NotificationRevision', 'repository/ApiV3RevisionRepository','repository/ApiV3NotificationRevisionRepository', 'model/ApiV3Request', 'util/ApiV3Exception', 'util/ApiV3MutationStore', 'util/ApiV3Transactions', 'util/ApiV3TokenUtil',
  'util/ApiV3Json', 'util/ApiV3WriteInputs', 'util/ApiV3NotificationTypes', 'util/ApiV3NotificationDto', 'util/ContentNotificationReferences', 'model/Reward', 'repository/ApiV3RequestRepository',
  'repository/NotificationRepository', 'service/NotificationStateMgmtService', 'service/ApiV3IdempotencyMgmtService', 'service/ApiV3NotificationMgmtService'];
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-notification-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Notification tests failed');
}
try {
  const classes = path.join(work, 'classes');
  const cp = [latke, testng, sqlite].join(path.delimiter);
  run(javac, ['--release', '25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java', '-proc:none', '-cp', cp, '-d', classes,
    ...sources.map(name => `src/main/java/org/b3log/symphony/${name}.java`),
    'src/test/java/org/b3log/symphony/repository/ApiV3NotificationSqlTest.java',
    'src/test/java/org/b3log/symphony/repository/ApiV3NotificationRevisionTest.java']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.repository.ApiV3NotificationSqlTest']);
  run(java, ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.repository.ApiV3NotificationRevisionTest']);
} finally {
  fs.rmSync(work, { recursive: true, force: true });
}
