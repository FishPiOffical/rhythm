#!/usr/bin/env node
/** Offline DOM fixture: real registration source/generated JS plus PC/mobile hidden fields.
 * Intercepts AJAX; never connects to the application or changes a real account/password.
 */
import fs from 'node:fs';import path from 'node:path';import {createHash} from 'node:crypto';
const out=path.resolve(process.argv[2]||'/tmp/rhythm-account-registration-fixture');fs.mkdirSync(out,{recursive:true});
const cases=[];
for(const skin of ['pc','mobile']){
 const template=fs.readFileSync(`src/main/resources/skins/classic/${skin}/verify/register2.ftl`,'utf8');
 const hidden=template.match(/<input id="registerCode2"[^>]*>/)?.[0];if(!hidden||!hidden.includes("${(code!'')?html}"))throw Error('Missing encoded proof field: '+skin);
 for(const name of ['verify.js','verify.min.js'])cases.push({name:skin+'/'+name,source:fs.readFileSync('src/main/resources/js/'+name,'utf8'),hidden:hidden.replace("${(code!'')?html}",'123456')});
}
const expected=createHash('md5').update('fixture-password-only').digest('hex');
const script=`const cases=${JSON.stringify(cases)},expected=${JSON.stringify(expected)},results=[];let allow=true,calls=[],replyMode="json";
window.Label={servePath:window.location.origin+'/rhythm-fixture',invalidPasswordLabel:'invalid',confirmPwdErrorLabel:'confirm'};
window.Validate={goValidate(){return allow;}};window.Util={getParameterByName(){return 'inviter&userId=2';}};
$.ajax=request=>{calls.push(request);if(replyMode==='error'){request.error({},'parsererror');return;}const reply=replyMode==='challenge'?'<html><script>window.__accountFixtureInjected=true;</script></html>':{code:-1,msg:replyMode==='unsafe-msg'?'<img src=x onerror="window.__accountFixtureInjected=true">':'fixture rejected: no real registration'};if(replyMode==='legacy-msg')delete reply.code;request.success(reply,'success');};
try{for(const part of cases){
 replyMode='json';document.getElementById('stage').innerHTML='<div id="regForm"><input id="registerUserName" value="fixture-user"><input id="registerUserPhone" value="13800000001"><input id="registerInviteCode" value="AbCdEfGh12345678"></div><div id="gotoLoginBtn"></div><div id="captcha"></div><button id="registerBtn">Send</button><div id="registerTip"></div><input id="rpwdUserPassword" value="fixture-password-only"><input id="rpwdConfirmPassword" value="fixture-password-only"><input id="rpwdUserId" value="1"><input id="code" value="123456"><div id="rpwdTip"></div><input id="registerUserPassword2" value="fixture-password-only"><input id="registerConfirmPassword2" value="fixture-password-only"><input name="userAppRole" type="radio" value="1" checked><input id="referral2" value="inviter"><input id="userId2" value="1"><input id="mbtiReg" value="INFJ-T"><div id="registerTip2"></div>'+part.hidden;
 const verify=new Function(part.source+';return Verify;')();calls=[];allow=false;verify.register2();if(calls.length)throw Error('validation did not block');
 allow=true;verify.register2();if(calls.length!==1)throw Error('expected exactly one AJAX call');
 const request=calls[0],body=JSON.parse(request.data);
 if(request.type!=='POST'||request.url!==Label.servePath+'/register2?r=inviter%26userId%3D2')throw Error('referral URL not encoded');
 if(body.userId!=='1'||body.code!=='123456'||body.userPassword!==expected||body.userAppRole!=='1'||body.mbti!=='INFJ-T')throw Error('request lost registration proof or normal fields');
 if(!document.getElementById('registerTip2').textContent.includes('fixture rejected'))throw Error('failure feedback missing');
 document.getElementById('registerCode2').value='';verify.register2();if(JSON.parse(calls[1].data).code!=='')throw Error('missing proof was silently fabricated');
 results.push('PASS '+part.name+' proof, MD5, role, encoded referral, validation and failure feedback');
 const pageURL=window.location.href,actions=[['register','#registerTip',()=>verify.register('fixture-captcha')],['register2','#registerTip2',()=>verify.register2()],['resetPwd','#rpwdTip',()=>verify.resetPwd()]];
 for(const [action,tip,invoke]of actions){
  for(const mode of ['challenge','error']){
   calls=[];replyMode=mode;invoke();if(calls.length!==1)throw Error(action+' automatically retried a blocked request');
   const anchor=document.querySelector(tip+' a');if(!anchor||anchor.href!==Label.servePath+'/test'||anchor.target!=='_blank'||!anchor.rel.includes('noopener')||!anchor.rel.includes('noreferrer'))throw Error(action+' lacks a safe manual challenge link');
   if(window.location.href!==pageURL||document.getElementById('regForm').style.display==='none'||document.getElementById('registerUserPassword2').value!=='fixture-password-only'||document.getElementById('rpwdUserPassword').value!=='fixture-password-only')throw Error(action+' lost the account form');
   if(window.__accountFixtureInjected||document.querySelector(tip+' script'))throw Error(action+' injected returned HTML');
   if(action==='register'&&calls[0].url!==Label.servePath+'/register?r=inviter%26userId%3D2')throw Error('first-stage referral URL is not encoded');
   results.push('PASS '+part.name+' '+action+' '+mode+' keeps form and offers manual same-origin challenge without retry');
  }
  calls=[];replyMode='unsafe-msg';invoke();if(document.querySelector(tip+' img')||window.__accountFixtureInjected||!document.querySelector(tip).textContent.includes('<img'))throw Error(action+' renders API message as HTML');
  results.push('PASS '+part.name+' '+action+' displays API error text safely');
  calls=[];replyMode='legacy-msg';invoke();if(calls.length!==1||document.querySelector(tip+' a')||!document.querySelector(tip).textContent.includes('fixture rejected'))throw Error(action+' misclassifies a legacy msg-only rejection');
  results.push('PASS '+part.name+' '+action+' preserves legacy msg-only validation errors');
 }
 const originalBase=Label.servePath;Label.servePath='https://example.invalid';verify.accountResponse('<html>challenge</html>','#registerTip');if(document.querySelector('#registerTip a'))throw Error('foreign-origin challenge link was allowed');Label.servePath=originalBase;
 results.push('PASS '+part.name+' rejects foreign-origin challenge links');

 }
 document.title='PASS account registration fixture';document.getElementById('results').textContent=results.join('\\n')+'\\nNo application requests or real credential changes.';
}catch(error){document.title='FAIL account registration fixture';document.getElementById('results').textContent=results.join('\\n')+'\\nFAIL '+error.message;}
`;
const encoded=value=>Buffer.from(value).toString('base64');
const html='<!doctype html><meta charset="utf-8"><title>Account registration fixture</title><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'unsafe-inline\' \'unsafe-eval\' data:; connect-src \'none\'">'
 +'<h1>Offline registration checks</h1><pre id="results">running</pre><div id="stage"></div>'
 +['src/main/resources/js/lib/jquery/jquery.min.js','src/main/resources/js/lib/md5.js'].map(file=>'<script src="data:text/javascript;base64,'+encoded(fs.readFileSync(file))+'"></script>').join('')
 +'<script>const run=document.createElement("script");run.textContent=new TextDecoder().decode(Uint8Array.from(atob("'+encoded(script)+'"),c=>c.charCodeAt(0)));document.body.append(run);</script>';
fs.writeFileSync(path.join(out,'index.html'),html);console.log(path.join(out,'index.html'));
