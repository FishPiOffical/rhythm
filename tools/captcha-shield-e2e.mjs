#!/usr/bin/env node
/**
 * Phase 1 of the captcha-shield acceptance runner.
 * This phase only saves configuration and human confirmations; it never sends
 * requests to the service and never attempts to solve a captcha.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import net from 'node:net';
import readline from 'node:readline/promises';
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

const rl = readline.createInterface({ input, output });

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
  return currentUrl(driverUrl, sessionId);
}

async function createChromeSession(driverUrl, entry) {
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
  return session.sessionId;
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
  const current = await navigate(driverUrl, sessionId, loginUrl);
  if (isCaptchaPath(current)) await waitForCaptchaCompletion(entry, driverUrl, sessionId);
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
  try {
    // Create each profile immediately before its test section so idle browser
    // windows do not open and then sit unused while another source is tested.
    sessions.set(entryL.key, await createChromeSession(driver.driverUrl, entryL));
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
    sessions.set(entryA.key, await createChromeSession(driver.driverUrl, entryA));
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
    sessions.set(entryB.key, await createChromeSession(driver.driverUrl, entryB));
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
    else if (command === 'flow') await runFirstAcceptanceFlow(loadConfig());
    else throw new Error(`未知命令：${command}（可用 configure、show、ips、driver、browser、flow）`);
  } catch (error) {
    console.error(`失败：${error.message}`);
    process.exitCode = 1;
  } finally {
    rl.close();
  }
}

await main();
