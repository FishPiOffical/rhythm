#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Default checks never delete existing content.
 * API_V3_TOKEN needs comments.write and commonRemoveComment.
 * API_V3_COMMENT_DELETE=1 + API_V3_COMMENT_DELETE_ID explicitly deletes one disposable owned comment;
 * the original deletion fee is permanent. Optional OTHER_COMMENT_ID and BLOCKED_COMMENT_ID are denial fixtures.
 */
import assert from 'node:assert/strict';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request(id,status,{credential=token,key='comment-delete-denial',body={},cookie='',query=''}={}){
 const response=await fetch(new URL(`/api/v3/comments/${id}${query}`,base),{method:'DELETE',redirect:'manual',signal:AbortSignal.timeout(30000),
  headers:{'Content-Type':'application/json','Idempotency-Key':key,'User-Agent':'RhythmV3CommentDeleteSmoke',...(credential?{Authorization:`Bearer ${credential}`} :{}),...(cookie?{Cookie:cookie}:{})},body:JSON.stringify(body)});
 assert.equal(response.status,status);assert.ok(response.headers.get('x-request-id'));
 const json=await response.json();if(status===200)assert.equal(json.code,0);else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return json.data;
}
await check('Bearer required',()=>request('0',401,{credential:null}));
if(process.env.API_V3_SESSION_COOKIE)await check('Cookie alone rejected',()=>request('0',401,{credential:null,cookie:process.env.API_V3_SESSION_COOKIE}));
if(process.env.API_V3_LEGACY_KEY)await check('legacy key alone rejected',()=>request('0',401,{credential:null,query:`?apiKey=${encodeURIComponent(process.env.API_V3_LEGACY_KEY)}`}));
if(process.env.API_V3_SCOPELESS_TOKEN)await check('comments.write required',()=>request('0',403,{credential:process.env.API_V3_SCOPELESS_TOKEN}));
if(token){
 await check('missing comment',()=>request('0',404));
 await check('invalid ID rejected',()=>request('invalid',422));
 await check('unknown fields rejected',()=>request('0',422,{body:{force:true,fee:0}}));
 await check('request key required',()=>request('0',422,{key:''}));
 if(process.env.API_V3_OTHER_COMMENT_ID)await check('other owner hidden',()=>request(process.env.API_V3_OTHER_COMMENT_ID,404));
 if(process.env.API_V3_BLOCKED_COMMENT_ID)await check('interactions prevent deletion',()=>request(process.env.API_V3_BLOCKED_COMMENT_ID,422));
}
if(process.env.API_V3_COMMENT_DELETE==='1'){
 const id=process.env.API_V3_COMMENT_DELETE_ID;if(!token||!/^\d{1,19}$/.test(id||''))throw new Error('Explicit deletion requires token and a disposable owned comment ID');
 await check('delete once, replay after removal and reject new-key missing target',async()=>{
  const key=`comment-delete-${Date.now()}`,first=await request(id,200,{key});
  assert.deepEqual(first,{id,removed:true});assert.deepEqual(await request(id,200,{key}),first);
  await request(id,404,{key:`${key}-new`});
 });
}
if(failures)process.exitCode=1;
