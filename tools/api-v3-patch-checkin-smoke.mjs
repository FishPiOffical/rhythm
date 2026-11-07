#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Default: denials/read-only checks.
 * API_V3_TOKEN needs activity.read + activity.write. Optional API_V3_READ_ONLY_TOKEN checks scope rejection.
 * API_V3_ALLOW_PATCH_USE=1 permanently consumes a card on a disposable eligible account; no undo.
 */
import assert from 'node:assert/strict';import {randomUUID} from 'node:crypto';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN,path='/me/bag/patchCheckinCard/use';let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request(route,{method='GET',body={},key,credential=token,status=200}={}){
 const response=await fetch(new URL(`/api/v3${route}`,base),{method,redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3PatchSmoke',...(credential?{Authorization:`Bearer ${credential}`}:{ }),...(method==='GET'?{}:{'Content-Type':'application/json'}),...(key?{'Idempotency-Key':key}:{})},...(method==='GET'?{}:{body:JSON.stringify(body)})});
 assert.ok((Array.isArray(status)?status:[status]).includes(response.status));assert.ok(response.headers.get('x-request-id'));const json=await response.json();
 if(response.status===200)assert.equal(json.code,0);else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return {status:response.status,data:json.data};
}
await check('patch requires Bearer',()=>request(path,{method:'POST',credential:null,key:randomUUID(),status:401}));
if(process.env.API_V3_READ_ONLY_TOKEN)await check('patch requires activity.write',()=>request(path,{method:'POST',credential:process.env.API_V3_READ_ONLY_TOKEN,key:randomUUID(),status:403}));
const counts=bag=>Object.fromEntries(bag.list.map(row=>[row.code,row.count]));
if(token){
 await check('patch rejects custom date/user/count and missing key',async()=>{
  for(const body of [{date:20260301},{userId:'other'},{count:2}])await request(path,{method:'POST',body,key:randomUUID(),status:422});
  await request(path,{method:'POST',status:422});
 });
 if(process.env.API_V3_ALLOW_PATCH_USE==='1')await check('patch or unavailable leaves one consistent card/streak result',async()=>{
  const before=counts((await request('/me/bag')).data),key=randomUUID();
  const first=await request(path,{method:'POST',key,status:[200,409]});const after=counts((await request('/me/bag')).data);
  if(first.status===409){assert.deepEqual(after,before);return;}
  const value=first.data;assert.deepEqual(Object.keys(value).sort(),['bag','itemCode','longestStreak','streak','streakEnd','streakStart']);assert.equal(value.itemCode,'patchCheckinCard');
  assert.equal(after.patchCheckinCard,before.patchCheckinCard-1);assert.ok(!('patchStart'in after));assert.equal(counts(value.bag).patchCheckinCard,after.patchCheckinCard);
  for(const [code,count]of Object.entries(before))if(!['patchCheckinCard','patchStart'].includes(code))assert.equal(after[code],count);
  assert.deepEqual((await request(path,{method:'POST',key})).data,value);
  assert.equal((await request('/me/checkin')).data.streak,value.streak);
  await request(path,{method:'POST',key:randomUUID(),status:409});assert.deepEqual(counts((await request('/me/bag')).data),after);
 });
}
process.exitCode=failures?1:0;
