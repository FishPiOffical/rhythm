#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Defaults never send a valid message.
 * API_V3_TOKEN: content.write; API_V3_CHAT_READ_TOKEN: optional content.read credential.
 * A disposable account is required for an actual send. Explicitly provide all of:
 * API_V3_ALLOW_CHAT_SEND=1, API_V3_CHAT_MESSAGE, API_V3_CHAT_IDEMPOTENCY_KEY.
 * Keep that key/body for resolving errors. No automatic revoke or other cleanup (legacy revoke may cost points).
 */
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
const url=new URL('api/v3/chat/messages',base.endsWith('/')?base:base+'/');let failures=0;
async function check(name,work){try{await work();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request({credential=token,body={},raw,type='application/json',key=randomUUID(),status=200}={}){
 const r=await fetch(url,{method:'POST',redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3ChatSendSmoke',...(credential?{Authorization:`Bearer ${credential}`}:{ }),...(type?{'Content-Type':type}:{}),...(key?{'Idempotency-Key':key}:{})},body:raw??JSON.stringify(body)});
 assert.ok((Array.isArray(status)?status:[status]).includes(r.status));assert.ok(r.headers.get('x-request-id'));assert.match(r.headers.get('cache-control'),/no-store/);const json=await r.json();
 if(r.status===200){assert.equal(json.code,0);assert.deepEqual(Object.keys(json.data).sort(),['createdAt','id']);assert.match(json.data.id,/^[0-9]{1,32}$/);assert.ok(Number.isSafeInteger(json.data.createdAt));}
 else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return {status:r.status,code:json.code,data:json.data};
}
await check('ordinary send requires Bearer',()=>request({credential:null,body:{content:'smoke not sent'},status:401}));
if(process.env.API_V3_NO_WRITE_SCOPE_TOKEN)await check('ordinary send requires content.write',()=>request({credential:process.env.API_V3_NO_WRITE_SCOPE_TOKEN,body:{content:'smoke not sent'},status:403}));
if(token){
 await check('invalid body, key and media reject without sending',async()=>{
  for(const body of [{},{content:7},{content:' '},{content:'x'.repeat(4097)},{content:'text',userId:'other'},{content:'text',apiKey:'legacy'},{content:'text',client:7}])await request({body,status:422});
  await request({body:{content:'not sent'},key:null,status:422});await request({body:{content:'not sent'},type:'text/plain',status:415});
  await request({raw:'{"content":"one","content":"two"}',status:400});
 });
 await check('business commands reject without execution',async()=>{
  for(const content of ['[redpacket]{}[/redpacket]','[weather]{}[/weather]','[music]{}[/music]','[setdiscuss]unused[/setdiscuss]','[barrager]{}[/barrager]','执法 unused','zf unused','{"msgType":"redPacket"}'])await request({body:{content},status:422});
 });
 if(process.env.API_V3_ALLOW_CHAT_SEND==='1')await check('explicit ordinary send and same-key replay',async()=>{
  const content=process.env.API_V3_CHAT_MESSAGE,key=process.env.API_V3_CHAT_IDEMPOTENCY_KEY;
  assert.ok(content&&content.trim().length>=5&&content.length<=4096,'provide a disposable-account message');assert.match(key||'',/^[A-Za-z0-9._:-]{1,128}$/);
  const body={content,client:'Other/APIv3Test'};let first=await request({body,key,status:[200,409]});
  if(first.status===409){assert.equal(first.code,'confirmation_required');first=await request({body,key});}
  assert.deepEqual((await request({body,key})).data,first.data);
  const conflict=await request({body:{...body,content:content==='different smoke body'?'another smoke body':'different smoke body'},key,status:409});assert.equal(conflict.code,'idempotency_conflict');
  const credential=process.env.API_V3_CHAT_READ_TOKEN||token;
  const response=await fetch(new URL(`${url.href}/${first.data.id}`),{redirect:'manual',signal:AbortSignal.timeout(20000),headers:{Authorization:`Bearer ${credential}`,'User-Agent':'RhythmV3ChatSendSmoke'}});
  assert.ok([200,403].includes(response.status));if(response.status===200){const value=await response.json();assert.equal(value.data.id,first.data.id);assert.equal(value.data.type,'text');}
 });
}
process.exitCode=failures?1:0;
