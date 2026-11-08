#!/usr/bin/env node
/**
 * Captcha-shield acceptance runner. Configuration, browser compatibility, and
 * the staged business flow are exposed as separate commands.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import net from 'node:net';
import readline from 'node:readline/promises';
import { TEST_MODULES, FIRST_FLOW_MODULES, SECURITY_MONITOR_USERNAME, parseTestModules, runAcceptanceSuite } from './captcha-shield-suite.mjs';
import { stdin as input, stdout as output } from 'node:process';

const REQUIRED_EXTRA_IPS = 5;
const ACCOUNT_REQUIREMENTS = [
  { key: 'baseline', label: '普通登录基线账号', roleHint: '普通用户（不要填 admin）' },
  { key: 'admin', label: '管理员账号', roleHint: '必须属于 admin/管理员分组' },
];
const CONFIG_DIR = process.env.LOCALAPPDATA
  ? path.join(process.env.LOCALAPPDATA, 'RhythmCaptchaShieldE2E')
  : path.join(os.homedir(), '.rhythm-captcha-shield-e2e');
const CONFIG_PATH = path.join(CONFIG_DIR, 'config.json');
const DRIVER_START_TIMEOUT_MS = 10_000;
const DRIVER_REQUEST_TIMEOUT_MS = 15_000;
const NAVIGATION_TIMEOUT_MS = 45_000;
const PAGE_READY_TIMEOUT_MS = 15_000;
const NAVIGATION_INTERVAL_MS = 1_500;

const rl = readline.createInterface({ input, output });
const lastNavigationAt = new Map();

function ask(question, defaultValue = '') {
  const suffix = defaultValue ? ` [${defaultValue}]` : '';
  return rl.question(`${question}${suffix}: `).then(value => value.trim() || defaultValue);
}

function powershell(command, extraEnv = {}) {
  // Node can inherit PowerShell 7 module paths. Passing them to Windows
  // PowerShell 5.1 causes duplicate type-data imports and breaks DPAPI cmdlets.
  const windowsPowerShellModules = [
    process.env.WINDIR ? `${process.env.WINDIR}\\System32\\WindowsPowerShell\\v1.0\\Modules` : '',
    process.env.ProgramFiles ? `${process.env.ProgramFiles}\\WindowsPowerShell\\Modules` : '',
  ].filter(Boolean).join(';');
  return execFileSync('powershell.exe', [
    '-NoProfile',
    '-NonInteractive',
    '-ExecutionPolicy',
    'Bypass',
    '-Command',
    command,
  ], {
    encoding: 'utf8',
    windowsHide: true,
    env: { ...process.env, ...extraEnv, PSModulePath: windowsPowerShellModules },
    maxBuffer: 1024 * 1024,
  }).trim();
}

function protectSecret(secret) {
  if (!secret) return '';
  return powershell(
    '$secure = ConvertTo-SecureString -String $env:RH_SECRET -AsPlainText -Force; ConvertFrom-SecureString $secure',
    { RH_SECRET: secret },
  );
}

function unprotectSecret(protectedSecret) {
  if (!protectedSecret) return '';
  return powershell(
    '$secure = ConvertTo-SecureString -String $env:RH_PROTECTED; $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }',
    { RH_PROTECTED: protectedSecret },
  );
}

function listLocalIps() {
  const command = [
    '$items = Get-NetIPAddress -ErrorAction Stop;',
    '$items = $items | Where-Object { $_.IPAddress -notin @("0.0.0.0", "::") -and $_.AddressState -eq "Preferred" };',
    '$items | Select-Object InterfaceAlias, AddressFamily, IPAddress, PrefixLength, AddressState, SkipAsSource | Sort-Object InterfaceAlias, AddressFamily, IPAddress | ConvertTo-Json -Compress',
  ].join(' ');
  const raw = powershell(command);
  if (!raw) return [];
  const parsed = JSON.parse(raw);
  return (Array.isArray(parsed) ? parsed : [parsed]).map(item => ({
    interfaceAlias: String(item.InterfaceAlias || ''),
    addressFamily: String(item.AddressFamily || ''),
    ip: String(item.IPAddress || ''),
    prefixLength: Number(item.PrefixLength || 0),
    addressState: String(item.AddressState || ''),
    skipAsSource: Boolean(item.SkipAsSource),
  }));
}

function isLoopback(item) {
  return item.interfaceAlias.toLowerCase().includes('loopback')
    || item.ip === '127.0.0.1'
    || item.ip === '::1';
}

function isIpv4(item) {
  return item.addressFamily === '2' && !item.ip.includes(':');
}

function isUsableIpv6(item) {
  const ip = item.ip.toLowerCase();
  return item.addressFamily === '23'
    && ip.includes(':')
    && !ip.startsWith('fe80:')
    && !ip.startsWith('::1')
    && !ip.includes('%');
}

function printIps(items) {
  if (!items.length) {
    console.log('未发现可用网卡地址。');
    return;
  }
  console.log('\n本机可用网卡地址：');
  items.forEach((item, index) => {
    const kind = isLoopback(item) ? 'L候选' : '额外IP候选';
    console.log(`  [${index + 1}] ${item.ip}/${item.prefixLength}  ${item.interfaceAlias}  ${item.addressFamily}  ${kind}`);
  });
}

function saveConfig(config) {
  fs.mkdirSync(CONFIG_DIR, { recursive: true });
  fs.writeFileSync(CONFIG_PATH, `${JSON.stringify(config, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 });
  try {
    fs.chmodSync(CONFIG_PATH, 0o600);
  } catch {
    // Windows ACLs, not POSIX mode bits, protect the LOCALAPPDATA file.
  }
}

function loadConfig() {
  if (!fs.existsSync(CONFIG_PATH)) return null;
  return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
}

async function withSecurityMonitorAccount(config) {
  if (!config?.accounts) throw new Error('请先运行配置向导');
  const existing = config.accounts.find(account => account.username === SECURITY_MONITOR_USERNAME && account.passwordProtected);
  if (existing) {
    console.log(`安全监控复用已保存账号：${existing.username}（仅使用 L 地址）。`);
    return config;
  }
  console.log(`安全监控和自检接口仅允许 ${SECURITY_MONITOR_USERNAME}，A/B 仍使用原管理员测试账号。`);
  console.log('只需补填一次监控账号密码；须确认该账号属于管理员分组，后续自动复用。');
  const password = await ask(`${SECURITY_MONITOR_USERNAME} 密码（直接输入，保存时会加密）`);
  if (!password) throw new Error('监控账号密码不能为空');
  return { ...config, accounts: [...config.accounts.filter(account => account.key !== 'monitor'), {
    key: 'monitor', label: '安全监控账号', username: SECURITY_MONITOR_USERNAME,
    role: 'admin', passwordProtected: protectSecret(password),
  }] };
}

async function prepareSecurityMonitorConfig(config) {
  const prepared = await withSecurityMonitorAccount(config);
  if (prepared !== config) saveConfig(prepared);
  return prepared;
}

function maskUsername(account) {
  return account.username || '(未填写)';
}

function showConfig(config) {
  if (!config) {
    console.log(`尚未保存配置：${CONFIG_PATH}`);
    return;
  }
  console.log(`配置文件：${CONFIG_PATH}`);
  console.log(`测试地址：${config.baseUrl}`);
  console.log(`期望 commit：${config.expectedCommit || '(未填写)'}`);
  console.log(`账号数量：${config.accounts.length}`);
  config.accounts.forEach((account, index) => {
    console.log(`  ${index + 1}. ${maskUsername(account)} / 权限组：${account.role || '(未填写)'}`);
  });
  console.log(`L：${config.ipPlan.loopback?.ip || '(未选择)'}`);
  config.ipPlan.extra.forEach((item, index) => console.log(`${String.fromCharCode(65 + index)}：${item.ip}`));
  console.log(`ChromeDriver：${config.chromeDriverPath || '(未设置，将从 PATH 或环境变量查找)'}`);
  console.log(`上次自选测试：${config.testModules?.join(',') || '(未选择)'}`);
  console.log(`最近保存：${config.updatedAt || '(未知)'}`);
}

function findChromeDriver(config) {
  const candidates = [
    config?.chromeDriverPath,
    process.env.CHROMEDRIVER_PATH,
    path.join(process.cwd(), 'tools', 'chromedriver.exe'),
    path.join(process.env.LOCALAPPDATA || '', 'chromedriver', 'chromedriver.exe'),
  ].filter(Boolean);
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return candidate;
  }
  try {
    const result = execFileSync('where.exe', ['chromedriver.exe'], {
      encoding: 'utf8',
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim().split(/\r?\n/).find(Boolean);
    if (result && fs.existsSync(result)) return result;
  } catch {
    // A missing driver is reported with an actionable message below.
  }
  return null;
}

function findChromeExecutable() {
  const candidates = [
    process.env.CHROME_PATH,
    path.join(process.env.ProgramFiles || '', 'Google', 'Chrome', 'Application', 'chrome.exe'),
    path.join(process.env['ProgramFiles(x86)'] || '', 'Google', 'Chrome', 'Application', 'chrome.exe'),
    path.join(process.env.LOCALAPPDATA || '', 'Google', 'Chrome', 'Application', 'chrome.exe'),
  ].filter(Boolean);
  return candidates.find(candidate => fs.existsSync(candidate)) || null;
}

async function configureDriver() {
  const config = loadConfig();
  if (!config) throw new Error('尚未找到配置文件，请先运行 configure 保存测试配置');
  console.log('=== 设置 ChromeDriver ===');
  console.log('只保存驱动路径，不会读取或保存账号密码。');
  const current = config.chromeDriverPath || process.env.CHROMEDRIVER_PATH || '';
  const entered = await ask('chromedriver.exe 路径', current);
  if (!entered) throw new Error('ChromeDriver 路径不能为空');
  const driverPath = path.resolve(entered);
  if (!fs.existsSync(driverPath) || !fs.statSync(driverPath).isFile()) {
    throw new Error(`找不到 ChromeDriver 文件：${driverPath}`);
  }
  config.chromeDriverPath = driverPath;
  config.updatedAt = new Date().toISOString();
  saveConfig(config);
  console.log(`ChromeDriver 路径已保存：${driverPath}`);
}

async function findFreePort() {
  const server = await new Promise((resolve, reject) => {
    const candidate = net.createServer();
    candidate.once('error', reject);
    candidate.listen(0, '127.0.0.1', () => resolve(candidate));
  });
  const address = server.address();
  const port = typeof address === 'object' && address ? address.port : 0;
  await new Promise(resolve => server.close(resolve));
  return port;
}

async function waitForDriver(driverUrl, processHandle) {
  const deadline = Date.now() + DRIVER_START_TIMEOUT_MS;
  let lastError;
  while (Date.now() < deadline) {
    if (processHandle.exitCode !== null) {
      throw new Error(`ChromeDriver 提前退出（退出码 ${processHandle.exitCode}）`);
    }
    try {
      const response = await fetch(`${driverUrl}/status`, { signal: AbortSignal.timeout(1000) });
      if (response.ok) return;
    } catch (error) {
      lastError = error;
    }
    await new Promise(resolve => setTimeout(resolve, 150));
  }
  throw new Error(`等待 ChromeDriver 超时${lastError ? `：${lastError.message}` : ''}`);
}

async function webdriverRequest(driverUrl, endpoint, method = 'GET', body, timeoutMs = DRIVER_REQUEST_TIMEOUT_MS) {
  const response = await fetch(`${driverUrl}${endpoint}`, {
    method,
    headers: body === undefined ? undefined : { 'content-type': 'application/json; charset=utf-8' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const raw = await response.text();
  let payload;
  try {
    payload = raw ? JSON.parse(raw) : {};
  } catch {
    throw new Error(`ChromeDriver 返回了无法解析的响应（HTTP ${response.status}）`);
  }
  if (!response.ok || payload.value?.error) {
    const detail = payload.value?.message || payload.value?.error || `HTTP ${response.status}`;
    throw new Error(`ChromeDriver 请求失败：${detail}`);
  }
  return payload.value;
}

const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

async function webdriverExecute(driverUrl, sessionId, script, args = []) {
  return webdriverRequest(driverUrl, `/session/${sessionId}/execute/sync`, 'POST', { script, args });
}

async function webdriverFind(driverUrl, sessionId, using, value) {
  return webdriverRequest(driverUrl, `/session/${sessionId}/element`, 'POST', { using, value });
}

function elementId(element) {
  return element['element-6066-11e4-a52e-4f735466cecf'] || element.ELEMENT;
}

async function sendElementKeys(driverUrl, sessionId, element, text) {
  const id = elementId(element);
  return webdriverRequest(driverUrl, `/session/${sessionId}/element/${id}/value`, 'POST', { text, value: [...text] });
}

async function clickElement(driverUrl, sessionId, element) {
  return webdriverRequest(driverUrl, `/session/${sessionId}/element/${elementId(element)}/click`, 'POST', {});
}

async function currentUrl(driverUrl, sessionId) {
  return webdriverRequest(driverUrl, `/session/${sessionId}/url`);
}

async function navigate(driverUrl, sessionId, url) {
  const previousNavigation = lastNavigationAt.get(sessionId);
  if (previousNavigation) {
    const remaining = NAVIGATION_INTERVAL_MS - (Date.now() - previousNavigation);
    if (remaining > 0) {
      console.log(`请求节流：等待 ${(remaining / 1000).toFixed(1)} 秒后访问 ${new URL(url).pathname || '/'}`);
      await sleep(remaining);
    }
  }
  lastNavigationAt.set(sessionId, Date.now());
  await webdriverRequest(driverUrl, `/session/${sessionId}/url`, 'POST', { url }, NAVIGATION_TIMEOUT_MS);
  const deadline = Date.now() + PAGE_READY_TIMEOUT_MS;
  let state = null;
  while (Date.now() < deadline) {
    state = await webdriverExecute(driverUrl, sessionId, 'return {readyState: document.readyState, pendingImages: [...document.images].filter(image => image.src && !image.complete).length};');
    if (state?.readyState === 'complete' || state?.readyState === 'interactive') break;
    await sleep(250);
  }
  if (!['complete', 'interactive'].includes(state?.readyState)) {
    throw new Error(`页面未在 ${PAGE_READY_TIMEOUT_MS / 1000} 秒内完成加载：${new URL(url).pathname || '/'}`);
  }
  if (state.pendingImages) {
    const pending = await webdriverExecute(driverUrl, sessionId, 'return [...document.images].filter(image => image.src && !image.complete).slice(0, 5).map(image => image.src);');
    const paths = (Array.isArray(pending) ? pending : []).map(resource => {
      try { return new URL(resource).pathname; } catch (_) { return '[未知资源]'; }
    });
    console.log(`提示：${new URL(url).pathname || '/'} 有 ${state.pendingImages} 个图片资源仍在后台加载，已忽略，不阻断本轮访问：${paths.join('、')}`);
  }
  const actualUrl = await currentUrl(driverUrl, sessionId);
  const failure = await webdriverExecute(driverUrl, sessionId, `return {
    title: document.title || '',
    body: (document.body && document.body.innerText || '').slice(0, 4000)
  };`);
  const failureText = `${failure?.title || ''}\n${failure?.body || ''}`;
  if (/\b503\b|service unavailable|服务不可用|暂不可用/i.test(failureText)) {
    throw new Error(`页面返回或显示 503（${new URL(actualUrl).pathname || '/'}），已停止继续请求，避免扩大封禁：${String(failure?.title || '').trim() || 'Service Unavailable'}`);
  }
  return actualUrl;
}

async function createChromeSession(driverUrl, entry, profileRoot = path.join(CONFIG_DIR, 'profiles')) {
  const profileDir = path.join(profileRoot, entry.key);
  fs.mkdirSync(profileDir, { recursive: true });
  const capabilities = {
    capabilities: {
      alwaysMatch: {
        browserName: 'chrome',
        pageLoadStrategy: 'eager',
        'goog:chromeOptions': {
          args: [
            `--user-data-dir=${profileDir}`,
            '--new-window',
            '--no-first-run',
            '--no-default-browser-check',
            '--disable-notifications',
          ],
        },
      },
    },
  };
  const session = await webdriverRequest(driverUrl, '/session', 'POST', capabilities);
  return session.sessionId;
}

async function resetBrowserSession(driverUrl, sessionId) {
  // A complete flow must start logged out even when a previous run left a
  // persistent profile behind. Cookies are the authoritative session state;
  // storage is cleared as well so old client flags cannot alter the flow.
  await webdriverRequest(driverUrl, `/session/${sessionId}/cookie`, 'DELETE');
  await webdriverExecute(driverUrl, sessionId, 'try { localStorage.clear(); sessionStorage.clear(); } catch (_) {}');
}

function sourceEntries(config) {
  return [
    { key: 'L', label: 'L（回环）', item: config.ipPlan.loopback },
    ...config.ipPlan.extra.map((item, index) => ({
      key: String.fromCharCode(65 + index),
      label: String.fromCharCode(65 + index),
      item,
    })),
  ];
}

function sourceUrl(config, item) {
  const host = item.ip === '127.0.0.1' || item.ip === '::1'
    ? 'localhost'
    : item.ip.includes(':') ? `[${item.ip}]` : item.ip;
  return `http://${host}:${config.port}/`;
}

function isCaptchaPath(rawUrl) {
  try {
    return new URL(rawUrl).pathname.replace(/\/+$/, '') === '/test';
  } catch {
    return false;
  }
}

async function waitForCaptchaCompletion(entry, driverUrl, sessionId) {
  console.log(`\n${entry.key} 已进入验证码页面（/test）。`);
  console.log('请在刚打开的 Chrome 窗口中手动完成极验；脚本不会模拟或伪造验证。');
  await rl.question('完成后按 Enter 继续：');
  const currentUrl = await webdriverRequest(driverUrl, `/session/${sessionId}/url`);
  if (isCaptchaPath(currentUrl)) {
    throw new Error(`${entry.key} 仍停留在 /test，未确认验证码已完成`);
  }
  console.log(`${entry.key} 已离开 /test，当前页面路径：${new URL(currentUrl).pathname || '/'}`);
}

async function startChromeDriver(config) {
  const driverPath = findChromeDriver(config);
  if (!driverPath) {
    throw new Error('未找到 chromedriver.exe。请安装与 Chrome 主版本匹配的 ChromeDriver，或设置 CHROMEDRIVER_PATH 后重试；工具不会自动下载驱动。');
  }
  const driverPort = await findFreePort();
  const driverUrl = `http://127.0.0.1:${driverPort}`;
  const driverProcess = spawn(driverPath, [`--port=${driverPort}`, '--allowed-ips=127.0.0.1'], {
    windowsHide: true,
    stdio: ['ignore', 'ignore', 'pipe'],
  });
  let stderr = '';
  driverProcess.stderr.on('data', chunk => { stderr += String(chunk); });
  try {
    await waitForDriver(driverUrl, driverProcess);
  } catch (error) {
    driverProcess.kill();
    if (stderr.trim()) console.error(`ChromeDriver：${stderr.trim().split(/\r?\n/).slice(-1)[0]}`);
    throw error;
  }
  return { driverUrl, driverProcess, stderr: () => stderr };
}

async function stopChromeDriver(driver) {
  if (!driver) return;
  driver.driverProcess.kill();
}

class CdpClient {
  constructor(websocketUrl) {
    this.websocketUrl = websocketUrl;
    this.nextId = 0;
    this.pending = new Map();
    this.listeners = new Map();
    this.lastDocumentResponse = null;
    this.mainFrameId = null;
    this.mainFrameLoaderId = null;
    this.readyDocumentLoaderId = null;
    this.mainFrameNavigationVersion = 0;
    this.lastCaptchaRedirect = null;
    this.socket = null;
  }

  async connect() {
    this.socket = new WebSocket(this.websocketUrl);
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('连接 Chrome DevTools 超时')), DRIVER_START_TIMEOUT_MS);
      this.socket.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
      this.socket.addEventListener('error', () => { clearTimeout(timer); reject(new Error('连接 Chrome DevTools 失败')); }, { once: true });
    });
    this.socket.addEventListener('message', event => {
      let message;
      try { message = JSON.parse(String(event.data)); } catch { return; }
      if (message.id && this.pending.has(message.id)) {
        const { resolve, reject } = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) reject(new Error(message.error.message || 'Chrome DevTools 命令失败'));
        else resolve(message.result || {});
      } else if (message.method) {
        for (const listener of this.listeners.get(message.method) || []) listener(message.params || {});
      }
    });
    this.socket.addEventListener('close', () => {
      for (const { reject } of this.pending.values()) reject(new Error('Chrome DevTools 连接已关闭'));
      this.pending.clear();
    });
    await this.send('Page.enable');
    await this.send('Runtime.enable');
    return this;
  }

  send(method, params = {}) {
    if (this.socket?.readyState !== WebSocket.OPEN) return Promise.reject(new Error('Chrome DevTools 连接已关闭'));
    const id = ++this.nextId;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, listener) {
    if (!this.listeners.has(method)) this.listeners.set(method, []);
    this.listeners.get(method).push(listener);
  }

  close() {
    try { this.socket?.close(); } catch { /* already closed */ }
  }
}

