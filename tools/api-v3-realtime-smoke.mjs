#!/usr/bin/env node
/** Run after confirmed IDEA rebuild/restart and socket-ticket schema installation.
 * API_V3_TOKEN needs notifications.read; API_V3_NO_NOTIFICATION_SCOPE_TOKEN optionally tests 403.
 * Default: denial checks. API_V3_ALLOW_REALTIME_CONNECT=1 explicitly issues grants and opens sockets.
 * API_V3_REALTIME_ARTICLE_ID optionally checks article tickets using API_V3_ARTICLE_TOKEN (or API_V3_TOKEN).
 * API_V3_REALTIME_CHAT=1 checks chat tickets using API_V3_CHAT_TOKEN (or API_V3_TOKEN).
 * API_V3_REALTIME_MESSAGES=1 checks private tickets using API_V3_MESSAGES_TOKEN (or API_V3_TOKEN).
 * No notification/content mutations; event changes must be triggered manually. Never print credentials or raw responses.
 */
import assert from 'node:assert/strict';
const base=process.env.API_V3_BASE_URL||'http://localhost:8080',token=process.env.API_V3_TOKEN;let failures=0;
const messagesToken=process.env.API_V3_MESSAGES_TOKEN||token;
const chatToken=process.env.API_V3_CHAT_TOKEN||token;
const articleId=process.env.API_V3_REALTIME_ARTICLE_ID,articleToken=process.env.API_V3_ARTICLE_TOKEN||token;
if(articleId)assert.match(articleId,/^[0-9]{1,32}$/);
async function check(name,run){try{await run();console.log(`PASS ${name}`);}catch(e){failures++;console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);}}
async function ticket({credential=token,body={},status=200,article,chat=false,messages=false}={}){
 const route=messages?'api/v3/me/messages/realtime-ticket':chat?'api/v3/chat/realtime-ticket':article?`api/v3/articles/${article}/realtime-ticket`:'api/v3/realtime/tickets';
 const r=await fetch(new URL(route,base.endsWith('/')?base:base+'/'),{method:'POST',redirect:'manual',signal:AbortSignal.timeout(20000),headers:{'User-Agent':'RhythmV3RealtimeSmoke','Content-Type':'application/json',...(credential?{Authorization:`Bearer ${credential}`}:{})},body:JSON.stringify(body)});
 assert.equal(r.status,status);assert.ok(r.headers.get('x-request-id'));const value=await r.json();if(status===200){assert.equal(value.code,0);assert.match(r.headers.get('cache-control'),/no-store/);return value.data;}assert.notEqual(value.code,0);assert.equal(value.data,null);
}
async function connect(grant,{expectClose=false,query='',authenticate=true}={}){
 const url=new URL(grant.path,base);assert.equal(url.origin,new URL(base).origin);assert.ok(!url.search&&!url.hash);url.protocol=url.protocol==='https:'?'wss:':'ws:';url.search=query;
 return new Promise((resolve,reject)=>{
  const ws=new WebSocket(url);let ready=false,done=false;
  const finish=error=>{if(done)return;done=true;clearTimeout(timer);ws.close();error?reject(error):resolve();};
  const timer=setTimeout(()=>finish(new Error('socket timeout')),15000);
  ws.addEventListener('message',({data})=>{try{
   const message=JSON.parse(data);assert.deepEqual(Object.keys(message).sort(),['data','type']);
   if(message.type==='authentication.required'){if(authenticate)ws.send(JSON.stringify({type:'authenticate',ticket:grant.ticket}));}
   else if(message.type==='ready'){assert.equal(expectClose,false);ready=true;assert.equal(message.data.channel,grant.channel);if(grant.channel==='article')assert.equal(message.data.articleId,grant.articleId);ws.send('{"type":"ping"}');}
   else if(message.type==='pong'){assert.ok(ready);finish();}
   else if(message.type==='messages.changed'){assert.equal(grant.channel,'messages');assert.deepEqual(message.data,{});}
   else if(message.type==='notifications.changed'){assert.equal(grant.channel,'notifications');assert.deepEqual(message.data,{});}
   else if(message.type==='chat.sync_required'){assert.equal(grant.channel,'chat');assert.deepEqual(message.data,{});}
   else if(message.type.startsWith('chat.message.')){assert.equal(grant.channel,'chat');assert.ok(['created','updated','deleted','reactions'].some(kind=>message.type==='chat.message.'+kind));assert.deepEqual(Object.keys(message.data).sort(),['messageId','revision']);assert.match(message.data.messageId,/^[0-9]{1,32}$/);assert.match(message.data.revision,/^[1-9][0-9]*$/);}
   else if(message.type==='article.changed'){assert.equal(grant.channel,'article');assert.deepEqual(message.data,{articleId:grant.articleId});}
   else assert.fail('unknown message type');
  }catch(e){finish(e);}});
  ws.addEventListener('close',()=>{if(!done)finish(expectClose&&!ready?null:new Error('unexpected close'));});
  ws.addEventListener('error',()=>{if(!done&&!expectClose)finish(new Error('socket connection failed'));});
 });
}
await check('ticket requires Bearer',()=>ticket({credential:null,status:401}));
if(process.env.API_V3_NO_NOTIFICATION_SCOPE_TOKEN)await check('ticket requires notifications.read',()=>ticket({credential:process.env.API_V3_NO_NOTIFICATION_SCOPE_TOKEN,status:403}));
if(token){
 await check('ticket rejects user/token/scope/channel overrides',async()=>{for(const body of [{userId:'321'},{tokenId:'654'},{channel:'admin'},{scope:'admin'},{apiKey:'legacy'}])await ticket({body,status:422});});
 if(process.env.API_V3_ALLOW_REALTIME_CONNECT==='1'){
  await check('challenge, one-use authentication and ping/pong',async()=>{const grant=await ticket();assert.deepEqual(Object.keys(grant).sort(),['channel','expiresIn','path','ticket']);assert.ok(grant.expiresIn>0&&grant.expiresIn<=60);await connect(grant);await connect(grant,{expectClose:true});});
  await check('URL credentials and missing authentication close',async()=>{const grant=await ticket();await connect(grant,{expectClose:true,query:'?apiKey=legacy'});await connect(grant,{expectClose:true,authenticate:false});});
 }
}
await check('article ticket requires Bearer',()=>ticket({article:articleId||'1',credential:null,status:401}));
if(process.env.API_V3_NO_CONTENT_SCOPE_TOKEN)await check('article ticket requires content.read',()=>ticket({article:articleId||'1',credential:process.env.API_V3_NO_CONTENT_SCOPE_TOKEN,status:403}));
if(articleId&&articleToken){
 await check('article ticket rejects subscription/identity overrides',async()=>{for(const body of [{articleId:'987'},{userId:'321'},{channel:'notifications'},{apiKey:'legacy'}])await ticket({article:articleId,credential:articleToken,body,status:422});});
 if(process.env.API_V3_ALLOW_REALTIME_CONNECT==='1')await check('article binding, challenge and one-use ping/pong',async()=>{
  const grant=await ticket({article:articleId,credential:articleToken});assert.deepEqual(Object.keys(grant).sort(),['articleId','channel','expiresIn','path','ticket']);assert.equal(grant.channel,'article');assert.equal(grant.articleId,articleId);assert.ok(grant.expiresIn>0&&grant.expiresIn<=60);await connect(grant);await connect(grant,{expectClose:true});
 });
}
await check('chat ticket requires Bearer',()=>ticket({chat:true,credential:null,status:401}));
if(process.env.API_V3_NO_CONTENT_SCOPE_TOKEN)await check('chat ticket requires content.read',()=>ticket({chat:true,credential:process.env.API_V3_NO_CONTENT_SCOPE_TOKEN,status:403}));
if(process.env.API_V3_REALTIME_CHAT==='1'&&chatToken){
 await check('chat ticket rejects subscription overrides',async()=>{for(const body of [{channel:'admin'},{userId:'321'},{room:'private'},{apiKey:'legacy'}])await ticket({chat:true,credential:chatToken,body,status:422});});
 if(process.env.API_V3_ALLOW_REALTIME_CONNECT==='1')await check('chat binding, challenge, one-use authentication and ping',async()=>{
  const grant=await ticket({chat:true,credential:chatToken});assert.deepEqual(Object.keys(grant).sort(),['channel','expiresIn','path','ticket']);assert.equal(grant.channel,'chat');assert.ok(grant.expiresIn>0&&grant.expiresIn<=60);await connect(grant);await connect(grant,{expectClose:true});
 });
}
await check('private ticket requires Bearer',()=>ticket({messages:true,credential:null,status:401}));
if(process.env.API_V3_NO_MESSAGES_SCOPE_TOKEN)await check('private ticket requires messages.read',()=>ticket({messages:true,credential:process.env.API_V3_NO_MESSAGES_SCOPE_TOKEN,status:403}));
if(process.env.API_V3_REALTIME_MESSAGES==='1'&&messagesToken){
 await check('private ticket rejects owner/peer/scope/channel overrides',async()=>{for(const body of [{userId:'321'},{peerId:'321'},{scope:'messages.write'},{channel:'notifications'},{apiKey:'legacy'}])await ticket({messages:true,credential:messagesToken,body,status:422});});
 if(process.env.API_V3_ALLOW_REALTIME_CONNECT==='1')await check('private binding, challenge, one-use authentication and ping',async()=>{
  const grant=await ticket({messages:true,credential:messagesToken});assert.deepEqual(Object.keys(grant).sort(),['channel','expiresIn','path','ticket']);assert.equal(grant.channel,'messages');assert.ok(grant.expiresIn>0&&grant.expiresIn<=60);await connect(grant);await connect(grant,{expectClose:true});
 });
}
process.exitCode=failures?1:0;
