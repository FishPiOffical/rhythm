#!/usr/bin/env node
/** After default-client SQL scope upgrade, IDEA rebuild/restart and new login.
 * API_V3_TOKEN: disposable account with profile.read/settings.write.
 * Defaults to reads only. API_V3_ALLOW_SETTINGS_WRITES=1 changes then restores one field per group.
 * Restore attempts also run on errors. Do not concurrently edit this account while running.
 * API_V3_READ_ONLY_TOKEN optionally verifies missing write scope.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
let failures=0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch(e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, {method='GET', body, key, status=200, credential=token}={}) {
  const r=await fetch(new URL(`/api/v3${path}`,base), {method,redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3SettingsSmoke',...(credential?{Authorization:`Bearer ${credential}`} : {}),...(method==='GET'?{}:{'Content-Type':'application/json'}),...(key?{'Idempotency-Key':key}:{})},...(method==='GET'?{}:{body:JSON.stringify(body||{})})});
  assert.equal(r.status,status); assert.ok(r.headers.get('x-request-id')); const json=await r.json(); if(status===200)assert.equal(json.code,0);else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return json.data;
}
for(const group of ['profile','privacy','display','functions','location','locale']) {
  await check(`read requires Bearer ${group}`,()=>request(`/me/settings/${group}`,{credential:null,status:401}));
  await check(`write requires Bearer ${group}`,()=>request(`/me/settings/${group}`,{method:'PUT',body:{},key:randomUUID(),credential:null,status:401}));
}
if(process.env.API_V3_READ_ONLY_TOKEN) await check('settings.write scope required',()=>request('/me/settings/privacy',{method:'PUT',body:{articles:false},key:randomUUID(),credential:process.env.API_V3_READ_ONLY_TOKEN,status:403}));
if(token) {
  await check('read settings groups and skin catalog',async()=>{
    const profile=await request('/me/settings/profile');assert.deepEqual(Object.keys(profile).sort(),['userNickname','userIntro','userURL'].sort());
    const privacy=await request('/me/settings/privacy');assert.equal(Object.keys(privacy).length,13);for(const v of Object.values(privacy))assert.equal(typeof v,'boolean');
    const functions=await request('/me/settings/functions');assert.equal(Object.keys(functions).length,9);assert.equal(typeof functions.notifications,'boolean');
    const location=await request('/me/settings/location');assert.deepEqual(Object.keys(location),['publicLocation']);assert.equal(typeof location.publicLocation,'boolean');
    const locale=await request('/me/settings/locale');assert.deepEqual(Object.keys(locale).sort(),['language','timezone']);
    const options=await request('/me/settings/locale-options');assert.ok(options.languages.includes('zh_CN'));assert.ok(options.languages.includes('en_US'));for(const zone of options.timezones)assert.deepEqual(Object.keys(zone).sort(),['id','name']);
    const display=await request('/me/settings/display');assert.deepEqual(Object.keys(display).sort(),['commentViewMode','avatarViewMode','skin','mobileSkin'].sort());
    const catalog=await request('/me/settings/skins');assert.ok(Array.isArray(catalog.list));for(const skin of catalog.list){assert.deepEqual(Object.keys(skin).sort(),['id','device','name','description','previewURL','order'].sort());assert.ok(['pc','mobile'].includes(skin.device));}
  });
  if(process.env.API_V3_ALLOW_SETTINGS_WRITES==='1') for(const group of ['privacy','display','profile','functions','location','locale']) await check(`partial ${group}/replay/restore`,async()=>{
    const path=`/me/settings/${group}`, before=await request(path), key=randomUUID(), restoreKey=randomUUID();
    if(group==='locale'&&!['zh_CN','en_US'].includes(before.language)){console.log('SKIP locale fixture cannot be safely restored');return;}
    const field={privacy:'articles',display:'commentViewMode',profile:'userNickname',functions:'notifications',location:'publicLocation',locale:'language'}[group];
    const value=['privacy','functions','location'].includes(group)?!before[field]:group==='display'?1-before[field]:group==='locale'?(before[field]==='zh_CN'?'en_US':'zh_CN'):`v3-${Date.now()}`;
    let started=false;
    try {
      await request(path,{method:'PUT',body:{userId:'other'},key:randomUUID(),status:422});
      await request(path,{method:'PUT',body:{[field]:value},status:422});
      const first=await request(path,{method:'PUT',body:{[field]:value},key});started=true;assert.equal(first[field],value);
      for(const [name,old] of Object.entries(before))if(name!==field)assert.deepEqual(first[name],old);
      assert.deepEqual(await request(path,{method:'PUT',body:{[field]:value},key}),first);
      await request(path,{method:'PUT',body:{[field]:before[field]},key,status:409});
      assert.equal((await request(path))[field],value);
      await request(path,{method:'PUT',body:{[field]:before[field]},key:restoreKey});
      await request(path,{method:'PUT',body:{[field]:value},key});assert.equal((await request(path))[field],before[field]);
    } finally { if(started)await request(path,{method:'PUT',body:{[field]:before[field]},key:restoreKey}); }
  });
}
process.exitCode=failures?1:0;
