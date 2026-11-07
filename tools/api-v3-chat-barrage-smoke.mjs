#!/usr/bin/env node
/** After confirmed IDEA rebuild/restart. Default probes cannot send a valid barrage.
 * Sending costs the current price. Explicitly set API_V3_ALLOW_CHAT_BARRAGE=1,
 * API_V3_CHAT_BARRAGE_CONTENT, API_V3_CHAT_BARRAGE_KEY and a disposable API_V3_TOKEN.
 * Preserve the same key/body after uncertain responses. Does not alter prices or call old sends.
 */
import assert from 'node:assert/strict';import {randomUUID} from 'node:crypto';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
const endpoint=new URL('api/v3/chat/barrager',base.endsWith('/')?base:base+'/');let failures=0;
async function check(name,work){try{await work();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request({credential=token,key=randomUUID(),body={},type='application/json',status=200}={}){
 const r=await fetch(endpoint,{method:'POST',redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3ChatBarrageSmoke',...(credential?{Authorization:`Bearer ${credential}`}:{ }),...(key?{'Idempotency-Key':key}:{}),'Content-Type':type},body:JSON.stringify(body)});
 assert.equal(r.status,status);assert.ok(r.headers.get('x-request-id'));assert.match(r.headers.get('cache-control'),/no-store/);const value=await r.json();
 if(status===200){assert.equal(value.code,0);assert.deepEqual(Object.keys(value.data).sort(),['cost','createdAt','id','unit']);assert.match(value.data.id,/^[0-9a-f-]{36}$/);assert.ok(Number.isSafeInteger(value.data.cost)&&value.data.cost>=0);assert.ok(Number.isSafeInteger(value.data.createdAt));assert.equal(typeof value.data.unit,'string');}
 else{assert.notEqual(value.code,0);assert.equal(value.data,null);}return value;
}
await check('barrage requires Bearer',()=>request({credential:null,status:401}));
if(process.env.API_V3_NO_WRITE_SCOPE_TOKEN)await check('barrage requires content.write',()=>request({credential:process.env.API_V3_NO_WRITE_SCOPE_TOKEN,status:403}));
if(token){
 await check('invalid requests do not send or charge',async()=>{
  for(const body of [{},{content:''},{content:'x'.repeat(33)},{content:'<script>x</script>'},{content:'Hi',color:'red'},{content:'Hi',cost:0},{content:'Hi',apiKey:'legacy'}])await request({body,status:422});
  await request({body:{content:'Hi'},key:null,status:422});await request({type:'text/plain',status:415});
 });
 if(process.env.API_V3_ALLOW_CHAT_BARRAGE==='1')await check('explicit barrage, original receipt replay and body conflict',async()=>{
  const content=process.env.API_V3_CHAT_BARRAGE_CONTENT,key=process.env.API_V3_CHAT_BARRAGE_KEY;assert.ok(content&&content.length<=32);assert.match(key||'',/^[A-Za-z0-9._:-]{1,128}$/);
  const body={content,color:'#FFFFFF'},first=await request({key,body});assert.deepEqual((await request({key,body})).data,first.data);await request({key,body:{...body,color:'#000000'},status:409});
 });
}
process.exitCode=failures?1:0;
