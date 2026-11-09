#!/usr/bin/env node
/** 在临时 MySQL 上运行正式阅读登记、仓储及 HTTP 代码，与应用数据库隔离。 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync, spawn } from 'node:child_process';
const settlement=process.argv.includes('--settlement');
const ide = '/Applications/Android Studio.app/Contents',
 home = process.env.JAVA_HOME || `${ide}/jbr/Contents/Home`,
 dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-article-read-')),
 files = [];
const cp = [
 'src/main/resources/lib/latke-core.jar',
 `${ide}/lib/intellij.libraries.caffeine.jar`,
 path.join(os.homedir(), '.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar')
].join(path.delimiter);
const run = (cmd, args, options = {}) => {
 const r = spawnSync(cmd, args, { encoding: 'utf8', timeout: 45000, ...options });
 if (r.status !== 0) throw Error(r.stdout + ' ' + r.stderr);
 return r.stdout.trim()
};

const shim = (name, source) => {
 const f = path.join(dir, name + '.java');
 fs.mkdirSync(path.dirname(f), { recursive: true });
 fs.writeFileSync(f, source);
 files.push(f)
};

function method(source, signature) {
 const i = source.indexOf(signature);
 let depth = 0;
 for (let j = source.indexOf('{', i); j < source.length; j++) {
  if (source[j] === '{') depth++;
  if (source[j] === '}' && --depth === 0) return source.slice(i, j + 1)
 }
 throw Error(signature)
}

let container;
const children = [];
try{
 for(const [name,table,methods]of [['ArticleRepository','articles',['public String getCommentPublicationAuthor(','public JSONObject getForUpdate(']],['UserRepository','users',['public JSONObject getForUpdate(']]]){const source=fs.readFileSync(`src/main/java/org/b3log/symphony/repository/${name}.java`,'utf8');shim('org/b3log/symphony/repository/'+name,`package org.b3log.symphony.repository;import org.json.JSONObject;import java.util.List;import org.b3log.symphony.model.Article;import org.b3log.latke.repository.*;public class ${name} extends AbstractRepository{public ${name}(){super("${table}");}${methods.map(s=>method(source,s)).join('\n')}${settlement&&name==='UserRepository'?'public void refreshSettingsCache(String id)throws RepositoryException{}public void evictSettingsCaches(String id){}':''}}`)}
 const sandbox=fs.readFileSync('src/main/java/org/b3log/symphony/model/Tag.java','utf8').match(/TAG_TITLE_C_SANDBOX = "([^"]+)"/)[1];shim('org/b3log/symphony/model/Tag',`package org.b3log.symphony.model;public class Tag{public static final String TAG_TITLE_C_SANDBOX=${JSON.stringify(sandbox)};}`);
 const pointModel=fs.readFileSync('src/main/java/org/b3log/symphony/model/Pointtransfer.java','utf8');
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys{public static final String[] RESERVED_USER_NAMES={};'+(settlement?[...new Set([...pointModel.matchAll(/Symphonys\.(POINT_[A-Z_]+)/g)].map(m=>m[1]))].map(n=>'public static final int '+n+'=0;').join(''):'')+'}');
 if(settlement){
  shim('org/b3log/symphony/repository/PointtransferRedcRepository','package org.b3log.symphony.repository;public class PointtransferRedcRepository extends org.b3log.latke.repository.AbstractRepository{public PointtransferRedcRepository(){super("pointtransfer_redc");}public static void addRecordAsync(String user,String id){throw new AssertionError("unexpected async helper");}}');
  shim('org/b3log/symphony/repository/ProfessionSourceEventRepository','package org.b3log.symphony.repository;public class ProfessionSourceEventRepository extends org.b3log.latke.repository.AbstractRepository{public ProfessionSourceEventRepository(){super("profession_source_event");}}');
  shim('org/b3log/symphony/service/LongArticleReadSettlementMessageService','package org.b3log.symphony.service;public class LongArticleReadSettlementMessageService{public void send(org.json.JSONObject value){}}');
 }
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor{public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "test-read";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);}}');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService','package org.b3log.symphony.service;import org.json.JSONObject;public class ApiV3TokenMgmtService{public JSONObject authenticate(String token){if(!java.util.Set.of("valid","read-only","activity-only").contains(token))return null;return new JSONObject().put("user",new JSONObject().put("oId","2")).put("scope",token.equals("valid")?"content.read activity.write":token.equals("read-only")?"content.read":"activity.write");}public static boolean hasScope(String s,String wanted){return java.util.Arrays.asList(s.split(" ")).contains(wanted);}}');
 const real=['model/Article','model/Comment','model/UserExt','model/Common','model/Role','model/LongArticleRead','model/LongArticleReadClaim','model/LongArticleReadWindowIncrement','model/LongArticleReadWindowPage','repository/LongArticleReadUserRepository','repository/LongArticleReadAnonRepository','repository/LongArticleReadWindowRepository','repository/AtomicRepositorySupport','util/ApiV3Exception','util/ApiV3Requests','util/ApiV3ContentUtil','util/ApiV3Transactions','util/ApiV3TransactionSavepoint','util/ApiV3Json','util/ApiV3WriteInputs','service/LongArticleReadCaptureRequest','service/LongArticleReadCaptureService','processor/ApiV3ArticleReadProcessor','processor/middleware/ApiV3AuthMidware','processor/middleware/ApiV3WriteMidware'];
 if(settlement)real.push('model/Pointtransfer','model/PointtransferSource','model/ProfessionSourceEvent','model/LongArticleReadSettlementClaim','repository/PointtransferRepository','repository/LongArticleReadHistoryRepository','repository/LongArticleReadSettlementRepository','util/AccountPairLocks','util/ArticlePublicationCaches','service/PointtransferMgmtService','service/LongArticleReadSettlementService');
 run(path.join(home,'bin/javac'),['--release','25','-proc:none','-cp',cp,'-d',dir,...files,'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java',...real.map(n=>'src/main/java/org/b3log/symphony/'+n+'.java'),'src/test/java/org/b3log/symphony/service/ApiV3ArticleReadMysqlTest.java',...(settlement?['src/test/java/org/b3log/symphony/service/ApiV3ArticleReadSettlementMysqlTest.java']:[])]);
 const image = run('docker', ['image', 'inspect', 'mysql:8.4', '--format', '{{.Id}}']);
 container = 'rhythm-v3-read-' + crypto.randomBytes(6).toString('hex');
 run('docker', ['run', '-d', '--rm', '--pull', 'never', '--memory', '768m', '--name', container,
  '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', '-e', 'MYSQL_ROOT_HOST=%', '-p', '127.0.0.1::3306',
  image, '--skip-log-bin', '--performance-schema=OFF', '--innodb-buffer-pool-size=32M']);
 const deadline = Date.now() + 90000;
 while (true) {
  const ready = spawnSync('docker', ['exec', container, 'mysqladmin', '-uroot', 'ping'], { encoding: 'utf8', timeout: 5000 });
  const comm = spawnSync('docker', ['exec', container, 'cat', '/proc/1/comm'], { encoding: 'utf8', timeout: 5000 });
  if (ready.status === 0 && comm.stdout.trim() === 'mysqld') break;
  if (Date.now() > deadline) throw Error('MySQL startup timeout');
  await new Promise(r => setTimeout(r, 250))
 }
 const sql=q=>run('docker',['exec','-i',container,'mysql','--default-character-set=utf8mb4','-uroot','-N','-B'],{input:q});
 sql('CREATE DATABASE read_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;');
 const articleProcessor=fs.readFileSync('src/main/java/org/b3log/symphony/processor/ArticleProcessor.java','utf8'),classic=fs.readFileSync('src/main/java/org/b3log/symphony/service/ArticleMgmtService.java','utf8');
 if(!articleProcessor.includes('context.attr(User.USER)')||!articleProcessor.includes('longArticleReadService.record(new LongArticleReadCaptureRequest(articleId, viewerId, null, null))')||!classic.includes('longArticleReadService.record(new LongArticleReadCaptureRequest(articleId,'))throw Error('Old call-chain wiring missing');
 const old=fs.readFileSync('sql/20260123_long_article_read.sql','utf8'),window=fs.readFileSync('sql/20260809_profession_pending.sql','utf8').match(/CREATE TABLE IF NOT EXISTS `symphony_article_long_read_window`[\s\S]*?;/)[0];
 sql('USE read_test;'+['user','anon'].map(t=>old.match(new RegExp('CREATE TABLE IF NOT EXISTS `symphony_article_long_read_'+t+'`[\\s\\S]*?;'))[0]).join('\n')+window+"INSERT INTO symphony_article_long_read_user VALUES ('100','10','2',1793318399999);");
 sql('USE read_test;\n'+fs.readFileSync('sql/20261030_api_v3_article_reads.sql','utf8'));
 const mapped = sql('USE read_test;SELECT windowStart FROM symphony_article_long_read_user;');
 if (mapped !== String(Math.floor(1793318399999 / 21600000) * 21600000)) throw Error('Old first-read mapping failed');
 sql('USE read_test;\n' + fs.readFileSync('sql/20261030_api_v3_article_reads.sql', 'utf8'));
 sql('USE read_test;ALTER TABLE symphony_article_long_read_user DROP INDEX uk_article_user_window,DROP COLUMN windowStart;');
 sql('USE read_test;\n'+fs.readFileSync('sql/20261030_api_v3_article_reads.sql','utf8'));
 if(sql('USE read_test;SELECT windowStart FROM symphony_article_long_read_user;')!==mapped)throw Error('Auto-created table migration lost original window');
 console.log('PASS article read mysql 0 exact migration preserves old original window, repeat and auto-created missing-index schema');
 sql('USE read_test;CREATE TABLE users(oId VARCHAR(19) PRIMARY KEY,userStatus INT);CREATE TABLE articles(oId VARCHAR(19) PRIMARY KEY,articleAuthorId VARCHAR(19),articleStatus INT,articleType INT,articleAnonymous INT,articleShowInList INT,articleTags VARCHAR(128),articleAnonymousView INT);');
 const port = run('docker', ['port', container, '3306/tcp']);
 if (!/^127\.0\.0\.1:[0-9]+$/.test(port) || port.endsWith(':3306')) throw Error('Unsafe port');
 console.log(run(path.join(home,'bin/java'),['-Drhythm.read.mysqlUrl=jdbc:mysql://'+port+'/read_test?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&useAffectedRows=false&connectTimeout=5000&socketTimeout=20000','-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3ArticleReadMysqlTest']));
 if(settlement){
 const reliability=fs.readFileSync('sql/20260809_profession_pending.sql','utf8'),gate=reliability.match(/CREATE TABLE IF NOT EXISTS `symphony_article_long_read_settlement`[\s\S]*?;/)[0],history=old.match(/CREATE TABLE IF NOT EXISTS `symphony_article_long_read_history`[\s\S]*?;/)[0];
 sql('USE read_test;'+gate+history+`ALTER TABLE users ADD userPoint INT NOT NULL DEFAULT 1000,ADD userUsedPoint INT NOT NULL DEFAULT 0;ALTER TABLE articles ADD articleTitle VARCHAR(128) NOT NULL DEFAULT '';
 CREATE TABLE transfers(oId VARCHAR(19) PRIMARY KEY,fromId VARCHAR(32),toId VARCHAR(32),sum INT,fromBalance INT,toBalance INT,time BIGINT,type INT,dataId VARCHAR(64),memo VARCHAR(128),sourceAppId VARCHAR(64),sourceAppName VARCHAR(64),sourceScene VARCHAR(64),sourceRequestId VARCHAR(128));
 CREATE TABLE transfer_index(oId VARCHAR(19) PRIMARY KEY,userId VARCHAR(32),rawId VARCHAR(19));
 CREATE TABLE events(oId VARCHAR(19) PRIMARY KEY,eventKey VARCHAR(128) UNIQUE,eventType VARCHAR(128),schemaVersion INT,subjectUserId VARCHAR(19),targetType VARCHAR(32),targetId VARCHAR(19),occurredAt BIGINT,capturedAt BIGINT,payloadJson MEDIUMTEXT,visibilityClass VARCHAR(32),sourceSystem VARCHAR(64),status VARCHAR(32),createdAt BIGINT,updatedAt BIGINT);`);
 console.log(run(path.join(home,'bin/java'),['-Drhythm.read.mysqlUrl=jdbc:mysql://'+port+'/read_test?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&useAffectedRows=false&connectTimeout=5000&socketTimeout=20000','-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3ArticleReadSettlementMysqlTest']));
 const settleArgs=['-Drhythm.read.mysqlUrl=jdbc:mysql://'+port+'/read_test?user=root&sslMode=DISABLED&allowPublicKeyRetrieval=true&useAffectedRows=false&connectTimeout=5000&socketTimeout=20000','-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3ArticleReadSettlementMysqlTest'];
 run(path.join(home,'bin/java'),[...settleArgs,'setup']);
 const worker = role => {
  const child = spawn(path.join(home, 'bin/java'), [...settleArgs, 'worker', role, path.join(dir, 'release-settlement')]),
   state = { child, out: '', err: '', done: false };
  child.stdout.on('data', x => state.out += x);
  child.stderr.on('data', x => state.err += x);
  state.finished = new Promise((resolve, reject) => {
   child.on('error', reject);
   child.on('close', code => {
    state.done = true;
    code === 0 ? resolve() : reject(Error(state.out + ' ' + state.err))
   })
  });
  state.finished.catch(() => {});
  children.push(state);
  return state
 };

 const until = async (state, marker) => {
  const end = Date.now() + 15000;
  while (!state.out.includes(marker)) {
   if (state.done || Date.now() > end) throw Error('Worker did not reach ' + marker + ' ' + state.out + ' ' + state.err);
   await new Promise(r => setTimeout(r, 20))
  }
 };
 const holder = worker('holder');
 await until(holder, 'HOLDING_AUTHOR');
 const contender = worker('contender');
 await until(contender, 'STARTING_SETTLEMENT');
 // 第二个 JVM 必须在 InnoDB 中真实等待作者锁。
 let blocked = false;
 const end = Date.now() + 10000;
 while (Date.now() < end) {
  if (Number(sql("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT';")) > 0) {
   blocked = true;
   break
  }
  if (contender.done) throw Error('Contender escaped held author lock');
  await new Promise(r => setTimeout(r, 50))
 }
 if (!blocked) throw Error('No observed InnoDB lock wait');
 fs.writeFileSync(path.join(dir, 'release-settlement'), 'release');
 await Promise.all([holder.finished, contender.finished]);
 if(!holder.out.includes('SETTLEMENT_DONE')||!contender.out.includes('SETTLEMENT_DONE'))throw Error('Settlement worker did not complete');
 console.log(run(path.join(home, 'bin/java'), [...settleArgs, 'verify']));
 console.log('Observed InnoDB LOCK WAIT between independent JVMs before release');

 }

} finally {
 for (const state of children)
  if (!state.done) state.child.kill('SIGKILL');
 await Promise.allSettled(children.map(s => s.finished));
 if (container) {
  run('docker', ['rm', '-f', container]);
  console.log('Disposable read MySQL removed')
 }
 fs.rmSync(dir, { recursive: true, force: true })
}
