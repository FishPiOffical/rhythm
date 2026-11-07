#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Default probes reject invalid requests.
 * Real topic changes cost 16 points. Explicitly set API_V3_ALLOW_CHAT_TOPIC=1,
 * API_V3_CHAT_TOPIC_TEXT, API_V3_CHAT_TOPIC_KEY and a disposable account's API_V3_TOKEN.
 * Preserve the original key/body after uncertain responses. No automatic legacy changes.
 */
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
const endpoint=new URL('api/v3/chat/topic',base.endsWith('/')?base:base+'/');let failures=0;
async function check(name,work){try{await work();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request({credential=token,key=randomUUID(),body={},type='application/json',status=200}={}){
 const r=await fetch(endpoint,{method:'PUT',redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3ChatTopicSmoke',...(credential?{Authorization:`Bearer ${credential}`}:{ }),...(key?{'Idempotency-Key':key}:{}),'Content-Type':type},body:JSON.stringify(body)});
 assert.equal(r.status,status);assert.ok(r.headers.get('x-request-id'));assert.match(r.headers.get('cache-control'),/no-store/);const result=await r.json();
 if(status===200){assert.equal(result.code,0);assert.deepEqual(Object.keys(result.data).sort(),['cost','revision','text']);assert.equal(result.data.cost,16);assert.match(result.data.revision,/^[1-9][0-9]*$/);}else{assert.notEqual(result.code,0);assert.equal(result.data,null);}return result;
}
await check('topic requires Bearer',()=>request({credential:null,status:401}));
if(process.env.API_V3_NO_WRITE_SCOPE_TOKEN)await check('topic requires content.write',()=>request({credential:process.env.API_V3_NO_WRITE_SCOPE_TOKEN,status:403}));
if(token){
 await check('invalid topic requests do not change state',async()=>{
  for(const body of [{},{text:''},{text:'a'.repeat(65)},{text:'x',cost:0},{text:'x',apiKey:'legacy'}])await request({body,status:422});
  await request({body:{text:'x'},key:null,status:422});await request({type:'text/plain',status:415});
 });
 if(process.env.API_V3_ALLOW_CHAT_TOPIC==='1')await check('explicit topic change and original-key replay',async()=>{
  const text=process.env.API_V3_CHAT_TOPIC_TEXT,key=process.env.API_V3_CHAT_TOPIC_KEY;assert.ok(text&&text.length<=64);assert.match(key||'',/^[A-Za-z0-9._:-]{1,128}$/);
  const first=await request({key,body:{text}});assert.deepEqual((await request({key,body:{text}})).data,first.data);
  await request({key,body:{text:text==='other'?'another':'other'},status:409});
 });
}
process.exitCode=failures?1:0;
