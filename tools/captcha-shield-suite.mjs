/** Selectable real-browser acceptance modules. Browser and persistence are injected. */
export const TEST_MODULES = Object.freeze([
  { id: 'baseline', name: '登录基线', detail: 'L：主页10次、不同文章10页' },
  { id: 'captcha', name: '验证码', detail: 'A：首访、周期验证、登录放行' },
  { id: 'session', name: 'IP切换', detail: 'A/B：同账号切换后匿名回访' },
  { id: 'blacklist', name: '黑名单', detail: 'C：不同URL拦截、直接验证码页、验证恢复' },
  { id: 'unban', name: '管理解封', detail: 'D：真实解封、本次启动加白；会修改D状态' },
  { id: 'readonly', name: '生产只读', detail: 'L：控件、保存拒绝、权限、自检；须PRODUCTION' },
].map(Object.freeze));

export const FIRST_FLOW_MODULES = Object.freeze(['baseline', 'captcha', 'session']);

// AdminProcessor.isSecurityOperator restricts monitor/self-check to this account.
export const SECURITY_MONITOR_USERNAME = 'adlered';

export function parseTestModules(value) {
  const tokens = String(value).trim().split(/[,，\s]+/).filter(Boolean);
  if (!tokens.length) throw new Error('至少选择一个测试项目');
  if (tokens.length === 1 && tokens[0].toLowerCase() === 'all') return TEST_MODULES.map(item => item.id);
  const ids = tokens.map(token => {
    const item = /^\d+$/.test(token) ? TEST_MODULES[Number(token) - 1] : TEST_MODULES.find(module => module.id === token);
    if (!item) throw new Error(`未知测试项目：${token}`);
    return item.id;
  });
  return TEST_MODULES.filter(item => ids.includes(item.id)).map(item => item.id);
}

export function canonicalIp(value) {
  const ip = String(value).replace(/^\[|\]$/g, '').toLowerCase();
  if (ip.startsWith('::ffff:') && ip.includes('.')) return ip.slice(7);
  if (!ip.includes(':')) return ip;
  return new URL(`http://[${ip}]/`).hostname.replace(/^\[|\]$/g, '');
}

const eventKey = event => JSON.stringify([
  event.timestamp, event.ip, event.method, event.uri, event.flow, event.result, event.reason, event.userStatus,
]);

export function matchesCurrentEvent(event, before, expected) {
  const watermark = Math.max(0, ...before.events.map(item => Number(item.timestamp)));
  if (Number(event.timestamp) < watermark || before.events.some(item => eventKey(item) === eventKey(event))) return false;
  return Object.entries(expected).every(([key, value]) => {
    if (key === 'ip') return canonicalIp(event.ip) === canonicalIp(value);
    if (key === 'userStatusPrefix') return String(event.userStatus).startsWith(value);
    return event[key] === value;
  });
}

