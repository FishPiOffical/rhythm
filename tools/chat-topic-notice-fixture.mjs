#!/usr/bin/env node
/** Build an isolated browser DOM fixture from actual source AND generated topic-notification branches.
 * Uses the bundled jQuery. Does not contact the application, send chat messages or start a server.
 * Serve the generated directory on loopback and open index.html in a browser.
 */
import fs from 'node:fs';import path from 'node:path';
const output=path.resolve(process.argv[2]||'/tmp/rhythm-chat-topic-fixture');fs.mkdirSync(output,{recursive:true});
const cases=[];
for(const name of ['channel.js','channel-2.js','channel.min.js','channel-2.min.js']){
 const source=fs.readFileSync(`src/main/resources/js/${name}`,'utf8'),match=/case\s*["']discussChanged["']\s*:/.exec(source);
 if(!match)throw new Error(`Missing topic case in ${name}`);
 const begin=match.index+match[0].length,end=source.indexOf('break;',begin);
 if(end<0)throw new Error(`Missing topic branch end in ${name}`);
 const code=source.slice(begin,end),binding=/([A-Za-z_$][\w$]*)\.whoChanged\b/.exec(code)?.[1];
 if(!binding)throw new Error(`Review changed event binding in ${name}`);
 cases.push({name,code,binding,append:name.startsWith('channel-2')});
}
const payload={whoChanged:'user"/><img src=x onerror=window.topicExecuted=true> &中文',newDiscuss:'<img src=x onerror=window.topicExecuted=true> <script>window.topicExecuted=true</script> & " \' 😀'};
const script=`const cases=${JSON.stringify(cases)},payload=${JSON.stringify(payload)},results=[];window.topicExecuted=false;$.fx.off=true;
function test(part,data,fallback){
 delete window.fishpiChatTopicRevision;
 document.getElementById('stage').innerHTML='<div id="chats"><div id="existing">old</div></div><div id="discuss-title"></div>';
 const Label={servePath:'http://example.invalid'},ChatRoom={scrollToBottom(){}};
 if(!fallback)ChatRoom.appendAndMaybeScroll=html=>$('#chats').append(html);
 new Function('$','ChatRoom','Label',part.binding,part.code)($,ChatRoom,Label,data);
 const notice=document.querySelector('.newDiscussNotice');if(!notice)throw Error('missing notice');
 const links=notice.querySelectorAll('a');if(links.length!==2)throw Error('unexpected anchor injection');
 if(notice.querySelector('img,script,iframe,object'))throw Error('executable element injected');
 if(links[0].textContent!==data.whoChanged||links[1].textContent!==data.newDiscuss)throw Error('text did not round-trip');
 if(links[0].getAttribute('href')!==Label.servePath+'/member/'+encodeURIComponent(data.whoChanged))throw Error('author path not encoded');
 if(document.getElementById('discuss-title').textContent!==data.newDiscuss)throw Error('title text mismatch');
 if(window.topicExecuted)throw Error('payload executed');
 if((document.getElementById('chats').firstElementChild===notice)===part.append)throw Error('notice insertion order changed');
 results.push('PASS '+part.name+(fallback?' fallback':'')+' '+(data===payload?'hostile text':'normal text'));
}
try{for(const part of cases){for(const data of [payload,{whoChanged:'Fish-123',newDiscuss:'普通话题 😀'}])test(part,data,false);if(part.append)test(part,payload,true);
 test(part,{whoChanged:'Fish',newDiscuss:'latest',topicRevision:'9007199254740993'},false);
 const run=data=>new Function('$','ChatRoom','Label',part.binding,part.code)($,{scrollToBottom(){},appendAndMaybeScroll:html=>$('#chats').append(html)},{servePath:'http://example.invalid'},data);
 for(const revision of ['9007199254740993','9007199254740992','123','0','invalid','92233720368547758080'])run({whoChanged:'Fish',newDiscuss:'stale',topicRevision:revision});
 if(document.querySelectorAll('.newDiscussNotice').length!==1||document.getElementById('discuss-title').textContent!=='latest')throw Error('duplicate/stale revision applied');
 run({whoChanged:'Fish',newDiscuss:'next',topicRevision:'9007199254740994'});
 if(document.querySelectorAll('.newDiscussNotice').length!==2||document.getElementById('discuss-title').textContent!=='next')throw Error('new revision lost precision');
 results.push('PASS '+part.name+' revision dedup/order beyond Number precision');
 }
 document.getElementById('results').textContent=results.join('\\n');document.title='PASS topic notification DOM';}
catch(error){document.getElementById('results').textContent=results.join('\\n')+'\\nFAIL '+error.message;document.title='FAIL topic notification DOM';}
`;
const jquery=fs.readFileSync('src/main/resources/js/lib/jquery/jquery.min.js').toString('base64'),encoded=Buffer.from(script).toString('base64');
const html='<!doctype html><meta charset="utf-8"><title>Topic notification DOM test</title>'
 +'<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'unsafe-inline\' \'unsafe-eval\' data:; style-src \'unsafe-inline\'; img-src \'none\'; connect-src \'none\'">'
 +'<div id="stage"></div><pre id="results">running</pre><script src="data:text/javascript;base64,'+jquery+'"></script>'
 +'<script>const run=document.createElement("script");run.textContent=new TextDecoder().decode(Uint8Array.from(atob("'+encoded+'"),c=>c.charCodeAt(0)));document.body.append(run);</script>';
fs.writeFileSync(path.join(output,'index.html'),html);console.log(path.join(output,'index.html'));
