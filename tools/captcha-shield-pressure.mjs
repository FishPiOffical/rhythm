import http from 'node:http';
import net from 'node:net';

const WINDOW_MS = 60_000;
const CONCURRENCY = 8;
// Mirrors Firewall's anonymous limit; a server policy change must fail acceptance.
const ANONYMOUS_THRESHOLD = 250;

/** No cookies, proxy headers, redirects or shared browser requests. Bind the real socket. */
export async function requestFromIp({ origin, ip, pathname, userAgent }) {
  if (!net.isIP(ip)) throw new Error('压力来源必须是本机配置中的数值IP');
  const target = new URL(pathname, origin);
  const host = target.hostname.replace(/^\[|\]$/g, '');
  if (target.protocol !== 'http:' || target.origin !== new URL(origin).origin
      || !net.isIP(host) || new URL(`http://${net.isIP(ip) === 6 ? `[${ip}]` : ip}`).hostname !== target.hostname) {
    throw new Error('压力请求只允许访问绑定来源IP自身的HTTP地址');
  }
  const started = Date.now();
  return new Promise((resolve, reject) => {
    const request = http.get({ hostname: host, port: target.port || 80, path: target.pathname + target.search,
      localAddress: ip, agent: false, headers: userAgent === undefined ? {} : { 'User-Agent': userAgent },
      signal: AbortSignal.timeout(10_000),
    }, response => {
      let preview = '';
      let bytes = 0;
      const localAddress = response.socket.localAddress;
      response.setEncoding('utf8');
      response.on('data', chunk => {
        bytes += Buffer.byteLength(chunk);
        if (preview.length < 400) preview += chunk.slice(0, 400 - preview.length);
      });
      response.on('error', reject);
      response.on('end', () => resolve({ url: target.href, pathname: target.pathname, status: response.statusCode,
        location: response.headers.location || null, localAddress, bytes,
        preview: preview.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 80), durationMs: Date.now() - started }));
    });
    request.on('error', error => {
      const detail = error.code === 'EADDRNOTAVAIL' ? '；本机已无法绑定此IP，请重新启动测试确认当前网卡IP，本轮不换源重试' : '';
      reject(new Error(`E ${ip} 请求 ${target.pathname} 失败：${error.message}${detail}`));
    });
  });
}

