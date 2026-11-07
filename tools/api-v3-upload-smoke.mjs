#!/usr/bin/env node
/** Run only after confirmed IDEA rebuild/restart. Never print credentials.
 * API_V3_TOKEN needs uploads.write; API_V3_NO_UPLOAD_SCOPE_TOKEN optionally checks 403.
 * API_V3_ALLOW_UPLOAD_TICKET=1 mints a short-lived grant; no file is uploaded by default.
 * API_V3_ALLOW_UPLOAD=1 + API_V3_UPLOAD_FILE explicitly uploads that local file (permanent external write).
 */
import assert from 'node:assert/strict';import fs from 'node:fs';import path from 'node:path';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;let failures=0;
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function request({credential=token,body={},status=200}={}){
 const r=await fetch(new URL('/api/v3/uploads/tickets',base),{method:'POST',redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3UploadSmoke','Content-Type':'application/json',...(credential?{Authorization:`Bearer ${credential}`}:{})},body:JSON.stringify(body)});
 assert.equal(r.status,status);assert.ok(r.headers.get('x-request-id'));const json=await r.json();
 if(status===200){assert.equal(json.code,0);assert.match(r.headers.get('cache-control'),/no-store/);}else{assert.notEqual(json.code,0);assert.equal(json.data,null);}return json.data;
}
await check('ticket requires Bearer',()=>request({credential:null,status:401}));
if(process.env.API_V3_NO_UPLOAD_SCOPE_TOKEN)await check('ticket requires uploads.write',()=>request({credential:process.env.API_V3_NO_UPLOAD_SCOPE_TOKEN,status:403}));
if(token){
 await check('ticket cannot choose identity, target, scope or lifetime',async()=>{
  for(const body of [{userId:'123'},{uploadURL:'https://evil.invalid'},{scope:'admin'},{expiresIn:86400},{apiKey:'old'}])await request({body,status:422});
 });
 if(process.env.API_V3_ALLOW_UPLOAD_TICKET==='1')await check('fresh bounded upload grant and optional real upload',async()=>{
  const grant=await request();assert.deepEqual(Object.keys(grant).sort(),['expiresIn','fieldName','ticket','tokenType','uploadURL']);assert.equal(grant.expiresIn,180);assert.equal(grant.tokenType,'Bearer');assert.equal(grant.fieldName,'file');assert.ok(grant.uploadURL.endsWith('/api/v1/files'));
  const claims=JSON.parse(Buffer.from(grant.ticket.split('.')[1],'base64url').toString());assert.equal(claims.scope,'file:upload');assert.equal(claims.exp-claims.iat,180);assert.equal(claims.aud,'rhypic');
  assert.notEqual((await request()).ticket,grant.ticket);
  if(process.env.API_V3_ALLOW_UPLOAD==='1'){
   const file=process.env.API_V3_UPLOAD_FILE;assert.ok(file,'API_V3_UPLOAD_FILE required');const bytes=fs.readFileSync(file);assert.ok(bytes.length>0&&bytes.length<=1024*1024,'Use a small disposable fixture');
   const form=new FormData();form.append('file',new Blob([bytes]),path.basename(file));
   // Only the upload grant goes to the image host, never the Rhythm access token.
   const upload=await fetch(grant.uploadURL,{method:'POST',headers:{Authorization:`Bearer ${grant.ticket}`},body:form,redirect:'manual',signal:AbortSignal.timeout(60000)});
   assert.equal(upload.status,200);const result=await upload.json();assert.equal(result.code,0);assert.ok(Object.keys(result.data.succMap).length>0);assert.deepEqual(result.data.errFiles,[]);
  }
 });
}
process.exitCode=failures?1:0;
