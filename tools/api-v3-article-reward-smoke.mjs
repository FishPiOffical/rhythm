#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Defaults to missing-target and denial checks.
 * API_V3_TOKEN: content.read + content.write, and commonThankArticle for thanks.
 * API_V3_ARTICLE_THANK_WRITE=1 + API_V3_ARTICLE_THANK_ID explicitly thanks one disposable other-user article.
 * API_V3_ARTICLE_PURCHASE_WRITE=1 + API_V3_ARTICLE_REWARD_ID purchases one disposable other-user article;
 * current article fees are permanent. Optional API_V3_MAX_REWARD_POINT refuses a higher advertised price.
 */
import assert from 'node:assert/strict';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request(path,status,{method='GET',credential=token,key='article-reward-denial',body={},cookie='',query=''}={}){
 const response=await fetch(new URL(`/api/v3${path}${query}`,base),{method,redirect:'manual',signal:AbortSignal.timeout(30000),
  headers:{'User-Agent':'RhythmV3ArticleRewardSmoke',...(credential?{Authorization:`Bearer ${credential}`} :{}),...(cookie?{Cookie:cookie}:{}),
   ...(method==='GET'?{}:{'Content-Type':'application/json','Idempotency-Key':key})},...(method==='GET'?{}:{body:JSON.stringify(body)})});
 assert.equal(response.status,status);assert.ok(response.headers.get('x-request-id'));const json=await response.json();
 if(status===200)assert.equal(json.code,0);else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return json.data;
}
for(const kind of ['thanks','reward'])for(const method of ['GET','POST']){
 const path=`/articles/0/${kind}`;
 await check(`${method} ${kind} requires Bearer`,()=>request(path,401,{method,credential:null}));
 if(process.env.API_V3_SESSION_COOKIE)await check(`${kind} rejects Cookie alone`,()=>request(path,401,{method,credential:null,cookie:process.env.API_V3_SESSION_COOKIE}));
 if(process.env.API_V3_LEGACY_KEY)await check(`${kind} rejects apiKey alone`,()=>request(path,401,{method,credential:null,query:`?apiKey=${encodeURIComponent(process.env.API_V3_LEGACY_KEY)}`}));
 if(process.env.API_V3_SCOPELESS_TOKEN)await check(`${kind} requires scope`,()=>request(path,403,{method,credential:process.env.API_V3_SCOPELESS_TOKEN}));
 if(token)await check(`${method} ${kind} missing target`,()=>request(path,404,{method}));
}
if(token)for(const kind of ['thanks','reward']){
 await check(`${kind} rejects price/identity fields`,()=>request(`/articles/0/${kind}`,422,{method:'POST',body:{point:0,authorId:'1'}}));
 await check(`${kind} requires key`,()=>request(`/articles/0/${kind}`,422,{method:'POST',key:''}));
 await check(`${kind} rejects invalid ID`,()=>request(`/articles/invalid/${kind}`,422));
}
for(const [kind,flag,idKey] of [['thanks','API_V3_ARTICLE_THANK_WRITE','API_V3_ARTICLE_THANK_ID'],['reward','API_V3_ARTICLE_PURCHASE_WRITE','API_V3_ARTICLE_REWARD_ID']]){
 if(process.env[flag]!=='1')continue;
 const id=process.env[idKey];if(!token||!/^\d{1,19}$/.test(id||''))throw new Error('Explicit action requires token and disposable other-user article ID');
 await check(`${kind} state/action/same-key replay/new-key duplicate`,async()=>{
  const path=`/articles/${id}/${kind}`,before=await request(path,200),key=`article-${kind}-${Date.now()}`;
  if(kind==='reward'){
   assert.ok(before.rewardPoint>0);
   const cap=process.env.API_V3_MAX_REWARD_POINT;if(cap!==undefined){assert.match(cap,/^\d{1,10}$/);assert.ok(before.rewardPoint<=Number(cap));}
  }
  const result=await request(path,200,{method:'POST',key});assert.deepEqual(result,{id,[kind==='thanks'?'thanked':'rewarded']:true});
  assert.deepEqual(await request(path,200,{method:'POST',key}),result);assert.deepEqual(await request(path,200,{method:'POST',key:`${key}-again`}),result);
  const after=await request(path,200);assert.equal(after[kind==='thanks'?'thanked':'rewarded'],true);
  if(kind==='thanks')assert.equal(after.thankCount,before.thankCount+(before.thanked?0:1));
  else {const detail=await request(`/articles/${id}`,200);assert.equal(detail.rewarded,true);assert.equal(typeof detail.rewardContent,'string');assert.equal(typeof detail.rewardContentHtml,'string');assert.ok(!Object.hasOwn(detail,'articleIP'));}
 });
}
if(failures)process.exitCode=1;