/** Injected I/O keeps the production-window rules testable without changing server policy. */
export async function runPressureProbe({ origin, ip, threshold, userAgent, request, observe, verify,
  monitor, sleep, record, progress }) {
  const limit = Math.min(threshold, ANONYMOUS_THRESHOLD);
  if (!Number.isSafeInteger(limit) || limit < 1) throw new Error('后台请求阈值不是有效正整数');
  let evidence = { status: 'RUNNING', sourceIp: ip, concurrency: CONCURRENCY, effectiveThreshold: limit,
    ipset: { status: 'NOT_VERIFIED', detail: '仅验应用HTTP与监控；不代表操作系统ipset执行成功' }, requests: [] };
  const publish = () => progress(evidence);
  const capture = responses => { evidence = { ...evidence, requests: [...evidence.requests, ...responses] }; publish(); };
  const send = (pathname, withUa = true) => request({ origin, ip, pathname, userAgent: withUa ? userAgent : undefined });
  function expect(response, status) {
    if (response.status !== status) throw new Error(`E ${response.pathname} 预期 HTTP ${status}，实际 HTTP ${response.status}；内容：${response.preview}`);
    if (status === 302 && (!response.location || new URL(response.location, origin).origin !== new URL(origin).origin
        || new URL(response.location, origin).pathname !== '/test')) throw new Error(`E ${response.pathname} 未明确跳转本站/test`);
    if (status !== 302 && response.location) throw new Error(`E HTTP ${status} 不应同时要求重定向`);
  }
  async function single(pathname, status, withUa = true) {
    let response;
    try { response = await send(pathname, withUa); }
    catch (error) { capture([{ pathname, error: error.message }]); throw error; }
    capture([response]);
    console.log(`[E] GET ${response.url} -> HTTP ${response.status}${response.location ? ` → ${response.location}` : ''}；来源 ${response.localAddress}；内容：${response.preview || '(空)'}`);
    expect(response, status);
    return response;
  }
  const cc = pathname => ({ method: 'GET', uri: pathname, type: 'cc', flow: 'BeforeRequestHandler -> Firewall' });
  const redirect = pathname => ({ method: 'GET', uri: pathname, type: 'captcha', result: 'redirect',
    reason: '黑名单跳转', flow: 'BeforeRequestHandler -> AnonymousCaptchaShield' });
  publish();
  console.log(`E ${ip}：8并发，仅请求主文档且不跟随跳转；应用封禁留到服务重启，不自动解封。`);
  await observe('E 匿名首访进入验证码黑名单', () => single('/', 302), {
    method: 'GET', uri: '/', type: 'captcha', result: 'challenge', reason: '首次访问',
    flow: 'AnonymousViewCheckMidware -> AnonymousCaptchaShield', userStatusPrefix: '访客（',
  });
  await observe('E 阈值内黑名单返回302', () => single('/about', 302), redirect('/about'));

  // Start in a fresh server minute, excluding preparation traffic from the boundary assertion.
  const snapshot = await monitor();
  const serverNow = Math.max(...snapshot.events.map(event => Number(event.timestamp)));
  if (!Number.isFinite(serverNow) || serverNow <= 0) throw new Error('监控缺少服务端时间，不能确定CC计数窗口');
  const waitMs = WINDOW_MS - serverNow % WINDOW_MS + 250;
  console.log(`等待服务端下一分钟开始（约${Math.ceil(waitMs / 1000)}秒），本次匿名阈值 ${limit}；等待期间不向E发请求。`);
  let remaining = waitMs;
  while (remaining > 0) {
    const chunk = Math.min(remaining, 1000);
    await sleep(chunk);
    remaining -= chunk;
    if (remaining > 0 && Math.ceil(remaining / 1000) % 10 === 0) console.log(`E 等待新计数窗口：剩余约${Math.ceil(remaining / 1000)}秒`);
  }
  const first = await observe('E 新分钟首请求仍被验证码黑名单拦截', () => single('/about', 302), redirect('/about'));
  const bucket = Math.floor(first.event.timestamp / WINDOW_MS);
  if (bucket <= Math.floor(serverNow / WINDOW_MS)) throw new Error('服务端尚未进入新分钟，不能累计阈值验收');
  evidence = { ...evidence, windowStartedAt: bucket * WINDOW_MS };
  publish();
  const beforeBurst = await monitor();
  const started = Date.now();
  for (let completed = 1; completed < limit;) {
    const size = Math.min(CONCURRENCY, limit - completed);
    const settled = await Promise.allSettled(Array.from({ length: size }, () => send('/about')));
    const responses = settled.map(item => item.status === 'fulfilled' ? item.value : { error: item.reason.message });
    capture(responses);
    const failure = settled.find(item => item.status === 'rejected');
    if (failure) throw failure.reason;
    for (const response of responses) expect(response, 302);
    completed += size;
    console.log(`E 本分钟 ${completed}/${limit}：本批${size}次全部 HTTP 302 → /test`);
  }
  const burstDurationMs = Date.now() - started;
  if (limit > 1) {
    const last = await verify('E 并发阈值内请求仍受验证码盾保护', beforeBurst, redirect('/about'), limit - 1);
    if (Math.floor(last.timestamp / WINDOW_MS) !== bucket) throw new Error('并发阶段跨过了服务端分钟边界，本轮不能判定阈值通过');
  }
  await record('E 阈值内请求完成', { requestCount: limit, burstRequestCount: limit - 1,
    peakConcurrency: Math.min(CONCURRENCY, limit - 1), burstDurationMs });
  const crossed = await observe('E 超过匿名阈值返回429且先于验证码跳转', () => single('/about', 429), {
    ...cc('/about'), result: 'ban', reason: '请求频率超限',
  });
  if (Math.floor(crossed.event.timestamp / WINDOW_MS) !== bucket) throw new Error('越界请求跨过服务端分钟边界，不能判定阈值通过');
  await observe('E 封禁后/test也返回429', () => single('/test', 429), { ...cc('/test'), resultIn: ['ban', 'deny'] });

  const beforeUa = await monitor();
  const missingUa = await single('/test', 418, false);
  const afterUa = await monitor();
  await record('E 无UA先返回418（HTTP证据，控制台记录不入面板）', { http: missingUa,
    monitorRead: { beforeTotal: beforeUa.total, afterTotal: afterUa.total } });
  evidence = { ...evidence, status: 'PASS', boundaryRequestCount: limit + 1 };
  publish();
  console.log('通过：E阈值内302、越界429、封禁后验证码页429、无UA418；Linux ipset未验。');
}