async function cdpEvaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (result.exceptionDetails) {
    const detail = result.exceptionDetails.exception?.description?.split('\n')[0];
    throw new Error(detail || result.exceptionDetails.text || '页面脚本执行失败');
  }
  return result.result?.value;
}

async function cdpPageState(client) {
  return cdpEvaluate(client, '({readyState: document.readyState, href: location.href, pendingImages: [...document.images].filter(image => image.src && !image.complete).length, title: document.title || "", body: (document.body && document.body.innerText || "").slice(0, 4000)})');
}

function contentSnippet(text) {
  return String(text || '').replace(/\s+/g, ' ').trim().slice(0, 80) || '(空内容)';
}

function printPageEvidence(client, state, evidence = {}) {
  const label = evidence.sourceKey ? `[${evidence.sourceKey}] ` : '';
  const responseStatus = client.lastDocumentResponse?.status;
  const cached = client.lastDocumentResponse?.fromDiskCache || client.lastDocumentResponse?.fromServiceWorker;
  console.log(`${label}${evidence.reload ? '强制刷新' : '访问'} ${state.href || '(未知 URL)'} -> HTTP ${responseStatus || '未知'}${cached ? '（浏览器缓存/Service Worker）' : ''}；标题：${contentSnippet(state.title)}；内容：${contentSnippet(state.body)}`);
}