export async function runAcceptanceSuite({ config, selected, runtime: rt, persist }) {
  const ids = parseTestModules(selected.join(','));
  const baseline = config.accounts.find(account => account.key === 'baseline');
  const admin = config.accounts.find(account => account.key === 'admin');
  if (!baseline || !admin) throw new Error('配置缺少基线账号或管理员账号');
  const monitorAccount = config.accounts.find(account => account.username === SECURITY_MONITOR_USERNAME);
  if (!monitorAccount) throw new Error(`安全监控接口仅允许 ${SECURITY_MONITOR_USERNAME}，请补填监控账号后重试`);
  const entries = rt.entries(config);
  const sessions = new Map();
  const result = {
    startedAt: new Date().toISOString(), status: 'RUNNING',
    modules: ids.map(id => ({ id, name: TEST_MODULES.find(item => item.id === id).name, status: 'NOT_RUN', checks: [] })),
  };
  let currentModule;
  let monitorSession;
  let articles;
  let aLoggedIn;
  let documentProbeSequence = 0;
  const save = () => persist(result);
  const source = key => {
    const entry = entries.find(item => item.key === key);
    if (!entry) throw new Error(`配置缺少 ${key} 来源地址`);
    return entry;
  };
  const url = (session, pathname = '/') => new URL(pathname, rt.sourceUrl(config, session.entry.item)).href;
  async function open(key, sourceKey = key) {
    if (!sessions.has(key)) {
      const entry = { ...source(sourceKey), key };
      const purpose = key === 'Monitor-L' ? `安全监控，${SECURITY_MONITOR_USERNAME} 登录`
        : key === 'IP-admin' ? '管理员解封操作'
        : key === 'Guest-L' ? '匿名后台权限检查' : `${sourceKey} 来源业务测试`;
      console.log(`打开窗口 ${key}：${purpose}；地址 ${rt.sourceUrl(config, entry.item)}`);
      const session = await rt.start(entry);
      sessions.set(key, { ...session, entry, lastAt: { value: 0 } });
    }
    return sessions.get(key);
  }
  async function navigate(session, pathname, { freshDocument = false, ...options } = {}) {
    const target = new URL(url(session, pathname));
    if (freshDocument) {
      // Cacheable redirects (notably favicon) can skip the server entirely.
      // Vary only this document's query; keep subresource caching enabled.
      target.searchParams.set('_captcha_e2e', `${result.startedAt}-${++documentProbeSequence}`);
      console.log(`${session.entry.key} 本次边界请求：${target.href}（路径不变，避免复用旧跳转）`);
    }
    return rt.navigate(session.client, target.href, session.lastAt, { sourceKey: session.entry.key }, options);
  }
  function requireOk(session, href, pathname) {
    const status = session.client.lastDocumentResponse?.status;
    if (status !== 200 || rt.isCaptcha(href) || session.client.lastCaptchaRedirect
        || new URL(href).pathname !== pathname) {
      throw new Error(`${session.entry.key} 预期 ${pathname} HTTP 200 且无验证码跳转，实际 ${new URL(href).pathname} HTTP ${status || '未知'}`);
    }
  }
  async function noCaptcha(session, pathname) {
    const href = await navigate(session, pathname);
    requireOk(session, href, pathname);
    return href;
  }
  async function login(session, account) {
    await rt.login(session.entry, session.client, config, account, session.lastAt);
  }
  async function ensureMonitor() {
    if (!monitorSession) {
      console.log(`准备：L 地址 ${monitorAccount.username} 登录，用于核对本轮监控（不计为登录基线通过）。`);
      monitorSession = await open('Monitor-L', 'L');
      await login(monitorSession, monitorAccount);
      await noCaptcha(monitorSession, '/admin/misc');
      await rt.evaluate(monitorSession.client, `(() => {
        const checkbox = document.querySelector('#securityAutoRefresh');
        if (!checkbox) throw new Error('监控页面缺少自动刷新控件');
        checkbox.checked = false;
      })()`);
    }
    return monitorSession;
  }
  async function monitor() {
    const session = await ensureMonitor();
    const data = await rt.evaluate(session.client, `(async () => {
      if (!document.querySelector('.security-panel')) throw new Error('未找到社区安全面板');
      const response = await fetch('/admin/security/monitor', {cache:'no-store', signal:AbortSignal.timeout(10000)});
      if (!response.ok || response.redirected) throw new Error('监控接口 '+new URL(response.url).pathname+' 返回 HTTP '+response.status+'（与后台页面状态独立；仅允许 ${SECURITY_MONITOR_USERNAME}）');
      const body = await response.json();
      if (body.code !== 0 || !Array.isArray(body.data?.events) || !Number.isFinite(body.data?.total)) throw new Error('监控响应格式错误');
      const data = body.data;
      document.querySelector('#securityAutoRefresh').checked = false;
      SecuritySwitches.renderEvents(data.events);
      document.querySelector('#securityTotal').textContent = data.total;
      document.querySelector('#securityLast10').textContent = data.last10Minutes;
      document.querySelector('#securityRefreshMessage').textContent = '验收核对：'+new Date().toLocaleTimeString();
      return data;
    })()`);
    return data;
  }
  async function verifyMonitor(label, session, before, expected) {
    const target = { ip: session.entry.item.ip, ...expected };
    // localhost may resolve to either loopback family. Learn the actual monitor IP from its own authenticated request.
    if (session.entry.item === source('L').item) {
      const own = before.events.find(event => event.uri === '/admin/security/monitor'
        && event.userStatus === `本人登录（用户：${monitorAccount.username}）`);
      if (!own) throw new Error('无法从管理员监控请求确认 localhost 的实际来源IP');
      target.ip = own.ip;
    }
    let match;
    let after;
    const deadline = Date.now() + 8000;
    do {
      after = await monitor();
      match = after.events.find(event => matchesCurrentEvent(event, before, target));
      if (match) break;
      await rt.sleep(500);
    } while (Date.now() < deadline);
    if (!match) {
      const recent = after.events.filter(event => canonicalIp(event.ip) === canonicalIp(target.ip))
        .slice(0, 5).map(event => `${event.method} ${event.uri} ${event.flow} ${event.result} ${event.reason}`).join('；');
      throw new Error(`${label}：未找到本轮监控 ${JSON.stringify(target)}；该IP近期记录：${recent || '无'}。记录可能已超出100条保留范围，不能据此判通过。`);
    }
    const counterKey = `${match.type}.${match.result}`;
    if (after.total <= before.total || Number(after.counts?.[counterKey] || 0) <= Number(before.counts?.[counterKey] || 0)) {
      throw new Error(`${label}：匹配到记录但对应统计未增长 ${counterKey}`);
    }
    const visible = await rt.evaluate(monitorSession.client, `Array.from(document.querySelectorAll('#securityEvents tr')).map(row=>Array.from(row.cells).map(cell=>cell.textContent.trim()))`);
    const cells = [match.time, match.ip, match.userStatus, `${match.method} ${match.uri}`, match.handler, match.flow, match.result, match.reason];
    if (!visible.some(row => row.length === 8 && row.every((cell, index) => cell === cells[index]))) {
      throw new Error(`${label}：监控接口有记录，但后台8列显示不一致`);
    }
    console.log(`通过：${label}；日志 ${match.ip} ${match.method} ${match.uri}；${match.handler}；${match.flow}；${match.result}/${match.reason}；${match.userStatus}；统计 ${counterKey} +${after.counts[counterKey] - (before.counts[counterKey] || 0)}`);
    currentModule.checks.push({ label, status: 'PASS', event: match, counterKey, counterDelta: after.counts[counterKey] - (before.counts[counterKey] || 0) });
    save();
    return match;
  }
  async function observe(label, session, action, expected) {
    const before = await monitor();
    const value = await action();
    await verifyMonitor(label, session, before, expected);
    return value;
  }
  const cc = (pathname, extra = {}) => ({ method: 'GET', uri: pathname, type: 'cc', result: 'allow', flow: 'BeforeRequestHandler -> Firewall', ...extra });
  const challenge = (pathname, resultType = 'challenge') => ({ method: 'GET', uri: pathname, type: 'captcha', result: resultType,
    flow: resultType === 'redirect' ? 'BeforeRequestHandler -> AnonymousCaptchaShield' : 'AnonymousViewCheckMidware -> AnonymousCaptchaShield' });
  async function enterChallenge(session) {
    const href = await navigate(session, '/');
    if (!rt.isCaptcha(href)) {
      if (!session.client.lastCaptchaRedirect) throw new Error(`${session.entry.key} 首访未进入/test；请确认历史IP白名单和服务重启准备`);
      await rt.ensureCaptcha(session.entry, session.client, url(session), session.lastAt);
    }
  }
  async function completeChallenge(session) {
    await observe(`${session.entry.key} 人工极验提交`, session,
      () => rt.waitCaptcha(session.entry, session.client), { ...cc('/validateCaptcha'), method: 'POST', handler: 'CaptchaProcessor#validateCaptcha' });
    await observe(`${session.entry.key} 验证后公开页恢复`, session, () => noCaptcha(session, '/about'), cc('/about', { handler: 'IndexProcessor#showAbout' }));
  }
  async function getArticles() {
    if (!articles) {
      const session = await ensureMonitor();
      await noCaptcha(session, '/');
      articles = await rt.articles(session.client, config, session.entry);
      await noCaptcha(session, '/admin/misc');
    }
    return articles.map(value => new URL(value).pathname);
  }
  async function prepareA() {
    if (aLoggedIn) return aLoggedIn;
    const a = await open('A');
    console.log('准备：A 完成首访极验并登录，用于IP切换；不执行未选择的周期测试。');
    await observe('准备A首访验证', a, () => enterChallenge(a), { ...challenge('/'), reason: '首次访问' });
    await completeChallenge(a);
    await login(a, admin);
    aLoggedIn = a;
    return a;
  }
  const modules = {
    async baseline() {
      const l = await open('L');
      await login(l, baseline);
      await noCaptcha(l, '/');
      articles = await rt.articles(l.client, config, l.entry);
      for (let i = 1; i <= 10; i++) {
        await observe(`L 主页 ${i}/10`, l, () => noCaptcha(l, '/'), cc('/', { handler: 'IndexProcessor#showIndex', userStatus: `本人登录（用户：${baseline.username}）` }));
      }
      for (const [index, article] of articles.entries()) {
        const pathname = new URL(article).pathname;
        await observe(`L 文章 ${index + 1}/10`, l, () => noCaptcha(l, pathname), cc(pathname, { userStatus: `本人登录（用户：${baseline.username}）` }));
      }
    },
    async captcha() {
      const paths = await getArticles();
      const a = await open('A');
      await observe('A 首访验证', a, () => enterChallenge(a), { ...challenge('/'), reason: '首次访问' });
      await completeChallenge(a);
      let redirected = false;
      for (let i = 1; i <= 5; i++) {
        const pathname = i <= 2 ? '/' : paths[i - 3];
        const before = await monitor();
        const href = await navigate(a, pathname);
        if (rt.isCaptcha(href) || a.client.lastCaptchaRedirect) {
          if (!rt.isCaptcha(href)) await rt.ensureCaptcha(a.entry, a.client, url(a), a.lastAt);
          const event = await verifyMonitor('A 周期验证日志', a, before, { type: 'captcha', result: 'challenge', reason: '访问满5次',
            flow: 'AnonymousViewCheckMidware -> AnonymousCaptchaShield' });
          console.log(`通过：A 第 ${i} 次页面访问触发周期验证；日志 ${event.method} ${event.uri} ${event.reason}`);
          currentModule.checks.push({ label: 'A 周期验证', status: 'PASS', pageVisits: i, event });
          save();
          await completeChallenge(a);
          redirected = true;
          break;
        }
        requireOk(a, href, pathname);
      }
      if (!redirected) throw new Error('A 五次页面访问内未重新触发验证码');
      await login(a, admin);
      aLoggedIn = a;
      for (let i = 0; i < 5; i++) {
        const pathname = i < 2 ? '/' : paths[i - 2];
        await observe(`A 登录后放行 ${i + 1}/5`, a, () => noCaptcha(a, pathname), cc(pathname, { userStatus: `本人登录（用户：${admin.username}）` }));
      }
    },
    async session() {
      const a = await prepareA();
      const b = await open('B');
      await observe('B 首访验证', b, () => enterChallenge(b), { ...challenge('/'), reason: '首次访问' });
      await completeChallenge(b);
      await login(b, admin);
      await observe('B 登录后放行', b, () => noCaptcha(b, '/admin/misc'), cc('/admin/misc', { handler: 'AdminProcessor#showMisc', userStatus: `本人登录（用户：${admin.username}）` }));
      await rt.close(a);
      sessions.delete('A');
      const returned = await open('A-return', 'A');
      await observe('匿名A回访或一次强刷进入验证', returned, async () => {
        let href = await navigate(returned, '/');
        if (!rt.isCaptcha(href) && !returned.client.lastCaptchaRedirect) {
          console.log('A 首次回访未进入/test，执行一次强刷（额外真实请求，单独记录）。');
          currentModule.checks.push({ label: 'A 首次回访', status: 'OBSERVED', httpStatus: returned.client.lastDocumentResponse?.status, pathname: new URL(href).pathname });
          href = await navigate(returned, '/', { reload: true });
        }
        if (!rt.isCaptcha(href) && !returned.client.lastCaptchaRedirect) throw new Error('A 回访及一次强刷均未进入/test');
        if (!rt.isCaptcha(href)) await rt.ensureCaptcha(returned.entry, returned.client, url(returned), returned.lastAt);
      }, challenge('/'));
      await completeChallenge(returned);
    },
    async blacklist() {
      const c = await open('C');
      console.log('C 黑名单测试：先不要完成验证码；检查不同URL后，脚本会提示你手动验证。');
      await observe('C 加入验证码黑名单', c, () => enterChallenge(c), { ...challenge('/'), reason: '首次访问' });
      const missing = `/captcha-e2e-missing-${Date.now()}`;
      for (const pathname of ['/', '/about', '/login', '/article/0', missing, '/favicon.ico', '/api/user']) {
        await observe(`C 黑名单拦截 ${pathname}`, c, async () => {
          const href = await navigate(c, pathname, { freshDocument: true });
          if (!rt.isCaptcha(href) || c.client.lastDocumentResponse?.status !== 200) throw new Error(`C 黑名单访问 ${pathname} 未回到/test`);
        }, { ...challenge(pathname, 'redirect'), reason: '黑名单跳转' });
      }
      await observe('C 黑名单直接访问/test', c, async () => {
        const href = await navigate(c, '/test', { freshDocument: true });
        if (!rt.isCaptcha(href) || c.client.lastDocumentResponse?.status !== 200) throw new Error('C 直接/test未显示验证码页');
      }, cc('/test', { handler: 'CaptchaProcessor#captcha' }));
      await completeChallenge(c);
    },
    async unban() {
      const d = await open('D');
      console.log('D 管理解封测试：不要完成D的验证码，由管理员表单自动解封。');
      await observe('D 解封前进入黑名单', d, () => enterChallenge(d), { ...challenge('/'), reason: '首次访问' });
      const targetIp = currentModule.checks.at(-1).event.ip;
      const operator = await open('IP-admin', 'L');
      await login(operator, admin);
      await noCaptcha(operator, '/admin/ip');
      console.log(`准备通过IP管理真实解封D：${targetIp}（按服务端实际IP格式）；仅验证应用内加白，Windows不证明ipset成功。`);
      await rt.evaluate(operator.client, `(() => {
        const form = document.querySelector('#ipListForm');
        const input = form?.querySelector('textarea[name="ipList"]');
        const button = form?.querySelector('button[onclick="Util.ipAction(2)"]');
        if (!input || !button) throw new Error('IP管理表单/解封按钮与已核对界面不一致');
        input.value = ${JSON.stringify(targetIp)};
        input.dispatchEvent(new Event('input', {bubbles:true}));
        button.click();
      })()`);
      const deadline = Date.now() + 15000;
      let released = false;
      while (Date.now() < deadline) {
        const href = await navigate(d, '/about');
        const snapshot = await monitor();
        released = !rt.isCaptcha(href) && snapshot.events.some(event => canonicalIp(event.ip) === canonicalIp(d.entry.item.ip)
          && event.uri === '/about' && event.userStatus === '访客+白名单IP（管理员解封）');
        if (released) break;
        await rt.sleep(1000);
      }
      if (!released) throw new Error('D解封后未观察到放行及“管理员解封”白名单；后台302不代表异步解封成功，请查服务端日志');
      for (let i = 1; i <= 6; i++) {
        await observe(`D 管理解封后放行 ${i}/6`, d, () => noCaptcha(d, '/'), cc('/', { userStatus: '访客+白名单IP（管理员解封）' }));
      }
      await observe('D 加白后直接/test返回首页', d, async () => {
        const href = await navigate(d, '/test');
        requireOk(d, href, '/');
      }, cc('/test', { handler: 'CaptchaProcessor#captcha' }));
    },
    async readonly() {
      const session = await ensureMonitor();
      await noCaptcha(session, '/admin/misc');
      const stateExpression = `(() => {
        const ids = ['firewallEnabled','firewallThreshold','verificationEnabled','firstVisitCaptchaEnabled','securitySaveButton'];
        return {mode:document.querySelector('.security-mode strong')?.textContent.trim(), controls:ids.map(id=>{
          const e=document.getElementById(id); if(!e)throw new Error('缺少安全控件 '+id);
          return {id,disabled:e.disabled,value:e.type==='checkbox'?e.checked:e.type==='number'?e.value:null};
        })};
      })()`;
      const before = await rt.evaluate(session.client, stateExpression);
      if (before.mode !== 'PRODUCTION' || before.controls.some(item => !item.disabled)) throw new Error('生产只读测试要求PRODUCTION且四项设置与保存均disabled');
      currentModule.checks.push({ label: '生产设置控件只读', status: 'PASS', state: before });
      save();
      const denied = await rt.evaluate(session.client, `(async () => {
        const token = Array.from(document.scripts).map(script=>script.textContent).join(' ').match(/'csrfToken'\\s*:\\s*'([^']+)'/)?.[1];
        if (!token) throw new Error('无法从实际保存脚本读取CSRF令牌，未发送请求');
        const response = await fetch('/admin/security/save', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded',csrfToken:token},
          body:new URLSearchParams({firewallEnabled:document.querySelector('#firewallEnabled').checked,
            verificationEnabled:document.querySelector('#verificationEnabled').checked,
            firstVisitCaptchaEnabled:document.querySelector('#firstVisitCaptchaEnabled').checked,
            threshold:document.querySelector('#firewallThreshold').value}), signal:AbortSignal.timeout(10000)});
        return {status:response.status,redirected:response.redirected};
      })()`);
      if (denied.status !== 403 || denied.redirected) throw new Error(`生产保存入口未明确拒绝修改 HTTP ${denied.status}`);
      await noCaptcha(session, '/admin/misc');
      const after = await rt.evaluate(session.client, stateExpression);
      if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error('生产保存请求后安全设置发生变化');
      currentModule.checks.push({ label: '有效页面凭据提交原设置仍403且状态未变', status: 'PASS', httpStatus: denied.status });
      const selfCheck = await rt.evaluate(session.client, `(async () => {
        const response=await fetch('/admin/security/self-check',{cache:'no-store',signal:AbortSignal.timeout(10000)});
        if(!response.ok || response.redirected)throw new Error('自检请求失败 HTTP '+response.status);
        const result=await response.json();
        if(result.code!==0 || !Array.isArray(result.data?.checks))throw new Error('自检响应格式错误');
        return result.data;
      })()`);
      if (selfCheck.status !== 'PASS' || selfCheck.checks.some(item => !item.passed)) throw new Error('后台结构自检未全部通过');
      currentModule.checks.push({ label: '后台结构自检（不代替真实请求测试）', status: 'PASS', selfCheck });
      const guest = await open('Guest-L', 'L');
      for (const pathname of ['/admin/security/monitor', '/admin/security/self-check']) {
        await observe(`匿名拒绝 ${pathname}`, guest, async () => {
          // /login is public and supplies an origin for browser fetch without triggering anonymous-page counters.
          await noCaptcha(guest, '/login');
          const response = await rt.evaluate(guest.client, `(async()=>{const r=await fetch(${JSON.stringify(pathname)},{cache:'no-store',signal:AbortSignal.timeout(10000)});return {status:r.status,redirected:r.redirected,body:(await r.text()).slice(0,120)};})()`);
          if (response.status !== 403 || response.redirected) throw new Error(`匿名后台读取未403：${pathname} HTTP ${response.status}`);
        }, cc(pathname, { userStatusPrefix: '访客' }));
      }
      console.log('通过：生产控件只读、真实保存403且状态未变、匿名后台拒绝、结构自检。开发模式修改另行验收。');
    },
  };
  save();
  try {
    for (const module of result.modules) {
      currentModule = module;
      module.status = 'RUNNING';
      save();
      console.log(`\n=== ${module.name}（${module.id}） ===`);
      // Prepare and validate the monitor before creating a business window.
      if (!monitorSession) await monitor();
      await modules[module.id]();
      module.status = 'PASS';
      save();
      console.log(`模块通过：${module.name}`);
    }
    result.status = 'PASS';
    result.finishedAt = new Date().toISOString();
    save();
    return result;
  } catch (error) {
    result.status = 'FAIL';
    result.finishedAt = new Date().toISOString();
    currentModule.status = 'FAIL';
    currentModule.error = error.message;
    save();
    throw error;
  }
}
