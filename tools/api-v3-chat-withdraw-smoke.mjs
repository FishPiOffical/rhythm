#!/usr/bin/env node
/** After confirmed IDEA rebuild/restart. Default probes only reject invalid requests.
 * An actual withdrawal requires API_V3_ALLOW_CHAT_WITHDRAW=1, API_V3_CHAT_MESSAGE_ID,
 * API_V3_CHAT_WITHDRAW_KEY and a disposable account's API_V3_TOKEN (content.write).
 * Keep the original ID/key to resolve uncertain responses. No automatic send or legacy withdrawal.
 */
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;
const root=new URL('api/v3/chat/messages/',base.endsWith('/')?base:base+'/');let failures=0;
async function check(name,work){try{await work();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request({id='0',credential=token,key=randomUUID(),raw,type,status=200}={}){
 const response=await fetch(new URL(id,root),{method:'DELETE',redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3ChatWithdrawSmoke',...(credential?{Authorization:`Bearer ${credential}`}:{ }),...(type?{'Content-Type':type}:{}),...(key?{'Idempotency-Key':key}:{})},...(raw===undefined?{}:{body:raw})});
 assert.equal(response.status,status);assert.ok(response.headers.get('x-request-id'));assert.match(response.headers.get('cache-control'),/no-store/);const value=await response.json();
 if(status===200){assert.equal(value.code,0);assert.deepEqual(Object.keys(value.data).sort(),['cost','id','removed']);assert.equal(value.data.id,id);assert.equal(value.data.removed,true);assert.ok(Number.isSafeInteger(value.data.cost)&&value.data.cost>=0);}
 else{assert.notEqual(value.code,0);assert.equal(value.data,null);}return value;
}
await check('withdrawal requires Bearer',()=>request({credential:null,status:401}));
if(process.env.API_V3_NO_WRITE_SCOPE_TOKEN)await check('withdrawal requires content.write',()=>request({credential:process.env.API_V3_NO_WRITE_SCOPE_TOKEN,status:403}));
if(token){
 await check('body, key and ID reject before withdrawal',async()=>{
  await request({key:null,status:422});await request({id:'bad',status:422});
  await request({type:'text/plain',raw:'{}',status:415});await request({type:'application/json',raw:'{bad}',status:400});
  for(const body of [{apiKey:'legacy'},{userId:'other'},{cost:0}])await request({type:'application/json',raw:JSON.stringify(body),status:422});
 });
 if(process.env.API_V3_ALLOW_CHAT_WITHDRAW==='1')await check('explicit withdrawal, receipt replay and missing detail',async()=>{
  const id=process.env.API_V3_CHAT_MESSAGE_ID,key=process.env.API_V3_CHAT_WITHDRAW_KEY;
  assert.match(id||'',/^[1-9][0-9]{0,31}$/);assert.match(key||'',/^[A-Za-z0-9._:-]{1,128}$/);
  const first=await request({id,key});assert.deepEqual((await request({id,key})).data,first.data);
  await request({id,key:randomUUID(),status:404});
  const credential=process.env.API_V3_CHAT_READ_TOKEN||token;
  const response=await fetch(new URL(id,root),{redirect:'manual',signal:AbortSignal.timeout(20000),headers:{Authorization:`Bearer ${credential}`,'User-Agent':'RhythmV3ChatWithdrawSmoke'}});
  assert.ok([403,404].includes(response.status));
 });
}
process.exitCode=failures?1:0;