function cdpBeginNavigation(client) {
  client.lastDocumentResponse = null;
  client.lastCaptchaRedirect = null;
  client.readyDocumentLoaderId = null;
  return {
    navigationVersion: client.mainFrameNavigationVersion,
    previousLoaderId: client.mainFrameLoaderId,
    deadline: Date.now() + PAGE_READY_TIMEOUT_MS,
  };
}

async function cdpWaitForNavigation(client, url, navigation, evidence = {}, result = {}, pendingCheck) {
  const { navigationVersion, previousLoaderId, deadline } = navigation;
  let state;
  let navigationReady = false;
  while (Date.now() < deadline) {
    await sleep(200);
    if (pendingCheck && client.mainFrameNavigationVersion === navigationVersion
        && client.mainFrameLoaderId === previousLoaderId) await pendingCheck();
    const loaderId = result.loaderId || (client.mainFrameLoaderId !== previousLoaderId ? client.mainFrameLoaderId : null);
    // Only accept DOM readiness and a response belonging to this navigation, never the previous page.
    if (!loaderId || client.mainFrameNavigationVersion <= navigationVersion
        || client.mainFrameLoaderId !== loaderId || client.readyDocumentLoaderId !== loaderId
        || client.lastDocumentResponse?.loaderId !== loaderId) continue;
    state = await cdpPageState(client);
    if (state && ['interactive', 'complete'].includes(state.readyState)
        && new URL(state.href).href.split('#')[0] === new URL(client.lastDocumentResponse.url).href.split('#')[0]) {
      navigationReady = true;
      break;
    }
  }
  if (!navigationReady) {
    throw new Error(`页面未在 ${PAGE_READY_TIMEOUT_MS / 1000} 秒内完成主框架导航：${new URL(url).pathname || '/'}`);
  }
  if (state.pendingImages) {
    const pending = await cdpEvaluate(client, '[...document.images].filter(image => image.src && !image.complete).slice(0, 5).map(image => image.src)');
    const paths = (Array.isArray(pending) ? pending : []).map(resource => {
      try { return new URL(resource).pathname; } catch (_) { return '[未知资源]'; }
    });
    console.log(`提示：${new URL(state.href).pathname || '/'} 有 ${state.pendingImages} 个图片资源仍在后台加载，已忽略，不阻断本轮访问：${paths.join('、')}`);
  }
  if (/\b503\b|service unavailable|服务不可用|暂不可用/i.test(`${state.title}\n${state.body}`)) {
    throw new Error(`页面返回或显示 503（${new URL(state.href || url).pathname || '/'}），已停止继续请求，避免扩大封禁：${state.title || 'Service Unavailable'}`);
  }
  const responseStatus = client.lastDocumentResponse?.status;
  const responseUrl = client.lastDocumentResponse?.url || state.href || url;
  if (Number(responseStatus) >= 400) {
    throw new Error(`页面主文档返回 HTTP ${responseStatus}（${new URL(responseUrl).pathname || '/'}），已停止继续请求`);
  }
  printPageEvidence(client, state, evidence);
  return state;
}

