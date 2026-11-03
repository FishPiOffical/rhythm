#!/usr/bin/env node
// Syntactic middleware inventory, anchored to the current reachable contract routes; not semantic/runtime proof.
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
export function callArguments(source,start){
 let depth=0,quote='',escape=false,part=start+1,args=[]
 for(let i=start;i<source.length;i++){
  const c=source[i]
  if(quote){if(escape)escape=false;else if(c==='\\')escape=true;else if(c===quote)quote='';continue}
  if(c==='"'||c==="'"){quote=c;continue}
  if('([{'.includes(c))depth++
  else if(')]}'.includes(c)){if(--depth===0){args.push(source.slice(part,i).trim());return args}}
  else if(c===','&&depth===1){args.push(source.slice(part,i).trim());part=i+1}
 }
 throw Error('Unclosed registration call')
}
export function access(route,args,scopes){
 const middleware=args.slice(2),required=[],optional=[]
 for(const item of middleware){const match=item.match(/^auth\s*::\s*(\w+)$/);if(!match)continue
  if(match[1]==='handle')required.push('authenticated')
  else if(Object.hasOwn(scopes,match[1]))required.push(scopes[match[1]])
  else if(['optionalContent','optionalProfile'].includes(match[1]))optional.push(match[1]==='optionalContent'?'content.read':'profile.read')
  else throw Error('Unknown auth method '+match[1])
 }
 const anonymous=middleware.some(x=>/^anonymous\s*::\s*handle$/.test(x))
 if(required.length)return{kind:'required-bearer',requiredScopes:required,middleware}
 if(optional.length){if(!anonymous)throw Error('Optional Bearer route lacks explicit anonymous gate');return{kind:'optional-bearer',optionalScopes:optional,middleware}}
 if(anonymous)return{kind:'anonymous-gate',middleware}
 const key=route.method+' '+route.path
 const special={'POST /api/v3/oauth/consent':'cookieOnly','POST /api/v3/oauth/token':'protocolToken','POST /api/v3/oauth/revoke':'protocolRevoke'}
 if(special[key]&&middleware.some(x=>x.endsWith('::'+special[key])))return{kind:'reviewed-oauth-protocol',middleware,manualReviewRequired:true}
 if(key==='WEBSOCKET /api/v3/realtime')return{kind:'reviewed-websocket-protocol',middleware,manualReviewRequired:true}
 throw Error('Unclassified access chain '+key)
}
export function inventory(contract){
 const source=fs.readFileSync('src/main/java/org/b3log/symphony/processor/middleware/ApiV3AuthMidware.java','utf8')
 const scopes=Object.fromEntries([...source.matchAll(/public void (\w+)\(final RequestContext context\)\s*\{ required\(context, "([^"]+)"\); \}/g)].map(x=>[x[1],x[2]]))
 const rows=[],problems=[]
 for(const route of contract.routes){try{
  const text=fs.readFileSync(route.source,'utf8'),needle='"'+route.path+'"',offset=text.split('\n').slice(0,route.line-1).join('\n').length
  const method=route.method==='WEBSOCKET'?'webSocket':route.method.toLowerCase(),start=text.indexOf('Dispatcher.'+method+'(',offset)
  if(start<0||text.slice(start,text.indexOf('\n',start)).indexOf(needle)<0)throw Error('Registration location does not match reachable contract')
  const args=callArguments(text,text.indexOf('(',start));if(args[0]!==needle)throw Error('Route literal mismatch')
  rows.push({...route,...access(route,args,scopes)})
 }catch(error){problems.push({method:route.method,path:route.path,problem:error.message})}}
 const kinds={};for(const row of rows)kinds[row.kind]=(kinds[row.kind]||0)+1
 return{scope:'Syntactic explicit auth middleware inventory, not arbitrary Java control-flow or HTTP/WS verification; reviewed protocol exceptions remain separate',count:rows.length,kinds,problems,routes:rows}
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===fs.realpathSync(process.argv[1])){
 const value=key=>{const i=process.argv.indexOf(key);if(i<0||!process.argv[i+1])throw Error(key+' requires a path');return process.argv[i+1]}
 const report=inventory(JSON.parse(fs.readFileSync(value('--routes'),'utf8')));fs.writeFileSync(value('--json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({count:report.count,kinds:report.kinds,problems:report.problems}));if(report.problems.length)process.exitCode=1
}
