#!/usr/bin/env node
/** Offline browser fixture: actual source/generated barrage handlers and shipped jQuery plugin. No app access. */
import fs from 'node:fs';import path from 'node:path';
const output=path.resolve(process.argv[2]||'/tmp/rhythm-chat-barrage-fixture');fs.mkdirSync(output,{recursive:true});
const cases=[];
for(const name of ['channel.js','channel-2.js','channel.min.js','channel-2.min.js']){
 const source=fs.readFileSync(`src/main/resources/js/${name}`,'utf8'),match=/case\s*["']barrager["']\s*:/.exec(source);
 if(!match)throw Error(`Missing barrage case ${name}`);
 const begin=match.index+match[0].length,end=source.indexOf('break;',begin),code=source.slice(begin,end),binding=/([A-Za-z_$][\w$]*)\.barragerContent\b/.exec(code)?.[1];
 if(end<0||!binding)throw Error(`Review changed barrage handler ${name}`);cases.push({name,code,binding});
}
const script=`const cases=${JSON.stringify(cases)},results=[];window.barrageExecuted=false;
$.fn.animate=function(){return this;};
try{for(const part of cases){for(const hostile of [false,true]){for(const nickname of [false,true]){
 $('.barrage').remove();const body=hostile?'<img src=x onerror=window.barrageExecuted=true> & " 😀':'普通弹幕 😀';
 const user=hostile?'who"/><img src=x onerror=window.barrageExecuted=true>':'Fish-123';
 const data={barragerContent:body,barragerColor:'#A1B2C3',userName:user,userNickname:nickname?(hostile?'<script>window.barrageExecuted=true</script>昵称':'昵称'):'',userAvatarURL:''};
 new Function('$','Label',part.binding,part.code)($,{servePath:'http://example.invalid'},data);
 const link=document.querySelector('.barrage .p a');if(!link)throw Error('missing barrage');
 const display=(data.userNickname||user)+': '+body;if(link.textContent!==display)throw Error('text round-trip failed');
 if(link.querySelector('img,script,iframe,object')||window.barrageExecuted)throw Error('executable markup injected');
 if(link.getAttribute('href')!=='http://example.invalid/member/'+encodeURIComponent(user))throw Error('author path not encoded');
 results.push('PASS '+part.name+' '+(hostile?'hostile':'normal')+' '+(nickname?'nickname':'username'));
 }}}
 document.getElementById('results').textContent=results.join('\\n');document.title='PASS barrage DOM';}
catch(error){document.getElementById('results').textContent=results.join('\\n')+'\\nFAIL '+error.message;document.title='FAIL barrage DOM';}`;
const data=file=>fs.readFileSync(file).toString('base64'),encoded=Buffer.from(script).toString('base64');
fs.writeFileSync(path.join(output,'index.html'),'<!doctype html><meta charset="utf-8"><title>Barrage DOM fixture</title>'
 +'<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'unsafe-inline\' \'unsafe-eval\' data:; style-src \'unsafe-inline\'; img-src \'none\'; connect-src \'none\'">'
 +'<pre id="results">running</pre><script src="data:text/javascript;base64,'+data('src/main/resources/js/lib/jquery/jquery.min.js')+'"></script>'
 +'<script src="data:text/javascript;base64,'+data('src/main/resources/js/lib/barrager/jquery.barrager.min.js')+'"></script>'
 +'<script>const run=document.createElement("script");run.textContent=new TextDecoder().decode(Uint8Array.from(atob("'+encoded+'"),c=>c.charCodeAt(0)));document.body.append(run);</script>');
console.log(path.join(output,'index.html'));