async function cdpNavigate(client, url, evidence = {}, { reload = false } = {}) {
  const navigation = cdpBeginNavigation(client);
  const result = reload
    ? await client.send('Page.reload', { ignoreCache: true })
    : await client.send('Page.navigate', { url });
  if (result.errorText) throw new Error(`浏览器导航失败：${result.errorText}`);
  const state = await cdpWaitForNavigation(client, url, navigation, { ...evidence, reload }, result);
  return state.href;
}

async function startCdpSession(config, entry, profileRoot) {
  const chromePath = findChromeExecutable();
  if (!chromePath) throw new Error('未找到 Chrome，请设置 CHROME_PATH 或安装 Google Chrome');
  const port = await findFreePort();
  const profileDir = path.join(profileRoot, entry.key);
  fs.mkdirSync(profileDir, { recursive: true });
  const chromeProcess = spawn(chromePath, [
    `--remote-debugging-port=${port}`,
    '--remote-debugging-address=127.0.0.1',
    '--remote-allow-origins=*',
    `--user-data-dir=${profileDir}`,
    '--profile-directory=Default',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-notifications',
    'about:blank',
  ], { detached: true, windowsHide: false, stdio: 'ignore' });
  const endpoint = `http://127.0.0.1:${port}`;
  let websocketUrl;
  const deadline = Date.now() + DRIVER_START_TIMEOUT_MS;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${endpoint}/json/list`, { signal: AbortSignal.timeout(1000) });
      const targets = await response.json();
      const target = targets.find(item => item.type === 'page' && item.webSocketDebuggerUrl);
      if (target) { websocketUrl = target.webSocketDebuggerUrl; break; }
    } catch { /* Chrome is still starting. */ }
    await sleep(150);
  }
  if (!websocketUrl) {
    chromeProcess.kill();
    throw new Error('等待普通 Chrome DevTools 端口超时');
  }
  const client = await new CdpClient(websocketUrl).connect();
  const frameTree = await client.send('Page.getFrameTree');
  client.mainFrameId = frameTree.frameTree?.frame?.id || null;
  client.mainFrameLoaderId = frameTree.frameTree?.frame?.loaderId || null;
  client.on('Page.frameNavigated', event => {
    if (!event.frame?.parentId) {
      client.mainFrameId = event.frame.id;
      client.mainFrameLoaderId = event.frame.loaderId;
      client.mainFrameNavigationVersion++;
    }
  });
  client.on('Page.navigatedWithinDocument', event => {
    if (event.frameId === client.mainFrameId) client.mainFrameNavigationVersion++;
  });
  client.on('Page.lifecycleEvent', event => {
    if (event.frameId === client.mainFrameId && event.name === 'DOMContentLoaded') {
      client.readyDocumentLoaderId = event.loaderId;
    }
  });
  await client.send('Page.setLifecycleEventsEnabled', { enabled: true });
  await client.send('Network.enable');
  // Start this session cold, then let repeat visits reuse cacheable resources.
  await client.send('Network.setCacheDisabled', { cacheDisabled: false });
  await client.send('Network.clearBrowserCache');
  await client.send('Network.setBypassServiceWorker', { bypass: true });
  console.log(`${entry.key} 本轮已清理一次 HTTP 缓存，后续访问正常复用；A 回访的单次强刷除外。`);
  client.on('Network.requestWillBeSent', event => {
    const redirect = event.redirectResponse;
    if (!redirect) return;
    const location = Object.entries(redirect.headers || {})
      .find(([name]) => name.toLowerCase() === 'location')?.[1];
    if (Number(redirect.status) < 300 || Number(redirect.status) >= 400 || !location) return;
    try {
      const target = new URL(String(location), redirect.url);
      if (isCaptchaPath(target.href)) {
        client.lastCaptchaRedirect = { url: event.request?.url || redirect.url, status: redirect.status };
      }
    } catch { /* Ignore malformed redirect metadata. */ }
  });
  client.on('Network.responseReceived', event => {
    if (event.type === 'Document' && (!client.mainFrameId || event.frameId === client.mainFrameId)) {
      client.lastDocumentResponse = {
        status: event.response?.status, url: event.response?.url, loaderId: event.loaderId,
        fromDiskCache: event.response?.fromDiskCache, fromServiceWorker: event.response?.fromServiceWorker,
      };
    }
  });
  return { client, chromeProcess };
}

async function closeCdpSession(session) {
  if (!session) return;
  try { await session.client.send('Browser.close'); } catch { session.client.close(); }
  await sleep(300);
  if (!session.chromeProcess.killed) session.chromeProcess.kill();
}

function preserveCdpSession(session) {
  if (!session) return;
  // Disconnect the controller without sending Browser.close so the user can
  // inspect the failed page and its cookies in the still-open Chrome window.
  session.client.close();
  if (typeof session.chromeProcess.unref === 'function') session.chromeProcess.unref();
}

async function cdpWaitForCaptcha(entry, client) {
  console.log(`\n${entry.key} 已进入验证码页面（/test）。`);
  console.log('请在普通 Chrome 窗口中手动完成极验；脚本会自动检测页面离开 /test，不需要按 Enter。');
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    const href = await cdpEvaluate(client, 'location.href');
    if (!isCaptchaPath(href)) {
      console.log(`${entry.key} 已离开 /test，当前页面路径：${new URL(href).pathname || '/'}`);
      printPageEvidence(client, await cdpPageState(client), { sourceKey: entry.key });
      client.lastCaptchaRedirect = null;
      return href;
    }
    await sleep(500);
  }
  throw new Error(`${entry.key} 等待极验超过 180 秒，仍停留在 /test；请确认极验是否失败或页面是否被封禁`);
}

async function cdpNavigateWithPacing(client, url, lastAt, evidence = {}, options = {}) {
  const remaining = NAVIGATION_INTERVAL_MS - (Date.now() - (lastAt.value || 0));
  if (lastAt.value && remaining > 0) {
    console.log(`请求节流：等待 ${(remaining / 1000).toFixed(1)} 秒后访问 ${new URL(url).pathname || '/'}`);
    await sleep(remaining);
  }
  lastAt.value = Date.now();
  return cdpNavigate(client, url, evidence, options);
}

async function cdpEnsureCaptchaTopLevel(entry, client, url, lastAt) {
  const state = await cdpNavigateWithPacing(client, new URL('/test', url).href, lastAt, { sourceKey: entry.key });
  if (!isCaptchaPath(state)) {
    throw new Error(`${entry.key} 检测到请求跳转信号，但顶层访问 /test 后未显示验证码页`);
  }
}

async function cdpRequireCaptcha(entry, client, url, lastAt, evidence = {}, { refreshOnce = false } = {}) {
  let href = await cdpNavigateWithPacing(client, url, lastAt, { ...evidence, sourceKey: entry.key });
  if (refreshOnce && !isCaptchaPath(href) && !client.lastCaptchaRedirect) {
    console.log(`${entry.key} 首次回访未进入 /test，按本节流程强制刷新一次（额外一次真实访问，单独记录结果）。`);
    href = await cdpNavigateWithPacing(client, href, lastAt, { ...evidence, sourceKey: entry.key }, { reload: true });
  }
  if (!isCaptchaPath(href) && !client.lastCaptchaRedirect) throw new Error(`${entry.key} 访问 ${new URL(url).pathname || '/'} 时未进入 /test`);
  if (!isCaptchaPath(href)) {
    console.log(`${entry.key} 检测到页面请求被重定向到 /test：HTTP ${client.lastCaptchaRedirect.status}`);
    await cdpEnsureCaptchaTopLevel(entry, client, url, lastAt);
  }
  await cdpWaitForCaptcha(entry, client);
}

async function cdpRequireNoCaptcha(entry, client, url, lastAt, evidence = {}) {
  const href = await cdpNavigateWithPacing(client, url, lastAt, { ...evidence, sourceKey: entry.key });
  if (client.lastCaptchaRedirect) {
    throw new Error(`${entry.key} 访问 ${new URL(url).pathname || '/'} 时有请求被重定向到 /test：HTTP ${client.lastCaptchaRedirect.status}`);
  }
  if (isCaptchaPath(href)) throw new Error(`${entry.key} 访问 ${new URL(url).pathname || '/'} 时进入 /test`);
  return href;
}

async function cdpLogin(entry, client, config, account, lastAt, evidence = {}) {
  const loginUrl = `${sourceUrl(config, entry.item)}login`;
  let href = await cdpNavigateWithPacing(client, loginUrl, lastAt, { ...evidence, sourceKey: entry.key });
  if (isCaptchaPath(href)) {
    await cdpWaitForCaptcha(entry, client);
    href = await cdpNavigateWithPacing(client, loginUrl, lastAt, { ...evidence, sourceKey: entry.key });
  }
  if (new URL(href).pathname !== '/login') throw new Error(`${entry.key} 登录页被重定向到 ${new URL(href).pathname || '/'}`);
  const username = JSON.stringify(account.username);
  const password = JSON.stringify(unprotectSecret(account.passwordProtected));
  const filled = await cdpEvaluate(client, `(() => {
    const set = (selector, value) => { const el = document.querySelector(selector); if (!el) return false; const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; setter.call(el, value); el.dispatchEvent(new Event('input', {bubbles:true})); el.dispatchEvent(new Event('change', {bubbles:true})); return true; };
    return set('#nameOrEmail', ${username}) && set('#loginPassword', ${password});
  })()`);
  if (!filled) throw new Error(`${entry.key} 登录页缺少账号或密码输入框`);
  const navigation = cdpBeginNavigation(client);
  const clicked = await cdpEvaluate(client, `(() => {
    const button = document.querySelector('#account_login button.green');
    if (!button) return false;
    button.click();
    return true;
  })()`);
  if (!clicked) throw new Error(`${entry.key} 登录页缺少登录按钮`);
  const state = await cdpWaitForNavigation(client, loginUrl, navigation,
    { ...evidence, sourceKey: entry.key }, {}, async () => {
      const tip = await cdpEvaluate(client, 'document.querySelector("#loginTip.error")?.textContent || ""');
      if (String(tip).trim()) throw new Error(`${entry.key} 登录账号 ${account.username} 未成功：${String(tip).trim()}`);
    });
  href = state.href;
  if (isCaptchaPath(href)) {
    await cdpWaitForCaptcha(entry, client);
    return cdpLogin(entry, client, config, account, lastAt, evidence);
  }
  // These header links are rendered from currentUser in the PC/mobile templates.
  // Article author avatars are not evidence of the current session's identity.
  const identity = await cdpEvaluate(client, `(() => {
    const pc = document.querySelector('#aPersonListPanel[data-url]');
    const mobile = document.querySelector('.nav a.nav-avatar[href]');
    const target = pc?.getAttribute('data-url') || mobile?.getAttribute('href');
    if (!target) return null;
    const url = new URL(target, location.href);
    const match = /^\\/member\\/([^/]+)\\/?$/.exec(url.pathname);
    return match ? decodeURIComponent(match[1]) : null;
  })()`);
  if (!identity) throw new Error(`${entry.key} 登录账号 ${account.username} 未成功：页面已就绪，但页头没有当前用户标记`);
  if (identity !== account.username) throw new Error(`${entry.key} 登录身份不符：预期 ${account.username}，实际 ${identity}`);
  console.log(`${entry.key} 已登录账号：${account.username}`);
}

async function cdpCollectArticleUrls(client, config, entry) {
  const links = await cdpEvaluate(client, ' [...document.querySelectorAll("a[href]")].map(a => a.href).filter(href => { try { return new URL(href).pathname.startsWith("/article/"); } catch (_) { return false; } }).filter((href, index, all) => all.indexOf(href) === index).slice(0, 20)');
  if (!Array.isArray(links) || links.length < 10) throw new Error(`${entry.key} 首页只发现 ${Array.isArray(links) ? links.length : 0} 个不同文章链接，第一阶段要求至少 10 个`);
  return links.slice(0, 10).map(url => {
    const parsed = new URL(url);
    return `${sourceUrl(config, entry.item)}${parsed.pathname.replace(/^\//, '')}${parsed.search}${parsed.hash}`;
  });
}

