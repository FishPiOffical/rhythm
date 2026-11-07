#!/usr/bin/env node
/** Run only after user-confirmed IDEA rebuild/restart. No writes unless API_V3_COMMENT_THANK=1.
 * API_V3_TOKEN needs comments.write and commonThankComment. Optional scope-less/cookie/legacy-key fixtures.
 * API_V3_COMMENT_THANK_ID is another user's disposable comment; enabled success checks spend points permanently.
 * API_V3_SELF_COMMENT_ID and API_V3_PRIVATE_COMMENT_ID are optional denial fixtures.
 */
import assert from 'node:assert/strict';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request(id,status,{credential=token,key='thanks-smoke-denial',body={},cookie='',query=''}={}){
 const response=await fetch(new URL(`/api/v3/comments/${id}/thanks${query}`,base),{method:'POST',redirect:'manual',signal:AbortSignal.timeout(30000),
  headers:{'Content-Type':'application/json','Idempotency-Key':key,'User-Agent':'RhythmV3CommentThankSmoke',...(credential?{Authorization:`Bearer ${credential}`} :{}),...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(body)});
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
 await check('server fields rejected',()=>request('0',422,{body:{authorId:'1',points:1}}));
 await check('request key required',()=>request('0',422,{key:''}));
 if(process.env.API_V3_SELF_COMMENT_ID)await check('self thanks rejected',()=>request(process.env.API_V3_SELF_COMMENT_ID,422));
 if(process.env.API_V3_PRIVATE_COMMENT_ID)await check('unreadable private comment hidden',()=>request(process.env.API_V3_PRIVATE_COMMENT_ID,404));
}
if(process.env.API_V3_COMMENT_THANK==='1'){
 const id=process.env.API_V3_COMMENT_THANK_ID;if(!token||!/^\d{1,19}$/.test(id||''))throw new Error('Explicit thanks requires token and disposable comment ID');
 await check('thanks, same-key replay and new-key duplicate',async()=>{
  const key=`thanks-smoke-${Date.now()}`,first=await request(id,200,{key});
  assert.deepEqual(first,{id,thanked:true});assert.deepEqual(await request(id,200,{key}),first);
  assert.deepEqual(await request(id,200,{key:`${key}-again`}),first);
 });
}
if(failures)process.exitCode=1;
