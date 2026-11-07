#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Default checks do not rename.
 * API_V3_TOKEN: activity.read + activity.write + settings.write.
 * API_V3_ACTIVITY_ONLY_TOKEN / API_V3_SETTINGS_ONLY_TOKEN: optional missing-scope checks.
 * API_V3_ALLOW_NAME_CARD_USE=1 and API_V3_NEW_USER_NAME explicitly permit one rename/card consumption.
 * Use a disposable account. No automatic rename back (that would consume a second card).
 */
import assert from 'node:assert/strict';import {randomUUID} from 'node:crypto';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN,path='/me/bag/nameCard/use';let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request(route,{method='GET',body={},key,credential=token,status=200}={}){
 const r=await fetch(new URL(`/api/v3${route}`,base),{method,redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3NameSmoke',...(credential?{Authorization:`Bearer ${credential}`}:{ }),...(method==='GET'?{}:{'Content-Type':'application/json'}),...(key?{'Idempotency-Key':key}:{})},...(method==='GET'?{}:{body:JSON.stringify(body)})});
 assert.ok((Array.isArray(status)?status:[status]).includes(r.status));assert.ok(r.headers.get('x-request-id'));const json=await r.json();
 if(r.status===200)assert.equal(json.code,0);else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return {status:r.status,data:json.data};
}
await check('name card requires Bearer',()=>request(path,{method:'POST',body:{userName:'Fresh-Name'},credential:null,key:randomUUID(),status:401}));
for(const [name,credential]of [['activity only',process.env.API_V3_ACTIVITY_ONLY_TOKEN],['settings only',process.env.API_V3_SETTINGS_ONLY_TOKEN]])if(credential)await check(`${name} cannot rename`,()=>request(path,{method:'POST',body:{userName:'Fresh-Name'},credential,key:randomUUID(),status:403}));
const counts=bag=>Object.fromEntries(bag.list.map(row=>[row.code,row.count]));
if(token){
 await check('invalid body and missing key do not rename',async()=>{
  for(const body of [{userName:'bad_name'},{userName:'Fresh-Name',userId:'other'},{userName:'Fresh-Name',count:2},{}])await request(path,{method:'POST',body,key:randomUUID(),status:422});
  await request(path,{method:'POST',body:{userName:'Fresh-Name'},status:422});
 });
 if(process.env.API_V3_ALLOW_NAME_CARD_USE==='1')await check('one card and one rename, historical replay and key conflict',async()=>{
  const userName=process.env.API_V3_NEW_USER_NAME;assert.ok(userName,'API_V3_NEW_USER_NAME required');
  const before=counts((await request('/me/bag')).data),key=randomUUID();
  const first=await request(path,{method:'POST',body:{userName},key,status:[200,409]});const after=counts((await request('/me/bag')).data);
  if(first.status===409){assert.deepEqual(after,before);return;}
  assert.deepEqual(Object.keys(first.data).sort(),['bag','itemCode','userName']);assert.equal(first.data.userName,userName.trim());assert.equal(after.nameCard,before.nameCard-1);
  for(const [code,count]of Object.entries(before))if(code!=='nameCard')assert.equal(after[code],count);
  assert.deepEqual((await request(path,{method:'POST',body:{userName},key})).data,first.data);
  await request(path,{method:'POST',body:{userName:'Different-Name'},key,status:409});assert.deepEqual(counts((await request('/me/bag')).data),after);
 });
}
process.exitCode=failures?1:0;
