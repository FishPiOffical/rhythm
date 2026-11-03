#!/usr/bin/env node
/** Real comment mutations, delayed notifications and v3 votes with SQLite runtime adapters.
 * Does not exercise the application IoC, real repositories/caches, points service, MySQL locks or HTTP.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide='/Applications/Android Studio.app/Contents',home=process.env.JAVA_HOME||`${ide}/jbr/Contents/Home`;
const testng=process.env.API_V3_TESTNG_JAR||`${ide}/plugins/testng/lib/testng.jar`;
const sqlite=process.env.API_V3_SQLITE_JAR||`${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
const suffix=process.platform==='win32'?'.exe':'',latke='src/main/resources/lib/latke-core.jar';
const java=path.join(home,'bin',`java${suffix}`),javac=path.join(home,'bin',`javac${suffix}`);
for(const file of [java,javac,testng,sqlite,latke])if(!fs.existsSync(file))throw new Error('Configure Java 25, TestNG and SQLite paths');
const work=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-comment-removal-test-'));
function run(command,args){const result=spawnSync(command,args,{stdio:'inherit'});if(result.error)throw result.error;if(result.status!==0)throw new Error('Comment removal tests failed');}
try {
 const sources=[];
 function shim(name,source){const file=path.join(work,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,source);sources.push(file);}
 const base='org.b3log.symphony.service.CommentRemovalServiceTest';
 const reactionSource=fs.readFileSync('src/main/java/org/b3log/symphony/repository/ReactionRepository.java','utf8');
 const reactionStart=reactionSource.indexOf('    public void removeTargetInCurrentTransaction(');
 const reactionEnd=reactionSource.indexOf('    public JSONObject lockEntity(',reactionStart);
 if(reactionStart<0||reactionEnd<0)throw new Error('Reaction removal source boundary missing');
 for(const [name,table] of Object.entries({Comment:'comment',Article:'article',User:'user',Option:'option',Reward:'reward',Vote:'vote',Reaction:'reaction',Revision:'revision',Notification:'notification',Tag:'tag',TagArticle:'tag_article'})) {
   const extra=name==='Comment'?`public org.json.JSONObject getOwnedEditTarget(String id,String owner)throws org.b3log.latke.repository.RepositoryException{return first(select(org.b3log.symphony.util.CommentEditing.ownedSql(getName(),false),id,owner));}
     public org.json.JSONObject getOwnedForUpdate(String id,String owner)throws org.b3log.latke.repository.RepositoryException{return first(select(org.b3log.symphony.util.CommentEditing.ownedSql(getName(),true),id,owner));}`
     :name==='Reward'?`public boolean existsForUpdate(String id,int type)throws org.b3log.latke.repository.RepositoryException{if(!hasTransactionBegun())throw new IllegalStateException();return !select("SELECT oId FROM reward WHERE dataId=? AND type=? LIMIT 1 FOR UPDATE",id,type).isEmpty();}`
     :name==='Article'?`public String getCommentPublicationAuthor(String id)throws org.b3log.latke.repository.RepositoryException{var rows=select("SELECT articleAuthorId FROM article WHERE oId=? LIMIT 1",id);return rows.isEmpty()?"":rows.getFirst().optString("articleAuthorId");}`
     :name==='Tag'?`public org.json.JSONObject getByTitle(String title)throws org.b3log.latke.repository.RepositoryException{return first(select("SELECT * FROM tag WHERE tagTitle=? LIMIT 1 FOR UPDATE",title));}`
     :name==='Reaction'?reactionSource.slice(reactionStart,reactionEnd):'';
   shim(`org/b3log/symphony/repository/${name}Repository.java`,`package org.b3log.symphony.repository; import org.b3log.latke.Keys; import org.b3log.latke.repository.RepositoryException; import org.b3log.symphony.model.Reaction; import org.json.JSONObject; public class ${name}Repository extends ${base}.Rows {public ${name}Repository(){super("${table}");} ${extra}}`);
 }
 shim('org/b3log/symphony/service/ApiV3ContentQueryService.java',`package org.b3log.symphony.service; public class ApiV3ContentQueryService extends org.b3log.symphony.service.CommentNotificationServiceTest.Visibility {}`);
 shim('org/b3log/symphony/service/RoleQueryService.java',`package org.b3log.symphony.service; public class RoleQueryService { public java.util.Set<String> getPermissions(String role){return "noThank".equals(role)?java.util.Set.of():java.util.Set.of(org.b3log.symphony.model.Permission.PERMISSION_ID_C_COMMON_THANK_ARTICLE);} }`);
 for(const [name,parent] of Object.entries({PointtransferMgmtService:'Payments',ProfessionSourceEventCaptureService:'Professions',NotificationMgmtService:'NotificationEvents',LivenessMgmtService:'ActivityEvents'})) {
   shim(`org/b3log/symphony/service/${name}.java`,`package org.b3log.symphony.service; public class ${name} extends ${base}.${parent} {}`);
 }
 shim('org/b3log/symphony/service/ArticleMgmtService.java',`package org.b3log.symphony.service; public class ArticleMgmtService {
   public <T>T withPublicationLock(String actor,java.util.concurrent.Callable<T> operation,java.util.function.Consumer<T> after)throws Exception {
     return withPublicationLocks(actor,java.util.List.of(org.b3log.symphony.model.Pointtransfer.ID_C_SYS),operation,after);
   }
   public <T>T withPublicationLocks(String actor,java.util.List<String> destinations,java.util.concurrent.Callable<T> operation,java.util.function.Consumer<T> after)throws Exception {
     return org.b3log.symphony.util.AccountPairLocks.execute(destinations.stream().map(to->new org.b3log.symphony.util.AccountPairLocks.Pair(actor,to)).toList(),()->{
       T result;try(var scope=org.b3log.symphony.util.ArticlePublicationCaches.open(()->false)){result=operation.call();}after.accept(result);return result;
     });
   }
 }`);
 // These two models have unrelated rendering/configuration dependencies; copy only their literal contract constants.
 for(const [name,fields] of Object.entries({Tag:['TAG_COMMENT_CNT','TAG_RANDOM_DOUBLE'],Pointtransfer:['ID_C_SYS','TRANSFER_TYPE_C_DEL_COMMENT','TRANSFER_TYPE_C_COMMENT_REWARD','TRANSFER_TYPE_C_QNA_OFFER','TRANSFER_TYPE_C_ARTICLE_THANK','TRANSFER_TYPE_C_ARTICLE_REWARD','TRANSFER_SUM_C_ARTICLE_THANK'],Liveness:['LIVENESS_THANK','LIVENESS_ACCEPT_ANSWER','LIVENESS_VOTE','LIVENESS_REWARD']})) {
   const source=fs.readFileSync(`src/main/java/org/b3log/symphony/model/${name}.java`,'utf8');
   const declarations=fields.map(field=>{
     const match=source.match(new RegExp(`public static final (String|int)\\s+${field}\\s*=\\s*("[^"]*"|[0-9]+|Symphonys\\.[A-Za-z0-9_]+);`));
     if(!match)throw new Error(`Expected literal model constant ${name}.${field}`);
     return match[0];
   });
   shim(`org/b3log/symphony/model/${name}.java`,`package org.b3log.symphony.model; import org.b3log.symphony.util.Symphonys; public class ${name} {${declarations.join('\n')}}`);
 }
 shim('org/b3log/symphony/util/Symphonys.java',`package org.b3log.symphony.util; public class Symphonys {
   public static final String[] RESERVED_USER_NAMES={}; private static final java.util.Properties props=new java.util.Properties();
   static {try(var in=new java.io.FileInputStream("src/main/resources/symphony.properties")){props.load(in);}catch(Exception e){throw new IllegalStateException(e);}}
   public static int getInt(String key){return Integer.parseInt(props.getProperty(key,"0"));}
   public static final int POINT_DELETE_COMMENT=getInt("pointDelComment");
   public static final int POINT_THANK_COMMENT=getInt("pointThankComment");
   public static final int POINT_THANK_ARTICLE=getInt("pointThankArticle");
 }`);
 // Compile the exact legacy endpoint methods against explicit HTTP/rendering adapters.
 const legacySource=fs.readFileSync('src/main/java/org/b3log/symphony/processor/ArticleProcessor.java','utf8');
 const legacyStart=legacySource.indexOf('    public void rewardArticle(final RequestContext context) {');
 const legacyEnd=legacySource.indexOf('    /**\n     * Article thanks.',legacyStart);
 if(legacyStart<0||legacyEnd<0)throw new Error('Legacy reward endpoint source boundary missing');
 shim('org/b3log/symphony/service/LegacyArticleRewardEndpoint.java',`package org.b3log.symphony.service;
 import org.json.JSONObject; import org.b3log.latke.Keys; import org.b3log.latke.model.User;
 import org.b3log.latke.service.ServiceException; import org.b3log.symphony.model.*;
 import org.b3log.symphony.util.ApiV3ContentUtil; import java.util.*;
 public class LegacyArticleRewardEndpoint extends LegacyArticleRewardEndpointTest.Adapters {
 ${legacySource.slice(legacyStart,legacyEnd)}
 }`);
 const real=['model/Article','model/Comment','model/Common','model/Notification','model/UserExt','model/Role','model/Permission','model/Option','model/Reward','model/Vote','model/Revision','model/Reaction','model/PointtransferSource',
   'util/CommentRemoval','util/CheckinPatchMutation','util/CommentRemovalQueries','util/ContentNotificationReferences','util/CommentEditing','util/ArticlePublicationCaches','util/AccountPairLocks','util/ApiV3ContentUtil','util/ApiV3WriteInputs','util/ApiV3Votes','service/CommentRemovalMgmtService','service/CommentThankMgmtService','service/CommentAcceptMgmtService','service/CommentVoteMgmtService','service/CommentNotificationMgmtService','service/ApiV3CommentVoteService','service/VoteMgmtService','service/ApiV3ArticleVoteService','service/ArticleRewardMgmtService','service/ApiV3ArticleRewardService',
   'model/ApiV3Request','repository/ApiV3RequestRepository','util/ApiV3MutationStore','util/ApiV3Exception','util/ApiV3Json','util/ApiV3Transactions', 'util/ApiV3TokenUtil','service/ApiV3IdempotencyMgmtService'];
 const classes=path.join(work,'classes'),cp=[latke,testng,sqlite].join(path.delimiter);
 run(javac,['--release','25', 'src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java','-proc:none','-cp',cp,'-d',classes,...sources,...real.map(name=>`src/main/java/org/b3log/symphony/${name}.java`),'src/test/java/org/b3log/symphony/service/CommentRemovalServiceTest.java','src/test/java/org/b3log/symphony/service/CommentThankServiceTest.java','src/test/java/org/b3log/symphony/service/CommentAcceptServiceTest.java','src/test/java/org/b3log/symphony/service/CommentVoteServiceTest.java','src/test/java/org/b3log/symphony/service/CommentNotificationServiceTest.java','src/test/java/org/b3log/symphony/service/ApiV3CommentVoteServiceTest.java','src/test/java/org/b3log/symphony/service/ApiV3ArticleVoteServiceTest.java','src/test/java/org/b3log/symphony/service/ApiV3ArticleRewardServiceTest.java','src/test/java/org/b3log/symphony/service/LegacyArticleRewardEndpointTest.java','src/test/java/org/b3log/symphony/util/CheckinPatchMutationTest.java']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.CommentRemovalServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.CommentThankServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.CommentAcceptServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.CommentVoteServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.CommentNotificationServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3CommentVoteServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3ArticleVoteServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.ApiV3ArticleRewardServiceTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.service.LegacyArticleRewardEndpointTest']);
 run(java,['-cp',[classes,cp].join(path.delimiter),'org.b3log.symphony.util.CheckinPatchMutationTest']);
}finally{fs.rmSync(work,{recursive:true,force:true});}
