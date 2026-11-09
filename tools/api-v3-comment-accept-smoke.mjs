#!/usr/bin/env node
/** 确认 IDEA 重新编译或重启后运行，默认验证拒绝路径。
 * API_V3_TOKEN 需要 comments.write。API_V3_COMMENT_ACCEPT=1 与 API_V3_COMMENT_ACCEPT_ID
 * 显式采纳令牌所有者文章中的临时回答，可能永久转移积分。
 * API_V3_OTHER_CANDIDATE_ID 必须是同一篇文章的另一条回答，用于验证采纳冲突。
 * 可选夹具覆盖缺少权限、Cookie、旧密钥、本人评论和其他文章的评论。
 */
import assert from 'node:assert/strict';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request(id,status,{credential=token,key='accept-smoke-denial',body={},cookie='',query=''}={}){
 const response=await fetch(new URL(`/api/v3/comments/${id}/accept${query}`,base),{method:'POST',redirect:'manual',signal:AbortSignal.timeout(30000),
  headers:{'Content-Type':'application/json','Idempotency-Key':key,'User-Agent':'RhythmV3CommentAcceptSmoke',...(credential?{Authorization:`Bearer ${credential}`} :{}),...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(body)});
 assert.equal(response.status,status);assert.ok(response.headers.get('x-request-id'));
 const json=await response.json();if(status===200)assert.equal(json.code,0);else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return json.data;
}
await check('Bearer required',()=>request('0',401,{credential:null}));
if(process.env.API_V3_SESSION_COOKIE)await check('Cookie alone rejected',()=>request('0',401,{credential:null,cookie:process.env.API_V3_SESSION_COOKIE}));
if(process.env.API_V3_LEGACY_KEY)await check('legacy apiKey alone rejected',()=>request('0',401,{credential:null,query:`?apiKey=${encodeURIComponent(process.env.API_V3_LEGACY_KEY)}`}));
if(process.env.API_V3_SCOPELESS_TOKEN)await check('comments.write required',()=>request('0',403,{credential:process.env.API_V3_SCOPELESS_TOKEN}));
if(token){
 await check('missing comment',()=>request('0',404));
 await check('invalid target ID',()=>request('invalid',422));
 await check('server fields rejected',()=>request('0',422,{body:{authorId:'1',offerPoint:1}}));
 await check('request key required',()=>request('0',422,{key:''}));
 if(process.env.API_V3_SELF_COMMENT_ID)await check('own answer rejected',()=>request(process.env.API_V3_SELF_COMMENT_ID,422));
 if(process.env.API_V3_FOREIGN_ARTICLE_COMMENT_ID)await check('article owner required',()=>request(process.env.API_V3_FOREIGN_ARTICLE_COMMENT_ID,403));
 if(process.env.API_V3_NON_QUESTION_COMMENT_ID)await check('non-question acceptance rejected',()=>request(process.env.API_V3_NON_QUESTION_COMMENT_ID,422,{key:`non-question-${Date.now()}`}));
 if(process.env.API_V3_LOW_OFFER_COMMENT_ID)await check('question below 20 points rejected',()=>request(process.env.API_V3_LOW_OFFER_COMMENT_ID,422,{key:`low-offer-${Date.now()}`}));
}
if(process.env.API_V3_COMMENT_ACCEPT==='1'){
 const id=process.env.API_V3_COMMENT_ACCEPT_ID;if(!token||!/^\d{1,19}$/.test(id||''))throw new Error('Explicit acceptance requires token and disposable answer ID');
 await check('accept, same-key replay and new-key duplicate',async()=>{
  const key=`accept-smoke-${Date.now()}`,first=await request(id,200,{key});
  assert.deepEqual(first,{id,accepted:true});assert.deepEqual(await request(id,200,{key}),first);
  assert.deepEqual(await request(id,200,{key:`${key}-again`}),first);
  if(process.env.API_V3_OTHER_CANDIDATE_ID)await request(process.env.API_V3_OTHER_CANDIDATE_ID,409,{key:`${key}-other`});
 });
}
if(failures)process.exitCode=1;
