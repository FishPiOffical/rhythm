/** 校验聊天渲染器源码与生成产物，浏览器夹具独立于应用。 */
import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import http from 'node:http';
const files = ['chat-room.js','chat-room-2.js','chat-room.min.js','chat-room-2.min.js'];
for (const file of files) {
  let replacement;
  const context = {window:{localStorage:{},location:{href:'http://localhost/fixture'}},Label:{servePath:''},
    document:{querySelector:()=>({replaceChildren(node){replacement=node;}}),createElement:tag=>({tag,setAttribute(){},addEventListener(){}})},
    $:()=>({prop(){}}),Map,console,URL};
  vm.createContext(context);
  vm.runInContext(fs.readFileSync('src/main/resources/js/' + file, 'utf8'), context);
  const chat=context.ChatRoom;
  const payload='Song " & <img src=x onerror="bad()">';
  const html=chat.renderMusicCard({title:payload,from:payload,source:'123',coverURL:'javascript:bad()'});
  assert.ok(html.includes('&lt;img'));
  assert.ok(html.includes('&quot;'));
  assert.ok(!html.includes('src="javascript:'));
  assert.ok(!html.includes('<img src=x'));
  assert.ok(html.includes('/images/music/cat.gif'));
  const weather=chat.renderWeatherCard('123',{date:'2026-01-01',weatherCode:'CLEAR_DAY',min:'-1',max:'1',t:payload,st:payload});
  assert.ok(weather.includes('&lt;img'));
  assert.ok(!weather.includes('data-t="Song "'));
  chat.playSound.renderSource('123');
  assert.equal(replacement.tag, 'iframe');
  assert.ok(replacement.src.includes('id=123&'));
  chat.playSound.renderSource('https://audio.example/a.mp3?s=a%2Bb&n=1');
  assert.equal(replacement.tag, 'audio');
  assert.equal(replacement.src,'https://audio.example/a.mp3?s=a%2Bb&n=1');
  const previous = replacement;
  chat.playSound.renderSource('123" onload="bad()');
  assert.equal(replacement, previous);
  chat.playSound.renderSource('javascript:bad()');
  assert.equal(replacement, previous);
  console.log('PASS card render '+file+' text/attribute encoding, image scheme, numeric player and opaque HTTPS audio source');
}
if (process.argv.includes('--serve')) {
  const page = script => `<!doctype html><meta charset="UTF-8"><title>卡片渲染隔离验证</title>
<style>body{font:16px sans-serif;max-width:720px;margin:24px auto;padding:12px}section{padding:16px;border:1px solid #ccc;margin:12px 0}.music-player-img{width:40px}.music-player-controller span{cursor:pointer}.music-detail audio{width:100%}</style>
<h1>${script.includes('-2')?'第二套聊天室':'第一套聊天室'}卡片夹具</h1><p>仅验证实际脚本的展示与播放器构建，不连接业务服务。</p>
<a href="/first">第一套</a> <a href="/second">第二套</a><section id="music"></section><section id="weather"></section>
<div class="music-list-box"></div><div class="music-detail"></div><p id="result"></p>
<script>var Label={servePath:''};var $=()=>({prop(){}});var Util={notice(){}};</script><script src="/${script}"></script>
<script>
const title='Song " & <img src=x onerror="document.body.dataset.executed=1"> 😀';
document.querySelector('#music').innerHTML=ChatRoom.renderMusicCard({title,from:'Artist " & 😀',source:'https://audio.example/song.mp3?s=a%2Bb&x=1',coverURL:''});
document.querySelector('#weather').innerHTML=ChatRoom.renderWeatherCard('123',{date:'2026-12-30,2026-12-31,2027-01-01,2027-01-02,2027-01-03',weatherCode:'CLEAR_DAY,CLEAR_DAY,CLOUDY,CLOUDY,WIND',max:'10,11,12,13,14',min:'0,1,2,3,4',t:'北京 " & 😀',st:'客户端预报 " & 😀'});
const music=document.querySelector('.music-player-controller');
const weather=document.querySelector('#weather_123');
const valid=music.dataset.title===title && music.dataset.from==='Artist " & 😀' && weather.dataset.t==='北京 " & 😀' && !document.body.dataset.executed && document.querySelectorAll('#music img').length===1;
document.querySelector('#result').textContent=valid?'PASS：文字、属性和emoji完整保留；未插入标题中的HTML。':'FAIL';
</script>`;
  const server=http.createServer((req,res)=>{
    res.setHeader('Content-Security-Policy',"default-src 'self';script-src 'self' 'unsafe-inline';style-src 'unsafe-inline';img-src 'self';media-src 'self';frame-src 'self'");
    const name=req.url.slice(1);
    if (files.includes(name)) {
      res.setHeader('Content-Type', 'text/javascript; charset=utf-8');
      res.end(fs.readFileSync('src/main/resources/js/' + name));
    } else if (req.url === '/first' || req.url === '/second') {
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      res.end(page(req.url === '/second' ? 'chat-room-2.min.js' : 'chat-room.min.js'));
    } else {
      res.statusCode = 404;
      res.end();
    }
  });
  server.listen(0, '127.0.0.1', () => console.log('FIXTURE http://127.0.0.1:' + server.address().port + '/first'));
}
