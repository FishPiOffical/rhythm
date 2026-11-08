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
  console.log(`最近保存：${config.updatedAt || '(未知)'}`);
}

function findChromeDriver() {
  const candidates = [
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

async function webdriverRequest(driverUrl, endpoint, method = 'GET', body) {
  const response = await fetch(`${driverUrl}${endpoint}`, {
    method,
    headers: body === undefined ? undefined : { 'content-type': 'application/json; charset=utf-8' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(DRIVER_REQUEST_TIMEOUT_MS),
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

async function runBrowser(config) {
  if (!config?.port || !config.ipPlan?.loopback || config.ipPlan.extra?.length !== REQUIRED_EXTRA_IPS) {
    throw new Error('未找到完整阶段 1 配置，请先运行 configure 并确认 L 加 5 个额外 IP');
  }
  const driverPath = findChromeDriver();
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
    else if (command === 'browser') await runBrowser(loadConfig());
    else throw new Error(`未知命令：${command}（可用 configure、show、ips、browser）`);
  } catch (error) {
    console.error(`失败：${error.message}`);
    process.exitCode = 1;
  } finally {
    rl.close();
  }
}

await main();
