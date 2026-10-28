#!/usr/bin/env node
/** Syntactic register-call reachability and API.md contract parity; never claims IoC/runtime success. */
import fs from 'node:fs'
import path from 'node:path'
const root=process.cwd()
function comments(source){return source.replace(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g,v=>v.startsWith('//')||v.startsWith('/*')?v.replace(/[^\n]/g,' '):v)}
function closeBrace(source,start){let depth=1;for(let i=start;i<source.length;i++){if(source[i]==='"'||source[i]==="'"){const q=source[i];for(i++;i<source.length;i++){if(source[i]==='\\')i++;else if(source[i]===q)break}}else if(source[i]==='{')depth++;else if(source[i]==='}'&&!--depth)return i}throw Error('Unclosed Java body')}
const visited=new Set(),routes=[],problems=[]
function read(className,method='register'){
 const key=className+'.'+method;if(visited.has(key))return;visited.add(key)
 const file='src/main/java/org/b3log/symphony/processor/'+className+'.java',source=comments(fs.readFileSync(path.join(root,file),'utf8'))
 const declaration=new RegExp('\\b(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?void\\s+'+method+'\\s*\\([^)]*\\)\\s*\\{','g'),match=declaration.exec(source)
 if(!match){problems.push('Missing '+key);return}if(declaration.exec(source))problems.push('Ambiguous overload '+key)
 const start=match.index+match[0].length,end=closeBrace(source,start),body=source.slice(start,end)
 if(/\b(?:if|switch|for|while|try)\s*[({]/.test(body.replace(/"(?:\\.|[^"\\])*"/g,'""')))problems.push('Conditional/control-flow registration needs manual review: '+key)
 for(const m of body.matchAll(/\b(ApiV3\w*Processor)\.(register\w*)\s*\(/g))read(m[1],m[2])
 const localNames=new Set([...source.matchAll(/\bvoid\s+(register\w*)\s*\(/g)].map(m=>m[1]))
 for(const name of localNames){if(name===method)continue;const re=new RegExp('(?<![\\w.])'+name+'\\s*\\(','g');if(re.test(body))read(className,name)}
 for(const m of body.matchAll(/Dispatcher\.(get|post|put|delete|patch|webSocket)\(\s*"(\/api\/v3[^"\n]*)"/g))routes.push({method:m[1]==='webSocket'?'WEBSOCKET':m[1].toUpperCase(),path:m[2],source:file,line:source.slice(0,start+m.index).split('\n').length,registration:key})
}
read('Router','requestMapping')
if(!visited.has('ApiV3Processor.register'))problems.push('Router.requestMapping does not reach ApiV3Processor.register')
const count=new Map();for(const r of routes){const k=r.method+' '+r.path;count.set(k,(count.get(k)||0)+1)}
const api=fs.readFileSync(path.join(root,'API.md'),'utf8'),documented=new Set([...api.matchAll(/`(GET|POST|PUT|DELETE|PATCH|WEBSOCKET|WS) (\/api\/v3[^`?\s]*)/g)].map(m=>(m[1]==='WS'?'WEBSOCKET':m[1])+' '+m[2]))
const missingDocs=[...count.keys()].filter(k=>!documented.has(k)).sort(),missingRoutes=[...documented].filter(k=>!count.has(k)).sort(),duplicates=[...count].filter(([,n])=>n>1)
const methods={};for(const r of routes)methods[r.method]=(methods[r.method]||0)+1
const report={scope:'Syntactic registration reachability from Router -> ApiV3Processor.register, including local register helpers; not Java compilation, IoC, database or HTTP verification',registrationMethods:[...visited].sort(),count:routes.length,methods,documentedCount:documented.size,missingDocs,missingRoutes,duplicates,problems,routes:routes.sort((a,b)=>a.path.localeCompare(b.path)||a.method.localeCompare(b.method))}
const arg=process.argv.indexOf('--json');if(arg>=0){if(!process.argv[arg+1])throw Error('--json requires path');fs.writeFileSync(process.argv[arg+1],JSON.stringify(report,null,2)+'\n')}
console.log(JSON.stringify({count:report.count,documentedCount:report.documentedCount,methods,registrationMethods:visited.size,missingDocs,missingRoutes,duplicates,problems}));if(problems.length||duplicates.length||missingDocs.length||missingRoutes.length)process.exitCode=1
