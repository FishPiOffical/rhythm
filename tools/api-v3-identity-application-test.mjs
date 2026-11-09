#!/usr/bin/env node
/** 使用 SQLite 与传输适配器测试实际私有认证申请、材料、证明与回执 SQL 及严格 JDBC 事务。 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const cp = [
 'src/main/resources/lib/latke-core.jar',
 process.env.API_V3_CAFFEINE_JAR || `${ide}/lib/intellij.libraries.caffeine.jar`,
 process.env.API_V3_TESTNG_JAR || `${ide}/plugins/testng/lib/testng.jar`,
 process.env.API_V3_SQLITE_JAR || `${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`
].join(path.delimiter);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-v3-identity-')),shims=[];
function shim(name, body) {
 const file = path.join(dir, name + '.java');
 fs.mkdirSync(path.dirname(file), { recursive: true });
 fs.writeFileSync(file, body);
 shims.push(file);
}

function method(source, signature) {
 const begin = source.indexOf(signature);
 if (begin < 0) throw Error(signature);
 let depth = 0;
 for (let i = source.indexOf('{', begin); i < source.length; i++) {
  if (source[i] === '{') depth++;
  if (source[i] === '}' && --depth === 0) return source.slice(begin, i + 1);
 }
 throw Error('Unclosed method');
}

function run(bin, args) {
 const r = spawnSync(bin, args, { stdio: 'inherit' });
 if (r.error) throw r.error;
 if (r.status !== 0) throw Error('Identity application tests failed');
}
try{
 const point=fs.readFileSync('src/main/java/org/b3log/symphony/model/Pointtransfer.java','utf8');
 shim('org/b3log/symphony/util/Symphonys','package org.b3log.symphony.util;public class Symphonys {public static final String[] RESERVED_USER_NAMES={"admin"};public static final long INVITECODE_EXPIRED=259200000L;'+[...new Set([...point.matchAll(/Symphonys\.(POINT_[A-Z_]+)/g)].map(m=>m[1]))].map(k=>'public static final int '+k+'='+((k==='POINT_BUY_INVITECODE'||k==='POINT_CHANGE_USERNAME'||k==='POINT_DATA_EXPORT')?100:0)+';').join('')+'}');
 shim('org/b3log/symphony/repository/UserRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;import org.b3log.latke.repository.RepositoryException;public class UserRepository extends AbstractRepository {public UserRepository(){super("user");}public void updateColumnsInCurrentTransaction(String id,org.json.JSONObject row)throws RepositoryException{throw new UnsupportedOperationException();}public void evictUserNameCaches(String id,String name){}public void refreshSettingsCache(String id)throws RepositoryException{}public void evictSettingsCaches(String id){}public org.json.JSONObject getForUpdate(String id)throws RepositoryException{throw new UnsupportedOperationException();}public org.json.JSONObject getByName(String name)throws RepositoryException{throw new UnsupportedOperationException();}}');
 shim('org/b3log/symphony/repository/PointtransferRedcRepository','package org.b3log.symphony.repository;import org.b3log.latke.repository.AbstractRepository;public class PointtransferRedcRepository extends AbstractRepository {public PointtransferRedcRepository(){super("pointtransfer_redc");}public static void addRecordAsync(String user,String raw){throw new AssertionError("unexpected legacy async indexing");}}');
 const tokens=fs.readFileSync('src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java','utf8');
 shim('org/b3log/symphony/service/ApiV3TokenMgmtService',`package org.b3log.symphony.service;import org.json.JSONObject;import org.apache.commons.lang.StringUtils;import java.util.*;public class ApiV3TokenMgmtService {public static final String WEBSITE_CLIENT="website_v3";public JSONObject authenticate(String value){return null;}${method(tokens,'public static boolean hasScope(')}}`);
 shim('org/b3log/symphony/processor/ApiV3Processor','package org.b3log.symphony.processor;public class ApiV3Processor {public static final String IDENTITY="apiV3Identity";public static String requestId(org.b3log.latke.http.RequestContext c){return "fixture-post-export";}public static void success(org.b3log.latke.http.RequestContext c,Object data){c.attr("test.status",200);c.attr("test.data",data);}public static void error(org.b3log.latke.http.RequestContext c,int status,String code,String msg){c.attr("test.status",status);c.attr("test.code",code);c.attr("test.message",msg);}}');
 shim('org/b3log/symphony/repository/ArticleRepository','package org.b3log.symphony.repository;public class ArticleRepository extends org.b3log.latke.repository.AbstractRepository {public ArticleRepository(){super("article");}}');
 shim('org/b3log/symphony/repository/CommentRepository','package org.b3log.symphony.repository;public class CommentRepository extends org.b3log.latke.repository.AbstractRepository {public CommentRepository(){super("comment");}}');
 const real=['model/UserExt','model/Role','model/Common','model/ApiV3Client','model/ApiV3Token','model/ApiV3AuthorizationRequest','model/ApiV3AuthorizationCode','model/AccountCredentialState','model/OpenIdRefreshToken','model/ApiV3Request','model/Pointtransfer','model/PointtransferSource','repository/ApiV3ClientRepository','repository/ApiV3TokenRepository','repository/ApiV3AuthorizationCodeRepository','repository/ApiV3AuthorizationRequestRepository','repository/AccountCredentialStateRepository','repository/OpenIdRefreshTokenRepository','repository/ApiV3RequestRepository','repository/PointtransferRepository','repository/ApiV3PostExportRepository','repository/ApiV3PostExportChunkRepository','util/ApiV3Requests','util/ApiV3Exception','util/ApiV3Json','util/ApiV3WriteInputs','util/ApiV3Transactions','util/ApiV3TransactionStatements','util/ApiV3MutationStore','util/ApiV3TokenUtil','util/MultiFactorAuthenticator','util/ApiV3SecurityInputs','util/AccountPairLocks','util/ArticlePublicationCaches','util/ApiV3PostExportFiles','service/AccountCredentialMgmtService','service/ApiV3SecurityProofService','service/ApiV3SecuritySettingsService','service/ApiV3IdempotencyMgmtService','service/PointtransferMgmtService','service/ApiV3PostExportSnapshotService','service/ApiV3PostExportService','processor/ApiV3SecurityProcessor','repository/ApiV3IdentityApplicationRepository','repository/ApiV3IdentityMaterialRepository','repository/ApiV3IdentityReviewerRepository','util/ApiV3OAuthTransportLogFilter','util/ApiV3IdentityImage','service/ApiV3IdentityApplicationService','processor/ApiV3IdentityApplicationProcessor','processor/ApiV3PostExportProcessor','processor/middleware/ApiV3AuthMidware','processor/middleware/ApiV3WriteMidware'];
 run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',dir,...shims,'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java',...[...new Set(real)].map(s=>'src/main/java/org/b3log/symphony/'+s+'.java'),'src/test/java/org/b3log/symphony/service/ApiV3SecurityTest.java','src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationTest.java']);
 run(path.join(home,'bin','java'),['-cp',[dir,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3IdentityApplicationTest']);
} finally {
 fs.rmSync(dir, { recursive: true, force: true });
}
