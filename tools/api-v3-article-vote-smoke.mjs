#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Default mode only reads missing targets/checks denials.
 * API_V3_TOKEN: content.read + content.write. Optional scope-less/cookie/legacy-key denial fixtures.
 * API_V3_ARTICLE_VOTE_WRITE=1 + API_V3_ARTICLE_VOTE_ID explicitly changes one disposable other-user article's
 * vote through up/down/clear and leaves it cleared; may create one notification/activity entry.
 */
import assert from 'node:assert/strict';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request(id,status,{method='GET',credential=token,key='article-vote-denial',body=method==='DELETE'?{}:{vote:'up'},cookie='',query=''}={}){
 const response=await fetch(new URL(`/api/v3/articles/${id}/vote${query}`,base),{method,redirect:'manual',signal:AbortSignal.timeout(30000),
  headers:{'User-Agent':'RhythmV3ArticleVoteSmoke',...(credential?{Authorization:`Bearer ${credential}`} :{}),...(cookie?{Cookie:cookie}:{}),
   ...(method==='GET'?{}:{'Content-Type':'application/json','Idempotency-Key':key})},...(method==='GET'?{}:{body:JSON.stringify(body)})});
 assert.equal(response.status,status);assert.ok(response.headers.get('x-request-id'));
 const json=await response.json();if(status===200)assert.equal(json.code,0);else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return json.data;
}
for(const method of ['GET','PUT','DELETE']){
 await check(`${method} requires Bearer`,()=>request('0',401,{method,credential:null}));
 if(process.env.API_V3_SESSION_COOKIE)await check(`${method} rejects Cookie alone`,()=>request('0',401,{method,credential:null,cookie:process.env.API_V3_SESSION_COOKIE}));
 if(process.env.API_V3_LEGACY_KEY)await check(`${method} rejects legacy key alone`,()=>request('0',401,{method,credential:null,query:`?apiKey=${encodeURIComponent(process.env.API_V3_LEGACY_KEY)}`}));
 if(process.env.API_V3_SCOPELESS_TOKEN)await check(`${method} requires scope`,()=>request('0',403,{method,credential:process.env.API_V3_SCOPELESS_TOKEN}));
 if(token)await check(`${method} missing target`,()=>request('0',404,{method}));
}
if(token){
 for(const vote of [null,0,true,'UP','up ',''])await check(`PUT rejects invalid vote ${typeof vote}`,()=>request('0',422,{method:'PUT',body:{vote}}));
 await check('PUT rejects unknown fields',()=>request('0',422,{method:'PUT',body:{vote:'up',authorId:'1'}}));
 await check('PUT requires key',()=>request('0',422,{method:'PUT',key:''}));
 await check('DELETE rejects fields',()=>request('0',422,{method:'DELETE',body:{vote:null}}));
 await check('ID format rejected',()=>request('invalid',422));
 if(process.env.API_V3_UNREADABLE_ARTICLE_ID)await check('unreadable article hidden',()=>request(process.env.API_V3_UNREADABLE_ARTICLE_ID,404));
}
if(process.env.API_V3_ARTICLE_VOTE_WRITE==='1'){
 const id=process.env.API_V3_ARTICLE_VOTE_ID;if(!token||!/^\d{1,19}$/.test(id||''))throw new Error('Explicit write requires token and disposable other-user article ID');
 await check('set/read/replay/change/clear with stable receipt and no toggle',async()=>{
  const key=`article-vote-${Date.now()}`,first=await request(id,200,{method:'PUT',key});
  assert.deepEqual(Object.keys(first).sort(),['downCount','id','upCount','vote']);assert.equal(first.vote,'up');
  assert.deepEqual(await request(id,200),first);assert.deepEqual(await request(id,200,{method:'PUT',key}),first);
  assert.deepEqual(await request(id,200,{method:'PUT',key:`${key}-same`}),first);
  const down=await request(id,200,{method:'PUT',key:`${key}-down`,body:{vote:'down'}});assert.equal(down.vote,'down');
  assert.deepEqual(await request(id,200,{method:'PUT',key}),first);assert.deepEqual(await request(id,200),down);
  await request(id,409,{method:'PUT',key,body:{vote:'down'}});await request(id,409,{method:'DELETE',key});
  const cleared=await request(id,200,{method:'DELETE',key:`${key}-clear`});assert.equal(cleared.vote,null);
  assert.deepEqual(await request(id,200),cleared);assert.deepEqual(await request(id,200,{method:'DELETE',key:`${key}-clear`}),cleared);
  assert.deepEqual(await request(id,200,{method:'DELETE',key:`${key}-clear-again`}),cleared);
 });
}
if(failures)process.exitCode=1;