async function chooseAcceptanceModules(config, argument) {
  if (!config) throw new Error('请先运行配置向导');
  console.log('=== 自选测试 ===');
  TEST_MODULES.forEach((module, index) => console.log(`  ${index + 1}. ${module.name}：${module.detail}`));
  const previous = Array.isArray(config.testModules) ? config.testModules : FIRST_FLOW_MODULES;
  const value = argument || await ask('输入编号或名称，多个用逗号分隔，all为全部', previous.join(','));
  const selected = parseTestModules(value);
  console.log(`本轮测试：${selected.map(id => TEST_MODULES.find(module => module.id === id).name).join(' → ')}`);
  console.log('必要登录准备自动执行；真实极验仍需手动完成。');
  saveConfig({ ...config, testModules: selected });
  return selected;
}

async function runFirstAcceptanceFlowCdp(config, selected = FIRST_FLOW_MODULES) {
  if (!config?.port || !config.ipPlan?.loopback || config.ipPlan.extra?.length !== REQUIRED_EXTRA_IPS) {
    throw new Error('未找到完整配置，请先运行配置向导并确认 L 加 5 个额外 IP');
  }
  const runRoot = path.resolve(CONFIG_DIR, 'runs', `cdp-suite-${Date.now()}`);
  const profileRoot = path.join(runRoot, 'profiles');
  const resultPath = path.join(runRoot, 'results.json');
  fs.mkdirSync(runRoot, { recursive: true });
  const sessions = new Set();
  let completed = false;
  try {
    console.log('使用普通 Chrome + DevTools Protocol，不启动 ChromeDriver。');
    const result = await runAcceptanceSuite({
      config, selected,
      persist: data => fs.writeFileSync(resultPath, `${JSON.stringify(data, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 }),
      runtime: {
        entries: sourceEntries, sourceUrl, evaluate: cdpEvaluate, sleep,
        isCaptcha: isCaptchaPath, login: cdpLogin, articles: cdpCollectArticleUrls,
        navigate: cdpNavigateWithPacing, ensureCaptcha: cdpEnsureCaptchaTopLevel, waitCaptcha: cdpWaitForCaptcha,
        start: async entry => {
          const session = await startCdpSession(config, entry, profileRoot);
          sessions.add(session);
          return session;
        },
        close: async session => {
          await closeCdpSession(session);
          // Suite sessions add entry/lastAt to the controller; track by the shared client.
          for (const tracked of sessions) if (tracked.client === session.client) sessions.delete(tracked);
        },
      },
    });
    console.log(`\n所选 ${result.modules.length} 个模块全部通过。`);
    completed = true;
  } catch (error) {
    console.error(`失败原因：${error.message}`);
    throw error;
  } finally {
    console.log(`本轮结果：${resultPath}`);
    if (completed) {
      for (const session of [...sessions].reverse()) await closeCdpSession(session);
      if (path.dirname(path.resolve(profileRoot)) !== runRoot || path.basename(profileRoot) !== 'profiles') {
        throw new Error('临时用户目录不在本轮目录内，拒绝清理');
      }
      try { fs.rmSync(profileRoot, { recursive: true, force: true }); }
      catch (error) { console.error(`浏览器临时目录清理失败：${error.message}；目录：${profileRoot}`); }
    } else {
      for (const session of sessions) preserveCdpSession(session);
      console.error('\n测试失败，已保留浏览器现场，未关闭 Chrome。');
      console.error(`临时用户目录：${profileRoot}`);
      console.error('脚本将在此暂停，不会继续清理浏览器；请查看 Chrome 窗口。');
      try { await ask('确认查看完成后按 Enter，脚本将退出（不会关闭 Chrome）'); }
      catch (error) { console.error(`无法等待终端输入：${error.message}`); }
      console.error('Chrome 窗口和临时目录仍保留；确认现场后请手动关闭窗口。');
    }
  }
}

async function requireCaptcha(entry, driverUrl, sessionId, url) {
  const current = await navigate(driverUrl, sessionId, url);
  if (!isCaptchaPath(current)) {
    throw new Error(`${entry.key} 访问 ${new URL(url).pathname || '/'} 时未进入 /test`);
  }
  await waitForCaptchaCompletion(entry, driverUrl, sessionId);
}

async function requireNoCaptcha(entry, driverUrl, sessionId, url) {
  const current = await navigate(driverUrl, sessionId, url);
  if (isCaptchaPath(current)) {
    throw new Error(`${entry.key} 访问 ${new URL(url).pathname || '/'} 时意外进入 /test`);
  }
  return current;
}

async function loginWithAccount(entry, driverUrl, sessionId, config, account) {
  const loginUrl = `${sourceUrl(config, entry.item)}login`;
  let current = await navigate(driverUrl, sessionId, loginUrl);
  if (isCaptchaPath(current)) {
    await waitForCaptchaCompletion(entry, driverUrl, sessionId);
    // /test normally returns to the originally requested page. Re-open the
    // login form instead of assuming the browser stayed on /login.
    current = await navigate(driverUrl, sessionId, loginUrl);
    if (isCaptchaPath(current)) {
      throw new Error(`${entry.key} 完成验证码后仍无法打开登录页`);
    }
  }
  if (new URL(current).pathname !== '/login') {
    throw new Error(`${entry.key} 登录页被重定向到 ${new URL(current).pathname || '/'}，请确认本次测试会话已清理`);
  }
  const usernameInput = await webdriverFind(driverUrl, sessionId, 'css selector', '#nameOrEmail');
  const passwordInput = await webdriverFind(driverUrl, sessionId, 'css selector', '#loginPassword');
  await sendElementKeys(driverUrl, sessionId, usernameInput, account.username);
  await sendElementKeys(driverUrl, sessionId, passwordInput, unprotectSecret(account.passwordProtected));
  const loginButton = await webdriverFind(driverUrl, sessionId, 'css selector', '#account_login button.green');
  await clickElement(driverUrl, sessionId, loginButton);
  await sleep(1200);
  const afterLogin = await currentUrl(driverUrl, sessionId);
  if (isCaptchaPath(afterLogin)) {
    await waitForCaptchaCompletion(entry, driverUrl, sessionId);
    return loginWithAccount(entry, driverUrl, sessionId, config, account);
  }
  const loggedIn = await webdriverExecute(driverUrl, sessionId, 'return !!document.querySelector(".avatar, .avatar-small, .nav-avatar, a[data-url*=\'/member/\'], a[href=\'/logout\']");');
  if (!loggedIn) {
    const tip = await webdriverExecute(driverUrl, sessionId, 'return (document.querySelector("#loginTip") || {}).textContent || "登录后仍停留在登录页";');
    throw new Error(`${entry.key} 登录账号 ${account.username} 未成功：${String(tip).trim() || '未检测到登录状态'}`);
  }
  console.log(`${entry.key} 已登录账号：${account.username}`);
}

async function collectArticleUrls(driverUrl, sessionId, config, entry) {
  const links = await webdriverExecute(driverUrl, sessionId, `
    return [...document.querySelectorAll('a[href]')].map(a => a.href)
      .filter(href => { try { return new URL(href).pathname.startsWith('/article/'); } catch (_) { return false; } })
      .filter((href, index, all) => all.indexOf(href) === index).slice(0, 20);
  `);
  const urls = Array.isArray(links) ? links : [];
  if (urls.length < 10) {
    throw new Error(`${entry.key} 首页只发现 ${urls.length} 个不同文章链接，第一阶段要求至少 10 个，未伪造文章地址`);
  }
  return urls.slice(0, 10).map(url => {
    const parsed = new URL(url);
    return `${sourceUrl(config, entry.item)}${parsed.pathname.replace(/^\//, '')}${parsed.search}${parsed.hash}`;
  });
}

async function checkAdminMisc(entry, driverUrl, sessionId, config, section) {
  const url = `${sourceUrl(config, entry.item)}admin/misc`;
  const current = await navigate(driverUrl, sessionId, url);
  if (isCaptchaPath(current)) throw new Error(`${entry.key} 核对 /admin/misc 时进入 /test`);
  const pathName = new URL(current).pathname;
  if (pathName === '/login' || pathName === '/401' || pathName === '/403') {
    throw new Error(`${entry.key} 无法访问 /admin/misc（当前路径 ${pathName}）`);
  }
  const report = await webdriverExecute(driverUrl, sessionId, `return {
    section: ${JSON.stringify(section)},
    hasSecurityPanel: !!document.querySelector('.security-panel'),
    eventRows: document.querySelectorAll('#securityEvents tr').length,
    selfCheck: (document.querySelector('#securitySelfCheck') || {}).textContent || ''
  };`);
  if (!report?.hasSecurityPanel) throw new Error(`${entry.key} /admin/misc 未找到社区安全面板`);
  console.log(`${entry.key} /admin/misc 已核对：${section}，监控行数 ${report.eventRows || 0}`);
  return report;
}

async function runFirstAcceptanceFlow(config) {
  if (!config?.port || !config.ipPlan?.loopback || config.ipPlan.extra?.length !== REQUIRED_EXTRA_IPS) {
    throw new Error('未找到完整阶段 1 配置，请先运行 configure 并确认 L 加 5 个额外 IP');
  }
  const baseline = config.accounts.find(account => account.key === 'baseline');
  const admin = config.accounts.find(account => account.key === 'admin');
  if (!baseline || !admin) throw new Error('配置中缺少普通登录基线账号或管理员账号');
  const entries = sourceEntries(config);
  const entryL = entries.find(entry => entry.key === 'L');
  const entryA = entries.find(entry => entry.key === 'A');
  const entryB = entries.find(entry => entry.key === 'B');
  const driver = await startChromeDriver(config);
  const sessions = new Map();
  const runProfileRoot = path.join(CONFIG_DIR, 'runs', `flow-${Date.now()}`);
  try {
    // Create each profile immediately before its test section so idle browser
    // windows do not open and then sit unused while another source is tested.
    sessions.set(entryL.key, await createChromeSession(driver.driverUrl, entryL, runProfileRoot));
    await resetBrowserSession(driver.driverUrl, sessions.get(entryL.key));
    const lSession = sessions.get('L');
    console.log('\n=== 第一阶段：L 登录基线 ===');
    await requireNoCaptcha(entryL, driver.driverUrl, lSession, sourceUrl(config, entryL.item));
    await loginWithAccount(entryL, driver.driverUrl, lSession, config, baseline);
    const articleUrls = await collectArticleUrls(driver.driverUrl, lSession, config, entryL);
    for (let i = 0; i < 10; i++) await requireNoCaptcha(entryL, driver.driverUrl, lSession, sourceUrl(config, entryL.item));
    for (const articleUrl of articleUrls) await requireNoCaptcha(entryL, driver.driverUrl, lSession, articleUrl);
    console.log('L 基线通过：主页 10 次、10 个不同文章页均未进入 /test。');

    console.log('L 基线完成；管理员监控核对将在 A 登录管理员后执行。');

    console.log('\n=== 第一阶段：A 首访与每 5 次访问 ===');
    sessions.set(entryA.key, await createChromeSession(driver.driverUrl, entryA, runProfileRoot));
    await resetBrowserSession(driver.driverUrl, sessions.get(entryA.key));
    const aSession = sessions.get('A');
    await requireCaptcha(entryA, driver.driverUrl, aSession, sourceUrl(config, entryA.item));
    const aArticleUrls = articleUrls.map(url => `${sourceUrl(config, entryA.item)}${new URL(url).pathname.replace(/^\//, '')}`);
    let redirectedAt = 0;
    for (let count = 1; count <= 5; count++) {
      const target = count <= 2 ? sourceUrl(config, entryA.item) : aArticleUrls[count - 3];
      const current = await navigate(driver.driverUrl, aSession, target);
      if (isCaptchaPath(current)) { redirectedAt = count; await waitForCaptchaCompletion(entryA, driver.driverUrl, aSession); break; }
    }
    if (!redirectedAt) throw new Error('A 通过 5 次主页/文章访问后仍未回到 /test');
    console.log(`A 已在第 ${redirectedAt} 次访问内重新进入 /test。`);
    await loginWithAccount(entryA, driver.driverUrl, aSession, config, admin);
    await checkAdminMisc(entryA, driver.driverUrl, aSession, config, 'A 验证码后管理员登录');

    for (let count = 1; count <= 5; count++) {
      const target = count <= 2 ? sourceUrl(config, entryA.item) : aArticleUrls[count - 3];
      await requireNoCaptcha(entryA, driver.driverUrl, aSession, target);
    }
    console.log('A 登录后通过 5 次访问，未再次进入 /test。');

    console.log('\n=== 第一阶段：B 登录态与 A 缓存切换 ===');
    sessions.set(entryB.key, await createChromeSession(driver.driverUrl, entryB, runProfileRoot));
    await resetBrowserSession(driver.driverUrl, sessions.get(entryB.key));
    const bSession = sessions.get('B');
    await requireCaptcha(entryB, driver.driverUrl, bSession, sourceUrl(config, entryB.item));
    await loginWithAccount(entryB, driver.driverUrl, bSession, config, admin);
    await checkAdminMisc(entryB, driver.driverUrl, bSession, config, 'B 验证码后管理员登录');
    await requireCaptcha(entryA, driver.driverUrl, aSession, sourceUrl(config, entryA.item));
    console.log('A/B 源地址切换通过：B 登录后返回 A，再次进入 /test。');
    await checkAdminMisc(entryA, driver.driverUrl, aSession, config, 'A/B 切换完成');
    console.log('\n第一阶段业务流程完成。');
  } finally {
    for (const sessionId of [...sessions.values()].reverse()) {
      await webdriverRequest(driver.driverUrl, `/session/${sessionId}`, 'DELETE').catch(() => {});
    }
    await stopChromeDriver(driver);
    try {
      fs.rmSync(runProfileRoot, { recursive: true, force: true });
    } catch {
      // Chrome may release profile files slightly after DELETE; stale run
      // folders are harmless and can be removed on a later run.
    }
  }
}

async function runBrowser(config) {
  if (!config?.port || !config.ipPlan?.loopback || config.ipPlan.extra?.length !== REQUIRED_EXTRA_IPS) {
    throw new Error('未找到完整阶段 1 配置，请先运行 configure 并确认 L 加 5 个额外 IP');
  }
  const driverPath = findChromeDriver(config);
  if (!driverPath) {
    throw new Error('未找到 chromedriver.exe。请安装与 Chrome 主版本匹配的 ChromeDriver，或设置 CHROMEDRIVER_PATH 后重试；工具不会自动下载驱动。');
  }

  const driverPort = await findFreePort();
  const driverUrl = `http://127.0.0.1:${driverPort}`;
  const driverProcess = spawn(driverPath, [`--port=${driverPort}`, '--allowed-ips=127.0.0.1'], {
    windowsHide: true,
    stdio: ['ignore', 'ignore', 'pipe'],
  });
  let stderr = '';
  driverProcess.stderr.on('data', chunk => { stderr += String(chunk); });
  const sessions = [];
  try {
    await waitForDriver(driverUrl, driverProcess);
    for (const entry of sourceEntries(config)) {
      const profileDir = path.join(CONFIG_DIR, 'profiles', entry.key);
      fs.mkdirSync(profileDir, { recursive: true });
      const capabilities = {
        capabilities: {
          alwaysMatch: {
            browserName: 'chrome',
            pageLoadStrategy: 'eager',
            'goog:chromeOptions': {
              args: [
                `--user-data-dir=${profileDir}`,
                '--new-window',
                '--no-first-run',
                '--no-default-browser-check',
                '--disable-notifications',
              ],
            },
          },
        },
      };
      const session = await webdriverRequest(driverUrl, '/session', 'POST', capabilities);
      const sessionId = session.sessionId;
      sessions.push(sessionId);
      const targetUrl = sourceUrl(config, entry.item);
      console.log(`\n打开 ${entry.label}：${targetUrl}`);
      await webdriverRequest(driverUrl, `/session/${sessionId}/url`, 'POST', { url: targetUrl });
      const currentUrl = await webdriverRequest(driverUrl, `/session/${sessionId}/url`);
      if (isCaptchaPath(currentUrl)) {
        await waitForCaptchaCompletion(entry, driverUrl, sessionId);
      } else {
        console.log(`${entry.key} 未进入 /test，当前页面路径：${new URL(currentUrl).pathname || '/'}`);
      }
      await webdriverRequest(driverUrl, `/session/${sessionId}/window/maximize`, 'POST', {}).catch(() => {});
    }
    console.log('\n阶段 2 浏览器流程已完成：每个源地址都已打开，遇到 /test 时均要求人工完成极验。');
  } catch (error) {
    if (stderr.trim()) console.error(`ChromeDriver：${stderr.trim().split(/\r?\n/).slice(-1)[0]}`);
    throw error;
  } finally {
    for (const sessionId of sessions.reverse()) {
      await webdriverRequest(driverUrl, `/session/${sessionId}`, 'DELETE').catch(() => {});
    }
    driverProcess.kill();
  }
}

async function configure() {
  console.log('=== 验证码盾自动化验收：阶段 1 配置 ===');
  console.log('本阶段只保存配置，不访问服务，不验证账号，不执行压测。');

  const previous = loadConfig();
  let port;
  let expectedCommit;
  let accounts;
  let loopback;
  let extra;

  const reusePrevious = previous && previous.accounts?.length && previous.ipPlan?.extra?.length
    ? (showConfig(previous), (await ask('是否复用上次填写的账号和 IP（输入 y 复用，其他键重新填写）', 'y')).toLowerCase() === 'y')
    : false;

  if (reusePrevious) {
    port = previous.port;
    expectedCommit = previous.expectedCommit || '';
    accounts = previous.accounts;
    loopback = previous.ipPlan.loopback;
    extra = previous.ipPlan.extra;
    console.log('已复用上次的端口、账号和 IP，下面只需重新确认三项检查。');
  } else {
    port = await ask('服务端端口', previous?.port || '8080');
    expectedCommit = await ask('期望测试的 commit SHA（可留空）', previous?.expectedCommit || '');

    accounts = [];
    for (const requirement of ACCOUNT_REQUIREMENTS) {
      const old = previous?.accounts?.find(account => account.key === requirement.key);
      console.log(`\n${requirement.label}：${requirement.roleHint}`);
      const username = await ask('用户名', old?.username || '');
      const role = await ask('权限组', old?.role || '');
      const password = await ask('密码（直接输入，保存时会加密）');
      if (!username || !password) throw new Error(`${requirement.label}的用户名和密码不能为空`);
      accounts.push({
        key: requirement.key,
        label: requirement.label,
        username,
        role,
        passwordProtected: protectSecret(password),
      });
    }

    const ips = listLocalIps();
    printIps(ips);
    const loopbackCandidate = ips.find(isLoopback);
    if (!loopbackCandidate) throw new Error('没有发现回环地址候选，无法固定 L');
    loopback = loopbackCandidate;

    const ipv4Extra = ips.filter(item => !isLoopback(item) && isIpv4(item));
    const ipv6Extra = ips.filter(item => !isLoopback(item) && isUsableIpv6(item));
    if (ipv4Extra.length + ipv6Extra.length < REQUIRED_EXTRA_IPS) {
      const found = [...ipv4Extra, ...ipv6Extra].map(item => item.ip).join('、') || '无';
      throw new Error(`当前仅发现 ${ipv4Extra.length} 个 IPv4 和 ${ipv6Extra.length} 个可用 IPv6 额外地址，严格需要 ${REQUIRED_EXTRA_IPS} 个，当前地址：${found}`);
    }
    extra = [...ipv4Extra, ...ipv6Extra].slice(0, REQUIRED_EXTRA_IPS);
    const ipv6Count = extra.filter(isUsableIpv6).length;
    console.log(`已自动选择 ${REQUIRED_EXTRA_IPS} 个额外 IP，不需要手填（优先 IPv4，不足时补 IPv6）：`);
    extra.forEach((item, index) => console.log(`   ${String.fromCharCode(65 + index)}：${item.ip}${isUsableIpv6(item) ? '（IPv6）' : ''}`));
    if (ipv6Count > 0) {
      console.log(`其中 ${ipv6Count} 个为可路由 IPv6；后续浏览器和 HTTP 请求必须使用 IPv6 字面量方括号格式。`);
    }
  }

  const monitorConfig = await withSecurityMonitorAccount({ accounts });
  accounts = monitorConfig.accounts;

  console.log('\n=== 三项确认（脚本完全信任勾选，不做额外环境检查）===');
  const environmentConfirmed = (await ask('1. 我已确认端口、runtimeMode、临时 jiyan.key、服务已启动且代码最新（输入 y）', ''))
    .toLowerCase() === 'y';
  const accountsConfirmed = (await ask('2. 我已确认账号用户名、密码和权限组（输入 y）', ''))
    .toLowerCase() === 'y';
  console.log('3. 已选择的 IP：');
  console.log(`   L: ${loopback.ip}`);
  extra.forEach((item, index) => console.log(`   ${String.fromCharCode(65 + index)}: ${item.ip}`));
  const ipsConfirmed = (await ask(`3. 我已确认 L 加 ${REQUIRED_EXTRA_IPS} 个额外 IP（输入 y）`, ''))
    .toLowerCase() === 'y';

  if (!environmentConfirmed || !accountsConfirmed || !ipsConfirmed) {
    throw new Error('三项确认未全部勾选，未保存新配置');
  }

  saveConfig({
    version: 1,
    baseUrl: `http://localhost:${port}`,
    port,
    expectedCommit,
    accounts,
    ipPlan: { loopback, extra },
    chromeDriverPath: previous?.chromeDriverPath || '',
    testModules: previous?.testModules || [...FIRST_FLOW_MODULES],
    updatedAt: new Date().toISOString(),
  });
  console.log(`阶段 1 配置已保存：${CONFIG_PATH}`);
  console.log('密码已使用当前 Windows 用户 DPAPI 保护，jiyan.key 未被读取或保存。');
}

async function main() {
  const command = process.argv[2] || 'configure';
  try {
    if (command === 'configure') await configure();
    else if (command === 'show') showConfig(loadConfig());
    else if (command === 'ips') printIps(listLocalIps());
    else if (command === 'driver') await configureDriver();
    else if (command === 'browser') await runBrowser(loadConfig());
    else if (command === 'flow') await runFirstAcceptanceFlowCdp(await prepareSecurityMonitorConfig(loadConfig()));
    else if (command === 'suite') {
      const config = loadConfig();
      const selected = await chooseAcceptanceModules(config, process.argv.slice(3).join(','));
      const prepared = await prepareSecurityMonitorConfig({ ...config, testModules: selected });
      await runFirstAcceptanceFlowCdp(prepared, selected);
    }
    else if (command === 'webdriver-flow') await runFirstAcceptanceFlow(loadConfig());
    else throw new Error(`未知命令：${command}（可用 configure、show、ips、driver、browser、flow、suite）`);
  } catch (error) {
    console.error(`失败：${error.message}`);
    process.exitCode = 1;
  } finally {
    rl.close();
  }
}

await main();
