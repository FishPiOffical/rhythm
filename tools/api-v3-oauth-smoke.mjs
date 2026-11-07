#!/usr/bin/env node
// Run only after the developer has rebuilt/restarted IDEA and migrated the OAuth tables.
// Default checks never approve consent, exchange a code, refresh or revoke credentials.
import assert from 'node:assert/strict';
if(process.env.API_V3_SERVER_RESTARTED!=='1')throw new Error('Confirm the IDEA rebuild/restart, then set API_V3_SERVER_RESTARTED=1');
const base=new URL(process.env.API_V3_BASE_URL||'http://localhost:8080');
if(!['http:','https:'].includes(base.protocol)||base.username||base.password||base.search||base.hash)throw new Error('Invalid base URL');
async function post(route,fields){return fetch(new URL(route,base),{method:'POST',redirect:'manual',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(fields)});}
function required(name){const v=process.env[name];if(!v)throw new Error(`Missing ${name}`);return v;}
const malformed=await post('/api/v3/oauth/token',{grant_type:'not_supported',client_id:'smoke_invalid_client'});
// A production anonymous shield may redirect an unverified IP before the OAuth handler.
assert.ok([400,401,403,302,429].includes(malformed.status));
console.log(`PASS unauthenticated malformed request handled (${malformed.status}); no response body logged`);
if(process.env.API_V3_OAUTH_EXCHANGE==='1'){
 const res=await post('/api/v3/oauth/token',{grant_type:'authorization_code',client_id:required('API_V3_OAUTH_CLIENT'),redirect_uri:required('API_V3_OAUTH_REDIRECT'),code:required('API_V3_OAUTH_CODE'),code_verifier:required('API_V3_OAUTH_VERIFIER')});
 assert.equal(res.status,200,'Exchange failed; response omitted to protect credentials');
 let token=await res.json();assert.equal(token.token_type,'Bearer');assert.ok(token.access_token&&token.refresh_token);assert.ok(!('data'in token));
 console.log('PASS human-approved authorization code exchanged; credentials kept in memory');
 if(process.env.API_V3_OAUTH_REFRESH==='1'){
  const next=await post('/api/v3/oauth/token',{grant_type:'refresh_token',client_id:required('API_V3_OAUTH_CLIENT'),refresh_token:token.refresh_token});assert.equal(next.status,200);const rotated=await next.json();assert.notEqual(rotated.refresh_token,token.refresh_token);token=rotated;console.log('PASS refresh rotated');
 }
 if(process.env.API_V3_OAUTH_REVOKE==='1'){
  const res=await post('/api/v3/oauth/revoke',{client_id:required('API_V3_OAUTH_CLIENT'),token:token.refresh_token});assert.equal(res.status,200);assert.deepEqual(await res.json(),{});console.log('PASS authorization revoked');
 }
}
