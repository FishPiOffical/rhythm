#!/usr/bin/env node
/**
 * Phase 1 of the captcha-shield acceptance runner.
 * This phase only saves configuration and human confirmations; it never sends
 * requests to the service and never attempts to solve a captcha.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
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
    if (ipv4Extra.length < REQUIRED_EXTRA_IPS) {
      const found = ipv4Extra.map(item => item.ip).join('、') || '无';
      throw new Error(`当前仅发现 ${ipv4Extra.length} 个可用 IPv4 额外地址，严格需要 ${REQUIRED_EXTRA_IPS} 个，当前地址：${found}`);
    }
    extra = ipv4Extra.slice(0, REQUIRED_EXTRA_IPS);
    console.log(`已自动选择前 ${REQUIRED_EXTRA_IPS} 个非 IPv6 额外 IP，不需要手填：`);
    extra.forEach((item, index) => console.log(`   ${String.fromCharCode(65 + index)}：${item.ip}`));
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
    else throw new Error(`未知命令：${command}（可用 configure、show、ips）`);
  } catch (error) {
    console.error(`失败：${error.message}`);
    process.exitCode = 1;
  } finally {
    rl.close();
  }
}

await main();
